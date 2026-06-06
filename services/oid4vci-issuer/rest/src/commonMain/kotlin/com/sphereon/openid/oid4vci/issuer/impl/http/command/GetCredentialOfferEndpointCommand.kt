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
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrorResponse
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferCommand
import com.sphereon.openid.oid4vci.issuer.command.OfferRateLimiter
import com.sphereon.openid.oid4vci.issuer.command.OfferUriLifecycle
import com.sphereon.openid.oid4vci.issuer.store.CredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.store.CredentialOfferStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionStatus
import com.sphereon.openid.oid4vci.rest.CredentialOfferSession
import com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString

/**
 * Endpoint command for retrieving a credential offer by ID.
 *
 * GET /credentials/offers/{offerId}
 *
 * Behaviour depends on the offer's [OfferUriLifecycle]:
 * - [OfferUriLifecycle.SINGLE_USE] (default, and legacy offers without a session): serves the
 *   stored offer JSON unchanged.
 * - [OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH]: enforces the offer's rate limit and mints a
 *   fresh offer on each fetch, returned with `Cache-Control: no-store, no-cache, must-revalidate`.
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
    private val offerSessionStore: CredentialOfferSessionStore,
    private val offerRateLimiter: OfferRateLimiter,
    private val createCredentialOfferCommand: CreateCredentialOfferCommand,
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

        // A legacy offer with no REST session, or a session without reusable semantics, keeps the
        // single-use behaviour: serve the stored offer JSON exactly as before.
        val offerSession = offerSessionStore.getByOfferId(offerId).getOrElse { error -> return Err(error) }
        if (offerSession == null || offerSession.uriLifecycle == OfferUriLifecycle.SINGLE_USE) {
            updateSessionToOfferReceived(offerId)
            return Ok(jsonResponse(200, protocolJson.encodeToString(offer)))
        }

        return serveReusableOffer(offerId, offerSession)
    }

    private suspend fun serveReusableOffer(
        offerId: String,
        offerSession: CredentialOfferSession,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val rateLimit =
            offerSession.rateLimit
                ?: return Err(
                    IdkError.INVALID_STATE(
                        message = "Reusable offer $offerId has no rate limit configured",
                    ),
                )

        val withinLimit = offerRateLimiter.tryAcquire(offerId, rateLimit).getOrElse { error -> return Err(error) }
        if (!withinLimit) {
            return Ok(
                GenericHttpResponse(
                    statusCode = 429,
                    headers = REUSABLE_OFFER_HEADERS,
                    body =
                        protocolJson.encodeToString(
                            Oid4vciErrorResponse.serializer(),
                            Oid4vciErrorResponse(
                                error = Oid4vciErrors.INVALID_REQUEST,
                                errorDescription = "Offer fetch rate limit exceeded",
                            ),
                        ),
                ),
            )
        }

        val freshOffer = mintFreshOffer(offerSession).getOrElse { error -> return Err(error) }

        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers = REUSABLE_OFFER_HEADERS,
                body = protocolJson.encodeToString(freshOffer),
            ),
        )
    }

    /**
     * Mints a fresh [CredentialOffer] for a [OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH] URI.
     *
     * Rebuilds [CreateCredentialOfferArgs] from the session's replayable `offerTemplate`
     * and re-invokes the issuer-level [CreateCredentialOfferCommand]. That command registers a
     * fresh pre-authorized code with the AS bridge, creates a fresh issuance session and (when a
     * pipeline is configured) a fresh pipeline session, returning a brand-new [CredentialOffer].
     * The stable offer URI / offer id and this [CredentialOfferSession] row are untouched: only
     * the inner protocol content is fresh per fetch.
     *
     * The reusable-URI fields ([CredentialOfferSession.uriLifecycle],
     * [CredentialOfferSession.rateLimit], [CredentialOfferSession.initialLookupKeys]) are replayed
     * from the session itself; the rest come from the template.
     */
    private suspend fun mintFreshOffer(offerSession: CredentialOfferSession): IdkResult<CredentialOffer, IdkError> {
        val template =
            offerSession.offerTemplate
                ?: return Err(
                    IdkError.INVALID_STATE(
                        message =
                            "Reusable offer ${offerSession.offerId} has no replay template on its " +
                                "CredentialOfferSession; cannot mint a fresh offer",
                    ),
                )

        val args =
            CreateCredentialOfferArgs(
                issuerId = template.issuerId,
                credentialConfigurationIds = template.credentialConfigurationIds,
                preAuthorizedCodeGrant = template.preAuthorizedCodeGrant,
                authorizationCodeGrant = template.authorizationCodeGrant,
                txCodeRequired = template.txCodeRequired,
                preSeededAttributes = template.preSeededAttributes,
                offerTtlSeconds = template.offerTtlSeconds,
                scheme = template.scheme,
                uriLifecycle = offerSession.uriLifecycle,
                initialLookupKeys = offerSession.initialLookupKeys,
                rateLimit = offerSession.rateLimit,
            )

        val created = createCredentialOfferCommand.execute(args).getOrElse { error -> return Err(error) }
        return Ok(created.offer)
    }

    private suspend fun updateSessionToOfferReceived(offerId: String) {
        val sessionId = offerStore.getSessionId(offerId).getOrElse { return } ?: return
        val session = sessionStore.get(sessionId).getOrElse { return } ?: return
        if (session.status == IssuanceSessionStatus.OFFER_CREATED) {
            sessionStore.update(session.copy(status = IssuanceSessionStatus.OFFER_RECEIVED))
        }
    }

    private companion object {
        val REUSABLE_OFFER_HEADERS =
            mapOf(
                "Content-Type" to "application/json",
                "Cache-Control" to "no-store, no-cache, must-revalidate",
            )
    }
}
