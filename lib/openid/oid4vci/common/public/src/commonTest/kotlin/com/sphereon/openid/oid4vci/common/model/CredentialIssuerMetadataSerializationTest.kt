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

import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vc.common.LogoProperties
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialIssuerMetadataSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun minimalMetadataRoundTrip() {
        val metadata =
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example.com",
                credentialEndpoint = "https://issuer.example.com/credential",
                credentialConfigurationsSupported =
                    mapOf(
                        "TestCredential" to
                            CredentialConfigurationSupported(
                                format = "jwt_vc_json",
                            ),
                    ),
            )

        val encoded = json.encodeToString(metadata)
        val decoded = json.decodeFromString<CredentialIssuerMetadata>(encoded)

        assertEquals(metadata, decoded)
        assertEquals("https://issuer.example.com", decoded.credentialIssuer)
        assertEquals("https://issuer.example.com/credential", decoded.credentialEndpoint)
        assertEquals(1, decoded.credentialConfigurationsSupported.size)
        assertEquals("jwt_vc_json", decoded.credentialConfigurationsSupported["TestCredential"]?.format)
        assertNull(decoded.authorizationServers)
        assertNull(decoded.batchCredentialEndpoint)
        assertNull(decoded.deferredCredentialEndpoint)
        assertNull(decoded.notificationEndpoint)
        assertNull(decoded.nonceEndpoint)
        assertNull(decoded.signedMetadata)
        assertNull(decoded.display)
        assertTrue(decoded.additionalMetadata.isEmpty())
    }

    @Test
    fun fullMetadataRoundTrip() {
        val metadata =
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example.com",
                authorizationServers = listOf("https://auth.example.com", "https://auth2.example.com"),
                credentialEndpoint = "https://issuer.example.com/credential",
                batchCredentialEndpoint = "https://issuer.example.com/batch_credential",
                deferredCredentialEndpoint = "https://issuer.example.com/deferred_credential",
                notificationEndpoint = "https://issuer.example.com/notification",
                nonceEndpoint = "https://issuer.example.com/nonce",
                credentialConfigurationsSupported =
                    mapOf(
                        "UniversityDegree" to
                            CredentialConfigurationSupported(
                                format = "vc+sd-jwt",
                                scope = "UniversityDegree",
                                vct = "https://credentials.example.com/university_degree",
                                cryptographicBindingMethodsSupported = listOf("did:example", "did:key"),
                                credentialSigningAlgValuesSupported = listOf("ES256"),
                                proofTypesSupported =
                                    mapOf(
                                        "jwt" to ProofTypeSupported(proofSigningAlgValuesSupported = listOf("ES256")),
                                    ),
                                display =
                                    listOf(
                                        DisplayProperties(
                                            name = "University Degree",
                                            locale = "en-US",
                                            logo =
                                                LogoProperties(
                                                    uri = "https://university.example.edu/logo.png",
                                                    altText = "University Logo",
                                                ),
                                            backgroundColor = "#12107c",
                                            textColor = "#FFFFFF",
                                        ),
                                    ),
                            ),
                        "EmployeeID" to
                            CredentialConfigurationSupported(
                                format = "jwt_vc_json",
                                scope = "EmployeeID",
                                credentialDefinition =
                                    CredentialDefinition(
                                        type = listOf("VerifiableCredential", "EmployeeIDCredential"),
                                    ),
                            ),
                    ),
                signedMetadata = "eyJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJodHRwczovL2lzc3Vlci5leGFtcGxlLmNvbSJ9.signature",
                display =
                    listOf(
                        DisplayProperties(
                            name = "Example Issuer",
                            locale = "en-US",
                            logo =
                                LogoProperties(
                                    uri = "https://issuer.example.com/logo.png",
                                    altText = "Issuer Logo",
                                ),
                        ),
                    ),
            )

        val encoded = json.encodeToString(metadata)
        val decoded = json.decodeFromString<CredentialIssuerMetadata>(encoded)

        assertEquals(metadata, decoded)
        assertEquals(2, decoded.authorizationServers?.size)
        assertEquals("https://auth.example.com", decoded.authorizationServers?.get(0))
        assertEquals(2, decoded.credentialConfigurationsSupported.size)
        assertNotNull(decoded.signedMetadata)
        assertEquals(1, decoded.display?.size)
    }

    @Test
    fun unknownFieldsCapturedInAdditionalMetadata() {
        val jsonString =
            """
            {
                "credential_issuer": "https://issuer.example.com",
                "credential_endpoint": "https://issuer.example.com/credential",
                "credential_configurations_supported": {
                    "Test": { "format": "jwt_vc_json" }
                },
                "custom_field": "custom_value",
                "another_extension": 42
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialIssuerMetadata>(jsonString)

        assertEquals("https://issuer.example.com", decoded.credentialIssuer)
        assertEquals(2, decoded.additionalMetadata.size)
        assertEquals("custom_value", decoded.additionalMetadata["custom_field"]?.jsonPrimitive?.content)
        assertEquals("42", decoded.additionalMetadata["another_extension"]?.jsonPrimitive?.content)

        // Round-trip preserves the additional metadata
        val reEncoded = json.encodeToString(decoded)
        val reDecoded = json.decodeFromString<CredentialIssuerMetadata>(reEncoded)
        assertEquals(decoded, reDecoded)
        assertEquals("custom_value", reDecoded.additionalMetadata["custom_field"]?.jsonPrimitive?.content)
    }

    @Test
    fun deserializesFromSpecJson() {
        val specJson =
            """
            {
                "credential_issuer": "https://credential-issuer.example.com",
                "credential_endpoint": "https://credential-issuer.example.com/credential",
                "nonce_endpoint": "https://credential-issuer.example.com/nonce",
                "credential_configurations_supported": {
                    "UniversityDegreeCredential": {
                        "format": "vc+sd-jwt",
                        "scope": "UniversityDegree",
                        "vct": "https://credentials.example.com/identity_credential",
                        "cryptographic_binding_methods_supported": ["did:example"],
                        "credential_signing_alg_values_supported": ["ES256"],
                        "proof_types_supported": {
                            "jwt": {
                                "proof_signing_alg_values_supported": ["ES256"]
                            }
                        },
                        "display": [
                            {
                                "name": "University Credential",
                                "locale": "en-US",
                                "logo": {
                                    "uri": "https://university.example.edu/public/logo.png",
                                    "alt_text": "University Logo"
                                },
                                "background_color": "#12107c",
                                "text_color": "#FFFFFF"
                            }
                        ]
                    }
                }
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialIssuerMetadata>(specJson)

        assertEquals("https://credential-issuer.example.com", decoded.credentialIssuer)
        assertEquals("https://credential-issuer.example.com/credential", decoded.credentialEndpoint)
        assertEquals("https://credential-issuer.example.com/nonce", decoded.nonceEndpoint)

        val config = decoded.credentialConfigurationsSupported["UniversityDegreeCredential"]
        assertNotNull(config)
        assertEquals("vc+sd-jwt", config.format)
        assertEquals("UniversityDegree", config.scope)
        assertEquals("https://credentials.example.com/identity_credential", config.vct)
        assertEquals(listOf("did:example"), config.cryptographicBindingMethodsSupported)
        assertEquals(listOf("ES256"), config.credentialSigningAlgValuesSupported)

        val jwtProof = config.proofTypesSupported?.get("jwt")
        assertNotNull(jwtProof)
        assertEquals(listOf("ES256"), jwtProof.proofSigningAlgValuesSupported)

        val display = config.display?.firstOrNull()
        assertNotNull(display)
        assertEquals("University Credential", display.name)
        assertEquals("en-US", display.locale)
        assertEquals("https://university.example.edu/public/logo.png", display.logo?.uri)
        assertEquals("University Logo", display.logo?.altText)
        assertEquals("#12107c", display.backgroundColor)
        assertEquals("#FFFFFF", display.textColor)

        // Verify round-trip
        val reEncoded = json.encodeToString(decoded)
        val reDecoded = json.decodeFromString<CredentialIssuerMetadata>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun version11MetadataWithBatchCredentialIssuanceRoundTrip() {
        val jsonString =
            """
            {
                "credential_issuer": "https://issuer.example.com",
                "credential_endpoint": "https://issuer.example.com/credential",
                "credential_configurations_supported": {
                    "TestCredential": { "format": "jwt_vc_json" }
                },
                "batch_credential_issuance": {
                    "batch_size": 10
                }
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialIssuerMetadata>(jsonString)

        assertNotNull(decoded.batchCredentialIssuance)
        assertEquals(10, decoded.batchCredentialIssuance?.batchSize)
        assertNull(decoded.batchCredentialEndpoint, "1.1 uses batch_credential_issuance, not batch_credential_endpoint")

        // Re-serialize and verify wire format
        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        val batchObj = reObj["batch_credential_issuance"]!!.jsonObject
        assertEquals(10, batchObj["batch_size"]?.jsonPrimitive?.int)
        assertFalse(reObj.containsKey("batch_credential_endpoint"))

        val reDecoded = json.decodeFromString<CredentialIssuerMetadata>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun version10MetadataWithBatchCredentialEndpointRoundTrip() {
        val jsonString =
            """
            {
                "credential_issuer": "https://issuer.example.com",
                "credential_endpoint": "https://issuer.example.com/credential",
                "batch_credential_endpoint": "https://issuer.example.com/batch_credential",
                "credential_configurations_supported": {
                    "TestCredential": { "format": "jwt_vc_json" }
                }
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialIssuerMetadata>(jsonString)

        assertEquals("https://issuer.example.com/batch_credential", decoded.batchCredentialEndpoint)
        assertNull(decoded.batchCredentialIssuance, "1.0 uses batch_credential_endpoint, not batch_credential_issuance")

        // Re-serialize and verify wire format
        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        assertEquals("https://issuer.example.com/batch_credential", reObj["batch_credential_endpoint"]?.jsonPrimitive?.content)
        assertFalse(reObj.containsKey("batch_credential_issuance"))

        val reDecoded = json.decodeFromString<CredentialIssuerMetadata>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun version11MetadataWithCredentialResponseEncryptionTopLevel() {
        val jsonString =
            """
            {
                "credential_issuer": "https://issuer.example.com",
                "credential_endpoint": "https://issuer.example.com/credential",
                "credential_configurations_supported": {
                    "TestCredential": { "format": "jwt_vc_json" }
                },
                "credential_response_encryption": {
                    "alg_values_supported": ["ECDH-ES", "RSA-OAEP"],
                    "enc_values_supported": ["A256GCM", "A128CBC-HS256"],
                    "zip_values_supported": ["DEF"],
                    "encryption_required": true
                }
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialIssuerMetadata>(jsonString)

        assertNotNull(decoded.credentialResponseEncryption)
        assertEquals(listOf("ECDH-ES", "RSA-OAEP"), decoded.credentialResponseEncryption?.algValuesSupported)
        assertEquals(listOf("A256GCM", "A128CBC-HS256"), decoded.credentialResponseEncryption?.encValuesSupported)
        assertEquals(listOf("DEF"), decoded.credentialResponseEncryption?.zipValuesSupported)
        assertTrue(decoded.credentialResponseEncryption?.encryptionRequired == true)

        // Re-serialize and verify wire format
        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        val encObj = reObj["credential_response_encryption"]!!.jsonObject
        assertEquals(2, encObj["alg_values_supported"]?.jsonArray?.size)
        assertEquals(
            "ECDH-ES",
            encObj["alg_values_supported"]
                ?.jsonArray
                ?.get(0)
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(2, encObj["enc_values_supported"]?.jsonArray?.size)
        assertEquals(1, encObj["zip_values_supported"]?.jsonArray?.size)
        assertEquals(
            "DEF",
            encObj["zip_values_supported"]
                ?.jsonArray
                ?.get(0)
                ?.jsonPrimitive
                ?.content,
        )
        assertTrue(encObj["encryption_required"]?.jsonPrimitive?.boolean == true)

        val reDecoded = json.decodeFromString<CredentialIssuerMetadata>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun version11MetadataWithCredentialRequestEncryption() {
        val jsonString =
            """
            {
                "credential_issuer": "https://issuer.example.com",
                "credential_endpoint": "https://issuer.example.com/credential",
                "credential_configurations_supported": {
                    "TestCredential": { "format": "jwt_vc_json" }
                },
                "credential_request_encryption": {
                    "jwks": {
                        "keys": [
                            {
                                "kty": "EC",
                                "crv": "P-256",
                                "x": "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
                                "y": "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0"
                            }
                        ]
                    },
                    "enc_values_supported": ["A256GCM"],
                    "zip_values_supported": ["DEF"],
                    "encryption_required": false
                }
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialIssuerMetadata>(jsonString)

        assertNotNull(decoded.credentialRequestEncryption)
        assertNotNull(decoded.credentialRequestEncryption?.jwks)
        val keys =
            decoded.credentialRequestEncryption
                ?.jwks
                ?.get("keys")
                ?.jsonArray
        assertNotNull(keys)
        assertEquals(1, keys.size)
        assertEquals("EC", keys[0].jsonObject["kty"]?.jsonPrimitive?.content)
        assertEquals(listOf("A256GCM"), decoded.credentialRequestEncryption?.encValuesSupported)
        assertEquals(listOf("DEF"), decoded.credentialRequestEncryption?.zipValuesSupported)
        assertFalse(decoded.credentialRequestEncryption?.encryptionRequired == true)

        // Re-serialize and verify wire format
        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        val reqEncObj = reObj["credential_request_encryption"]!!.jsonObject
        assertTrue(reqEncObj.containsKey("jwks"), "must have 'jwks' key")
        assertTrue(reqEncObj.containsKey("enc_values_supported"))
        assertTrue(reqEncObj.containsKey("zip_values_supported"))

        val reDecoded = json.decodeFromString<CredentialIssuerMetadata>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun version11CredentialMetadataWithClaimsArrayRoundTrip() {
        // credential_metadata with path-based claims lives inside credential_configurations_supported
        val jsonString =
            """
            {
                "credential_issuer": "https://issuer.example.com",
                "credential_endpoint": "https://issuer.example.com/credential",
                "credential_configurations_supported": {
                    "IdentityCredential": {
                        "format": "vc+sd-jwt",
                        "vct": "https://credentials.example.com/identity_credential",
                        "credential_metadata": {
                            "display": [
                                {
                                    "name": "Identity Credential",
                                    "locale": "en-US"
                                }
                            ],
                            "claims": [
                                {
                                    "path": ["given_name"],
                                    "mandatory": true,
                                    "display": [
                                        { "name": "Given Name", "locale": "en-US" },
                                        { "name": "Vorname", "locale": "de-DE" }
                                    ]
                                },
                                {
                                    "path": ["address", "street_address"],
                                    "mandatory": false
                                },
                                {
                                    "path": ["nationalities", 0]
                                }
                            ]
                        }
                    }
                }
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialIssuerMetadata>(jsonString)

        val config = decoded.credentialConfigurationsSupported["IdentityCredential"]
        assertNotNull(config)
        assertNotNull(config.credentialMetadata)

        val credMeta = config.credentialMetadata!!
        assertEquals(1, credMeta.display?.size)
        assertEquals("Identity Credential", credMeta.display?.get(0)?.name)

        assertNotNull(credMeta.claims)
        assertEquals(3, credMeta.claims?.size)

        // First claim: path=["given_name"], mandatory=true, 2 display entries
        val claim0 = credMeta.claims!![0]
        assertEquals(1, claim0.path.size)
        assertEquals("given_name", claim0.path[0].jsonPrimitive.content)
        assertTrue(claim0.mandatory == true)
        assertEquals(2, claim0.display?.size)
        assertEquals("Given Name", claim0.display?.get(0)?.name)
        assertEquals("en-US", claim0.display?.get(0)?.locale)
        assertEquals("Vorname", claim0.display?.get(1)?.name)

        // Second claim: path=["address", "street_address"], mandatory=false
        val claim1 = credMeta.claims!![1]
        assertEquals(2, claim1.path.size)
        assertEquals("address", claim1.path[0].jsonPrimitive.content)
        assertEquals("street_address", claim1.path[1].jsonPrimitive.content)
        assertFalse(claim1.mandatory == true)

        // Third claim: path=["nationalities", 0] (array index)
        val claim2 = credMeta.claims!![2]
        assertEquals(2, claim2.path.size)
        assertEquals("nationalities", claim2.path[0].jsonPrimitive.content)
        assertEquals(0, claim2.path[1].jsonPrimitive.int)
        assertNull(claim2.mandatory)

        // Re-serialize and verify wire format
        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        val configObj = reObj["credential_configurations_supported"]!!.jsonObject["IdentityCredential"]!!.jsonObject
        assertTrue(configObj.containsKey("credential_metadata"), "must preserve credential_metadata key")
        val metaObj = configObj["credential_metadata"]!!.jsonObject
        assertTrue(metaObj.containsKey("claims"))
        val claimsArray = metaObj["claims"]!!.jsonArray
        assertEquals(3, claimsArray.size)

        // Verify path is serialized as array
        val firstClaimObj = claimsArray[0].jsonObject
        val pathArray = firstClaimObj["path"]!!.jsonArray
        assertEquals("given_name", pathArray[0].jsonPrimitive.content)

        val reDecoded = json.decodeFromString<CredentialIssuerMetadata>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun credentialDefinitionWithContextRoundTrip() {
        val defJson = """{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","UniversityDegreeCredential"]}"""
        val def = json.decodeFromString<CredentialDefinition>(defJson)

        assertEquals(listOf("https://www.w3.org/2018/credentials/v1"), def.context)
        assertEquals(listOf("VerifiableCredential", "UniversityDegreeCredential"), def.type)

        val reEncoded = json.encodeToString(CredentialDefinition.serializer(), def)
        assertTrue(reEncoded.contains("@context"))

        val reDecoded = json.decodeFromString<CredentialDefinition>(reEncoded)
        assertEquals(def, reDecoded)
    }

    @Test
    fun credentialDefinitionContextPreservedInIssuerMetadata() {
        val jsonString =
            """
            {
                "credential_issuer": "https://issuer.example.com",
                "credential_endpoint": "https://issuer.example.com/credential",
                "credential_configurations_supported": {
                    "UniversityDegree": {
                        "format": "ldp_vc",
                        "credential_definition": {
                            "@context": ["https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"],
                            "type": ["VerifiableCredential", "UniversityDegreeCredential"]
                        }
                    }
                }
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialIssuerMetadata>(jsonString)

        val config = decoded.credentialConfigurationsSupported["UniversityDegree"]
        assertNotNull(config)
        val credDef = config.credentialDefinition
        assertNotNull(credDef)
        assertEquals(
            listOf("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
            credDef.context,
        )
        assertEquals(listOf("VerifiableCredential", "UniversityDegreeCredential"), credDef.type)

        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        val credDefObj =
            reObj["credential_configurations_supported"]!!
                .jsonObject["UniversityDegree"]!!
                .jsonObject["credential_definition"]!!
                .jsonObject
        assertTrue(credDefObj.containsKey("@context"), "must preserve @context key in wire format")
        assertEquals(2, credDefObj["@context"]!!.jsonArray.size)

        val reDecoded = json.decodeFromString<CredentialIssuerMetadata>(reEncoded)
        assertEquals(decoded, reDecoded)
    }
}
