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

class IdnaTest {
    @Test
    fun pureAsciiPassthroughLowercased() {
        val r = Idna.toAscii("Example.COM")
        assertTrue(r.isOk)
        assertEquals("example.com", r.value)
    }

    @Test
    fun didWebvhSpecExampleDomain() {
        // From did:webvh v1.0 spec §3.1:
        //   did:webvh:{SCID}:jp納豆.例.jp:用户
        //   -> https://xn--jp-cd2fp15c.xn--fsq.jp/%E7%94%A8%E6%88%B7/did.jsonl
        // We assert only the host portion here; the path-segment percent-
        // encoding is handled elsewhere (WebvhDidUrlBuilder).
        val unicode = "jp納豆.例.jp"
        val r = Idna.toAscii(unicode)
        assertTrue(r.isOk, "toAscii should succeed: ${if (r.isErr) r.error else ""}")
        assertEquals("xn--jp-cd2fp15c.xn--fsq.jp", r.value)
    }

    @Test
    fun toUnicodeRoundTrips() {
        val unicode = "jp納豆.例.jp"
        val ascii = Idna.toAscii(unicode).value
        val back = Idna.toUnicode(ascii)
        assertTrue(back.isOk)
        assertEquals(unicode, back.value)
    }

    @Test
    fun emptyDomainRejected() {
        assertTrue(Idna.toAscii("").isErr)
    }

    @Test
    fun emptyLabelRejected() {
        assertTrue(Idna.toAscii("foo..bar").isErr)
    }

    @Test
    fun trailingDotPreserved() {
        // FQDN with trailing dot: every non-trailing label must be non-empty,
        // but the final empty label is allowed (root zone marker).
        val r = Idna.toAscii("example.com.")
        assertTrue(r.isOk)
        assertEquals("example.com.", r.value)
    }
}
