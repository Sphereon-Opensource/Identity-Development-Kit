/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.core.idn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PunycodeTest {
    @Test
    fun rfc3492_arabic_egyptian() {
        // RFC 3492 §7.1 (A) Arabic (Egyptian)
        val input = "ليهمابتكلموشعربي؟"
        val expected = "egbpdaj6bu4bxfgehfvwxn"
        val encoded = Punycode.encode(input)
        assertTrue(encoded.isOk)
        assertEquals(expected, encoded.value)
        val decoded = Punycode.decode(expected)
        assertTrue(decoded.isOk)
        assertEquals(input, decoded.value)
    }

    @Test
    fun rfc3492_chinese_simplified() {
        // RFC 3492 §7.1 (B) Chinese (simplified)
        val input = "他们为什么不说中文"
        val expected = "ihqwcrb4cv8a8dqg056pqjye"
        val encoded = Punycode.encode(input)
        assertTrue(encoded.isOk, "encode should succeed: ${if (encoded.isErr) encoded.error else ""}")
        assertEquals(expected, encoded.value)
        val decoded = Punycode.decode(expected)
        assertTrue(decoded.isOk)
        assertEquals(input, decoded.value)
    }

    @Test
    fun rfc3492_german() {
        // RFC 3492 §7.1 (G) German "bücher"
        val input = "bücher"
        val expected = "bcher-kva"
        val encoded = Punycode.encode(input)
        assertTrue(encoded.isOk)
        assertEquals(expected, encoded.value)
        val decoded = Punycode.decode(expected)
        assertTrue(decoded.isOk)
        assertEquals(input, decoded.value)
    }

    @Test
    fun example_jp_natto() {
        // Single label of the did:webvh spec example domain
        val input = "jp納豆"
        val encoded = Punycode.encode(input)
        assertTrue(encoded.isOk)
        // Round-trip is the spec-relevant property; the exact ACE form is then
        // covered by IdnaTest using the xn-- prefix.
        val decoded = Punycode.decode(encoded.value)
        assertTrue(decoded.isOk)
        assertEquals(input, decoded.value)
    }

    @Test
    fun all_ascii_input_is_basic_only() {
        // Pure ASCII labels still encode as basic-only (no extended portion).
        // The ACE form is "<basic>-" (trailing delimiter, nothing after).
        val encoded = Punycode.encode("example")
        assertTrue(encoded.isOk)
        assertEquals("example-", encoded.value)
    }
}
