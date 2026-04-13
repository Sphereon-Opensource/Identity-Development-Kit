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

package com.sphereon.mdoc.transfer.reader

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborNil
import com.sphereon.cbor.CborString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for Handover classes (QrHandover, NfcHandover, OID4VPHandover, RestApiHandover).
 */
class HandoverTest {

    // QrHandover tests

    @Test
    fun testQrHandoverCreation() {
        val handover = QrHandover()
        assertNotNull(handover)
    }

    @Test
    fun testQrHandoverCborBuilder() {
        val handover = QrHandover()
        val builder = handover.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testQrHandoverFromCborStructure() {
        val cborNil = CborNil()
        val handover = QrHandover.fromCborStructure(cborNil)
        assertNotNull(handover)
    }

    @Test
    fun testQrHandoverEncode() {
        val handover = QrHandover()
        val bytes = handover.encodeCbor()
        // QR handover encodes to CBOR nil
        assertNotNull(bytes)
        assertEquals(1, bytes.size) // CBOR nil is single byte 0xF6
    }

    // NfcHandover tests

    @Test
    fun testNfcHandoverCreation() {
        val selectMessage = byteArrayOf(0x01, 0x02, 0x03)
        val handover = NfcHandover(selectMessage, null)

        assertEquals(3, handover.handoverSelectMessage.size)
        assertNull(handover.handoverRequestMessage)
    }

    @Test
    fun testNfcHandoverWithRequestMessage() {
        val selectMessage = byteArrayOf(0x01, 0x02, 0x03)
        val requestMessage = byteArrayOf(0x04, 0x05)
        val handover = NfcHandover(selectMessage, requestMessage)

        assertEquals(3, handover.handoverSelectMessage.size)
        assertEquals(2, handover.handoverRequestMessage?.size)
    }

    @Test
    fun testNfcHandoverCborBuilder() {
        val selectMessage = byteArrayOf(0x01, 0x02, 0x03)
        val handover = NfcHandover(selectMessage, null)
        val builder = handover.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testNfcHandoverFromCborStructure() {
        val selectMessage = byteArrayOf(0x01, 0x02, 0x03)
        val structure = CborArray(mutableListOf<CborItem<*>>(
            CborByteString(selectMessage),
            CborNil()
        ))

        val handover = NfcHandover.fromCborStructure(structure as CborArray<CborItem<*>>)
        assertTrue(selectMessage.contentEquals(handover.handoverSelectMessage))
        assertNull(handover.handoverRequestMessage)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testNfcHandoverFromCborStructureWithRequest() {
        val selectMessage = byteArrayOf(0x01, 0x02, 0x03)
        val requestMessage = byteArrayOf(0x04, 0x05)
        val structure = CborArray(mutableListOf<CborItem<*>>(
            CborByteString(selectMessage),
            CborByteString(requestMessage)
        ))

        val handover = NfcHandover.fromCborStructure(structure as CborArray<CborItem<*>>)
        assertTrue(selectMessage.contentEquals(handover.handoverSelectMessage))
        assertTrue(requestMessage.contentEquals(handover.handoverRequestMessage!!))
    }

    @Test
    fun testNfcHandoverEncodeDecode() {
        val selectMessage = byteArrayOf(0x01, 0x02, 0x03)
        val handover = NfcHandover(selectMessage, null)
        val bytes = handover.encodeCbor()
        val decoded = NfcHandover.decodeCbor(bytes)

        assertTrue(selectMessage.contentEquals(decoded.handoverSelectMessage))
    }

    @Test
    fun testNfcHandoverCompanionLabels() {
        assertEquals(0, NfcHandover.HANDOVER_SELECT_MESSAGE)
        assertEquals(1, NfcHandover.HANDOVER_REQUEST_MESSAGE)
    }

    // OID4VPHandover tests

    @Test
    fun testOid4vpHandoverCreation() {
        val clientIdHash = byteArrayOf(0x01, 0x02, 0x03)
        val responseUriHash = byteArrayOf(0x04, 0x05, 0x06)
        val nonce = "test-nonce"

        val handover = OID4VPHandover(clientIdHash, responseUriHash, nonce)

        assertTrue(clientIdHash.contentEquals(handover.clientIdHash))
        assertTrue(responseUriHash.contentEquals(handover.responseUriHash))
        assertEquals("test-nonce", handover.nonce)
    }

    @Test
    fun testOid4vpHandoverCborBuilder() {
        val handover = OID4VPHandover(
            byteArrayOf(0x01),
            byteArrayOf(0x02),
            "nonce"
        )
        val builder = handover.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testOid4vpHandoverFromCborStructure() {
        val clientIdHash = byteArrayOf(0x01, 0x02)
        val responseUriHash = byteArrayOf(0x03, 0x04)
        val nonce = "test-nonce"

        val structure = CborArray(mutableListOf<CborItem<*>>(
            CborByteString(clientIdHash),
            CborByteString(responseUriHash),
            CborString(nonce)
        ))

        val handover = OID4VPHandover.fromCborStructure(structure as CborArray<CborItem<*>>)
        assertTrue(clientIdHash.contentEquals(handover.clientIdHash))
        assertTrue(responseUriHash.contentEquals(handover.responseUriHash))
        assertEquals(nonce, handover.nonce)
    }

    @Test
    fun testOid4vpHandoverEquality() {
        val handover1 = OID4VPHandover(byteArrayOf(0x01), byteArrayOf(0x02), "nonce")
        val handover2 = OID4VPHandover(byteArrayOf(0x01), byteArrayOf(0x02), "nonce")
        assertEquals(handover1, handover2)
    }

    @Test
    fun testOid4vpHandoverEqualitySameInstance() {
        val handover = OID4VPHandover(byteArrayOf(0x01), byteArrayOf(0x02), "nonce")
        assertEquals(handover, handover)
    }

    @Test
    fun testOid4vpHandoverInequalityDifferentType() {
        val handover = OID4VPHandover(byteArrayOf(0x01), byteArrayOf(0x02), "nonce")
        assertNotEquals<Any>(handover, "not a handover")
    }

    @Test
    fun testOid4vpHandoverInequalityNull() {
        val handover = OID4VPHandover(byteArrayOf(0x01), byteArrayOf(0x02), "nonce")
        assertNotEquals<Any?>(handover, null)
    }

    @Test
    fun testOid4vpHandoverInequalityClientIdHash() {
        val handover1 = OID4VPHandover(byteArrayOf(0x01), byteArrayOf(0x02), "nonce")
        val handover2 = OID4VPHandover(byteArrayOf(0x99.toByte()), byteArrayOf(0x02), "nonce")
        assertNotEquals(handover1, handover2)
    }

    @Test
    fun testOid4vpHandoverInequalityResponseUriHash() {
        val handover1 = OID4VPHandover(byteArrayOf(0x01), byteArrayOf(0x02), "nonce")
        val handover2 = OID4VPHandover(byteArrayOf(0x01), byteArrayOf(0x99.toByte()), "nonce")
        assertNotEquals(handover1, handover2)
    }

    @Test
    fun testOid4vpHandoverInequalityNonce() {
        val handover1 = OID4VPHandover(byteArrayOf(0x01), byteArrayOf(0x02), "nonce1")
        val handover2 = OID4VPHandover(byteArrayOf(0x01), byteArrayOf(0x02), "nonce2")
        assertNotEquals(handover1, handover2)
    }

    @Test
    fun testOid4vpHandoverHashCode() {
        val handover1 = OID4VPHandover(byteArrayOf(0x01), byteArrayOf(0x02), "nonce")
        val handover2 = OID4VPHandover(byteArrayOf(0x01), byteArrayOf(0x02), "nonce")
        assertEquals(handover1.hashCode(), handover2.hashCode())
    }

    @Test
    fun testOid4vpHandoverCompanionLabels() {
        assertEquals(0, OID4VPHandover.CLIENT_ID_HASH)
        assertEquals(1, OID4VPHandover.RESPONSE_URI_HASH)
        assertEquals(2, OID4VPHandover.AUTHORIZATION_REQUEST_NONCE)
    }

    @Test
    fun testOid4vpHandoverEncodeDecode() {
        val handover = OID4VPHandover(byteArrayOf(0x01, 0x02), byteArrayOf(0x03, 0x04), "nonce")
        val bytes = handover.encodeCbor()
        val decoded = OID4VPHandover.decodeCbor(bytes)

        assertEquals(handover, decoded)
    }

    // RestApiHandover tests

    @Test
    fun testRestApiHandoverCreation() {
        val hash = byteArrayOf(0x01, 0x02, 0x03)
        val handover = RestApiHandover(hash)

        assertTrue(hash.contentEquals(handover.readerEngagementHash))
    }

    @Test
    fun testRestApiHandoverCborBuilder() {
        val handover = RestApiHandover(byteArrayOf(0x01, 0x02))
        val builder = handover.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testRestApiHandoverFromCborStructure() {
        val hash = byteArrayOf(0x01, 0x02, 0x03)
        val structure = CborByteString(hash)

        val handover = RestApiHandover.fromCborStructure(structure)
        assertTrue(hash.contentEquals(handover.readerEngagementHash))
    }

    @Test
    fun testRestApiHandoverEquality() {
        val handover1 = RestApiHandover(byteArrayOf(0x01, 0x02))
        val handover2 = RestApiHandover(byteArrayOf(0x01, 0x02))
        assertEquals(handover1, handover2)
    }

    @Test
    fun testRestApiHandoverEqualitySameInstance() {
        val handover = RestApiHandover(byteArrayOf(0x01, 0x02))
        assertEquals(handover, handover)
    }

    @Test
    fun testRestApiHandoverInequalityDifferentType() {
        val handover = RestApiHandover(byteArrayOf(0x01, 0x02))
        assertNotEquals<Any>(handover, "not a handover")
    }

    @Test
    fun testRestApiHandoverInequalityNull() {
        val handover = RestApiHandover(byteArrayOf(0x01, 0x02))
        assertNotEquals<Any?>(handover, null)
    }

    @Test
    fun testRestApiHandoverInequalityDifferentHash() {
        val handover1 = RestApiHandover(byteArrayOf(0x01, 0x02))
        val handover2 = RestApiHandover(byteArrayOf(0x03, 0x04))
        assertNotEquals(handover1, handover2)
    }

    @Test
    fun testRestApiHandoverHashCode() {
        val handover1 = RestApiHandover(byteArrayOf(0x01, 0x02))
        val handover2 = RestApiHandover(byteArrayOf(0x01, 0x02))
        assertEquals(handover1.hashCode(), handover2.hashCode())
    }

    @Test
    fun testRestApiHandoverEncodeDecode() {
        val handover = RestApiHandover(byteArrayOf(0x01, 0x02, 0x03))
        val bytes = handover.encodeCbor()
        val decoded = RestApiHandover.decodeCbor(bytes)

        assertEquals(handover, decoded)
    }

    // Handover.fromCborStructure tests

    @Test
    fun testHandoverFromCborStructureNil() {
        val cborNil = CborNil()
        val handover = Handover.fromCborStructure(cborNil)
        assertTrue(handover is QrHandover)
    }

    @Test
    fun testHandoverFromCborStructureByteString() {
        val cborBytes = CborByteString(byteArrayOf(0x01, 0x02))
        val handover = Handover.fromCborStructure(cborBytes)
        assertTrue(handover is RestApiHandover)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testHandoverFromCborStructureArrayTwoElements() {
        // NFC handover has 2 elements
        val structure = CborArray(mutableListOf<CborItem<*>>(
            CborByteString(byteArrayOf(0x01)),
            CborNil()
        ))
        val handover = Handover.fromCborStructure(structure as CborArray<CborItem<*>>)
        assertTrue(handover is NfcHandover)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testHandoverFromCborStructureArrayThreeElements() {
        // OID4VP handover has 3 elements
        val structure = CborArray(mutableListOf<CborItem<*>>(
            CborByteString(byteArrayOf(0x01)),
            CborByteString(byteArrayOf(0x02)),
            CborString("nonce")
        ))
        val handover = Handover.fromCborStructure(structure as CborArray<CborItem<*>>)
        assertTrue(handover is OID4VPHandover)
    }

    @Test
    fun testHandoverFromCborStructureInvalidType() {
        val cborString = CborString("invalid")
        assertFailsWith<IllegalArgumentException> {
            Handover.fromCborStructure(cborString)
        }
    }
}
