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

import com.sphereon.openid.oid4vp.dcql.DcqlClaimQuery
import com.sphereon.openid.oid4vp.dcql.ClaimsPathPointer
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.mdocMeta
import com.sphereon.openid.oid4vp.dcql.sdJwtVcMeta
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for scope-to-DCQL resolution per OpenID4VP 1.0 Section 5.5.
 */
class ScopeToDcqlTest {
    // Sample DCQL queries for testing
    private val identityQuery =
        DcqlQuery(
            credentials =
                listOf(
                    DcqlCredentialQuery(
                        id = "identity_credential",
                        format = "dc+sd-jwt",
                        meta = sdJwtVcMeta("urn:test:identity"),
                        claims =
                            listOf(
                                DcqlClaimQuery(path = ClaimsPathPointer(listOf(JsonPrimitive("given_name")))),
                                DcqlClaimQuery(path = ClaimsPathPointer(listOf(JsonPrimitive("family_name")))),
                            ),
                    ),
                ),
        )

    private val diplomaQuery =
        DcqlQuery(
            credentials =
                listOf(
                    DcqlCredentialQuery(
                        id = "diploma_credential",
                        format = "dc+sd-jwt",
                        meta = sdJwtVcMeta("urn:test:diploma"),
                        claims =
                            listOf(
                                DcqlClaimQuery(path = ClaimsPathPointer(listOf(JsonPrimitive("degree")))),
                                DcqlClaimQuery(path = ClaimsPathPointer(listOf(JsonPrimitive("university")))),
                            ),
                    ),
                ),
        )

    // =========================================================================
    // ScopeDefinition Tests
    // =========================================================================

    @Test
    fun scopeDefinitionShouldHoldScopeValueAndDcqlQuery() {
        val definition =
            ScopeDefinition(
                scopeValue = "com.example.identity",
                description = "Identity credential presentation",
                dcqlQuery = identityQuery,
            )

        assertEquals("com.example.identity", definition.scopeValue)
        assertEquals("Identity credential presentation", definition.description)
        assertNotNull(definition.dcqlQuery)
        assertEquals(1, definition.dcqlQuery.credentials.size)
    }

    @Test
    fun scopeDefinitionBuilderShouldWorkCorrectly() {
        val definition =
            buildScopeDefinition {
                scopeValue("com.example.diploma")
                description("Diploma credential")
                dcqlQuery(diplomaQuery)
            }

        assertEquals("com.example.diploma", definition.scopeValue)
        assertEquals("Diploma credential", definition.description)
        assertEquals(
            "diploma_credential",
            definition.dcqlQuery.credentials
                ?.first()
                ?.id,
        )
    }

    // =========================================================================
    // ScopeRegistry Tests
    // =========================================================================

    @Test
    fun scopeRegistryShouldRegisterAndRetrieveScopeDefinitions() {
        val registry =
            ScopeRegistry()
                .register(ScopeDefinition("com.example.identity", dcqlQuery = identityQuery))
                .register(ScopeDefinition("com.example.diploma", dcqlQuery = diplomaQuery))

        assertTrue(registry.contains("com.example.identity"))
        assertTrue(registry.contains("com.example.diploma"))
        assertFalse(registry.contains("unknown"))

        val definition = registry.get("com.example.identity")
        assertNotNull(definition)
        assertEquals(
            "identity_credential",
            definition.dcqlQuery.credentials
                ?.first()
                ?.id,
        )
    }

    @Test
    fun scopeRegistryBuilderShouldWorkCorrectly() {
        val registry =
            buildScopeRegistry {
                scope {
                    scopeValue("com.example.identity")
                    dcqlQuery(identityQuery)
                }
                scope {
                    scopeValue("com.example.diploma")
                    dcqlQuery(diplomaQuery)
                }
            }

        assertEquals(2, registry.size)
        assertTrue(registry.contains("com.example.identity"))
        assertTrue(registry.contains("com.example.diploma"))
    }

    @Test
    fun scopeRegistryShouldUnregisterScopeDefinitions() {
        val registry =
            ScopeRegistry()
                .register(ScopeDefinition("com.example.identity", dcqlQuery = identityQuery))

        assertTrue(registry.contains("com.example.identity"))

        registry.unregister("com.example.identity")

        assertFalse(registry.contains("com.example.identity"))
    }

    // =========================================================================
    // ScopeResolver Tests
    // =========================================================================

    @Test
    fun scopeResolverShouldResolveSingleScopeToDcqlQuery() {
        val registry =
            ScopeRegistry()
                .register(ScopeDefinition("com.example.identity", dcqlQuery = identityQuery))

        val resolver = ScopeResolver(registry)
        val result = resolver.resolve("com.example.identity")

        assertTrue(result.fullyResolved)
        assertEquals(listOf("com.example.identity"), result.resolvedScopes)
        assertTrue(result.unresolvedScopes.isEmpty())
        assertNotNull(result.dcqlQuery)
        assertEquals(1, result.dcqlQuery?.credentials?.size)
    }

