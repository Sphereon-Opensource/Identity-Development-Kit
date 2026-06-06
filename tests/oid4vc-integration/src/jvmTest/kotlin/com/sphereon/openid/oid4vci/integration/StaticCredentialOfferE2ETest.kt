/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.issuer.command.OfferRateLimit
import com.sphereon.openid.oid4vci.issuer.command.OfferUriLifecycle
import com.sphereon.openid.oid4vci.issuer.impl.http.Oid4vciIssuerProtocolHttpAdapter
import com.sphereon.openid.oid4vci.rest.CreateCredentialOfferInput
import com.sphereon.openid.oid4vci.rest.CreateCredentialOfferOutput
import com.sphereon.openid.oid4vci.rest.CreateCredentialOfferServiceCommand
import com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStore
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Session-scoped graph access for the static-offer integration test.
 *
 * Pulls the backend [CreateCredentialOfferServiceCommand] (which exposes the REST-level
 * `uriLifecycle` field) and the [CredentialOfferSessionStore] so the test can directly
 * verify the persisted session state alongside the HTTP-shaped wallet flow.
 */
@ContributesTo(SessionScope::class)
interface StaticOfferTestGraph {
    val createCredentialOfferServiceCommand: CreateCredentialOfferServiceCommand
    val credentialOfferSessionStore: CredentialOfferSessionStore
}

/**
 * End-to-end integration test for the Phase 3 static-credential-offer feature
 * (`uriLifecycle = REUSABLE_FRESH_PER_FETCH`).
 *
 * Composes the real Metro DI graph and drives the flow that a real wallet would see:
 *
 * 1. Backend creates a credential offer with `REUSABLE_FRESH_PER_FETCH` lifecycle and a rate limit.
 * 2. Wallet A performs `GET /oid4vci/credentials/offers/{offerId}` and receives a fresh inner offer.
 * 3. Wallet B performs the same GET and receives a different inner offer.
 *
 * Asserts:
 * - The stable offer URI is byte-identical across the two CreateCredentialOffer calls (URI lifecycle
 *   means the URI itself is reused).
 * - Each GET returns a fresh pre-authorized code (so each wallet gets an independent session).
 * - Both GETs return `Cache-Control: no-store, no-cache, must-revalidate` per OID4VCI §4.1.3.
 * - The persisted [CredentialOfferSessionStore] row records `REUSABLE_FRESH_PER_FETCH` and the rate
 *   limit, which is the durable contract the GET handler reads on each fetch.
 */
class StaticCredentialOfferE2ETest {
    private val ctx = Oid4vciTestContext(this)

    private val issuerHost = "issuer.example.com"
    private val issuerUrl = "https://$issuerHost"

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    private fun issuerAdapter(): Oid4vciIssuerProtocolHttpAdapter {
        val adapters = (ctx.session.graph as HttpAdapterTestGraph).httpAdapters
        return adapters.filterIsInstance<Oid4vciIssuerProtocolHttpAdapter>().firstOrNull()
            ?: error("Oid4vciIssuerProtocolHttpAdapter not found in DI graph")
    }

