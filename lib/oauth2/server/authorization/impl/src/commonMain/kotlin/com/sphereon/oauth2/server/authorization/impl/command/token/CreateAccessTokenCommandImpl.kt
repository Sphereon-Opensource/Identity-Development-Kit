/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.command.token

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.jose.jws.CreateJwsArgs
import com.sphereon.crypto.jose.jws.CreateJwsOpts
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import dev.zacsweers.metro.Named
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.AccessTokenData
import com.sphereon.oauth2.server.authorization.storage.TokenStorage
import kotlinx.datetime.Clock
import com.sphereon.oauth2.server.authorization.impl.command.putClaims
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of CreateAccessTokenCommand
 *
 * Creates JWT access tokens according to RFC 9068 (JSON Web Token Profile for OAuth 2.0 Access Tokens).
 *
 * Token structure:
 * - Header: alg, typ="at+jwt", kid
 * - Payload:
 *   - iss (issuer)
 *   - sub (subject)
 *   - aud (audience)
 *   - exp (expiration)
 *   - iat (issued at)
 *   - client_id
 *   - scope (optional)
 *   - cnf.jkt (DPoP binding, optional)
 *   - Additional claims (custom)
 *
 * The token is signed using JWS and stored in TokenStorage.
 *
 * Security considerations:
 * - Tokens MUST be short-lived (typically 1 hour or less)
 * - Tokens SHOULD include audience restriction
 * - DPoP binding SHOULD be used when available
 * - Token ID (jti) for revocation tracking
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateAccessTokenCommandImpl", exact = true)
class CreateAccessTokenCommandImpl(
    execution: SessionExecution,
    private val jwtService: JwtService,
    private val tokenStorage: TokenStorage,
    private val configProvider: OAuth2ServersConfigProvider,
    @Named("oauth2.issuerUrl") private val issuerUrl: String,
    @Named("oauth2.serverIdentifier") private val serverIdentifier: ManagedIdentifierOptsOrResult?
) : TypedServiceCommandAdapter<CreateAccessTokenArgs, StringResult>(
    commandId = CreateAccessTokenCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<CreateAccessTokenArgs>(),
    outputTypeToken = typeToken<StringResult>(),
), CreateAccessTokenCommand {

    override val commandId: String get() = CreateAccessTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateAccessTokenArgs

    override suspend fun doExecute(
        args: CreateAccessTokenArgs,
        applyDuring: (CreateAccessTokenArgs) -> CreateAccessTokenArgs
    ): IdkResult<StringResult, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(
            applied.subject, applied.clientId, applied.scope,
            applied.audience, applied.expiresInSeconds, applied.dpopJkt,
            applied.additionalClaims
        ).map { StringResult(it) }.mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        subject: String,
        clientId: String,
        scope: String?,
        audience: List<String>,
        expiresInSeconds: Int,
        dpopJkt: String?,
        additionalClaims: Map<String, Any>
    ): IdkResult<String, AuthorizationServerError> {
        return try {
            val now = Clock.System.now()
            val expiresAt = now + expiresInSeconds.seconds

            // If serverIdentifier is not configured, fall back to opaque tokens
            if (serverIdentifier == null) {
                return createOpaqueToken(
                    subject, clientId, scope, audience,
                    expiresInSeconds, dpopJkt, additionalClaims,
                    now, expiresAt
                )
            }

            // Generate JWT token ID (jti) for revocation tracking
            val jti = generateTokenId()

            // Build JWT payload according to RFC 9068
            val payload = buildJsonObject {
                // Standard claims (RFC 9068 Section 2.2)
                put("iss", issuerUrl)
                put("sub", subject)
                put("client_id", clientId)
                put("iat", now.epochSeconds)
                put("exp", expiresAt.epochSeconds)
                put("jti", jti)

                // Audience (RFC 9068 Section 2.2.3)
                if (audience.isNotEmpty()) {
                    if (audience.size == 1) {
                        put("aud", audience.first())
                    } else {
                        put("aud", buildJsonArray {
                            audience.forEach { aud -> add(JsonPrimitive(aud)) }
                        })
                    }
                }

                // Scope (RFC 9068 Section 2.2.2)
                if (!scope.isNullOrBlank()) {
                    put("scope", scope)
                }

                // DPoP binding (RFC 9449 Section 6)
                if (dpopJkt != null) {
                    put("cnf", buildJsonObject {
                        put("jkt", dpopJkt)
                    })
                }

                // Additional claims
                putClaims(additionalClaims)
            }

            // Create JWT header with typ="at+jwt" per RFC 9068 Section 2.1
            val header = buildJsonObject {
                put("typ", "at+jwt")
            }

            // Sign JWT using JwtService
            val jwsArgs = CreateJwsArgs(
                issuer = serverIdentifier,
                payload = payload.toString(),
                opts = CreateJwsOpts(
                    protectedHeader = header,
                    noIssPayloadUpdate = true  // We already added iss to payload
                )
            )

            val jwtResult = jwtService.createJwsCompact(jwsArgs)
                .mapError { error ->
                    AuthorizationServerError.ServerError(
                        details = "Failed to sign access token: ${error.message.defaultMessage}",
                        exception = error.exception
                    )
                }
                .getOrElse { return Err(it) }

            val accessToken = jwtResult.jwt

            // Create token data for storage
            val tokenData = AccessTokenData(
                accessToken = accessToken,
                tokenType = if (dpopJkt != null) "DPoP" else "Bearer",
                clientId = clientId,
                subject = subject,
                scope = scope,
                audience = audience,
                issuer = issuerUrl,
                issuedAt = now,
                expiresAt = expiresAt,
                dpopJkt = dpopJkt,
                revoked = false,
                refreshTokenId = null,  // Set by caller if refresh token is issued
                additionalData = additionalClaims
            )

            // Store token for introspection and revocation
            tokenStorage.storeAccessToken(accessToken, tokenData)
                .mapError { error ->
                    AuthorizationServerError.ServerError(
                        details = "Failed to store access token: $error",
                        exception = null
                    )
                }
                .getOrElse { return Err(it) }

            Ok(accessToken)

        } catch (e: Exception) {
            Err(
                AuthorizationServerError.ServerError(
                    details = "Access token creation failed: ${e.message}",
                    exception = e
                )
            )
        }
    }

    /**
     * Creates an opaque (non-JWT) access token
     * Used as fallback when JWT signing is not configured
     */
    private suspend fun createOpaqueToken(
        subject: String,
        clientId: String,
        scope: String?,
        audience: List<String>,
        expiresInSeconds: Int,
        dpopJkt: String?,
        additionalClaims: Map<String, Any>,
        now: kotlinx.datetime.Instant,
        expiresAt: kotlinx.datetime.Instant
    ): IdkResult<String, AuthorizationServerError> {
        // Generate cryptographically secure random token
        val accessToken = generateTokenId()

        // Create token data
        val tokenData = AccessTokenData(
            accessToken = accessToken,
            tokenType = if (dpopJkt != null) "DPoP" else "Bearer",
            clientId = clientId,
            subject = subject,
            scope = scope,
            audience = audience,
            issuer = issuerUrl,
            issuedAt = now,
            expiresAt = expiresAt,
            dpopJkt = dpopJkt,
            revoked = false,
            refreshTokenId = null,
            additionalData = additionalClaims
        )

        // Store token
        return tokenStorage.storeAccessToken(accessToken, tokenData)
            .mapError { error ->
                AuthorizationServerError.ServerError(
                    details = "Failed to store access token: $error",
                    exception = null
                )
            }
            .map { accessToken }
    }

    /**
     * Generates a cryptographically secure token ID
     * 32 bytes (256 bits) of entropy, base64url encoded
     */
    private fun generateTokenId(): String {
        val randomBytes = Random.Default.nextBytes(32)
        return randomBytes.encodeToBase64Url()
    }
}
