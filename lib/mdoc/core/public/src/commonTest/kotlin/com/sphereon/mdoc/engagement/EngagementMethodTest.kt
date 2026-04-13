/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.mdoc.engagement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for engagement method classes.
 */
class EngagementMethodTest {

    // QREngagementMethod tests

    @Test
    fun testQREngagementMethodDefaultScheme() {
        val method = QREngagementMethod()
        assertEquals("mdoc:", method.scheme)
        assertEquals(EngagementType.QR, method.type)
    }

    @Test
    fun testQREngagementMethodCustomScheme() {
        val method = QREngagementMethod(scheme = "custom:")
        assertEquals("custom:", method.scheme)
        assertEquals(EngagementType.QR, method.type)
    }

    @Test
    fun testQREngagementMethodEquality() {
        val method1 = QREngagementMethod()
        val method2 = QREngagementMethod()
        assertEquals(method1, method2)
    }

    @Test
    fun testQREngagementMethodHashCode() {
        val method1 = QREngagementMethod()
        val method2 = QREngagementMethod()
        assertEquals(method1.hashCode(), method2.hashCode())
    }

    // NfcEngagementMethod tests

    @Test
    fun testNfcEngagementMethodType() {
        val method = NfcEngagementMethod()
        assertEquals(EngagementType.NFC, method.type)
    }

    // Oid4vpEngagementMethod tests

    @Test
    fun testOid4vpEngagementMethodValidUri() {
        val uri = "mdoc-openid4vp://?client_id=example.com&request_uri=https://example.com/request"
        val method = Oid4vpEngagementMethod(uri)
        assertEquals(uri, method.authorizationRequestUri)
        assertEquals(EngagementType.TO_APP, method.type)
    }

    @Test
    fun testOid4vpEngagementMethodInvalidUriThrows() {
        assertFailsWith<IllegalArgumentException> {
            Oid4vpEngagementMethod("https://example.com/invalid")
        }
    }

    @Test
    fun testOid4vpEngagementMethodEmptyUriThrows() {
        assertFailsWith<IllegalArgumentException> {
            Oid4vpEngagementMethod("")
        }
    }

    @Test
    fun testOid4vpEngagementMethodWrongSchemeThrows() {
        assertFailsWith<IllegalArgumentException> {
            Oid4vpEngagementMethod("mdoc://not-oid4vp")
        }
    }

    @Test
    fun testOid4vpEngagementMethodEquality() {
        val uri = "mdoc-openid4vp://?client_id=test"
        val method1 = Oid4vpEngagementMethod(uri)
        val method2 = Oid4vpEngagementMethod(uri)
        assertEquals(method1, method2)
    }

    @Test
    fun testOid4vpEngagementMethodHashCode() {
        val uri = "mdoc-openid4vp://?client_id=test"
        val method1 = Oid4vpEngagementMethod(uri)
        val method2 = Oid4vpEngagementMethod(uri)
        assertEquals(method1.hashCode(), method2.hashCode())
    }

    // MdocEngagementMethod interface tests

    @Test
    fun testAllMethodsImplementMdocEngagementMethod() {
        val qrMethod: MdocEngagementMethod = QREngagementMethod()
        val nfcMethod: MdocEngagementMethod = NfcEngagementMethod()
        val oid4vpMethod: MdocEngagementMethod = Oid4vpEngagementMethod("mdoc-openid4vp://?test=1")

        assertEquals(EngagementType.QR, qrMethod.type)
        assertEquals(EngagementType.NFC, nfcMethod.type)
        assertEquals(EngagementType.TO_APP, oid4vpMethod.type)
    }
}
