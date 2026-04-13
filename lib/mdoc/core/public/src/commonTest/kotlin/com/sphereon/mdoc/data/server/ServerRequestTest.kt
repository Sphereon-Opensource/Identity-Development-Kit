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

import com.sphereon.cbor.CborString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for ServerRequest data class.
 */
class ServerRequestTest {

    @Test
    fun testServerRequestCreation() {
        val itemsRequest = ServerItemsRequest(
            docType = CborString("org.iso.18013.5.1.mDL"),
            nameSpaces = mutableMapOf("org.iso.18013.5.1" to mutableMapOf("family_name" to true)),
            requestInfo = null
        )
        val request = ServerRequest(
            version = "1.0",
            token = "test-token-123",
            docRequests = arrayOf(itemsRequest)
        )

        assertEquals("1.0", request.version)
        assertEquals("test-token-123", request.token)
        assertEquals(1, request.docRequests.size)
    }

    @Test
    fun testServerRequestDefaultVersion() {
        val itemsRequest = ServerItemsRequest(
            docType = CborString("org.iso.18013.5.1.mDL"),
            nameSpaces = mutableMapOf(),
            requestInfo = null
        )
        val request = ServerRequest(
            token = "test-token",
            docRequests = arrayOf(itemsRequest)
        )

        assertEquals("1.0", request.version)
    }

    @Test
    fun testServerRequestMultipleDocRequests() {
        val itemsRequest1 = ServerItemsRequest(
            docType = CborString("org.iso.18013.5.1.mDL"),
            nameSpaces = mutableMapOf("org.iso.18013.5.1" to mutableMapOf("family_name" to true)),
            requestInfo = null
        )
        val itemsRequest2 = ServerItemsRequest(
            docType = CborString("eu.europa.ec.eudi.pid.1"),
            nameSpaces = mutableMapOf("eu.europa.ec.eudi.pid.1" to mutableMapOf("given_name" to true)),
            requestInfo = null
        )
        val request = ServerRequest(
            token = "multi-doc-token",
            docRequests = arrayOf(itemsRequest1, itemsRequest2)
        )

        assertEquals(2, request.docRequests.size)
        assertEquals("org.iso.18013.5.1.mDL", request.docRequests[0].docType.value)
        assertEquals("eu.europa.ec.eudi.pid.1", request.docRequests[1].docType.value)
    }

    @Test
    fun testServerRequestEmptyDocRequests() {
        val request = ServerRequest(
            token = "empty-token",
            docRequests = arrayOf()
        )

        assertTrue(request.docRequests.isEmpty())
    }

    @Test
    fun testServerRequestToString() {
        val itemsRequest = ServerItemsRequest(
            docType = CborString("org.iso.18013.5.1.mDL"),
            nameSpaces = mutableMapOf(),
            requestInfo = null
        )
        val request = ServerRequest(
            token = "test-token",
            docRequests = arrayOf(itemsRequest)
        )
        val str = request.toString()

        assertTrue(str.contains("ServerRequest"))
        assertTrue(str.contains("test-token"))
    }

    @Test
    fun testServerRequestCopy() {
        val itemsRequest = ServerItemsRequest(
            docType = CborString("org.iso.18013.5.1.mDL"),
            nameSpaces = mutableMapOf(),
            requestInfo = null
        )
        val request = ServerRequest(
            token = "original-token",
            docRequests = arrayOf(itemsRequest)
        )
        val copied = request.copy(token = "new-token")

        assertEquals("new-token", copied.token)
        assertEquals(request.version, copied.version)
    }

    @Test
    fun testServerRequestHashCode() {
        val itemsRequest = ServerItemsRequest(
            docType = CborString("org.iso.18013.5.1.mDL"),
            nameSpaces = mutableMapOf(),
            requestInfo = null
        )
        val request1 = ServerRequest(token = "token1", docRequests = arrayOf(itemsRequest))
        val request2 = ServerRequest(token = "token1", docRequests = arrayOf(itemsRequest))

        // Note: Arrays have identity-based hashCode, so these may differ
        // This tests that hashCode doesn't throw
        request1.hashCode()
        request2.hashCode()
    }

    @Test
    fun testServerRequestEqualitySameInstance() {
        val itemsRequest = ServerItemsRequest(
            docType = CborString("org.iso.18013.5.1.mDL"),
            nameSpaces = mutableMapOf(),
            requestInfo = null
        )
        val request = ServerRequest(token = "token1", docRequests = arrayOf(itemsRequest))

        assertEquals(request, request)
    }

    @Test
    fun testServerRequestInequalityDifferentToken() {
        val itemsRequest = ServerItemsRequest(
            docType = CborString("org.iso.18013.5.1.mDL"),
            nameSpaces = mutableMapOf(),
            requestInfo = null
        )
        val docRequests = arrayOf(itemsRequest)
        val request1 = ServerRequest(token = "token1", docRequests = docRequests)
        val request2 = ServerRequest(token = "token2", docRequests = docRequests)

        assertFalse(request1.equals(request2))
    }

    @Test
    fun testServerRequestInequalityDifferentVersion() {
        val itemsRequest = ServerItemsRequest(
            docType = CborString("org.iso.18013.5.1.mDL"),
            nameSpaces = mutableMapOf(),
            requestInfo = null
        )
        val docRequests = arrayOf(itemsRequest)
        val request1 = ServerRequest(version = "1.0", token = "token", docRequests = docRequests)
        val request2 = ServerRequest(version = "2.0", token = "token", docRequests = docRequests)

        assertFalse(request1.equals(request2))
    }
}
