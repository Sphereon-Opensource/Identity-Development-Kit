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

package com.sphereon.mdoc.data.server

import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.DocumentError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ServerResponse data class.
 */
class ServerResponseTest {
    @Test
    fun testServerResponseCreation() {
        val response =
            ServerResponse(
                version = "1.0",
                documents = arrayOf("jwt.token.here"),
                documentErrors = arrayOf(),
            )

        assertEquals("1.0", response.version)
        assertEquals(1, response.documents?.size)
        assertTrue(response.documentErrors.isEmpty())
    }

    @Test
    fun testServerResponseWithNullDocuments() {
        val response =
            ServerResponse(
                version = "1.0",
                documents = null,
                documentErrors = arrayOf(),
            )

        assertNull(response.documents)
    }

    @Test
    fun testServerResponseWithMultipleDocuments() {
        val response =
            ServerResponse(
                version = "1.0",
                documents = arrayOf("jwt1.token.here", "jwt2.token.here", "jwt3.token.here"),
                documentErrors = arrayOf(),
            )

        assertEquals(3, response.documents?.size)
        assertEquals("jwt1.token.here", response.documents?.get(0))
        assertEquals("jwt2.token.here", response.documents?.get(1))
        assertEquals("jwt3.token.here", response.documents?.get(2))
    }

    @Test
    fun testServerResponseWithDocumentErrors() {
        val docType = DocType("org.iso.18013.5.1.mDL")
        val error = DocumentError(1)
        val errors = arrayOf(mapOf(docType to error))
        val response =
            ServerResponse(
                version = "1.0",
                documents = null,
                documentErrors = errors,
            )

        assertEquals(1, response.documentErrors.size)
    }

    @Test
    fun testServerResponseEqualitySameInstance() {
        val response =
            ServerResponse(
                version = "1.0",
                documents = arrayOf("jwt.token"),
                documentErrors = arrayOf(),
            )

        assertTrue(response.equals(response))
    }

    @Test
    fun testServerResponseEqualityEqualValues() {
        val documents = arrayOf("jwt.token")
        val errors = arrayOf<Map<DocType, DocumentError>>()
        val response1 =
            ServerResponse(
                version = "1.0",
                documents = documents,
                documentErrors = errors,
            )
        val response2 =
            ServerResponse(
                version = "1.0",
                documents = documents,
                documentErrors = errors,
            )

        // Same array references
        assertEquals(response1, response2)
    }

    @Test
    fun testServerResponseEqualityContentEquals() {
        val response1 =
            ServerResponse(
                version = "1.0",
                documents = arrayOf("jwt.token"),
                documentErrors = arrayOf(),
            )
        val response2 =
            ServerResponse(
                version = "1.0",
                documents = arrayOf("jwt.token"),
                documentErrors = arrayOf(),
            )

        // Custom equals uses contentEquals for arrays
        assertEquals(response1, response2)
    }

    @Test
    fun testServerResponseInequalityDifferentType() {
        val response =
            ServerResponse(
                version = "1.0",
                documents = null,
                documentErrors = arrayOf(),
            )

        assertFalse(response.equals("not a response"))
    }

    @Test
    fun testServerResponseInequalityDifferentVersion() {
        val response1 =
            ServerResponse(
                version = "1.0",
                documents = null,
                documentErrors = arrayOf(),
            )
        val response2 =
            ServerResponse(
                version = "2.0",
                documents = null,
                documentErrors = arrayOf(),
            )

        assertNotEquals(response1, response2)
    }

    @Test
    fun testServerResponseInequalityDocumentsNullVsNonNull() {
        val response1 =
            ServerResponse(
                version = "1.0",
                documents = null,
                documentErrors = arrayOf(),
            )
        val response2 =
            ServerResponse(
                version = "1.0",
                documents = arrayOf("jwt.token"),
                documentErrors = arrayOf(),
            )

        assertNotEquals(response1, response2)
    }

    @Test
    fun testServerResponseInequalityDocumentsNonNullVsNull() {
        val response1 =
            ServerResponse(
                version = "1.0",
                documents = arrayOf("jwt.token"),
                documentErrors = arrayOf(),
            )
        val response2 =
            ServerResponse(
                version = "1.0",
                documents = null,
                documentErrors = arrayOf(),
            )

        assertNotEquals(response1, response2)
    }

    @Test
    fun testServerResponseInequalityDifferentDocuments() {
        val response1 =
            ServerResponse(
                version = "1.0",
                documents = arrayOf("jwt1.token"),
                documentErrors = arrayOf(),
            )
        val response2 =
            ServerResponse(
                version = "1.0",
                documents = arrayOf("jwt2.token"),
                documentErrors = arrayOf(),
            )

        assertNotEquals(response1, response2)
    }

    @Test
    fun testServerResponseInequalityDifferentDocumentErrors() {
        val docType1 = DocType("org.iso.18013.5.1.mDL")
        val docType2 = DocType("eu.europa.ec.eudi.pid.1")
        val response1 =
            ServerResponse(
                version = "1.0",
                documents = null,
                documentErrors = arrayOf(mapOf(docType1 to DocumentError(1))),
            )
        val response2 =
            ServerResponse(
                version = "1.0",
                documents = null,
                documentErrors = arrayOf(mapOf(docType2 to DocumentError(2))),
            )

        assertNotEquals(response1, response2)
    }

    @Test
    fun testServerResponseHashCode() {
        val response1 =
            ServerResponse(
                version = "1.0",
                documents = arrayOf("jwt.token"),
                documentErrors = arrayOf(),
            )
        val response2 =
            ServerResponse(
                version = "1.0",
                documents = arrayOf("jwt.token"),
                documentErrors = arrayOf(),
            )

        // contentHashCode is used, so equal content should have same hash
        assertEquals(response1.hashCode(), response2.hashCode())
    }

    @Test
    fun testServerResponseHashCodeWithNullDocuments() {
        val response1 =
            ServerResponse(
                version = "1.0",
                documents = null,
                documentErrors = arrayOf(),
            )
        val response2 =
            ServerResponse(
                version = "1.0",
                documents = null,
                documentErrors = arrayOf(),
            )

        assertEquals(response1.hashCode(), response2.hashCode())
    }

    @Test
    fun testServerResponseToString() {
        val response =
            ServerResponse(
                version = "1.0",
                documents = arrayOf("jwt.token"),
                documentErrors = arrayOf(),
            )
        val str = response.toString()

        assertTrue(str.contains("ServerResponse"))
        assertTrue(str.contains("1.0"))
    }

    @Test
    fun testServerResponseCopy() {
        val response =
            ServerResponse(
                version = "1.0",
                documents = arrayOf("jwt.token"),
                documentErrors = arrayOf(),
            )
        val copied = response.copy(version = "2.0")

        assertEquals("2.0", copied.version)
    }

    @Test
    fun testServerResponseBothDocumentsNull() {
        val response1 =
            ServerResponse(
                version = "1.0",
                documents = null,
                documentErrors = arrayOf(),
            )
        val response2 =
            ServerResponse(
                version = "1.0",
                documents = null,
                documentErrors = arrayOf(),
            )

        assertEquals(response1, response2)
    }
}
