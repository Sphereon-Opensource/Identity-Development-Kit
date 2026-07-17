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
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.RefreshTokenData
import com.sphereon.oauth2.server.authorization.storage.TokenStorage
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of CreateRefreshTokenCommand
 *
 * Creates refresh tokens according to RFC 6749 Section 1.5.
 *
 * Refresh tokens are credentials used to obtain access tokens when the current
 * access token becomes invalid or expires.
 *
 * Token structure:
 * - Opaque random string (not JWT)
 * - Cryptographically secure random generation
 * - Sufficient entropy (256 bits recommended)
 *
 * The token is stored in TokenStorage with associated metadata.
 *
 * Security considerations:
 * - Refresh tokens MUST be confidential (transmitted securely)
 * - Tokens SHOULD be long-lived but not infinite (typically days to months)
 * - Token rotation SHOULD be implemented (new refresh token on each use)
 * - Tokens MUST be bound to the client they were issued to
 * - DPoP binding SHOULD be preserved if present
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateRefreshTokenCommandImpl", exact = true)
class CreateRefreshTokenCommandImpl(
    execution: SessionExecution,
    private val tokenStorage: TokenStorage,
    private val configProvider: OAuth2ServersConfigProvider,
    private val secureRandom: SecureRandom,
) : TypedServiceCommandAdapter<CreateRefreshTokenArgs, StringResult, IdkError>(
        commandId = CreateRefreshTokenCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateRefreshTokenArgs>(),
        outputTypeToken = typeToken<StringResult>(),
    ),
    CreateRefreshTokenCommand {
    override val commandId: String get() = CreateRefreshTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateRefreshTokenArgs

    override suspend fun doExecute(
        args: CreateRefreshTokenArgs,
        applyDuring: (CreateRefreshTokenArgs) -> CreateRefreshTokenArgs,
    ): IdkResult<StringResult, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied)
            .map { StringResult(it) }
            .mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(args: CreateRefreshTokenArgs): IdkResult<String, AuthorizationServerError> {
        val now = Clock.System.now()
        val effectiveExpiresIn = args.expiresInSeconds ?: configProvider.serverConfig.refreshTokenLifetimeSeconds
        val expiresAt = now + effectiveExpiresIn.seconds

        // Generate cryptographically secure random refresh token
        // 256 bits = 32 bytes, encoded as base64url
        val refreshToken = generateSecureToken()

        // Persist OIDC fields alongside the token so OIDC Core 1.0 §12 id_token reissue on
        // refresh sees the original authentication context (auth_time/acr/amr/nonce/sid).
        val tokenData =
            RefreshTokenData(
                refreshToken = refreshToken,
                clientId = args.clientId,
                subject = args.subject,
                scope = args.scope,
                resource = args.resource,
                defaultAccessTokenAudience = args.defaultAccessTokenAudience,
                issuedAt = now,
                expiresAt = expiresAt,
                revoked = false,
                used = false,
                dpopJkt = args.dpopJkt,
                authTime = args.authTime,
                acr = args.acr,
                amr = args.amr,
                nonce = args.nonce,
                loginSessionId = args.loginSessionId,
                additionalData = emptyMap(),
            )

        // Store token
        tokenStorage.storeRefreshToken(refreshToken, tokenData).getOrElse { error ->
            return Err(
                AuthorizationServerError.ServerError(
                    details = "Failed to store refresh token: $error",
                    exception = null,
                ),
            )
        }

        return Ok(refreshToken)
    }

    /**
     * Generate a cryptographically secure random refresh token.
     * 32 bytes (256 bits) of entropy, base64url encoded.
     */
    private suspend fun generateSecureToken(): String = secureRandom.newToken()
}
