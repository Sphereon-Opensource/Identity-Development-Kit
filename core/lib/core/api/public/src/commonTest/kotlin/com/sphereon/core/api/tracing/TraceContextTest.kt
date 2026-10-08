/*
 * (c) 2026 Sphereon International B.V.
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
 *
 */

package com.sphereon.core.api.tracing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract Tests: TraceContext W3C traceparent serialization/deserialization.
 *
 * W3C Trace Context spec:
 * Format: version-traceId-spanId-traceFlags
 * Example: 00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01
 *
 * - version: 2 hex chars (currently "00")
 * - traceId: 32 hex chars
 * - spanId: 16 hex chars
 * - traceFlags: 2 hex chars (bit field, 01 = sampled)
 */
class TraceContextCreationTest {
    @Test
    fun traceContextStoresTraceId() {
        // Given a TraceContext with a trace ID
        val ctx =
            TraceContext(
                traceId = "0af7651916cd43dd8448eb211c80319c",
                spanId = "00f067aa0ba902b7",
            )

        // Then the trace ID is accessible
        assertEquals("0af7651916cd43dd8448eb211c80319c", ctx.traceId)
    }

    @Test
    fun traceContextStoresSpanId() {
        // Given a TraceContext with a span ID
        val ctx =
            TraceContext(
                traceId = "0af7651916cd43dd8448eb211c80319c",
                spanId = "00f067aa0ba902b7",
            )

        // Then the span ID is accessible
        assertEquals("00f067aa0ba902b7", ctx.spanId)
    }

    @Test
    fun traceContextParentSpanIdIsOptional() {
        // Given a TraceContext without parent span ID
        val ctx =
            TraceContext(
                traceId = "0af7651916cd43dd8448eb211c80319c",
                spanId = "00f067aa0ba902b7",
            )

        // Then parentSpanId defaults to null
        assertNull(ctx.parentSpanId)
    }

    @Test
    fun traceContextStoresParentSpanId() {
        // Given a TraceContext with a parent span ID
        val ctx =
            TraceContext(
                traceId = "0af7651916cd43dd8448eb211c80319c",
                spanId = "00f067aa0ba902b7",
                parentSpanId = "b7ad6b7169203331",
            )

        // Then the parent span ID is accessible
        assertEquals("b7ad6b7169203331", ctx.parentSpanId)
    }

    @Test
    fun traceContextTraceFlagsDefaultToZero() {
        // Given a TraceContext without explicit flags
        val ctx =
            TraceContext(
                traceId = "0af7651916cd43dd8448eb211c80319c",
                spanId = "00f067aa0ba902b7",
            )

        // Then traceFlags default to 0
        assertEquals(0, ctx.traceFlags)
    }

    @Test
    fun traceContextStoresTraceFlags() {
        // Given a TraceContext with sampled flag
        val ctx =
            TraceContext(
                traceId = "0af7651916cd43dd8448eb211c80319c",
                spanId = "00f067aa0ba902b7",
                traceFlags = 1, // sampled
            )

        // Then traceFlags are accessible
        assertEquals(1, ctx.traceFlags)
    }
}

class TraceContextW3CSerializationTest {
    @Test
    fun toW3CTraceParentFormatsCorrectly() {
        // Given a TraceContext
        val ctx =
            TraceContext(
                traceId = "0af7651916cd43dd8448eb211c80319c",
                spanId = "00f067aa0ba902b7",
                traceFlags = 1,
            )

        // When serializing to W3C traceparent
        val header = ctx.toW3CTraceparent()

        // Then the format is version-traceId-spanId-flags
        assertEquals("00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01", header)
    }

    @Test
    fun toW3CTraceParentWithZeroFlags() {
        // Given a TraceContext with zero flags
        val ctx =
            TraceContext(
                traceId = "0af7651916cd43dd8448eb211c80319c",
                spanId = "00f067aa0ba902b7",
                traceFlags = 0,
            )

        // When serializing
        val header = ctx.toW3CTraceparent()

        // Then flags are zero-padded
        assertEquals("00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-00", header)
    }

    @Test
    fun toW3CTraceParentVersionIsAlways00() {
        // Given a TraceContext
        val ctx =
            TraceContext(
                traceId = "abcdef0123456789abcdef0123456789",
                spanId = "0123456789abcdef",
            )

        // When serializing
        val header = ctx.toW3CTraceparent()

        // Then version is 00
        assertTrue(header.startsWith("00-"))
    }
}

class TraceContextW3CDeserializationTest {
    @Test
    fun fromW3CTraceParentParsesValidHeader() {
        // Given a valid W3C traceparent header
        val header = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01"

        // When parsing
        val ctx = TraceContext.fromW3CTraceparent(header)

        // Then the context is created with correct values
        assertNotNull(ctx)
        assertEquals("0af7651916cd43dd8448eb211c80319c", ctx.traceId)
        assertEquals("00f067aa0ba902b7", ctx.spanId)
        assertEquals(1, ctx.traceFlags)
    }

