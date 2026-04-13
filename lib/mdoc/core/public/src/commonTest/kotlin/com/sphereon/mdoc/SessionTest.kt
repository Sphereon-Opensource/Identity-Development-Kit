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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for Session-related classes.
 */
class SessionTest {

    // SessionDataStatus tests

    @Test
    fun testSessionDataStatusErrorSessionEncryption() {
        val status = SessionDataStatus.ERROR_SESSION_ENCRYPTION
        assertEquals(10L, status.getCode())
        assertEquals("Error: session encryption", status.errorDescription)
        assertTrue(status.requiredAction.contains("terminated"))
        assertTrue(status.isError())
    }

    @Test
    fun testSessionDataStatusErrorCborDecoding() {
        val status = SessionDataStatus.ERROR_CBOR_DECODING
        assertEquals(11L, status.getCode())
        assertEquals("Error: CBOR decoding", status.errorDescription)
        assertTrue(status.requiredAction.contains("terminated"))
        assertTrue(status.isError())
    }

    @Test
    fun testSessionDataStatusSessionTermination() {
        val status = SessionDataStatus.SESSION_TERMINATION
        assertEquals(20L, status.getCode())
        assertEquals("Session termination", status.errorDescription)
        assertTrue(status.requiredAction.contains("terminated"))
        assertFalse(status.isError())
    }

    @Test
    fun testSessionDataStatusEntriesCount() {
        assertEquals(3, SessionDataStatus.entries.size)
    }

    @Test
    fun testSessionDataStatusValueOf() {
        assertEquals(SessionDataStatus.ERROR_SESSION_ENCRYPTION, SessionDataStatus.valueOf("ERROR_SESSION_ENCRYPTION"))
        assertEquals(SessionDataStatus.ERROR_CBOR_DECODING, SessionDataStatus.valueOf("ERROR_CBOR_DECODING"))
        assertEquals(SessionDataStatus.SESSION_TERMINATION, SessionDataStatus.valueOf("SESSION_TERMINATION"))
    }

    // SessionData tests

    @Test
    fun testSessionDataCreationEmpty() {
        val sessionData = SessionData(data = null, status = null, original = null)
        assertNull(sessionData.data)
        assertNull(sessionData.status)
        assertNull(sessionData.original)
    }

    @Test
    fun testSessionDataWithData() {
        val data = CborByteString(byteArrayOf(1, 2, 3))
        val sessionData = SessionData(data = data, status = null, original = null)
        assertNotNull(sessionData.data)
        assertEquals(3, sessionData.data!!.value.size)
    }

    @Test
    fun testSessionDataWithStatus() {
        val status = CborUInt(20L)
        val sessionData = SessionData(data = null, status = status, original = null)
        assertNotNull(sessionData.status)
        assertEquals(20L, sessionData.status!!.value)
    }

    @Test
    fun testSessionDataGetStatusTermination() {
        val status = CborUInt(20L)
        val sessionData = SessionData(data = null, status = status, original = null)
        assertEquals(SessionDataStatus.SESSION_TERMINATION, sessionData.getStatus())
    }

    @Test
    fun testSessionDataGetStatusEncryptionError() {
        val status = CborUInt(10L)
        val sessionData = SessionData(data = null, status = status, original = null)
        assertEquals(SessionDataStatus.ERROR_SESSION_ENCRYPTION, sessionData.getStatus())
    }

    @Test
    fun testSessionDataGetStatusDecodingError() {
        val status = CborUInt(11L)
        val sessionData = SessionData(data = null, status = status, original = null)
        assertEquals(SessionDataStatus.ERROR_CBOR_DECODING, sessionData.getStatus())
    }

    @Test
    fun testSessionDataGetStatusNull() {
        val sessionData = SessionData(data = null, status = null, original = null)
        assertNull(sessionData.getStatus())
    }

    @Test
    fun testSessionDataGetStatusUnknown() {
        val status = CborUInt(99L)
        val sessionData = SessionData(data = null, status = status, original = null)
        assertNull(sessionData.getStatus())
    }

    @Test
    fun testSessionDataCopyWith() {
        val original = SessionData(data = null, status = null, original = null)
        val newData = CborByteString(byteArrayOf(1, 2, 3))
        val newStatus = CborUInt(20L)
        val copied = original.copyWith(data = newData, status = newStatus)

        assertNotNull(copied.data)
        assertNotNull(copied.status)
        assertEquals(newData, copied.data)
        assertEquals(newStatus, copied.status)
    }

    @Test
    fun testSessionDataCopyWithOriginal() {
        val original = SessionData(data = null, status = null, original = null)
        val newOriginal = byteArrayOf(0xA1.toByte())
        val copied = original.copyWith(original = newOriginal)

        assertNotNull(copied.original)
        assertEquals(1, copied.original!!.size)
    }

    @Test
    fun testSessionDataCborBuilder() {
        val sessionData = SessionData(data = null, status = null, original = null)
        val builder = sessionData.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testSessionDataToString() {
        val sessionData = SessionData(data = null, status = null, original = null)
        val str = sessionData.toString()
        assertTrue(str.contains("SessionData"))
    }

    @Test
    fun testSessionDataCompanionLabels() {
        assertEquals("data", SessionData.DATA.value)
        assertEquals("status", SessionData.STATUS.value)
    }

    // SessionDataJson tests

    @Test
    fun testSessionDataJsonCreation() {
        val json = SessionDataJson(data = "test", status = 20L)
        assertEquals("test", json.data)
        assertEquals(20L, json.status)
    }

    @Test
    fun testSessionDataJsonCreationEmpty() {
        val json = SessionDataJson()
        assertNull(json.data)
        assertNull(json.status)
    }

    @Test
    fun testSessionDataJsonToJsonStringThrows() {
        val json = SessionDataJson()
        assertFailsWith<IllegalStateException> {
            json.toJsonString()
        }
    }

    @Test
    fun testSessionDataJsonToCborThrows() {
        val json = SessionDataJson()
        assertFailsWith<IllegalStateException> {
            json.toCbor()
        }
    }

    // SessionEstablishment tests

    @Test
    fun testSessionEstablishmentCompanionLabels() {
        assertEquals("eReaderKey", SessionEstablishment.E_READER_KEY.value)
        assertEquals("data", SessionEstablishment.DATA.value)
    }
}
