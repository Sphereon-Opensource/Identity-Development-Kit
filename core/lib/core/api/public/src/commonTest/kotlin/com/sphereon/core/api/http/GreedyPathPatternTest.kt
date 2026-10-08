package com.sphereon.core.api.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class GreedyPathPatternTest {
    @Test
    fun greedyCapturesOneSegmentWithCanonicalName() {
        val pattern = CompiledPathPattern.compile("/blobs/{path+}")
        assertTrue(pattern.matches("/blobs/hello.txt"))
        assertEquals(mapOf("path" to "hello.txt"), pattern.extractParams("/blobs/hello.txt"))
    }

    @Test
    fun greedyCapturesMultipleSegmentsAndDots() {
        val pattern = CompiledPathPattern.compile("/blobs/{path+}")
        assertEquals("a/b/archive.v2.tar.gz", pattern.extractParams("/blobs/a/b/archive.v2.tar.gz")["path"])
    }

    @Test
    fun greedyRequiresAtLeastOneSegment() {
        val pattern = CompiledPathPattern.compile("/blobs/{path+}")
        assertFalse(pattern.matches("/blobs/"))
        assertTrue(pattern.extractParams("/blobs/").isEmpty())
    }

    @Test
    fun greedyReservesFollowingContentOperation() {
        val pattern = CompiledPathPattern.compile("/stores/{storeId}/blobs/{path+}/content")
        assertTrue(pattern.matches("/stores/default/blobs/docs/content/start.md/content"))
        assertEquals(mapOf("storeId" to "default", "path" to "docs/content/start.md"), pattern.extractParams("/stores/default/blobs/docs/content/start.md/content"))
    }

    @Test
    fun greedyRejectsMissingOrDifferentOperationSuffix() {
        val pattern = CompiledPathPattern.compile("/blobs/{path+}/content")
        assertFalse(pattern.matches("/blobs/content"))
        assertFalse(pattern.matches("/blobs/a/stat"))
        assertTrue(pattern.extractParams("/blobs/a/stat").isEmpty())
    }

    @Test
    fun greedyDecodesEachCapturedSegment() {
        val pattern = CompiledPathPattern.compile("/blobs/{path+}/stat")
        assertEquals("a b/c/d.txt", pattern.extractParams("/blobs/a%20b/c%2Fd.txt/stat")["path"])
    }

    @Test
    fun greedyPreservesSuffixedSingleParameter() {
        val pattern = CompiledPathPattern.compile("/blobs/{path+}/{id}:validate")
        assertEquals(mapOf("path" to "a/b", "id" to "123"), pattern.extractParams("/blobs/a/b/123:validate"))
    }

    @Test
    fun greedyPreservesTrailingZeroLengthTailWildcard() {
        val pattern = CompiledPathPattern.compile("/blobs/{path+}/assets/{rest...}")
        assertTrue(pattern.matches("/blobs/a/assets"))
        assertEquals(mapOf("path" to "a", "rest" to ""), pattern.extractParams("/blobs/a/assets"))
    }

    @Test
    fun greedyRejectsUnnamedToken() {
        assertFailsWith<IllegalArgumentException> { CompiledPathPattern.compile("/blobs/{+}") }
    }

    @Test
    fun greedyRejectsWithinSegmentSuffix() {
        assertFailsWith<IllegalArgumentException> { CompiledPathPattern.compile("/blobs/{path+}:stat") }
    }

    @Test
    fun ordinaryTailWildcardStillMatchesEmpty() {
        val pattern = CompiledPathPattern.compile("/assets/{path...}")
        assertTrue(pattern.matches("/assets"))
        assertEquals("", pattern.extractParams("/assets")["path"])
        assertFailsWith<IllegalArgumentException> { CompiledPathPattern.compile("/assets/{path...}/content") }
    }

    @Test
    fun greedyRejectsDoubledPlusToken() {
        assertFailsWith<IllegalArgumentException> { CompiledPathPattern.compile("/blobs/{path++}") }
    }

    @Test
    fun multipleGreedyCapturesAreDeterministic() {
        val pattern = CompiledPathPattern.compile("/a/{first+}/pivot/{second+}/end")
        assertEquals(mapOf("first" to "x/pivot/y", "second" to "z"), pattern.extractParams("/a/x/pivot/y/pivot/z/end"))
    }

    @Test
    fun multipleGreedyBacktracksToRequireNonemptySecondCapture() {
        val pattern = CompiledPathPattern.compile("/a/{first+}/pivot/{second+}/end")
        assertEquals(mapOf("first" to "x", "second" to "y/pivot"), pattern.extractParams("/a/x/pivot/y/pivot/end"))
    }

    @Test
    fun multipleGreedyMissingSuffixReturnsNoPartialCaptures() {
        val pattern = CompiledPathPattern.compile("/a/{first+}/pivot/{second+}/end")
        val path = "/a/" + List(80) { "x/pivot" }.joinToString("/") + "/missing"
        assertFalse(pattern.matches(path))
        assertTrue(pattern.extractParams(path).isEmpty())
    }
}