    @Test
    fun fromW3CTraceParentParsesZeroFlags() {
        // Given a traceparent with zero flags
        val header = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-00"

        // When parsing
        val ctx = TraceContext.fromW3CTraceparent(header)

        // Then flags are zero
        assertNotNull(ctx)
        assertEquals(0, ctx.traceFlags)
    }

    @Test
    fun fromW3CTraceParentReturnsNullForEmptyString() {
        // Given an empty string
        val ctx = TraceContext.fromW3CTraceparent("")

        // Then null is returned
        assertNull(ctx)
    }

    @Test
    fun fromW3CTraceParentReturnsNullForGarbage() {
        // Given a garbage string
        val ctx = TraceContext.fromW3CTraceparent("not-a-valid-traceparent")

        // Then null is returned
        assertNull(ctx)
    }

    @Test
    fun fromW3CTraceParentReturnsNullForTooFewSegments() {
        // Given a header with too few segments
        val ctx = TraceContext.fromW3CTraceparent("00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7")

        // Then null is returned
        assertNull(ctx)
    }

    @Test
    fun fromW3CTraceParentReturnsNullForTooManySegments() {
        // Given a header with too many segments
        val ctx = TraceContext.fromW3CTraceparent("00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01-extra")

        // Then null is returned
        assertNull(ctx)
    }

    @Test
    fun fromW3CTraceParentReturnsNullForInvalidVersion() {
        // Given a header with an unsupported version (ff)
        val ctx = TraceContext.fromW3CTraceparent("ff-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01")

        // Then null is returned (ff is reserved for future use, per spec invalid for parsing)
        assertNull(ctx)
    }

    @Test
    fun fromW3CTraceParentReturnsNullForShortTraceId() {
        // Given a header with a trace ID that is too short
        val ctx = TraceContext.fromW3CTraceparent("00-0af7651916cd43dd-00f067aa0ba902b7-01")

        // Then null is returned
        assertNull(ctx)
    }

    @Test
    fun fromW3CTraceParentReturnsNullForShortSpanId() {
        // Given a header with a span ID that is too short
        val ctx = TraceContext.fromW3CTraceparent("00-0af7651916cd43dd8448eb211c80319c-00f067aa-01")

        // Then null is returned
        assertNull(ctx)
    }

    @Test
    fun fromW3CTraceparentParsesAllZeroTraceId() {
        // Given a header where traceId is all zeros
        // Note: The W3C spec considers all-zero invalid, but the parser accepts valid format.
        // Higher-level validation can reject all-zero trace IDs if needed.
        val ctx = TraceContext.fromW3CTraceparent("00-00000000000000000000000000000000-00f067aa0ba902b7-01")

        // Then it is parsed (format is valid even if semantically questionable)
        assertNotNull(ctx)
        assertEquals("00000000000000000000000000000000", ctx.traceId)
    }

    @Test
    fun fromW3CTraceparentParsesAllZeroSpanId() {
        // Given a header where spanId is all zeros
        val ctx = TraceContext.fromW3CTraceparent("00-0af7651916cd43dd8448eb211c80319c-0000000000000000-01")

        // Then it is parsed (format is valid)
        assertNotNull(ctx)
        assertEquals("0000000000000000", ctx.spanId)
    }
}

class TraceContextRoundTripTest {
    @Test
    fun roundTripPreservesAllFields() {
        // Given a TraceContext
        val original =
            TraceContext(
                traceId = "0af7651916cd43dd8448eb211c80319c",
                spanId = "00f067aa0ba902b7",
                traceFlags = 1,
            )

        // When serializing and deserializing
        val header = original.toW3CTraceparent()
        val restored = TraceContext.fromW3CTraceparent(header)

        // Then the round-trip preserves all fields
        assertNotNull(restored)
        assertEquals(original.traceId, restored.traceId)
        assertEquals(original.spanId, restored.spanId)
        assertEquals(original.traceFlags, restored.traceFlags)
    }

    @Test
    fun roundTripWithZeroFlags() {
        // Given a TraceContext with zero flags
        val original =
            TraceContext(
                traceId = "abcdef0123456789abcdef0123456789",
                spanId = "0123456789abcdef",
                traceFlags = 0,
            )

        // When round-tripping
        val header = original.toW3CTraceparent()
        val restored = TraceContext.fromW3CTraceparent(header)

        // Then values are preserved
        assertNotNull(restored)
        assertEquals(original.traceId, restored.traceId)
        assertEquals(original.spanId, restored.spanId)
        assertEquals(0, restored.traceFlags)
    }

    @Test
    fun roundTripMultipleContexts() {
        // Given several different contexts
        val contexts =
            listOf(
                TraceContext("aaaabbbbccccdddd1111222233334444", "1111222233334444", traceFlags = 0),
                TraceContext("ffffeeeeddddccccbbbbaaaa99998888", "ffffeeeeddddcccc", traceFlags = 1),
                TraceContext("0af7651916cd43dd8448eb211c80319c", "00f067aa0ba902b7", traceFlags = 0),
            )

        // When round-tripping each
        for (original in contexts) {
            val header = original.toW3CTraceparent()
            val restored = TraceContext.fromW3CTraceparent(header)

            // Then all are preserved
            assertNotNull(restored, "Failed to round-trip: $header")
            assertEquals(original.traceId, restored.traceId)
            assertEquals(original.spanId, restored.spanId)
            assertEquals(original.traceFlags, restored.traceFlags)
        }
    }
}

