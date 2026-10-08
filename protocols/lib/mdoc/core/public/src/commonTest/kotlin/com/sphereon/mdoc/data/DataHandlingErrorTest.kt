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

package com.sphereon.mdoc.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for DataHandlingError and DataHandlingErrorCode enums.
 */
class DataHandlingErrorTest {
    // DataHandlingErrorCode tests

    @Test
    fun testErrorCodeOkValue() {
        assertEquals(0L, DataHandlingErrorCode.OK.value)
    }

    @Test
    fun testErrorCodeRfuValue() {
        assertEquals(1L, DataHandlingErrorCode.RFU.value)
    }

    @Test
    fun testErrorCodeApplicationSpecificValue() {
        assertEquals(-1L, DataHandlingErrorCode.APPLICATION_SPECIFIC.value)
    }

    @Test
    fun testErrorCodeEntriesCount() {
        assertEquals(3, DataHandlingErrorCode.entries.size)
    }

    @Test
    fun testFromErrorCodeValueOk() {
        val code = DataHandlingErrorCode.fromErrorCodeValue(0L)
        assertEquals(DataHandlingErrorCode.OK, code)
    }

    @Test
    fun testFromErrorCodeValueRfu() {
        val code = DataHandlingErrorCode.fromErrorCodeValue(1L)
        assertEquals(DataHandlingErrorCode.RFU, code)
    }

    @Test
    fun testFromErrorCodeValueRfuPositive() {
        // Any positive value >= 1 should map to RFU
        val code = DataHandlingErrorCode.fromErrorCodeValue(5L)
        assertEquals(DataHandlingErrorCode.RFU, code)
    }

    @Test
    fun testFromErrorCodeValueRfuLarge() {
        val code = DataHandlingErrorCode.fromErrorCodeValue(100L)
        assertEquals(DataHandlingErrorCode.RFU, code)
    }

    @Test
    fun testFromErrorCodeValueApplicationSpecific() {
        // Negative values map to APPLICATION_SPECIFIC
        val code = DataHandlingErrorCode.fromErrorCodeValue(-1L)
        assertEquals(DataHandlingErrorCode.APPLICATION_SPECIFIC, code)
    }

    @Test
    fun testFromErrorCodeValueApplicationSpecificNegative() {
        val code = DataHandlingErrorCode.fromErrorCodeValue(-5L)
        assertEquals(DataHandlingErrorCode.APPLICATION_SPECIFIC, code)
    }

    @Test
    fun testFromErrorCodeValueApplicationSpecificLargeNegative() {
        val code = DataHandlingErrorCode.fromErrorCodeValue(-100L)
        assertEquals(DataHandlingErrorCode.APPLICATION_SPECIFIC, code)
    }

    // DataHandlingError tests

    @Test
    fun testDataHandlingErrorOk() {
        val error = DataHandlingError.OK
        assertEquals(DataHandlingErrorCode.OK, error.errorCode)
        assertEquals("Data not returned", error.errorCodeMessage)
        assertTrue(error.errorDescription.contains("does not provide"))
    }

    @Test
    fun testDataHandlingErrorRfu() {
        val error = DataHandlingError.RFU
        assertEquals(DataHandlingErrorCode.RFU, error.errorCode)
        assertEquals("RFU", error.errorCodeMessage)
        assertEquals("RFU", error.errorDescription)
    }

    @Test
    fun testDataHandlingErrorApplicationSpecific() {
        val error = DataHandlingError.APPLICATION_SPECIFIC
        assertEquals(DataHandlingErrorCode.APPLICATION_SPECIFIC, error.errorCode)
        assertTrue(error.errorCodeMessage.contains("application-specific"))
    }

    @Test
    fun testDataHandlingErrorEntriesCount() {
        assertEquals(3, DataHandlingError.entries.size)
    }

    @Test
    fun testFromErrorCodeOk() {
        val error = DataHandlingError.fromErrorCode(0L)
        assertEquals(DataHandlingError.OK, error)
    }

    @Test
    fun testFromErrorCodeRfu() {
        val error = DataHandlingError.fromErrorCode(1L)
        assertEquals(DataHandlingError.RFU, error)
    }

    @Test
    fun testFromErrorCodeRfuPositive() {
        val error = DataHandlingError.fromErrorCode(5L)
        assertEquals(DataHandlingError.RFU, error)
    }

    @Test
    fun testFromErrorCodeApplicationSpecific() {
        val error = DataHandlingError.fromErrorCode(-1L)
        assertEquals(DataHandlingError.APPLICATION_SPECIFIC, error)
    }

    @Test
    fun testFromErrorCodeApplicationSpecificNegative() {
        val error = DataHandlingError.fromErrorCode(-10L)
        assertEquals(DataHandlingError.APPLICATION_SPECIFIC, error)
    }

    @Test
    fun testErrorCodesAreUnique() {
        val codes = DataHandlingErrorCode.entries.map { it.value }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun testValueOf() {
        assertEquals(DataHandlingError.OK, DataHandlingError.valueOf("OK"))
        assertEquals(DataHandlingError.RFU, DataHandlingError.valueOf("RFU"))
        assertEquals(DataHandlingError.APPLICATION_SPECIFIC, DataHandlingError.valueOf("APPLICATION_SPECIFIC"))
    }

    @Test
    fun testErrorCodeValueOf() {
        assertEquals(DataHandlingErrorCode.OK, DataHandlingErrorCode.valueOf("OK"))
        assertEquals(DataHandlingErrorCode.RFU, DataHandlingErrorCode.valueOf("RFU"))
        assertEquals(DataHandlingErrorCode.APPLICATION_SPECIFIC, DataHandlingErrorCode.valueOf("APPLICATION_SPECIFIC"))
    }
}
