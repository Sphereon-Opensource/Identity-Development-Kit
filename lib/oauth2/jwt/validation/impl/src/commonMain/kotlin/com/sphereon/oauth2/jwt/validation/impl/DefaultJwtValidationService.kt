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
 *
 */

package com.sphereon.oauth2.jwt.validation.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.jwt.validation.AccessTokenValidationOptions
import com.sphereon.oauth2.jwt.validation.IdTokenValidationOptions
import com.sphereon.oauth2.jwt.validation.IdpConfig
import com.sphereon.oauth2.jwt.validation.IdpRegistry
import com.sphereon.oauth2.jwt.validation.JwtValidationError
import com.sphereon.oauth2.jwt.validation.JwtValidationService
import com.sphereon.oauth2.jwt.validation.TokenClaims
import com.sphereon.oauth2.jwt.validation.ValidatedAccessToken
import com.sphereon.oauth2.jwt.validation.ValidatedIdToken
import com.sphereon.oauth2.server.resource.command.VerifyJwtArgs
import com.sphereon.oauth2.server.resource.command.VerifyJwtCommand
import com.sphereon.oauth2.server.resource.model.TokenPayload
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Default implementation of JwtValidationService.
 *
 * Wraps IDK's VerifyJwtCommand for JWT signature verification,
 * with support for multi-IdP environments.
 *
 * Key flow:
 * 1. Parse JWT to extract issuer (without verification)
 * 2. Look up IdP configuration based on issuer or tenant hint
 * 3. Delegate to VerifyJwtCommand for full verification
 * 4. Map result to ValidatedAccessToken
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<JwtValidationService>())
class DefaultJwtValidationService(
    private val verifyJwtCommand: VerifyJwtCommand,
    private val idpRegistry: IdpRegistry,
) : JwtValidationService {
    override suspend fun validateAccessToken(
        token: String,
        options: AccessTokenValidationOptions,
    ): IdkResult<ValidatedAccessToken, JwtValidationError> {
        // Validate token format
        val parts = token.split(".")
        if (parts.size != 3) {
            return Err(JwtValidationError.invalidFormat("JWT must have 3 parts, got ${parts.size}"))
        }

        // Find the IdP configuration to use
        val idpResult = resolveIdpConfig(token, options)
        if (idpResult is Err) {
            return idpResult
        }

        val idpConfig = (idpResult as Ok).value

        // Determine expected audience
        val expectedAudience = options.expectedAudience ?: idpConfig.audience

        // Verify the JWT using IDK's command
        val verifyResult =
            verifyJwtCommand.execute(
                VerifyJwtArgs(
                    jwt = token,
                    authorizationServer = idpConfig.issuer,
                    expectedAudience = expectedAudience,
                    jwksUri = idpConfig.jwksUri,
                ),
            )

        return verifyResult.fold(
            success = { jwtPayload ->
                // Check required scopes
                if (options.requiredScopes.isNotEmpty()) {
                    val tokenScopes = jwtPayload.scope?.split(" ")?.toSet() ?: emptySet()
                    val missingScopes = options.requiredScopes - tokenScopes
                    if (missingScopes.isNotEmpty()) {
                        return Err(JwtValidationError.missingClaim("scope: ${missingScopes.joinToString(", ")}"))
                    }
                }

                // Check required claims
                for (claim in options.requiredClaims) {
                    if (!jwtPayload.additionalClaims.containsKey(claim)) {
                        return Err(JwtValidationError.missingClaim(claim))
                    }
                }

                // Extract tenant ID from configured claim
                val tenantId = extractTenantId(jwtPayload, idpConfig)

                // Map to ValidatedAccessToken
                Ok(
                    ValidatedAccessToken(
                        subject = jwtPayload.sub,
                        issuer = jwtPayload.iss,
                        audiences = jwtPayload.aud ?: emptyList(),
                        expiresAt = jwtPayload.exp.epochSeconds,
                        issuedAt = jwtPayload.iat.epochSeconds,
                        notBefore = null, // TokenPayload.Jwt doesn't include nbf
                        scopes = jwtPayload.scope?.split(" ")?.toSet() ?: emptySet(),
                        tenantId = tenantId,
                        clientId = jwtPayload.clientId,
                        jwtId = jwtPayload.jti,
                        rawToken = token,
                        claims = buildClaims(jwtPayload),
                        idpId = idpConfig.id,
                    ),
                )
            },
            failure = { error ->
                // Map IDK error to JwtValidationError
                val errorMessage = error.message?.defaultMessage ?: "JWT verification failed"
                when {
                    errorMessage.contains("expired", ignoreCase = true) -> {
                        Err(JwtValidationError.expired(0))
                    }

                    errorMessage.contains("signature", ignoreCase = true) -> {
                        Err(JwtValidationError.signatureInvalid(idpConfig.issuer))
                    }

                    errorMessage.contains("issuer", ignoreCase = true) -> {
                        Err(JwtValidationError.untrustedIssuer(idpConfig.issuer, listOf(idpConfig.issuer)))
                    }

                    errorMessage.contains("audience", ignoreCase = true) -> {
                        Err(JwtValidationError.invalidAudience(emptyList(), expectedAudience ?: ""))
                    }

                    else -> {
                        Err(JwtValidationError.validationError(errorMessage))
                    }
                }
            },
        )
    }

    override suspend fun validateIdToken(
        token: String,
        options: IdTokenValidationOptions,
    ): IdkResult<ValidatedIdToken, JwtValidationError> {
        // Validate token format
        val parts = token.split(".")
        if (parts.size != 3) {
            return Err(JwtValidationError.invalidFormat("JWT must have 3 parts, got ${parts.size}"))
        }

        // Find the IdP configuration to use
        val accessOptions =
            AccessTokenValidationOptions(
                expectedAudience = options.expectedAudience,
                idpId = options.idpId,
                tenantHint = options.tenantHint,
            )
        val idpResult = resolveIdpConfig(token, accessOptions)
        if (idpResult is Err) {
            return Err(idpResult.error)
        }

        val idpConfig = (idpResult as Ok).value

        // Verify the JWT
        val verifyResult =
            verifyJwtCommand.execute(
                VerifyJwtArgs(
                    jwt = token,
                    authorizationServer = idpConfig.issuer,
                    expectedAudience = options.expectedAudience ?: idpConfig.audience,
                    jwksUri = idpConfig.jwksUri,
                ),
            )

        return verifyResult.fold(
            success = { jwtPayload ->
                // Validate nonce if required
                if (options.expectedNonce != null) {
                    val tokenNonce = jwtPayload.additionalClaims["nonce"]
                    if (tokenNonce != options.expectedNonce) {
                        return Err(
                            JwtValidationError.validationError(
                                "Nonce mismatch: expected ${options.expectedNonce}, got $tokenNonce",
                            ),
                        )
                    }
                }

                val tenantId = extractTenantId(jwtPayload, idpConfig)

                Ok(
                    ValidatedIdToken(
                        subject = jwtPayload.sub,
                        issuer = jwtPayload.iss,
                        audiences = jwtPayload.aud ?: emptyList(),
                        expiresAt = jwtPayload.exp.epochSeconds,
                        issuedAt = jwtPayload.iat.epochSeconds,
                        authTime = jwtPayload.additionalClaims["auth_time"]?.toDoubleOrNull()?.toLong(),
                        nonce = jwtPayload.additionalClaims["nonce"],
                        name = jwtPayload.additionalClaims["name"],
                        email = jwtPayload.additionalClaims["email"],
                        emailVerified = jwtPayload.additionalClaims["email_verified"]?.toBooleanStrictOrNull(),
                        preferredUsername = jwtPayload.additionalClaims["preferred_username"],
                        givenName = jwtPayload.additionalClaims["given_name"],
                        familyName = jwtPayload.additionalClaims["family_name"],
                        tenantId = tenantId,
                        rawToken = token,
                        claims = buildClaims(jwtPayload),
                        idpId = idpConfig.id,
                    ),
                )
            },
            failure = { error ->
                Err(
                    JwtValidationError.validationError(
                        error.message?.defaultMessage ?: "ID token verification failed",
                    ),
                )
            },
        )
    }

    override suspend fun extractClaims(token: String): IdkResult<TokenClaims, JwtValidationError> {
        val parts = token.split(".")
        if (parts.size != 3) {
            return Err(JwtValidationError.invalidFormat("JWT must have 3 parts, got ${parts.size}"))
        }

        return try {
            // Decode header and payload (base64url)
            val headerJson = decodeBase64Url(parts[0])
            val payloadJson = decodeBase64Url(parts[1])

            // Parse as JSON - simplified parsing without full JSON parsing
            // In real implementation, use kotlinx.serialization
            val issuer = extractJsonString(payloadJson, "iss")
            val subject = extractJsonString(payloadJson, "sub")
            val exp = extractJsonNumber(payloadJson, "exp")
            val iat = extractJsonNumber(payloadJson, "iat")
            val audRaw = extractJsonString(payloadJson, "aud")
            val audiences =
                if (audRaw != null) {
                    listOf(audRaw)
                } else {
                    emptyList()
                }

            Ok(
                TokenClaims(
                    header = emptyMap(), // Simplified - would parse header JSON
                    payload = emptyMap(), // Simplified - would parse payload JSON
                    issuer = issuer,
                    subject = subject,
                    audiences = audiences,
                    expiresAt = exp,
                    issuedAt = iat,
                ),
            )
        } catch (expected: Exception) {
            Err(JwtValidationError.invalidFormat("Failed to parse JWT: ${expected.message}"))
        }
    }

    private suspend fun resolveIdpConfig(
        token: String,
        options: AccessTokenValidationOptions,
    ): IdkResult<IdpConfig, JwtValidationError> {
        // Priority 1: Explicit IdP ID
        val explicitIdpId = options.idpId
        if (explicitIdpId != null) {
            return idpRegistry.getIdpById(explicitIdpId)
        }

        // Priority 2: Tenant hint
        val tenantHint = options.tenantHint
        if (tenantHint != null) {
            val tenantIdp = idpRegistry.getIdpForTenant(tenantHint)
            if (tenantIdp is Ok) {
                return tenantIdp
            }
        }

        // Priority 3: Extract issuer from token and look up
        val claims = extractClaims(token)
        if (claims is Ok) {
            val issuer = claims.value.issuer
            if (issuer != null) {
                return idpRegistry.getIdpByIssuer(issuer)
            }
        }

        // Priority 4: Default IdP
        return idpRegistry.getDefaultIdp()
    }

    private fun extractTenantId(
        jwtPayload: TokenPayload.Jwt,
        idpConfig: IdpConfig,
    ): String? {
        // Try primary tenant claim
        jwtPayload.additionalClaims[idpConfig.tenantClaim]?.let { return it }

        // Try alternative claims
        for (altClaim in idpConfig.tenantClaimAlternatives) {
            jwtPayload.additionalClaims[altClaim]?.let { return it }
        }

        // Client ID as fallback
        return jwtPayload.clientId
    }

    private fun buildClaims(jwtPayload: TokenPayload.Jwt): Map<String, JsonElement> =
        buildMap {
            put("sub", JsonPrimitive(jwtPayload.sub))
            put("iss", JsonPrimitive(jwtPayload.iss))
            jwtPayload.aud?.let { put("aud", JsonPrimitive(it.joinToString(" "))) }
            put("exp", JsonPrimitive(jwtPayload.exp.epochSeconds))
            put("iat", JsonPrimitive(jwtPayload.iat.epochSeconds))
            jwtPayload.scope?.let { put("scope", JsonPrimitive(it)) }
            jwtPayload.clientId?.let { put("client_id", JsonPrimitive(it)) }
            jwtPayload.jti?.let { put("jti", JsonPrimitive(it)) }
            jwtPayload.additionalClaims.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
        }

    private fun decodeBase64Url(input: String): String =
        try {
            input.decodeFromBase64Url().decodeToString()
        } catch (_: Exception) {
            ""
        }

    private fun extractJsonString(
        json: String,
        key: String,
    ): String? {
        val pattern = """"$key"\s*:\s*"([^"]+)""""
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractJsonNumber(
        json: String,
        key: String,
    ): Long? {
        val pattern = """"$key"\s*:\s*(\d+\.?\d*)"""
        val regex = Regex(pattern)
        return regex
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()
            ?.toLong()
    }
}
