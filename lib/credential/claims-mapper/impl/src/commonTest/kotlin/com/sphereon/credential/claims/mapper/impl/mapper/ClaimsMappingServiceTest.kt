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

package com.sphereon.credential.claims.mapper.impl.mapper

import com.sphereon.credential.claims.mapper.api.model.ClaimMapping
import com.sphereon.credential.claims.mapper.api.model.ClaimMappingConfiguration
import com.sphereon.credential.claims.mapper.api.model.ClaimPathType
import com.sphereon.credential.claims.mapper.api.model.ClaimTransformation
import com.sphereon.credential.claims.mapper.api.model.CredentialMapping
import com.sphereon.credential.claims.mapper.api.model.CredentialWithId
import com.sphereon.credential.claims.mapper.api.model.DateInputFormat
import com.sphereon.openid.oid4vp.common.CredentialFormat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the low-level [ClaimsMappingServiceImpl].
 *
 * This test class focuses on the pure mapping functionality without persistence.
 * It tests:
 * - Basic claim mapping
 * - Transformations
 * - Nested paths
 * - Optional/required credentials and claims
 * - Priority-based merging
 * - Default values
 */
class ClaimsMappingServiceTest {

    private val mappingService = ClaimsMappingServiceImpl.withDefaults()

    // =============================================================================
    // Basic Mapping Tests
    // =============================================================================

