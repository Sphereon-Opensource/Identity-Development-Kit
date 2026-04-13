/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.identity.reconciliation.api

import com.sphereon.identity.reconciliation.model.CanonicalAttributeBag
import com.sphereon.identity.reconciliation.model.CanonicalAttributeRule
import com.sphereon.identity.reconciliation.model.CanonicalMergeMode
import com.sphereon.identity.reconciliation.model.AttributeProvenanceSummary
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanonicalAttributeFunctionsTest {

    private fun bag(vararg attributes: Pair<String, String>) = CanonicalAttributeBag(
        attributes = attributes.associate { (k, v) -> k to JsonPrimitive(v) },
        provenance = AttributeProvenanceSummary(),
    )

    @Test
    fun validateRequiredAttributesReturnsEmptyWhenAllPresent() {
        val canonical = bag("given_name" to "Alice", "email" to "alice@example.com")
        val rules = listOf(
            CanonicalAttributeRule("given_name", CanonicalMergeMode.OIDC_WINS, required = true),
            CanonicalAttributeRule("email", CanonicalMergeMode.OIDC_WINS, required = true),
        )
        val violations = validateRequiredAttributes(canonical, rules)
        assertTrue(violations.isEmpty())
    }

    @Test
    fun validateRequiredAttributesDetectsMissingAttribute() {
        val canonical = bag("given_name" to "Alice")
        val rules = listOf(
            CanonicalAttributeRule("given_name", CanonicalMergeMode.OIDC_WINS, required = true),
            CanonicalAttributeRule("email", CanonicalMergeMode.OIDC_ONLY, required = true),
        )
        val violations = validateRequiredAttributes(canonical, rules)
        assertEquals(1, violations.size)
        assertEquals("email", violations[0].canonicalName)
        assertEquals("oidc", violations[0].expectedSourceHint)
    }

    @Test
    fun validateRequiredAttributesIgnoresNonRequiredMissing() {
        val canonical = bag("given_name" to "Alice")
        val rules = listOf(
            CanonicalAttributeRule("given_name", CanonicalMergeMode.OIDC_WINS, required = true),
            CanonicalAttributeRule("birth_date", CanonicalMergeMode.WALLET_ONLY, required = false),
        )
        val violations = validateRequiredAttributes(canonical, rules)
        assertTrue(violations.isEmpty())
    }

    @Test
    fun validateRequiredAttributesSourceHintWallet() {
        val canonical = bag()
        val rules = listOf(
            CanonicalAttributeRule("birth_date", CanonicalMergeMode.WALLET_ONLY, required = true),
        )
        val violations = validateRequiredAttributes(canonical, rules)
        assertEquals("wallet", violations[0].expectedSourceHint)
    }

    @Test
    fun validateRequiredAttributesSourceHintAny() {
        val canonical = bag()
        val rules = listOf(
            CanonicalAttributeRule("given_name", CanonicalMergeMode.OIDC_WINS, required = true),
        )
        val violations = validateRequiredAttributes(canonical, rules)
        assertEquals("any", violations[0].expectedSourceHint)
    }

    @Test
    fun attributesToProjectRespectsProjectFlag() {
        val canonical = bag(
            "given_name" to "Alice",
            "email" to "alice@example.com",
            "birth_date" to "1990-01-01",
        )
        val rules = listOf(
            CanonicalAttributeRule("given_name", CanonicalMergeMode.OIDC_WINS, project = true),
            CanonicalAttributeRule("email", CanonicalMergeMode.OIDC_WINS, project = true),
            CanonicalAttributeRule("birth_date", CanonicalMergeMode.WALLET_ONLY, project = false),
        )
        val projected = attributesToProject(canonical, rules)
        assertEquals(2, projected.size)
        assertTrue(projected.containsKey("given_name"))
        assertTrue(projected.containsKey("email"))
        assertFalse(projected.containsKey("birth_date"))
    }

    @Test
    fun attributesToPersistRespectsPersistFlag() {
        val canonical = bag(
            "given_name" to "Alice",
            "affiliation" to "student",
        )
        val rules = listOf(
            CanonicalAttributeRule("given_name", CanonicalMergeMode.OIDC_WINS, persist = true),
            CanonicalAttributeRule("affiliation", CanonicalMergeMode.OIDC_ONLY, persist = false),
        )
        val persisted = attributesToPersist(canonical, rules)
        assertEquals(1, persisted.size)
        assertTrue(persisted.containsKey("given_name"))
        assertFalse(persisted.containsKey("affiliation"))
    }

    @Test
    fun attributesToProjectIgnoresAttributesNotInBag() {
        val canonical = bag("given_name" to "Alice")
        val rules = listOf(
            CanonicalAttributeRule("given_name", CanonicalMergeMode.OIDC_WINS, project = true),
            CanonicalAttributeRule("email", CanonicalMergeMode.OIDC_WINS, project = true),
        )
        val projected = attributesToProject(canonical, rules)
        assertEquals(1, projected.size)
        assertTrue(projected.containsKey("given_name"))
    }

    @Test
    fun validateRequiredAttributesMultipleMissing() {
        val canonical = bag()
        val rules = listOf(
            CanonicalAttributeRule("given_name", CanonicalMergeMode.OIDC_WINS, required = true),
            CanonicalAttributeRule("email", CanonicalMergeMode.OIDC_WINS, required = true),
            CanonicalAttributeRule("birth_date", CanonicalMergeMode.WALLET_ONLY, required = true),
        )
        val violations = validateRequiredAttributes(canonical, rules)
        assertEquals(3, violations.size)
    }

    @Test
    fun attributesPresentWithPersistAndProjectFlags() {
        val canonical = bag(
            "given_name" to "Alice",
            "email" to "alice@example.com",
            "birth_date" to "1990-01-01",
            "affiliation" to "student",
        )
        val rules = listOf(
            CanonicalAttributeRule("given_name", CanonicalMergeMode.OIDC_WINS, persist = true, project = true),
            CanonicalAttributeRule("email", CanonicalMergeMode.OIDC_WINS, persist = true, project = true),
            CanonicalAttributeRule("birth_date", CanonicalMergeMode.WALLET_ONLY, persist = true, project = false),
            CanonicalAttributeRule("affiliation", CanonicalMergeMode.OIDC_ONLY, persist = false, project = true),
        )
        val persisted = attributesToPersist(canonical, rules)
        val projected = attributesToProject(canonical, rules)

        assertEquals(3, persisted.size)
        assertTrue(persisted.containsKey("given_name"))
        assertTrue(persisted.containsKey("email"))
        assertTrue(persisted.containsKey("birth_date"))
        assertFalse(persisted.containsKey("affiliation"))

        assertEquals(3, projected.size)
        assertTrue(projected.containsKey("given_name"))
        assertTrue(projected.containsKey("email"))
        assertTrue(projected.containsKey("affiliation"))
        assertFalse(projected.containsKey("birth_date"))
    }
}
