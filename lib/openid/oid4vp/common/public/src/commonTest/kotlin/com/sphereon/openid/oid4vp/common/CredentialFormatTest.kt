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

import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.openid.oid4vc.common.CredentialFormatDetector
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
        assertEquals(CredentialFormat.SD_JWT_VC, CredentialFormat.fromValue("dc+sd-jwt"))
        assertEquals(CredentialFormat.W3C_VC_SD_JWT, CredentialFormat.fromValue("vc+sd-jwt"))
        assertEquals(CredentialFormat.MSO_MDOC, CredentialFormat.fromValue("mso_mdoc"))
        assertEquals(CredentialFormat.JWT_VC_JSON, CredentialFormat.fromValue("jwt_vc_json"))
        assertEquals(CredentialFormat.JWT_VC_JSON_LD, CredentialFormat.fromValue("jwt_vc_json-ld"))
        assertEquals(CredentialFormat.LDP_VC, CredentialFormat.fromValue("ldp_vc"))
        assertNull(CredentialFormat.fromValue("jwt_vp_json"))
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
        assertEquals(CredentialFormat.SD_JWT_VC, CredentialFormat.fromValueLenient("dc+sd-jwt"))
        assertEquals(CredentialFormat.MSO_MDOC, CredentialFormat.fromValueLenient("mso_mdoc"))
    }

    @Test
    fun `fromValueLenient keeps SD-JWT media types distinct`() {
        assertEquals(CredentialFormat.SD_JWT_VC, CredentialFormat.fromValueLenient("application/dc+sd-jwt"))
        assertEquals(CredentialFormat.W3C_VC_SD_JWT, CredentialFormat.fromValueLenient("application/vc+sd-jwt"))
        assertNull(CredentialFormat.fromValueLenient("sd-jwt"))
        assertNull(CredentialFormat.fromValueLenient("SD-JWT"))
        assertNull(CredentialFormat.fromValueLenient("some-sd-jwt-variant"))
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
        assertEquals(CredentialFormat.JWT_VC_JSON_LD, CredentialFormat.fromValueLenient("jwt_vc_json-ld"))
        assertNull(CredentialFormat.fromValueLenient("jwt_vp"))
        assertNull(CredentialFormat.fromValueLenient("jwt_vp_json"))
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
        assertEquals(CredentialFormat.SD_JWT_VC, CredentialFormatDetector.detect(sdJwt))
    }

    @Test
    fun `detectFormat identifies a strictly classified VCDM 1 credential JWT`() {
        val payload =
            """{"iss":"did:example:issuer","nbf":1700000000,"sub":"did:example:subject","vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential"],"credentialSubject":{"id":"did:example:subject"}}}"""
        val jwt = "$V1_HEADER.${JwsUtils.encodeBytesToBase64Url(payload.encodeToByteArray())}.$SIGNATURE"
        assertEquals(CredentialFormat.JWT_VC_JSON, CredentialFormatDetector.detect(jwt))
    }

    @Test
    fun `detectFormat identifies mDoc by absence of dots and sufficient length`() {
        val mdoc = "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBleBhvcmcuaXNvLjE4MDEzLjUuMS5tRExqaXNzdWVyU2lnbmVk"
        assertEquals(CredentialFormat.MSO_MDOC, CredentialFormatDetector.detect(mdoc))
    }

    @Test
    fun `detectFormat returns null for unrecognizable formats`() {
        assertNull(CredentialFormatDetector.detect(""))
        assertNull(CredentialFormatDetector.detect("short"))
        assertNull(CredentialFormatDetector.detect("ab.cd")) // too short
    }

    // ============================================================================
    // Property Tests
    // ============================================================================

    @Test
    fun `isSdJwt property works correctly`() {
        assertTrue(CredentialFormat.SD_JWT_VC.isSdJwt)
        assertTrue(CredentialFormat.W3C_VC_SD_JWT.isSdJwt)
        assertFalse(CredentialFormat.MSO_MDOC.isSdJwt)
        assertFalse(CredentialFormat.JWT_VC_JSON.isSdJwt)
        assertFalse(CredentialFormat.JWT_VC_JSON_LD.isSdJwt)
        assertFalse(CredentialFormat.LDP_VC.isSdJwt)
    }

    @Test
    fun `isJwt property works correctly`() {
        assertFalse(CredentialFormat.SD_JWT_VC.isJwt)
        assertFalse(CredentialFormat.W3C_VC_SD_JWT.isJwt)
        assertFalse(CredentialFormat.MSO_MDOC.isJwt)
        assertTrue(CredentialFormat.JWT_VC_JSON.isJwt)
        assertTrue(CredentialFormat.JWT_VC_JSON_LD.isJwt)
        assertFalse(CredentialFormat.LDP_VC.isJwt)
    }

    @Test
    fun `isMdoc property works correctly`() {
        assertFalse(CredentialFormat.SD_JWT_VC.isMdoc)
        assertFalse(CredentialFormat.W3C_VC_SD_JWT.isMdoc)
        assertTrue(CredentialFormat.MSO_MDOC.isMdoc)
        assertFalse(CredentialFormat.JWT_VC_JSON.isMdoc)
        assertFalse(CredentialFormat.JWT_VC_JSON_LD.isMdoc)
        assertFalse(CredentialFormat.LDP_VC.isMdoc)
    }

    @Test
    fun `value property returns correct string`() {
        assertEquals("dc+sd-jwt", CredentialFormat.SD_JWT_VC.value)
        assertEquals("vc+sd-jwt", CredentialFormat.W3C_VC_SD_JWT.value)
        assertEquals("mso_mdoc", CredentialFormat.MSO_MDOC.value)
        assertEquals("jwt_vc_json", CredentialFormat.JWT_VC_JSON.value)
        assertEquals("jwt_vc_json-ld", CredentialFormat.JWT_VC_JSON_LD.value)
        assertEquals("ldp_vc", CredentialFormat.LDP_VC.value)
    }

    // ============================================================================
    // Extension Function Tests
    // ============================================================================

    @Test
    fun `detectCredentialFormat extension works`() {
        val sdJwt = "header.payload.sig~disc~kb"
        assertEquals(CredentialFormat.SD_JWT_VC, sdJwt.detectCredentialFormat())
    }

    @Test
    fun `matchesCredentialFormat extension works`() {
        assertTrue("dc+sd-jwt".matchesCredentialFormat(CredentialFormat.SD_JWT_VC))
        assertFalse("sd-jwt".matchesCredentialFormat(CredentialFormat.SD_JWT_VC))
        assertTrue("vc+sd-jwt".matchesCredentialFormat(CredentialFormat.W3C_VC_SD_JWT))
        assertFalse("dc+sd-jwt".matchesCredentialFormat(CredentialFormat.W3C_VC_SD_JWT))
        assertFalse("vc+sd-jwt".matchesCredentialFormat(CredentialFormat.SD_JWT_VC))
        assertFalse("mso_mdoc".matchesCredentialFormat(CredentialFormat.SD_JWT_VC))
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
        assertEquals("\"dc+sd-jwt\"", json.encodeToString(CredentialFormat.SD_JWT_VC))
        assertEquals("\"vc+sd-jwt\"", json.encodeToString(CredentialFormat.W3C_VC_SD_JWT))
        assertEquals("\"mso_mdoc\"", json.encodeToString(CredentialFormat.MSO_MDOC))
        assertEquals("\"jwt_vc_json\"", json.encodeToString(CredentialFormat.JWT_VC_JSON))
        assertEquals("\"jwt_vc_json-ld\"", json.encodeToString(CredentialFormat.JWT_VC_JSON_LD))
        assertEquals("\"ldp_vc\"", json.encodeToString(CredentialFormat.LDP_VC))
    }

    @Test
    fun `deserialization from JSON string works through typealias`() {
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(CredentialFormat.SD_JWT_VC, json.decodeFromString<CredentialFormat>("\"dc+sd-jwt\""))
        assertEquals(CredentialFormat.MSO_MDOC, json.decodeFromString<CredentialFormat>("\"mso_mdoc\""))
    }

    private companion object {
        const val HEADER = "eyJhbGciOiJFZERTQSIsInR5cCI6InZjK2p3dCJ9"
        const val V1_HEADER = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9"
        const val SIGNATURE = "AQID"
    }
}
