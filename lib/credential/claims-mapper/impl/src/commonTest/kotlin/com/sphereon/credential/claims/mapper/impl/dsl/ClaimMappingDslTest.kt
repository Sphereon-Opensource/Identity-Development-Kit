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

package com.sphereon.credential.claims.mapper.impl.dsl

import com.sphereon.credential.claims.mapper.api.dsl.claimMappingConfig
import com.sphereon.credential.claims.mapper.api.dsl.concatenate
import com.sphereon.credential.claims.mapper.api.dsl.lowercase
import com.sphereon.credential.claims.mapper.api.dsl.regexReplace
import com.sphereon.credential.claims.mapper.api.dsl.substring
import com.sphereon.credential.claims.mapper.api.dsl.uppercase
import com.sphereon.credential.claims.mapper.api.model.ClaimTransformation
import com.sphereon.credential.claims.mapper.api.model.CredentialWithId
import com.sphereon.credential.claims.mapper.impl.mapper.ClaimsMappingServiceImpl
import com.sphereon.openid.oid4vp.common.CredentialFormat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the Claim Mapping DSL.
 *
 * These tests demonstrate the DSL usage patterns for creating claim mapping configurations.
 */
class ClaimMappingDslTest {

    private val mappingService = ClaimsMappingServiceImpl.withDefaults()

    @Test
    fun `DSL should create simple identity mappings`() = runTest {
        // DSL usage - clean and readable
        val config = claimMappingConfig("simple-config", "Simple Mapping") {
            credential("pid") {
                map("given_name")
                map("family_name")
                map("email")
            }
        }

        assertEquals("simple-config", config.id)
        assertEquals("Simple Mapping", config.name)
        assertEquals(1, config.credentialMappings.size)
        assertEquals(3, config.credentialMappings[0].claimMappings.size)

        // Verify it works with the service
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
        assertEquals(JsonPrimitive("John"), result.value.claims["given_name"])
        assertEquals(JsonPrimitive("Doe"), result.value.claims["family_name"])
        assertEquals(JsonPrimitive("john@example.com"), result.value.claims["email"])
    }

    @Test
    fun `DSL should create source to target mappings`() = runTest {
        val config = claimMappingConfig("rename-config", "Rename Claims") {
            credential("pid") {
                "given_name" mappedTo "first_name"
                "family_name" mappedTo "last_name"
            }
        }

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
        assertEquals(JsonPrimitive("John"), result.value.claims["first_name"])
        assertEquals(JsonPrimitive("Doe"), result.value.claims["last_name"])
    }

    @Test
    fun `DSL should support nested target paths with target function`() {
        // This test verifies the symmetric API between path() and target()
        val config = claimMappingConfig("target-path-config", "Target Path Claims") {
            credential("pid") {
                // Simple source to nested target
                "given_name" mappedTo target("user", "firstName")
                "family_name" mappedTo target("user", "lastName")

                // Nested source to nested target (symmetric path() and target())
                path("address", "street") mappedTo target("location", "streetAddress")
            }
        }

        val mappings = config.credentialMappings[0].claimMappings

        // Verify simple source to nested target
        assertEquals(listOf("given_name"), mappings[0].sourceClaimPath)
        assertEquals(listOf("user", "firstName"), mappings[0].targetClaimPath)

        assertEquals(listOf("family_name"), mappings[1].sourceClaimPath)
        assertEquals(listOf("user", "lastName"), mappings[1].targetClaimPath)

        // Verify nested source to nested target
        assertEquals(listOf("address", "street"), mappings[2].sourceClaimPath)
        assertEquals(listOf("location", "streetAddress"), mappings[2].targetClaimPath)
    }

