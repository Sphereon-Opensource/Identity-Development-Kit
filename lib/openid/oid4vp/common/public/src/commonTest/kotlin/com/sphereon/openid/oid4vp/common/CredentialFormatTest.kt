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

package com.sphereon.openid.oid4vp.common

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for CredentialFormat enum
 */
class CredentialFormatTest {
    // ============================================================================
    // fromValue Tests
    // ============================================================================

    @Test
    fun `fromValue returns correct enum for exact match`() {
        assertEquals(CredentialFormat.SD_JWT_DC, CredentialFormat.fromValue("dc+sd-jwt"))
        assertEquals(CredentialFormat.SD_JWT_VC, CredentialFormat.fromValue("vc+sd-jwt"))
        assertEquals(CredentialFormat.MSO_MDOC, CredentialFormat.fromValue("mso_mdoc"))
        assertEquals(CredentialFormat.JWT_VC_JSON, CredentialFormat.fromValue("jwt_vc_json"))
        assertEquals(CredentialFormat.JWT_VP_JSON, CredentialFormat.fromValue("jwt_vp_json"))
    }

    @Test
    fun `fromValue returns null for unknown format`() {
        assertNull(CredentialFormat.fromValue("unknown"))
        assertNull(CredentialFormat.fromValue(""))
        assertNull(CredentialFormat.fromValue("sd-jwt")) // partial match should not work
    }

    // ============================================================================
    // fromValueLenient Tests
    // ============================================================================

    @Test
    fun `fromValueLenient returns correct enum for exact match`() {
        assertEquals(CredentialFormat.SD_JWT_DC, CredentialFormat.fromValueLenient("dc+sd-jwt"))
        assertEquals(CredentialFormat.MSO_MDOC, CredentialFormat.fromValueLenient("mso_mdoc"))
    }

    @Test
    fun `fromValueLenient handles partial SD-JWT matches`() {
        assertEquals(CredentialFormat.SD_JWT_DC, CredentialFormat.fromValueLenient("sd-jwt"))
        assertEquals(CredentialFormat.SD_JWT_DC, CredentialFormat.fromValueLenient("SD-JWT"))
        assertEquals(CredentialFormat.SD_JWT_DC, CredentialFormat.fromValueLenient("some-sd-jwt-variant"))
    }

    @Test
    fun `fromValueLenient handles partial mDoc matches`() {
        assertEquals(CredentialFormat.MSO_MDOC, CredentialFormat.fromValueLenient("mdoc"))
        assertEquals(CredentialFormat.MSO_MDOC, CredentialFormat.fromValueLenient("MDOC"))
        assertEquals(CredentialFormat.MSO_MDOC, CredentialFormat.fromValueLenient("some_mdoc_variant"))
    }

    @Test
    fun `fromValueLenient handles JWT matches`() {
        assertEquals(CredentialFormat.JWT_VC_JSON, CredentialFormat.fromValueLenient("jwt_vc"))
        assertEquals(CredentialFormat.JWT_VP_JSON, CredentialFormat.fromValueLenient("jwt_vp"))
    }

    @Test
    fun `fromValueLenient returns null for unknown format`() {
        assertNull(CredentialFormat.fromValueLenient("unknown"))
        assertNull(CredentialFormat.fromValueLenient(""))
    }

    // ============================================================================
    // detectFormat Tests
    // ============================================================================

    @Test
    fun `detectFormat identifies SD-JWT by tilde separator`() {
        val sdJwt = "eyJhbGciOiJFUzI1NiJ9.payload.signature~disclosure1~disclosure2~kbjwt"
        assertEquals(CredentialFormat.SD_JWT_DC, CredentialFormat.detectFormat(sdJwt))
    }

    @Test
    fun `detectFormat identifies JWT by three-part structure`() {
        val jwt = "eyJhbGciOiJFUzI1NiJ9.payload.signature"
        assertEquals(CredentialFormat.JWT_VC_JSON, CredentialFormat.detectFormat(jwt))
    }

    @Test
    fun `detectFormat identifies mDoc by absence of dots and sufficient length`() {
        val mdoc = "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBleBhvcmcuaXNvLjE4MDEzLjUuMS5tRExqaXNzdWVyU2lnbmVk"
        assertEquals(CredentialFormat.MSO_MDOC, CredentialFormat.detectFormat(mdoc))
    }

