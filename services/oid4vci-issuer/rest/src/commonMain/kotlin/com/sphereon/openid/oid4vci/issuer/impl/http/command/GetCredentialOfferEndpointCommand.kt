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

package com.sphereon.openid.oid4vci.issuer.impl.http.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.jsonResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrorResponse
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.openid.oid4vci.issuer.store.CredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.store.CredentialOfferStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionStatus
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString

/**
 * Endpoint command for retrieving a credential offer by ID.
 *
 * GET /credentials/offers/{offerId}
 */
interface GetCredentialOfferEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.protocol.offer"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/credentials/offers/{offerId}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getCredentialOffer",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer", "credential-offers"),
                summary = "Get a credential offer by ID",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetCredentialOfferEndpointCommand>())
class GetCredentialOfferEndpointCommandImpl(
    execution: SessionExecution,
    private val offerStore: CredentialOfferStore,
    private val sessionStore: CredentialIssuanceSessionStore,
) : HttpEndpointCommandAdapter(
        id = GetCredentialOfferEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetCredentialOfferEndpointCommand.ENDPOINT,
    ),
    GetCredentialOfferEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams("/credentials/offers/{offerId}")
        val offerId = req.requirePathParam("offerId").getOrElse { return Err(it) }

        val offer =
            offerStore.get(offerId).getOrElse { error -> return Err(error) }
                ?: return Ok(
                    // Offer-fetch is implementation-specific in OID4VCI 1.0; follow standard HTTP
                    // semantics (404 for not-found) rather than the §8.3.1 credential-request
                    // error envelope, which only applies to POST /credential.
                    jsonResponse(
                        statusCode = 404,
                        body =
                            protocolJson.encodeToString(
                                Oid4vciErrorResponse.serializer(),
                                Oid4vciErrorResponse(
                                    error = Oid4vciErrors.UNKNOWN_CREDENTIAL_CONFIGURATION,
                                    errorDescription = "Credential offer not found or expired",
                                ),
                            ),
                    ),
                )

        updateSessionToOfferReceived(offerId)
        return Ok(jsonResponse(200, protocolJson.encodeToString(offer)))
    }

    private suspend fun updateSessionToOfferReceived(offerId: String) {
        val sessionId = offerStore.getSessionId(offerId).getOrElse { return } ?: return
        val session = sessionStore.get(sessionId).getOrElse { return } ?: return
        if (session.status == IssuanceSessionStatus.OFFER_CREATED) {
            sessionStore.update(session.copy(status = IssuanceSessionStatus.OFFER_RECEIVED))
        }
    }
}
