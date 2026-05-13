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

package com.sphereon.jsonld

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IriTest {
    @Test
    fun parsesAbsoluteHttpsIri() {
        val iri = assertNotNull(Iri.tryParse("https://example.com:8080/a/b?x=1#frag"))
        assertTrue(iri.isAbsolute)
        assertEquals("https", iri.scheme)
        assertEquals("example.com:8080", iri.authority)
        assertEquals("/a/b", iri.path)
        assertEquals("x=1", iri.query)
        assertEquals("frag", iri.fragment)
    }

    @Test
    fun parsesIriWithEmptyAuthority() {
        val iri = assertNotNull(Iri.tryParse("file:///etc/hosts"))
        assertEquals("file", iri.scheme)
        assertEquals("", iri.authority)
        assertEquals("/etc/hosts", iri.path)
    }

    @Test
    fun parsesUntpContextIri() {
        val iri = assertNotNull(Iri.tryParse("https://vocabulary.uncefact.org/untp/"))
        assertEquals("https", iri.scheme)
        assertEquals("vocabulary.uncefact.org", iri.authority)
        assertEquals("/untp/", iri.path)
        assertNull(iri.query)
        assertNull(iri.fragment)
    }

    @Test
    fun parsesRelativeReferenceWithoutScheme() {
        val iri = assertNotNull(Iri.tryParse("/path/to/resource"))
        assertFalse(iri.isAbsolute)
        assertNull(iri.scheme)
        assertNull(iri.authority)
        assertEquals("/path/to/resource", iri.path)
    }

    @Test
    fun parsesQueryOnlyAndFragmentOnly() {
        val q = assertNotNull(Iri.tryParse("?q=1"))
        assertEquals("", q.path)
        assertEquals("q=1", q.query)

        val f = assertNotNull(Iri.tryParse("#frag"))
        assertEquals("", f.path)
        assertEquals("frag", f.fragment)
    }

    @Test
    fun parsesUnicodeHostAndPath() {
        val iri = assertNotNull(Iri.tryParse("https://例.example/路径"))
        assertEquals("https", iri.scheme)
        assertEquals("例.example", iri.authority)
        assertEquals("/路径", iri.path)
    }

    @Test
    fun rejectsSpaceAndControlCharacters() {
        assertNull(Iri.tryParse("https://example.com/has space"))
        assertNull(Iri.tryParse("https://example.com/with\ttab"))
        assertNull(Iri.tryParse("https://example.com/with\nnewline"))
    }

    @Test
    fun rejectsSchemeNotStartingWithLetter() {
        assertNull(Iri.tryParse("1bad:foo"))
        assertNull(Iri.tryParse("+bad:foo"))
    }

    @Test
    fun toStringIsIdentity() {
        val raw = "https://example.com/x?y#z"
        assertEquals(raw, assertNotNull(Iri.tryParse(raw)).toString())
    }

    @Test
    fun unsafeOfBypassesValidation() {
        // Caller asserts well-formedness; we just wrap. Component access is
        // best-effort per the regex.
        val iri = Iri.unsafeOf("ill formed but accepted")
        assertEquals("ill formed but accepted", iri.value)
    }

    // RFC 3986 §5.4.1 normal examples: base = http://a/b/c/d;p?q
    @Test
    fun resolveRfc3986Section5_4_1NormalExamples() {
        val base = assertNotNull(Iri.tryParse("http://a/b/c/d;p?q"))

        fun ref(r: String) = assertNotNull(Iri.tryParse(r))

        assertEquals("g:h", base.resolve(ref("g:h")).toString())
        assertEquals("http://a/b/c/g", base.resolve(ref("g")).toString())
        assertEquals("http://a/b/c/g", base.resolve(ref("./g")).toString())
        assertEquals("http://a/b/c/g/", base.resolve(ref("g/")).toString())
        assertEquals("http://a/g", base.resolve(ref("/g")).toString())
        assertEquals("http://g", base.resolve(ref("//g")).toString())
        assertEquals("http://a/b/c/d;p?y", base.resolve(ref("?y")).toString())
        assertEquals("http://a/b/c/g?y", base.resolve(ref("g?y")).toString())
        assertEquals("http://a/b/c/d;p?q#s", base.resolve(ref("#s")).toString())
        assertEquals("http://a/b/c/g#s", base.resolve(ref("g#s")).toString())
        assertEquals("http://a/b/c/g?y#s", base.resolve(ref("g?y#s")).toString())
        assertEquals("http://a/b/c/;x", base.resolve(ref(";x")).toString())
        assertEquals("http://a/b/c/g;x", base.resolve(ref("g;x")).toString())
        assertEquals("http://a/b/c/g;x?y#s", base.resolve(ref("g;x?y#s")).toString())
        assertEquals("http://a/b/c/d;p?q", base.resolve(ref("")).toString())
        assertEquals("http://a/b/c/", base.resolve(ref(".")).toString())
        assertEquals("http://a/b/c/", base.resolve(ref("./")).toString())
        assertEquals("http://a/b/", base.resolve(ref("..")).toString())
        assertEquals("http://a/b/", base.resolve(ref("../")).toString())
        assertEquals("http://a/b/g", base.resolve(ref("../g")).toString())
        assertEquals("http://a/", base.resolve(ref("../..")).toString())
        assertEquals("http://a/", base.resolve(ref("../../")).toString())
        assertEquals("http://a/g", base.resolve(ref("../../g")).toString())
    }

    // RFC 3986 §5.4.2 abnormal examples: edge cases around ./ ../ traversal.
    @Test
    fun resolveRfc3986Section5_4_2AbnormalExamples() {
        val base = assertNotNull(Iri.tryParse("http://a/b/c/d;p?q"))

        fun ref(r: String) = assertNotNull(Iri.tryParse(r))

        assertEquals("http://a/g", base.resolve(ref("../../../g")).toString())
        assertEquals("http://a/g", base.resolve(ref("../../../../g")).toString())
        assertEquals("http://a/g", base.resolve(ref("/./g")).toString())
        assertEquals("http://a/g", base.resolve(ref("/../g")).toString())
        assertEquals("http://a/b/c/g.", base.resolve(ref("g.")).toString())
        assertEquals("http://a/b/c/.g", base.resolve(ref(".g")).toString())
        assertEquals("http://a/b/c/g..", base.resolve(ref("g..")).toString())
        assertEquals("http://a/b/c/..g", base.resolve(ref("..g")).toString())

        assertEquals("http://a/b/g", base.resolve(ref("./../g")).toString())
        assertEquals("http://a/b/c/g/", base.resolve(ref("./g/.")).toString())
        assertEquals("http://a/b/c/g/h", base.resolve(ref("g/./h")).toString())
        assertEquals("http://a/b/c/h", base.resolve(ref("g/../h")).toString())
        assertEquals("http://a/b/c/g;x=1/y", base.resolve(ref("g;x=1/./y")).toString())
        assertEquals("http://a/b/c/y", base.resolve(ref("g;x=1/../y")).toString())
    }

    @Test
    fun resolveAbsoluteReferenceReturnsReference() {
        val base = assertNotNull(Iri.tryParse("https://base.example/x/"))
        val ref = assertNotNull(Iri.tryParse("https://other.example/y"))
        assertEquals("https://other.example/y", base.resolve(ref).toString())
    }

    @Test
    fun resolveStringOverloadReturnsNullForInvalid() {
        val base = assertNotNull(Iri.tryParse("https://base.example/"))
        assertNull(base.resolve("has space"))
    }
}
