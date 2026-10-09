package com.sphereon.core.api.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class BoundedGreedyPathPatternTest {
    @Test
    fun multipleGreedyRejectionHasPatternTimesPathTransitionBound() {
        val pattern = CompiledPathPattern.compile("/{first+}/{second+}/end")
        for (size in listOf(64, 128, 512)) {
            val raw = List(size) { "x" }
            var transitions = 0L
            assertNull(pattern.matchGreedySegments(raw, onTransition = { transitions++ }))
            assertTrue(transitions > 0, "The observer must see actual engine work")
            assertTrue(transitions <= 3L * (size + 1) + 3,
                "Repeated endpoint enumeration exceeded the independently derived bound: $transitions at N=$size")
            val path = "/" + raw.joinToString("/")
            assertFalse(pattern.matches(path))
            assertEquals(emptyMap(), pattern.extractParams(path))
        }
    }

    @Test
    fun successfulAdjacentGreediesRetainLongestFirstWithinTheSameBound() {
        val pattern = CompiledPathPattern.compile("/{first+}/{second+}/end")
        val raw = List(128) { "x" } + listOf("y", "end")
        var transitions = 0L
        val captures = pattern.matchGreedySegments(raw, onTransition = { transitions++ })
        assertEquals(listOf(
            CompiledPathPattern.Segment.Parameter("first+") to (0..127),
            CompiledPathPattern.Segment.Parameter("second+") to (128..128),
        ), captures)
        assertTrue(transitions > 0)
        assertTrue(transitions <= 3L * 131 + 3)
        assertEquals(mapOf("first" to List(128) { "x" }.joinToString("/"), "second" to "y"),
            pattern.extractParams("/" + raw.joinToString("/")))
    }

    @Test
    fun adjacentAndRepeatedLiteralPartitionsUseLiteralLongestFirstOracles() {
        val adjacent = CompiledPathPattern.compile("/{first+}/{second+}/end")
        assertEquals(mapOf("first" to "a/b", "second" to "c"), adjacent.extractParams("/a/b/c/end"))
        assertEquals(mapOf("first" to "a", "second" to "b"), adjacent.extractParams("/a/b/end"))
        assertFalse(adjacent.matches("/a/end"))
        assertEquals(emptyMap(), adjacent.extractParams("/a/end"))
        val repeated = CompiledPathPattern.compile("/a/{first+}/pivot/{second+}/end")
        assertEquals(mapOf("first" to "x/pivot/y", "second" to "z"),
            repeated.extractParams("/a/x/pivot/y/pivot/z/end"))
        assertEquals(mapOf("first" to "x", "second" to "y/pivot"),
            repeated.extractParams("/a/x/pivot/y/pivot/end"))
        val three = CompiledPathPattern.compile("/{one+}/{two+}/{three+}/end")
        assertEquals(mapOf("one" to "a/b", "two" to "c", "three" to "d"),
            three.extractParams("/a/b/c/d/end"))
    }

    @Test
    fun suffixPredicateReservesANonemptyOrdinaryCapture() {
        val pattern = CompiledPathPattern.compile("/{first+}/{id}:validate")
        assertEquals(mapOf("first" to "a/b", "id" to "123"), pattern.extractParams("/a/b/123:validate"))
        assertFalse(pattern.matches("/a/:validate"))
        assertFalse(pattern.matches("/a/123:other"))
        assertEquals(emptyMap(), pattern.extractParams("/a/:validate"))
    }

    @Test
    fun feasibilityUsesRawSegmentsAndOnlyAcceptedCapturesAreDecoded() {
        val pattern = CompiledPathPattern.compile("/{first+}/{id}:validate/end")
        assertEquals(mapOf("first" to "a/b/c d", "id" to "did:x"),
            pattern.extractParams("/a%2Fb/c%20d/did%3Ax:validate/end"))
        assertFalse(pattern.matches("/a/id%3Avalidate/end"))
        assertFalse(pattern.matches("/a/id:validate/%65nd"))
        assertEquals(emptyMap(), pattern.extractParams("/a/id:validate/%65nd"))
        assertEquals(mapOf("first" to "a/b", "second" to "c"),
            CompiledPathPattern.compile("/{first+}/{second+}/end").extractParams("//a///b/c/end/"))
    }

    @Test
    fun greedyAndTailWildcardRetainZeroLengthAndLongestFirstSemantics() {
        val pattern = CompiledPathPattern.compile("/{first+}/assets/{rest...}")
        assertEquals(mapOf("first" to "a", "rest" to ""), pattern.extractParams("/a/assets"))
        assertEquals(mapOf("first" to "a/assets/b", "rest" to ""),
            pattern.extractParams("/a/assets/b/assets"))
        assertEquals(mapOf("first" to "a", "rest" to "c/d"), pattern.extractParams("/a/assets/c%2Fd"))
        assertFalse(pattern.matches("/assets"))
        val adjacentTail = CompiledPathPattern.compile("/{first+}/{rest...}")
        assertEquals(mapOf("first" to "a/b", "rest" to ""), adjacentTail.extractParams("/a/b"))
    }

    @Test
    fun ordinaryAndTailOnlyFastPathsRetainTheirExistingResults() {
        assertEquals(mapOf("id" to "did:x"),
            CompiledPathPattern.compile("/keys/{id}:validate").extractParams("/keys/did%3Ax:validate"))
        assertFalse(CompiledPathPattern.compile("/keys/{id}:validate").matches("/keys/:validate"))
        assertEquals(mapOf("path" to ""), CompiledPathPattern.compile("/assets/{path...}").extractParams("/assets"))
        assertTrue(CompiledPathPattern.compile("/").matches("//"))
        assertFalse(CompiledPathPattern.compile("/end").matches("/%65nd"))
    }

    @Test
    fun malformedGreedyAndNontailWildcardDeclarationsRemainRejected() {
        for (pattern in listOf("/{+}", "/{path++}", "/{path+}:stat", "/{path...}/end")) {
            assertFailsWith<IllegalArgumentException> { CompiledPathPattern.compile(pattern) }
        }
    }
}
