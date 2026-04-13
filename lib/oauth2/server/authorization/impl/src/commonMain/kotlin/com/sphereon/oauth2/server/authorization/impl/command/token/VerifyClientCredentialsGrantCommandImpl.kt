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
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.command.VerifiedClientCredentialsGrant
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of VerifyClientCredentialsGrantCommand
 *
 * Verifies client_credentials grant requests according to RFC 6749 Section 4.4.
 *
 * The client_credentials grant type is used when the client is requesting access
 * to protected resources under its control (not on behalf of a user).
 *
 * Verification steps:
 * 1. Verify client is registered
 * 2. Verify client is authorized to use client_credentials grant
 * 3. Verify requested scope (if provided) is allowed for this client
 * 4. Return verified grant data
 *
 * Security considerations:
 * - Client authentication is REQUIRED
 * - Only confidential clients should be allowed to use this grant
 * - No user (subject) is involved - token is issued to the client itself
 * - Scope should be restricted based on client registration
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyClientCredentialsGrantCommandImpl", exact = true)
class VerifyClientCredentialsGrantCommandImpl(
    execution: SessionExecution,
    private val clientRegistry: ClientRegistry
) : TypedServiceCommandAdapter<VerifyClientCredentialsGrantArgs, VerifiedClientCredentialsGrant>(
    commandId = VerifyClientCredentialsGrantCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<VerifyClientCredentialsGrantArgs>(),
    outputTypeToken = typeToken<VerifiedClientCredentialsGrant>(),
), VerifyClientCredentialsGrantCommand {

    override val commandId: String get() = VerifyClientCredentialsGrantCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyClientCredentialsGrantArgs

    override suspend fun doExecute(
        args: VerifyClientCredentialsGrantArgs,
        applyDuring: (VerifyClientCredentialsGrantArgs) -> VerifyClientCredentialsGrantArgs
    ): IdkResult<VerifiedClientCredentialsGrant, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.clientId, applied.requestedScope).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        clientId: String,
        requestedScope: String?
    ): IdkResult<VerifiedClientCredentialsGrant, AuthorizationServerError> {
        // Retrieve client registration
        val client = clientRegistry.getClient(clientId).getOrElse { error ->
            return Err(
                AuthorizationServerError.ServerError(
                    details = "Failed to retrieve client registration: $error",
                    exception = null
                )
            )
        }

        if (client == null) {
            return Err(
                AuthorizationServerError.InvalidClient(
                    details = "Client not found",
                    exception = null
                )
            )
        }

        // Verify client is authorized to use client_credentials grant
        if (GrantType.CLIENT_CREDENTIALS !in client.grantTypes) {
            return Err(
                AuthorizationServerError.UnauthorizedClient(
                    clientId = clientId
                )
            )
        }

        // Verify scope if provided
        // If client has allowedScopes configured, validate that all requested scopes are permitted
        val grantedScope = if (requestedScope != null && requestedScope.isNotBlank()) {
            val requestedScopes = requestedScope.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
            val allowedScopes = client.allowedScopes
            if (allowedScopes != null) {
                val disallowed = requestedScopes.filter { it !in allowedScopes }
                if (disallowed.isNotEmpty()) {
                    return Err(
                        AuthorizationServerError.InvalidScope(
                            scope = disallowed.joinToString(" "),
                            allowedScopes = allowedScopes
                        )
                    )
                }
            }
            requestedScope
        } else {
            requestedScope
        }

        // Return verified grant
        // Note: subject is the client_id itself (no user involved)
        return Ok(
            VerifiedClientCredentialsGrant(
                subject = clientId,  // In client_credentials, the client IS the subject
                clientId = clientId,
                scope = grantedScope
            )
        )
    }
}
