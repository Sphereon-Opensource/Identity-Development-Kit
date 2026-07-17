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
) : TypedServiceCommandAdapter<VerifyRefreshTokenGrantArgs, VerifiedRefreshTokenGrant, IdkError>(
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
        return executeInternal(
            applied.refreshToken,
            applied.clientId,
            applied.requestedScope,
            applied.requestedResource,
        ).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        refreshToken: String,
        clientId: String,
        requestedScope: String?,
        requestedResource: List<String>,
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

        // Verify token is not revoked. The wire shape stays `invalid_grant` per RFC 6749 §5.2;
        // the structured meta key lets the grant handler upstream tell apart reuse-detection
        // (a previously-rotated chain replayed) from other invalid_grant flavors so it can emit
        // an OAuth2AuditEventType.REFRESH_TOKEN_REUSE_DETECTED event without parsing the
        // human-readable details string.
        if (tokenData.revoked) {
            return Err(
                AuthorizationServerError.InvalidGrant(
                    details = "Refresh token has been revoked",
                    meta = mapOf(REUSE_DETECTED_META_KEY to true),
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

        if (requestedResource.any { !isSecureResourceIndicator(it) }) {
            return Err(AuthorizationServerError.InvalidTarget(resource = requestedResource.first(), reason = "resource must be a secure absolute URI without a fragment"))
        }
        if (requestedResource.isNotEmpty() && requestedResource != tokenData.resource) {
            return Err(AuthorizationServerError.InvalidGrant(details = "resource does not match refresh token grant"))
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

        // Return verified grant. OIDC Core 1.0 §12: the refresh-token grant MAY reissue an
        // id_token; downstream orchestration uses the preserved authTime/acr/amr/nonce/sid
        // surfaced here so the refreshed id_token mirrors the original authentication context.
        return Ok(
            VerifiedRefreshTokenGrant(
                subject = tokenData.subject,
                clientId = tokenData.clientId,
                scope = finalScope,
                resource = tokenData.resource,
                defaultAccessTokenAudience = tokenData.defaultAccessTokenAudience,
                dpopJkt = tokenData.dpopJkt,
                refreshTokenId = tokenData.refreshToken,
                authTime = tokenData.authTime,
                acr = tokenData.acr,
                amr = tokenData.amr,
                nonce = tokenData.nonce,
                loginSessionId = tokenData.loginSessionId,
            ),
        )
    }

    companion object {
        /**
         * Meta key set on the [AuthorizationServerError.InvalidGrant] returned when a presented
         * refresh token is rejected because its `revoked` flag is set. Set means: the chain was
         * already rotated and the consumed token is being replayed, which is the OAuth 2.1
         * RFC 6749 §10.4 reuse-detection signal. Consumers (the grant handler upstream) inspect
         * this key to emit `OAuth2AuditEventType.REFRESH_TOKEN_REUSE_DETECTED` separately from
         * the generic `invalid_grant` flow.
         */
        const val REUSE_DETECTED_META_KEY: String = "refresh_token_reuse_detected"
    }
}

private fun isSecureResourceIndicator(value: String): Boolean =
    SECURE_RESOURCE_URI.matches(value)

private val SECURE_RESOURCE_URI = Regex("^https://[a-z0-9.-]+(?::[0-9]{1,5})?(?:/[^#\\s]*)?$", RegexOption.IGNORE_CASE)
