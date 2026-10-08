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

package com.sphereon.mdoc

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.toCborByteString
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.mdoc.testutil.encodeCoseKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for SessionEstablishment class.
 */
class SessionEstablishmentTest {
    private val sessionEstablishmentCborCodec = SessionEstablishmentCborCodecImpl()

    private fun createTestCoseKey(): CoseKey =
        CoseKeyJson
            .Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()

    @Test
    fun testSessionEstablishmentCreation() {
        val coseKey = createTestCoseKey()
        val encodedReaderKey = CborEncodedItem(encodeCoseKey(coseKey), coseKey)
        val data = byteArrayOf(0x01, 0x02, 0x03).toCborByteString()

        val establishment =
            SessionEstablishment(
                encodedReaderKey = encodedReaderKey,
                data = data,
                original = null,
            )

        assertNotNull(establishment.encodedReaderKey)
        assertEquals(data, establishment.data)
    }

    @Test
    fun testSessionEstablishmentGetReaderKey() {
        val coseKey = createTestCoseKey()
        val encodedReaderKey = CborEncodedItem(encodeCoseKey(coseKey), coseKey)
        val data = byteArrayOf(0x01, 0x02, 0x03).toCborByteString()

        val establishment =
            SessionEstablishment(
                encodedReaderKey = encodedReaderKey,
                data = data,
                original = null,
            )

        val readerKey = establishment.getReaderKey()
        assertNotNull(readerKey)
    }

    @Test
    fun testSessionEstablishmentCopyWith() {
        val coseKey = createTestCoseKey()
        val encodedReaderKey = CborEncodedItem(encodeCoseKey(coseKey), coseKey)
        val data = byteArrayOf(0x01, 0x02, 0x03).toCborByteString()

        val establishment =
            SessionEstablishment(
                encodedReaderKey = encodedReaderKey,
                data = data,
                original = null,
            )

        val newData = byteArrayOf(0x04, 0x05).toCborByteString()
        val copied = establishment.copyWith(data = newData)

        assertEquals(newData, copied.data)
        assertEquals(establishment.encodedReaderKey, copied.encodedReaderKey)
    }

    @Test
    fun testSessionEstablishmentCopyWithOriginal() {
        val coseKey = createTestCoseKey()
        val encodedReaderKey = CborEncodedItem(encodeCoseKey(coseKey), coseKey)
        val data = byteArrayOf(0x01, 0x02, 0x03).toCborByteString()

        val establishment =
            SessionEstablishment(
                encodedReaderKey = encodedReaderKey,
                data = data,
                original = null,
            )

        val originalBytes = byteArrayOf(0x10, 0x20, 0x30)
        val copied = establishment.copyWith(original = originalBytes)

        assertTrue(originalBytes.contentEquals(copied.original))
    }

    @Test
    fun testSessionEstablishmentEncode() {
        val coseKey = createTestCoseKey()
        val encodedReaderKey = CborEncodedItem(encodeCoseKey(coseKey), coseKey)
        val data = byteArrayOf(0x01, 0x02, 0x03).toCborByteString()

        val establishment =
            SessionEstablishment(
                encodedReaderKey = encodedReaderKey,
                data = data,
                original = null,
            )

        val encoded = sessionEstablishmentCborCodec.encode(establishment).getOrThrow()
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun testSessionEstablishmentEncodeWithOriginal() {
        val coseKey = createTestCoseKey()
        val encodedReaderKey = CborEncodedItem(encodeCoseKey(coseKey), coseKey)
        val data = byteArrayOf(0x01, 0x02, 0x03).toCborByteString()
        val originalBytes = byteArrayOf(0xA1.toByte(), 0x01, 0x02)

        val establishment =
            SessionEstablishment(
                encodedReaderKey = encodedReaderKey,
                data = data,
                original = originalBytes,
            )

        val encoded = sessionEstablishmentCborCodec.encode(establishment).getOrThrow()
        assertTrue(originalBytes.contentEquals(encoded))
    }

    @Test
    fun testSessionEstablishmentDecodeWithCodec() {
        val coseKey = createTestCoseKey()
        val encodedReaderKey = CborEncodedItem(encodeCoseKey(coseKey), coseKey)
        val data = byteArrayOf(0x01, 0x02, 0x03).toCborByteString()

        val establishment =
            SessionEstablishment(
                encodedReaderKey = encodedReaderKey,
                data = data,
                original = null,
            )

        val encoded = sessionEstablishmentCborCodec.encode(establishment).getOrThrow()
        val decoded = sessionEstablishmentCborCodec.decode(encoded).getOrThrow().value

        assertNotNull(decoded)
        assertNotNull(decoded.encodedReaderKey)
        assertNotNull(decoded.data)
        assertTrue(encoded.contentEquals(decoded.original))
    }

    @Test
    fun testSessionEstablishmentCodecProducesCborMap() {
        val coseKey = createTestCoseKey()
        val encodedReaderKey = CborEncodedItem(encodeCoseKey(coseKey), coseKey)
        val data = byteArrayOf(0x01, 0x02, 0x03).toCborByteString()

        val establishment =
            SessionEstablishment(
                encodedReaderKey = encodedReaderKey,
                data = data,
                original = null,
            )

        val structure =
            com.sphereon.cbor.Cbor
                .decode<CborMap<*, *>>(sessionEstablishmentCborCodec.encode(establishment).getOrThrow())
        assertNotNull(structure)
    }

    @Test
    fun testSessionEstablishmentToString() {
        val coseKey = createTestCoseKey()
        val encodedReaderKey = CborEncodedItem(encodeCoseKey(coseKey), coseKey)
        val data = byteArrayOf(0x01, 0x02, 0x03).toCborByteString()

        val establishment =
            SessionEstablishment(
                encodedReaderKey = encodedReaderKey,
                data = data,
                original = null,
            )

        val str = establishment.toString()
        assertTrue(str.contains("SessionEstablishment"))
        assertTrue(str.contains("encodedReaderKey"))
        assertTrue(str.contains("data"))
    }

    @Test
    fun testSessionEstablishmentGetRawData() {
        val coseKey = createTestCoseKey()
        val encodedReaderKey = CborEncodedItem(encodeCoseKey(coseKey), coseKey)
        val data = byteArrayOf(0x01, 0x02, 0x03).toCborByteString()
        val originalBytes = byteArrayOf(0xA1.toByte(), 0x01, 0x02, 0x03)

        val establishment =
            SessionEstablishment(
                encodedReaderKey = encodedReaderKey,
                data = data,
                original = originalBytes,
            )

        val rawData = establishment.getRawData()
        assertTrue(originalBytes.contentEquals(rawData))
    }

    // Companion object labels tests

    @Test
    fun testSessionEstablishmentCompanionLabels() {
        assertEquals("eReaderKey", SessionEstablishment.E_READER_KEY.value)
        assertEquals("data", SessionEstablishment.DATA.value)
    }
}
