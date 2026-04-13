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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CborDateTimeTest {

    // CborTDate tests (Tag 0 - date/time string)

    @Test
    fun testCborTDateCreation() {
        val tdate = CborTDate("2024-01-15T10:30:00Z")
        assertTrue(tdate is CborTagged<*>)
        assertEquals(0, tdate.tagNumber)
    }

    @Test
    fun testCborTDateTagNumber() {
        val tdate = CborTDate("2024-06-15T12:00:00Z")
        assertEquals(CborTagged.DATE_TIME_STRING.toInt(), tdate.tagNumber)
    }

    @Test
    fun testCborTDateValue() {
        val dateStr = "2024-01-15T10:30:00Z"
        val tdate = CborTDate(dateStr)
        val value = tdate.toValue()
        assertNotNull(value)
        assertTrue(value.contains("2024"))
    }

    @Test
    fun testCborTDateToJsonSimple() {
        val tdate = CborTDate("2024-01-15T10:30:00Z")
        val json = tdate.toJsonSimple()
        assertNotNull(json)
    }

    @Test
    fun testCborTDateEncodeDecode() {
        val tdate = CborTDate("2024-01-15T10:30:00Z")
        val encoded = cborSerializer.encode(tdate)
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())

        val decoded = cborSerializer.decode<CborTagged<*>>(encoded)
        assertEquals(0, decoded.tagNumber)
    }

    // CborFullDate tests (Tag 1004 - full-date string)

    @Test
    fun testCborFullDateCreation() {
        val fullDate = CborFullDate("2024-01-15")
        assertTrue(fullDate is CborTagged<*>)
        assertEquals(1004, fullDate.tagNumber)
    }

    @Test
    fun testCborFullDateTagNumber() {
        val fullDate = CborFullDate("2024-06-15")
        assertEquals(CborTagged.FULL_DATE_STRING.toInt(), fullDate.tagNumber)
    }

    @Test
    fun testCborFullDateValue() {
        val dateStr = "2024-01-15"
        val fullDate = CborFullDate(dateStr)
        val value = fullDate.toValue()
        assertEquals(dateStr, value)
    }

    @Test
    fun testCborFullDateToJsonSimple() {
        val fullDate = CborFullDate("2024-01-15")
        val json = fullDate.toJsonSimple()
        assertNotNull(json)
        assertTrue(json.toString().contains("2024-01-15"))
    }

    @Test
    fun testCborFullDateEncodeDecode() {
        val fullDate = CborFullDate("2024-01-15")
        val encoded = cborSerializer.encode(fullDate)
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())

        val decoded = cborSerializer.decode<CborTagged<*>>(encoded)
        assertEquals(1004, decoded.tagNumber)
    }

    // CborTime tests (Tag 1 - epoch-based date/time)

    @Test
    fun testCborTimeCreation() {
        val epochSeconds: cddl_time = 1705315800L
        val cborTime = CborTime(epochSeconds)
        assertTrue(cborTime is CborTagged<*>)
        assertEquals(1, cborTime.tagNumber)
    }

    @Test
    fun testCborTimeTagNumber() {
        val cborTime = CborTime(1705315800L)
        assertEquals(CborTagged.DATE_TIME_NUMBER.toInt(), cborTime.tagNumber)
    }

    @Test
    fun testCborTimeValue() {
        val epochSeconds: cddl_time = 1705315800L
        val cborTime = CborTime(epochSeconds)
        val value = cborTime.toValue()
        assertEquals(epochSeconds, value)
    }

    @Test
    fun testCborTimeEncodeDecode() {
        val epochSeconds: cddl_time = 1705315800L
        val cborTime = CborTime(epochSeconds)
        val encoded = cborSerializer.encode(cborTime)
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())

        val decoded = cborSerializer.decode<CborTagged<*>>(encoded)
        assertEquals(1, decoded.tagNumber)
    }

    @Test
    fun testCborTimeZeroEpoch() {
        val cborTime = CborTime(0L)
        assertEquals(0L, cborTime.toValue())
    }

    @Test
    fun testCborTimeNegativeEpoch() {
        val cborTime = CborTime(-86400L)
        assertEquals(-86400L, cborTime.toValue())
    }

    // TDate value class tests

    @Test
    fun testTDateCreation() {
        val tdate = TDate("2024-01-15T10:30:00Z")
        assertNotNull(tdate)
    }

    @Test
    fun testTDateToString() {
        val tdate = TDate("2024-01-15T10:30:00Z")
        val str = tdate.toString()
        assertNotNull(str)
        assertTrue(str.contains("2024"))
    }

    @Test
    fun testTDateToCborStructure() {
        val tdate = TDate("2024-01-15T10:30:00Z")
        val cbor = tdate.toCborStructure()
        assertTrue(cbor is CborTDate)
        assertEquals(0, cbor.tagNumber)
    }

    // Tag constants tests

    @Test
    fun testDateTimeTagConstants() {
        assertEquals(0, CborTagged.DATE_TIME_STRING)
        assertEquals(1, CborTagged.DATE_TIME_NUMBER)
        assertEquals(1004, CborTagged.FULL_DATE_STRING)
    }

    // CDDL date/time type tests

    @Test
    fun testCDDLTdateInfo() {
        assertEquals(0, CDDL.tdate.info)
        assertEquals(MajorType.TAG, CDDL.tdate.majorType)
        assertEquals("tdate", CDDL.tdate.format)
    }

    @Test
    fun testCDDLTimeInfo() {
        assertEquals(1, CDDL.time.info)
        assertEquals(MajorType.TAG, CDDL.time.majorType)
        assertEquals("time", CDDL.time.format)
    }

    @Test
    fun testCDDLFullDateInfo() {
        assertEquals(1004, CDDL.full_date.info)
        assertEquals(MajorType.TAG, CDDL.full_date.majorType)
        assertEquals("full-date", CDDL.full_date.format)
    }

    // Nested tagged structure tests

    @Test
    fun testDateInMap() {
        val map = CborMap(mutableMapOf<CoseLabel<*>, CborItem<*>>(
            StringLabel("birthdate") to CborFullDate("1990-05-20"),
            StringLabel("timestamp") to CborTime(1705315800L)
        ))

        val encoded = cborSerializer.encode(map)
        val decoded = cborSerializer.decode<CborMap<CborItem<*>, CborItem<*>>>(encoded)

        assertEquals(2, decoded.value.size)
    }

    @Test
    fun testDateInArray() {
        val array = CborArray(mutableListOf<CborItem<*>>(
            CborTDate("2024-01-15T10:30:00Z"),
            CborFullDate("2024-01-15"),
            CborTime(1705315800L)
        ))

        val encoded = cborSerializer.encode(array)
        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>(encoded)

        assertEquals(3, decoded.value.size)
    }

    // RFC 8949 date/time encoding tests

    @Test
    fun testTag0DateTimeStringEncoding() {
        val tagged = CborTagged(0, CborString("2013-03-21T20:04:00Z"))
        val encoded = cborSerializer.encode(tagged)
        assertNotNull(encoded)

        val decoded = cborSerializer.decode<CborTagged<*>>(encoded)
        assertEquals(0, decoded.tagNumber)
        assertTrue(decoded.toValue().toString().contains("2013"))
    }

    @Test
    fun testTag1EpochEncoding() {
        val tagged = CborTagged(1, CborUInt(1363896240L))
        val encoded = cborSerializer.encode(tagged)
        assertNotNull(encoded)

        val decoded = cborSerializer.decode<CborTagged<*>>(encoded)
        assertEquals(1, decoded.tagNumber)
    }

    @Test
    fun testTag1004FullDateEncoding() {
        val tagged = CborTagged(1004, CborString("2024-01-15"))
        val encoded = cborSerializer.encode(tagged)
        assertNotNull(encoded)

        val decoded = cborSerializer.decode<CborTagged<*>>(encoded)
        assertEquals(1004, decoded.tagNumber)
        assertEquals("2024-01-15", decoded.toValue())
    }

    // Edge cases

    @Test
    fun testDateTimeWithMilliseconds() {
        val tdate = CborTDate("2024-01-15T10:30:00.123Z")
        assertNotNull(tdate.toValue())
    }

    @Test
    fun testFullDateLeapYear() {
        val fullDate = CborFullDate("2024-02-29")
        assertEquals("2024-02-29", fullDate.toValue())
    }

    @Test
    fun testFullDateEndOfYear() {
        val fullDate = CborFullDate("2024-12-31")
        assertEquals("2024-12-31", fullDate.toValue())
    }

    @Test
    fun testFullDateStartOfYear() {
        val fullDate = CborFullDate("2024-01-01")
        assertEquals("2024-01-01", fullDate.toValue())
    }

    @Test
    fun testTimeLargeEpoch() {
        val cborTime = CborTime(4102444800L)
        assertEquals(4102444800L, cborTime.toValue())
    }
}
