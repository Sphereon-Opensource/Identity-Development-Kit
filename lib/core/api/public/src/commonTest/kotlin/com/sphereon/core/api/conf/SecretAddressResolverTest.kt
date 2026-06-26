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

package com.sphereon.core.api.conf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecretAddressResolverTest {
    private val resolver = DefaultSecretAddressResolver()

    @Test
    fun labelStrategyShardsTenantsApartOnTheEnvFloor() {
        val acme =
            resolver.physicalAddress(
                logicalKey = "db.password",
                providerType = "env",
                scope = ConfigLevel.TENANT,
                scopeIdentifier = "acme",
                instanceId = null,
                strategy = PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX,
            )
        val globex =
            resolver.physicalAddress(
                logicalKey = "db.password",
                providerType = "env",
                scope = ConfigLevel.TENANT,
                scopeIdentifier = "globex",
                instanceId = null,
                strategy = PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX,
            )

        // Segments are joined by `__`; the key's own `.` delimiter collapses to a single `_`,
        // so the tenant shard (`__`) stays unambiguous against the in-key delimiter (`_`).
        assertEquals("ACME__DB_PASSWORD", acme)
        assertEquals("GLOBEX__DB_PASSWORD", globex)
        assertNotEquals(acme, globex)
    }

    @Test
    fun envLabelIsStableAcrossReadAndWrite() {
        fun addr() =
            resolver.physicalAddress(
                logicalKey = "db.password",
                providerType = "env",
                scope = ConfigLevel.TENANT,
                scopeIdentifier = "acme",
                instanceId = "issuer-1",
                strategy = PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX,
            )
        assertEquals(addr(), addr())
        assertEquals("ACME__ISSUER_1__DB_PASSWORD", addr())
    }

    @Test
    fun awsLabelIsNotUppercasedButUnderscoreDelimited() {
        // AWS secret names are case-sensitive; the underscore label keeps the normalized key's case.
        val address =
            resolver.physicalAddress(
                logicalKey = "db.password",
                providerType = "aws",
                scope = ConfigLevel.TENANT,
                scopeIdentifier = "acme",
                instanceId = null,
                strategy = PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX,
            )
        assertEquals("acme__db_password", address)
    }

    @Test
    fun pathStrategyProducesTenantInstancePath() {
        val address =
            resolver.physicalAddress(
                logicalKey = "db.password",
                providerType = "vault",
                scope = ConfigLevel.TENANT,
                scopeIdentifier = "acme",
                instanceId = "issuer-1",
                strategy = PartitionStrategy.TENANT_PATH_INSTANCE_PATH,
            )
        assertEquals("tenants/acme/issuer-1/db/password", address)
    }

    @Test
    fun pathStrategyWithoutInstanceOmitsInstanceSegment() {
        val address =
            resolver.physicalAddress(
                logicalKey = "db.password",
                providerType = "vault",
                scope = ConfigLevel.TENANT,
                scopeIdentifier = "acme",
                instanceId = null,
                strategy = PartitionStrategy.TENANT_PATH_INSTANCE_PATH,
            )
        assertEquals("tenants/acme/db/password", address)
    }

    @Test
    fun appScopeIsNotSharded() {
        val label =
            resolver.physicalAddress(
                logicalKey = "db.password",
                providerType = "env",
                scope = ConfigLevel.APP,
                scopeIdentifier = null,
                instanceId = null,
                strategy = PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX,
            )
        // Un-sharded, but still rendered in the env backend's native style (UPPER `_`).
        assertEquals("DB_PASSWORD", label)

        val path =
            resolver.physicalAddress(
                logicalKey = "db.password",
                providerType = "vault",
                scope = ConfigLevel.APP,
                scopeIdentifier = null,
                instanceId = null,
                strategy = PartitionStrategy.TENANT_PATH_INSTANCE_PATH,
            )
        assertEquals("db/password", path)
    }

    @Test
    fun dedicatedBackendAndGlobalAreNeverSharded() {
        val dedicated =
            resolver.physicalAddress(
                logicalKey = "db.password",
                providerType = "vault",
                scope = ConfigLevel.TENANT,
                scopeIdentifier = "acme",
                instanceId = "issuer-1",
                strategy = PartitionStrategy.DEDICATED_BACKEND,
            )
        // No tenant shard, but rendered in vault's native path style.
        assertEquals("db/password", dedicated)

        val global =
            resolver.physicalAddress(
                logicalKey = "shared.api.key",
                providerType = "aws",
                scope = ConfigLevel.TENANT,
                scopeIdentifier = "acme",
                instanceId = null,
                strategy = PartitionStrategy.GLOBAL,
            )
        // No tenant shard, but rendered in aws's native flat-name style (`_`).
        assertEquals("shared_api_key", global)
    }

    @Test
    fun logicalKeyNormalizationIsEquivalentAcrossDelimiterForms() {
        // The user's requirement: dotted == UPPER_SNAKE == hyphen == slash forms of the same logical
        // key map to the SAME physical address (case is not a delimiter, so HOME stays one token).
        val forms =
            listOf(
                "example.secret.ref.value",
                "EXAMPLE_SECRET_REF_VALUE",
                "example-secret-ref-value",
                "example/secret/ref/value",
            )
        // env (flat label, sharded)
        val envAddresses =
            forms.map {
                resolver.physicalAddress(it, "env", ConfigLevel.TENANT, "acme", null, PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX)
            }
        assertEquals(setOf("ACME__EXAMPLE_SECRET_REF_VALUE"), envAddresses.toSet())

        // vault (path, sharded)
        val vaultAddresses =
            forms.map {
                resolver.physicalAddress(it, "vault", ConfigLevel.TENANT, "acme", null, PartitionStrategy.TENANT_PATH_INSTANCE_PATH)
            }
        assertEquals(setOf("tenants/acme/example/secret/ref/value"), vaultAddresses.toSet())

        // A single uppercase token (acronym/env var) must NOT be split per-letter.
        assertEquals(
            "HOME",
            resolver.physicalAddress("HOME", "env", ConfigLevel.APP, null, null, PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX),
        )
    }

    @Test
    fun envFloorShardsPerTenantSoTenantsDoNotCollide() {
        // The canonical collision case: two tenants sharing the env floor must read distinct vars.
        val acme = resolver.physicalAddress("db.password", "env", ConfigLevel.TENANT, "acme", null, PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX)
        val globex = resolver.physicalAddress("db.password", "env", ConfigLevel.TENANT, "globex", null, PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX)
        assertEquals("ACME__DB_PASSWORD", acme)
        assertEquals("GLOBEX__DB_PASSWORD", globex)
        assertTrue(acme != globex)
    }

    @Test
    fun defaultStrategyPerProviderType() {
        assertEquals(PartitionStrategy.TENANT_PATH_INSTANCE_PATH, PartitionStrategy.defaultFor("vault"))
        assertEquals(PartitionStrategy.TENANT_PATH_INSTANCE_PATH, PartitionStrategy.defaultFor("kubernetes-mount"))
        assertEquals(PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX, PartitionStrategy.defaultFor("env"))
        assertEquals(PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX, PartitionStrategy.defaultFor("azure"))
        assertEquals(PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX, PartitionStrategy.defaultFor("aws"))
        assertEquals(PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX, PartitionStrategy.defaultFor("unknown"))
    }

    // --- Azure injective label form (charset-safe + collision-free) -----------------------------

    @Test
    fun azureLabelProducesValidAzureSecretName() {
        val address =
            resolver.physicalAddress(
                logicalKey = "db.password",
                providerType = "azure",
                scope = ConfigLevel.TENANT,
                scopeIdentifier = "acme",
                instanceId = null,
                strategy = PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX,
            )
        // Only [a-zA-Z0-9-], <=127 chars.
        assertTrue(Regex("^[a-zA-Z0-9-]+$").matches(address), "got: $address")
        assertTrue(address.length <= 127)
        // Human-readable prefix is present plus a disambiguating hash suffix.
        assertTrue(address.startsWith("acme-db-password-"), "got: $address")
    }

    @Test
    fun azureLabelIsStableForReadWriteSymmetry() {
        fun addr() =
            resolver.physicalAddress(
                logicalKey = "db.password",
                providerType = "azure",
                scope = ConfigLevel.TENANT,
                scopeIdentifier = "acme",
                instanceId = null,
                strategy = PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX,
            )
        assertEquals(addr(), addr())
    }

    @Test
    fun azureLabelShardsDistinctTenantsApart() {
        val acme =
            resolver.physicalAddress(
                "db.password",
                "azure",
                ConfigLevel.TENANT,
                "acme",
                null,
                PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX,
            )
        val globex =
            resolver.physicalAddress(
                "db.password",
                "azure",
                ConfigLevel.TENANT,
                "globex",
                null,
                PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX,
            )
        assertNotEquals(acme, globex)
    }

    @Test
    fun azureLabelIsInjectiveUnderLossySanitization() {
        // Two distinct (tenant, key) tuples that would alias under naive '_'->'-' / '.'->'-'
        // sanitization (e.g. tenant "a-b"+key "c" vs tenant "a"+key "b-c") MUST NOT collide,
        // because the hash is over the RAW tuple.
        val first =
            resolver.physicalAddress(
                "c",
                "azure",
                ConfigLevel.TENANT,
                "a.b",
                null,
                PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX,
            )
        val second =
            resolver.physicalAddress(
                "b.c",
                "azure",
                ConfigLevel.TENANT,
                "a",
                null,
                PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX,
            )
        assertNotEquals(first, second)
    }

    @Test
    fun azureLabelDistinguishesInstanceIds() {
        val noInstance =
            resolver.physicalAddress(
                "db.password",
                "azure",
                ConfigLevel.TENANT,
                "acme",
                null,
                PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX,
            )
        val withInstance =
            resolver.physicalAddress(
                "db.password",
                "azure",
                ConfigLevel.TENANT,
                "acme",
                "issuer-1",
                PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX,
            )
        assertNotEquals(noInstance, withInstance)
    }
}
