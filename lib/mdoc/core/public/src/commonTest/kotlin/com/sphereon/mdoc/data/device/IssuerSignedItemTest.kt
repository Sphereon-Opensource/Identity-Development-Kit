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

package com.sphereon.mdoc.data.device

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.mdoc.data.mso.DigestID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for IssuerSignedItem and RandomValue classes.
 */
class IssuerSignedItemTest {
    // RandomValue tests

    @Test
    fun testRandomValueCreation() {
        val randomValue = RandomValue()
        assertNotNull(randomValue.value)
        assertEquals(24, randomValue.value.size)
    }

    @Test
    fun testRandomValueWithCustomBytes() {
        val customBytes = ByteArray(32) { it.toByte() }
        val randomValue = RandomValue(customBytes)
        assertEquals(32, randomValue.value.size)
        assertTrue(customBytes.contentEquals(randomValue.value))
    }

    @Test
    fun testRandomValueToCborStructure() {
        val randomValue = RandomValue()
        val cborStructure = randomValue.toCborItem()
        assertNotNull(cborStructure)
        assertTrue(randomValue.value.contentEquals(cborStructure.value))
    }

    @Test
    fun testRandomValueToString() {
        val customBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val randomValue = RandomValue(customBytes)
        val str = randomValue.toString()
        assertNotNull(str)
        assertTrue(str.isNotEmpty())
    }

    @Test
    fun testRandomValueFromCborStructure() {
        val originalValue = RandomValue()
        val cborStructure = originalValue.toCborItem()
        val decoded = RandomValue.Decoder.fromCborItem(cborStructure)
        assertTrue(originalValue.value.contentEquals(decoded.value))
    }

    // IssuerSignedItem tests

    @Test
    fun testIssuerSignedItemCreation() {
        val digestID = DigestID(1u)
        val random = RandomValue()
        val elementIdentifier = DataElementIdentifier("given_name")
        val elementValue = "John"

        val item =
            IssuerSignedItem(
                digestID = digestID,
                random = random,
                elementIdentifier = elementIdentifier,
                elementValue = elementValue,
            )

        assertEquals(digestID, item.digestID)
        assertEquals(random, item.random)
        assertEquals(elementIdentifier, item.elementIdentifier)
        assertEquals(elementValue, item.elementValue)
    }

    @Test
    fun testIssuerSignedItemCreate() {
        val digestID = DigestID(1u)
        val elementIdentifier = DataElementIdentifier("family_name")
        val elementValue = "Doe"

        val item =
            IssuerSignedItem.create(
                digestID = digestID,
                elementIdentifier = elementIdentifier,
                elementValue = elementValue,
            )

        assertEquals(digestID, item.digestID)
        assertEquals(elementIdentifier, item.elementIdentifier)
        assertEquals(elementValue, item.elementValue)
        assertNotNull(item.random)
    }