    @Test
    fun `detectFormat returns null for unrecognizable formats`() {
        assertNull(CredentialFormat.detectFormat(""))
        assertNull(CredentialFormat.detectFormat("short"))
        assertNull(CredentialFormat.detectFormat("ab.cd")) // too short
    }

    // ============================================================================
    // Property Tests
    // ============================================================================

    @Test
    fun `isSdJwt property works correctly`() {
        assertTrue(CredentialFormat.SD_JWT_DC.isSdJwt)
        assertTrue(CredentialFormat.SD_JWT_VC.isSdJwt)
        assertFalse(CredentialFormat.MSO_MDOC.isSdJwt)
        assertFalse(CredentialFormat.JWT_VC_JSON.isSdJwt)
        assertFalse(CredentialFormat.JWT_VP_JSON.isSdJwt)
    }

    @Test
    fun `isJwt property works correctly`() {
        assertFalse(CredentialFormat.SD_JWT_DC.isJwt)
        assertFalse(CredentialFormat.SD_JWT_VC.isJwt)
        assertFalse(CredentialFormat.MSO_MDOC.isJwt)
        assertTrue(CredentialFormat.JWT_VC_JSON.isJwt)
        assertTrue(CredentialFormat.JWT_VP_JSON.isJwt)
    }

    @Test
    fun `isMdoc property works correctly`() {
        assertFalse(CredentialFormat.SD_JWT_DC.isMdoc)
        assertFalse(CredentialFormat.SD_JWT_VC.isMdoc)
        assertTrue(CredentialFormat.MSO_MDOC.isMdoc)
        assertFalse(CredentialFormat.JWT_VC_JSON.isMdoc)
        assertFalse(CredentialFormat.JWT_VP_JSON.isMdoc)
    }

    @Test
    fun `value property returns correct string`() {
        assertEquals("dc+sd-jwt", CredentialFormat.SD_JWT_DC.value)
        assertEquals("vc+sd-jwt", CredentialFormat.SD_JWT_VC.value)
        assertEquals("mso_mdoc", CredentialFormat.MSO_MDOC.value)
        assertEquals("jwt_vc_json", CredentialFormat.JWT_VC_JSON.value)
        assertEquals("jwt_vp_json", CredentialFormat.JWT_VP_JSON.value)
    }

    // ============================================================================
    // Extension Function Tests
    // ============================================================================

    @Test
    fun `detectCredentialFormat extension works`() {
        val sdJwt = "header.payload.sig~disc~kb"
        assertEquals(CredentialFormat.SD_JWT_DC, sdJwt.detectCredentialFormat())
    }

    @Test
    fun `matchesCredentialFormat extension works`() {
        assertTrue("dc+sd-jwt".matchesCredentialFormat(CredentialFormat.SD_JWT_DC))
        assertTrue("sd-jwt".matchesCredentialFormat(CredentialFormat.SD_JWT_DC))
        assertFalse("mso_mdoc".matchesCredentialFormat(CredentialFormat.SD_JWT_DC))
    }

    // ============================================================================
    // Serialization Migration Tests (typealias round-trip)
    // ============================================================================

    @Test
    fun `serialization round trip through typealias preserves wire format`() {
        val json = Json { ignoreUnknownKeys = true }
        for (format in CredentialFormat.entries) {
            val encoded = json.encodeToString(format)
            val decoded = json.decodeFromString<CredentialFormat>(encoded)
            assertEquals(format, decoded, "Round-trip failed for $format via OID4VP typealias")
        }
    }

    @Test
    fun `serialization produces correct JSON strings through typealias`() {
        val json = Json { ignoreUnknownKeys = true }
        assertEquals("\"dc+sd-jwt\"", json.encodeToString(CredentialFormat.SD_JWT_DC))
        assertEquals("\"vc+sd-jwt\"", json.encodeToString(CredentialFormat.SD_JWT_VC))
        assertEquals("\"mso_mdoc\"", json.encodeToString(CredentialFormat.MSO_MDOC))
        assertEquals("\"jwt_vc_json\"", json.encodeToString(CredentialFormat.JWT_VC_JSON))
        assertEquals("\"jwt_vp_json\"", json.encodeToString(CredentialFormat.JWT_VP_JSON))
    }

    @Test
    fun `deserialization from JSON string works through typealias`() {
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(CredentialFormat.SD_JWT_DC, json.decodeFromString<CredentialFormat>("\"dc+sd-jwt\""))
        assertEquals(CredentialFormat.MSO_MDOC, json.decodeFromString<CredentialFormat>("\"mso_mdoc\""))
    }
}