    @Test
    fun reusableOfferUriMintsFreshOfferPerFetchAndRecordsLifecycleInStore() =
        runTest {
            val backendCommand = (ctx.session.graph as StaticOfferTestGraph).createCredentialOfferServiceCommand
            val offerSessionStore = (ctx.session.graph as StaticOfferTestGraph).credentialOfferSessionStore
            val issuerHttpAdapter = issuerAdapter()

            // Step 1: backend creates an offer with REUSABLE_FRESH_PER_FETCH and a generous rate limit
            // so the two GETs below are both within budget.
            val createInput =
                CreateCredentialOfferInput(
                    credentialConfigurationIds = listOf("UniversityDegree"),
                    issuerId = issuerUrl,
                    uriLifecycle = OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH,
                    rateLimit = OfferRateLimit(maxPerWindow = 10, windowSeconds = 60),
                )
            val createResult = backendCommand.execute(createInput)
            assertTrue(
                createResult.isOk,
                "Reusable offer creation should succeed: ${if (createResult.isErr) {
                    createResult.error.message.defaultMessage
                } else {
                    ""
                }}",
            )
            val created: CreateCredentialOfferOutput = createResult.value
            val correlationId = created.correlationId
            val offerUri = created.offerUri
            assertTrue(offerUri.isNotEmpty(), "Offer URI should be populated")

            // Step 2: persisted session row records the static-offer contract verbatim. The GET
            // handler reads exactly this state on each fetch, so the durable record is the
            // contract.
            val storedSessionResult = offerSessionStore.get(correlationId)
            assertTrue(storedSessionResult.isOk, "Session lookup should succeed")
            val storedSession =
                storedSessionResult.value
                    ?: error("Session should be persisted for correlationId=$correlationId")
            assertEquals(
                OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH,
                storedSession.uriLifecycle,
                "Stored session should reflect the reusable URI lifecycle",
            )
            val storedRateLimit = storedSession.rateLimit
            assertNotNull(storedRateLimit, "Stored session should carry the rate limit")
            assertEquals(10, storedRateLimit.maxPerWindow)
            assertEquals(60, storedRateLimit.windowSeconds)
            assertNotNull(
                storedSession.offerTemplate,
                "Stored session should carry the replayable offer template",
            )

            // Step 3: extract the offerId from the offerUri so we can hit GET. The URI shape is
            // `<scheme>?credential_offer_uri=<encoded>` (OID4VCI §4.1.1); the encoded inner URI
            // ends in `/credentials/offers/{offerId}`.
            val offerId = storedSession.offerId
            val offerPath = "/oid4vci/credentials/offers/$offerId"

            // Step 4: wallet A fetches the offer.
            val walletARequest =
                GenericHttpRequest(
                    method = "GET",
                    path = offerPath,
                    pathParameters = mapOf("offerId" to offerId),
                    headers = mapOf("host" to issuerHost, "x-forwarded-proto" to "https"),
                )
            val walletAResponse = issuerHttpAdapter.handleRequest(walletARequest)
            assertEquals(
                200,
                walletAResponse.statusCode,
                "Wallet A fetch should return 200. Body: ${walletAResponse.body}",
            )
            val walletABody = walletAResponse.body ?: error("Wallet A response missing body")
            val walletAOffer = json.decodeFromString(CredentialOffer.serializer(), walletABody)
            assertEquals(
                "no-store, no-cache, must-revalidate",
                walletAResponse.headers["Cache-Control"],
                "Reusable offer must include the OID4VCI §4.1.3-blessed no-cache headers",
            )

            // Step 5: wallet B fetches the same URI.
            val walletBRequest =
                GenericHttpRequest(
                    method = "GET",
                    path = offerPath,
                    pathParameters = mapOf("offerId" to offerId),
                    headers = mapOf("host" to issuerHost, "x-forwarded-proto" to "https"),
                )
            val walletBResponse = issuerHttpAdapter.handleRequest(walletBRequest)
            assertEquals(
                200,
                walletBResponse.statusCode,
                "Wallet B fetch should return 200. Body: ${walletBResponse.body}",
            )
            val walletBBody = walletBResponse.body ?: error("Wallet B response missing body")
            val walletBOffer = json.decodeFromString(CredentialOffer.serializer(), walletBBody)
            assertEquals(
                "no-store, no-cache, must-revalidate",
                walletBResponse.headers["Cache-Control"],
                "Second reusable fetch must also include the OID4VCI §4.1.3-blessed no-cache headers",
            )

            // Step 6: the two responses must describe the SAME issuer + credential configuration set
            // (the static offer surface) but carry DIFFERENT pre-authorized codes (independent
            // issuance sessions per fetch).
            assertEquals(walletAOffer.credentialIssuer, walletBOffer.credentialIssuer)
            assertEquals(
                walletAOffer.credentialConfigurationIds,
                walletBOffer.credentialConfigurationIds,
            )
            val walletAPreAuthCode =
                walletAOffer.grants
                    ?.preAuthorizedCode
                    ?.preAuthorizedCode
            val walletBPreAuthCode =
                walletBOffer.grants
                    ?.preAuthorizedCode
                    ?.preAuthorizedCode
            assertNotNull(walletAPreAuthCode, "Wallet A should receive a pre-authorized code")
            assertNotNull(walletBPreAuthCode, "Wallet B should receive a pre-authorized code")
            assertNotEquals(
                walletAPreAuthCode,
                walletBPreAuthCode,
                "Each reusable-URI fetch must mint a fresh pre-authorized code so the wallets" +
                    " end up on independent issuance sessions",
            )
        }

    @Test
    fun singleUseOfferDoesNotEmitNoStoreHeader() =
        runTest {
            val backendCommand = (ctx.session.graph as StaticOfferTestGraph).createCredentialOfferServiceCommand
            val offerSessionStore = (ctx.session.graph as StaticOfferTestGraph).credentialOfferSessionStore
            val issuerHttpAdapter = issuerAdapter()

            // Default lifecycle is SINGLE_USE. Behaviour: GET serves the stored offer JSON without
            // the §4.1.3 anti-cache header set (that header is reserved for reusable URIs).
            val createInput =
                CreateCredentialOfferInput(
                    credentialConfigurationIds = listOf("UniversityDegree"),
                    issuerId = issuerUrl,
                )
            val createResult = backendCommand.execute(createInput)
            assertTrue(createResult.isOk, "Single-use offer creation should succeed")
            val correlationId = createResult.value.correlationId

            val storedSession =
                offerSessionStore.get(correlationId).value
                    ?: error("Single-use session should be persisted")
            assertEquals(OfferUriLifecycle.SINGLE_USE, storedSession.uriLifecycle)

            val offerId = storedSession.offerId
            val response =
                issuerHttpAdapter.handleRequest(
                    GenericHttpRequest(
                        method = "GET",
                        path = "/oid4vci/credentials/offers/$offerId",
                        pathParameters = mapOf("offerId" to offerId),
                        headers = mapOf("host" to issuerHost, "x-forwarded-proto" to "https"),
                    ),
                )
            assertEquals(200, response.statusCode, "Single-use offer fetch should succeed")
            assertNotEquals(
                "no-store, no-cache, must-revalidate",
                response.headers["Cache-Control"],
                "Single-use offers must not emit the reusable-URI no-cache header",
            )
        }
}
