/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.oauth2.common.command

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * Input for an OAuth `private_key_jwt` client assertion.
 *
 * OpenID Connect Core section 9 requires `iss` and `sub` to equal the client id, and requires
 * `aud`, `jti`, and `exp`. FAPI Security Profile 2.0 narrows `aud` to the authorization server's
 * issuer identifier. The caller therefore passes that issuer as [audience], not the token endpoint.
 */
data class PrivateKeyJwtAssertionAssemblyRequest(
    val clientId: String,
    val audience: String,
    val issuedAt: Long? = null,
    val lifetimeSeconds: Long = PrivateKeyJwtAssertionAssembly.DEFAULT_ASSERTION_LIFETIME_SECONDS,
)

data class PrivateKeyJwtSigningInput(
    val headerJson: JsonObject,
    val payloadJson: JsonObject,
    val encodedHeader: String,
    val encodedPayload: String,
    val signingInput: ByteArray,
)

/**
 * Key-custody-neutral assembly for OAuth `private_key_jwt` client assertions.
 *
 * This is OAuth protocol code: it creates the JOSE header and the RFC 7523/OpenID Connect claims,
 * but never owns or invokes a signing key. A client with local KMS custody or a wallet using a
 * WSCA/WSCD signs [PrivateKeyJwtSigningInput.signingInput] through its own custody boundary and
 * passes the raw signature to [finish].
 */
@Inject
@SingleIn(SessionScope::class)
class PrivateKeyJwtAssertionAssembly(
    private val secureRandom: SecureRandom,
) {
    suspend fun assemble(
        request: PrivateKeyJwtAssertionAssemblyRequest,
        publicJwk: Jwk,
    ): PrivateKeyJwtSigningInput {
        require(request.clientId.isNotBlank()) { "oauth_private_key_jwt_client_id_blank" }
        require(request.audience.isNotBlank()) { "oauth_private_key_jwt_audience_blank" }
        require(request.lifetimeSeconds in 1..MAX_ASSERTION_LIFETIME_SECONDS) {
            "oauth_private_key_jwt_lifetime_invalid"
        }
        val issuedAt = request.issuedAt ?: Clock.System.now().epochSeconds
        val algorithm = resolveJoseSignatureAlgorithm(publicJwk)
        require(algorithm in ALLOWED_ASYMMETRIC_ALGORITHMS) {
            "oauth_private_key_jwt_algorithm_unsupported:$algorithm"
        }
        val keyId = publicJwk.kid?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("oauth_private_key_jwt_kid_missing")
        val header =
            buildJsonObject {
                put("alg", algorithm)
                put("kid", keyId)
                put("typ", "JWT")
            }
        val payload =
            buildJsonObject {
                put("iss", request.clientId)
                put("sub", request.clientId)
                put("aud", request.audience)
                put("jti", secureRandom.newToken(lengthBytes = JTI_RANDOM_BYTES))
                put("iat", issuedAt)
                put("exp", issuedAt + request.lifetimeSeconds)
            }
        val encodedHeader = Json.encodeToString(JsonObject.serializer(), header).encodeToByteArray().encodeToBase64Url()
        val encodedPayload = Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray().encodeToBase64Url()
        return PrivateKeyJwtSigningInput(
            headerJson = header,
            payloadJson = payload,
            encodedHeader = encodedHeader,
            encodedPayload = encodedPayload,
            signingInput = "$encodedHeader.$encodedPayload".encodeToByteArray(),
        )
    }

    fun finish(
        input: PrivateKeyJwtSigningInput,
        signature: ByteArray,
    ): String = "${input.encodedHeader}.${input.encodedPayload}.${signature.encodeToBase64Url()}"

    companion object {
        const val DEFAULT_ASSERTION_LIFETIME_SECONDS: Long = 60
        const val MAX_ASSERTION_LIFETIME_SECONDS: Long = 300
        private const val JTI_RANDOM_BYTES: Int = 16
        private val ALLOWED_ASYMMETRIC_ALGORITHMS = setOf("PS256", "ES256", "EdDSA", "RS256", "ES384", "ES512")
    }
}
