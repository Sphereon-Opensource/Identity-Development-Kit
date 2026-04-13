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

package com.sphereon.oauth2.server.authorization.impl.command.introspection

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventCategories
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.events.EventTypes
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenArgs
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.AccessTokenData
import com.sphereon.oauth2.server.authorization.model.RefreshTokenData
import com.sphereon.oauth2.server.authorization.storage.TokenStorage
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

/**
 * Implementation of IntrospectTokenCommand
 *
 * Introspects OAuth2 tokens according to RFC 7662.
 *
 * This command determines whether a token is currently active and returns
 * metadata about the token if it is active.
 *
 * A token is considered "active" if:
 * - It exists in the token storage
 * - It has not expired
 * - It has not been revoked
 * - The introspecting client is authorized to introspect this token
 *
 * Response for active token (RFC 7662 Section 2.2):
 * ```json
 * {
 *   "active": true,
 *   "scope": "read write",
 *   "client_id": "client123",
 *   "username": "user@example.com",
 *   "token_type": "Bearer",
 *   "exp": 1419356238,
 *   "iat": 1419350238,
 *   "nbf": 1419350238,
 *   "sub": "user123",
 *   "aud": "https://protected.example.net/resource",
 *   "iss": "https://server.example.com/",
 *   "jti": "token-id-123"
 * }
 * ```
 *
 * Response for inactive token:
 * ```json
 * {
 *   "active": false
 * }
 * ```
 *
 * Security considerations:
 * - MUST verify the introspecting client is authorized to introspect tokens
 * - MAY restrict which tokens a client can introspect (e.g., only their own)
 * - SHOULD NOT leak information about why a token is inactive
 * - Response may contain sensitive information, use with trusted clients only
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthServerIntrospectTokenCommandImpl", exact = true)
class AuthServerIntrospectTokenCommandImpl(
    execution: SessionExecution,
    private val tokenStorage: TokenStorage,
    private val configProvider: OAuth2ServersConfigProvider,
    private val eventService: SessionEventService? = null,
) : TypedServiceCommandAdapter<IntrospectTokenArgs, TokenIntrospectionResponse>(
        commandId = IntrospectTokenCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<IntrospectTokenArgs>(),
        outputTypeToken = typeToken<TokenIntrospectionResponse>(),
    ),
    IntrospectTokenCommand {
    override val commandId: String get() = IntrospectTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is IntrospectTokenArgs

    override suspend fun doExecute(
        args: IntrospectTokenArgs,
        applyDuring: (IntrospectTokenArgs) -> IntrospectTokenArgs,
    ): IdkResult<TokenIntrospectionResponse, IdkError> {
        val applied = applyDuring(args)
        val result =
            executeInternal(applied.token, applied.tokenTypeHint)
                .mapError { IdkError.fromDTO(it) }
        emitOutcome(applied, result)
        return result
    }

    private suspend fun emitOutcome(
        args: IntrospectTokenArgs,
        result: IdkResult<TokenIntrospectionResponse, IdkError>,
    ) {
        val type = if (result.isOk) EventTypes.OAUTH2_TOKEN_INTROSPECTED else EventTypes.OAUTH2_TOKEN_FAILED
        val category = if (result.isOk) EventCategories.SECURITY else EventCategories.ERROR
        val payload =
            buildJsonObject {
                args.tokenTypeHint?.let { put("tokenTypeHint", it) }
                val response = result.getOrNull()
                if (response != null) {
                    put("active", response.active)
                } else {
                    put("operation", "introspect")
                }
            }
        val es = eventService ?: return
        es.emit(
            es
                .eventBuilder()
                .type(type)
                .subsystem(EventSubsystems.OAUTH)
                .category(category)
                .origin(IntrospectTokenCommand.COMMAND_ID)
                .payload(payload)
                .build(),
        )
    }

    private suspend fun executeInternal(
        token: String,
        tokenTypeHint: String?,
    ): IdkResult<TokenIntrospectionResponse, AuthorizationServerError> {
        // Check if introspection is enabled
        if (!configProvider.serverConfig.introspection.isEnabled) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Token introspection is not enabled on this server",
                ),
            )
        }

        val now = Clock.System.now()

        // Try to find the token based on token_type_hint
        // If hint is provided, check that type first for efficiency
        val tokenData =
            when (tokenTypeHint) {
                "access_token" -> {
                    // Try access token first
                    val accessToken =
                        tokenStorage
                            .getAccessToken(token)
                            .mapError { error ->
                                AuthorizationServerError.ServerError(
                                    details = "Failed to retrieve access token: $error",
                                    exception = null,
                                )
                            }.getOrElse { return Ok(TokenIntrospectionResponse(active = false)) }

                    if (accessToken != null) {
                        accessToken
                    } else {
                        // Fallback to refresh token
                        tokenStorage
                            .getRefreshToken(token)
                            .mapError { error ->
                                AuthorizationServerError.ServerError(
                                    details = "Failed to retrieve refresh token: $error",
                                    exception = null,
                                )
                            }.getOrElse { return Ok(TokenIntrospectionResponse(active = false)) }
                    }
                }

                "refresh_token" -> {
                    // Try refresh token first
                    val refreshToken =
                        tokenStorage
                            .getRefreshToken(token)
                            .mapError { error ->
                                AuthorizationServerError.ServerError(
                                    details = "Failed to retrieve refresh token: $error",
                                    exception = null,
                                )
                            }.getOrElse { return Ok(TokenIntrospectionResponse(active = false)) }

                    if (refreshToken != null) {
                        refreshToken
                    } else {
                        // Fallback to access token
                        tokenStorage
                            .getAccessToken(token)
                            .mapError { error ->
                                AuthorizationServerError.ServerError(
                                    details = "Failed to retrieve access token: $error",
                                    exception = null,
                                )
                            }.getOrElse { return Ok(TokenIntrospectionResponse(active = false)) }
                    }
                }

                else -> {
                    // No hint, try access token first, then refresh token
                    val accessToken =
                        tokenStorage
                            .getAccessToken(token)
                            .mapError { error ->
                                AuthorizationServerError.ServerError(
                                    details = "Failed to retrieve access token: $error",
                                    exception = null,
                                )
                            }.getOrElse { return Ok(TokenIntrospectionResponse(active = false)) }

                    if (accessToken != null) {
                        accessToken
                    } else {
                        tokenStorage
                            .getRefreshToken(token)
                            .mapError { error ->
                                AuthorizationServerError.ServerError(
                                    details = "Failed to retrieve refresh token: $error",
                                    exception = null,
                                )
                            }.getOrElse { return Ok(TokenIntrospectionResponse(active = false)) }
                    }
                }
            }

        // If token not found, return inactive
        if (tokenData == null) {
            return Ok(TokenIntrospectionResponse(active = false))
        }

        // Handle based on token type
        return when (tokenData) {
            is AccessTokenData -> {
                // Check if token is expired
                if (tokenData.expiresAt < now) {
                    return Ok(TokenIntrospectionResponse(active = false))
                }

                // Check if token is revoked
                if (tokenData.revoked) {
                    return Ok(TokenIntrospectionResponse(active = false))
                }

                // RFC 7662 Section 2.1: the AS determines whether the introspecting
                // client is authorized. Resource servers (e.g., credential issuers)
                // introspect tokens they did not issue — matching client_id would be wrong.
                // TODO: implement proper introspection authorization policy

                // Token is active - build introspection response
                Ok(
                    TokenIntrospectionResponse(
                        active = true,
                        scope = tokenData.scope,
                        clientId = tokenData.clientId,
                        username = tokenData.subject,
                        tokenType = tokenData.tokenType,
                        exp = tokenData.expiresAt.epochSeconds,
                        iat = tokenData.issuedAt.epochSeconds,
                        nbf = tokenData.issuedAt.epochSeconds,
                        sub = tokenData.subject,
                        aud = tokenData.audience.ifEmpty { null },
                        iss = tokenData.issuer,
                        jti = null,
                    ),
                )
            }

            is RefreshTokenData -> {
                // Check if token is expired (nullable for refresh tokens)
                val expiresAt = tokenData.expiresAt
                if (expiresAt != null && expiresAt < now) {
                    return Ok(TokenIntrospectionResponse(active = false))
                }

                // Check if token is revoked
                if (tokenData.revoked) {
                    return Ok(TokenIntrospectionResponse(active = false))
                }

                // RFC 7662: introspection authorization — see access token branch comment

                // Token is active - build introspection response
                Ok(
                    TokenIntrospectionResponse(
                        active = true,
                        scope = tokenData.scope,
                        clientId = tokenData.clientId,
                        username = tokenData.subject,
                        tokenType =
                            if (tokenData.dpopJkt != null) {
                                "DPoP"
                            } else {
                                "Bearer"
                            },
                        exp = tokenData.expiresAt?.epochSeconds,
                        iat = tokenData.issuedAt.epochSeconds,
                        nbf = tokenData.issuedAt.epochSeconds,
                        sub = tokenData.subject,
                        aud = null,
                        iss = null,
                        jti = null,
                    ),
                )
            }

            else -> {
                // Unknown token type
                Ok(TokenIntrospectionResponse(active = false))
            }
        }
    }
}
