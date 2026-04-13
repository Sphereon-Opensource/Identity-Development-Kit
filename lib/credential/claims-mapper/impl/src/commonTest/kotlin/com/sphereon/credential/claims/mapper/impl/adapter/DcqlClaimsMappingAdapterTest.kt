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
 *
 */

package com.sphereon.credential.claims.mapper.impl.adapter

import com.sphereon.credential.claims.mapper.api.model.ClaimMapping
import com.sphereon.credential.claims.mapper.api.model.ClaimMappingConfiguration
import com.sphereon.credential.claims.mapper.api.model.ClaimTransformation
import com.sphereon.credential.claims.mapper.api.model.CredentialMapping
import com.sphereon.credential.claims.mapper.api.model.CredentialWithId
import com.sphereon.credential.claims.mapper.impl.store.InMemoryClaimMappingConfigurationStore
import com.sphereon.openid.oid4vp.common.CredentialFormat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [DcqlClaimsMappingAdapterImpl].
 *
 * This test class focuses on DCQL-specific functionality:
 * - Looking up configurations by DCQL query ID
 * - Mapping claims using DCQL query ID
 * - Error handling for missing DCQL configurations
 */
class DcqlClaimsMappingAdapterTest {
    private lateinit var store: InMemoryClaimMappingConfigurationStore
    private lateinit var dcqlAdapter: DcqlClaimsMappingAdapterImpl

    @BeforeTest
    fun setup() {
        store = InMemoryClaimMappingConfigurationStore()
        dcqlAdapter = DcqlClaimsMappingAdapterImpl.withDefaults(store)
    }

    private fun createTestConfig(
        id: String = "test-config",
        dcqlQueryId: String = "test-query",
    ): ClaimMappingConfiguration =
        ClaimMappingConfiguration(
            id = id,
            name = "Test Configuration",
            credentialMappings =
                listOf(
                    CredentialMapping(
                        credentialId = "pid",
                        claimMappings =
                            listOf(
                                ClaimMapping(
                                    sourceClaimPath = listOf("given_name"),
                                    targetClaimPath = listOf("given_name"),
                                ),
                                ClaimMapping(
                                    sourceClaimPath = listOf("family_name"),
                                    targetClaimPath = listOf("family_name"),
                                ),
                            ),
                    ),
                ),
            queryId = dcqlQueryId,
        )

    private fun createTestCredential(): CredentialWithId {
        val disclosedClaims =
            buildJsonObject {
                put("given_name", JsonPrimitive("John"))
                put("family_name", JsonPrimitive("Doe"))
            }

        return CredentialWithId(
            credentialId = "pid",
            format = CredentialFormat.SD_JWT_DC,
            payload = "",
            disclosedClaims = disclosedClaims,
        )
    }

    // =============================================================================
    // mapClaimsByQueryId Tests
    // =============================================================================

    @Test
    fun `mapClaimsByQueryId should find config by query ID and map claims`() =
        runTest {
            val config = createTestConfig(dcqlQueryId = "login-query")
            store.save(config)

            val credential = createTestCredential()
            val result = dcqlAdapter.mapClaimsByQueryId(listOf(credential), "login-query")

            assertTrue(result.isOk)
            assertEquals(JsonPrimitive("John"), result.value.claims["given_name"])
            assertEquals(JsonPrimitive("Doe"), result.value.claims["family_name"])
        }

    @Test
    fun `mapClaimsByQueryId should fail for non-existent query ID`() =
        runTest {
            val credential = createTestCredential()
            val result = dcqlAdapter.mapClaimsByQueryId(listOf(credential), "non-existent-query")

            assertTrue(result.isErr)
            assertTrue(result.error.code.contains("CONFIGURATION_NOT_FOUND"))
        }

    @Test
    fun `mapClaimsByQueryId should apply transformations`() =
        runTest {
            val config =
                ClaimMappingConfiguration(
                    id = "transform-config",
                    name = "Transform Test",
                    credentialMappings =
                        listOf(
                            CredentialMapping(
                                credentialId = "pid",
                                claimMappings =
                                    listOf(
                                        ClaimMapping(
                                            sourceClaimPath = listOf("given_name"),
                                            targetClaimPath = listOf("full_name"),
                                            transformation =
                                                ClaimTransformation.Concatenate(
                                                    separator = " ",
                                                    suffixPaths = listOf(listOf("family_name")),
                                                ),
                                        ),
                                    ),
                            ),
                        ),
                    queryId = "transform-query",
                )
            store.save(config)

            val credential = createTestCredential()
            val result = dcqlAdapter.mapClaimsByQueryId(listOf(credential), "transform-query")

            assertTrue(result.isOk)
            assertEquals(JsonPrimitive("John Doe"), result.value.claims["full_name"])
        }