/**
 * Security-focused tests: Malformed traceparent must not propagate garbage.
 * Per security review item #10.
 */
class TraceContextSecurityTest {
    @Test
    fun rejectsNonHexCharsInTraceId() {
        // Given a traceparent with non-hex characters in trace ID
        val ctx = TraceContext.fromW3CTraceparent("00-ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ-00f067aa0ba902b7-01")

        // Then null is returned (no garbage propagation)
        assertNull(ctx)
    }

    @Test
    fun rejectsNonHexCharsInSpanId() {
        // Given a traceparent with non-hex characters in span ID
        val ctx = TraceContext.fromW3CTraceparent("00-0af7651916cd43dd8448eb211c80319c-ZZZZZZZZZZZZZZZZ-01")

        // Then null is returned
        assertNull(ctx)
    }

    @Test
    fun rejectsNonHexCharsInFlags() {
        // Given a traceparent with non-hex characters in flags
        val ctx = TraceContext.fromW3CTraceparent("00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-ZZ")

        // Then null is returned
        assertNull(ctx)
    }

    @Test
    fun rejectsOversizedTraceId() {
        // Given a traceparent with an oversized trace ID (33 hex chars)
        val ctx = TraceContext.fromW3CTraceparent("00-0af7651916cd43dd8448eb211c80319c0-00f067aa0ba902b7-01")

        // Then null is returned (must be exactly 32 hex chars)
        assertNull(ctx)
    }

    @Test
    fun rejectsOversizedSpanId() {
        // Given a traceparent with an oversized span ID (17 hex chars)
        val ctx = TraceContext.fromW3CTraceparent("00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b70-01")

        // Then null is returned (must be exactly 16 hex chars)
        assertNull(ctx)
    }

    @Test
    fun rejectsInjectionAttemptInTraceId() {
        // Given a traceparent with SQL injection attempt in trace ID position
        val ctx = TraceContext.fromW3CTraceparent("00-'; DROP TABLE audit_event;--aaa-00f067aa0ba902b7-01")

        // Then null is returned (injection attempt rejected)
        assertNull(ctx)
    }

    @Test
    fun rejectsInjectionAttemptInSpanId() {
        // Given a traceparent with script injection in span ID position
        val ctx = TraceContext.fromW3CTraceparent("00-0af7651916cd43dd8448eb211c80319c-<script>alert-01")

        // Then null is returned
        assertNull(ctx)
    }

    @Test
    fun rejectsWhitespaceInTraceparent() {
        // Given a traceparent with whitespace
        val ctx = TraceContext.fromW3CTraceparent("00 -0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01")

        // Then null is returned
        assertNull(ctx)
    }

    @Test
    fun rejectsNewlinesInTraceparent() {
        // Given a traceparent with newline injection
        val ctx = TraceContext.fromW3CTraceparent("00-0af7651916cd43dd8448eb211c80319c\n-00f067aa0ba902b7-01")

        // Then null is returned
        assertNull(ctx)
    }

    @Test
    fun rejectsVeryLongInput() {
        // Given an extremely long traceparent string
        val longInput = "00-" + "a".repeat(10000) + "-00f067aa0ba902b7-01"
        val ctx = TraceContext.fromW3CTraceparent(longInput)

        // Then null is returned (not a valid W3C traceparent)
        assertNull(ctx)
    }

    @Test
    fun rejectsNullByteInTraceparent() {
        // Given a traceparent with null byte injection
        val ctx = TraceContext.fromW3CTraceparent("00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba9\u000002b7-01")

        // Then null is returned
        assertNull(ctx)
    }

    @Test
    fun rejectsUnicodeInTraceparent() {
        // Given a traceparent with unicode characters
        val ctx = TraceContext.fromW3CTraceparent("00-0af7651916cd43dd8448\u00e9b211c80319c-00f067aa0ba902b7-01")

        // Then null is returned
        assertNull(ctx)
    }
}

class TraceContextDataClassTest {
    @Test
    fun equalityWorks() {
        // Given two identical contexts
        val ctx1 = TraceContext("aabbccdd11223344aabbccdd11223344", "1122334455667788")
        val ctx2 = TraceContext("aabbccdd11223344aabbccdd11223344", "1122334455667788")

        // Then they are equal
        assertEquals(ctx1, ctx2)
        assertEquals(ctx1.hashCode(), ctx2.hashCode())
    }

    @Test
    fun copyWorks() {
        // Given a context
        val original = TraceContext("aabbccdd11223344aabbccdd11223344", "1122334455667788", traceFlags = 0)

        // When copying with a new flag
        val copy = original.copy(traceFlags = 1)

        // Then original is unchanged and copy has new value
        assertEquals(0, original.traceFlags)
        assertEquals(1, copy.traceFlags)
        assertEquals(original.traceId, copy.traceId)
    }
}
