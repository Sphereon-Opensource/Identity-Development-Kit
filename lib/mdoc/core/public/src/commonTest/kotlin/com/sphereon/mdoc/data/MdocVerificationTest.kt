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

package com.sphereon.mdoc.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for MdocVerification enum.
 */
class MdocVerificationTest {
    @Test
    fun testMdocVerificationEntries() {
        val entries = MdocVerification.entries
        assertEquals(5, entries.size)
        assertTrue(entries.contains(MdocVerification.CERTIFICATE_CHAIN))
        assertTrue(entries.contains(MdocVerification.ISSUER_AUTH_SIGNATURE))
        assertTrue(entries.contains(MdocVerification.DIGEST_VALUES))
        assertTrue(entries.contains(MdocVerification.DOC_TYPE))
        assertTrue(entries.contains(MdocVerification.VALIDITY))
    }

    @Test
    fun testMdocVerificationAll() {
        val all = MdocVerification.ALL
        assertEquals(5, all.size)
        assertTrue(all.contains(MdocVerification.CERTIFICATE_CHAIN))
        assertTrue(all.contains(MdocVerification.ISSUER_AUTH_SIGNATURE))
        assertTrue(all.contains(MdocVerification.DIGEST_VALUES))
        assertTrue(all.contains(MdocVerification.DOC_TYPE))
        assertTrue(all.contains(MdocVerification.VALIDITY))
    }

    @Test
    fun testMdocVerificationIssuerAuth() {
        val issuerAuth = MdocVerification.ISSUER_AUTH
        assertEquals(3, issuerAuth.size)
        assertTrue(issuerAuth.contains(MdocVerification.CERTIFICATE_CHAIN))
        assertTrue(issuerAuth.contains(MdocVerification.ISSUER_AUTH_SIGNATURE))
        assertTrue(issuerAuth.contains(MdocVerification.VALIDITY))
    }

    @Test
    fun testMdocVerificationDocument() {
        val document = MdocVerification.DOCUMENT
        // DOCUMENT should equal ALL
        assertEquals(MdocVerification.ALL, document)
        assertEquals(5, document.size)
    }

    @Test
    fun testMdocVerificationEnumValues() {
        assertEquals("CERTIFICATE_CHAIN", MdocVerification.CERTIFICATE_CHAIN.name)
        assertEquals("ISSUER_AUTH_SIGNATURE", MdocVerification.ISSUER_AUTH_SIGNATURE.name)
        assertEquals("DIGEST_VALUES", MdocVerification.DIGEST_VALUES.name)
        assertEquals("DOC_TYPE", MdocVerification.DOC_TYPE.name)
        assertEquals("VALIDITY", MdocVerification.VALIDITY.name)
    }

    @Test
    fun testMdocVerificationOrdinals() {
        assertEquals(0, MdocVerification.CERTIFICATE_CHAIN.ordinal)
        assertEquals(1, MdocVerification.ISSUER_AUTH_SIGNATURE.ordinal)
        assertEquals(2, MdocVerification.DIGEST_VALUES.ordinal)
        assertEquals(3, MdocVerification.DOC_TYPE.ordinal)
        assertEquals(4, MdocVerification.VALIDITY.ordinal)
    }
}
