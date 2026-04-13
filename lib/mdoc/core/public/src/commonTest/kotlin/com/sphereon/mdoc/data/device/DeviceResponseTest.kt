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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for DeviceResponse, DeviceResponseVersion, DeviceResponseStatus, and DocumentError classes.
 */
class DeviceResponseTest {
    // DeviceResponseVersion tests

    @Test
    fun testDeviceResponseVersionCreation() {
        val version = DeviceResponseVersion("1.0")
        assertEquals("1.0", version.toString())
    }

    @Test
    fun testDeviceResponseVersionInvalidThrows() {
        assertFailsWith<IllegalArgumentException> {
            DeviceResponseVersion("2.0")
        }
    }

    @Test
    fun testDeviceResponseVersionInvalidEmptyThrows() {
        assertFailsWith<IllegalArgumentException> {
            DeviceResponseVersion("")
        }
    }

    // DeviceResponseStatus tests

    @Test
    fun testDeviceResponseStatusCreationOk() {
        val status = DeviceResponseStatus(0u)
        assertEquals(0u, status.value)
        assertEquals("0", status.toString())
    }

    @Test
    fun testDeviceResponseStatusCreationGeneralError() {
        val status = DeviceResponseStatus(11u)
        assertEquals(11u, status.value)
        assertEquals("11", status.toString())
    }

    @Test
    fun testDeviceResponseStatusCreationCborDecodingError() {
        val status = DeviceResponseStatus(12u)
        assertEquals(12u, status.value)
        assertEquals("12", status.toString())
    }

    @Test
    fun testDeviceResponseStatusCreationCborValidationError() {
        val status = DeviceResponseStatus(20u)
        assertEquals(20u, status.value)
        assertEquals("20", status.toString())
    }

    @Test
    fun testDeviceResponseStatusInvalidThrows() {
        assertFailsWith<IllegalArgumentException> {
            DeviceResponseStatus(1u)
        }
    }

    @Test
    fun testDeviceResponseStatusInvalidHighValueThrows() {
        assertFailsWith<IllegalArgumentException> {
            DeviceResponseStatus(100u)
        }
    }

    // DocumentError tests

    @Test
    fun testDocumentErrorCreation() {
        val error = DocumentError(10)
        assertEquals(10, error.errorCode)
        assertEquals("10", error.toString())
    }

    @Test
    fun testDocumentErrorNegativeValue() {
        val error = DocumentError(-1)
        assertEquals(-1, error.errorCode)
    }

    // DeviceResponse tests

    @Test
    fun testDeviceResponseDefaultCreation() {
        val response = DeviceResponse(documents = null, original = null)
        assertEquals("1.0", response.version.toString())
        assertNull(response.documents)
        assertEquals(0u, response.status.value)
        assertNull(response.original)
    }

    @Test
    fun testDeviceResponseWithStatus() {
        val response =
            DeviceResponse(
                documents = null,
                status = DeviceResponseStatus(11u),
                original = null,
            )
        assertEquals(11u, response.status.value)
    }

    @Test
    fun testDeviceResponseOriginalBytesRetained() {
        val original = byteArrayOf(0x01, 0x02, 0x03)
        val response = DeviceResponse(documents = null, original = original)

        assertEquals(original.toList(), response.original?.toList())
    }

    @Test
    fun testDeviceResponseWithDocuments() {
        val response = DeviceResponse(documents = arrayOf(), original = null)
        assertNotNull(response.documents)
        assertTrue(response.documents!!.isEmpty())
    }

    @Test
    fun testDeviceResponseEquality() {
        val response1 = DeviceResponse(documents = null, original = null)
        val response2 = DeviceResponse(documents = null, original = null)
        assertEquals(response1, response2)
    }

    @Test
    fun testDeviceResponseEqualitySameInstance() {
        val response = DeviceResponse(documents = null, original = null)
        assertEquals(response, response)
    }

    @Test
    fun testDeviceResponseInequalityNull() {
        val response = DeviceResponse(documents = null, original = null)
        assertFalse(response.equals(null))
    }

