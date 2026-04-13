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

package com.sphereon.core.defaults.context

import com.sphereon.di.context.IdentityResolutionInput
import com.sphereon.di.context.PrincipalType
import com.sphereon.di.context.ResolutionSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultIdentityResolutionPipelineTest {
    private fun createPipeline(): DefaultIdentityResolutionPipeline {
        val tenantResolvers =
            setOf(
                OidcTenantResolver(),
                StaticTenantResolver(),
                EmailDomainTenantResolver(),
            )
        val principalResolvers =
            setOf(
                OidcPrincipalResolver(),
                StaticPrincipalResolver(),
                EmailPrincipalResolver(),
            )
        return DefaultIdentityResolutionPipeline(tenantResolvers, principalResolvers)
    }

    @Test
    fun tenantFromToken() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        tokenClaims = mapOf("tenant_id" to JsonPrimitive("acme"), "sub" to JsonPrimitive("user-1")),
                    ),
                )
            assertEquals("acme", result.tenantId)
            assertEquals(ResolutionSource.TOKEN, result.metadata.resolvedFrom)
        }

    @Test
    fun tenantFromHeader() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        headers = mapOf("X-Tenant-Id" to "acme"),
                    ),
                )
            assertEquals("acme", result.tenantId)
            assertEquals(ResolutionSource.HEADER, result.metadata.resolvedFrom)
        }

    @Test
    fun headerFallbackWhenTokenHasNoTenantClaim() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        headers = mapOf("X-Tenant-Id" to "header-tenant"),
                        tokenClaims = mapOf("sub" to JsonPrimitive("user-1")), // no tenant_id claim
                    ),
                )
            // Token has no tenant_id, so header provides the tenant
            assertEquals("header-tenant", result.tenantId)
            assertEquals("user-1", result.principalId)
        }

    @Test
    fun principalFromSub() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        tokenClaims = mapOf("sub" to JsonPrimitive("user-123"), "tenant_id" to JsonPrimitive("acme")),
                    ),
                )
            assertEquals("user-123", result.principalId)
            assertEquals(PrincipalType.USER, result.principalType)
        }

    @Test
    fun principalTypeUserFromToken() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        tokenClaims =
                            mapOf(
                                "sub" to JsonPrimitive("user-1"),
                                "email" to JsonPrimitive("user@acme.com"),
                                "tenant_id" to JsonPrimitive("acme"),
                            ),
                    ),
                )
            assertEquals(PrincipalType.USER, result.principalType)
        }

    @Test
    fun principalTypeWorkloadFromClientCredentials() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        tokenClaims =
                            mapOf(
                                "sub" to JsonPrimitive("service-client"),
                                "grant_type" to JsonPrimitive("client_credentials"),
                                "tenant_id" to JsonPrimitive("acme"),
                            ),
                    ),
                )
            assertEquals(PrincipalType.WORKLOAD, result.principalType)
        }

    @Test
    fun principalTypeAnonymousWhenNoData() =
        runTest {
            val pipeline = createPipeline()
            val result = pipeline.resolve(IdentityResolutionInput())
            assertEquals(PrincipalType.ANONYMOUS, result.principalType)
            assertNull(result.tenantId)
            assertNull(result.principalId)
        }

    @Test
    fun metadataCapturesHeaderValues() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        headers = mapOf("X-Tenant-Id" to "header-tenant", "X-Principal-Id" to "header-principal"),
                        tokenClaims = emptyMap(), // no token claims
                    ),
                )
            // Header values are always captured in metadata
            assertEquals("header-tenant", result.metadata.headerTenantId)
            assertEquals("header-principal", result.metadata.headerPrincipalId)
            assertEquals("header-tenant", result.tenantId)
        }

    @Test
    fun issuerAndAudienceExtractedFromToken() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        tokenClaims =
                            mapOf(
                                "sub" to JsonPrimitive("user-1"),
                                "tenant_id" to JsonPrimitive("acme"),
                                "iss" to JsonPrimitive("https://auth.example.com"),
                                "aud" to JsonPrimitive("my-client-id"),
                            ),
                    ),
                )
            assertEquals("https://auth.example.com", result.metadata.issuer)
            assertEquals("my-client-id", result.metadata.audience)
        }

    @Test
    fun principalFromHeaderWhenNoToken() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        headers =
                            mapOf(
                                "X-Tenant-Id" to "acme",
                                "X-Principal-Id" to "service-a",
                            ),
                    ),
                )
            assertEquals("acme", result.tenantId)
            assertEquals("service-a", result.principalId)
            assertEquals(PrincipalType.SERVICE, result.principalType)
        }

    // --- IDP-01 identity fallback regression coverage ---

    @Test
    fun resolvedFromIsTokenWhenTokenResolves() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        tokenClaims = mapOf("tenant_id" to JsonPrimitive("token-tenant"), "sub" to JsonPrimitive("user-1")),
                    ),
                )
            assertEquals("token-tenant", result.tenantId)
            assertEquals("user-1", result.principalId)
            assertEquals(ResolutionSource.TOKEN, result.metadata.resolvedFrom)
            assertEquals(PrincipalType.USER, result.principalType)
        }

    @Test
    fun resolvedFromIsHeaderWhenOnlyHeaderPresent() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        headers = mapOf("X-Tenant-Id" to "header-tenant"),
                    ),
                )
            assertEquals("header-tenant", result.tenantId)
            assertNull(result.principalId)
            assertEquals(ResolutionSource.HEADER, result.metadata.resolvedFrom)
            assertEquals(PrincipalType.ANONYMOUS, result.principalType)
            assertEquals("header-tenant", result.metadata.headerTenantId)
        }

    // --- TEST-01: HOST and PATH resolution source regression coverage ---

    @Test
    fun resolvedFromIsHostWhenOnlyHostPresent() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        hostHeader = "acme.example.com",
                    ),
                )
            assertEquals("acme.example.com", result.tenantId)
            assertEquals(ResolutionSource.HOST, result.metadata.resolvedFrom)
            assertEquals(PrincipalType.ANONYMOUS, result.principalType)
            assertNull(result.principalId)
        }

    @Test
    fun resolvedFromIsPathWhenOnlyPathPresent() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        pathPrefix = "acme",
                    ),
                )
            assertEquals("acme", result.tenantId)
            assertEquals(ResolutionSource.PATH, result.metadata.resolvedFrom)
            assertEquals(PrincipalType.ANONYMOUS, result.principalType)
            assertNull(result.principalId)
        }

    @Test
    fun hostTakesPrecedenceOverPath() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        hostHeader = "host-tenant.example.com",
                        pathPrefix = "path-tenant",
                    ),
                )
            // HOST has higher priority than PATH
            assertEquals("host-tenant.example.com", result.tenantId)
            assertEquals(ResolutionSource.HOST, result.metadata.resolvedFrom)
        }

    @Test
    fun tokenTakesPrecedenceOverHost() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        tokenClaims = mapOf("tenant_id" to JsonPrimitive("token-tenant"), "sub" to JsonPrimitive("user-1")),
                        hostHeader = "host-tenant.example.com",
                    ),
                )
            // TOKEN has higher priority than HOST
            assertEquals("token-tenant", result.tenantId)
            assertEquals(ResolutionSource.TOKEN, result.metadata.resolvedFrom)
        }

    @Test
    fun headerTakesPrecedenceOverHost() =
        runTest {
            val pipeline = createPipeline()
            val result =
                pipeline.resolve(
                    IdentityResolutionInput(
                        headers = mapOf("X-Tenant-Id" to "header-tenant"),
                        hostHeader = "host-tenant.example.com",
                    ),
                )
            // HEADER has higher priority than HOST
            assertEquals("header-tenant", result.tenantId)
            assertEquals(ResolutionSource.HEADER, result.metadata.resolvedFrom)
        }

    @Test
    fun allSourcesMissingReturnsAnonymous() =
        runTest {
            val pipeline = createPipeline()
            val result = pipeline.resolve(IdentityResolutionInput())
            assertNull(result.tenantId)
            assertNull(result.principalId)
            assertEquals(PrincipalType.ANONYMOUS, result.principalType)
            assertEquals(ResolutionSource.UNKNOWN, result.metadata.resolvedFrom)
            assertNull(result.metadata.issuer)
            assertNull(result.metadata.audience)
            assertNull(result.metadata.headerTenantId)
            assertNull(result.metadata.headerPrincipalId)
        }
}
