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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for DeviceRetrievalStatusCode enum.
 */
class DeviceRetrievalStatusCodeTest {
    @Test
    fun testOkStatusCode() {
        val status = DeviceRetrievalStatusCode.OK
        assertEquals(0L, status.statusCode)
        assertEquals("OK", status.statusMessage)
        assertTrue(status.explanation.contains("Normal processing"))
        assertTrue(status.actionsRequired.contains("No specific action"))
    }

    @Test
    fun testGeneralErrorStatusCode() {
        val status = DeviceRetrievalStatusCode.GENERAL_ERROR
        assertEquals(10L, status.statusCode)
        assertEquals("General Error", status.statusMessage)
        assertTrue(status.explanation.contains("error without any given reason"))
    }

    @Test
    fun testCborDecodingErrorStatusCode() {
        val status = DeviceRetrievalStatusCode.CBOR_DECODING_ERROR
        assertEquals(11L, status.statusCode)
        assertEquals("CBOR decoding error", status.statusMessage)
        assertTrue(status.explanation.contains("CBOR decoding"))
        assertTrue(status.explanation.contains("not valid CBOR"))
    }

    @Test
    fun testCborValidationErrorStatusCode() {
        val status = DeviceRetrievalStatusCode.CBOR_VALIDATION_ERROR
        assertEquals(12L, status.statusCode)
        assertEquals("CBOR validation error", status.statusMessage)
        assertTrue(status.explanation.contains("CBOR validation"))
    }

    @Test
    fun testAllEntriesCount() {
        assertEquals(4, DeviceRetrievalStatusCode.entries.size)
    }

    @Test
    fun testFromStatusCodeOk() {
        val status = DeviceRetrievalStatusCode.fromStatusCode(0L)
        assertEquals(DeviceRetrievalStatusCode.OK, status)
    }

    @Test
    fun testFromStatusCodeGeneralError() {
        val status = DeviceRetrievalStatusCode.fromStatusCode(10L)
        assertEquals(DeviceRetrievalStatusCode.GENERAL_ERROR, status)
    }

    @Test
    fun testFromStatusCodeCborDecodingError() {
        val status = DeviceRetrievalStatusCode.fromStatusCode(11L)
        assertEquals(DeviceRetrievalStatusCode.CBOR_DECODING_ERROR, status)
    }

    @Test
    fun testFromStatusCodeCborValidationError() {
        val status = DeviceRetrievalStatusCode.fromStatusCode(12L)
        assertEquals(DeviceRetrievalStatusCode.CBOR_VALIDATION_ERROR, status)
    }

    @Test
    fun testFromStatusCodeUnknownThrows() {
        assertFailsWith<NoSuchElementException> {
            DeviceRetrievalStatusCode.fromStatusCode(99L)
        }
    }

    @Test
    fun testStatusCodesAreUnique() {
        val codes = DeviceRetrievalStatusCode.entries.map { it.statusCode }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun testValueOf() {
        assertEquals(DeviceRetrievalStatusCode.OK, DeviceRetrievalStatusCode.valueOf("OK"))
        assertEquals(DeviceRetrievalStatusCode.GENERAL_ERROR, DeviceRetrievalStatusCode.valueOf("GENERAL_ERROR"))
        assertEquals(DeviceRetrievalStatusCode.CBOR_DECODING_ERROR, DeviceRetrievalStatusCode.valueOf("CBOR_DECODING_ERROR"))
        assertEquals(DeviceRetrievalStatusCode.CBOR_VALIDATION_ERROR, DeviceRetrievalStatusCode.valueOf("CBOR_VALIDATION_ERROR"))
    }

    @Test
    fun testOrdinalValues() {
        assertEquals(0, DeviceRetrievalStatusCode.OK.ordinal)
        assertEquals(1, DeviceRetrievalStatusCode.GENERAL_ERROR.ordinal)
        assertEquals(2, DeviceRetrievalStatusCode.CBOR_DECODING_ERROR.ordinal)
        assertEquals(3, DeviceRetrievalStatusCode.CBOR_VALIDATION_ERROR.ordinal)
    }

    @Test
    fun testActionsRequiredPresent() {
        DeviceRetrievalStatusCode.entries.forEach { status ->
            assertTrue(status.actionsRequired.isNotEmpty(), "actionsRequired should not be empty for $status")
        }
    }

    @Test
    fun testExplanationPresent() {
        DeviceRetrievalStatusCode.entries.forEach { status ->
            assertTrue(status.explanation.isNotEmpty(), "explanation should not be empty for $status")
        }
    }
}
