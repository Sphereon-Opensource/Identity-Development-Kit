/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.OpaqueSecretResolver
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerTarget
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Transport-neutral internal authorization-server client.
 *
 * Enterprise deployments replace the legacy Basic-auth implementation with an authenticated
 * routed-command client. Keeping the topology below [HttpAsBridge] preserves its protocol and
 * DPoP validation while removing secret selection from that bridge.
 */
interface Oid4vciAsInternalClient {
    suspend fun registerPreAuthorizedCode(request: PreAuthRegistrationRequest): IdkResult<Unit, IdkError>

    suspend fun introspectAccessToken(token: String, authorizationServer: Oid4vciAuthorizationServerTarget): IdkResult<JsonObject, IdkError>
}

/**
 * Standalone-IDK compatibility implementation.
 *
 * EDK replaces this binding. It remains here only for deployments that still explicitly configure
 * a bridge client secret reference.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciAsInternalClient>())
class LegacyBasicAuthOid4vciAsInternalClient(
    private val execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
    private val asBaseUrlResolver: Oid4vciAsBridgeBaseUrlResolver,
    private val opaqueSecretResolver: OpaqueSecretResolver,
) : Oid4vciAsInternalClient {
    private val httpClient: HttpClient by lazy {
        httpClientFactory.createClient(HttpClientOptions())
    }
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    override suspend fun registerPreAuthorizedCode(
        request: PreAuthRegistrationRequest,
    ): IdkResult<Unit, IdkError> =
        try {
            val credentials = clientCredentials(request.authorizationServer)
            val target = asTarget("/internal/preauth/register", request.authorizationServer)
            val response =
                httpClient.post(target.url) {
                    contentType(ContentType.Application.Json)
                    headers {
                        append(HttpHeaders.Authorization, credentials.toBasicAuthHeader())
                        target.hostHeader?.let { append(HttpHeaders.Host, it) }
                    }
                    setBody(json.encodeToString(request))
                }
            if (response.status.value in 200..299) {
                Ok(Unit)
            } else {
                Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "AS returned ${response.status.value}: ${response.bodyAsText()}",
                    ),
                )
            }
        } catch (expected: Exception) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to register pre-auth code with AS: ${expected.message}"))
        }

    override suspend fun introspectAccessToken(token: String, authorizationServer: Oid4vciAuthorizationServerTarget): IdkResult<JsonObject, IdkError> =
        try {
            val credentials = clientCredentials(authorizationServer)
            val target = asTarget("/introspect", authorizationServer)
            val response =
                httpClient.post(target.url) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    headers {
                        append(HttpHeaders.Authorization, credentials.toBasicAuthHeader())
                        target.hostHeader?.let { append(HttpHeaders.Host, it) }
                    }
                    setBody("token=$token&token_type_hint=access_token&client_id=${credentials.clientId}")
                }
            Ok(json.decodeFromString(response.bodyAsText()))
        } catch (expected: Exception) {
            Err(IdkError.UNKNOWN_ERROR(message = "Token introspection failed: ${expected.message}"))
        }

    private suspend fun clientCredentials(authorizationServer: Oid4vciAuthorizationServerTarget): AsBridgeClientCredentials {
        val reference = resolveAsBridgeClientCredentialReference(configService::getPropertyAsString, authorizationServer.runtimeServerKey)
        val clientSecret =
            opaqueSecretResolver.resolve(reference.clientSecretId).getOrElse {
                error("Authorization-server bridge credential is unavailable")
            }
        return AsBridgeClientCredentials(reference.clientId, clientSecret)
    }

    private suspend fun asTarget(path: String, authorizationServer: Oid4vciAuthorizationServerTarget): AsTarget {
        val publicBase = authorizationServer.issuer.trimEnd('/').takeIf { it.isNotEmpty() }
        val internalUrl = configService.getPropertyAsString("${HttpAsBridge.CONFIG_PREFIX}.internal-url")?.trimEnd('/')
        return when {
            publicBase != null && internalUrl != null ->
                AsTarget("$internalUrl$path", publicBase.substringAfter("://"))
            publicBase != null -> AsTarget("$publicBase$path", null)
            else -> AsTarget("${internalUrl ?: "http://localhost:8080"}$path", null)
        }
    }
}

private data class AsTarget(
    val url: String,
    val hostHeader: String?,
)

internal data class AsBridgeClientCredentials(
    val clientId: String,
    val clientSecret: String,
)

internal data class AsBridgeClientCredentialReference(
    val clientId: String,
    val clientSecretId: String,
)

internal fun AsBridgeClientCredentials.toBasicAuthHeader(): String {
    val encoded = "${clientId.encodeURLParameter()}:${clientSecret.encodeURLParameter()}"
    return "Basic ${encoded.encodeToByteArray().encodeToBase64()}"
}

internal fun resolveAsBridgeClientCredentialReference(
    read: (String) -> String?,
    requestedServerId: String? = null,
): AsBridgeClientCredentialReference {
    val activeServerId = requestedServerId?.trim()?.takeIf { it.isNotEmpty() }
        ?: read("oauth2.servers.default-server")?.trim()?.takeIf { it.isNotEmpty() }
    val activeIssuerPrefix = activeServerId?.let { "oauth2.servers.$it.internal-clients.issuer" }
    val clientId =
        activeIssuerPrefix
            ?.let { read("$it.client-id") }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: read("${HttpAsBridge.CONFIG_PREFIX}.client-id")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "issuer-service"
    val clientSecretId =
        activeIssuerPrefix
            ?.let { read("$it.client-secret-id") }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: read("${HttpAsBridge.CONFIG_PREFIX}.client-secret-id")?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("Authorization-server bridge clientSecretId is not configured")
    return AsBridgeClientCredentialReference(clientId = clientId, clientSecretId = clientSecretId)
}
