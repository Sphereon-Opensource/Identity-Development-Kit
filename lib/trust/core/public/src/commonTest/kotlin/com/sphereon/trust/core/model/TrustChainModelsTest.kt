/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.model

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustChainModelsTest {
    @Test
    fun aFederationChainArrivesOrderedFromLeafToAnchor() {
        val leaf = "https://leaf.example"
        val intermediate = "https://intermediate.example"
        val anchor = "https://anchor.example"
        val chain =
            TrustChain.fromEntityStatementEntries(
                listOf(leaf, intermediate, anchor),
                TrustChainLinks.VERIFIED,
            )!!

        assertEquals(3, chain.hops.size)
        assertEquals(TrustChainHopPosition.LEAF, chain.hops.first().position)
        assertEquals(TrustChainHopPosition.ANCHOR, chain.hops.last().position)
        assertEquals(leaf, chain.hops[0].identifier)
        assertEquals(intermediate, chain.hops[1].identifier)
        assertEquals(anchor, chain.hops[2].identifier)
        assertEquals(intermediate, chain.hops[0].assertedBy)
        assertEquals(anchor, chain.hops[1].assertedBy)
        assertNull(chain.hops[2].assertedBy)
        assertEquals(TrustChainLinks.VERIFIED, chain.links)
    }

    @Test
    fun aFederationJwtChainUsesSubNotTheRawToken() {
        val leaf = "https://rp.example"
        val anchor = "https://ta.example"
        val chain =
            TrustChain.fromEntityStatementEntries(
                listOf(entityStatementJwt(sub = leaf, iss = anchor), entityStatementJwt(sub = anchor, iss = anchor)),
                TrustChainLinks.VERIFIED,
            )!!

        assertEquals(listOf(leaf, anchor), chain.hops.map { it.identifier })
        assertEquals(TrustChainHopPosition.LEAF, chain.hops.first().position)
        assertEquals(TrustChainHopPosition.ANCHOR, chain.hops.last().position)
        assertEquals(anchor, chain.hops.first().assertedBy)
    }

    @Test
    fun aMechanismThatResolvesNoChainLeavesItAbsentNotEmptyButPresent() {
        assertNull(TrustChain.fromEntityStatementEntries(emptyList(), TrustChainLinks.VERIFIED))
        assertNull(
            TrustValidationResult(
                trusted = false,
                status = TrustStatus.UNTRUSTED,
            ).trustChain,
        )
    }

    @Test
    fun presentWithZeroHopsCannotBeConstructed() {
        assertFailsWith<IllegalArgumentException> {
            TrustChain(hops = emptyList(), links = TrustChainLinks.VERIFIED)
        }
    }

    @Test
    fun anUnadmittedAnchorIsNotReportedAsABrokenChain() {
        val chain =
            TrustChain.fromEntityStatementEntries(
                listOf("https://leaf.example", "https://foreign-anchor.example"),
                TrustChainLinks.VERIFIED,
            )!!
        val result =
            TrustValidationResult(
                trusted = true,
                status = TrustStatus.TRUSTED,
                trustChain = chain,
                domainAdmission =
                    TrustDomainAdmission.admit(
                        chain = chain,
                        posture = TrustDomainPosture.CUSTOM,
                        assignedAnchorIds = listOf("https://our-anchor.example"),
                    ),
            )

        assertEquals(TrustChainLinks.VERIFIED, result.trustChain!!.links, "the cryptography was fine")
        assertEquals(TrustDomainAdmissionOutcome.NOT_ADMITTED, result.domainAdmission!!.outcome)
        assertNull(result.domainAdmission!!.admittingDomain)
    }

    @Test
    fun trustAllAcceptanceCannotCarryANamedAdmittingDomain() {
        val admission =
            TrustDomainAdmission.admit(
                chain = null,
                posture = TrustDomainPosture.TRUST_ALL,
                assignedAnchorIds = emptyList(),
            )
        assertEquals(TrustDomainPosture.TRUST_ALL, admission.posture)
        assertEquals(TrustDomainAdmissionOutcome.ADMITTED_TRUST_ALL, admission.outcome)
        assertNull(admission.admittingDomain)
        assertFailsWith<IllegalArgumentException> {
            TrustDomainAdmission(
                posture = TrustDomainPosture.TRUST_ALL,
                outcome = TrustDomainAdmissionOutcome.ADMITTED_TRUST_ALL,
                admittingDomain = "https://pretend-authority.example",
            )
        }
    }

    @Test
    fun failClosedIsNotInferredFromAnEmptyAssignedListWhenPostureIsTrustAll() {
        val admission =
            TrustDomainAdmission.admit(
                chain = null,
                posture = TrustDomainPosture.TRUST_ALL,
                assignedAnchorIds = emptyList(),
            )
        assertEquals(TrustDomainAdmissionOutcome.ADMITTED_TRUST_ALL, admission.outcome)
    }

    @Test
    fun aBrokenLinkStaysDistinctFromAnUnadmittedAnchor() {
        val broken =
            TrustChain.fromEntityStatementEntries(
                listOf("https://leaf.example", "https://anchor.example"),
                TrustChainLinks.BROKEN,
            )!!
        assertEquals(TrustChainLinks.BROKEN, broken.links)
        val unadmitted =
            TrustDomainAdmission.admit(
                chain =
                    TrustChain.fromEntityStatementEntries(
                        listOf("https://leaf.example", "https://anchor.example"),
                        TrustChainLinks.VERIFIED,
                    ),
                posture = TrustDomainPosture.CUSTOM,
                assignedAnchorIds = listOf("https://other-anchor.example"),
            )
        assertEquals(TrustDomainAdmissionOutcome.NOT_ADMITTED, unadmitted.outcome)
        assertTrue(broken.links != TrustChainLinks.VERIFIED)
    }

    @Test
    fun attestationAuthorisationStaysAbsentWhenNothingCanAnswer() {
        val result =
            TrustValidationResult(
                trusted = true,
                status = TrustStatus.TRUSTED,
            )
        assertNull(result.attestationAuthorisation)
    }

    @Test
    fun opaqueNonJwtNonUriEntriesDoNotBecomeHops() {
        assertNull(
            TrustChain.fromEntityStatementEntries(
                listOf("jwt-leaf", "jwt-anchor"),
                TrustChainLinks.VERIFIED,
            ),
        )
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun entityStatementJwt(
    sub: String,
    iss: String,
): String {
    val header =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode("{\"alg\":\"none\"}".encodeToByteArray())
    val payload =
        Base64.UrlSafe
            .withPadding(Base64.PaddingOption.ABSENT)
            .encode("""{"sub":"$sub","iss":"$iss"}""".encodeToByteArray())
    return "$header.$payload.sig"
}
