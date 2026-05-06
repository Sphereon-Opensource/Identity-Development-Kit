/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * `IdkError.fromDTO` preserves the typed source error on `IdkError.source`. Consumers
 * downstream of an `IdkResult<*, IdkError>` boundary use `sourceAs<T>()` to recover
 * the original sealed [IdkErrorType] family without dispatching on `code` + `meta`
 * magic-strings.
 */
class IdkErrorSourcePreservationTest {
    /** A pretend domain-specific sealed error family that carries extra typed fields. */
    private sealed interface DomainError : IdkErrorType {
        data class FooFailed(
            val fooId: String,
            val attemptCount: Int,
            override val code: String = "FOO_FAILED",
            override val message: IdkError.Message =
                IdkError.Message(
                    i18nKey = "domain.foo_failed",
                    defaultMessage = "Foo $fooId failed after $attemptCount attempts",
                ),
            override val severity: IdkError.Severity = IdkError.Severity.ERROR,
            override val exception: Throwable? = null,
            override val causes: List<IdkErrorType> = emptyList(),
            override val meta: Map<String, Any?> = mapOf("foo_id" to fooId, "attempts" to attemptCount),
        ) : DomainError

        data class BarTimedOut(
            val barId: String,
            override val code: String = "BAR_TIMEOUT",
            override val message: IdkError.Message =
                IdkError.Message(
                    i18nKey = "domain.bar_timeout",
                    defaultMessage = "Bar $barId timed out",
                ),
            override val severity: IdkError.Severity = IdkError.Severity.ERROR,
            override val exception: Throwable? = null,
            override val causes: List<IdkErrorType> = emptyList(),
            override val meta: Map<String, Any?> = mapOf("bar_id" to barId),
        ) : DomainError
    }

    @Test
    fun fromDTO_preservesTypedSource() {
        val typed = DomainError.FooFailed(fooId = "f-1", attemptCount = 3)
        val wrapped = IdkError.fromDTO(typed)

        // Round-trip the exact instance — no copy, no rewrap.
        assertSame(typed, wrapped.source, "fromDTO must capture the original error instance")
    }

    @Test
    fun sourceAs_recoversTypedFamilyMember() {
        val typed = DomainError.FooFailed(fooId = "f-1", attemptCount = 3)
        val wrapped = IdkError.fromDTO(typed)

        val recovered = wrapped.sourceAs<DomainError.FooFailed>()
        assertNotNull(recovered, "sourceAs must recover the same subtype")
        assertEquals("f-1", recovered.fooId)
        assertEquals(3, recovered.attemptCount)
    }

    @Test
    fun sourceAs_returnsNullForWrongSubtype() {
        // A consumer that asks for the wrong sealed-family member must get null, not an
        // exception — that's the whole point of the typed accessor.
        val typed = DomainError.FooFailed(fooId = "f-1", attemptCount = 3)
        val wrapped = IdkError.fromDTO(typed)

        assertNull(wrapped.sourceAs<DomainError.BarTimedOut>(), "sourceAs of a non-matching subtype must return null")
    }

    @Test
    fun sourceAs_returnsNullWhenSourceIsAbsent() {
        // IdkError built directly (via fromString) has no typed source — sourceAs must
        // surface that as null so callers fall back to the wire-shape.
        val direct = IdkError.fromString(message = "generic", code = "X")

        assertNull(direct.source)
        assertNull(direct.sourceAs<DomainError.FooFailed>())
    }

    @Test
    fun fromDTO_unwrapsNestedIdkError_doesNotChainSources() {
        // If a caller wraps an IdkError-that-already-has-a-source via fromDTO again
        // (mappers chained through multiple boundaries), the inner typed source must be
        // preserved — NOT replaced by the intermediate IdkError. Otherwise sourceAs<T>()
        // would never find the original typed payload.
        val typed = DomainError.FooFailed(fooId = "f-1", attemptCount = 3)
        val firstWrap = IdkError.fromDTO(typed)
        val secondWrap = IdkError.fromDTO(firstWrap)

        assertSame(
            typed,
            secondWrap.source,
            "fromDTO chained through an existing IdkError must keep the original typed source, not the intermediate wrap",
        )
        assertNotNull(secondWrap.sourceAs<DomainError.FooFailed>())
    }

    @Test
    fun fromDTO_ofPlainTypedError_setsSourceToThatError() {
        // Sanity: a plain typed error (no IdkError wrapping) becomes the source.
        val typed = DomainError.BarTimedOut(barId = "b-1")
        val wrapped = IdkError.fromDTO(typed)

        assertSame(typed, wrapped.source)
    }
}
