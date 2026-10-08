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
 */

package com.sphereon.trust.etsi.signature.jades

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for JAdES model parsing.
 */
class JAdESModelTest {
    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Test
    fun parseJAdESProtectedHeaders() {
        val json =
            """
            {
                "alg": "ES256",
                "sigT": "2024-06-15T12:00:00Z",
                "x5c": ["MIIB...cert1...", "MIIB...cert2..."],
                "x5t#S256": "abc123",
                "crit": ["sigT"]
            }
            """.trimIndent()

        val headers = lenientJson.decodeFromString<JAdESProtectedHeaders>(json)

        assertEquals("2024-06-15T12:00:00Z", headers.sigT)
        assertNotNull(headers.x5c)
        assertEquals(2, headers.x5c!!.size)
        assertEquals("abc123", headers.x5tS256)
        assertNotNull(headers.crit)
        assertEquals(1, headers.crit!!.size)
        assertEquals("sigT", headers.crit!![0])
    }

    @Test
    fun parseSigningTime() {
        val headers = JAdESProtectedHeaders(sigT = "2024-06-15T12:00:00Z")
        val instant = headers.getSigningTime()
        assertNotNull(instant)
        assertEquals("2024-06-15T12:00:00Z", instant.toString())
    }

    @Test
    fun parseInvalidSigningTimeReturnsNull() {
        val headers = JAdESProtectedHeaders(sigT = "not-a-date")
        val instant = headers.getSigningTime()
        assertNull(instant)
    }

    @Test
    fun parseSignedDataReference() {
        val json =
            """
            {
                "sigD": {
                    "mId": "http://uri.etsi.org/19182/HttpHeaders",
                    "pars": ["http://example.com/doc1"],
                    "hashM": "http://www.w3.org/2001/04/xmlenc#sha256",
                    "hashV": ["dGVzdA"],
                    "ctys": ["application/json"]
                }
            }
            """.trimIndent()

        val headers = Json.decodeFromString<JAdESProtectedHeaders>(json)

        assertNotNull(headers.sigD)
        assertEquals("http://uri.etsi.org/19182/HttpHeaders", headers.sigD!!.mId)
        assertEquals(1, headers.sigD!!.pars?.size)
        assertEquals("http://example.com/doc1", headers.sigD!!.pars?.first())
    }

    @Test
    fun emptyHeadersAreHandled() {
        val headers = JAdESProtectedHeaders()

        assertNull(headers.sigT)
        assertNull(headers.x5c)
        assertNull(headers.sigD)
        assertNull(headers.crit)
        assertNull(headers.getSigningTime())
    }
}