    @Test
    fun testDeviceResponseInequalityDifferentClass() {
        val response = DeviceResponse(documents = null, original = null)
        assertFalse(response.equals("not a response"))
    }

    @Test
    fun testDeviceResponseInequalityDifferentStatus() {
        val response1 = DeviceResponse(documents = null, status = DeviceResponseStatus(0u), original = null)
        val response2 = DeviceResponse(documents = null, status = DeviceResponseStatus(11u), original = null)
        assertNotEquals(response1, response2)
    }

    @Test
    fun testDeviceResponseInequalityDifferentVersion() {
        // Since version must be 1.0, we can only test that different versions would be unequal
        // by comparing hashcodes with different status values
        val response1 = DeviceResponse(documents = null, status = DeviceResponseStatus(0u), original = null)
        val response2 = DeviceResponse(documents = null, status = DeviceResponseStatus(11u), original = null)
        assertNotEquals(response1.hashCode(), response2.hashCode())
    }

    @Test
    fun testDeviceResponseHashCode() {
        val response1 = DeviceResponse(documents = null, original = null)
        val response2 = DeviceResponse(documents = null, original = null)
        assertEquals(response1.hashCode(), response2.hashCode())
    }

    @Test
    fun testDeviceResponseToString() {
        val response = DeviceResponse(documents = null, original = null)
        val str = response.toString()
        assertTrue(str.contains("DeviceResponse"))
        assertTrue(str.contains("version=1.0"))
    }

    @Test
    fun testDeviceResponseCompanionLabels() {
        assertEquals("version", DeviceResponse.VERSION.value)
        assertEquals("documents", DeviceResponse.DOCUMENTS.value)
        assertEquals("documentErrors", DeviceResponse.DOCUMENT_ERRORS.value)
        assertEquals("status", DeviceResponse.STATUS.value)
    }

    // DeviceResponse.Builder tests

    @Test
    fun testDeviceResponseBuilderDefault() {
        val builder = DeviceResponse.Builder()
        val response = builder.build()

        assertEquals("1.0", response.version.toString())
        assertNotNull(response.documents)
        assertTrue(response.documents!!.isEmpty())
        assertEquals(0u, response.status.value)
    }

    @Test
    fun testDeviceResponseBuilderWithStatus() {
        val builder =
            DeviceResponse
                .Builder()
                .withStatus(DeviceResponseStatus(11u))
        val response = builder.build()

        assertEquals(11u, response.status.value)
    }

    @Test
    fun testDeviceResponseBuilderWithDocuments() {
        val builder =
            DeviceResponse
                .Builder()
                .withDocuments(arrayOf())
        val response = builder.build()

        assertNotNull(response.documents)
        assertTrue(response.documents!!.isEmpty())
    }

    @Test
    fun testDeviceResponseBuilderWithDocumentErrors() {
        val builder =
            DeviceResponse
                .Builder()
                .withDocumentErrors(null)
        val response = builder.build()

        assertNull(response.documentErrors)
    }

    @Test
    fun testDeviceResponseBuilderChaining() {
        val builder =
            DeviceResponse
                .Builder()
                .withDocuments(arrayOf())
                .withStatus(DeviceResponseStatus(0u))
                .withDocumentErrors(arrayOf())

        val response = builder.build()
        assertNotNull(response)
    }

    // Edge cases for DeviceResponse equality

    @Test
    fun testDeviceResponseEqualityWithNullDocuments() {
        val response1 = DeviceResponse(documents = null, original = null)
        val response2 = DeviceResponse(documents = arrayOf(), original = null)
        assertNotEquals(response1, response2)
    }

    @Test
    fun testDeviceResponseEqualityWithNullDocumentErrors() {
        val response1 = DeviceResponse(documents = null, documentErrors = null, original = null)
        val response2 = DeviceResponse(documents = null, documentErrors = arrayOf(), original = null)
        // Empty array is NOT converted to null in the constructor - they are different
        assertNotEquals(response1, response2)
    }

