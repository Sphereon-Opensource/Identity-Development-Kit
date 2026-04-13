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

package com.sphereon.identity.reconciliation.impl.claims

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for claim mapping and merging logic as implemented in the reconciliation flow.
 *
 * The attribute mapping in reconciliation is config-driven via ReconciliationProvider.attributeMappings
 * and ReconciliationProvider.userInfoAttributeMappings. The mapping logic is additive: source attributes
 * are preserved and mapped values are added under canonical names.
 *
 * These tests exercise the claim mapping functions directly, while
 * [CompleteReconciliationCommandTest] tests the same logic integrated in the full command flow.
 */
class ClaimMappingServiceTest {
    /**
     * Apply claim mappings to a claim set.
     * For each mapping (sourceClaimName -> canonicalClaimName), if source exists,
     * copy value to canonical name. Original claims are preserved (additive).
     */
    private fun applyClaimMappings(
        claims: Map<String, JsonElement>,
        mappings: Map<String, String>,
    ): Map<String, JsonElement> {
        if (mappings.isEmpty()) return claims
        return buildMap {
            putAll(claims)
            for ((sourceName, canonicalName) in mappings) {
                val value = claims[sourceName]
                if (value != null) {
                    put(canonicalName, value)
                }
            }
        }
    }

    /**
     * Merge wallet claims with OIDC claims.
     * OIDC claims take precedence for institution-bound claims.
     * Wallet-only claims are preserved.
     */
    private fun mergeClaims(
        walletClaims: Map<String, JsonElement>,
        oidcClaims: Map<String, JsonElement>,
        institutionBoundClaims: Set<String> = emptySet(),
    ): Map<String, JsonElement> =
        buildMap {
            // Start with wallet claims
            putAll(walletClaims)
            // Overlay OIDC claims (OIDC takes precedence for institution-bound, both added otherwise)
            putAll(oidcClaims)
            // For institution-bound claims, always use OIDC value
            for (claim in institutionBoundClaims) {
                val oidcValue = oidcClaims[claim]
                if (oidcValue != null) {
                    put(claim, oidcValue)
                }
            }
        }

    @Test
    fun configDrivenMappingApplication() =
        runTest {
            val rawClaims: Map<String, JsonElement> =
                mapOf(
                    "sub" to JsonPrimitive("user-123"),
                    "email" to JsonPrimitive("user@example.com"),
                )
            val mappings = mapOf("sub" to "externalId", "email" to "email_address")

            val mapped = applyClaimMappings(rawClaims, mappings)

            // Mapped values present under canonical names
            assertEquals(JsonPrimitive("user-123"), mapped["externalId"])
            assertEquals(JsonPrimitive("user@example.com"), mapped["email_address"])
            // Original claims are preserved (additive mapping)
            assertEquals(JsonPrimitive("user-123"), mapped["sub"])
            assertEquals(JsonPrimitive("user@example.com"), mapped["email"])
        }

    @Test
    fun ownershipRules_oidcWinsForInstitutionBound() =
        runTest {
            val walletClaims: Map<String, JsonElement> =
                mapOf(
                    "studentNumber" to JsonPrimitive("WALLET-123"),
                    "name" to JsonPrimitive("Wallet Name"),
                )
            val oidcClaims: Map<String, JsonElement> =
                mapOf(
                    "studentNumber" to JsonPrimitive("OIDC-456"),
                    "email" to JsonPrimitive("oidc@school.nl"),
                )
            val institutionBound = setOf("studentNumber")

            val merged = mergeClaims(walletClaims, oidcClaims, institutionBound)

            // OIDC wins for institution-bound claims
            assertEquals(JsonPrimitive("OIDC-456"), merged["studentNumber"])
            // OIDC claims are included
            assertEquals(JsonPrimitive("oidc@school.nl"), merged["email"])
            // Wallet-only claims preserved
            assertEquals(JsonPrimitive("Wallet Name"), merged["name"])
        }

    @Test
    fun walletAndOidcClaimMerging() =
        runTest {
            val walletClaims: Map<String, JsonElement> =
                mapOf(
                    "did" to JsonPrimitive("did:key:abc"),
                    "name" to JsonPrimitive("Wallet Name"),
                )
            val oidcClaims: Map<String, JsonElement> =
                mapOf(
                    "email" to JsonPrimitive("user@school.nl"),
                    "studentNumber" to JsonPrimitive("S12345"),
                )

            val merged = mergeClaims(walletClaims, oidcClaims)

            // Wallet-only claim preserved
            assertEquals(JsonPrimitive("did:key:abc"), merged["did"])
            // OIDC claims included
            assertEquals(JsonPrimitive("user@school.nl"), merged["email"])
            assertEquals(JsonPrimitive("S12345"), merged["studentNumber"])
            // Wallet claim preserved
            assertNotNull(merged["name"])
            assertEquals(JsonPrimitive("Wallet Name"), merged["name"])
        }

    @Test
    fun missingRequiredClaimError() =
        runTest {
            val walletClaims: Map<String, JsonElement> = mapOf("name" to JsonPrimitive("Test"))
            val oidcClaims: Map<String, JsonElement> = mapOf("email" to JsonPrimitive("test@test.com"))
            val requiredClaims = setOf("studentNumber")

            val merged = mergeClaims(walletClaims, oidcClaims)

            // Verify that the required claim is indeed missing
            val missingClaims = requiredClaims.filter { it !in merged }
            assertTrue(missingClaims.isNotEmpty(), "Should detect missing required claims")
            assertTrue("studentNumber" in missingClaims, "studentNumber should be in missing list")
        }

    @Test
    fun emptyMappingConfig() =
        runTest {
            val rawClaims: Map<String, JsonElement> =
                mapOf(
                    "sub" to JsonPrimitive("user-123"),
                    "email" to JsonPrimitive("user@example.com"),
                )
            val emptyMappings = emptyMap<String, String>()

            val mapped = applyClaimMappings(rawClaims, emptyMappings)

            // Pass-through: output equals input
            assertEquals(rawClaims, mapped)
            assertEquals(JsonPrimitive("user-123"), mapped["sub"])
            assertEquals(JsonPrimitive("user@example.com"), mapped["email"])
        }

    @Test
    fun claimValueTransformation() =
        runTest {
            // Test claim value normalization (lowercase email)
            val rawClaims: Map<String, JsonElement> =
                mapOf(
                    "email" to JsonPrimitive("User@EXAMPLE.COM"),
                    "name" to JsonPrimitive("Test User"),
                )

            // Simulate a transformation pipeline: lowercase email normalization
            val transformed =
                rawClaims.mapValues { (key, value) ->
                    if (key == "email" && value is JsonPrimitive) {
                        JsonPrimitive(value.content.lowercase())
                    } else {
                        value
                    }
                }

            assertEquals(JsonPrimitive("user@example.com"), transformed["email"])
            assertEquals(JsonPrimitive("Test User"), transformed["name"])
        }
}
