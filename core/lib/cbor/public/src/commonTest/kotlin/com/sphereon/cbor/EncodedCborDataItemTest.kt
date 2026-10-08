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

package com.sphereon.cbor

import com.sphereon.core.api.decodeFromHex
import com.sphereon.core.api.encodeToHex
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.toHexString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EncodedCborDataItemTest {
    @Test
    fun decodeRfc7049TestVector() {
        val testVector = "d818456449455446"
        val cborEncodedDataItem = Cbor.decode<CborEncodedItem<Any>>(testVector.decodeFromHex())
        assertNotNull(cborEncodedDataItem)
        val hexDataItem = cborEncodedDataItem.value.value.encodeToHex()
        assertEquals("6449455446", hexDataItem)
        val reEncoded = cborEncodedDataItem.encodeCbor()
        assertContentEquals(testVector.decodeFromHex(), reEncoded)
    }

    @Test
    fun testNestedArrayEncoding() {
        // Nested array [1, 2, [3, 4]]

        val testVector = "D81846830102820304"
        val cborEncodedDataItem = Cbor.decode<CborEncodedItem<Any>>(testVector.decodeFromHex())
        assertNotNull(cborEncodedDataItem)
        val hexDataItem = cborEncodedDataItem.value.value.encodeToHex()
        assertEquals("830102820304", hexDataItem)
        val reEncoded = cborEncodedDataItem.encodeCbor()
        assertContentEquals(testVector.decodeFromHex(), reEncoded)
        assertTrue(cborEncodedDataItem.isOriginal)
        assertTrue(cborEncodedDataItem.wasEncoded())

        val data = cborEncodedDataItem.data { Cbor.decode(it) }
        assertNotNull(data)
        assertTrue(cborEncodedDataItem.isDataInitialized())

        assertIs<CborArray<CborItem<*>>>(data)
        assertEquals(3, data.value.size)
        assertEquals(1, data.value[0].asInt)
        assertEquals(2, data.value[1].asInt)
        assertIs<CborArray<CborItem<*>>>(data.value[2])
        val subArray = data.value[2] as CborArray<CborItem<*>>
        assertEquals(2, subArray.value.size)
        assertEquals(3, subArray.value[0].asInt)
        assertEquals(4, subArray.value[1].asInt)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testEncodedCborItemDeserialization() {
        val testString = "test".toCborString()
        val testByteString = testString.toBstr()
        val bsBuilder = ByteStringBuilder()
        println(testByteString.encode(bsBuilder))
        println(bsBuilder.toByteString().toHexString(HexFormat.Default))
        val encodedItem = CborEncodedItem<String>(testByteString)
        val encodedBytes = Cbor.encode(encodedItem)

        val decoded = Cbor.decode<CborEncodedItem<String>>(encodedBytes)
        assertNotNull(decoded)
        assertEquals(encodedItem, decoded)
        assertContentEquals(encodedItem.value.value, decoded.value.value)

        "".toCborByteString()

        val hex = encodedBytes.encodeToHex()
        println(hex)
        assertEquals("d818456474657374", hex)
    }
}