    @Test
    fun `mapClaimsWithConfig should map claims from pre-decoded JSON`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("given_name"),
                            targetClaimPath = listOf("given_name")
                        ),
                        ClaimMapping(
                            sourceClaimPath = listOf("family_name"),
                            targetClaimPath = listOf("family_name")
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
            put("family_name", JsonPrimitive("Doe"))
            put("email", JsonPrimitive("john@example.com"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        val mapped = result.value
        assertEquals(2, mapped.claims.size)
        assertEquals(JsonPrimitive("John"), mapped.claims["given_name"])
        assertEquals(JsonPrimitive("Doe"), mapped.claims["family_name"])
        assertTrue(mapped.sourceCredentialIds.contains("pid"))
    }

    @Test
    fun `configuration without dcqlQueryId should work for basic mapping`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "basic-config",
            name = "Basic Mapping",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping.simple("given_name"),
                        ClaimMapping.simple("family_name")
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
            put("family_name", JsonPrimitive("Doe"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("John"), result.value.claims["given_name"])
        assertEquals(JsonPrimitive("Doe"), result.value.claims["family_name"])
    }

    // =============================================================================
    // Transformation Tests
    // =============================================================================

    @Test
    fun `mapClaimsWithConfig should apply Uppercase transformation`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("given_name"),
                            targetClaimPath = listOf("name"),
                            transformation = ClaimTransformation.Uppercase
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("JOHN"), result.value.claims["name"])
    }

    @Test
    fun `mapClaimsWithConfig should apply Lowercase transformation`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("email"),
                            targetClaimPath = listOf("email"),
                            transformation = ClaimTransformation.Lowercase
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("email", JsonPrimitive("John.Doe@Example.COM"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("john.doe@example.com"), result.value.claims["email"])
    }

    @Test
    fun `mapClaimsWithConfig should apply Substring transformation`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("birth_date"),
                            targetClaimPath = listOf("birth_year"),
                            transformation = ClaimTransformation.Substring(
                                startIndex = 0,
                                endIndex = 4
                            )
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("birth_date", JsonPrimitive("1990-05-15"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("1990"), result.value.claims["birth_year"])
    }

    @Test
    fun `mapClaimsWithConfig should apply RegexReplace transformation`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("phone"),
                            targetClaimPath = listOf("phone_digits"),
                            transformation = ClaimTransformation.RegexReplace(
                                pattern = "[^0-9]",
                                replacement = ""
                            )
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("phone", JsonPrimitive("+31 (6) 1234-5678"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("31612345678"), result.value.claims["phone_digits"])
    }

    @Test
    fun `mapClaimsWithConfig should apply Concatenate transformation`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("given_name"),
                            targetClaimPath = listOf("name"),
                            transformation = ClaimTransformation.Concatenate(
                                separator = " ",
                                suffixPaths = listOf(listOf("family_name"))
                            )
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
            put("family_name", JsonPrimitive("Doe"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("John Doe"), result.value.claims["name"])
    }

    @Test
    fun `mapClaimsWithConfig should apply Concatenate with custom separator`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("street"),
                            targetClaimPath = listOf("full_address"),
                            transformation = ClaimTransformation.Concatenate(
                                separator = ", ",
                                suffixPaths = listOf(
                                    listOf("city"),
                                    listOf("country")
                                )
                            )
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("street", JsonPrimitive("123 Main St"))
            put("city", JsonPrimitive("Amsterdam"))
            put("country", JsonPrimitive("Netherlands"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("123 Main St, Amsterdam, Netherlands"), result.value.claims["full_address"])
    }

    @Test
    fun `mapClaimsWithConfig should apply Concatenate with prefixPaths`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("given_name"),
                            targetClaimPath = listOf("formal_name"),
                            transformation = ClaimTransformation.Concatenate(
                                separator = " ",
                                prefixPaths = listOf(listOf("title"))
                            )
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("title", JsonPrimitive("Dr."))
            put("given_name", JsonPrimitive("John"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("Dr. John"), result.value.claims["formal_name"])
    }

    @Test
    fun `mapClaimsWithConfig should apply Concatenate with both prefixPaths and suffixPaths`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("given_name"),
                            targetClaimPath = listOf("full_formal_name"),
                            transformation = ClaimTransformation.Concatenate(
                                separator = " ",
                                prefixPaths = listOf(listOf("title")),
                                suffixPaths = listOf(
                                    listOf("family_name"),
                                    listOf("suffix")
                                )
                            )
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("title", JsonPrimitive("Dr."))
            put("given_name", JsonPrimitive("John"))
            put("family_name", JsonPrimitive("Doe"))
            put("suffix", JsonPrimitive("Jr."))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("Dr. John Doe Jr."), result.value.claims["full_formal_name"])
    }

    @Test
    fun `mapClaimsWithConfig should skip missing prefixPaths values in Concatenate`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("given_name"),
                            targetClaimPath = listOf("name"),
                            transformation = ClaimTransformation.Concatenate(
                                separator = " ",
                                prefixPaths = listOf(listOf("title")),
                                suffixPaths = listOf(listOf("family_name"))
                            )
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
            put("family_name", JsonPrimitive("Doe"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("John Doe"), result.value.claims["name"])
    }

    @Test
    fun `transformation should work with nested sourceClaimPath`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("contact", "email", "primary"),
                            targetClaimPath = listOf("email"),
                            transformation = ClaimTransformation.Lowercase
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("contact", buildJsonObject {
                put("email", buildJsonObject {
                    put("primary", JsonPrimitive("John.Doe@EXAMPLE.COM"))
                })
            })
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("john.doe@example.com"), result.value.claims["email"])
    }

    @Test
    fun `concatenate transformation should work with nested sourceClaimPaths`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("person", "name", "given"),
                            targetClaimPath = listOf("full_name"),
                            transformation = ClaimTransformation.Concatenate(
                                separator = " ",
                                suffixPaths = listOf(
                                    listOf("person", "name", "family")
                                )
                            )
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("person", buildJsonObject {
                put("name", buildJsonObject {
                    put("given", JsonPrimitive("John"))
                    put("family", JsonPrimitive("Doe"))
                })
            })
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("John Doe"), result.value.claims["full_name"])
    }

    // =============================================================================
    // Nested Path Tests
    // =============================================================================

    @Test
    fun `nested claim paths should work`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("address", "street"),
                            targetClaimPath = listOf("street_address")
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("address", buildJsonObject {
                put("street", JsonPrimitive("123 Main St"))
                put("city", JsonPrimitive("Anytown"))
            })
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("123 Main St"), result.value.claims["street_address"])
    }

    @Test
    fun `deeply nested sourceClaimPath should extract values`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("user", "profile", "personal", "name", "first"),
                            targetClaimPath = listOf("given_name")
                        ),
                        ClaimMapping(
                            sourceClaimPath = listOf("user", "profile", "personal", "name", "last"),
                            targetClaimPath = listOf("family_name")
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("user", buildJsonObject {
                put("profile", buildJsonObject {
                    put("personal", buildJsonObject {
                        put("name", buildJsonObject {
                            put("first", JsonPrimitive("John"))
                            put("last", JsonPrimitive("Doe"))
                        })
                    })
                })
            })
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(2, result.value.claims.size)
        assertEquals(JsonPrimitive("John"), result.value.claims["given_name"])
        assertEquals(JsonPrimitive("Doe"), result.value.claims["family_name"])
    }

    @Test
    fun `multiple nested sourceClaimPaths from same parent should work`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("address", "street_address"),
                            targetClaimPath = listOf("street")
                        ),
                        ClaimMapping(
                            sourceClaimPath = listOf("address", "locality"),
                            targetClaimPath = listOf("city")
                        ),
                        ClaimMapping(
                            sourceClaimPath = listOf("address", "region"),
                            targetClaimPath = listOf("state")
                        ),
                        ClaimMapping(
                            sourceClaimPath = listOf("address", "postal_code"),
                            targetClaimPath = listOf("zip")
                        ),
                        ClaimMapping(
                            sourceClaimPath = listOf("address", "country"),
                            targetClaimPath = listOf("country")
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("address", buildJsonObject {
                put("street_address", JsonPrimitive("123 Main St"))
                put("locality", JsonPrimitive("Amsterdam"))
                put("region", JsonPrimitive("Noord-Holland"))
                put("postal_code", JsonPrimitive("1012 AB"))
                put("country", JsonPrimitive("NL"))
            })
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(5, result.value.claims.size)
        assertEquals(JsonPrimitive("123 Main St"), result.value.claims["street"])
        assertEquals(JsonPrimitive("Amsterdam"), result.value.claims["city"])
        assertEquals(JsonPrimitive("Noord-Holland"), result.value.claims["state"])
        assertEquals(JsonPrimitive("1012 AB"), result.value.claims["zip"])
        assertEquals(JsonPrimitive("NL"), result.value.claims["country"])
    }

    @Test
    fun `nested targetClaimPath should produce dot-separated key`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("given_name"),
                            targetClaimPath = listOf("user", "name", "first")
                        ),
                        ClaimMapping(
                            sourceClaimPath = listOf("family_name"),
                            targetClaimPath = listOf("user", "name", "last")
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
            put("family_name", JsonPrimitive("Doe"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(2, result.value.claims.size)
        assertEquals(JsonPrimitive("John"), result.value.claims["user.name.first"])
        assertEquals(JsonPrimitive("Doe"), result.value.claims["user.name.last"])
    }

    @Test
    fun `nested source and target claim paths should work together`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("address", "street_address"),
                            targetClaimPath = listOf("contact", "address", "street")
                        ),
                        ClaimMapping(
                            sourceClaimPath = listOf("address", "locality"),
                            targetClaimPath = listOf("contact", "address", "city")
                        ),
                        ClaimMapping(
                            sourceClaimPath = listOf("address", "country"),
                            targetClaimPath = listOf("contact", "address", "country")
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("address", buildJsonObject {
                put("street_address", JsonPrimitive("123 Main St"))
                put("locality", JsonPrimitive("Amsterdam"))
                put("country", JsonPrimitive("NL"))
            })
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(3, result.value.claims.size)
        assertEquals(JsonPrimitive("123 Main St"), result.value.claims["contact.address.street"])
        assertEquals(JsonPrimitive("Amsterdam"), result.value.claims["contact.address.city"])
        assertEquals(JsonPrimitive("NL"), result.value.claims["contact.address.country"])
    }

    // =============================================================================
    // Optional/Required Credential Tests
    // =============================================================================

    @Test
    fun `mapClaimsWithConfig should handle optional credentials`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("given_name"),
                            targetClaimPath = listOf("given_name")
                        )
                    )
                ),
                CredentialMapping(
                    credentialId = "mdl",
                    optional = true,
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("driving_license_number"),
                            targetClaimPath = listOf("license_number")
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(1, result.value.claims.size)
        assertEquals(JsonPrimitive("John"), result.value.claims["given_name"])
        assertTrue(result.value.skippedOptionalCredentials.contains("mdl"))
    }

    @Test
    fun `mapClaimsWithConfig should fail for missing required credential`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    optional = false,
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("given_name"),
                            targetClaimPath = listOf("given_name")
                        )
                    )
                )
            )
        )

        val result = mappingService.mapClaimsWithConfig(emptyList(), config)

        assertTrue(result.isErr)
        assertTrue(result.error.code.contains("REQUIRED_CREDENTIAL_MISSING"))
    }

    // =============================================================================
    // Optional/Required Claim Tests
    // =============================================================================

    @Test
    fun `mapClaimsWithConfig should fail when required claim is missing`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("email"),
                            targetClaimPath = listOf("email"),
                            required = true
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isErr)
        assertTrue(result.error.code.contains("CLAIM_NOT_FOUND"))
    }

    @Test
    fun `mapClaimsWithConfig should skip optional missing claims`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("given_name"),
                            targetClaimPath = listOf("given_name")
                        ),
                        ClaimMapping(
                            sourceClaimPath = listOf("email"),
                            targetClaimPath = listOf("email"),
                            required = false
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(1, result.value.claims.size)
        assertEquals(JsonPrimitive("John"), result.value.claims["given_name"])
        assertFalse(result.value.claims.containsKey("email"))
    }

    @Test
    fun `missing nested sourceClaimPath should be handled correctly`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("address", "street"),
                            targetClaimPath = listOf("street"),
                            required = false
                        ),
                        ClaimMapping(
                            sourceClaimPath = listOf("address", "nonexistent", "field"),
                            targetClaimPath = listOf("missing"),
                            required = false
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("address", buildJsonObject {
                put("street", JsonPrimitive("123 Main St"))
            })
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(1, result.value.claims.size)
        assertEquals(JsonPrimitive("123 Main St"), result.value.claims["street"])
        assertFalse(result.value.claims.containsKey("missing"))
    }

    @Test
    fun `required nested sourceClaimPath should fail when missing`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("address", "nonexistent", "field"),
                            targetClaimPath = listOf("required_field"),
                            required = true
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("address", buildJsonObject {
                put("street", JsonPrimitive("123 Main St"))
            })
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isErr)
        assertTrue(result.error.code.contains("CLAIM_NOT_FOUND"))
    }

    // =============================================================================
    // Default Value Tests
    // =============================================================================

    @Test
    fun `mapClaimsWithConfig should apply default claims from ClaimMapping`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("given_name"),
                            targetClaimPath = listOf("given_name")
                        ),
                        ClaimMapping(
                            sourceClaimPath = listOf("locale"),
                            targetClaimPath = listOf("locale"),
                            defaultValue = JsonPrimitive("en-US")
                        )
                    )
                )
            )
        )

        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(2, result.value.claims.size)
        assertEquals(JsonPrimitive("John"), result.value.claims["given_name"])
        assertEquals(JsonPrimitive("en-US"), result.value.claims["locale"])
        assertTrue(result.value.appliedDefaults.contains("locale"))
    }

    // =============================================================================
    // Priority Tests
    // =============================================================================

    @Test
    fun `mapClaimsWithConfig should handle priority-based merging`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("given_name"),
                            targetClaimPath = listOf("name"),
                            priority = 10
                        )
                    )
                ),
                CredentialMapping(
                    credentialId = "mdl",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("given_name"),
                            targetClaimPath = listOf("name"),
                            priority = 5
                        )
                    )
                )
            )
        )

        val pidClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
        }
        val mdlClaims = buildJsonObject {
            put("given_name", JsonPrimitive("Jonathan"))
        }

        val credentials = listOf(
            CredentialWithId(
                credentialId = "pid",
                format = CredentialFormat.SD_JWT_DC,
                payload = "",
                disclosedClaims = pidClaims
            ),
            CredentialWithId(
                credentialId = "mdl",
                format = CredentialFormat.SD_JWT_DC,
                payload = "",
                disclosedClaims = mdlClaims
            )
        )

        val result = mappingService.mapClaimsWithConfig(credentials, config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("John"), result.value.claims["name"])
    }

    @Test
    fun `priority merging should work with nested target paths`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("given_name"),
                            targetClaimPath = listOf("user", "name"),
                            priority = 10
                        )
                    )
                ),
                CredentialMapping(
                    credentialId = "mdl",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("first_name"),
                            targetClaimPath = listOf("user", "name"),
                            priority = 5
                        )
                    )
                )
            )
        )

        val pidClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
        }
        val mdlClaims = buildJsonObject {
            put("first_name", JsonPrimitive("Jonathan"))
        }

        val credentials = listOf(
            CredentialWithId(
                credentialId = "pid",
                format = CredentialFormat.SD_JWT_DC,
                payload = "",
                disclosedClaims = pidClaims
            ),
            CredentialWithId(
                credentialId = "mdl",
                format = CredentialFormat.SD_JWT_DC,
                payload = "",
                disclosedClaims = mdlClaims
            )
        )

        val result = mappingService.mapClaimsWithConfig(credentials, config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("John"), result.value.claims["user.name"])
    }

    // =============================================================================
    // ClaimMapping Helper Tests
    // =============================================================================

    @Test
    fun `sourcePathAsString should correctly join path segments`() {
        val singleSegment = ClaimMapping(
            sourceClaimPath = listOf("email"),
            targetClaimPath = listOf("email")
        )
        assertEquals("email", singleSegment.sourcePathAsString())

        val twoSegments = ClaimMapping(
            sourceClaimPath = listOf("address", "city"),
            targetClaimPath = listOf("city")
        )
        assertEquals("address.city", twoSegments.sourcePathAsString())

        val deepNesting = ClaimMapping(
            sourceClaimPath = listOf("level1", "level2", "level3", "level4", "value"),
            targetClaimPath = listOf("value")
        )
        assertEquals("level1.level2.level3.level4.value", deepNesting.sourcePathAsString())
    }

    @Test
    fun `targetPathAsString should correctly join path segments`() {
        val singleSegment = ClaimMapping(
            sourceClaimPath = listOf("foo"),
            targetClaimPath = listOf("bar")
        )
        assertEquals("bar", singleSegment.targetPathAsString())

        val multiSegment = ClaimMapping(
            sourceClaimPath = listOf("foo"),
            targetClaimPath = listOf("user", "profile", "name")
        )
        assertEquals("user.profile.name", multiSegment.targetPathAsString())

        val deepNesting = ClaimMapping(
            sourceClaimPath = listOf("a"),
            targetClaimPath = listOf("level1", "level2", "level3", "level4", "value")
        )
        assertEquals("level1.level2.level3.level4.value", deepNesting.targetPathAsString())
    }

    @Test
    fun `ClaimMapping nested helper should create correct sourceClaimPath`() {
        val mapping = ClaimMapping.nested("address", "street", "number", targetClaim = "street_number")

        assertEquals(listOf("address", "street", "number"), mapping.sourceClaimPath)
        assertEquals(listOf("street_number"), mapping.targetClaimPath)
        assertEquals("address.street.number", mapping.sourcePathAsString())
        assertEquals("street_number", mapping.targetPathAsString())
    }

    @Test
    fun `ClaimMapping mDoc helper should create correct sourceClaimPath for ISO namespace`() {
        val mapping = ClaimMapping.mDoc(
            namespace = "org.iso.18013.5.1",
            elementIdentifier = "given_name",
            targetClaim = "given_name"
        )

        assertEquals(listOf("org.iso.18013.5.1", "given_name"), mapping.sourceClaimPath)
        assertEquals(listOf("given_name"), mapping.targetClaimPath)
        assertEquals("org.iso.18013.5.1.given_name", mapping.sourcePathAsString())
    }

    @Test
    fun `ClaimMapping helper methods should create correct paths`() {
        val simpleMapping = ClaimMapping.simple("given_name", "first_name")
        assertEquals(listOf("given_name"), simpleMapping.sourceClaimPath)
        assertEquals(listOf("first_name"), simpleMapping.targetClaimPath)
        assertEquals("given_name", simpleMapping.sourcePathAsString())
        assertEquals("first_name", simpleMapping.targetPathAsString())

        val identityMapping = ClaimMapping.simple("email")
        assertEquals(listOf("email"), identityMapping.sourceClaimPath)
        assertEquals(listOf("email"), identityMapping.targetClaimPath)

        val nestedMapping = ClaimMapping.nested("address", "street", targetClaim = "street_address")
        assertEquals(listOf("address", "street"), nestedMapping.sourceClaimPath)
        assertEquals(listOf("street_address"), nestedMapping.targetClaimPath)
        assertEquals("address.street", nestedMapping.sourcePathAsString())
        assertEquals("street_address", nestedMapping.targetPathAsString())

        val mdocMapping = ClaimMapping.mDoc(
            namespace = "org.iso.18013.5.1",
            elementIdentifier = "family_name",
            targetClaim = "family_name"
        )
        assertEquals(listOf("org.iso.18013.5.1", "family_name"), mdocMapping.sourceClaimPath)
        assertEquals(listOf("family_name"), mdocMapping.targetClaimPath)
        assertEquals("org.iso.18013.5.1.family_name", mdocMapping.sourcePathAsString())
    }

    @Test
    fun `companion pathAsString should convert path to dot-separated string`() {
        assertEquals("email", ClaimMapping.pathAsString(listOf("email")))
        assertEquals("address.street", ClaimMapping.pathAsString(listOf("address", "street")))
        assertEquals(
            "user.profile.name.first",
            ClaimMapping.pathAsString(listOf("user", "profile", "name", "first"))
        )
        assertEquals(
            "address.city",
            ClaimMapping.pathAsString(listOf("address", "city"), ClaimPathType.SOURCE)
        )
        assertEquals(
            "contact.email",
            ClaimMapping.pathAsString(listOf("contact", "email"), ClaimPathType.TARGET)
        )
        assertEquals("", ClaimMapping.pathAsString(emptyList()))
    }

    @Test
    fun `CredentialMapping targetClaimPaths should return all target paths`() {
        val credentialMapping = CredentialMapping(
            credentialId = "pid",
            claimMappings = listOf(
                ClaimMapping(
                    sourceClaimPath = listOf("given_name"),
                    targetClaimPath = listOf("user", "first_name")
                ),
                ClaimMapping(
                    sourceClaimPath = listOf("family_name"),
                    targetClaimPath = listOf("user", "last_name")
                ),
                ClaimMapping(
                    sourceClaimPath = listOf("email"),
                    targetClaimPath = listOf("contact", "email")
                )
            )
        )

        val targetPaths = credentialMapping.targetClaimPaths()
        assertEquals(3, targetPaths.size)
        assertTrue(targetPaths.contains("user.first_name"))
        assertTrue(targetPaths.contains("user.last_name"))
        assertTrue(targetPaths.contains("contact.email"))
    }

    @Test
    fun `ClaimMappingConfiguration allTargetClaimPaths should aggregate all paths`() {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("given_name"),
                            targetClaimPath = listOf("user", "name")
                        )
                    )
                ),
                CredentialMapping(
                    credentialId = "mdl",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("license_number"),
                            targetClaimPath = listOf("documents", "license", "number")
                        )
                    )
                )
            )
        )

        val allPaths = config.allTargetClaimPaths()
        assertEquals(2, allPaths.size)
        assertTrue(allPaths.contains("user.name"))
        assertTrue(allPaths.contains("documents.license.number"))
    }

    // =============================================================================
    // ToIsoDate Transformation Tests
    // =============================================================================

    private fun toIsoDateConfig(inputFormat: DateInputFormat = DateInputFormat.AUTO) =
        ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("date_value"),
                            targetClaimPath = listOf("iso_date"),
                            transformation = ClaimTransformation.ToIsoDate(inputFormat)
                        )
                    )
                )
            )
        )

    private fun credentialWithDate(dateValue: String) = CredentialWithId(
        credentialId = "pid",
        format = CredentialFormat.SD_JWT_DC,
        payload = "",
        disclosedClaims = buildJsonObject {
            put("date_value", JsonPrimitive(dateValue))
        }
    )

    @Test
    fun `toIsoDate should normalize ISO 8601 instant`() = runTest {
        val result = mappingService.mapClaimsWithConfig(
            listOf(credentialWithDate("2025-01-15T10:30:00Z")),
            toIsoDateConfig(DateInputFormat.ISO_8601)
        )
        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("2025-01-15T10:30:00Z"), result.value.claims["iso_date"])
    }

    @Test
    fun `toIsoDate should convert epoch seconds`() = runTest {
        // 2025-01-15T10:30:00Z = 1736937000 epoch seconds
        val result = mappingService.mapClaimsWithConfig(
            listOf(credentialWithDate("1736937000")),
            toIsoDateConfig(DateInputFormat.EPOCH_SECONDS)
        )
        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("2025-01-15T10:30:00Z"), result.value.claims["iso_date"])
    }

    @Test
    fun `toIsoDate should convert epoch millis`() = runTest {
        // 2025-01-15T10:30:00Z = 1736937000000 epoch millis
        val result = mappingService.mapClaimsWithConfig(
            listOf(credentialWithDate("1736937000000")),
            toIsoDateConfig(DateInputFormat.EPOCH_MILLIS)
        )
        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("2025-01-15T10:30:00Z"), result.value.claims["iso_date"])
    }

    @Test
    fun `toIsoDate should convert date-only string to start of day UTC`() = runTest {
        val result = mappingService.mapClaimsWithConfig(
            listOf(credentialWithDate("2025-01-15")),
            toIsoDateConfig(DateInputFormat.ISO_8601_DATE)
        )
        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("2025-01-15T00:00:00Z"), result.value.claims["iso_date"])
    }

    @Test
    fun `toIsoDate auto should detect ISO 8601 instant`() = runTest {
        val result = mappingService.mapClaimsWithConfig(
            listOf(credentialWithDate("2025-01-15T10:30:00Z")),
            toIsoDateConfig(DateInputFormat.AUTO)
        )
        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("2025-01-15T10:30:00Z"), result.value.claims["iso_date"])
    }

    @Test
    fun `toIsoDate auto should detect date-only string`() = runTest {
        val result = mappingService.mapClaimsWithConfig(
            listOf(credentialWithDate("2025-01-15")),
            toIsoDateConfig(DateInputFormat.AUTO)
        )
        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("2025-01-15T00:00:00Z"), result.value.claims["iso_date"])
    }

    @Test
    fun `toIsoDate auto should detect epoch seconds`() = runTest {
        val result = mappingService.mapClaimsWithConfig(
            listOf(credentialWithDate("1736937000")),
            toIsoDateConfig(DateInputFormat.AUTO)
        )
        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("2025-01-15T10:30:00Z"), result.value.claims["iso_date"])
    }

    @Test
    fun `toIsoDate auto should detect epoch millis`() = runTest {
        val result = mappingService.mapClaimsWithConfig(
            listOf(credentialWithDate("1736937000000")),
            toIsoDateConfig(DateInputFormat.AUTO)
        )
        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("2025-01-15T10:30:00Z"), result.value.claims["iso_date"])
    }

    @Test
    fun `toIsoDate auto should passthrough unrecognized format`() = runTest {
        val result = mappingService.mapClaimsWithConfig(
            listOf(credentialWithDate("not-a-date")),
            toIsoDateConfig(DateInputFormat.AUTO)
        )
        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("not-a-date"), result.value.claims["iso_date"])
    }

    @Test
    fun `toIsoDate default format should be AUTO`() = runTest {
        val config = ClaimMappingConfiguration(
            id = "test-config",
            name = "Test",
            credentialMappings = listOf(
                CredentialMapping(
                    credentialId = "pid",
                    claimMappings = listOf(
                        ClaimMapping(
                            sourceClaimPath = listOf("date_value"),
                            targetClaimPath = listOf("iso_date"),
                            transformation = ClaimTransformation.ToIsoDate()
                        )
                    )
                )
            )
        )
        val result = mappingService.mapClaimsWithConfig(
            listOf(credentialWithDate("2025-01-15T10:30:00Z")),
            config
        )
        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("2025-01-15T10:30:00Z"), result.value.claims["iso_date"])
    }
}
