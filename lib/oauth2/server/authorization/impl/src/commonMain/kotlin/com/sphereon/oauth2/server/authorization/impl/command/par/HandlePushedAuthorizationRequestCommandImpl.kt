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

package com.sphereon.oauth2.server.authorization.impl.command.par

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.command.headerIgnoreCase
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand
import com.sphereon.oauth2.common.model.VerifyDpopProofOptions
import com.sphereon.oauth2.server.authorization.command.ClientAuthenticationEndpoint
import com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseArgs
import com.sphereon.oauth2.server.authorization.command.ParsePushedAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.PushedAuthorizationResponse
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.VerifyPushedAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.par.HandlePushedAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.par.HandlePushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.command.extractClientAuthentication
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of [HandlePushedAuthorizationRequestCommand]. Body lifted verbatim from
 * `OAuth2Handlers.handlePushedAuthorizationRequest`.
 *
 * RFC 9126: Pushed Authorization Requests (PAR). Allows clients to push authorization request
 * parameters to the AS via a direct HTTP POST before redirecting the user.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandlePushedAuthorizationRequestCommand>())
class HandlePushedAuthorizationRequestCommandImpl(
    execution: SessionExecution,
    private val authorizationServerService: AuthorizationServerService,
    private val verifyDpopProofCommand: VerifyDpopProofCommand,
) : TypedServiceCommandAdapter<HandlePushedAuthorizationRequestArgs, PushedAuthorizationResponse, IdkError>(
        commandId = HandlePushedAuthorizationRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandlePushedAuthorizationRequestArgs>(),
        outputTypeToken = typeToken<PushedAuthorizationResponse>(),
    ),
    HandlePushedAuthorizationRequestCommand {
    override val commandId: String get() = HandlePushedAuthorizationRequestCommand.COMMAND_ID

    private val commands get() = authorizationServerService.commands

    override suspend fun supports(args: Any): Boolean = args is HandlePushedAuthorizationRequestArgs

    override suspend fun doExecute(
        args: HandlePushedAuthorizationRequestArgs,
        applyDuring: (HandlePushedAuthorizationRequestArgs) -> HandlePushedAuthorizationRequestArgs,
    ): IdkResult<PushedAuthorizationResponse, IdkError> {
        val applied = applyDuring(args)
        val requestBody = applied.requestBody
        val requestHeaders = applied.requestHeaders

        // RFC 9126 §2.1 references the same client-authentication rules as RFC 6749 token
        // requests: the AS MUST authenticate the client before processing PAR. Use the shared
        // case-insensitive header extractor so canonicalized header names from intermediaries
        // (e.g. Caddy/Go's `OAuth-Client-Attestation-Pop`) match.
        val extracted =
            extractClientAuthentication(requestBody, requestHeaders)
                .getOrElse { error -> return Err(IdkError.fromDTO(error)) }
        val clientAuth = extracted.clientAuthentication
        val resolvedClientId =
            extracted.clientId
                ?: return Err(IdkError.UNAUTHORIZED_ERROR(message = "client_id is required at /par"))

        commands.verifyClientAuthentication
            .execute(
                VerifyClientAuthenticationArgs(
                    clientAuthentication = clientAuth,
                    clientId = resolvedClientId,
                    tokenEndpointUrl = applied.parEndpointUrl ?: "",
                    endpoint = ClientAuthenticationEndpoint.PAR,
                ),
            ).getOrElse { error -> return Err(error) }

        // RFC 9449 §10.1 (`dpop_jkt` authorization request parameter): "If the request
        // includes the dpop_jkt authorization request parameter and a DPoP HTTP Header, the
        // values of the dpop_jkt parameter and the JWK thumbprint of the DPoP proof's JWK
        // MUST be the same. ... If they are not the same, the authorization server MUST refuse
        // the request with an invalid_request error." Verifying the DPoP proof here also
        // catches malformed proofs early instead of letting them surface at /token.
        val dpopJktFromBody = requestBody["dpop_jkt"]?.firstOrNull()
        val dpopHeader = applied.requestHeaders.headerIgnoreCase("DPoP")
        val resolvedDpopJkt: String? =
            if (dpopHeader != null) {
                val parEndpointUrl =
                    applied.parEndpointUrl
                        ?: return Err(
                            IdkError.fromDTO(
                                AuthorizationServerError.ServerError(
                                    details = "PAR endpoint URL not propagated; cannot validate DPoP proof htu binding",
                                ),
                            ),
                        )
                val verifyResult =
                    verifyDpopProofCommand
                        .execute(
                            VerifyDpopProofOptions(
                                dpopProof = dpopHeader,
                                httpMethod = "POST",
                                httpUrl = parEndpointUrl,
                            ),
                        ).getOrElse { error ->
                            return Err(
                                IdkError.fromDTO(
                                    AuthorizationServerError.InvalidRequest(
                                        details = "Invalid DPoP proof on PAR: ${error.message.defaultMessage}",
                                    ),
                                ),
                            )
                        }
                if (dpopJktFromBody != null && dpopJktFromBody != verifyResult.jwkThumbprint) {
                    return Err(
                        IdkError.fromDTO(
                            AuthorizationServerError.InvalidRequest(
                                details =
                                    "dpop_jkt parameter '$dpopJktFromBody' does not match the DPoP proof's JWK " +
                                        "thumbprint '${verifyResult.jwkThumbprint}' (RFC 9449 §10.1)",
                            ),
                        ),
                    )
                }
                // RFC 9449 §10.1: derive the binding from the proof itself when the wallet did
                // not explicitly send `dpop_jkt`. The eventual auth code carries this jkt so a
                // /token request with a different DPoP key fails with `invalid_grant`.
                verifyResult.jwkThumbprint
            } else {
                dpopJktFromBody
            }

        // Persist the resolved binding back onto the body so the parser writes it onto the
        // stored AuthorizationRequestData. Single-value list matches the rest of the body shape.
        val parserBody =
            if (resolvedDpopJkt != null && resolvedDpopJkt != dpopJktFromBody) {
                requestBody + ("dpop_jkt" to listOf(resolvedDpopJkt))
            } else {
                requestBody
            }

        // Parse PAR request. The HTTP shell propagates the request-derived base URL so the
        // parser can validate JAR `aud` (RFC 9101 §10.2) when the AS leaves `issuer` unset and
        // resolves outbound URLs from request headers per tenant / virtual host.
        val authRequest =
            commands.parsePushedAuthorizationRequest
                .execute(
                    ParsePushedAuthorizationRequestArgs(
                        requestBody = parserBody,
                        clientAuthentication = clientAuth,
                        baseUrlOverride = applied.baseUrlOverride,
                    ),
                ).getOrElse { error -> return Err(error) }

        // Verify PAR request
        val verified =
            commands.verifyPushedAuthorizationRequest
                .execute(
                    VerifyPushedAuthorizationRequestArgs(authRequest, authRequest.clientId),
                ).getOrElse { error -> return Err(error) }

        // Create request_uri
        val requestUriData =
            commands.createRequestUri
                .execute(verified)
                .getOrElse { error -> return Err(error) }

        // Create PAR response
        return commands.createPushedAuthorizationResponse.execute(
            CreatePushedAuthorizationResponseArgs(
                requestUri = requestUriData.requestUri,
                expiresIn = requestUriData.expiresIn,
            ),
        )
    }
}