    @Test
    fun `mapClaimsByQueryId should handle multiple credentials`() =
        runTest {
            val config =
                ClaimMappingConfiguration(
                    id = "multi-cred-config",
                    name = "Multi Credential Test",
                    credentialMappings =
                        listOf(
                            CredentialMapping(
                                credentialId = "pid",
                                claimMappings =
                                    listOf(
                                        ClaimMapping(
                                            sourceClaimPath = listOf("given_name"),
                                            targetClaimPath = listOf("given_name"),
                                        ),
                                    ),
                            ),
                            CredentialMapping(
                                credentialId = "mdl",
                                claimMappings =
                                    listOf(
                                        ClaimMapping(
                                            sourceClaimPath = listOf("license_number"),
                                            targetClaimPath = listOf("license"),
                                        ),
                                    ),
                            ),
                        ),
                    queryId = "multi-cred-query",
                )
            store.save(config)

            val pidClaims =
                buildJsonObject {
                    put("given_name", JsonPrimitive("John"))
                }
            val mdlClaims =
                buildJsonObject {
                    put("license_number", JsonPrimitive("DL-12345"))
                }

            val credentials =
                listOf(
                    CredentialWithId(
                        credentialId = "pid",
                        format = CredentialFormat.SD_JWT_DC,
                        payload = "",
                        disclosedClaims = pidClaims,
                    ),
                    CredentialWithId(
                        credentialId = "mdl",
                        format = CredentialFormat.SD_JWT_DC,
                        payload = "",
                        disclosedClaims = mdlClaims,
                    ),
                )

            val result = dcqlAdapter.mapClaimsByQueryId(credentials, "multi-cred-query")

            assertTrue(result.isOk)
            assertEquals(JsonPrimitive("John"), result.value.claims["given_name"])
            assertEquals(JsonPrimitive("DL-12345"), result.value.claims["license"])
            assertTrue(result.value.sourceCredentialIds.contains("pid"))
            assertTrue(result.value.sourceCredentialIds.contains("mdl"))
        }

    @Test
    fun `mapClaimsByQueryId should handle optional credentials`() =
        runTest {
            val config =
                ClaimMappingConfiguration(
                    id = "optional-config",
                    name = "Optional Credential Test",
                    credentialMappings =
                        listOf(
                            CredentialMapping(
                                credentialId = "pid",
                                optional = false,
                                claimMappings =
                                    listOf(
                                        ClaimMapping(
                                            sourceClaimPath = listOf("given_name"),
                                            targetClaimPath = listOf("given_name"),
                                        ),
                                    ),
                            ),
                            CredentialMapping(
                                credentialId = "mdl",
                                optional = true,
                                claimMappings =
                                    listOf(
                                        ClaimMapping(
                                            sourceClaimPath = listOf("license_number"),
                                            targetClaimPath = listOf("license"),
                                        ),
                                    ),
                            ),
                        ),
                    queryId = "optional-query",
                )
            store.save(config)

            val credential = createTestCredential()
            val result = dcqlAdapter.mapClaimsByQueryId(listOf(credential), "optional-query")

            assertTrue(result.isOk)
            assertEquals(JsonPrimitive("John"), result.value.claims["given_name"])
            assertTrue(result.value.skippedOptionalCredentials.contains("mdl"))
        }

    // =============================================================================
    // Query ID Index Tests
    // =============================================================================

    @Test
    fun `mapClaimsByQueryId should work after query ID is updated`() =
        runTest {
            val config = createTestConfig(dcqlQueryId = "old-query")
            store.save(config)

            val credential = createTestCredential()

            // First verify the old query ID works
            val result1 = dcqlAdapter.mapClaimsByQueryId(listOf(credential), "old-query")
            assertTrue(result1.isOk)

            // Update the config with a new query ID
            val updatedConfig = config.copy(queryId = "new-query")
            store.save(updatedConfig)

            // Old query ID should no longer work
            val result2 = dcqlAdapter.mapClaimsByQueryId(listOf(credential), "old-query")
            assertTrue(result2.isErr)

            // New query ID should work
            val result3 = dcqlAdapter.mapClaimsByQueryId(listOf(credential), "new-query")
            assertTrue(result3.isOk)
            assertEquals(JsonPrimitive("John"), result3.value.claims["given_name"])
        }

