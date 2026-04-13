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

package com.sphereon.mdoc.data.server

import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ServerItemsRequest data class.
 */
class ServerItemsRequestTest {

    @Test
    fun testServerItemsRequestCreation() {
        val nameSpaces = mutableMapOf(
            "org.iso.18013.5.1" to mutableMapOf("family_name" to true, "given_name" to false)
        )
        val request = ServerItemsRequest(
            docType = CborString("org.iso.18013.5.1.mDL"),
            nameSpaces = nameSpaces,
            requestInfo = null
        )

        assertEquals("org.iso.18013.5.1.mDL", request.docType.value)
        assertEquals(1, request.nameSpaces.size)
        assertNull(request.requestInfo)
    }

    @Test
    fun testServerItemsRequestWithRequestInfo() {
        val nameSpaces = mutableMapOf(
            "org.iso.18013.5.1" to mutableMapOf("family_name" to true)
        )
        val requestInfo = CborMap(mutableMapOf(
            CborString("purpose") to CborString("verification")
        ))
        val request = ServerItemsRequest(
            docType = CborString("org.iso.18013.5.1.mDL"),
            nameSpaces = nameSpaces,
            requestInfo = requestInfo
        )

        assertEquals(requestInfo, request.requestInfo)
    }

    @Test
    fun testServerItemsRequestMultipleNameSpaces() {
        val nameSpaces = mutableMapOf(
            "org.iso.18013.5.1" to mutableMapOf("family_name" to true, "given_name" to true),
            "org.iso.18013.5.1.aamva" to mutableMapOf("DHS_compliance" to false)
        )
        val request = ServerItemsRequest(
            docType = CborString("org.iso.18013.5.1.mDL"),
            nameSpaces = nameSpaces,
            requestInfo = null
        )

        assertEquals(2, request.nameSpaces.size)
        assertTrue(request.nameSpaces.containsKey("org.iso.18013.5.1"))
        assertTrue(request.nameSpaces.containsKey("org.iso.18013.5.1.aamva"))
    }

    @Test
    fun testServerItemsRequestEquality() {
        val nameSpaces1 = mutableMapOf(
            "org.iso.18013.5.1" to mutableMapOf("family_name" to true)
        )
        val nameSpaces2 = mutableMapOf(
            "org.iso.18013.5.1" to mutableMapOf("family_name" to true)
        )
        val request1 = ServerItemsRequest(CborString("org.iso.18013.5.1.mDL"), nameSpaces1, null)
        val request2 = ServerItemsRequest(CborString("org.iso.18013.5.1.mDL"), nameSpaces2, null)

        assertEquals(request1, request2)
    }

    @Test
    fun testServerItemsRequestInequalityDocType() {
        val nameSpaces = mutableMapOf(
            "org.iso.18013.5.1" to mutableMapOf("family_name" to true)
        )
        val request1 = ServerItemsRequest(CborString("org.iso.18013.5.1.mDL"), nameSpaces, null)
        val request2 = ServerItemsRequest(CborString("eu.europa.ec.eudi.pid.1"), nameSpaces, null)

        assertNotEquals(request1, request2)
    }

    @Test
    fun testServerItemsRequestInequalityNameSpaces() {
        val nameSpaces1 = mutableMapOf(
            "org.iso.18013.5.1" to mutableMapOf("family_name" to true)
        )
        val nameSpaces2 = mutableMapOf(
            "org.iso.18013.5.1" to mutableMapOf("given_name" to true)
        )
        val request1 = ServerItemsRequest(CborString("org.iso.18013.5.1.mDL"), nameSpaces1, null)
        val request2 = ServerItemsRequest(CborString("org.iso.18013.5.1.mDL"), nameSpaces2, null)

        assertNotEquals(request1, request2)
    }

    @Test
    fun testServerItemsRequestHashCode() {
        val nameSpaces = mutableMapOf(
            "org.iso.18013.5.1" to mutableMapOf("family_name" to true)
        )
        val request1 = ServerItemsRequest(CborString("org.iso.18013.5.1.mDL"), nameSpaces, null)
        val request2 = ServerItemsRequest(CborString("org.iso.18013.5.1.mDL"), nameSpaces, null)

        assertEquals(request1.hashCode(), request2.hashCode())
    }

    @Test
    fun testServerItemsRequestToString() {
        val nameSpaces = mutableMapOf(
            "org.iso.18013.5.1" to mutableMapOf("family_name" to true)
        )
        val request = ServerItemsRequest(CborString("org.iso.18013.5.1.mDL"), nameSpaces, null)
        val str = request.toString()

        assertTrue(str.contains("ServerItemsRequest"))
        assertTrue(str.contains("org.iso.18013.5.1.mDL"))
    }

    @Test
    fun testServerItemsRequestCopy() {
        val nameSpaces = mutableMapOf(
            "org.iso.18013.5.1" to mutableMapOf("family_name" to true)
        )
        val request = ServerItemsRequest(CborString("org.iso.18013.5.1.mDL"), nameSpaces, null)
        val copied = request.copy(docType = CborString("eu.europa.ec.eudi.pid.1"))

        assertEquals("eu.europa.ec.eudi.pid.1", copied.docType.value)
        assertEquals(request.nameSpaces, copied.nameSpaces)
    }

    @Test
    fun testServerItemsRequestEmptyNameSpaces() {
        val request = ServerItemsRequest(
            docType = CborString("org.iso.18013.5.1.mDL"),
            nameSpaces = mutableMapOf(),
            requestInfo = null
        )

        assertTrue(request.nameSpaces.isEmpty())
    }

    @Test
    fun testServerItemsRequestMutableNameSpaces() {
        val nameSpaces = mutableMapOf(
            "org.iso.18013.5.1" to mutableMapOf("family_name" to true)
        )
        val request = ServerItemsRequest(CborString("org.iso.18013.5.1.mDL"), nameSpaces, null)

        // Verify nameSpaces is mutable
        request.nameSpaces["new_namespace"] = mutableMapOf("new_element" to true)
        assertEquals(2, request.nameSpaces.size)
    }
}
