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

package com.sphereon.mdoc.engagement

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.mdoc.testutil.encodeCoseKey
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import com.sphereon.mdoc.transfer.reader.ReaderEngagementCborCodecImpl
import com.sphereon.mdoc.transfer.reader.ReaderEngagementSecurity
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for engagement method classes.
 */
class EngagementMethodTest {
    private val readerEngagementCborCodec = ReaderEngagementCborCodecImpl()

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

    @Test
    fun testReaderEngagementMethodFromReaderEngagementRoundTripsUri() {
        val readerEngagement = createReaderEngagement()
        val method = ReaderEngagementMethod(readerEngagement, readerEngagementCborCodec)

        val uri = method.getEngagementDataUri()
        val decoded = readerEngagementCborCodec.decodeUri(uri).getOrThrow().value

        assertEquals(EngagementType.TO_APP, method.type)
        assertEquals(readerEngagement.version, decoded.version)
        assertEquals(readerEngagement.security, decoded.security)
        assertEquals(
            (readerEngagement.deviceRetrievalMethods?.single()?.retrievalOptions as RestApiOptions).uri,
            (decoded.deviceRetrievalMethods?.single()?.retrievalOptions as RestApiOptions).uri,
        )
        assertContentEquals(decoded.original, method.readerEngagement.original ?: decoded.original)
    }

    @Test
    fun testReaderEngagementMethodFromUriPreservesOriginalBytes() {
        val original = createReaderEngagement()
        val uri = readerEngagementCborCodec.encodeUri(original, "mdoc://").getOrThrow()

        val method = ReaderEngagementMethod(readerEngagementCborCodec.decodeUri(uri).getOrThrow().value, readerEngagementCborCodec)

        assertEquals(uri, method.getEngagementDataUri())
        assertContentEquals(readerEngagementCborCodec.decodeUri(uri).getOrThrow().originalBytes, method.readerEngagement.original)
    }

    @Test
    fun testReaderEngagementMethodRejectsOid4vpQueryParameterUri() {
        val result = readerEngagementCborCodec.decodeUri("mdoc-openid4vp://?client_id=test")
        assertTrue(result.isErr)
    }

    @Test
    fun testReaderEngagementMethodRejectsMalformedPayloadUri() {
        val result = readerEngagementCborCodec.decodeUri("mdoc://%%%")
        assertTrue(result.isErr)
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

    private fun createReaderEngagement(): ReaderEngagement =
        ReaderEngagement.V1_0(
            security =
                ReaderEngagementSecurity(
                    cipherSuite = 1u,
                    eReaderKeyBytes = createTestCoseKey().let { CborEncodedItem<CoseKeyType>(encodeCoseKey(it), it) },
                ),
            deviceRetrievalMethods =
                arrayOf(
                    DeviceRetrievalMethod(
                        type = DeviceRetrievalMethodType.WEBSITE,
                        retrievalOptions = RestApiOptions("https://reader.example.com/mdoc/session/123"),
                    ),
                ),
            protocolInfo = null,
            additionalItems = null,
            original = null,
        )

    private fun createTestCoseKey(): CoseKey =
        CoseKeyJson
            .Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()
}
