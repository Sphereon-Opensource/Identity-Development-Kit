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
 */

package com.sphereon.core.api.http.query

import kotlin.test.Test
import kotlin.test.assertEquals

class PercentEncodingTest {
    @Test
    fun unreservedAsciiPassesThroughUnchanged() {
        val input = "ABCxyz0189-._~"
        assertEquals(input, percentEncodeQueryComponent(input))
    }

    @Test
    fun spaceEncodesAsPercent20() {
        assertEquals("hello%20world", percentEncodeQueryComponent("hello world"))
    }

    @Test
    fun plusEncodesAsPercent2B() {
        assertEquals("a%2Bb", percentEncodeQueryComponent("a+b"))
    }

    @Test
    fun ampersandEncodesAsPercent26() {
        assertEquals("a%26b", percentEncodeQueryComponent("a&b"))
    }

    @Test
    fun equalsEncodesAsPercent3D() {
        assertEquals("k%3Dv", percentEncodeQueryComponent("k=v"))
    }

    @Test
    fun multiByteUtf8EncodesAsBytewise() {
        // 'é' is U+00E9, UTF-8: C3 A9.
        assertEquals("%C3%A9", percentEncodeQueryComponent("é"))
    }

    @Test
    fun alreadyEncodedSequenceIsTreatedAsLiteralPercent() {
        // The helper assumes UNencoded input; a literal '%' becomes '%25', so a string that looks
        // pre-encoded round-trips as a doubly-encoded string. That matches RFC 3986 §2.4.
        assertEquals("%2520", percentEncodeQueryComponent("%20"))
    }

    @Test
    fun reservedQueryDelimitersAreEncoded() {
        assertEquals("a%3Db%26c%3Dd%3Fe%23f", percentEncodeQueryComponent("a=b&c=d?e#f"))
    }

    @Test
    fun emptyStringYieldsEmptyString() {
        assertEquals("", percentEncodeQueryComponent(""))
    }
}
