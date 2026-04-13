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

package com.sphereon.openid.oid4vci.common.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialRequestSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun requestWithCredentialConfigurationIdRoundTrip() {
        val request =
            CredentialRequest(
                credentialConfigurationId = "UniversityDegreeCredential",
                format = "vc+sd-jwt",
                vct = "https://credentials.example.com/identity_credential",
            )

        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<CredentialRequest>(encoded)

        assertEquals(request, decoded)
        assertEquals("UniversityDegreeCredential", decoded.credentialConfigurationId)
        assertEquals("vc+sd-jwt", decoded.format)
        assertEquals("https://credentials.example.com/identity_credential", decoded.vct)
        assertNull(decoded.credentialIdentifier)
        assertNull(decoded.proofs)
        assertNull(decoded.doctype)
        assertNull(decoded.credentialResponseEncryption)
        assertTrue(decoded.additionalParameters.isEmpty())
    }

    @Test
    fun requestWithCredentialIdentifierRoundTrip() {
        val request =
            CredentialRequest(
                credentialIdentifier = "UniversityDegree_LDP_VC",
            )

        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<CredentialRequest>(encoded)

        assertEquals(request, decoded)
        assertEquals("UniversityDegree_LDP_VC", decoded.credentialIdentifier)
        assertNull(decoded.credentialConfigurationId)
        assertNull(decoded.format)
    }

    @Test
    fun requestWithJwtProofsRoundTrip() {
        val request =
            CredentialRequest(
                credentialConfigurationId = "UniversityDegreeCredential",
                proofs =
                    CredentialRequestProofs(
                        proofType = "jwt",
                        proofValues =
                            listOf(
                                JsonPrimitive(
                                    "eyJraWQiOiJkaWQ6ZXhhbXBsZTplYmZlYjFmNzEyZWJjNmYxYzI3NmUxMmVjMjEiLCJhbGciOiJFUzI1NiIsInR5cCI6Im9wZW5pZDR2Y2ktcHJvb2Yrand0In0.eyJpc3MiOiJzNkJoZFJrcXQzIiwiYXVkIjoiaHR0cHM6Ly9zZXJ2ZXIuZXhhbXBsZS5jb20iLCJpYXQiOjE2NTkxNDU5MjQsIm5vbmNlIjoiYTFiMmMzZDRlNWY2In0.signature",
                                ),
                            ),
                    ),
            )

        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<CredentialRequest>(encoded)

        assertEquals(request, decoded)
        assertNotNull(decoded.proofs)
        assertEquals("jwt", decoded.proofs?.proofType)
        assertEquals(1, decoded.proofs?.proofValues?.size)
    }

    @Test
    fun requestWithBatchProofsRoundTrip() {
        val request =
            CredentialRequest(
                credentialConfigurationId = "EmployeeIDCredential",
                proofs =
                    CredentialRequestProofs(
                        proofType = "jwt",
                        proofValues =
                            listOf(
                                JsonPrimitive("eyJ0eXAiOiJvcGVuaWQ0dmNpLXByb29mK2p3dCJ9.eyJub25jZSI6Im5vbmNlMSJ9.sig1"),
                                JsonPrimitive("eyJ0eXAiOiJvcGVuaWQ0dmNpLXByb29mK2p3dCJ9.eyJub25jZSI6Im5vbmNlMiJ9.sig2"),
                            ),
                    ),
            )

        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<CredentialRequest>(encoded)

        assertEquals(request, decoded)
        assertNotNull(decoded.proofs)
        assertEquals("jwt", decoded.proofs?.proofType)
        assertEquals(2, decoded.proofs?.proofValues?.size)
    }

    @Test
    fun requestWithEncryptionRoundTrip() {
        val jwk =
            buildJsonObject {
                put("kty", "EC")
                put("crv", "P-256")
                put("x", "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU")
                put("y", "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0")
            }

        val request =
            CredentialRequest(
                credentialConfigurationId = "VerifiableCredential",
                credentialResponseEncryption =
                    RequestedCredentialResponseEncryption(
                        jwk = jwk,
                        alg = "ECDH-ES",
                        enc = "A256GCM",
                    ),
            )

        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<CredentialRequest>(encoded)

        assertEquals(request, decoded)
        assertNotNull(decoded.credentialResponseEncryption)
        assertEquals("ECDH-ES", decoded.credentialResponseEncryption?.alg)
        assertEquals("A256GCM", decoded.credentialResponseEncryption?.enc)
        assertEquals(
            "EC",
            decoded.credentialResponseEncryption
                ?.jwk
                ?.get("kty")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun unknownFieldsCapturedInAdditionalParameters() {
        val jsonString =
            """
            {
                "credential_configuration_id": "UniversityDegreeCredential",
                "format": "vc+sd-jwt",
                "vct": "https://credentials.example.com/identity_credential",
                "vendor_extension": "some_value"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialRequest>(jsonString)

        assertEquals("UniversityDegreeCredential", decoded.credentialConfigurationId)
        assertEquals("vc+sd-jwt", decoded.format)
        assertEquals(1, decoded.additionalParameters.size)
        assertEquals("some_value", decoded.additionalParameters["vendor_extension"]?.jsonPrimitive?.content)

        val reEncoded = json.encodeToString(decoded)
        val reDecoded = json.decodeFromString<CredentialRequest>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun credentialRequestWithProofsWireFormat() {
        // OID4VCI 1.1 wire format: proofs is {"jwt": ["eyJ...", "eyJ..."]}
        // There must be NO proof_type key inside the proofs object
        val jsonString =
            """
            {
                "credential_configuration_id": "EmployeeIDCredential",
                "proofs": {
                    "jwt": [
                        "eyJ0eXAiOiJvcGVuaWQ0dmNpLXByb29mK2p3dCJ9.eyJub25jZSI6Im5vbmNlMSJ9.sig1",
                        "eyJ0eXAiOiJvcGVuaWQ0dmNpLXByb29mK2p3dCJ9.eyJub25jZSI6Im5vbmNlMiJ9.sig2"
                    ]
                }
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialRequest>(jsonString)

        assertNotNull(decoded.proofs)
        assertEquals("jwt", decoded.proofs?.proofType)
        assertEquals(2, decoded.proofs?.proofValues?.size)

        // Re-serialize and verify wire format
        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        val proofsObj = reObj["proofs"]!!.jsonObject

        // CRITICAL: proofs object must NOT contain proof_type key
        assertFalse(proofsObj.containsKey("proof_type"), "1.1 proofs wire format must NOT contain 'proof_type' key")

        // Must contain the proof type as the key with array value
        assertTrue(proofsObj.containsKey("jwt"), "proofs object must have proof type as key")
        val jwtArray = proofsObj["jwt"]!!.jsonArray
        assertEquals(2, jwtArray.size)
        assertTrue(jwtArray[0].jsonPrimitive.content.startsWith("eyJ"))
        assertTrue(jwtArray[1].jsonPrimitive.content.startsWith("eyJ"))

        // Round-trip equality
        val reDecoded = json.decodeFromString<CredentialRequest>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun credentialResponseEncryptionWithAlgVersion10RoundTrip() {
        // 1.0 style: alg is present
        val jsonString =
            """
            {
                "credential_configuration_id": "VerifiableCredential",
                "credential_response_encryption": {
                    "jwk": {
                        "kty": "EC",
                        "crv": "P-256",
                        "x": "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
                        "y": "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0"
                    },
                    "alg": "ECDH-ES",
                    "enc": "A256GCM"
                }
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialRequest>(jsonString)

        assertNotNull(decoded.credentialResponseEncryption)
        assertEquals("ECDH-ES", decoded.credentialResponseEncryption?.alg)
        assertEquals("A256GCM", decoded.credentialResponseEncryption?.enc)
        assertNull(decoded.credentialResponseEncryption?.zip)

        // Re-serialize and verify alg is present
        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        val encObj = reObj["credential_response_encryption"]!!.jsonObject
        assertEquals("ECDH-ES", encObj["alg"]?.jsonPrimitive?.content)
        assertEquals("A256GCM", encObj["enc"]?.jsonPrimitive?.content)
        assertFalse(encObj.containsKey("zip"), "1.0 encryption should not have zip")

        val reDecoded = json.decodeFromString<CredentialRequest>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun credentialResponseEncryptionWithZipVersion11RoundTrip() {
        // 1.1 style: alg is absent (determined from JWK), zip is present
        val jsonString =
            """
            {
                "credential_configuration_id": "VerifiableCredential",
                "credential_response_encryption": {
                    "jwk": {
                        "kty": "EC",
                        "crv": "P-256",
                        "x": "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
                        "y": "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0"
                    },
                    "enc": "A128CBC-HS256",
                    "zip": "DEF"
                }
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialRequest>(jsonString)

        assertNotNull(decoded.credentialResponseEncryption)
        assertNull(decoded.credentialResponseEncryption?.alg, "1.1 encryption may omit alg")
        assertEquals("A128CBC-HS256", decoded.credentialResponseEncryption?.enc)
        assertEquals("DEF", decoded.credentialResponseEncryption?.zip)

        // Re-serialize and verify zip is present, alg is absent
        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        val encObj = reObj["credential_response_encryption"]!!.jsonObject
        assertFalse(encObj.containsKey("alg"), "alg should be absent when not set")
        assertEquals("A128CBC-HS256", encObj["enc"]?.jsonPrimitive?.content)
        assertEquals("DEF", encObj["zip"]?.jsonPrimitive?.content)

        val reDecoded = json.decodeFromString<CredentialRequest>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun credentialConfigurationIdIsFirstClassNotExtension() {
        val jsonString =
            """
            {
                "credential_configuration_id": "UniversityDegreeCredential"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialRequest>(jsonString)

        assertEquals("UniversityDegreeCredential", decoded.credentialConfigurationId)
        assertTrue(decoded.additionalParameters.isEmpty(), "credential_configuration_id should NOT be in additionalParameters")
    }
}
