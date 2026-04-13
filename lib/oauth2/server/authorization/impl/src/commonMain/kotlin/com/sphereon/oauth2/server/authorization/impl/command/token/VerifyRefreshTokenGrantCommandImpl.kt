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
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.VerifiedRefreshTokenGrant
import com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.TokenStorage
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyRefreshTokenGrantCommandImpl", exact = true)
class VerifyRefreshTokenGrantCommandImpl(
    execution: SessionExecution,
    private val tokenStorage: TokenStorage,
) : TypedServiceCommandAdapter<VerifyRefreshTokenGrantArgs, VerifiedRefreshTokenGrant>(
        commandId = VerifyRefreshTokenGrantCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifyRefreshTokenGrantArgs>(),
        outputTypeToken = typeToken<VerifiedRefreshTokenGrant>(),
    ),
    VerifyRefreshTokenGrantCommand {
    override val commandId: String get() = VerifyRefreshTokenGrantCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyRefreshTokenGrantArgs

    override suspend fun doExecute(
        args: VerifyRefreshTokenGrantArgs,
        applyDuring: (VerifyRefreshTokenGrantArgs) -> VerifyRefreshTokenGrantArgs,
    ): IdkResult<VerifiedRefreshTokenGrant, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.refreshToken, applied.clientId, applied.requestedScope).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        refreshToken: String,
        clientId: String,
        requestedScope: String?,
    ): IdkResult<VerifiedRefreshTokenGrant, AuthorizationServerError> {
        // Retrieve refresh token data
        val tokenData =
            tokenStorage
                .getRefreshToken(refreshToken)
                .mapError { error ->
                    AuthorizationServerError.ServerError(
                        details = "Failed to retrieve refresh token: $error",
                        exception = null,
                    )
                }.getOrElse { return Err(it) }

        // Check if token was found
        if (tokenData == null) {
            return Err(
                AuthorizationServerError.InvalidGrant(
                    details = "Invalid or expired refresh token",
                ),
            )
        }

        // Verify token is not expired (nullable for refresh tokens)
        val expiresAt = tokenData.expiresAt
        val now = Clock.System.now()
        if (expiresAt != null && expiresAt < now) {
            return Err(
                AuthorizationServerError.InvalidGrant(
                    details = "Refresh token has expired",
                ),
            )
        }

        // Verify token is not revoked
        if (tokenData.revoked) {
            return Err(
                AuthorizationServerError.InvalidGrant(
                    details = "Refresh token has been revoked",
                ),
            )
        }

        // Verify client_id matches
        if (tokenData.clientId != clientId) {
            return Err(
                AuthorizationServerError.InvalidGrant(
                    details = "Refresh token was issued to a different client",
                ),
            )
        }

        // Verify requested scope (if provided)
        if (requestedScope != null && requestedScope.isNotBlank()) {
            val originalScope = tokenData.scope
            if (originalScope == null) {
                return Err(
                    AuthorizationServerError.InvalidScope(
                        scope = requestedScope,
                    ),
                )
            }

            // Parse scopes
            val originalScopes = originalScope.split(" ").toSet()
            val requestedScopes = requestedScope.split(" ").toSet()

            // Verify requested scopes are subset of original scopes
            if (!originalScopes.containsAll(requestedScopes)) {
                return Err(
                    AuthorizationServerError.InvalidScope(
                        scope = requestedScope,
                        allowedScopes = originalScopes.toList(),
                    ),
                )
            }
        }

        // Determine final scope (use requested if provided, otherwise original)
        val finalScope =
            if (requestedScope != null && requestedScope.isNotBlank()) {
                requestedScope
            } else {
                tokenData.scope
            }

        // Return verified grant
        return Ok(
            VerifiedRefreshTokenGrant(
                subject = tokenData.subject,
                clientId = tokenData.clientId,
                scope = finalScope,
                dpopJkt = tokenData.dpopJkt,
                refreshTokenId = tokenData.refreshToken,
            ),
        )
    }
}
