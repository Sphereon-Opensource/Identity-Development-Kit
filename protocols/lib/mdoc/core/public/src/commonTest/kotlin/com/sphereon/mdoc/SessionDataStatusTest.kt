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

package com.sphereon.mdoc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for SessionDataStatus enum.
 */
class SessionDataStatusTest {
    @Test
    fun testErrorSessionEncryptionStatus() {
        val status = SessionDataStatus.ERROR_SESSION_ENCRYPTION
        assertEquals(10L, status.getCode())
        assertEquals("Error: session encryption", status.errorDescription)
        assertEquals("The session shall be terminated.", status.requiredAction)
        assertTrue(status.isError())
    }

    @Test
    fun testErrorCborDecodingStatus() {
        val status = SessionDataStatus.ERROR_CBOR_DECODING
        assertEquals(11L, status.getCode())
        assertEquals("Error: CBOR decoding", status.errorDescription)
        assertEquals("The session shall be terminated.", status.requiredAction)
        assertTrue(status.isError())
    }

    @Test
    fun testSessionTerminationStatus() {
        val status = SessionDataStatus.SESSION_TERMINATION
        assertEquals(20L, status.getCode())
        assertEquals("Session termination", status.errorDescription)
        assertEquals("The session shall be terminated.", status.requiredAction)
        assertFalse(status.isError())
    }

    @Test
    fun testAllStatusValues() {
        assertEquals(3, SessionDataStatus.entries.size)

        val codes = SessionDataStatus.entries.map { it.getCode() }
        assertTrue(codes.contains(10L))
        assertTrue(codes.contains(11L))
        assertTrue(codes.contains(20L))
    }

    @Test
    fun testStatusValueOf() {
        assertEquals(SessionDataStatus.ERROR_SESSION_ENCRYPTION, SessionDataStatus.valueOf("ERROR_SESSION_ENCRYPTION"))
        assertEquals(SessionDataStatus.ERROR_CBOR_DECODING, SessionDataStatus.valueOf("ERROR_CBOR_DECODING"))
        assertEquals(SessionDataStatus.SESSION_TERMINATION, SessionDataStatus.valueOf("SESSION_TERMINATION"))
    }
}
