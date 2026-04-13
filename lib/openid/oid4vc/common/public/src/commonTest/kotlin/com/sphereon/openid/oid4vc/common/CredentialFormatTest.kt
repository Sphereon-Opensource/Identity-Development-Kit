/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vc.common

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialFormatTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun serializationRoundTripForAllValues() {
        for (format in CredentialFormat.entries) {
            val encoded = json.encodeToString(format)
            val decoded = json.decodeFromString<CredentialFormat>(encoded)
            assertEquals(format, decoded, "Round-trip failed for $format")
        }
    }

    @Test
    fun serializesToCorrectJsonValues() {
        assertEquals("\"dc+sd-jwt\"", json.encodeToString(CredentialFormat.SD_JWT_DC))
        assertEquals("\"vc+sd-jwt\"", json.encodeToString(CredentialFormat.SD_JWT_VC))
        assertEquals("\"mso_mdoc\"", json.encodeToString(CredentialFormat.MSO_MDOC))
        assertEquals("\"jwt_vc_json\"", json.encodeToString(CredentialFormat.JWT_VC_JSON))
        assertEquals("\"jwt_vp_json\"", json.encodeToString(CredentialFormat.JWT_VP_JSON))
    }

    @Test
    fun fromValueExactMatch() {
        assertEquals(CredentialFormat.SD_JWT_DC, CredentialFormat.fromValue("dc+sd-jwt"))
        assertEquals(CredentialFormat.SD_JWT_VC, CredentialFormat.fromValue("vc+sd-jwt"))
        assertEquals(CredentialFormat.MSO_MDOC, CredentialFormat.fromValue("mso_mdoc"))
        assertEquals(CredentialFormat.JWT_VC_JSON, CredentialFormat.fromValue("jwt_vc_json"))
        assertEquals(CredentialFormat.JWT_VP_JSON, CredentialFormat.fromValue("jwt_vp_json"))
        assertNull(CredentialFormat.fromValue("unknown"))
    }

    @Test
    fun fromValueLenientPartialMatches() {
        assertEquals(CredentialFormat.SD_JWT_DC, CredentialFormat.fromValueLenient("sd-jwt"))
        assertEquals(CredentialFormat.SD_JWT_DC, CredentialFormat.fromValueLenient("sd_jwt"))
        assertEquals(CredentialFormat.MSO_MDOC, CredentialFormat.fromValueLenient("mdoc"))
        assertEquals(CredentialFormat.JWT_VC_JSON, CredentialFormat.fromValueLenient("jwt_vc"))
        assertEquals(CredentialFormat.JWT_VP_JSON, CredentialFormat.fromValueLenient("jwt_vp"))
        assertNull(CredentialFormat.fromValueLenient("unknown_format"))
    }

    @Test
    fun detectFormatFromPresentation() {
        // SD-JWT (contains ~)
        assertEquals(CredentialFormat.SD_JWT_DC, CredentialFormat.detectFormat("header.payload.sig~disclosure1~"))
        // JWT (three dot-separated parts)
        assertEquals(CredentialFormat.JWT_VC_JSON, CredentialFormat.detectFormat("eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature"))
        // mDoc (long string, no dots)
        assertEquals(CredentialFormat.MSO_MDOC, CredentialFormat.detectFormat("omdkb2NUeXBlaW9yZy5pc28xODAxMy41LjEubURMdmVyc2lvbjE"))
        // Unknown
        assertNull(CredentialFormat.detectFormat("short"))
    }

    @Test
    fun isSdJwtProperty() {
        assertTrue(CredentialFormat.SD_JWT_DC.isSdJwt)
        assertTrue(CredentialFormat.SD_JWT_VC.isSdJwt)
        assertFalse(CredentialFormat.MSO_MDOC.isSdJwt)
        assertFalse(CredentialFormat.JWT_VC_JSON.isSdJwt)
        assertFalse(CredentialFormat.JWT_VP_JSON.isSdJwt)
    }

    @Test
    fun isJwtProperty() {
        assertFalse(CredentialFormat.SD_JWT_DC.isJwt)
        assertFalse(CredentialFormat.SD_JWT_VC.isJwt)
        assertFalse(CredentialFormat.MSO_MDOC.isJwt)
        assertTrue(CredentialFormat.JWT_VC_JSON.isJwt)
        assertTrue(CredentialFormat.JWT_VP_JSON.isJwt)
    }

    @Test
    fun isMdocProperty() {
        assertFalse(CredentialFormat.SD_JWT_DC.isMdoc)
        assertTrue(CredentialFormat.MSO_MDOC.isMdoc)
    }

    @Test
    fun isKnownFormat() {
        assertTrue(CredentialFormat.isKnownFormat("dc+sd-jwt"))
        assertTrue(CredentialFormat.isKnownFormat("mdoc"))
        assertFalse(CredentialFormat.isKnownFormat("totally_unknown"))
    }

    @Test
    fun extensionFunctionDetectCredentialFormat() {
        val format = "header.payload.sig~disc~".detectCredentialFormat()
        assertNotNull(format)
        assertEquals(CredentialFormat.SD_JWT_DC, format)
    }

    @Test
    fun extensionFunctionMatchesCredentialFormat() {
        assertTrue("dc+sd-jwt".matchesCredentialFormat(CredentialFormat.SD_JWT_DC))
        assertTrue("sd-jwt".matchesCredentialFormat(CredentialFormat.SD_JWT_VC)) // cross-SD-JWT match
        assertFalse("mso_mdoc".matchesCredentialFormat(CredentialFormat.JWT_VC_JSON))
    }
}
