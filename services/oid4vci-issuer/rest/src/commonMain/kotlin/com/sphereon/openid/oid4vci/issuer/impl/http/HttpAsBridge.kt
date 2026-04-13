/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.openid.oid4vci.issuer.bridge.AugmentAsMetadataArgs
import com.sphereon.openid.oid4vci.issuer.bridge.AuthorizationContextRef
import com.sphereon.openid.oid4vci.issuer.bridge.ConsumePreAuthCodeArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ConsumedPreAuthCode
import com.sphereon.openid.oid4vci.issuer.bridge.CreateAuthContextArgs
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.bridge.RegisterPreAuthCodeArgs
import com.sphereon.openid.oid4vci.issuer.bridge.RegisteredPreAuthCode
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import com.sphereon.openid.oid4vci.issuer.impl.bridge.SphereonAsBridge
import dev.whyoleg.cryptography.random.CryptographyRandom
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * HTTP-based AS bridge for separate-process deployments.
 *
 * Registers pre-authorized codes with the AS via its internal HTTP API.
 * Used when the OID4VCI issuer and OAuth2 AS run in different containers/processes.
 *
 * Configuration (under `sphereon.oid4vci.issuer.as-bridge`):
 * - `internal-url`: Internal URL of the AS (e.g., http://oauth2-as:8080)
 * - `client-id`: Client ID for authenticating with the AS internal API
 * - `client-secret`: Client secret for authenticating with the AS internal API
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciAuthorizationServerBridge>(), replaces = [SphereonAsBridge::class])
class HttpAsBridge(
    private val execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
) : Oid4vciAuthorizationServerBridge {
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

    private val asInternalUrl: String
        get() =
            configService.getPropertyAsString("$CONFIG_PREFIX.internal-url")
                ?: "http://localhost:8080"

    private val clientId: String
        get() =
            configService.getPropertyAsString("$CONFIG_PREFIX.client-id")
                ?: "issuer-service"

    private val clientSecret: String
        get() =
            configService.getPropertyAsString("$CONFIG_PREFIX.client-secret")
                ?: ""

    @OptIn(ExperimentalEncodingApi::class)
    private fun basicAuthHeader(): String {
        val credentials = "$clientId:$clientSecret"
        return "Basic ${Base64.encode(credentials.encodeToByteArray())}"
    }

    override suspend fun registerPreAuthorizedCode(args: RegisterPreAuthCodeArgs): IdkResult<RegisteredPreAuthCode, IdkError> {
        val code = CryptographyRandom.nextBytes(32).encodeToBase64Url()
        val txCode = if (args.txCodeRequired) generateTxCode() else null
        val txCodeHash =
            txCode?.let {
                hash(it.encodeToByteArray(), DigestAlg.SHA256).encodeToBase64Url()
            }

        val request =
            PreAuthRegistrationRequest(
                code = code,
                sessionId = args.sessionId,
                credentialConfigurationIds = args.credentialConfigurationIds,
                txCodeRequired = args.txCodeRequired,
                txCodeHash = txCodeHash,
                issuerIdentifier = args.issuerIdentifier,
                useCredentialIdentifiers = args.useCredentialIdentifiers,
            )

        try {
            val response =
                httpClient.post("$asInternalUrl/internal/preauth/register") {
                    contentType(ContentType.Application.Json)
                    headers { append(HttpHeaders.Authorization, basicAuthHeader()) }
                    setBody(json.encodeToString(request))
                }
            if (response.status.value !in 200..299) {
                val body = response.bodyAsText()
                return Err(IdkError.UNKNOWN_ERROR(message = "AS returned ${response.status.value}: $body"))
            }
        } catch (expected: Exception) {
            return Err(IdkError.UNKNOWN_ERROR(message = "Failed to register pre-auth code with AS: ${expected.message}"))
        }

        return Ok(RegisteredPreAuthCode(code = code, txCode = txCode))
    }

    override suspend fun consumePreAuthorizedCode(args: ConsumePreAuthCodeArgs): IdkResult<ConsumedPreAuthCode, IdkError> =
        Err(IdkError.UNKNOWN_ERROR(message = "Pre-auth code consumption is handled by the AS token endpoint"))

    override suspend fun createAuthorizationContext(args: CreateAuthContextArgs): IdkResult<AuthorizationContextRef, IdkError> =
        Ok(AuthorizationContextRef(issuerState = args.issuerState, sessionId = args.issuerState))

    override suspend fun validateAccessToken(args: ValidateAccessTokenArgs): IdkResult<ValidatedTokenContext, IdkError> {
        try {
            val response =
                httpClient.post("$asInternalUrl/introspect") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    headers { append(HttpHeaders.Authorization, basicAuthHeader()) }
                    setBody("token=${args.accessToken}&token_type_hint=access_token&client_id=$clientId")
                }
            val body = response.bodyAsText()
            val introspection = json.decodeFromString<JsonObject>(body)

            val active = introspection["active"]?.jsonPrimitive?.content?.toBoolean() ?: false
            if (!active) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Access token is not active"))
            }

            val subject =
                introspection["sub"]?.jsonPrimitive?.content
                    ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Token introspection missing sub claim"))

            val clientId = introspection["client_id"]?.jsonPrimitive?.content ?: ""
            val scope = introspection["scope"]?.jsonPrimitive?.content

            val authDetailsArray =
                introspection["authorization_details"]?.let { ad ->
                    try {
                        ad.jsonArray
                    } catch (_: Exception) {
                        null
                    }
                }

            val credentialConfigurationIds =
                authDetailsArray?.mapNotNull { detail ->
                    detail.jsonObject["credential_configuration_id"]?.jsonPrimitive?.content
                } ?: emptyList()

            val credentialIdentifiers =
                authDetailsArray?.flatMap { detail ->
                    detail.jsonObject["credential_identifiers"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                } ?: emptyList()

            return Ok(
                ValidatedTokenContext(
                    subject = subject,
                    clientId = clientId,
                    scope = scope,
                    credentialConfigurationIds = credentialConfigurationIds,
                    credentialIdentifiers = credentialIdentifiers.ifEmpty { null },
                ),
            )
        } catch (expected: Exception) {
            return Err(IdkError.UNKNOWN_ERROR(message = "Token introspection failed: ${expected.message}"))
        }
    }

    override suspend fun augmentAsMetadata(args: AugmentAsMetadataArgs): IdkResult<JsonObject, IdkError> =
        Ok(
            buildJsonObject {
                args.baseMetadata.forEach { (k, v) -> put(k, v) }
                put("pre-authorized_grant_anonymous_access_supported", JsonPrimitive(true))
            },
        )

    private fun generateTxCode(): String {
        val bytes = CryptographyRandom.nextBytes(6)
        return bytes.joinToString("") { (it.toInt() and 0xFF).mod(10).toString() }
    }

    companion object {
        const val CONFIG_PREFIX = "oid4vci.issuer.as-bridge"
    }
}

@Serializable
data class PreAuthRegistrationRequest(
    val code: String,
    val sessionId: String,
    val credentialConfigurationIds: List<String>,
    val txCodeRequired: Boolean = false,
    val txCodeHash: String? = null,
    val issuerIdentifier: String? = null,
    val useCredentialIdentifiers: Boolean = true,
)
