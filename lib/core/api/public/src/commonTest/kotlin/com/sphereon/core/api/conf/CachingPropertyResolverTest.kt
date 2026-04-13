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

package com.sphereon.core.api.conf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for CachingPropertySourcesPropertyResolver.
 * Verifies scope-partitioned caching behavior with multiple tenants and users.
 */
class CachingPropertyResolverTest {
    // Test helper: Creates a property source with configurable properties
    private fun createTestPropertySource(
        name: String,
        properties: Map<String, Any>,
    ): MapPropertySource = MapPropertySource(name, properties.toMutableMap())

    // Test helper: Creates a CachingPropertySourcesPropertyResolver with given config
    private fun createCachingResolver(
        properties: Map<String, Any>,
        cache: InMemorySyncSnapshotCache,
        level: ConfigLevel,
        tenantId: String? = null,
        principalId: String? = null,
    ): CachingPropertySourcesPropertyResolver {
        val source = createTestPropertySource("test", properties)
        val sources = DefaultPropertySources(mutableListOf(source))
        return CachingPropertySourcesPropertyResolver(
            propertySources = sources,
            snapshotCache = cache,
            level = level,
            tenantId = tenantId,
            principalId = principalId,
        )
    }

    // ========== Basic Caching Tests ==========

    @Test
    fun getSubPropertiesCachesResult() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "kms.providers.software.type" to "SOFTWARE",
                "kms.providers.software.enabled" to true,
            )
        val resolver = createCachingResolver(properties, cache, ConfigLevel.APP)

        // First call - cache miss
        val result1 = resolver.getSubProperties(setOf("kms.providers"), stripPrefix = true)
        assertEquals(2, result1.size)

        // Second call - should return cached result
        val result2 = resolver.getSubProperties(setOf("kms.providers"), stripPrefix = true)
        assertEquals(result1, result2)

        // Verify cache was used (snapshot should exist)
        val snapshotKey = SnapshotKey(ConfigLevel.APP, null, null, "kms.providers")
        val snapshot = cache.getSnapshot(snapshotKey)
        assertNotNull(snapshot)
    }

    @Test
    fun getSubPropertiesWithStripPrefixFalseWorks() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "kms.providers.software.type" to "SOFTWARE",
            )
        val resolver = createCachingResolver(properties, cache, ConfigLevel.APP)

        val result = resolver.getSubProperties(setOf("kms.providers"), stripPrefix = false)

        // Should contain the full key since stripPrefix=false
        assertTrue(result.containsKey("kms.providers.software.type"))
    }

    // ========== Multi-Tenant Isolation Tests ==========

    @Test
    fun differentTenantsHaveSeparateCaches() {
        val sharedCache = InMemorySyncSnapshotCache()

        // Tenant A properties
        val tenantAProps =
            mapOf(
                "kms.providers.software.type" to "SOFTWARE",
                "kms.providers.software.keystore.path" to "/tenant-a/keys",
            )
        val resolverTenantA =
            createCachingResolver(
                tenantAProps,
                sharedCache,
                ConfigLevel.TENANT,
                tenantId = "tenant-a",
            )

        // Tenant B properties (different values)
        val tenantBProps =
            mapOf(
                "kms.providers.software.type" to "SOFTWARE",
                "kms.providers.software.keystore.path" to "/tenant-b/keys",
            )
        val resolverTenantB =
            createCachingResolver(
                tenantBProps,
                sharedCache,
                ConfigLevel.TENANT,
                tenantId = "tenant-b",
            )

        // Fetch for tenant A
        val resultA = resolverTenantA.getSubProperties(setOf("kms.providers"), stripPrefix = true)
        assertEquals("/tenant-a/keys", resultA["software.keystore.path"])

        // Fetch for tenant B - should NOT return tenant A's cached data
        val resultB = resolverTenantB.getSubProperties(setOf("kms.providers"), stripPrefix = true)
        assertEquals("/tenant-b/keys", resultB["software.keystore.path"])

        // Verify separate cache entries exist
        val snapshotKeyA = SnapshotKey(ConfigLevel.TENANT, "tenant-a", null, "kms.providers")
        val snapshotKeyB = SnapshotKey(ConfigLevel.TENANT, "tenant-b", null, "kms.providers")

        assertNotNull(sharedCache.getSnapshot(snapshotKeyA))
        assertNotNull(sharedCache.getSnapshot(snapshotKeyB))

        // Verify they are different entries
        assertTrue(snapshotKeyA.toStringKey() != snapshotKeyB.toStringKey())
    }

    @Test
    fun appScopeCacheIsSharedAcrossTenants() {
        val sharedCache = InMemorySyncSnapshotCache()
        val appProps =
            mapOf(
                "app.name" to "test-app",
                "app.version" to "1.0.0",
            )

        // Create two APP-level resolvers (no tenant context)
        val resolver1 = createCachingResolver(appProps, sharedCache, ConfigLevel.APP)
        val resolver2 = createCachingResolver(appProps, sharedCache, ConfigLevel.APP)

        // First resolver populates cache
        val result1 = resolver1.getSubProperties(setOf("app"), stripPrefix = true)

        // Second resolver should use same cache entry
        val result2 = resolver2.getSubProperties(setOf("app"), stripPrefix = true)

        assertEquals(result1, result2)

        // Only one snapshot key should exist for APP scope
        val snapshotKey = SnapshotKey(ConfigLevel.APP, null, null, "app")
        assertNotNull(sharedCache.getSnapshot(snapshotKey))
    }

    // ========== Multi-User (Principal) Isolation Tests ==========

    @Test
    fun differentPrincipalsWithinSameTenantHaveSeparateCaches() {
        val sharedCache = InMemorySyncSnapshotCache()
        val tenantId = "shared-tenant"

        // User A properties
        val userAProps =
            mapOf(
                "user.preferences.theme" to "dark",
                "user.preferences.language" to "en",
            )
        val resolverUserA =
            createCachingResolver(
                userAProps,
                sharedCache,
                ConfigLevel.PRINCIPAL,
                tenantId = tenantId,
                principalId = "user-a",
            )

        // User B properties (same tenant, different preferences)
        val userBProps =
            mapOf(
                "user.preferences.theme" to "light",
                "user.preferences.language" to "de",
            )
        val resolverUserB =
            createCachingResolver(
                userBProps,
                sharedCache,
                ConfigLevel.PRINCIPAL,
                tenantId = tenantId,
                principalId = "user-b",
            )

        // Fetch for user A
        val resultA = resolverUserA.getSubProperties(setOf("user.preferences"), stripPrefix = true)
        assertEquals("dark", resultA["theme"])
        assertEquals("en", resultA["language"])

        // Fetch for user B - should have separate cache
        val resultB = resolverUserB.getSubProperties(setOf("user.preferences"), stripPrefix = true)
        assertEquals("light", resultB["theme"])
        assertEquals("de", resultB["language"])

        // Verify separate cache entries
        val snapshotKeyA = SnapshotKey(ConfigLevel.PRINCIPAL, tenantId, "user-a", "user.preferences")
        val snapshotKeyB = SnapshotKey(ConfigLevel.PRINCIPAL, tenantId, "user-b", "user.preferences")

        assertNotNull(sharedCache.getSnapshot(snapshotKeyA))
        assertNotNull(sharedCache.getSnapshot(snapshotKeyB))
    }

    @Test
    fun samePrincipalInDifferentTenantsHaveSeparateCaches() {
        val sharedCache = InMemorySyncSnapshotCache()
        val principalId = "shared-user" // Same user ID in both tenants

        // User in Tenant A
        val tenantAProps = mapOf("config.value" to "tenant-a-value")
        val resolverTenantA =
            createCachingResolver(
                tenantAProps,
                sharedCache,
                ConfigLevel.PRINCIPAL,
                tenantId = "tenant-a",
                principalId = principalId,
            )

        // Same user ID in Tenant B
        val tenantBProps = mapOf("config.value" to "tenant-b-value")
        val resolverTenantB =
            createCachingResolver(
                tenantBProps,
                sharedCache,
                ConfigLevel.PRINCIPAL,
                tenantId = "tenant-b",
                principalId = principalId,
            )

        val resultA = resolverTenantA.getSubProperties(setOf("config"), stripPrefix = true)
        val resultB = resolverTenantB.getSubProperties(setOf("config"), stripPrefix = true)

        // Same principal ID but different tenants = different values
        assertEquals("tenant-a-value", resultA["value"])
        assertEquals("tenant-b-value", resultB["value"])
    }

    // ========== Cache Key Composition Tests ==========

    @Test
    fun multiplePrefixesCreateCompositeKey() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "kms.providers.software.type" to "SOFTWARE",
                "sphereon.default.kms.providers.aws.type" to "AWS",
            )
        val resolver = createCachingResolver(properties, cache, ConfigLevel.APP)

        // Query with multiple prefixes
        val prefixes = setOf("kms.providers", "sphereon.default.kms.providers")
        val result = resolver.getSubProperties(prefixes, stripPrefix = true)

        // Should find properties from both prefixes
        assertTrue(result.isNotEmpty())

        // Cache key should be composite (sorted and joined)
        val expectedKeyPrefix = "kms.providers|sphereon.default.kms.providers"
        val snapshotKey = SnapshotKey(ConfigLevel.APP, null, null, expectedKeyPrefix)
        assertNotNull(cache.getSnapshot(snapshotKey))
    }

    // ========== NoOp Cache Behavior Tests ==========

    @Test
    fun noOpCacheDoesNotStoreSnapshots() {
        val properties = mapOf("test.key" to "test-value")
        val source = createTestPropertySource("test", properties)
        val sources = DefaultPropertySources(mutableListOf(source))

        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = NoOpSyncSnapshotCache,
                level = ConfigLevel.APP,
            )

        // Should still return correct results
        val result = resolver.getSubProperties(setOf("test"), stripPrefix = true)
        assertEquals("test-value", result["key"])

        // But NoOpSyncSnapshotCache always returns null
        val snapshotKey = SnapshotKey(ConfigLevel.APP, null, null, "test")
        assertNull(NoOpSyncSnapshotCache.getSnapshot(snapshotKey))
    }

    // ========== Cache Invalidation Tests ==========

    @Test
    fun cacheInvalidationByPrefixWorks() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "kms.providers.software.type" to "SOFTWARE",
            )
        val resolver = createCachingResolver(properties, cache, ConfigLevel.APP)

        // Populate cache
        resolver.getSubProperties(setOf("kms.providers"), stripPrefix = true)

        val snapshotKey = SnapshotKey(ConfigLevel.APP, null, null, "kms.providers")
        assertNotNull(cache.getSnapshot(snapshotKey))

        // Invalidate by prefix
        cache.invalidateByPrefix("kms.providers")

        // Cache should be empty now
        assertNull(cache.getSnapshot(snapshotKey))
    }

    @Test
    fun cacheClearRemovesAllEntries() {
        val cache = InMemorySyncSnapshotCache()

        // Create resolvers for different scopes
        val appResolver =
            createCachingResolver(
                mapOf("app.key" to "value"),
                cache,
                ConfigLevel.APP,
            )
        val tenantResolver =
            createCachingResolver(
                mapOf("tenant.key" to "value"),
                cache,
                ConfigLevel.TENANT,
                tenantId = "t1",
            )

        // Populate cache
        appResolver.getSubProperties(setOf("app"), stripPrefix = true)
        tenantResolver.getSubProperties(setOf("tenant"), stripPrefix = true)

        // Both should be cached
        assertNotNull(cache.getSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "app")))
        assertNotNull(cache.getSnapshot(SnapshotKey(ConfigLevel.TENANT, "t1", null, "tenant")))

        // Clear all
        cache.clear()

        // Both should be gone
        assertNull(cache.getSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "app")))
        assertNull(cache.getSnapshot(SnapshotKey(ConfigLevel.TENANT, "t1", null, "tenant")))
    }

    // ========== Property Delegation Tests ==========

    @Test
    fun nonCachedMethodsDelegateToUnderlying() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "direct.property" to "direct-value",
                "another.property" to 42,
            )
        val resolver = createCachingResolver(properties, cache, ConfigLevel.APP)

        // Single property lookups should delegate directly without caching
        assertEquals("direct-value", resolver.getPropertyAsString("direct.property"))
        assertEquals(42, resolver.getProperty("another.property", Int::class))

        // Verify no cache entries were created for single property lookups
        // (caching is only for getSubProperties)
        val snapshotKey = SnapshotKey(ConfigLevel.APP, null, null, "direct.property")
        assertNull(cache.getSnapshot(snapshotKey))
    }

    // ========== Interpolator Integration Tests ==========

    @Test
    fun interpolatorResolvesPlaceholdersInProperties() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "base.url" to "https://api.example.com",
                "users.endpoint" to "\${base.url}/users",
            )
        val source = createTestPropertySource("test", properties)
        val sources = DefaultPropertySources(mutableListOf(source))
        val interpolator = DefaultPropertyInterpolator()

        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.APP,
                interpolator = interpolator,
            )

        // Should resolve the placeholder
        val result = resolver.getPropertyAsString("users.endpoint")
        assertEquals("https://api.example.com/users", result)
    }

    @Test
    fun interpolatorResolvesPlaceholdersInSubProperties() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "api.base" to "https://api.example.com",
                "api.users" to "\${api.base}/users",
                "api.products" to "\${api.base}/products",
            )
        val source = createTestPropertySource("test", properties)
        val sources = DefaultPropertySources(mutableListOf(source))
        val interpolator = DefaultPropertyInterpolator()

        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.APP,
                interpolator = interpolator,
            )

        val result = resolver.getSubProperties(setOf("api"), stripPrefix = true)
        assertEquals("https://api.example.com", result["base"])
        assertEquals("https://api.example.com/users", result["users"])
        assertEquals("https://api.example.com/products", result["products"])
    }

    @Test
    fun interpolatorWithDefaultValueResolution() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "endpoint" to "\${missing.host:localhost}/api",
            )
        val source = createTestPropertySource("test", properties)
        val sources = DefaultPropertySources(mutableListOf(source))
        val interpolator = DefaultPropertyInterpolator()

        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.APP,
                interpolator = interpolator,
            )

        val result = resolver.getPropertyAsString("endpoint")
        assertEquals("localhost/api", result)
    }

    @Test
    fun noInterpolationWithoutInterpolator() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "base.url" to "https://api.example.com",
                "users.endpoint" to "\${base.url}/users",
            )
        val resolver = createCachingResolver(properties, cache, ConfigLevel.APP)

        // Without interpolator, value should be returned as-is
        val result = resolver.getPropertyAsString("users.endpoint")
        assertEquals("\${base.url}/users", result)
    }

    @Test
    fun interpolatorWithSecretResolver() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "secret.ref" to "\${secret:env:PATH}",
            )
        val source = createTestPropertySource("test", properties)
        val sources = DefaultPropertySources(mutableListOf(source))
        val secretResolver = createDefaultSecretResolver()
        val interpolator = DefaultPropertyInterpolator(secretResolver = secretResolver)

        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.APP,
                interpolator = interpolator,
            )

        val result = resolver.getPropertyAsString("secret.ref")
        assertNotNull(result)
        // Secret should be resolved (not the literal placeholder)
        assertTrue(!result.contains("\${secret:"))
    }

    // ========== Additional Delegation Tests ==========

    @Test
    fun containsPropertyDelegatesToUnderlying() {
        val cache = InMemorySyncSnapshotCache()
        val properties = mapOf("existing.key" to "value")
        val resolver = createCachingResolver(properties, cache, ConfigLevel.APP)

        assertTrue(resolver.containsProperty("existing.key"))
        assertFalse(resolver.containsProperty("missing.key"))
    }

    @Test
    fun getRequiredPropertyThrowsForMissingKey() {
        val cache = InMemorySyncSnapshotCache()
        val properties = mapOf("existing.key" to "value")
        val resolver = createCachingResolver(properties, cache, ConfigLevel.APP)

        // Should return value for existing key
        assertEquals("value", resolver.getRequiredPropertyAsString("existing.key"))

        // Should throw for missing key
        try {
            resolver.getRequiredPropertyAsString("missing.key")
            assertTrue(false, "Should have thrown exception")
        } catch (_: Exception) {
            // Expected
        }
    }

    @Test
    fun getAllPropertiesReturnsAllValues() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "key1" to "value1",
                "key2" to "value2",
                "key3" to 42,
            )
        val resolver = createCachingResolver(properties, cache, ConfigLevel.APP)

        val all = resolver.getAllProperties()
        assertEquals(3, all.size)
        assertEquals("value1", all["key1"])
        assertEquals("value2", all["key2"])
        assertEquals(42, all["key3"])
    }

    @Test
    fun getAllPropertiesAsStringConvertsValues() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "string.prop" to "value",
                "int.prop" to 42,
                "bool.prop" to true,
            )
        val resolver = createCachingResolver(properties, cache, ConfigLevel.APP)

        // Use redact=false to test raw value conversion without redaction
        val all = resolver.getAllPropertiesAsString(redact = false)
        assertEquals("value", all["string.prop"])
        assertEquals("42", all["int.prop"])
        assertEquals("true", all["bool.prop"])
    }

    @Test
    fun getSubPropertiesAsStringConvertsValues() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "prefix.string" to "value",
                "prefix.int" to 42,
                "prefix.bool" to true,
            )
        val resolver = createCachingResolver(properties, cache, ConfigLevel.APP)

        val result = resolver.getSubPropertiesAsString(setOf("prefix"), stripPrefix = true)
        assertEquals("value", result["string"])
        assertEquals("42", result["int"])
        assertEquals("true", result["bool"])
    }

    @Test
    fun getRequiredPropertyWithTypeConversion() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "int.key" to 42,
                "string.key" to "hello",
            )
        val resolver = createCachingResolver(properties, cache, ConfigLevel.APP)

        val intValue: Int = resolver.getRequiredProperty("int.key", Int::class)
        assertEquals(42, intValue)

        val stringValue: String = resolver.getRequiredProperty("string.key", String::class)
        assertEquals("hello", stringValue)
    }
}

