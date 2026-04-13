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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of CreateTokenResponseCommand
 *
 * Creates OAuth2 token endpoint responses according to RFC 6749 Section 5.1.
 *
 * Response format (JSON):
 * ```json
 * {
 *   "access_token": "ACCESS_TOKEN",
 *   "token_type": "Bearer",
 *   "expires_in": 3600,
 *   "refresh_token": "REFRESH_TOKEN",  // Optional
 *   "scope": "read write",              // Optional
 *   "c_nonce": "NONCE",                 // Optional (OpenID4VCI)
 *   "c_nonce_expires_in": 86400         // Optional (OpenID4VCI)
 * }
 * ```
 *
 * DPoP considerations:
 * - token_type MUST be "DPoP" when DPoP binding is used
 * - token_type MUST be "Bearer" otherwise
 *
 * This is a simple command that builds the response structure.
 * All validation and token generation should be done before calling this command.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateTokenResponseCommandImpl", exact = true)
class CreateTokenResponseCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<CreateTokenResponseArgs, TokenResponse>(
    commandId = CreateTokenResponseCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<CreateTokenResponseArgs>(),
    outputTypeToken = typeToken<TokenResponse>(),
), CreateTokenResponseCommand {

    override val commandId: String get() = CreateTokenResponseCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateTokenResponseArgs

    override suspend fun doExecute(
        args: CreateTokenResponseArgs,
        applyDuring: (CreateTokenResponseArgs) -> CreateTokenResponseArgs
    ): IdkResult<TokenResponse, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        args: CreateTokenResponseArgs
    ): IdkResult<TokenResponse, AuthorizationServerError> {
        // Build token response
        val response = TokenResponse(
            accessToken = args.accessToken,
            tokenType = args.tokenType,
            expiresIn = args.expiresIn,
            refreshToken = args.refreshToken,
            scope = args.scope,
            idToken = args.idToken,
            cNonce = args.cNonce,
            cNonceExpiresIn = args.cNonceExpiresIn,
            issuedTokenType = args.issuedTokenType
        )

        return Ok(response)
    }
}