    @Test
    fun `mapClaimsByQueryId should fail after config is deleted`() =
        runTest {
            val config = createTestConfig(dcqlQueryId = "delete-query")
            store.save(config)

            val credential = createTestCredential()

            // First verify it works
            val result1 = dcqlAdapter.mapClaimsByQueryId(listOf(credential), "delete-query")
            assertTrue(result1.isOk)

            // Delete the config
            store.delete(config.id)

            // Query should now fail
            val result2 = dcqlAdapter.mapClaimsByQueryId(listOf(credential), "delete-query")
            assertTrue(result2.isErr)
            assertTrue(result2.error.code.contains("CONFIGURATION_NOT_FOUND"))
        }

    // =============================================================================
    // Factory Method Tests
    // =============================================================================

    @Test
    fun `withDefaults should create working instance`() =
        runTest {
            val customStore = InMemoryClaimMappingConfigurationStore()
            val adapter = DcqlClaimsMappingAdapterImpl.withDefaults(customStore)

            val config = createTestConfig(dcqlQueryId = "factory-query")
            customStore.save(config)

            val credential = createTestCredential()
            val result = adapter.mapClaimsByQueryId(listOf(credential), "factory-query")

            assertTrue(result.isOk)
            assertEquals(JsonPrimitive("John"), result.value.claims["given_name"])
        }

    @Test
    fun `adapter with custom mapping service should work`() =
        runTest {
            val customStore = InMemoryClaimMappingConfigurationStore()
            val customMappingService =
                com.sphereon.credential.claims.mapper.impl.mapper.ClaimsMappingServiceImpl
                    .withDefaults()
            val adapter = DcqlClaimsMappingAdapterImpl(customMappingService, customStore)

            val config = createTestConfig(dcqlQueryId = "custom-query")
            customStore.save(config)

            val credential = createTestCredential()
            val result = adapter.mapClaimsByQueryId(listOf(credential), "custom-query")

            assertTrue(result.isOk)
            assertEquals(JsonPrimitive("John"), result.value.claims["given_name"])
        }

    // =============================================================================
    // Real-world Scenario Tests
    // =============================================================================

    @Test
    fun `should support typical OIDC login flow with DCQL`() =
        runTest {
            // This simulates a real-world OIDC login flow using DCQL
            val loginConfig =
                ClaimMappingConfiguration(
                    id = "oidc-login-config",
                    name = "OIDC Login Claims",
                    description = "Maps PID claims to OIDC claims for login",
                    credentialMappings =
                        listOf(
                            CredentialMapping(
                                credentialId = "eu.europa.ec.eudi.pid.1",
                                claimMappings =
                                    listOf(
                                        ClaimMapping.simple("given_name"),
                                        ClaimMapping.simple("family_name"),
                                        ClaimMapping(
                                            sourceClaimPath = listOf("given_name"),
                                            targetClaimPath = listOf("name"),
                                            transformation =
                                                ClaimTransformation.Concatenate(
                                                    separator = " ",
                                                    suffixPaths = listOf(listOf("family_name")),
                                                ),
                                        ),
                                        ClaimMapping(
                                            sourceClaimPath = listOf("email"),
                                            targetClaimPath = listOf("email"),
                                            transformation = ClaimTransformation.Lowercase,
                                        ),
                                    ),
                            ),
                        ),
                    queryId = "oidc-login-v1",
                )
            store.save(loginConfig)

            val disclosedClaims =
                buildJsonObject {
                    put("given_name", JsonPrimitive("John"))
                    put("family_name", JsonPrimitive("Doe"))
                    put("email", JsonPrimitive("John.Doe@Example.COM"))
                }

            val credential =
                CredentialWithId(
                    credentialId = "eu.europa.ec.eudi.pid.1",
                    format = CredentialFormat.SD_JWT_DC,
                    payload = "",
                    disclosedClaims = disclosedClaims,
                )

            val result = dcqlAdapter.mapClaimsByQueryId(listOf(credential), "oidc-login-v1")

            assertTrue(result.isOk)
            assertEquals(JsonPrimitive("John"), result.value.claims["given_name"])
            assertEquals(JsonPrimitive("Doe"), result.value.claims["family_name"])
            assertEquals(JsonPrimitive("John Doe"), result.value.claims["name"])
            assertEquals(JsonPrimitive("john.doe@example.com"), result.value.claims["email"])
        }
}
