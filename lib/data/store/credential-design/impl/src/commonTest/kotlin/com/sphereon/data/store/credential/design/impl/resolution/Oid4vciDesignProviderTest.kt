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

package com.sphereon.data.store.credential.design.impl.resolution

import com.sphereon.data.store.credential.design.model.ClaimPathSegment
import com.sphereon.data.store.credential.design.model.DesignSourceType
import com.sphereon.data.store.credential.design.model.ExternalDesignMetadata
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.data.store.credential.design.model.ResolveEntityDesignInput
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Oid4vciDesignProviderTest {
    private val credentialProvider = Oid4vciCredentialConfigDesignProvider()
    private val issuerProvider = Oid4vciIssuerMetadataDesignProvider()
    private val json = Json { ignoreUnknownKeys = true }

    // ---- sourceType / authoritative ----

    @Test
    fun credentialProviderSourceType() {
        assertEquals(DesignSourceType.OID4VCI_CREDENTIAL_CONFIGURATION, credentialProvider.sourceType)
    }

    @Test
    fun credentialProviderNotAuthoritative() {
        assertEquals(false, credentialProvider.authoritative)
    }

    @Test
    fun issuerProviderSourceType() {
        assertEquals(DesignSourceType.OID4VCI_ISSUER_METADATA, issuerProvider.sourceType)
    }

    @Test
    fun issuerProviderNotAuthoritative() {
        assertEquals(false, issuerProvider.authoritative)
    }

    // ---- Credential provider: null when no metadata ----

    @Test
    fun credentialProviderReturnsNullWhenNoExternalMetadata() =
        runTest {
            val input = ResolveCredentialDesignInput()
            val result = credentialProvider.resolveCredentialLayer("tenant", input)
            assertNull(result)
        }

    @Test
    fun credentialProviderReturnsNullWhenOid4vciConfigAbsent() =
        runTest {
            val input =
                ResolveCredentialDesignInput(
                    externalMetadata = ExternalDesignMetadata(),
                )
            val result = credentialProvider.resolveCredentialLayer("tenant", input)
            assertNull(result)
        }

    @Test
    fun credentialProviderReturnsNullOnMalformedJson() =
        runTest {
            val input =
                ResolveCredentialDesignInput(
                    externalMetadata =
                        ExternalDesignMetadata(
                            oid4vciCredentialConfiguration = buildJsonObject { put("not_a_format", "oops") },
                        ),
                )
            // No "format" field → deserialization fails → null
            val result = credentialProvider.resolveCredentialLayer("tenant", input)
            assertNull(result)
        }

    // ---- Credential provider: OID4VCI 1.1 credential_metadata path ----

    @Test
    fun credentialProviderMaps11CredentialMetadataDisplays() =
        runTest {
            val configJson =
                json.parseToJsonElement(
                    """
                    {
                      "format": "vc+sd-jwt",
                      "credential_metadata": {
                        "display": [
                          {"name": "Identity Credential", "locale": "en-US", "description": "Your digital ID"},
                          {"name": "Identitaetsnachweis", "locale": "de-DE"}
                        ]
                      }
                    }
                    """.trimIndent(),
                ) as JsonObject

            val input = inputCredConfig(configJson)
            val result = credentialProvider.resolveCredentialLayer("tenant", input)

            assertNotNull(result)
            assertEquals(2, result.displays.size)

            val enDisplay = result.displays.first { it.locale == "en-US" }
            assertEquals("Identity Credential", enDisplay.name)
            assertEquals("Your digital ID", enDisplay.description)

            val deDisplay = result.displays.first { it.locale == "de-DE" }
            assertEquals("Identitaetsnachweis", deDisplay.name)
            assertNull(deDisplay.description)
        }

    @Test
    fun credentialProviderMaps11CredentialMetadataClaims() =
        runTest {
            val configJson =
                json.parseToJsonElement(
                    """
                    {
                      "format": "vc+sd-jwt",
                      "credential_metadata": {
                        "claims": [
                          {
                            "path": ["given_name"],
                            "mandatory": true,
                            "display": [{"name": "Given Name", "locale": "en-US"}]
                          },
                          {
                            "path": ["address", "street_address"],
                            "mandatory": false,
                            "display": [{"name": "Street", "locale": "en-US"}]
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                ) as JsonObject

            val input = inputCredConfig(configJson)
            val result = credentialProvider.resolveCredentialLayer("tenant", input)

            assertNotNull(result)
            assertEquals(2, result.claims.size)

            val givenName = result.claims[0]
            assertEquals(listOf(ClaimPathSegment.Property("given_name")), givenName.path)
            assertTrue(givenName.mandatory)
            assertEquals(1, givenName.labels.size)
            assertEquals("Given Name", givenName.labels[0].label)
            assertEquals("en-US", givenName.labels[0].locale)

            val street = result.claims[1]
            assertEquals(
                listOf(ClaimPathSegment.Property("address"), ClaimPathSegment.Property("street_address")),
                street.path,
            )
            assertEquals(false, street.mandatory)
        }

    @Test
    fun credentialProviderMaps11ArrayPathClaims() =
        runTest {
            val configJson =
                json.parseToJsonElement(
                    """
                    {
                      "format": "vc+sd-jwt",
                      "credential_metadata": {
                        "claims": [
                          {
                            "path": ["degrees", null, "type"],
                            "display": [{"name": "Degree Type", "locale": "en"}]
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                ) as JsonObject

            val input = inputCredConfig(configJson)
            val result = credentialProvider.resolveCredentialLayer("tenant", input)

            assertNotNull(result)
            assertEquals(1, result.claims.size)
            assertEquals(
                listOf(
                    ClaimPathSegment.Property("degrees"),
                    ClaimPathSegment.AnyArrayElement,
                    ClaimPathSegment.Property("type"),
                ),
                result.claims[0].path,
            )
        }

    @Test
    fun credentialProvider11ProvidedFieldsMatchDisplaysAndClaims() =
        runTest {
            val configJson =
                json.parseToJsonElement(
                    """
                    {
                      "format": "vc+sd-jwt",
                      "credential_metadata": {
                        "display": [{"name": "Test", "locale": "en"}],
                        "claims": [
                          {
                            "path": ["given_name"],
                            "display": [{"name": "Given Name", "locale": "en"}]
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                ) as JsonObject

            val result = credentialProvider.resolveCredentialLayer("tenant", inputCredConfig(configJson))
            assertNotNull(result)
            assertTrue(result.providedFields.contains("display:en"))
            assertTrue(result.providedFields.any { it.startsWith("claim:") })
        }

    // ---- Credential provider: OID4VCI 1.0 top-level fallback ----

    @Test
    fun credentialProviderMaps10TopLevelDisplay() =
        runTest {
            val configJson =
                buildJsonObject {
                    put("format", "jwt_vc_json")
                    putJsonArray("display") {
                        add(
                            buildJsonObject {
                                put("name", "University Degree")
                                put("locale", "en-US")
                                put("description", "A university degree credential")
                            },
                        )
                    }
                }

            val input = inputCredConfig(configJson)
            val result = credentialProvider.resolveCredentialLayer("tenant", input)

            assertNotNull(result)
            assertEquals(1, result.displays.size)
            assertEquals("University Degree", result.displays[0].name)
            assertEquals("en-US", result.displays[0].locale)
            assertEquals("A university degree credential", result.displays[0].description)
        }

    @Test
    fun credentialProviderMaps10TopLevelClaimsMap() =
        runTest {
            val configJson =
                json.parseToJsonElement(
                    """
                    {
                      "format": "jwt_vc_json",
                      "claims": {
                        "given_name": {
                          "mandatory": true,
                          "display": [{"name": "Given Name", "locale": "en-US"}]
                        },
                        "family_name": {
                          "mandatory": false,
                          "display": [{"name": "Family Name", "locale": "en-US"}]
                        }
                      }
                    }
                    """.trimIndent(),
                ) as JsonObject

            val input = inputCredConfig(configJson)
            val result = credentialProvider.resolveCredentialLayer("tenant", input)

            assertNotNull(result)
            assertEquals(2, result.claims.size)

            val givenName = result.claims.first { it.path == listOf(ClaimPathSegment.Property("given_name")) }
            assertTrue(givenName.mandatory)
            assertEquals("Given Name", givenName.labels[0].label)

            val familyName = result.claims.first { it.path == listOf(ClaimPathSegment.Property("family_name")) }
            assertEquals(false, familyName.mandatory)
        }

    @Test
    fun credentialProviderMaps10CredentialDefinitionSubject() =
        runTest {
            val configJson =
                json.parseToJsonElement(
                    """
                    {
                      "format": "jwt_vc_json",
                      "credential_definition": {
                        "type": ["UniversityDegreeCredential"],
                        "credentialSubject": {
                          "degree": {
                            "mandatory": true,
                            "display": [{"name": "Degree", "locale": "en"}]
                          }
                        }
                      }
                    }
                    """.trimIndent(),
                ) as JsonObject

            val input = inputCredConfig(configJson)
            val result = credentialProvider.resolveCredentialLayer("tenant", input)

            assertNotNull(result)
            assertEquals(1, result.claims.size)
            assertEquals(listOf(ClaimPathSegment.Property("degree")), result.claims[0].path)
            assertTrue(result.claims[0].mandatory)
        }

    @Test
    fun credentialProviderPrefers10TopLevelClaimsOverCredentialDefinition() =
        runTest {
            // If top-level claims is present, credentialDefinition.credentialSubject is ignored.
            val configJson =
                json.parseToJsonElement(
                    """
                    {
                      "format": "jwt_vc_json",
                      "claims": {
                        "top_level_claim": {"mandatory": false}
                      },
                      "credential_definition": {
                        "credentialSubject": {
                          "def_claim": {"mandatory": true}
                        }
                      }
                    }
                    """.trimIndent(),
                ) as JsonObject

            val input = inputCredConfig(configJson)
            val result = credentialProvider.resolveCredentialLayer("tenant", input)

            assertNotNull(result)
            assertEquals(1, result.claims.size)
            assertEquals(listOf(ClaimPathSegment.Property("top_level_claim")), result.claims[0].path)
        }

    // ---- Credential provider: locale null fallback ----

    @Test
    fun credentialProviderHandlesNullLocale() =
        runTest {
            val configJson =
                buildJsonObject {
                    put("format", "vc+sd-jwt")
                    putJsonArray("display") {
                        add(buildJsonObject { put("name", "No Locale Credential") })
                    }
                }

            val input = inputCredConfig(configJson)
            val result = credentialProvider.resolveCredentialLayer("tenant", input)

            assertNotNull(result)
            assertEquals(1, result.displays.size)
            assertEquals("", result.displays[0].locale)
            assertEquals("No Locale Credential", result.displays[0].name)
        }

    // ---- Issuer provider: null when no metadata ----

    @Test
    fun issuerProviderReturnsNullWhenNoExternalMetadata() =
        runTest {
            val input = ResolveEntityDesignInput()
            val result = issuerProvider.resolveIssuerLayer("tenant", input)
            assertNull(result)
        }

    @Test
    fun issuerProviderReturnsNullWhenOid4vciIssuerMetadataAbsent() =
        runTest {
            val input =
                ResolveEntityDesignInput(
                    externalMetadata = ExternalDesignMetadata(),
                )
            val result = issuerProvider.resolveIssuerLayer("tenant", input)
            assertNull(result)
        }

    @Test
    fun issuerProviderReturnsNullOnMalformedJson() =
        runTest {
            val input =
                ResolveEntityDesignInput(
                    externalMetadata =
                        ExternalDesignMetadata(
                            oid4vciIssuerMetadata = buildJsonObject { put("bad", "data") },
                        ),
                )
            val result = issuerProvider.resolveIssuerLayer("tenant", input)
            assertNull(result)
        }

    // ---- Issuer provider: happy path ----

    @Test
    fun issuerProviderMapsDisplaysFromIssuerMetadata() =
        runTest {
            val metadataJson =
                buildJsonObject {
                    put("credential_issuer", "https://issuer.example.com")
                    put("credential_endpoint", "https://issuer.example.com/credential")
                    putJsonObject("credential_configurations_supported") {}
                    putJsonArray("display") {
                        add(
                            buildJsonObject {
                                put("name", "Example University")
                                put("locale", "en-US")
                            },
                        )
                        add(
                            buildJsonObject {
                                put("name", "Beispiel-Universitaet")
                                put("locale", "de-DE")
                            },
                        )
                    }
                }

            val input =
                ResolveEntityDesignInput(
                    externalMetadata = ExternalDesignMetadata(oid4vciIssuerMetadata = metadataJson),
                )
            val result = issuerProvider.resolveIssuerLayer("tenant", input)

            assertNotNull(result)
            assertEquals(2, result.displays.size)

            val en = result.displays.first { it.locale == "en-US" }
            assertEquals("Example University", en.displayName)
            assertNull(en.description)

            val de = result.displays.first { it.locale == "de-DE" }
            assertEquals("Beispiel-Universitaet", de.displayName)
        }

    @Test
    fun issuerProviderHandlesNullLocale() =
        runTest {
            val metadataJson =
                buildJsonObject {
                    put("credential_issuer", "https://issuer.example.com")
                    put("credential_endpoint", "https://issuer.example.com/credential")
                    putJsonObject("credential_configurations_supported") {}
                    putJsonArray("display") {
                        add(buildJsonObject { put("name", "No Locale Issuer") })
                    }
                }

            val input =
                ResolveEntityDesignInput(
                    externalMetadata = ExternalDesignMetadata(oid4vciIssuerMetadata = metadataJson),
                )
            val result = issuerProvider.resolveIssuerLayer("tenant", input)

            assertNotNull(result)
            assertEquals(1, result.displays.size)
            assertEquals("", result.displays[0].locale)
            assertEquals("No Locale Issuer", result.displays[0].displayName)
        }

    @Test
    fun issuerProviderReturnsNullWhenDisplayListAbsent() =
        runTest {
            // Metadata with no display array → returns null
            val metadataJson =
                buildJsonObject {
                    put("credential_issuer", "https://issuer.example.com")
                    put("credential_endpoint", "https://issuer.example.com/credential")
                    putJsonObject("credential_configurations_supported") {}
                }

            val input =
                ResolveEntityDesignInput(
                    externalMetadata = ExternalDesignMetadata(oid4vciIssuerMetadata = metadataJson),
                )
            val result = issuerProvider.resolveIssuerLayer("tenant", input)
            assertNull(result)
        }

    @Test
    fun issuerProviderProvidedFieldsMatchDisplayLocales() =
        runTest {
            val metadataJson =
                buildJsonObject {
                    put("credential_issuer", "https://issuer.example.com")
                    put("credential_endpoint", "https://issuer.example.com/credential")
                    putJsonObject("credential_configurations_supported") {}
                    putJsonArray("display") {
                        add(
                            buildJsonObject {
                                put("name", "Issuer EN")
                                put("locale", "en")
                            },
                        )
                        add(
                            buildJsonObject {
                                put("name", "Issuer DE")
                                put("locale", "de")
                            },
                        )
                    }
                }

            val input =
                ResolveEntityDesignInput(
                    externalMetadata = ExternalDesignMetadata(oid4vciIssuerMetadata = metadataJson),
                )
            val result = issuerProvider.resolveIssuerLayer("tenant", input)

            assertNotNull(result)
            assertEquals(setOf("display:en", "display:de"), result.providedFields)
        }

    @Test
    fun issuerProviderDoesNotImplementCredentialLayer() =
        runTest {
            val result = issuerProvider.resolveCredentialLayer("tenant", ResolveCredentialDesignInput())
            assertNull(result)
        }

    @Test
    fun credentialProviderDoesNotImplementIssuerLayer() =
        runTest {
            val result = credentialProvider.resolveIssuerLayer("tenant", ResolveEntityDesignInput())
            assertNull(result)
        }

    // ---- Helpers ----

    private fun inputCredConfig(configJson: JsonObject) =
        ResolveCredentialDesignInput(
            externalMetadata = ExternalDesignMetadata(oid4vciCredentialConfiguration = configJson),
        )
}