    @Test
    fun testIssuerSignedItemToCborStructure() {
        val item =
            IssuerSignedItem(
                digestID = DigestID(1u),
                random = RandomValue(),
                elementIdentifier = DataElementIdentifier("test_element"),
                elementValue = "test_value",
            )

        val encoded = encodeIssuerSignedItemWithCodec(item)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun testIssuerSignedItemEncodeDecode() {
        val item =
            IssuerSignedItem(
                digestID = DigestID(1u),
                random = RandomValue(ByteArray(24) { 0x42.toByte() }),
                elementIdentifier = DataElementIdentifier("given_name"),
                elementValue = "John",
            )

        val encoded = encodeIssuerSignedItemWithCodec(item)
        val decoded = decodeIssuerSignedItemWithCodec(encoded)

        assertEquals(item.digestID, decoded.digestID)
        assertEquals(item.elementIdentifier, decoded.elementIdentifier)
        assertEquals(item.elementValue, decoded.elementValue)
    }

    @Test
    fun testIssuerSignedItemEquality() {
        val random = RandomValue(ByteArray(24) { 0x42.toByte() })
        val item1 =
            IssuerSignedItem(
                digestID = DigestID(1u),
                random = random,
                elementIdentifier = DataElementIdentifier("given_name"),
                elementValue = "John",
            )
        val item2 =
            IssuerSignedItem(
                digestID = DigestID(1u),
                random = random,
                elementIdentifier = DataElementIdentifier("given_name"),
                elementValue = "John",
            )

        assertEquals(item1, item2)
        assertEquals(item1.hashCode(), item2.hashCode())
    }

    @Test
    fun testIssuerSignedItemEqualitySameInstance() {
        val item =
            IssuerSignedItem(
                digestID = DigestID(1u),
                random = RandomValue(),
                elementIdentifier = DataElementIdentifier("given_name"),
                elementValue = "John",
            )

        assertEquals(item, item)
    }

    @Test
    fun testIssuerSignedItemInequalityDifferentDigestID() {
        val random = RandomValue(ByteArray(24) { 0x42.toByte() })
        val item1 =
            IssuerSignedItem(
                digestID = DigestID(1u),
                random = random,
                elementIdentifier = DataElementIdentifier("given_name"),
                elementValue = "John",
            )
        val item2 =
            IssuerSignedItem(
                digestID = DigestID(2u),
                random = random,
                elementIdentifier = DataElementIdentifier("given_name"),
                elementValue = "John",
            )

        assertNotEquals(item1, item2)
    }

    @Test
    fun testIssuerSignedItemInequalityDifferentElementIdentifier() {
        val random = RandomValue(ByteArray(24) { 0x42.toByte() })
        val item1 =
            IssuerSignedItem(
                digestID = DigestID(1u),
                random = random,
                elementIdentifier = DataElementIdentifier("given_name"),
                elementValue = "John",
            )
        val item2 =
            IssuerSignedItem(
                digestID = DigestID(1u),
                random = random,
                elementIdentifier = DataElementIdentifier("family_name"),
                elementValue = "John",
            )

        assertNotEquals(item1, item2)
    }

    @Test
    fun testIssuerSignedItemInequalityDifferentElementValue() {
        val random = RandomValue(ByteArray(24) { 0x42.toByte() })
        val item1 =
            IssuerSignedItem(
                digestID = DigestID(1u),
                random = random,
                elementIdentifier = DataElementIdentifier("given_name"),
                elementValue = "John",
            )
        val item2 =
            IssuerSignedItem(
                digestID = DigestID(1u),
                random = random,
                elementIdentifier = DataElementIdentifier("given_name"),
                elementValue = "Jane",
            )

        assertNotEquals(item1, item2)
    }

    @Test
    fun testIssuerSignedItemInequalityNull() {
        val item =
            IssuerSignedItem(
                digestID = DigestID(1u),
                random = RandomValue(),
                elementIdentifier = DataElementIdentifier("given_name"),
                elementValue = "John",
            )

        assertNotEquals<Any?>(item, null)
    }

    @Test
    fun testIssuerSignedItemInequalityDifferentClass() {
        val item =
            IssuerSignedItem(
                digestID = DigestID(1u),
                random = RandomValue(),
                elementIdentifier = DataElementIdentifier("given_name"),
                elementValue = "John",
            )

        assertNotEquals<Any>(item, "not an item")
    }

    @Test
    fun testIssuerSignedItemToString() {
        val item =
            IssuerSignedItem(
                digestID = DigestID(1u),
                random = RandomValue(),
                elementIdentifier = DataElementIdentifier("given_name"),
                elementValue = "John",
            )

        val str = item.toString()
        assertTrue(str.contains("IssuerSignedItem"))
        assertTrue(str.contains("digestID"))
        assertTrue(str.contains("elementIdentifier"))
        assertTrue(str.contains("given_name"))
    }

    @Test
    fun testIssuerSignedItemWithIntegerValue() {
        val item =
            IssuerSignedItem(
                digestID = DigestID(2u),
                random = RandomValue(),
                elementIdentifier = DataElementIdentifier("age"),
                elementValue = 30,
            )

        assertEquals(30, item.elementValue)
    }

    @Test
    fun testIssuerSignedItemWithBooleanValue() {
        val item =
            IssuerSignedItem(
                digestID = DigestID(3u),
                random = RandomValue(),
                elementIdentifier = DataElementIdentifier("is_adult"),
                elementValue = true,
            )

        assertEquals(true, item.elementValue)
    }

    @Test
    fun testIssuerSignedItemFromCborEncodedBytes() {
        val item =
            IssuerSignedItem(
                digestID = DigestID(1u),
                random = RandomValue(ByteArray(24) { 0x42.toByte() }),
                elementIdentifier = DataElementIdentifier("test"),
                elementValue = "value",
            )

        // Encode the item to bytes and decode via fromCborEncodedItem
        val encoded = encodeIssuerSignedItemWithCodec(item)
        val decoded = decodeIssuerSignedItemWithCodec(encoded)

        assertEquals(item.digestID, decoded.digestID)
        assertEquals(item.elementIdentifier, decoded.elementIdentifier)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testIssuerSignedItemDigestExtension() {
        val item: IssuerSignedItem<Any> =
            IssuerSignedItem(
                digestID = DigestID(1u),
                random = RandomValue(ByteArray(24) { 0x42.toByte() }),
                elementIdentifier = DataElementIdentifier("given_name"),
                elementValue = "John" as Any,
            )

        val encodedItem: CborEncodedItem<IssuerSignedItem<Any>> = encodeIssuerSignedItemAsEncoded(item)
        val digest = encodedItem.digest()

        assertNotNull(digest)
        assertTrue(digest.isNotEmpty())
    }

    // Companion object labels tests

    @Test
    fun testIssuerSignedItemCompanionLabels() {
        assertEquals("digestID", IssuerSignedItem.DIGEST_ID.value)
        assertEquals("random", IssuerSignedItem.RANDOM.value)
        assertEquals("elementIdentifier", IssuerSignedItem.ELEMENT_IDENTIFIER.value)
        assertEquals("elementValue", IssuerSignedItem.ELEMENT_VALUE.value)
    }
}
