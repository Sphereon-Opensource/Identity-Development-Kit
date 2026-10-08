/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WalletCandidateDisclosureTest {
    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = false
        }

    @Test
    fun `two candidates for one requirement disclose different amounts`() {
        val projected = projectSelection(requirementWithCandidates("lean-credential", "rich-credential"))
        val lean = projected.candidateDisclosures.single { it.credentialId == "lean-credential" }
        val rich = projected.candidateDisclosures.single { it.credentialId == "rich-credential" }
        assertTrue(lean.disclosedClaims.size < rich.disclosedClaims.size, "the whole point of the band is that this difference is visible")
    }

    @Test
    fun `a disclosure names claims without values, because nothing is disclosed yet`() {
        val disclosure = projectSelection(requirementWithCandidates("rich-credential")).candidateDisclosures.single()
        // Not a security assertion. At this point in the exchange the holder has chosen nothing, so
        // there is no disclosure to describe the contents of. Values live on the credential itself
        // and the WALLET decides whether to mask them on screen.
        assertTrue(disclosure.disclosedClaims.all { it.label != null || it.path.isNotEmpty() })
        val encoded = json.encodeToString(disclosure)
        assertFalse(encoded.contains("\"value\""), "producer must not put claim values on disclosedClaims")
        disclosure.disclosedClaims.forEach { claim ->
            assertTrue(claim.path.isNotEmpty())
        }
    }

    @Test
    fun `empty candidateDisclosures means cost was not computed`() {
        val projected = projectSelectionFromIdsOnly("lean-credential", "rich-credential")
        assertEquals(listOf("lean-credential", "rich-credential"), projected.candidateCredentialIds)
        assertTrue(projected.requiredClaimPaths.isNotEmpty())
        assertTrue(projected.candidateDisclosures.isEmpty(), "ids-only listing cannot compute per-candidate cost")
    }

    @Test
    fun `a present disclosure with empty disclosedClaims is not collapsed with not computed`() {
        val disclosesNothing =
            WalletCredentialRequirement(
                id = "presence-only",
                candidateCredentialIds = listOf("age-over-18"),
                candidateDisclosures =
                    listOf(
                        WalletCandidateDisclosure(
                            credentialId = "age-over-18",
                            disclosedClaims = emptyList(),
                            satisfiesFully = true,
                        ),
                    ),
            )
        val notComputed = projectSelectionFromIdsOnly("age-over-18")
        assertTrue(disclosesNothing.candidateDisclosures.single().disclosedClaims.isEmpty())
        assertTrue(notComputed.candidateDisclosures.isEmpty())
        assertFalse(disclosesNothing.candidateDisclosures.isEmpty())
    }

    private fun projectSelection(requirement: WalletCredentialRequirement): WalletCredentialRequirement = requirement

    private fun projectSelectionFromIdsOnly(vararg candidateIds: String): WalletCredentialRequirement =
        WalletCredentialRequirement(
            id = "pid",
            format = "dc+sd-jwt",
            multipleAllowed = true,
            requiredClaimPaths =
                listOf(
                    listOf(JsonPrimitive("given_name")),
                    listOf(JsonPrimitive("family_name")),
                    listOf(JsonPrimitive("address"), JsonPrimitive("street_address")),
                    listOf(JsonPrimitive("x_custom"), JsonPrimitive("vendor_field")),
                ),
            candidateCredentialIds = candidateIds.toList(),
        )

    private fun requirementWithCandidates(vararg candidateIds: String): WalletCredentialRequirement {
        val claimsByCandidate =
            mapOf(
                "lean-credential" to leanClaims(),
                "rich-credential" to richClaims(),
            )
        val ids = candidateIds.toList()
        return WalletCredentialRequirement(
            id = "pid",
            format = "dc+sd-jwt",
            multipleAllowed = true,
            requiredClaimPaths =
                listOf(
                    listOf(JsonPrimitive("given_name")),
                    listOf(JsonPrimitive("family_name")),
                    listOf(JsonPrimitive("address"), JsonPrimitive("street_address")),
                    listOf(JsonPrimitive("x_custom"), JsonPrimitive("vendor_field")),
                ),
            candidateCredentialIds = ids,
            candidateDisclosures =
                ids.map { id ->
                    val claims = claimsByCandidate.getValue(id)
                    WalletCandidateDisclosure(
                        credentialId = id,
                        disclosedClaims = claims,
                        satisfiesFully = id == "rich-credential",
                    )
                },
        )
    }

    private fun leanClaims(): List<WalletRequestedClaim> =
        listOf(
            WalletRequestedClaim(path = listOf(JsonPrimitive("given_name"))),
            WalletRequestedClaim(path = listOf(JsonPrimitive("family_name"))),
        )

    private fun richClaims(): List<WalletRequestedClaim> =
        listOf(
            WalletRequestedClaim(path = listOf(JsonPrimitive("given_name"))),
            WalletRequestedClaim(path = listOf(JsonPrimitive("family_name"))),
            WalletRequestedClaim(path = listOf(JsonPrimitive("address"), JsonPrimitive("street_address"))),
            WalletRequestedClaim(path = listOf(JsonPrimitive("address"), JsonPrimitive("locality"))),
            WalletRequestedClaim(path = listOf(JsonPrimitive("nationality"))),
        )
}
