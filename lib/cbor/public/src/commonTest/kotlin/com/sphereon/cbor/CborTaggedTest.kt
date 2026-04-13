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

package com.sphereon.cbor

import com.sphereon.core.api.decodeFromHex
import com.sphereon.core.api.encodeToHex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CborTaggedTest {

    // Tag number constants
    @Test
    fun testTagConstants() {
        assertEquals(0, CborTagged.DATE_TIME_STRING)
        assertEquals(1, CborTagged.DATE_TIME_NUMBER)
        assertEquals(24, CborTagged.ENCODED_CBOR)
        assertEquals(1004, CborTagged.FULL_DATE_STRING)
    }

    // Basic tagged item tests
    @Test
    fun testSimpleTaggedItem() {
        val tagNumber = 100
        val taggedItem = CborString("test")
        val tagged = CborTagged(tagNumber, taggedItem)

        assertEquals(tagNumber, tagged.tagNumber)
        assertEquals(taggedItem, tagged.taggedItem)
        assertEquals("test", tagged.value)
    }

    @Test
    fun testTaggedEncodeDecode() {
        val tagged = CborTagged(42, CborUInt(123))
        val encoded = cborSerializer.encode(tagged)
        val decoded = cborSerializer.decode<CborTagged<Long>>(encoded)

        assertEquals(42, decoded.tagNumber)
        assertEquals(123L, decoded.value)
    }

    // Encoded CBOR tag (Tag 24) tests
    @Test
    fun testEncodedCborTag() {
        val innerItem = CborString("IETF")
        val byteString = CborByteString(cborSerializer.encode(innerItem))
        val encodedItem = CborEncodedItem<String>(byteString)

        val encoded = cborSerializer.encode(encodedItem)
        assertTrue(encoded.encodeToHex().startsWith("d818")) // Tag 24

        val decoded = cborSerializer.decode<CborEncodedItem<String>>(encoded)
        assertTrue(decoded.wasEncoded())
    }

    @Test
    fun testEncodedItemData() {
        val innerItem = CborArray(mutableListOf(CborUInt(1), CborUInt(2), CborUInt(3)))
        val byteString = CborByteString(cborSerializer.encode(innerItem))
        val encodedItem = CborEncodedItem<CborArray<CborItem<*>>>(byteString)

        val data = encodedItem.data()
        assertIs<CborArray<*>>(data)
        assertEquals(3, (data as CborArray<*>).value.size)
    }

    @Test
    fun testEncodedItemFromData() {
        val innerItem = CborMap(mutableMapOf(StringLabel("key") to CborUInt(42)))
        val encodedItem = CborEncodedItem.fromData(innerItem)

        assertIs<CborEncodedItem<*>>(encodedItem)
        assertTrue(encodedItem.isDataInitialized())
    }

    @Test
    fun testEncodedItemIsOriginal() {
        val testVector = "d818456449455446"
        val decoded = cborSerializer.decode<CborEncodedItem<Any>>(testVector.decodeFromHex())
        assertTrue(decoded.isOriginal)
    }

    // Date/time tags tests
    @Test
    fun testDateTimeStringTag() {
        val dateString = "2023-01-15T12:00:00Z"
        val tagged = CborTagged(CborTagged.DATE_TIME_STRING, CborString(dateString))

        val encoded = cborSerializer.encode(tagged)
        assertTrue(encoded.encodeToHex().startsWith("c0")) // Tag 0
    }

    @Test
    fun testDateTimeNumberTag() {
        val epochSeconds = 1673784000L
        val tagged = CborTagged(CborTagged.DATE_TIME_NUMBER, CborUInt(epochSeconds))

        val encoded = cborSerializer.encode(tagged)
        assertTrue(encoded.encodeToHex().startsWith("c1")) // Tag 1
    }

    // asTaggedSubject accessor
    @Test
    fun testAsTaggedSubject() {
        val tagged = CborTagged(42, CborString("test"))
        val subject = tagged.asTaggedSubject
        assertIs<CborString>(subject)
        assertEquals("test", subject.value)
    }

    @Test
    fun testAsTaggedSubjectOnNonTagged() {
        val uint = CborUInt(42)
        assertFailsWith<IllegalArgumentException> {
            uint.asTaggedSubject
        }
    }

    // asTaggedEncodedCbor accessor
    @Test
    fun testAsTaggedEncodedCbor() {
        val innerItem = CborUInt(42)
        val byteString = CborByteString(cborSerializer.encode(innerItem))
        val tagged = CborTagged(CborTagged.ENCODED_CBOR, byteString)

        val decoded = tagged.asTaggedEncodedCbor
        assertIs<CborUInt>(decoded)
        assertEquals(42L, (decoded as CborUInt).value)
    }

    @Test
    fun testAsTaggedEncodedCborWrongTag() {
        val tagged = CborTagged(99, CborByteString(byteArrayOf(0x01)))
        assertFailsWith<IllegalArgumentException> {
            tagged.asTaggedEncodedCbor
        }
    }

    @Test
    fun testAsTaggedEncodedCborWrongContent() {
        val tagged = CborTagged(CborTagged.ENCODED_CBOR, CborString("not bytes"))
        assertFailsWith<IllegalArgumentException> {
            tagged.asTaggedEncodedCbor
        }
    }

    // Major type test
    @Test
    fun testTaggedMajorType() {
        // CborTagged correctly returns MajorType.TAG as its major type
        val tagged = CborTagged(42, CborUInt(1))
        assertEquals(MajorType.TAG, tagged.majorType)

        val taggedString = CborTagged(42, CborString("test"))
        assertEquals(MajorType.TAG, taggedString.majorType)

        // The tagged item still has its own major type
        assertEquals(MajorType.UNSIGNED_INTEGER, tagged.taggedItem.majorType)
        assertEquals(MajorType.UNICODE_STRING, taggedString.taggedItem.majorType)
    }

    // Equality tests
    @Test
    fun testTaggedEquality() {
        val tagged1 = CborTagged(42, CborString("test"))
        val tagged2 = CborTagged(42, CborString("test"))
        val tagged3 = CborTagged(43, CborString("test"))
        val tagged4 = CborTagged(42, CborString("other"))

        assertEquals(tagged1, tagged2)
        assertNotEquals(tagged1, tagged3)
        assertNotEquals(tagged1, tagged4)
    }

    @Test
    fun testTaggedHashCode() {
        val tagged1 = CborTagged(42, CborString("test"))
        val tagged2 = CborTagged(42, CborString("test"))
        assertEquals(tagged1.hashCode(), tagged2.hashCode())
    }

    // JSON conversion
    @Test
    fun testTaggedToJsonSimple() {
        val tagged = CborTagged(42, CborString("test"))
        val json = tagged.toJsonSimple()
        assertEquals("\"test\"", json.toString())
    }

    // Decode tag numbers
    @Test
    fun testDecodeSmallTagNumber() {
        // Tag 0 (datetime string) with a valid RFC3339 datetime
        // c0 = tag 0, followed by text string "2013-03-21T20:04:00Z"
        val dateTime = "2013-03-21T20:04:00Z"
        val tdate = CborTDate(dateTime)
        val encoded = cborSerializer.encode(tdate)
        val decoded = cborSerializer.decode<CborItem<*>>(encoded)
        assertIs<CborTDate>(decoded)
    }

    @Test
    fun testDecodeLargeTagNumber() {
        // Tag with 2-byte number
        val tagged = CborTagged(1000, CborUInt(1))
        val encoded = cborSerializer.encode(tagged)
        val decoded = cborSerializer.decode<CborTagged<Long>>(encoded)
        assertEquals(1000, decoded.tagNumber)
    }

    // CborEncodedItem specific tests
    @Test
    fun testCborEncodedItemEquality() {
        val bytes1 = cborSerializer.encode(CborString("test"))
        val bytes2 = cborSerializer.encode(CborString("test"))

        val item1 = CborEncodedItem<String>(CborByteString(bytes1))
        val item2 = CborEncodedItem<String>(CborByteString(bytes2))

        assertEquals(item1, item2)
    }

    @Test
    fun testCborEncodedItemToData() {
        val innerItem = CborString("test")
        val bytes = cborSerializer.encode(innerItem)
        // Type parameter should be the CborItem type, not the primitive type
        val encodedItem = CborEncodedItem<CborString>(CborByteString(bytes))

        val data = CborEncodedItem.toData(encodedItem)
        assertIs<CborString>(data)
        assertEquals("test", data.value)
    }

    // Error cases
    @Test
    fun testInvalidAdditionalInfoForTag() {
        // Additional info 31 not allowed for tags
        assertFailsWith<IllegalArgumentException> {
            cborSerializer.decode<CborTagged<*>>("df".decodeFromHex())
        }
    }

    // Round-trip tests
    @Test
    fun testRoundTripTagged() {
        val testCases = listOf(
            CborTagged(0, CborString("2023-01-15T12:00:00Z")),
            CborTagged(1, CborUInt(1673784000)),
            CborTagged(42, CborArray(mutableListOf(CborUInt(1), CborUInt(2)))),
            CborTagged(1000, CborMap(mutableMapOf(StringLabel("key") to CborString("value"))))
        )

        for (tagged in testCases) {
            val encoded = cborSerializer.encode(tagged)
            val decoded = cborSerializer.decode<CborTagged<*>>(encoded)
            assertEquals(tagged.tagNumber, decoded.tagNumber, "Tag number mismatch for: ${tagged.tagNumber}")
        }
    }

    @Test
    fun testRoundTripEncodedItem() {
        val innerItems = listOf(
            CborString("test"),
            CborUInt(42),
            CborArray(mutableListOf(CborUInt(1), CborUInt(2))),
            CborMap(mutableMapOf(StringLabel("key") to CborString("value")))
        )

        for (innerItem in innerItems) {
            val bytes = cborSerializer.encode(innerItem)
            val encodedItem = CborEncodedItem<CborItem<*>>(CborByteString(bytes))
            val encoded = cborSerializer.encode(encodedItem)
            val decoded = cborSerializer.decode<CborEncodedItem<CborItem<*>>>(encoded)

            assertContentEquals(bytes, decoded.value.value)
        }
    }

    // Nested tagged items
    @Test
    fun testNestedTaggedItems() {
        val innerTagged = CborTagged(1, CborUInt(42))
        val outerTagged = CborTagged(2, innerTagged)

        val encoded = cborSerializer.encode(outerTagged)
        val decoded = cborSerializer.decode<CborTagged<*>>(encoded)

        assertEquals(2, decoded.tagNumber)
        val inner = decoded.taggedItem
        assertIs<CborTagged<*>>(inner)
        assertEquals(1, (inner as CborTagged<*>).tagNumber)
    }

    // CborEncodedItem with nested encoded items
    @Test
    fun testEncodedItemWithNestedStructure() {
        val nested = CborArray(mutableListOf(
            CborUInt(1),
            CborMap(mutableMapOf(StringLabel("key") to CborString("value")))
        ))
        val bytes = cborSerializer.encode(nested)
        val encodedItem = CborEncodedItem<CborArray<CborItem<*>>>(CborByteString(bytes))

        val data = encodedItem.data()
        assertIs<CborArray<*>>(data)
        assertEquals(2, (data as CborArray<*>).value.size)
    }
}
