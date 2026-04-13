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

package com.sphereon.mdoc

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborUInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for SessionData and SessionDataJson.
 */
class SessionDataTest {

    @Test
    fun testSessionDataCreationWithData() {
        val data = CborByteString("test-data".encodeToByteArray())
        val sessionData = SessionData(data = data, status = null, original = null)

        assertNotNull(sessionData.data)
        assertNull(sessionData.status)
        assertNull(sessionData.getStatus())
    }

    @Test
    fun testSessionDataCreationWithStatus() {
        val status = CborUInt(20L)
        val sessionData = SessionData(data = null, status = status, original = null)

        assertNull(sessionData.data)
        assertNotNull(sessionData.status)
        assertEquals(SessionDataStatus.SESSION_TERMINATION, sessionData.getStatus())
    }

    @Test
    fun testSessionDataCreationWithBoth() {
        val data = CborByteString("test-data".encodeToByteArray())
        val status = CborUInt(10L)
        val sessionData = SessionData(data = data, status = status, original = null)

        assertNotNull(sessionData.data)
        assertNotNull(sessionData.status)
        assertEquals(SessionDataStatus.ERROR_SESSION_ENCRYPTION, sessionData.getStatus())
    }

    @Test
    fun testGetStatusErrorSessionEncryption() {
        val sessionData = SessionData(data = null, status = CborUInt(10L), original = null)
        assertEquals(SessionDataStatus.ERROR_SESSION_ENCRYPTION, sessionData.getStatus())
    }

    @Test
    fun testGetStatusErrorCborDecoding() {
        val sessionData = SessionData(data = null, status = CborUInt(11L), original = null)
        assertEquals(SessionDataStatus.ERROR_CBOR_DECODING, sessionData.getStatus())
    }

    @Test
    fun testGetStatusSessionTermination() {
        val sessionData = SessionData(data = null, status = CborUInt(20L), original = null)
        assertEquals(SessionDataStatus.SESSION_TERMINATION, sessionData.getStatus())
    }

    @Test
    fun testGetStatusUnknownReturnsNull() {
        val sessionData = SessionData(data = null, status = CborUInt(99L), original = null)
        assertNull(sessionData.getStatus())
    }

    @Test
    fun testCopyWith() {
        val original = SessionData(
            data = CborByteString("original".encodeToByteArray()),
            status = CborUInt(10L),
            original = null
        )

        val newData = CborByteString("modified".encodeToByteArray())
        val copied = original.copyWith(data = newData)

        assertEquals(newData, copied.data)
        assertEquals(original.status, copied.status)
    }

    @Test
    fun testToString() {
        val sessionData = SessionData(
            data = CborByteString("test".encodeToByteArray()),
            status = CborUInt(20L),
            original = null
        )

        val str = sessionData.toString()
        assertNotNull(str)
        assertTrue(str.contains("SessionData"))
    }

    @Test
    fun testCborEncodeDecode() {
        val original = SessionData(
            data = CborByteString("test-data".encodeToByteArray()),
            status = CborUInt(20L),
            original = null
        )

        val encoded = original.encodeCbor()
        val decoded = SessionData.decodeCbor(encoded)

        assertNotNull(decoded.data)
        assertEquals(original.status?.value, decoded.status?.value)
    }

    @Test
    fun testSessionDataJsonToJsonStringThrows() {
        val json = SessionDataJson(data = "test", status = 20L)

        try {
            json.toJsonString()
            throw AssertionError("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Only cbor version") == true)
        }
    }

    @Test
    fun testSessionDataJsonToCborThrows() {
        val json = SessionDataJson(data = "test", status = 20L)

        try {
            json.toCbor()
            throw AssertionError("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Only cbor version") == true)
        }
    }
}
