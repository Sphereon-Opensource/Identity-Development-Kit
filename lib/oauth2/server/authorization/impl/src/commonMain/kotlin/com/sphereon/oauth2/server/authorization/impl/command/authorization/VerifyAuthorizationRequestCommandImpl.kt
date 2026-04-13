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

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.ResponseType
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestData
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationRequest
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.model.ClientType
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of VerifyAuthorizationRequestCommand
 *
 * Verifies OAuth2 authorization endpoint requests according to RFC 6749 Section 4.1.2.
 *
 * Verification steps:
 * 1. Retrieve client registration
 * 2. Verify client is authorized to use authorization_code grant
 * 3. Verify redirect_uri matches registered URIs
 * 4. Verify PKCE is used if required for client
 * 5. Validate requested scope
 * 6. Return verified request data
 *
 * Security considerations:
 * - Public clients MUST use PKCE (RFC 8252)
 * - redirect_uri MUST be validated against registered URIs (RFC 6749 Section 3.1.2.3)
 * - Exact string matching for redirect_uri validation
 * - Scope validation depends on server policy
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyAuthorizationRequestCommandImpl", exact = true)
class VerifyAuthorizationRequestCommandImpl(
    execution: SessionExecution,
    private val clientRegistry: ClientRegistry,
    private val serversConfigProvider: OAuth2ServersConfigProvider,
) : TypedServiceCommandAdapter<AuthorizationRequestData, VerifiedAuthorizationRequest>(
        commandId = VerifyAuthorizationRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<AuthorizationRequestData>(),
        outputTypeToken = typeToken<VerifiedAuthorizationRequest>(),
    ),
    VerifyAuthorizationRequestCommand {
    override val commandId: String get() = VerifyAuthorizationRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is AuthorizationRequestData

    override suspend fun doExecute(
        args: AuthorizationRequestData,
        applyDuring: (AuthorizationRequestData) -> AuthorizationRequestData,
    ): IdkResult<VerifiedAuthorizationRequest, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(request: AuthorizationRequestData): IdkResult<VerifiedAuthorizationRequest, AuthorizationServerError> {
        // Retrieve client registration (fall back to public client policy if not registered)
        val client =
            clientRegistry
                .getClient(request.clientId)
                .mapError { error ->
                    AuthorizationServerError.ServerError(
                        details = "Failed to retrieve client registration: $error",
                        exception = null,
                    )
                }.getOrElse { return Err(it) }
                ?: resolvePublicClient(request.clientId)
                ?: return Err(
                    AuthorizationServerError.UnauthorizedClient(
                        clientId = request.clientId,
                    ),
                )

        // Verify client is authorized to use authorization_code grant
        if (GrantType.AUTHORIZATION_CODE !in client.grantTypes) {
            return Err(
                AuthorizationServerError.UnauthorizedClient(
                    clientId = request.clientId,
                ),
            )
        }

        // Verify redirect_uri (RFC 6749 Section 3.1.2.3)
        val redirectUri = request.redirectUri

        if (client.redirectUris.isNotEmpty()) {
            if (redirectUri.isNullOrBlank()) {
                if (client.redirectUris.size != 1) {
                    return Err(
                        AuthorizationServerError.InvalidRequest(
                            details = "redirect_uri is required when client has multiple registered redirect URIs",
                        ),
                    )
                }
            } else {
                if (redirectUri !in client.redirectUris) {
                    return Err(
                        AuthorizationServerError.InvalidRequest(
                            details = "redirect_uri does not match any registered redirect URI for this client",
                        ),
                    )
                }
            }
        } else if (redirectUri.isNullOrBlank()) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "redirect_uri is required",
                ),
            )
        }

        // Determine final redirect_uri
        val finalRedirectUri = redirectUri ?: client.redirectUris.first()

        // Verify PKCE is used if required (RFC 8252)
        // Public clients MUST use PKCE
        if (client.requirePkce && request.codeChallenge == null) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "PKCE (code_challenge) is required for this client",
                ),
            )
        }

        // RFC 8252: Public clients MUST use PKCE
        if (client.clientType == ClientType.PUBLIC && request.codeChallenge == null) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Public clients MUST use PKCE (RFC 8252)",
                ),
            )
        }

        // Verify PAR is used if required (RFC 9126)
        if (client.requirePushedAuthorizationRequests && request.requestUri == null) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Pushed Authorization Requests (PAR) is required for this client",
                ),
            )
        }

        // Validate requested scope
        // If client has allowedScopes configured, validate that all requested scopes are permitted
        val requestedScope = request.scope
        val grantedScopes =
            if (requestedScope != null && requestedScope.isNotBlank()) {
                val requestedScopes = requestedScope.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
                val allowedScopes = client.allowedScopes
                if (allowedScopes != null) {
                    val disallowed = requestedScopes.filter { it !in allowedScopes }
                    if (disallowed.isNotEmpty()) {
                        return Err(
                            AuthorizationServerError.InvalidScope(
                                scope = disallowed.joinToString(" "),
                                allowedScopes = allowedScopes,
                            ),
                        )
                    }
                }
                requestedScopes
            } else {
                emptyList()
            }

        // Return verified request
        return Ok(
            VerifiedAuthorizationRequest(
                request = request,
                clientId = request.clientId,
                redirectUri = finalRedirectUri,
                grantedScopes = grantedScopes,
                pkceRequired = client.requirePkce || client.clientType == ClientType.PUBLIC,
                parRequired = client.requirePushedAuthorizationRequests,
            ),
        )
    }

    private fun resolvePublicClient(clientId: String): ClientRegistration? {
        val pc = serversConfigProvider.getDefaultServer().publicClients
        if (!pc.allowAny && clientId !in pc.allowedClientIds) {
            return null
        }

        return ClientRegistration(
            clientId = clientId,
            clientType = ClientType.PUBLIC,
            grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
            responseTypes = listOf(ResponseType.CODE),
            redirectUris = emptyList(),
            tokenEndpointAuthMethod = ClientAuthenticationMethod.NONE,
            requirePkce = true,
        )
    }
}
