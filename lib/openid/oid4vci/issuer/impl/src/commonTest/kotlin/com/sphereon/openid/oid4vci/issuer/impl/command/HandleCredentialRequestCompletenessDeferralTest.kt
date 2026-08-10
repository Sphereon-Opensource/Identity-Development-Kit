/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.openid.oid4vci.issuer.config.MissingRequiredClaimsPolicy
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciCompletenessLifecycleResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [resolveCompletenessOutcome], the pure decision function backing
 * [HandleCredentialRequestCommandImpl]'s OID4VCI §6.1 completeness gate.
 *
 * Mirrors the outcome matrix documented on [resolveCompletenessOutcome]: every scenario the
 * credential endpoint's deferral gate must handle, including the new per-instance
 * [MissingRequiredClaimsPolicy] branch (Task C4).
 */
class HandleCredentialRequestCompletenessDeferralTest {
    @Test
    fun finalMandatoryGateRequiresEveryNamespacedMdlClaim() {
        val mandatory =
            setOf(
                "org.iso.18013.5.1.family_name",
                "org.iso.18013.5.1.given_name",
                "org.iso.18013.5.1.driving_privileges",
            )

        assertEquals(
            listOf(
                "org.iso.18013.5.1.driving_privileges",
                "org.iso.18013.5.1.given_name",
            ),
            missingMandatoryClaimPaths(
                mandatory,
                mapOf("org.iso.18013.5.1.family_name" to JsonPrimitive("Doe")),
            ),
        )
    }

    @Test
    fun finalMandatoryGateTreatsBlankNullAndEmptyCollectionsAsMissing() {
        val attributes =
            mapOf(
                "blank" to JsonPrimitive("  "),
                "null" to JsonNull,
                "array" to JsonArray(emptyList()),
                "object" to JsonObject(emptyMap()),
                "json-array-text" to JsonPrimitive("[]"),
                "/pointer" to JsonPrimitive("present"),
                "false" to JsonPrimitive(false),
                "zero" to JsonPrimitive(0),
            )

        assertEquals(
            listOf("array", "blank", "json-array-text", "null", "object"),
            missingMandatoryClaimPaths(attributes.keys.map { it.removePrefix("/") }.toSet(), attributes),
        )
    }

    // ---------------------------------------------------------------------------------------
    // Pre-existing completeness/deferral behavior — unchanged by the missingRequiredClaims
    // policy, regression-covered here since the deferral decision tree that used to live inline
    // in evaluateCompleteness() was reordered and extracted as part of this task.
    // ---------------------------------------------------------------------------------------

    @Test
    fun allBindingsCompleteIssuesNormallyWithoutDeferredEntry() {
        val result = Oid4vciCompletenessLifecycleResult(shouldDefer = false, awaitingApproval = false, missingRequiredClaims = emptyList())

        val outcome = resolveCompletenessOutcome(result, MissingRequiredClaimsPolicy.REJECT)

        assertTrue(outcome.isOk)
        assertEquals(false, outcome.value)
    }

    @Test
    fun incompleteDeferrableBindingDefers() {
        // Every incomplete binding has an enabled deferral policy: no missing required claims are
        // reported, shouldDefer is true.
        val result = Oid4vciCompletenessLifecycleResult(shouldDefer = true, awaitingApproval = false, missingRequiredClaims = emptyList())

        val outcome = resolveCompletenessOutcome(result, MissingRequiredClaimsPolicy.REJECT)

        assertTrue(outcome.isOk)
        assertEquals(true, outcome.value)
    }

    @Test
    fun completeBindingAwaitingApprovalDefers() {
        val result = Oid4vciCompletenessLifecycleResult(shouldDefer = false, awaitingApproval = true, missingRequiredClaims = emptyList())

        val outcome = resolveCompletenessOutcome(result, MissingRequiredClaimsPolicy.REJECT)

        assertTrue(outcome.isOk)
        assertEquals(true, outcome.value)
    }

    // ---------------------------------------------------------------------------------------
    // Task C4: missingRequiredClaims reject/defer policy.
    //
    // Scenario: a required claim is missing AND the binding carries no explicit deferral policy
    // of its own (deferralRecommended=false at the pipeline layer), so the lifecycle hook reports
    // a non-empty missingRequiredClaims list with shouldDefer=false. The instance-level policy
    // decides whether that hard-blocks (reject, default) or defers (defer).
    // ---------------------------------------------------------------------------------------

    @Test
    fun incompleteNonDeferrableBindingFailsUnderRejectPolicy() {
        val result =
            Oid4vciCompletenessLifecycleResult(
                shouldDefer = false,
                awaitingApproval = false,
                missingRequiredClaims = listOf("given_name"),
            )

        val outcome = resolveCompletenessOutcome(result, MissingRequiredClaimsPolicy.REJECT)

        assertTrue(outcome.isErr)
        assertEquals("invalid_credential_request", outcome.error.code)
        assertTrue(outcome.error.message.defaultMessage.contains("given_name"), "error message should name the missing claim")
    }

    @Test
    fun incompleteNonDeferrableBindingDefersUnderDeferPolicy() {
        val result =
            Oid4vciCompletenessLifecycleResult(
                shouldDefer = false,
                awaitingApproval = false,
                missingRequiredClaims = listOf("given_name"),
            )

        val outcome = resolveCompletenessOutcome(result, MissingRequiredClaimsPolicy.DEFER)

        assertTrue(outcome.isOk)
        assertEquals(true, outcome.value)
    }

    @Test
    fun defaultPolicyIsReject() {
        // The Oid4vciIssuerConfigProvider interface default (no override) must be REJECT so
        // existing deployments keep today's hard-block behavior unless they opt in to defer.
        val provider =
            object : com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider {
                override val issuerIdentifier: String = "https://issuer.example"
                override val credentialConfigurations = emptyMap<String, com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported>()
                override val authorizationServers: List<String>? = null
                override val display: List<com.sphereon.openid.oid4vc.common.DisplayProperties>? = null
            }

        assertEquals(MissingRequiredClaimsPolicy.REJECT, provider.missingRequiredClaims)
    }

    @Test
    fun missingRequiredClaimsTakesPrecedenceOverAwaitingApproval() {
        // A required claim missing on a non-deferrable binding, while a *different* binding in
        // the same session is awaiting approval. The missing-required-claims signal must still
        // drive the outcome (reject, or defer per policy) rather than being silently swallowed by
        // the awaiting-approval binding's own (unrelated) deferral.
        val result =
            Oid4vciCompletenessLifecycleResult(
                shouldDefer = false,
                awaitingApproval = true,
                missingRequiredClaims = listOf("birth_date"),
            )

        val rejectOutcome = resolveCompletenessOutcome(result, MissingRequiredClaimsPolicy.REJECT)
        assertTrue(rejectOutcome.isErr)
        assertEquals("invalid_credential_request", rejectOutcome.error.code)

        val deferOutcome = resolveCompletenessOutcome(result, MissingRequiredClaimsPolicy.DEFER)
        assertTrue(deferOutcome.isOk)
        assertEquals(true, deferOutcome.value)
    }
}
