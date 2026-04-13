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

package com.sphereon.mdoc.data.mso

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborTDate
import com.sphereon.cbor.CborTagged
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.TDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ValidityInfo data class.
 */
class ValidityInfoTest {

    private val sampleSigned = TDate("2024-01-01T00:00:00Z")
    private val sampleValidFrom = TDate("2024-01-01T00:00:00Z")
    private val sampleValidUntil = TDate("2025-01-01T00:00:00Z")
    private val sampleExpectedUpdate = TDate("2024-07-01T00:00:00Z")

    @Test
    fun testValidityInfoCreation() {
        val info = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        assertEquals(sampleSigned, info.signed)
        assertEquals(sampleValidFrom, info.validFrom)
        assertEquals(sampleValidUntil, info.validUntil)
        assertNull(info.expectedUpdate)
    }

    @Test
    fun testValidityInfoWithExpectedUpdate() {
        val info = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil,
            expectedUpdate = sampleExpectedUpdate
        )
        assertNotNull(info.expectedUpdate)
        assertEquals(sampleExpectedUpdate, info.expectedUpdate)
    }

    @Test
    fun testValidityInfoEquality() {
        val info1 = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        val info2 = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        assertEquals(info1, info2)
    }

    @Test
    fun testValidityInfoEqualitySameInstance() {
        val info = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        assertEquals(info, info)
    }

    @Test
    fun testValidityInfoInequalityNull() {
        val info = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        assertFalse(info.equals(null))
    }

    @Test
    fun testValidityInfoInequalityDifferentType() {
        val info = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        assertFalse(info.equals("not a ValidityInfo"))
    }

    @Test
    fun testValidityInfoInequalityDifferentSigned() {
        val info1 = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        val info2 = ValidityInfo(
            signed = TDate("2023-01-01T00:00:00Z"),
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        assertNotEquals(info1, info2)
    }

    @Test
    fun testValidityInfoInequalityDifferentValidFrom() {
        val info1 = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        val info2 = ValidityInfo(
            signed = sampleSigned,
            validFrom = TDate("2023-06-01T00:00:00Z"),
            validUntil = sampleValidUntil
        )
        assertNotEquals(info1, info2)
    }

    @Test
    fun testValidityInfoInequalityDifferentValidUntil() {
        val info1 = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        val info2 = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = TDate("2026-01-01T00:00:00Z")
        )
        assertNotEquals(info1, info2)
    }

    @Test
    fun testValidityInfoInequalityDifferentExpectedUpdate() {
        val info1 = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil,
            expectedUpdate = sampleExpectedUpdate
        )
        val info2 = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil,
            expectedUpdate = TDate("2024-09-01T00:00:00Z")
        )
        assertNotEquals(info1, info2)
    }

    @Test
    fun testValidityInfoInequalityWithAndWithoutExpectedUpdate() {
        val info1 = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil,
            expectedUpdate = sampleExpectedUpdate
        )
        val info2 = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil,
            expectedUpdate = null
        )
        assertNotEquals(info1, info2)
    }

    @Test
    fun testValidityInfoHashCode() {
        val info1 = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        val info2 = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        assertEquals(info1.hashCode(), info2.hashCode())
    }

    @Test
    fun testValidityInfoHashCodeWithExpectedUpdate() {
        val info1 = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil,
            expectedUpdate = sampleExpectedUpdate
        )
        val info2 = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil,
            expectedUpdate = sampleExpectedUpdate
        )
        assertEquals(info1.hashCode(), info2.hashCode())
    }

    @Test
    fun testValidityInfoToString() {
        val info = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        val str = info.toString()
        assertTrue(str.contains("ValidityInfo"))
        assertTrue(str.contains("signed"))
        assertTrue(str.contains("validFrom"))
        assertTrue(str.contains("validUntil"))
    }

    @Test
    fun testValidityInfoToStringWithExpectedUpdate() {
        val info = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil,
            expectedUpdate = sampleExpectedUpdate
        )
        val str = info.toString()
        assertTrue(str.contains("expectedUpdate"))
    }

    @Test
    fun testValidityInfoCborBuilder() {
        val info = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        val builder = info.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testValidityInfoCompanionLabels() {
        assertEquals("signed", ValidityInfo.SIGNED.value)
        assertEquals("validFrom", ValidityInfo.VALID_FROM.value)
        assertEquals("validUntil", ValidityInfo.VALID_UNTIL.value)
        assertEquals("expectedUpdate", ValidityInfo.EXPECTED_UPDATE.value)
    }

    @Test
    fun testValidityInfoSerializerDescriptor() {
        val descriptor = ValidityInfoSerializer.descriptor
        assertNotNull(descriptor)
        assertEquals("com.sphereon.mdoc.data.mso.ValidityInfo", descriptor.serialName)
    }

    // ValidityInfoSerializer JSON tests

    @Test
    fun testValidityInfoJsonSerializationRoundTrip() {
        val info = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        val json = Json.encodeToString(ValidityInfoSerializer, info)
        val decoded = Json.decodeFromString(ValidityInfoSerializer, json)
        assertEquals(info.signed, decoded.signed)
        assertEquals(info.validFrom, decoded.validFrom)
        assertEquals(info.validUntil, decoded.validUntil)
        assertNull(decoded.expectedUpdate)
    }

    @Test
    fun testValidityInfoJsonSerializationWithExpectedUpdate() {
        val info = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil,
            expectedUpdate = sampleExpectedUpdate
        )
        val json = Json.encodeToString(ValidityInfoSerializer, info)
        val decoded = Json.decodeFromString(ValidityInfoSerializer, json)
        assertEquals(info.signed, decoded.signed)
        assertEquals(info.validFrom, decoded.validFrom)
        assertEquals(info.validUntil, decoded.validUntil)
        assertEquals(info.expectedUpdate, decoded.expectedUpdate)
    }

    // CBOR encode/decode tests

    @Test
    fun testValidityInfoCborEncodeDecode() {
        val info = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil
        )
        val encoded = info.encodeCbor()
        val decoded = ValidityInfo.decodeCbor(encoded)
        assertEquals(info.signed, decoded.signed)
        assertEquals(info.validFrom, decoded.validFrom)
        assertEquals(info.validUntil, decoded.validUntil)
    }

    @Test
    fun testValidityInfoCborEncodeDecodeWithExpectedUpdate() {
        val info = ValidityInfo(
            signed = sampleSigned,
            validFrom = sampleValidFrom,
            validUntil = sampleValidUntil,
            expectedUpdate = sampleExpectedUpdate
        )
        val encoded = info.encodeCbor()
        val decoded = ValidityInfo.decodeCbor(encoded)
        assertEquals(info.signed, decoded.signed)
        assertEquals(info.validFrom, decoded.validFrom)
        assertEquals(info.validUntil, decoded.validUntil)
        assertEquals(info.expectedUpdate, decoded.expectedUpdate)
    }

    // fromCborStructure branch tests - testing different CBOR date representations

    @Test
    fun testValidityInfoFromCborStructureWithCborTDate() {
        // Build a CborMap with CborTDate values
        val dateString = "2024-01-01T00:00:00Z"
        val map = mutableMapOf<StringLabel, CborItem<*>>()
        map[ValidityInfo.SIGNED] = CborTDate(dateString)
        map[ValidityInfo.VALID_FROM] = CborTDate(dateString)
        map[ValidityInfo.VALID_UNTIL] = CborTDate(dateString)
        val cborMap = CborMap(map)

        val info = ValidityInfo.fromCborStructure(cborMap)
        assertEquals(dateString, info.signed.toString())
        assertEquals(dateString, info.validFrom.toString())
        assertEquals(dateString, info.validUntil.toString())
    }

    @Test
    fun testValidityInfoFromCborStructureWithCborString() {
        // Build a CborMap with CborString values (untagged dates)
        val dateString = "2024-01-01T00:00:00Z"
        val map = mutableMapOf<StringLabel, CborItem<*>>()
        map[ValidityInfo.SIGNED] = CborString(dateString)
        map[ValidityInfo.VALID_FROM] = CborString(dateString)
        map[ValidityInfo.VALID_UNTIL] = CborString(dateString)
        val cborMap = CborMap(map)

        val info = ValidityInfo.fromCborStructure(cborMap)
        assertEquals(dateString, info.signed.toString())
        assertEquals(dateString, info.validFrom.toString())
        assertEquals(dateString, info.validUntil.toString())
    }

    @Test
    fun testValidityInfoFromCborStructureWithCborTagged() {
        // Build a CborMap with CborTagged values (generic tagged, not CborTDate)
        val dateString = "2024-01-01T00:00:00Z"
        val map = mutableMapOf<StringLabel, CborItem<*>>()
        // Use a different tag number (e.g., 100) to trigger CborTagged branch instead of CborTDate
        map[ValidityInfo.SIGNED] = CborTagged(100, CborString(dateString))
        map[ValidityInfo.VALID_FROM] = CborTagged(100, CborString(dateString))
        map[ValidityInfo.VALID_UNTIL] = CborTagged(100, CborString(dateString))
        val cborMap = CborMap(map)

        val info = ValidityInfo.fromCborStructure(cborMap)
        assertEquals(dateString, info.signed.toString())
        assertEquals(dateString, info.validFrom.toString())
        assertEquals(dateString, info.validUntil.toString())
    }

    @Test
    fun testValidityInfoFromCborStructureWithExpectedUpdateCborTDate() {
        val dateString = "2024-01-01T00:00:00Z"
        val expectedUpdateString = "2024-07-01T00:00:00Z"
        val map = mutableMapOf<StringLabel, CborItem<*>>()
        map[ValidityInfo.SIGNED] = CborTDate(dateString)
        map[ValidityInfo.VALID_FROM] = CborTDate(dateString)
        map[ValidityInfo.VALID_UNTIL] = CborTDate(dateString)
        map[ValidityInfo.EXPECTED_UPDATE] = CborTDate(expectedUpdateString)
        val cborMap = CborMap(map)

        val info = ValidityInfo.fromCborStructure(cborMap)
        assertNotNull(info.expectedUpdate)
        assertEquals(expectedUpdateString, info.expectedUpdate!!.toString())
    }

    @Test
    fun testValidityInfoFromCborStructureWithExpectedUpdateCborString() {
        val dateString = "2024-01-01T00:00:00Z"
        val expectedUpdateString = "2024-07-01T00:00:00Z"
        val map = mutableMapOf<StringLabel, CborItem<*>>()
        map[ValidityInfo.SIGNED] = CborTDate(dateString)
        map[ValidityInfo.VALID_FROM] = CborTDate(dateString)
        map[ValidityInfo.VALID_UNTIL] = CborTDate(dateString)
        map[ValidityInfo.EXPECTED_UPDATE] = CborString(expectedUpdateString)
        val cborMap = CborMap(map)

        val info = ValidityInfo.fromCborStructure(cborMap)
        assertNotNull(info.expectedUpdate)
        assertEquals(expectedUpdateString, info.expectedUpdate!!.toString())
    }

    @Test
    fun testValidityInfoFromCborStructureWithExpectedUpdateCborTagged() {
        val dateString = "2024-01-01T00:00:00Z"
        val expectedUpdateString = "2024-07-01T00:00:00Z"
        val map = mutableMapOf<StringLabel, CborItem<*>>()
        map[ValidityInfo.SIGNED] = CborTDate(dateString)
        map[ValidityInfo.VALID_FROM] = CborTDate(dateString)
        map[ValidityInfo.VALID_UNTIL] = CborTDate(dateString)
        // Use a different tag number (e.g., 100) to trigger CborTagged branch instead of CborTDate
        map[ValidityInfo.EXPECTED_UPDATE] = CborTagged(100, CborString(expectedUpdateString))
        val cborMap = CborMap(map)

        val info = ValidityInfo.fromCborStructure(cborMap)
        assertNotNull(info.expectedUpdate)
        assertEquals(expectedUpdateString, info.expectedUpdate!!.toString())
    }

    @Test
    fun testValidityInfoFromCborStructureInvalidSignedType() {
        val map = mutableMapOf<StringLabel, CborItem<*>>()
        map[ValidityInfo.SIGNED] = CborUInt(12345L)  // Invalid type
        map[ValidityInfo.VALID_FROM] = CborTDate("2024-01-01T00:00:00Z")
        map[ValidityInfo.VALID_UNTIL] = CborTDate("2024-01-01T00:00:00Z")
        val cborMap = CborMap(map)

        assertFailsWith<IllegalArgumentException> {
            ValidityInfo.fromCborStructure(cborMap)
        }
    }

    @Test
    fun testValidityInfoFromCborStructureInvalidValidFromType() {
        val map = mutableMapOf<StringLabel, CborItem<*>>()
        map[ValidityInfo.SIGNED] = CborTDate("2024-01-01T00:00:00Z")
        map[ValidityInfo.VALID_FROM] = CborUInt(12345L)  // Invalid type
        map[ValidityInfo.VALID_UNTIL] = CborTDate("2024-01-01T00:00:00Z")
        val cborMap = CborMap(map)

        assertFailsWith<IllegalArgumentException> {
            ValidityInfo.fromCborStructure(cborMap)
        }
    }

    @Test
    fun testValidityInfoFromCborStructureInvalidValidUntilType() {
        val map = mutableMapOf<StringLabel, CborItem<*>>()
        map[ValidityInfo.SIGNED] = CborTDate("2024-01-01T00:00:00Z")
        map[ValidityInfo.VALID_FROM] = CborTDate("2024-01-01T00:00:00Z")
        map[ValidityInfo.VALID_UNTIL] = CborUInt(12345L)  // Invalid type
        val cborMap = CborMap(map)

        assertFailsWith<IllegalArgumentException> {
            ValidityInfo.fromCborStructure(cborMap)
        }
    }

    @Test
    fun testValidityInfoFromCborStructureInvalidExpectedUpdateType() {
        val map = mutableMapOf<StringLabel, CborItem<*>>()
        map[ValidityInfo.SIGNED] = CborTDate("2024-01-01T00:00:00Z")
        map[ValidityInfo.VALID_FROM] = CborTDate("2024-01-01T00:00:00Z")
        map[ValidityInfo.VALID_UNTIL] = CborTDate("2024-01-01T00:00:00Z")
        map[ValidityInfo.EXPECTED_UPDATE] = CborUInt(12345L)  // Invalid type
        val cborMap = CborMap(map)

        assertFailsWith<IllegalArgumentException> {
            ValidityInfo.fromCborStructure(cborMap)
        }
    }

    // fromDates tests

    @Test
    fun testValidityInfoFromDatesWithMinimalParams() {
        val now = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()
        // Add one year by creating a new date
        val validUntil = com.sphereon.core.compat.LocalDateTimeKMP(
            now.year + 1, now.month, now.day, now.hour, now.minute, now.second, now.nanosecond
        )
        val info = ValidityInfo.fromDates(validUntil = validUntil)
        assertNotNull(info.signed)
        assertNotNull(info.validFrom)
        assertNotNull(info.validUntil)
        assertNull(info.expectedUpdate)
    }

    @Test
    fun testValidityInfoFromDatesWithExpectedUpdate() {
        val now = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()
        val validUntil = com.sphereon.core.compat.LocalDateTimeKMP(
            now.year + 1, now.month, now.day, now.hour, now.minute, now.second, now.nanosecond
        )
        val expectedUpdate = com.sphereon.core.compat.LocalDateTimeKMP(
            now.year, 7, now.day, now.hour, now.minute, now.second, now.nanosecond
        )
        val info = ValidityInfo.fromDates(
            signed = now,
            validFrom = now,
            validUntil = validUntil,
            expectedUpdate = expectedUpdate
        )
        assertNotNull(info.signed)
        assertNotNull(info.validFrom)
        assertNotNull(info.validUntil)
        assertNotNull(info.expectedUpdate)
    }

    // ValidityInfoSerializer edge case tests

    @Test
    fun testValidityInfoSerializerDeserializeMissingFields() {
        // JSON with missing required fields should throw SerializationException
        // Note: the exact exception type depends on JSON library's behavior
        val incompleteJson = """{"signed":"2025-01-20T12:00:00Z"}"""
        assertFailsWith<Exception> {
            Json.decodeFromString(ValidityInfoSerializer, incompleteJson)
        }
    }

    @Test
    fun testValidityInfoSerializerDeserializeAllFieldsMissing() {
        // Empty JSON object - all required fields missing
        val emptyJson = """{}"""
        assertFailsWith<Exception> {
            Json.decodeFromString(ValidityInfoSerializer, emptyJson)
        }
    }
}
