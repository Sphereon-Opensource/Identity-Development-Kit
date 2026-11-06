/*
 * (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vp.verifier.CredentialTrustValidation
import com.sphereon.openid.oid4vp.verifier.CredentialTrustValidationMode
import com.sphereon.openid.oid4vp.verifier.Oid4vpCredentialTrustValidationArgs
import com.sphereon.openid.oid4vp.verifier.Oid4vpCredentialTrustValidator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CredentialTrustValidatorCompositionTest {
    private val args = Oid4vpCredentialTrustValidationArgs(credentialQueryId = "credential", format = "dc+sd-jwt", presentation = "presentation")

    @Test
    fun shouldNotLetAnAllowingPeerOverrideAnEnforcingDenial() = runTest {
        val allow = validator(Ok(CredentialTrustValidation(enabled = true, trusted = true)))
        val deny = validator(Ok(CredentialTrustValidation(enabled = true, trusted = false, status = "SCOPED_MARK_REJECTED")))
        for (validators in listOf(listOf(allow, deny), listOf(deny, allow))) {
            val result = evaluateCredentialTrustValidators(validators, args)
            assertEquals(false, result.trusted)
            assertEquals("SCOPED_MARK_REJECTED", result.status)
        }
    }

    @Test
    fun shouldNotLetDisabledOrAuditResultsSuppressAnEnforcingDecision() = runTest {
        val disabled = validator(Ok(CredentialTrustValidation(enabled = false, mode = CredentialTrustValidationMode.DISABLED)))
        val audit = validator(Ok(CredentialTrustValidation(enabled = true, trusted = false, mode = CredentialTrustValidationMode.AUDIT)))
        val allow = validator(Ok(CredentialTrustValidation(enabled = true, trusted = true)))
        val result = evaluateCredentialTrustValidators(listOf(disabled, audit, allow), args)
        assertTrue(result.enabled)
        assertEquals(CredentialTrustValidationMode.DEFAULT_ENFORCE, result.mode)
        assertEquals(true, result.trusted)
    }

    @Test
    fun shouldFailClosedWhenAnySupportingValidatorReturnsAnError() = runTest {
        val allow = validator(Ok(CredentialTrustValidation(enabled = true, trusted = true)))
        val broken = validator(Err(IdkError.UNKNOWN_ERROR(message = "unavailable")))
        val result = evaluateCredentialTrustValidators(listOf(allow, broken), args)
        assertFalse(result.trusted == true)
        assertEquals("VALIDATION_ERROR", result.status)
    }

    @Test
    fun shouldRetainDiagnosticsFromEverySuccessfulEnforcingValidator() = runTest {
        val first = validator(Ok(CredentialTrustValidation(enabled = true, trusted = true, trustDomainIds = listOf("a"), diagnostics = listOf("first"))))
        val second = validator(Ok(CredentialTrustValidation(enabled = true, trusted = true, trustDomainIds = listOf("b"), diagnostics = listOf("second"))))
        val result = evaluateCredentialTrustValidators(listOf(first, second), args)
        assertEquals(true, result.trusted)
        assertEquals(listOf("a", "b"), result.trustDomainIds)
        assertEquals(listOf("first", "second"), result.diagnostics)
    }

    @Test
    fun shouldRetainScopedEvidenceRegardlessOfValidatorOrder() = runTest {
        val scoped = com.sphereon.openid.oid4vp.verifier.ScopedTrustMarkValidation(
            outcome = com.sphereon.openid.oid4vp.verifier.ScopedTrustMarkOutcome.VERIFIED,
            policyDigest = "policy", issuer = "issuer", trustMarkType = "mark",
            scopePolicyId = "scope", scopePolicyDigest = "digest", authorityDomainId = "domain",
            authorityAnchorId = "anchor", checkedAtEpochMillis = 1,
        )
        val plain = validator(Ok(CredentialTrustValidation(enabled = true, trusted = true)))
        val marked = validator(Ok(CredentialTrustValidation(enabled = true, trusted = true, scopedTrustMark = scoped, decisionDigest = "issuer-decision")))
        for (validators in listOf(listOf(plain, marked), listOf(marked, plain))) {
            val result = evaluateCredentialTrustValidators(validators, args)
            assertEquals(scoped, result.scopedTrustMark)
            assertEquals("issuer-decision", result.decisionDigest)
        }
    }

    @Test
    fun shouldKeepADenialFreeOfAPeersVerifiedScopedEvidence() = runTest {
        val scoped = com.sphereon.openid.oid4vp.verifier.ScopedTrustMarkValidation(
            outcome = com.sphereon.openid.oid4vp.verifier.ScopedTrustMarkOutcome.VERIFIED,
            policyDigest = "policy", issuer = "issuer", trustMarkType = "mark",
            scopePolicyId = "scope", scopePolicyDigest = "digest", authorityDomainId = "domain",
            authorityAnchorId = "anchor", checkedAtEpochMillis = 1,
        )
        val marked = validator(Ok(CredentialTrustValidation(enabled = true, trusted = true, scopedTrustMark = scoped, policyDigest = "p", decisionDigest = "d")))
        val deny = validator(Ok(CredentialTrustValidation(enabled = true, trusted = false, status = "DENIED")))
        for (validators in listOf(listOf(marked, deny), listOf(deny, marked))) {
            val result = evaluateCredentialTrustValidators(validators, args)
            assertEquals(false, result.trusted)
            assertEquals(null, result.scopedTrustMark)
            assertEquals(null, result.decisionDigest)
        }
    }

    @Test
    fun shouldKeepMatchedDomainAndAnchorWhenAScopedValidatorIsPromoted() = runTest {
        val scoped = com.sphereon.openid.oid4vp.verifier.ScopedTrustMarkValidation(
            outcome = com.sphereon.openid.oid4vp.verifier.ScopedTrustMarkOutcome.VERIFIED,
            policyDigest = "policy", issuer = "issuer", trustMarkType = "mark",
            scopePolicyId = "scope", scopePolicyDigest = "digest", authorityDomainId = "domain",
            authorityAnchorId = "anchor", checkedAtEpochMillis = 1,
        )
        val matched = validator(Ok(CredentialTrustValidation(enabled = true, trusted = true, matchedTrustDomainId = "domain-1", matchedAnchorId = "anchor-1")))
        val marked = validator(Ok(CredentialTrustValidation(enabled = true, trusted = true, scopedTrustMark = scoped)))
        val result = evaluateCredentialTrustValidators(listOf(matched, marked), args)
        assertEquals(scoped, result.scopedTrustMark)
        assertEquals("domain-1", result.matchedTrustDomainId)
        assertEquals("anchor-1", result.matchedAnchorId)
    }

    @Test
    fun shouldRejectConflictingScopedEvidenceFromEnforcingValidators() = runTest {
        val scoped = com.sphereon.openid.oid4vp.verifier.ScopedTrustMarkValidation(
            outcome = com.sphereon.openid.oid4vp.verifier.ScopedTrustMarkOutcome.VERIFIED,
            policyDigest = "policy", issuer = "issuer", trustMarkType = "mark",
            scopePolicyId = "scope", scopePolicyDigest = "digest", authorityDomainId = "domain",
            authorityAnchorId = "anchor", checkedAtEpochMillis = 1,
        )
        val first = validator(Ok(CredentialTrustValidation(enabled = true, trusted = true, scopedTrustMark = scoped)))
        val second = validator(Ok(CredentialTrustValidation(enabled = true, trusted = true, scopedTrustMark = scoped.copy(authorityAnchorId = "other"))))
        for (validators in listOf(listOf(first, second), listOf(second, first))) {
            val result = evaluateCredentialTrustValidators(validators, args)
            assertEquals(false, result.trusted)
            assertEquals("SCOPED_MARK_AMBIGUOUS", result.status)
        }
    }

    @Test
    fun shouldNotTreatAnAuditResultsMarkAsConflictingWithAnEnforcingOne() = runTest {
        val enforcingMark = com.sphereon.openid.oid4vp.verifier.ScopedTrustMarkValidation(
            outcome = com.sphereon.openid.oid4vp.verifier.ScopedTrustMarkOutcome.VERIFIED,
            policyDigest = "policy", issuer = "issuer", trustMarkType = "mark",
            scopePolicyId = "scope", scopePolicyDigest = "digest", authorityDomainId = "domain",
            authorityAnchorId = "anchor", checkedAtEpochMillis = 1,
        )
        val enforcing = validator(Ok(CredentialTrustValidation(enabled = true, trusted = true, scopedTrustMark = enforcingMark)))
        val audit = validator(Ok(CredentialTrustValidation(
            enabled = true, trusted = true, mode = CredentialTrustValidationMode.AUDIT,
            scopedTrustMark = enforcingMark.copy(authorityAnchorId = "other"),
        )))
        for (validators in listOf(listOf(audit, enforcing), listOf(enforcing, audit))) {
            val result = evaluateCredentialTrustValidators(validators, args)
            assertEquals(true, result.trusted)
            assertEquals(enforcingMark, result.scopedTrustMark)
            assertEquals(CredentialTrustValidationMode.DEFAULT_ENFORCE, result.mode)
        }
    }

    @Test
    fun shouldPreserveDisabledResultWhenNoValidatorSupportsTheCredential() = runTest {
        val result = evaluateCredentialTrustValidators(emptyList(), args)
        assertFalse(result.enabled)
        assertEquals(CredentialTrustValidationMode.DISABLED, result.mode)
    }

    private fun validator(result: IdkResult<CredentialTrustValidation, IdkError>) = object : Oid4vpCredentialTrustValidator {
        override suspend fun validate(args: Oid4vpCredentialTrustValidationArgs) = result
    }
}
