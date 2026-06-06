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

package com.sphereon.oauth2.server.authorization.impl.command.token

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventCategories
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.events.EventTypes
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.command.putClaims
import com.sphereon.oauth2.server.authorization.model.AccessTokenData
import com.sphereon.oauth2.server.authorization.signing.AsServerSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.storage.TokenStorage
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
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
    private val secureRandom: SecureRandom,
    private val configProvider: OAuth2ServersConfigProvider,
    private val signingIdentifierResolver: AsServerSigningIdentifierResolver,
    private val eventService: SessionEventService? = null,
) : TypedServiceCommandAdapter<CreateAccessTokenArgs, StringResult, IdkError>(
        commandId = CreateAccessTokenCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateAccessTokenArgs>(),
        outputTypeToken = typeToken<StringResult>(),
    ),
    CreateAccessTokenCommand {
    override val commandId: String get() = CreateAccessTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateAccessTokenArgs

    override suspend fun doExecute(
        args: CreateAccessTokenArgs,
        applyDuring: (CreateAccessTokenArgs) -> CreateAccessTokenArgs,
    ): IdkResult<StringResult, IdkError> {
        val applied = applyDuring(args)
        val issuerUrl =
            configProvider.serverConfig.issuer
                ?: applied.baseUrlOverride
        if (issuerUrl == null) {
            val failure: IdkResult<StringResult, IdkError> =
                Err(
                    IdkError.fromDTO(
                        AuthorizationServerError.ServerError(
                            details =
                                "OAuth2 server has no issuer configured and no request-time baseUrl override; " +
                                    "set oauth2.servers.<id>.issuer or ensure the request carries Host + X-Forwarded-Proto headers",
                        ),
                    ),
                )
            emitOutcome(applied, failure)
            return failure
        }

        // RFC 9068 access tokens carry a tenant_id custom claim sourced from the issuing
        // SessionExecution so resource servers can resolve tenant from the bearer token via
        // the OidcTenantResolver pipeline. Skip when the AS request is anonymous (no real
        // tenant context bound) — the resource server's tenant resolver will fall through.
        val sessionTenantId =
            runCatching { execution.sessionContext.context.tenant.tenantId }
                .getOrNull()
                ?.takeIf { it.isNotBlank() && it != "anonymous" }
        val mergedClaims: Map<String, Any> =
            if (sessionTenantId != null && "tenant_id" !in applied.additionalClaims) {
                applied.additionalClaims + ("tenant_id" to sessionTenantId)
            } else {
                applied.additionalClaims
            }

        val result =
            executeInternal(
                applied.subject,
                applied.clientId,
                applied.scope,
                applied.audience,
                applied.expiresInSeconds,
                applied.dpopJkt,
                applied.certificateThumbprintS256,
                mergedClaims,
                issuerUrl,
            ).map { StringResult(it) }.mapError { IdkError.fromDTO(it) }
        emitOutcome(applied, result)
        return result
    }

    private suspend fun emitOutcome(
        args: CreateAccessTokenArgs,
        result: IdkResult<StringResult, IdkError>,
    ) {
        val type = if (result.isOk) EventTypes.OAUTH2_TOKEN_ISSUED else EventTypes.OAUTH2_TOKEN_FAILED
        val category = if (result.isOk) EventCategories.SECURITY else EventCategories.ERROR
        val payload =
            buildJsonObject {
                put("clientId", args.clientId)
                args.scope?.let { put("scope", it) }
                put("audienceCount", args.audience.size)
                put("expiresInSeconds", args.expiresInSeconds)
            }
        val es = eventService ?: return
        es.emit(
            es
                .eventBuilder()
                .type(type)
                .subsystem(EventSubsystems.OAUTH)
                .category(category)
                .origin(CreateAccessTokenCommand.COMMAND_ID)
                .payload(payload)
                .build(),
        )
    }

    private suspend fun executeInternal(
        subject: String,
        clientId: String,
        scope: String?,
        audience: List<String>,
        expiresInSeconds: Int,
        dpopJkt: String?,
        certificateThumbprintS256: String?,
        additionalClaims: Map<String, Any>,
        issuerUrl: String,
    ): IdkResult<String, AuthorizationServerError> {
        val serverIdentifier = signingIdentifierResolver.resolveSigningIdentifier()
        return try {
            val now = Clock.System.now()
            val expiresAt = now + expiresInSeconds.seconds

            // If serverIdentifier is not configured, fall back to opaque tokens
            if (serverIdentifier == null) {
                return createOpaqueToken(
                    subject,
                    clientId,
                    scope,
                    audience,
                    dpopJkt,
                    certificateThumbprintS256,
                    additionalClaims,
                    now,
                    expiresAt,
                    issuerUrl,
                )
            }

            // Generate JWT token ID (jti) for revocation tracking
            val jti = generateTokenId()

            // Build JWT payload according to RFC 9068
            val payload =
                buildJsonObject {
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
                            put(
                                "aud",
                                buildJsonArray {
                                    audience.forEach { aud -> add(JsonPrimitive(aud)) }
                                },
                            )
                        }
                    }

                    // Scope (RFC 9068 Section 2.2.2)
                    if (!scope.isNullOrBlank()) {
                        put("scope", scope)
                    }

                    // DPoP binding (RFC 9449 §6) and / or RFC 8705 §3.1 cert binding combine
                    // additively in the cnf claim. Skip the claim entirely when neither binding
                    // applies so the access token shape stays unchanged for plain Bearer flows.
                    if (dpopJkt != null || certificateThumbprintS256 != null) {
                        put(
                            "cnf",
                            buildJsonObject {
                                if (dpopJkt != null) {
                                    put("jkt", dpopJkt)
                                }
                                if (certificateThumbprintS256 != null) {
                                    put("x5t#S256", certificateThumbprintS256)
                                }
                            },
                        )
                    }

                    // Additional claims. Filter `oidc.*`-namespaced internal entries (e.g. the
                    // §5.5 `claims` request-parameter wishlist threaded through to /userinfo)
                    // out of the JWT payload — they belong on the stored token's metadata only,
                    // not in the at+jwt body where every RP that introspects can read them.
                    putClaims(additionalClaims.filterKeys { !it.startsWith("oidc.") })
                }

            // Create JWT header with typ="at+jwt" per RFC 9068 Section 2.1
            val header =
                buildJsonObject {
                    put("typ", "at+jwt")
                }

            // Sign JWT using JwtService
            val jwsArgs =
                CreateJwsArgs(
                    issuer = serverIdentifier,
                    payload = payload.toString(),
                    opts =
                        CreateJwsOpts(
                            protectedHeader = header,
                            noIssPayloadUpdate = true, // We already added iss to payload
                        ),
                )

            val jwtResult =
                jwtService
                    .createJwsCompact(jwsArgs)
                    .mapError { error ->
                        AuthorizationServerError.ServerError(
                            details = "Failed to sign access token: ${error.message.defaultMessage}",
                            exception = error.exception,
                        )
                    }.getOrElse { return Err(it) }

            val accessToken = jwtResult.jwt

            // Create token data for storage
            val tokenData =
                AccessTokenData(
                    accessToken = accessToken,
                    tokenType =
                        if (dpopJkt != null) {
                            "DPoP"
                        } else {
                            "Bearer"
                        },
                    clientId = clientId,
                    subject = subject,
                    scope = scope,
                    audience = audience,
                    issuer = issuerUrl,
                    issuedAt = now,
                    expiresAt = expiresAt,
                    dpopJkt = dpopJkt,
                    certificateThumbprintS256 = certificateThumbprintS256,
                    revoked = false,
                    refreshTokenId = null, // Set by caller if refresh token is issued
                    additionalData = additionalClaims,
                )

            // Store token for introspection and revocation
            tokenStorage
                .storeAccessToken(accessToken, tokenData)
                .mapError { error ->
                    AuthorizationServerError.ServerError(
                        details = "Failed to store access token: $error",
                        exception = null,
                    )
                }.getOrElse { return Err(it) }

            Ok(accessToken)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.ServerError(
                    details = "Access token creation failed: ${expected.message}",
                    exception = expected,
                ),
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
        dpopJkt: String?,
        certificateThumbprintS256: String?,
        additionalClaims: Map<String, Any>,
        now: kotlin.time.Instant,
        expiresAt: kotlin.time.Instant,
        issuerUrl: String,
    ): IdkResult<String, AuthorizationServerError> {
        // Generate cryptographically secure random token
        val accessToken = generateTokenId()

        // Create token data
        val tokenData =
            AccessTokenData(
                accessToken = accessToken,
                tokenType =
                    if (dpopJkt != null) {
                        "DPoP"
                    } else {
                        "Bearer"
                    },
                clientId = clientId,
                subject = subject,
                scope = scope,
                audience = audience,
                issuer = issuerUrl,
                issuedAt = now,
                expiresAt = expiresAt,
                dpopJkt = dpopJkt,
                certificateThumbprintS256 = certificateThumbprintS256,
                revoked = false,
                refreshTokenId = null,
                additionalData = additionalClaims,
            )

        // Store token
        return tokenStorage
            .storeAccessToken(accessToken, tokenData)
            .mapError { error ->
                AuthorizationServerError.ServerError(
                    details = "Failed to store access token: $error",
                    exception = null,
                )
            }.map { accessToken }
    }

    /**
     * Generates a cryptographically secure token ID (jti).
     * 32 bytes (256 bits) of entropy, base64url encoded.
     */
    private suspend fun generateTokenId(): String = secureRandom.newToken()
}
