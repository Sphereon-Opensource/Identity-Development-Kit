/*
 * (c) 2026 Sphereon International B.V.
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

import com.sphereon.cbor.TDate
import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.LocalDateTimeKMP
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidityInfoTest {
    private val sampleSigned = TDate("2024-01-01T00:00:00Z")
    private val sampleValidFrom = TDate("2024-01-01T00:00:00Z")
    private val sampleValidUntil = TDate("2025-01-01T00:00:00Z")
    private val sampleExpectedUpdate = TDate("2024-07-01T00:00:00Z")

    @Test
    fun testValidityInfoCreation() {
        val info =
            ValidityInfo(
                signed = sampleSigned,
                validFrom = sampleValidFrom,
                validUntil = sampleValidUntil,
            )

        assertEquals(sampleSigned, info.signed)
        assertEquals(sampleValidFrom, info.validFrom)
        assertEquals(sampleValidUntil, info.validUntil)
        assertNull(info.expectedUpdate)
    }

    @Test
    fun testValidityInfoEquality() {
        val left = ValidityInfo(sampleSigned, sampleValidFrom, sampleValidUntil, sampleExpectedUpdate)
        val right = ValidityInfo(sampleSigned, sampleValidFrom, sampleValidUntil, sampleExpectedUpdate)

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
    }

    @Test
    fun testValidityInfoInequality() {
        val left = ValidityInfo(sampleSigned, sampleValidFrom, sampleValidUntil, sampleExpectedUpdate)
        val right = ValidityInfo(sampleSigned, sampleValidFrom, TDate("2026-01-01T00:00:00Z"), sampleExpectedUpdate)

        assertNotEquals(left, right)
        assertFalse(left.equals("not ValidityInfo"))
    }

    @Test
    fun testValidityInfoToString() {
        val text = ValidityInfo(sampleSigned, sampleValidFrom, sampleValidUntil, sampleExpectedUpdate).toString()

        assertTrue(text.contains("ValidityInfo"))
        assertTrue(text.contains("signed"))
        assertTrue(text.contains("expectedUpdate"))
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
        assertEquals("com.sphereon.mdoc.data.mso.ValidityInfo", ValidityInfoSerializer.descriptor.serialName)
    }

    @Test
    fun testValidityInfoJsonSerializationRoundTrip() {
        val info = ValidityInfo(sampleSigned, sampleValidFrom, sampleValidUntil, sampleExpectedUpdate)
        val json = Json.encodeToString(ValidityInfoSerializer, info)
        val decoded = Json.decodeFromString(ValidityInfoSerializer, json)

        assertEquals(info, decoded)
    }

    @Test
    fun testValidityInfoFromDatesWithMinimalParams() {
        val now = DateTimeUtils.DEFAULTS.dateTimeLocal()
        val validUntil =
            LocalDateTimeKMP(
                now.year + 1,
                now.month,
                now.day,
                now.hour,
                now.minute,
                now.second,
                now.nanosecond,
            )

        val info = ValidityInfo.fromDates(validUntil = validUntil)

        assertNotNull(info.signed)
        assertNotNull(info.validFrom)
        assertNotNull(info.validUntil)
        assertNull(info.expectedUpdate)
    }
}