/**
 * Tests for SnapshotKey to verify key generation is correct for multi-tenant scenarios.
 */
class SnapshotKeyMultiTenantTest {
    @Test
    fun snapshotKeyStringKeyIncludesTenantId() {
        val key = SnapshotKey(ConfigLevel.TENANT, "my-tenant", null, "kms.providers")
        val stringKey = key.toStringKey()

        assertTrue(stringKey.contains("TENANT"))
        assertTrue(stringKey.contains("t:my-tenant"))
        assertTrue(stringKey.contains("kms.providers"))
    }

    @Test
    fun snapshotKeyStringKeyIncludesPrincipalId() {
        val key = SnapshotKey(ConfigLevel.PRINCIPAL, "my-tenant", "my-user", "user.prefs")
        val stringKey = key.toStringKey()

        assertTrue(stringKey.contains("PRINCIPAL"))
        assertTrue(stringKey.contains("t:my-tenant"))
        assertTrue(stringKey.contains("p:my-user"))
        assertTrue(stringKey.contains("user.prefs"))
    }

    @Test
    fun differentTenantsProduceDifferentKeys() {
        val keyA = SnapshotKey(ConfigLevel.TENANT, "tenant-a", null, "config")
        val keyB = SnapshotKey(ConfigLevel.TENANT, "tenant-b", null, "config")

        assertTrue(keyA.toStringKey() != keyB.toStringKey())
    }

    @Test
    fun differentPrincipalsProduceDifferentKeys() {
        val keyA = SnapshotKey(ConfigLevel.PRINCIPAL, "tenant", "user-a", "config")
        val keyB = SnapshotKey(ConfigLevel.PRINCIPAL, "tenant", "user-b", "config")

        assertTrue(keyA.toStringKey() != keyB.toStringKey())
    }

    @Test
    fun samePrincipalDifferentTenantsProduceDifferentKeys() {
        val keyA = SnapshotKey(ConfigLevel.PRINCIPAL, "tenant-a", "same-user", "config")
        val keyB = SnapshotKey(ConfigLevel.PRINCIPAL, "tenant-b", "same-user", "config")

        assertTrue(keyA.toStringKey() != keyB.toStringKey())
    }
}
