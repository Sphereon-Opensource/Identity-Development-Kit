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

import com.sphereon.crypto.jose.jws.JwsUtils
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
        assertEquals("\"dc+sd-jwt\"", json.encodeToString(CredentialFormat.SD_JWT_VC))
        assertEquals("\"vc+sd-jwt\"", json.encodeToString(CredentialFormat.W3C_VC_SD_JWT))
        assertEquals("\"mso_mdoc\"", json.encodeToString(CredentialFormat.MSO_MDOC))
        assertEquals("\"jwt_vc_json\"", json.encodeToString(CredentialFormat.JWT_VC_JSON))
        assertEquals("\"jwt_vc_json-ld\"", json.encodeToString(CredentialFormat.JWT_VC_JSON_LD))
        assertEquals("\"ldp_vc\"", json.encodeToString(CredentialFormat.LDP_VC))
    }

    @Test
    fun fromValueExactMatch() {
        assertEquals(CredentialFormat.SD_JWT_VC, CredentialFormat.fromValue("dc+sd-jwt"))
        assertEquals(CredentialFormat.W3C_VC_SD_JWT, CredentialFormat.fromValue("vc+sd-jwt"))
        assertEquals(CredentialFormat.MSO_MDOC, CredentialFormat.fromValue("mso_mdoc"))
        assertEquals(CredentialFormat.JWT_VC_JSON, CredentialFormat.fromValue("jwt_vc_json"))
        assertEquals(CredentialFormat.JWT_VC_JSON_LD, CredentialFormat.fromValue("jwt_vc_json-ld"))
        assertEquals(CredentialFormat.LDP_VC, CredentialFormat.fromValue("ldp_vc"))
        assertNull(CredentialFormat.fromValue("jwt_vp_json"))
        assertNull(CredentialFormat.fromValue("unknown"))
    }

    @Test
    fun fromValueLenientKeepsSdJwtMediaTypesDistinct() {
        assertEquals(CredentialFormat.SD_JWT_VC, CredentialFormat.fromValueLenient("application/dc+sd-jwt"))
        assertEquals(CredentialFormat.W3C_VC_SD_JWT, CredentialFormat.fromValueLenient("application/vc+sd-jwt"))
        assertNull(CredentialFormat.fromValueLenient("sd-jwt"))
        assertNull(CredentialFormat.fromValueLenient("sd_jwt"))
        assertEquals(CredentialFormat.MSO_MDOC, CredentialFormat.fromValueLenient("mdoc"))
        assertEquals(CredentialFormat.JWT_VC_JSON, CredentialFormat.fromValueLenient("jwt_vc"))
        assertNull(CredentialFormat.fromValueLenient("jwt_vp"))
        assertNull(CredentialFormat.fromValueLenient("jwt_vp_json"))
        assertEquals(CredentialFormat.JWT_VC_JSON_LD, CredentialFormat.fromValueLenient("jwt_vc_json-ld"))
        assertEquals(CredentialFormat.LDP_VC, CredentialFormat.fromValueLenient("ldp_vc"))
        assertNull(CredentialFormat.fromValueLenient("vc+ld+json+jwt"))
        assertNull(CredentialFormat.fromValueLenient("ldp_vp"))
        assertNull(CredentialFormat.fromValueLenient("unknown_format"))
    }

    @Test
    fun credentialFormatDetectorDoesNotClassifyPresentationsAsCredentials() {
        // SD-JWT (contains ~)
        assertEquals(CredentialFormat.SD_JWT_VC, CredentialFormatDetector.detect("header.payload.sig~disclosure1~"))
        // Compact JWS formats require a strict VCDM classification; three dots alone are not enough.
        assertEquals(CredentialFormat.JWT_VC_JSON, CredentialFormatDetector.detect(v1Credential()))
        assertNull(CredentialFormatDetector.detect(v1Presentation()))
        assertEquals(CredentialFormat.JWT_VC_JSON_LD, CredentialFormatDetector.detect(v2Credential()))
        assertNull(CredentialFormatDetector.detect(v2Presentation()))
        // mDoc (long string, no dots)
        assertEquals(CredentialFormat.MSO_MDOC, CredentialFormatDetector.detect("omdkb2NUeXBlaW9yZy5pc28xODAxMy41LjEubURMdmVyc2lvbjE"))
    }

    @Test
    fun detectFormatFailsClosedForMalformedAmbiguousAndContradictoryCompactJws() {
        assertNull(CredentialFormatDetector.detect("header.payload.signature"))
        assertNull(CredentialFormatDetector.detect("$HEADER..$SIGNATURE"))
        assertNull(CredentialFormatDetector.detect("$NONE_HEADER.${JwsUtils.encodeBytesToBase64Url(v2CredentialPayload().encodeToByteArray())}.$SIGNATURE"))

        assertNull(
            CredentialFormatDetector.detect(
                compact(
                    """{"@context":["https://www.w3.org/2018/credentials/v1","https://www.w3.org/ns/credentials/v2"],"type":["VerifiableCredential"]}""",
                ),
            ),
        )
        assertNull(
            CredentialFormatDetector.detect(
                compact(
                    """{"@context":"https://www.w3.org/ns/credentials/v2","type":["VerifiableCredential"],"vc":{}}""",
                ),
            ),
        )
    }

    @Test
    fun isSdJwtProperty() {
        assertTrue(CredentialFormat.SD_JWT_VC.isSdJwt)
        assertTrue(CredentialFormat.W3C_VC_SD_JWT.isSdJwt)
        assertFalse(CredentialFormat.MSO_MDOC.isSdJwt)
        assertFalse(CredentialFormat.JWT_VC_JSON.isSdJwt)
        assertFalse(CredentialFormat.JWT_VC_JSON_LD.isSdJwt)
        assertFalse(CredentialFormat.LDP_VC.isSdJwt)
    }

    @Test
    fun isJwtProperty() {
        assertFalse(CredentialFormat.SD_JWT_VC.isJwt)
        assertFalse(CredentialFormat.W3C_VC_SD_JWT.isJwt)
        assertFalse(CredentialFormat.MSO_MDOC.isJwt)
        assertTrue(CredentialFormat.JWT_VC_JSON.isJwt)
        assertTrue(CredentialFormat.JWT_VC_JSON_LD.isJwt)
        assertFalse(CredentialFormat.LDP_VC.isJwt)
    }

    @Test
    fun compactJwsAndJwtRolePropertiesAreExplicit() {
        assertTrue(CredentialFormat.JWT_VC_JSON.isCompactJws)
        assertTrue(CredentialFormat.JWT_VC_JSON_LD.isCompactJws)
        assertFalse(CredentialFormat.SD_JWT_VC.isCompactJws)
        assertFalse(CredentialFormat.LDP_VC.isCompactJws)

        assertTrue(CredentialFormat.JWT_VC_JSON.isJwtVc)
        assertTrue(CredentialFormat.JWT_VC_JSON_LD.isJwtVc)
        assertFalse(CredentialFormat.LDP_VC.isJwtVc)
    }

    @Test
    fun isMdocProperty() {
        assertFalse(CredentialFormat.SD_JWT_VC.isMdoc)
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
        assertEquals(CredentialFormat.SD_JWT_VC, format)
    }

    @Test
    fun extensionFunctionMatchesCredentialFormat() {
        assertTrue("dc+sd-jwt".matchesCredentialFormat(CredentialFormat.SD_JWT_VC))
        assertTrue("vc+sd-jwt".matchesCredentialFormat(CredentialFormat.W3C_VC_SD_JWT))
        assertFalse("dc+sd-jwt".matchesCredentialFormat(CredentialFormat.W3C_VC_SD_JWT))
        assertFalse("vc+sd-jwt".matchesCredentialFormat(CredentialFormat.SD_JWT_VC))
        assertFalse("mso_mdoc".matchesCredentialFormat(CredentialFormat.JWT_VC_JSON))
    }

    private fun v1Credential(): String =
        compact(
            """{"iss":"did:example:issuer","nbf":1700000000,"sub":"did:example:subject","vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential"],"credentialSubject":{"id":"did:example:subject"}}}""",
            V1_HEADER,
        )

    private fun v1Presentation(): String =
        compact(
            """{"iss":"did:example:holder","aud":"https://verifier.example","vp":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiablePresentation"],"verifiableCredential":["urn:example:credential"]}}""",
            V1_HEADER,
        )

    private fun v2Credential(): String =
        compact(v2CredentialPayload())

    private fun v2CredentialPayload(): String =
        """{"@context":["https://www.w3.org/ns/credentials/v2"],"type":["VerifiableCredential"],"credentialSubject":{"id":"did:example:subject"}}"""

    private fun v2Presentation(): String =
        compact(
            """{"@context":["https://www.w3.org/ns/credentials/v2"],"type":["VerifiablePresentation"],"verifiableCredential":["urn:example:credential"]}""",
            V2_PRESENTATION_HEADER,
        )

    private fun compact(payload: String, header: String = HEADER): String = "$header.${JwsUtils.encodeBytesToBase64Url(payload.encodeToByteArray())}.$SIGNATURE"

    private companion object {
        const val HEADER = "eyJhbGciOiJFZERTQSIsInR5cCI6InZjK2p3dCJ9"
        const val V1_HEADER = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9"
        const val V2_PRESENTATION_HEADER = "eyJhbGciOiJFZERTQSIsInR5cCI6InZwK2p3dCJ9"
        const val NONE_HEADER = "eyJhbGciOiJub25lIiwidHlwIjoidmMrand0In0"
        const val SIGNATURE = "AQID"
    }
}
