/* Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 * See https://www.apache.org/licenses/LICENSE-2.0
 */
package com.sphereon.authenticsource

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthenticSourceProfileValidatorTest {
    private val validator = DefaultAuthenticSourceProfileValidator()
    private val provider = ProviderProjection("Synthetic provider", listOf(ProviderIdentifier("urn:example:id-type", "source-1")))
    private val other = ProviderProjection("Other provider", listOf(ProviderIdentifier("urn:example:id-type", "source-2")))
    private val eu = VerificationContext(SourceProfile.EU_2025_1569_ANNEX_IV_AS_AMENDED_2026_1735, provider, null)
    private val base = eu.copy(profile = SourceProfile.ETSI_TS_119_478_V1_1_1)
    private val candidate = AttributeCandidate("urn:example:attribute", "{\"value\":\"Alice\",\"nested\":[1,{\"x\":true}]}")
    private val second = AttributeCandidate("urn:example:second", "{\"value\":\"second\"}")
    private val match = "http://uri.etsi.org/19478/VerificationResult/Match"
    private val variation = "http://uri.etsi.org/19478/VerificationResult/MatchWithVariation"
    private val noMatch = "http://uri.etsi.org/19478/VerificationResult/NoMatch"
    private val unknown = "http://uri.etsi.org/19478/VerificationResult/Unknown"
    private val req get() = VerifyRequest(listOf(candidate))
    private val item get() = AttributeResultProjection(candidate.attributeIdentifier, match)
    private fun response(vararg results: AttributeResultProjection) = VerifyResponse(provider, attributeVerificationResults = results.toList())
    private fun error(code: String, request: VerifyRequest = req, result: VerifyResponse = response(item), ctx: VerificationContext = eu) {
        val actual = validator.validate(request, result, ctx)
        assertTrue(actual.isErr)
        assertEquals(code, actual.error.code)
    }
    private fun accepted(outcomeUri: String, expected: Outcome) {
        val result = validator.validate(req, response(item.copy(resultUri = outcomeUri)), eu)
        assertTrue(result.isOk)
        assertEquals(CorrelatedAttribute(candidate, expected, null), result.value.attributes.single())
    }
    @Test fun matchWithoutReturnedValuePreservesCandidate() = accepted(match, Outcome.MATCH)
    @Test fun variationWithoutReturnedValueDoesNotInventCorrection() = accepted(variation, Outcome.MATCH_WITH_VARIATION)
    @Test fun noMatchIsRetainedStructurally() = accepted(noMatch, Outcome.NO_MATCH)
    @Test fun unknownIsRetainedStructurally() = accepted(unknown, Outcome.UNKNOWN)
    @Test fun equivalentObjectsIgnoreWhitespaceAndKeyOrder() {
        val returned = "{ \"nested\": [1, {\"x\": true}], \"value\": \"Alice\" }"
        val result = validator.validate(req, response(item.copy(objectJson = returned)), eu)
        assertTrue(result.isOk)
        assertEquals(CorrelatedAttribute(candidate, Outcome.MATCH, returned), result.value.attributes.single())
    }
    @Test fun resultsCorrelateByIdentifierWhenReordered() {
        val secondResult = AttributeResultProjection(second.attributeIdentifier, variation)
        val result = validator.validate(VerifyRequest(listOf(candidate, second)), response(secondResult, item), eu)
        assertTrue(result.isOk)
        assertEquals(mapOf(candidate.attributeIdentifier to CorrelatedAttribute(candidate, Outcome.MATCH, null), second.attributeIdentifier to CorrelatedAttribute(second, Outcome.MATCH_WITH_VARIATION, null)), result.value.attributes.associateBy { it.candidate.attributeIdentifier })
    }
    @Test fun rejectsEmptyRequest() = error("AUTHENTIC_SOURCE_EMPTY_REQUEST", VerifyRequest(emptyList()), response())
    @Test fun rejectsChangedProvider() = error("AUTHENTIC_SOURCE_PROVIDER_MISMATCH", result = VerifyResponse(other, attributeVerificationResults = listOf(item)))
    @Test fun rejectsChangedSource() = error("AUTHENTIC_SOURCE_SOURCE_MISMATCH", result = VerifyResponse(provider, other, listOf(item)), ctx = eu.copy(expectedAuthenticSource = provider))
    @Test fun rejectsMissingExpectedSource() = error("AUTHENTIC_SOURCE_SOURCE_MISMATCH", ctx = eu.copy(expectedAuthenticSource = other))
    @Test fun rejectsUnexpectedSource() = error("AUTHENTIC_SOURCE_SOURCE_MISMATCH", result = VerifyResponse(provider, other, listOf(item)))
    @Test fun rejectsDuplicateResult() = error("AUTHENTIC_SOURCE_DUPLICATE_RESULT", result = response(item, item))
    @Test fun rejectsMissingResult() = error("AUTHENTIC_SOURCE_MISSING_RESULT", result = response())
    @Test fun rejectsUnrequestedResult() = error("AUTHENTIC_SOURCE_UNREQUESTED_RESULT", result = response(item, AttributeResultProjection("urn:example:other", match)))
    @Test fun rejectsDuplicateRequest() = error("AUTHENTIC_SOURCE_DUPLICATE_REQUEST", VerifyRequest(listOf(candidate, candidate)))
    @Test fun rejectsUnknownResultUri() = error("AUTHENTIC_SOURCE_UNKNOWN_RESULT", result = response(item.copy(resultUri = "urn:example:unknown-result")))
    @Test fun rejectsArrayCandidate() = error("AUTHENTIC_SOURCE_INVALID_OBJECT", VerifyRequest(listOf(candidate.copy(objectJson = "[]"))))
    @Test fun rejectsArrayReturnedValue() = error("AUTHENTIC_SOURCE_INVALID_OBJECT", result = response(item.copy(objectJson = "[1]")))
    @Test fun rejectsMalformedCandidateJson() = error("AUTHENTIC_SOURCE_INVALID_OBJECT", VerifyRequest(listOf(candidate.copy(objectJson = "{"))))
    @Test fun rejectsMalformedReturnedJson() = error("AUTHENTIC_SOURCE_INVALID_OBJECT", result = response(item.copy(objectJson = "{")))
    @Test fun rejectsMismatchedMatchEcho() = error("AUTHENTIC_SOURCE_MATCH_VALUE_MISMATCH", result = response(item.copy(objectJson = "{\"value\":\"Bob\"}")))
    @Test fun baseProfileRequiresMatchValue() = error("AUTHENTIC_SOURCE_VALUE_REQUIRED", ctx = base)
    @Test fun baseProfileRequiresVariationValue() = error("AUTHENTIC_SOURCE_VALUE_REQUIRED", result = response(item.copy(resultUri = variation)), ctx = base)
    @Test fun rejectsRequestedFragments() = error("AUTHENTIC_SOURCE_FRAGMENT_UNSUPPORTED", req.copy(hasFragments = true))
    @Test fun rejectsReturnedFragments() = error("AUTHENTIC_SOURCE_FRAGMENT_UNSUPPORTED", result = response(item).copy(hasFragmentResults = true))
}
