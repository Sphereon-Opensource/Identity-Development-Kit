/* Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 * See https://www.apache.org/licenses/LICENSE-2.0
 */
package com.sphereon.authenticsource

import com.sphereon.core.api.IdkErrorResult
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Checks projections only. Success is not authentication or evidence of authorization. */
class DefaultAuthenticSourceProfileValidator : AuthenticSourceProfileValidator {
    override fun validate(request: VerifyRequest, response: VerifyResponse, context: VerificationContext): IdkResult<CorrelatedResponse, IdkError> {
        if (request.hasFragments || response.hasFragmentResults) return error("AUTHENTIC_SOURCE_FRAGMENT_UNSUPPORTED")
        if (request.attributes.isEmpty()) return error("AUTHENTIC_SOURCE_EMPTY_REQUEST")
        if (response.provider != context.expectedProvider) return error("AUTHENTIC_SOURCE_PROVIDER_MISMATCH")
        if (response.authenticSource != context.expectedAuthenticSource) return error("AUTHENTIC_SOURCE_SOURCE_MISMATCH")

        val requested = request.attributes.associateBy { it.attributeIdentifier }
        if (requested.size != request.attributes.size) return error("AUTHENTIC_SOURCE_DUPLICATE_REQUEST")
        val results = response.attributeVerificationResults.associateBy { it.attributeIdentifier }
        if (results.size != response.attributeVerificationResults.size) return error("AUTHENTIC_SOURCE_DUPLICATE_RESULT")
        if (results.keys.any { it !in requested }) return error("AUTHENTIC_SOURCE_UNREQUESTED_RESULT")
        if (requested.keys.any { it !in results }) return error("AUTHENTIC_SOURCE_MISSING_RESULT")

        val correlated = mutableListOf<CorrelatedAttribute>()
        for (candidate in request.attributes) {
            val result = results.getValue(candidate.attributeIdentifier)
            val submitted = parseObject(candidate.objectJson) ?: return error("AUTHENTIC_SOURCE_INVALID_OBJECT")
            val returnedObjectJson = result.objectJson
            val returned = if (returnedObjectJson != null) {
                parseObject(returnedObjectJson) ?: return error("AUTHENTIC_SOURCE_INVALID_OBJECT")
            } else {
                null
            }
            val outcome = when (result.resultUri) {
                "http://uri.etsi.org/19478/VerificationResult/Match" -> Outcome.MATCH
                "http://uri.etsi.org/19478/VerificationResult/MatchWithVariation" -> Outcome.MATCH_WITH_VARIATION
                "http://uri.etsi.org/19478/VerificationResult/NoMatch" -> Outcome.NO_MATCH
                "http://uri.etsi.org/19478/VerificationResult/Unknown" -> Outcome.UNKNOWN
                else -> return error("AUTHENTIC_SOURCE_UNKNOWN_RESULT")
            }
            if (context.profile == SourceProfile.ETSI_TS_119_478_V1_1_1 &&
                (outcome == Outcome.MATCH || outcome == Outcome.MATCH_WITH_VARIATION) && returned == null
            ) return error("AUTHENTIC_SOURCE_VALUE_REQUIRED")
            if (outcome == Outcome.MATCH && returned != null && submitted != returned) {
                return error("AUTHENTIC_SOURCE_MATCH_VALUE_MISMATCH")
            }
            correlated.add(CorrelatedAttribute(candidate, outcome, result.objectJson))
        }
        return IdkResult.ok(CorrelatedResponse(correlated))
    }

    private fun parseObject(value: String): JsonObject? = try {
        Json.parseToJsonElement(value) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun error(code: String): IdkResult<CorrelatedResponse, IdkError> =
        IdkErrorResult(IdkError.fromString("Authentic source structural validation failed", code))
}