    @Test
    fun `DSL should support nested source paths`() = runTest {
        val config = claimMappingConfig("nested-config", "Nested Claims") {
            credential("pid") {
                path("address", "street") mappedTo "street_address"
                path("address", "city") mappedTo "city"
                path("address", "country") mappedTo "country"
            }
        }

        val disclosedClaims = buildJsonObject {
            put("address", buildJsonObject {
                put("street", JsonPrimitive("123 Main St"))
                put("city", JsonPrimitive("Amsterdam"))
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
        assertEquals(JsonPrimitive("123 Main St"), result.value.claims["street_address"])
        assertEquals(JsonPrimitive("Amsterdam"), result.value.claims["city"])
        assertEquals(JsonPrimitive("NL"), result.value.claims["country"])
    }

    @Test
    fun `DSL should support mDoc namespace paths`() {
        val config = claimMappingConfig("mdoc-config", "mDoc Claims") {
            credential("mdl") {
                mDoc("org.iso.18013.5.1", "family_name") mappedTo "family_name"
                mDoc("org.iso.18013.5.1", "given_name") mappedTo "given_name"
                mDoc("org.iso.18013.5.1", "birth_date") mappedTo "birthdate"
            }
        }

        val mapping = config.credentialMappings[0].claimMappings[0]
        assertEquals(listOf("org.iso.18013.5.1", "family_name"), mapping.sourceClaimPath)
        assertEquals(listOf("family_name"), mapping.targetClaimPath)
    }

    @Test
    fun `DSL should support uppercase transformation`() = runTest {
        val config = claimMappingConfig("transform-config", "Transform Claims") {
            credential("pid") {
                "given_name" mappedTo "upper_name" using uppercase()
            }
        }

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
        assertEquals(JsonPrimitive("JOHN"), result.value.claims["upper_name"])
    }

    @Test
    fun `DSL should support lowercase transformation`() = runTest {
        val config = claimMappingConfig("transform-config", "Transform Claims") {
            credential("pid") {
                "email" mappedTo "email" using lowercase()
            }
        }

        val disclosedClaims = buildJsonObject {
            put("email", JsonPrimitive("John.Doe@EXAMPLE.COM"))
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
    fun `DSL should support concatenate transformation`() = runTest {
        val config = claimMappingConfig("concat-config", "Concatenate Claims") {
            credential("pid") {
                "given_name" mappedTo "full_name" using concatenate(" ") {
                    suffix("family_name")
                }
            }
        }

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
        assertEquals(JsonPrimitive("John Doe"), result.value.claims["full_name"])
    }

    @Test
    fun `DSL should support concatenate with prefix and suffix`() = runTest {
        val config = claimMappingConfig("concat-full-config", "Full Concatenate") {
            credential("pid") {
                "given_name" mappedTo "formal_name" using concatenate(" ") {
                    prefix("title")
                    suffix("family_name")
                    suffix("suffix")
                }
            }
        }

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
        assertEquals(JsonPrimitive("Dr. John Doe Jr."), result.value.claims["formal_name"])
    }

    @Test
    fun `DSL should support substring transformation`() = runTest {
        val config = claimMappingConfig("substring-config", "Substring Claims") {
            credential("pid") {
                "birth_date" mappedTo "birth_year" using substring(0, 4)
            }
        }

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
    fun `DSL should support regex replace transformation`() = runTest {
        val config = claimMappingConfig("regex-config", "Regex Claims") {
            credential("pid") {
                "phone" mappedTo "phone_digits" using regexReplace("[^0-9]", "")
            }
        }

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
    fun `DSL should support optional credentials`() = runTest {
        val config = claimMappingConfig("optional-config", "Optional Credentials") {
            credential("pid") {
                "given_name" mappedTo "given_name"
            }
            credential("mdl", optional = true) {
                "license_number" mappedTo "license"
            }
        }

        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
        }

        val credential = CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims
        )

        // Only provide PID, not MDL (which is optional)
        val result = mappingService.mapClaimsWithConfig(listOf(credential), config)

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("John"), result.value.claims["given_name"])
        assertTrue(result.value.skippedOptionalCredentials.contains("mdl"))
    }

    @Test
    fun `DSL should support priority`() = runTest {
        val config = claimMappingConfig("priority-config", "Priority Claims") {
            credential("pid") {
                "given_name" mappedTo "name" priority 10
            }
            credential("mdl") {
                "first_name" mappedTo "name" priority 5
            }
        }

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
        // PID has higher priority
        assertEquals(JsonPrimitive("John"), result.value.claims["name"])
    }

    @Test
    fun `DSL should support default values`() = runTest {
        val config = claimMappingConfig("default-config", "Default Values") {
            credential("pid") {
                "given_name" mappedTo "given_name"
                "locale" mappedTo "locale" default "en-US"
            }
        }

        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
            // locale is NOT present
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
        assertEquals(JsonPrimitive("en-US"), result.value.claims["locale"])
        assertTrue(result.value.appliedDefaults.contains("locale"))
    }

    @Test
    fun `DSL should support description and queryId`() {
        val config = claimMappingConfig("full-config", "Full Configuration") {
            description = "Maps PID claims to OIDC claims for login"
            queryId = "login-query"

            credential("pid") {
                "given_name" mappedTo "given_name"
            }
        }

        assertEquals("full-config", config.id)
        assertEquals("Full Configuration", config.name)
        assertEquals("Maps PID claims to OIDC claims for login", config.description)
        assertEquals("login-query", config.queryId)
    }

    @Test
    fun `DSL should support detailed mapping builder`() = runTest {
        val config = claimMappingConfig("detailed-config", "Detailed Mapping") {
            credential("pid") {
                mapping {
                    from("given_name")
                    to("name")
                    priority = 10
                    required = false
                    defaultValue = JsonPrimitive("Unknown")
                    transform { uppercase() }
                }
            }
        }

        val mapping = config.credentialMappings[0].claimMappings[0]
        assertEquals(listOf("given_name"), mapping.sourceClaimPath)
        assertEquals(listOf("name"), mapping.targetClaimPath)
        assertEquals(10, mapping.priority)
        assertEquals(false, mapping.required)
        assertEquals(JsonPrimitive("Unknown"), mapping.defaultValue)
        assertEquals(ClaimTransformation.Uppercase, mapping.transformation)
    }

    @Test
    fun `DSL should support complex real-world configuration`() = runTest {
        // This demonstrates a real-world OIDC claim mapping configuration
        val config = claimMappingConfig("oidc-login", "OIDC Login Claims") {
            description = "Maps verifiable credentials to OIDC claims for authentication"
            queryId = "oidc-login-query"

            credential("pid") {
                // Identity claims
                "given_name" mappedTo "given_name"
                "family_name" mappedTo "family_name"
                "given_name" mappedTo "name" using concatenate(" ") {
                    suffix("family_name")
                }

                // Contact claims
                "email" mappedTo "email" using lowercase()
                "phone_number" mappedTo "phone_number"

                // Address claims from nested structure
                path("address", "street_address") mappedTo "address.street_address"
                path("address", "locality") mappedTo "address.locality"
                path("address", "region") mappedTo "address.region"
                path("address", "postal_code") mappedTo "address.postal_code"
                path("address", "country") mappedTo "address.country"

                // Date claims
                "birth_date" mappedTo "birthdate"
                "birth_date" mappedTo "birth_year" using substring(0, 4)

                // Locale with default
                "locale" mappedTo "locale" default "en"
            }

            credential("mdl", optional = true) {
                // mDoc claims from mobile driver's license
                mDoc("org.iso.18013.5.1", "document_number") mappedTo "drivers_license_number"
                mDoc("org.iso.18013.5.1", "expiry_date") mappedTo "drivers_license_expiry"
            }
        }

        assertEquals("oidc-login", config.id)
        assertEquals(2, config.credentialMappings.size)
        assertEquals(13, config.credentialMappings[0].claimMappings.size)
        assertEquals(2, config.credentialMappings[1].claimMappings.size)
        assertTrue(config.credentialMappings[1].optional)
    }
}