    @Test
    fun scopeResolverShouldResolveMultipleScopesAndMergeDcqlQueries() {
        val registry =
            ScopeRegistry()
                .register(ScopeDefinition("com.example.identity", dcqlQuery = identityQuery))
                .register(ScopeDefinition("com.example.diploma", dcqlQuery = diplomaQuery))

        val resolver = ScopeResolver(registry)
        val result = resolver.resolve("com.example.identity com.example.diploma")

        assertTrue(result.fullyResolved)
        assertEquals(2, result.resolvedScopes.size)
        assertTrue(result.unresolvedScopes.isEmpty())
        assertNotNull(result.dcqlQuery)

        // Merged query should have credentials from both
        assertEquals(2, result.dcqlQuery?.credentials?.size)
        val credentialIds = result.dcqlQuery?.credentials?.map { it.id }
        assertTrue(credentialIds?.contains("identity_credential") == true)
        assertTrue(credentialIds?.contains("diploma_credential") == true)
    }

    @Test
    fun scopeResolverShouldHandleUnresolvedScopesLikeOpenid() {
        val registry =
            ScopeRegistry()
                .register(ScopeDefinition("com.example.identity", dcqlQuery = identityQuery))

        val resolver = ScopeResolver(registry)
        val result = resolver.resolve("openid com.example.identity profile")

        assertTrue(result.partiallyResolved)
        assertFalse(result.fullyResolved) // openid and profile were not resolved
        assertEquals(listOf("com.example.identity"), result.resolvedScopes)
        assertEquals(listOf("openid", "profile"), result.unresolvedScopes)
        assertNotNull(result.dcqlQuery)
    }

    @Test
    fun scopeResolverShouldReturnNullDcqlForUnrecognizedScopes() {
        val registry = ScopeRegistry.empty()

        val resolver = ScopeResolver(registry)
        val result = resolver.resolve("openid profile")

        assertFalse(result.partiallyResolved)
        assertFalse(result.fullyResolved)
        assertTrue(result.resolvedScopes.isEmpty())
        assertEquals(listOf("openid", "profile"), result.unresolvedScopes)
        assertNull(result.dcqlQuery)
    }

    @Test
    fun scopeResolverShouldHandleNullOrBlankScopeStrings() {
        val registry = ScopeRegistry()
        val resolver = ScopeResolver(registry)

        val resultNull = resolver.resolve(null)
        assertNull(resultNull.dcqlQuery)
        assertTrue(resultNull.resolvedScopes.isEmpty())
        assertTrue(resultNull.unresolvedScopes.isEmpty())

        val resultBlank = resolver.resolve("")
        assertNull(resultBlank.dcqlQuery)
        assertTrue(resultBlank.resolvedScopes.isEmpty())
        assertTrue(resultBlank.unresolvedScopes.isEmpty())
    }

    @Test
    fun scopeResolverCanResolveShouldCheckRegistry() {
        val registry =
            ScopeRegistry()
                .register(ScopeDefinition("com.example.identity", dcqlQuery = identityQuery))

        val resolver = ScopeResolver(registry)

        assertTrue(resolver.canResolve("com.example.identity"))
        assertFalse(resolver.canResolve("unknown.scope"))
    }

    // =========================================================================
    // Duplicate Credential ID Validation Tests
    // =========================================================================

    @Test
    fun scopeResolverShouldDetectDuplicateCredentialIdsAcrossScopes() {
        // Create two scope definitions with the same credential ID
        val query1 =
            DcqlQuery(
                credentials = listOf(DcqlCredentialQuery(id = "same_id", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:same"))),
            )
        val query2 =
            DcqlQuery(
                credentials = listOf(DcqlCredentialQuery(id = "same_id", format = "mso_mdoc", meta = mdocMeta("org.example.same"))),
            )

        val registry =
            ScopeRegistry()
                .register(ScopeDefinition("scope1", dcqlQuery = query1))
                .register(ScopeDefinition("scope2", dcqlQuery = query2))

        val resolver = ScopeResolver(registry)
        val errors = resolver.validateScopeCombination(listOf("scope1", "scope2"))

        assertEquals(1, errors.size)
        val error = errors.first()
        assertTrue(error is ScopeResolutionError.DuplicateCredentialId)
        assertEquals("same_id", (error as ScopeResolutionError.DuplicateCredentialId).credentialId)
        assertTrue(error.scopes.containsAll(listOf("scope1", "scope2")))
    }

    @Test
    fun scopeResolverShouldAllowUniqueCredentialIdsAcrossScopes() {
        val registry =
            ScopeRegistry()
                .register(ScopeDefinition("com.example.identity", dcqlQuery = identityQuery))
                .register(ScopeDefinition("com.example.diploma", dcqlQuery = diplomaQuery))

        val resolver = ScopeResolver(registry)
        val errors =
            resolver.validateScopeCombination(
                listOf("com.example.identity", "com.example.diploma"),
            )

        assertTrue(errors.isEmpty())
    }

    // =========================================================================
    // DCQL Query Merging Tests
    // =========================================================================

    @Test
    fun mergeDcqlQueriesShouldCombineCredentialsFromMultipleQueries() {
        val merged = ScopeResolver.mergeDcqlQueries(listOf(identityQuery, diplomaQuery))

        assertEquals(2, merged.credentials.size)
        assertNull(merged.credential_sets) // Neither source had credential_sets
    }

    @Test
    fun mergeDcqlQueriesShouldReturnSingleQueryAsIs() {
        val merged = ScopeResolver.mergeDcqlQueries(listOf(identityQuery))

        assertEquals(identityQuery, merged)
    }
}
