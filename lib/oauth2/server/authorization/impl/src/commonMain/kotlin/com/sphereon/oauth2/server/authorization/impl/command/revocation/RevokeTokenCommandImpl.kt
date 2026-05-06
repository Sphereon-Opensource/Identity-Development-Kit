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

package com.sphereon.oauth2.server.authorization.impl.command.revocation

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventCategories
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.events.EventTypes
import com.sphereon.core.api.security.ConstantTime
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.server.authorization.command.RevokeTokenArgs
import com.sphereon.oauth2.server.authorization.command.RevokeTokenCommand
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

/**
 * Implementation of RevokeTokenCommand
 *
 * Revokes OAuth2 tokens according to RFC 7009.
 *
 * Per RFC 7009 Section 2.1:
 * - The authorization server responds with HTTP 200 for both successful and unsuccessful revocation
 * - Invalid tokens do not cause an error response
 * - The client MUST be authenticated
 *
 * Revocation behavior:
 * - Access token: revoke the specific token
 * - Refresh token: revoke the refresh token AND cascade to associated access tokens
 * - Unknown token: return success (per RFC 7009)
 * - Client mismatch: return success without revoking (per RFC 7009)
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("RevokeTokenCommandImpl", exact = true)
class RevokeTokenCommandImpl(
    execution: SessionExecution,
    private val tokenStorage: TokenStorage,
    private val configProvider: OAuth2ServersConfigProvider,
    private val eventService: SessionEventService? = null,
) : TypedServiceCommandAdapter<RevokeTokenArgs, Unit, IdkError>(
        commandId = RevokeTokenCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RevokeTokenArgs>(),
        outputTypeToken = typeToken<Unit>(),
    ),
    RevokeTokenCommand {
    override val commandId: String get() = RevokeTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is RevokeTokenArgs

    override suspend fun doExecute(
        args: RevokeTokenArgs,
        applyDuring: (RevokeTokenArgs) -> RevokeTokenArgs,
    ): IdkResult<Unit, IdkError> {
        val applied = applyDuring(args)
        val result =
            executeInternal(applied.token, applied.tokenTypeHint, applied.clientId)
                .mapError { IdkError.fromDTO(it) }
        emitOutcome(applied, result)
        return result
    }

    private suspend fun emitOutcome(
        args: RevokeTokenArgs,
        result: IdkResult<Unit, IdkError>,
    ) {
        val type = if (result.isOk) EventTypes.OAUTH2_TOKEN_REVOKED else EventTypes.OAUTH2_TOKEN_FAILED
        val category = if (result.isOk) EventCategories.SECURITY else EventCategories.ERROR
        val payload =
            buildJsonObject {
                put("clientId", args.clientId)
                args.tokenTypeHint?.let { put("tokenTypeHint", it) }
                if (!result.isOk) put("operation", "revoke")
            }
        val es = eventService ?: return
        es.emit(
            es
                .eventBuilder()
                .type(type)
                .subsystem(EventSubsystems.OAUTH)
                .category(category)
                .origin(RevokeTokenCommand.COMMAND_ID)
                .payload(payload)
                .build(),
        )
    }

    private suspend fun executeInternal(
        token: String,
        tokenTypeHint: String?,
        clientId: String,
    ): IdkResult<Unit, AuthorizationServerError> {
        // Check if revocation is enabled
        val serverConfig = configProvider.serverConfig
        if (!serverConfig.revocation.isEnabled) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Token revocation is not enabled on this server",
                ),
            )
        }

        // Try to find the token based on token_type_hint
        val tokenData =
            findToken(token, tokenTypeHint)
                ?: return Ok(Unit) // Unknown token -> success per RFC 7009

        when (tokenData) {
            is AccessTokenData -> {
                // Verify client ownership - if mismatch, return success per RFC 7009
                if (tokenData.clientId != clientId) {
                    return Ok(Unit)
                }
                // Revoke the access token
                tokenStorage
                    .revokeAccessToken(token)
                    .getOrElse { return Ok(Unit) } // Storage errors -> still return success
            }

            is RefreshTokenData -> {
                // Verify client ownership
                if (tokenData.clientId != clientId) {
                    return Ok(Unit)
                }
                // Revoke the refresh token
                tokenStorage
                    .revokeRefreshToken(token)
                    .getOrElse { return Ok(Unit) }

                // Cascade: revoke associated access tokens
                cascadeRevokeAccessTokens(clientId, token)
            }
        }

        return Ok(Unit)
    }

    private suspend fun findToken(
        token: String,
        tokenTypeHint: String?,
    ): Any? =
        when (tokenTypeHint) {
            "access_token" -> {
                val accessToken = tokenStorage.getAccessToken(token).getOrElse { null }
                accessToken ?: tokenStorage.getRefreshToken(token).getOrElse { null }
            }

            "refresh_token" -> {
                val refreshToken = tokenStorage.getRefreshToken(token).getOrElse { null }
                refreshToken ?: tokenStorage.getAccessToken(token).getOrElse { null }
            }

            else -> {
                val accessToken = tokenStorage.getAccessToken(token).getOrElse { null }
                accessToken ?: tokenStorage.getRefreshToken(token).getOrElse { null }
            }
        }

    /**
     * When a refresh token is revoked, cascade the revocation to any
     * access tokens that were issued from that refresh token.
     */
    private suspend fun cascadeRevokeAccessTokens(
        clientId: String,
        refreshToken: String,
    ) {
        val accessTokens =
            tokenStorage
                .findAccessTokensByClient(clientId)
                .getOrElse { emptyList() }

        for (accessTokenData in accessTokens) {
            // Defense in depth: refreshTokenId is already client-scoped via the prior
            // findAccessTokensByClient call, so a timing oracle here can only leak which of
            // the caller's own tokens match — which they already know. Still cheap to do CT.
            val storedRefreshId = accessTokenData.refreshTokenId
            if (storedRefreshId != null && ConstantTime.equalsCT(storedRefreshId, refreshToken) && !accessTokenData.revoked) {
                tokenStorage.revokeAccessToken(accessTokenData.accessToken)
            }
        }
    }
}
