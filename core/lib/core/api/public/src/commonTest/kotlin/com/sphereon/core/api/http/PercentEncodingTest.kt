/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.http

import kotlin.test.Test
import kotlin.test.assertEquals

class PercentEncodingTest {
    // ===== Basic decoding =====

    @Test
    fun decodesSinglePercentSequence() = assertEquals(":", "%3A".percentDecode())

    @Test
    fun decodesMultiplePercentSequences() =
        assertEquals(
            "did:jwk:eyJjcnYiOiJQLTI1NiJ9",
            "did%3Ajwk%3AeyJjcnYiOiJQLTI1NiJ9".percentDecode(),
        )

    @Test
    fun passesThroughUnencodedAscii() = assertEquals("hello world", "hello%20world".percentDecode())

    @Test
    fun passesThroughEmptyString() = assertEquals("", "".percentDecode())

    @Test
    fun fastPathReturnsSameStringWhenNothingToDecode() {
        val s = "did:jwk:abc"
        assertEquals(s, s.percentDecode())
    }

    // ===== plusAsSpace policy =====

    @Test
    fun keepsPlusLiteralByDefault() = assertEquals("hello+world", "hello+world".percentDecode())

    @Test
    fun translatesPlusToSpaceWhenFormEncoded() = assertEquals("hello world", "hello+world".percentDecode(plusAsSpace = true))

    @Test
    fun base64PlusIsPreservedInPathSegmentMode() = assertEquals("abc+def==", "abc+def==".percentDecode(plusAsSpace = false))

    // ===== UTF-8 multi-byte =====

    @Test
    fun decodesUtf8EuroSign() = assertEquals("€", "%E2%82%AC".percentDecode())

    @Test
    fun decodesMixedAsciiAndUtf8() = assertEquals("100 €", "100%20%E2%82%AC".percentDecode())

    @Test
    fun decodesUtf8ConsecutiveBytesBeforeLiteralChar() = assertEquals("€!", "%E2%82%AC%21".percentDecode())

    // ===== Malformed input is lenient =====

    @Test
    fun trailingPercentIsPassedThrough() = assertEquals("abc%", "abc%".percentDecode())

    @Test
    fun truncatedPercentIsPassedThrough() = assertEquals("abc%2", "abc%2".percentDecode())

    @Test
    fun nonHexAfterPercentIsPassedThrough() = assertEquals("a%ZZ", "a%ZZ".percentDecode())

    // ===== CompiledPathPattern decoding =====

    @Test
    fun extractParamsDecodesPercentEncodedDid() {
        val pattern = CompiledPathPattern.compile("/dids/{did}")
        val raw = "did:jwk:eyJjcnYiOiJQLTI1NiJ9"
        // Simulate what an OpenAPI codegen client would send.
        val encoded = "/dids/did%3Ajwk%3AeyJjcnYiOiJQLTI1NiJ9"
        assertEquals(mapOf("did" to raw), pattern.extractParams(encoded))
    }

    @Test
    fun extractParamsPassesThroughUnencodedDid() {
        val pattern = CompiledPathPattern.compile("/dids/{did}")
        val raw = "did:jwk:eyJjcnYiOiJQLTI1NiJ9"
        assertEquals(mapOf("did" to raw), pattern.extractParams("/dids/$raw"))
    }

    @Test
    fun extractParamsDecodesEveryParameter() {
        val pattern = CompiledPathPattern.compile("/dids/{did}/services/{serviceId}")
        val decoded =
            pattern.extractParams("/dids/did%3Ajwk%3AeyJ.../services/svc%3A123")
        assertEquals("did:jwk:eyJ...", decoded["did"])
        assertEquals("svc:123", decoded["serviceId"])
    }
}