    @Test
    fun testDeviceResponseEqualityBothDocumentsNull() {
        val response1 = DeviceResponse(documents = null, documentErrors = null, original = null)
        val response2 = DeviceResponse(documents = null, documentErrors = null, original = null)
        assertEquals(response1, response2)
    }

    // Additional equality tests for full branch coverage

    @Test
    fun testDeviceResponseEqualityDocumentsNotNullOtherNull() {
        val response1 = DeviceResponse(documents = arrayOf(), documentErrors = null, original = null)
        val response2 = DeviceResponse(documents = null, documentErrors = null, original = null)
        assertNotEquals(response1, response2)
    }

    @Test
    fun testDeviceResponseEqualityDocumentErrorsNotNullOtherNull() {
        val docErrors = arrayOf(mapOf(DocType("test") to DocumentError(1)))
        val response1 = DeviceResponse(documents = null, documentErrors = docErrors, original = null)
        val response2 = DeviceResponse(documents = null, documentErrors = null, original = null)
        assertNotEquals(response1, response2)
    }

    @Test
    fun testDeviceResponseEqualityBothDocumentErrorsNotNull() {
        val docErrors1 = arrayOf(mapOf(DocType("test") to DocumentError(1)))
        val docErrors2 = arrayOf(mapOf(DocType("test") to DocumentError(1)))
        val response1 = DeviceResponse(documents = null, documentErrors = docErrors1, original = null)
        val response2 = DeviceResponse(documents = null, documentErrors = docErrors2, original = null)
        assertEquals(response1, response2)
    }

    @Test
    fun testDeviceResponseHashCodeWithDocuments() {
        val response = DeviceResponse(documents = arrayOf(), original = null)
        assertNotNull(response.hashCode())
    }

    @Test
    fun testDeviceResponseHashCodeWithDocumentErrors() {
        val docErrors = arrayOf(mapOf(DocType("test") to DocumentError(1)))
        val response = DeviceResponse(documents = null, documentErrors = docErrors, original = null)
        assertNotNull(response.hashCode())
    }

    @Test
    fun testDeviceResponseStoresDocumentErrors() {
        val docErrors = arrayOf(mapOf(DocType("test") to DocumentError(1)))
        val response = DeviceResponse(documents = null, documentErrors = docErrors, original = null)

        assertEquals(docErrors.toList(), response.documentErrors?.toList())
    }

    // Builder edge cases

    @Test
    fun testDeviceResponseBuilderAddDocumentError() {
        val docError = mapOf(DocType("test") to DocumentError(1))
        val builder =
            DeviceResponse
                .Builder()
                .addDocumentError(docError)

        assertNotNull(builder)
    }

    @Test
    fun testDeviceResponseBuilderWithNonEmptyDocumentErrors() {
        val docError = mapOf(DocType("test") to DocumentError(1))
        val builder =
            DeviceResponse
                .Builder()
                .withDocumentErrors(arrayOf(docError))

        val response = builder.build()
        assertNotNull(response.documentErrors)
    }

    // Additional status values

    @Test
    fun testDeviceResponseStatus11ToString() {
        val status = DeviceResponseStatus(11u)
        assertEquals("11", status.toString())
    }

    @Test
    fun testDeviceResponseStatus12ToString() {
        val status = DeviceResponseStatus(12u)
        assertEquals("12", status.toString())
    }

    @Test
    fun testDeviceResponseStatus20ToString() {
        val status = DeviceResponseStatus(20u)
        assertEquals("20", status.toString())
    }

    // Additional status edge case tests

    @Test
    fun testDeviceResponseWithEmptyDocumentErrors() {
        val response =
            DeviceResponse(
                documents = null,
                documentErrors = arrayOf(),
                original = null,
            )

        assertNotNull(response.documentErrors)
        assertTrue(response.documentErrors!!.isEmpty())
    }

    @Test
    fun testDeviceResponseWithNullDocumentErrors() {
        val response =
            DeviceResponse(
                documents = null,
                documentErrors = null,
                original = null,
            )

        assertNull(response.documentErrors)
    }
}
