/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.binary

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.Retryability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * C20 regression coverage — BinaryError ⇄ IdkError round-trip used to collapse typed
 * codes (e.g. AUTH_INVALID_CREDENTIALS, AUTH_RATE_LIMITED, VAULT_*) into
 * UNKNOWN_ERROR with category INTERNAL, breaking transport status mapping for
 * domains that ship their own ErrorDefinitionType catalog.
 */
class BinaryErrorRoundTripTest {
    @Test
    fun typedCodeOutsideCanonicalTablePreservesCodeAndCategory() {
        val original =
            IdkError(
                code = "AUTH_INVALID_CREDENTIALS",
                message =
                    IdkError.Message(
                        i18nKey = "auth.error.credentials.invalid",
                        defaultMessage = "Invalid username or password",
                ),
                category = ErrorCategory.UNAUTHORIZED,
                meta =
                    mapOf(
                        "clientAction" to "UNWRAP_AND_RETRY",
                        "wrappedPayloadRef" to "wrapped-payload-123",
                    ),
            )

        val wire = BinaryError.fromIdkError(original)
        val roundTripped = wire.toIdkError()

        assertEquals("AUTH_INVALID_CREDENTIALS", roundTripped.code)
        assertEquals(ErrorCategory.UNAUTHORIZED, roundTripped.category)
        assertEquals("Invalid username or password", roundTripped.message.defaultMessage)
        assertEquals("UNWRAP_AND_RETRY", roundTripped.meta["clientAction"])
        assertEquals("wrapped-payload-123", roundTripped.meta["wrappedPayloadRef"])
    }

    @Test
    fun typedRateLimitedCodePreservesRateLimitedCategory() {
        val original =
            IdkError(
                code = "AUTH_RATE_LIMITED",
                message =
                    IdkError.Message(
                        i18nKey = "auth.error.rate-limited",
                        defaultMessage = "Too many attempts",
                    ),
                category = ErrorCategory.RATE_LIMITED,
            )

        val roundTripped = BinaryError.fromIdkError(original).toIdkError()

        assertEquals("AUTH_RATE_LIMITED", roundTripped.code)
        assertEquals(ErrorCategory.RATE_LIMITED, roundTripped.category)
    }

    @Test
    fun canonicalCodesStillMapToTheirDedicatedFactories() {
        // Codes in the canonical when-table (UNAUTHORIZED, FORBIDDEN, NOT_FOUND, etc.)
        // must continue routing through their typed IdkError factories rather than
        // the new category-preserving generic branch — otherwise any caller that
        // did `when (err) { is IdkError.UNAUTHORIZED_ERROR -> … }` would stop matching.
        val wire = BinaryError(code = "UNAUTHORIZED", message = "nope", category = "UNAUTHORIZED")
        val roundTripped = wire.toIdkError()

        assertEquals("UNAUTHORIZED", roundTripped.code)
        assertEquals(ErrorCategory.UNAUTHORIZED, roundTripped.category)
    }

    @Test
    fun serviceUnavailablePreservesRetrySemantics() {
        val original =
            IdkError.SERVICE_UNAVAILABLE_ERROR(
                message = "Service is temporarily overloaded; slow down and retry later",
                retryAfter = 3.seconds,
            )

        val roundTripped = BinaryError.fromIdkError(original).toIdkError()

        assertEquals("SERVICE_UNAVAILABLE", roundTripped.code)
        assertEquals(ErrorCategory.UNAVAILABLE, roundTripped.category)
        assertEquals(Retryability.TRANSIENT, roundTripped.retryability)
        assertEquals(3.seconds, roundTripped.retryAfter)
    }

    @Test
    fun quotaExceededPreservesRetrySemantics() {
        val original =
            IdkError.QUOTA_EXCEEDED_ERROR(
                message = "gRPC command receiver is temporarily overloaded; slow down and retry later",
                retryAfter = 4.seconds,
            )

        val roundTripped = BinaryError.fromIdkError(original).toIdkError()

        assertEquals("QUOTA_EXCEEDED_ERROR", roundTripped.code)
        assertEquals(ErrorCategory.RATE_LIMITED, roundTripped.category)
        assertEquals(Retryability.TRANSIENT, roundTripped.retryability)
        assertEquals(4.seconds, roundTripped.retryAfter)
    }

    @Test
    fun legacyPayloadWithoutCategoryDefaultsToInternal() {
        // Pre-fix wire payloads don't carry `category`. Reconstructed IdkError
        // falls back to INTERNAL — matching the prior UNKNOWN_ERROR behaviour for
        // transport status mapping purposes.
        val wire = BinaryError(code = "CUSTOM_DOMAIN_ERROR", message = "something", category = null)
        val roundTripped = wire.toIdkError()

        assertEquals("CUSTOM_DOMAIN_ERROR", roundTripped.code)
        assertEquals(ErrorCategory.INTERNAL, roundTripped.category)
    }

    @Test
    fun unknownCategoryStringFallsBackToInternal() {
        // Future category values from newer senders must not crash older receivers.
        val wire = BinaryError(code = "FOO_BAR", message = "x", category = "NOT_A_REAL_CATEGORY")
        val roundTripped = wire.toIdkError()

        assertEquals("FOO_BAR", roundTripped.code)
        assertEquals(ErrorCategory.INTERNAL, roundTripped.category)
    }
}
