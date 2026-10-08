/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

// Role-neutral OAuth REST capability. Executable server startup remains in services-oauth2-as-rest.
package com.sphereon.oauth2.server.authorization.impl.http.command.login

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.WebAuthnLoginConfig
import com.sphereon.oauth2.server.authorization.command.login.LoginWebAuthnAssertionBeginHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.ResponseCategory
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.http.withSecurityHeaders
import com.sphereon.oauth2.server.authorization.provider.BeginWebAuthnAssertionChallenge
import com.sphereon.oauth2.server.authorization.provider.WebAuthnAssertionChallengeProvider
import com.sphereon.oauth2.server.authorization.provider.WebAuthnAssertionChallengeOptions
import com.sphereon.oauth2.server.authorization.storage.PendingAuthorizationSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(LoginWebAuthnAssertionBeginHttpEndpointCommand.COMMAND_ID)
class LoginWebAuthnAssertionBeginHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val pendingAuthorizationSessionStore: PendingAuthorizationSessionStore,
    private val configProvider: OAuth2ServersConfigProvider,
    private val challengeProvider: Provider<WebAuthnAssertionChallengeProvider>? = null,
) : HttpEndpointCommandAdapter(
        id = LoginWebAuthnAssertionBeginHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = LoginWebAuthnAssertionBeginHttpEndpointCommand.ENDPOINT,
    ),
    LoginWebAuthnAssertionBeginHttpEndpointCommand {
    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val contentType = MediaType.parse(request.contentType)
        if (contentType == null || !contentType.matches(MediaType.ApplicationJson)) {
            return Ok(oauth2ErrorResponse(400, "invalid_request", "POST /login/webauthn/assertion/begin requires JSON", json))
        }
        val body =
            try {
                json.decodeFromString<BeginLoginWebAuthnAssertionHttpRequest>(request.body ?: "")
            } catch (_: IllegalArgumentException) {
                return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing or invalid request body", json))
            }
        if (body.sessionId.isBlank() || body.origin.isBlank()) {
            return Ok(oauth2ErrorResponse(400, "invalid_request", "sessionId and origin are required", json))
        }
        val pending =
            pendingAuthorizationSessionStore.findById(body.sessionId)
                .getOrElse { return Ok(oauth2ErrorResponse(400, "invalid_request", "Unknown or expired login session", json)) }
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Unknown or expired login session", json))
        val applicationId =
            pending.applicationId
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "WebAuthn login requires an application binding", json))
        val config =
            configProvider.serverConfig.webAuthn
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing WebAuthn login configuration", json))
        val invalid = config.invalidReason()
        if (!config.enabled || invalid != null) {
            return Ok(oauth2ErrorResponse(400, "invalid_request", invalid ?: "WebAuthn login is not enabled", json))
        }
        if (body.origin !in config.allowedOrigins) {
            return Ok(oauth2ErrorResponse(400, "invalid_request", "WebAuthn origin mismatch", json))
        }
        val provider =
            challengeProvider?.invoke()
                ?: return Ok(oauth2ErrorResponse(503, "temporarily_unavailable", "WebAuthn assertion challenge provider is unavailable", json))
        val options =
            provider.beginAssertion(
                BeginWebAuthnAssertionChallenge(
                    sessionId = body.sessionId,
                    applicationId = applicationId,
                    tenantId = body.tenantId?.takeIf { it.isNotBlank() },
                    identityId = body.identityId?.takeIf { it.isNotBlank() },
                    origin = body.origin,
                    rpId = config.rpId!!.trim(),
                    allowedOrigins = config.allowedOrigins,
                    attestationPolicy = config.attestationPolicy,
                    userVerification = config.userVerification,
                    allowedTransports = config.allowedTransports,
                    backupStatePolicy = config.backupStatePolicy,
                    challengeTtlSeconds = config.challengeTtlSeconds,
                    level3PrfEnabled = config.level3PrfEnabled,
                    credentialId = body.credentialId?.takeIf { it.isNotBlank() },
                ),
            ).getOrElse {
                return Ok(oauth2ErrorResponse(400, "invalid_request", it.description, json))
            }
        return Ok(
            GenericHttpResponse(
                statusCode = 201,
                headers = mapOf("Content-Type" to "application/json", "Cache-Control" to "no-store"),
                body = json.encodeToString(options),
            ).withSecurityHeaders(ResponseCategory.REST),
        )
    }

    private fun WebAuthnLoginConfig.invalidReason(): String? {
        val rpId = rpId?.trim()?.takeIf(String::isNotEmpty) ?: return "Missing WebAuthn RP ID"
        if (rpId.startsWith("http://") || rpId.startsWith("https://") || rpId.contains('/')) return "Invalid WebAuthn RP ID"
        if (allowedOrigins.isEmpty() || allowedOrigins.any { it.isBlank() }) return "Missing WebAuthn allowed origins"
        if (allowedOrigins.any { !it.startsWith("https://") && !it.startsWith("http://localhost") && !it.startsWith("http://127.0.0.1") }) {
            return "Invalid WebAuthn allowed origins"
        }
        if (attestationPolicy !in setOf("none", "indirect", "direct", "enterprise")) return "Invalid WebAuthn attestation policy"
        if (userVerification !in setOf("required", "preferred", "discouraged")) return "Invalid WebAuthn user verification policy"
        if (backupStatePolicy !in setOf("allow-any", "require-backup-eligible", "require-backed-up", "forbid-backed-up")) {
            return "Invalid WebAuthn backup-state policy"
        }
        if (challengeTtlSeconds <= 0) return "Invalid WebAuthn challenge TTL"
        return null
    }
}

@Serializable
data class BeginLoginWebAuthnAssertionHttpRequest(
    val sessionId: String,
    val tenantId: String? = null,
    val identityId: String? = null,
    val origin: String,
    val credentialId: String? = null,
)
