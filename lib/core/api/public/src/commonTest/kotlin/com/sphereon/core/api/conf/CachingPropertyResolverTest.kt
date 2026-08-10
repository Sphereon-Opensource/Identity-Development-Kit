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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

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
        val source =
            ProtectedMutableMapPropertySource("test", level).apply {
                addProperties(properties)
            }
        val sources = DefaultPropertySources(mutableListOf(source))
        return CachingPropertySourcesPropertyResolver(
            propertySources = sources,
            snapshotCache = cache,
            level = level,
            tenantId = tenantId,
            principalId = principalId,
        )
    }

    private fun forgedSnapshot(
        key: String,
        value: String,
    ): ConfigSnapshot {
        val now = Clock.System.now()
        return ConfigSnapshot(
            values =
                mapOf(
                    key to
                        CachedConfigValue(
                            value = value,
                            metadata =
                                ResolutionMetadata(
                                    source = "forged",
                                    scope = ConfigLevel.APP,
                                    originalKey = key,
                                    normalizedKey = key,
                                    order = 0,
                                    isSecret = false,
                                    isInterpolated = false,
                                    resolvedAt = now,
                                    ttl = 1.hours,
                                    provenance = ResolutionProvenance.known(ConfigLevel.APP),
                                ),
                            cachedAt = now,
                            expiresAt = now + 1.hours,
                            preserveType = true,
                        ),
                ),
            createdAt = now,
            expiresAt = now + 1.hours,
        )
    }

    // ========== Basic Caching Tests ==========

    @Test
    fun getSubPropertiesCachesResult() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "kms.providers.software.type" to "SOFTWARE",
                "kms.providers.software.state" to "enabled",
            )
        val resolver = createCachingResolver(properties, cache, ConfigLevel.APP)

        // First call - cache miss
        val result1 = resolver.getSubProperties(setOf("kms.providers"), stripPrefix = true)
        assertEquals(2, result1.size)

        // Second call - should return cached result
        val result2 = resolver.getSubProperties(setOf("kms.providers"), stripPrefix = true)
        assertEquals(result1, result2)

        // Verify cache was used (snapshot should exist)
        val snapshotKey = resolver.currentSnapshotKey(setOf("kms.providers"))
        val snapshot = cache.getSnapshot(snapshotKey)
        assertNotNull(snapshot)
    }

    @Test
    fun refreshablePropertySourceRevisionInvalidatesCachedSubProperties() {
        val cache = InMemorySyncSnapshotCache()
        val source = RefreshableTestPropertySource()
        val sources = DefaultPropertySources(mutableListOf(source))
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.APP,
            )

        val first = resolver.getSubProperties(setOf("kms.providers"), stripPrefix = true)
        assertEquals("SOFTWARE", first["initial.type"])
        assertFalse(first.containsKey("late.type"))
        assertNotNull(
            cache.getSnapshot(
                SnapshotKey(
                    ConfigLevel.APP,
                    null,
                    null,
                    "kms.providers",
                    sourceRevision = sources.revision,
                    contentRevision = sources.refreshableContentRevision(refresh = false),
                ),
            ),
        )

        source.publishLateProviderOnNextRefresh()

        val second = resolver.getSubProperties(setOf("kms.providers"), stripPrefix = true)
        assertEquals("SOFTWARE", second["initial.type"])
        assertEquals("SOFTWARE", second["late.type"])
    }

    @Test
    fun refreshableRevisionInvalidatesOtherCachedPrefixesForTenant() {
        val cache = InMemorySyncSnapshotCache()
        val source = MultiPrefixRefreshableTestPropertySource()
        val sources = DefaultPropertySources(mutableListOf(source))
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.TENANT,
                tenantId = "tenant-a",
            )

        assertEquals(
            "initial-secret",
            resolver.getSubProperties(setOf("oauth2.clients"), stripPrefix = true)["issuer.client.secret"],
        )
        assertEquals(
            "initial",
            resolver.getSubProperties(setOf("feature"), stripPrefix = true)["state"],
        )

        source.publishRotationOnNextRefresh()

        assertEquals(
            "rotated",
            resolver.getSubProperties(setOf("feature"), stripPrefix = true)["state"],
        )
        assertEquals(
            "rotated-secret",
            resolver.getSubProperties(setOf("oauth2.clients"), stripPrefix = true)["issuer.client.secret"],
        )
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
                "kms.providers.software.storage.path" to "/tenant-a/keys",
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
                "kms.providers.software.storage.path" to "/tenant-b/keys",
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
        assertEquals("/tenant-a/keys", resultA["software.storage.path"])

        // Fetch for tenant B - should NOT return tenant A's cached data
        val resultB = resolverTenantB.getSubProperties(setOf("kms.providers"), stripPrefix = true)
        assertEquals("/tenant-b/keys", resultB["software.storage.path"])

        // Verify separate cache entries exist
        val snapshotKeyA = resolverTenantA.currentSnapshotKey(setOf("kms.providers"))
        val snapshotKeyB = resolverTenantB.currentSnapshotKey(setOf("kms.providers"))

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
        val snapshotKey = resolver1.currentSnapshotKey(setOf("app"))
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
        val snapshotKeyA = resolverUserA.currentSnapshotKey(setOf("user.preferences"))
        val snapshotKeyB = resolverUserB.currentSnapshotKey(setOf("user.preferences"))

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
        val snapshotKey = resolver.currentSnapshotKey(prefixes)
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

        val snapshotKey = resolver.currentSnapshotKey(setOf("kms.providers"))
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
                mapOf("app.value" to "value"),
                cache,
                ConfigLevel.APP,
            )
        val tenantResolver =
            createCachingResolver(
                mapOf("tenant.value" to "value"),
                cache,
                ConfigLevel.TENANT,
                tenantId = "t1",
            )

        // Populate cache
        appResolver.getSubProperties(setOf("app"), stripPrefix = true)
        tenantResolver.getSubProperties(setOf("tenant"), stripPrefix = true)

        // Both should be cached
        val appKey = appResolver.currentSnapshotKey(setOf("app"))
        val tenantKey = tenantResolver.currentSnapshotKey(setOf("tenant"))
        assertNotNull(cache.getSnapshot(appKey))
        assertNotNull(cache.getSnapshot(tenantKey))

        // Clear all
        cache.clear()

        // Both should be gone
        assertNull(cache.getSnapshot(appKey))
        assertNull(cache.getSnapshot(tenantKey))
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
        val source = ScopedPropertySourceWrapper(createTestPropertySource("test", properties), ConfigLevel.APP)
        val sources = DefaultPropertySources(mutableListOf(source))
        val interpolator = DefaultPropertyInterpolator()

        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.APP,
                interpolator = interpolator,
                interpolationPolicyProvider =
                    FixedInterpolationPolicyProvider(InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
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
        val source = ScopedPropertySourceWrapper(createTestPropertySource("test", properties), ConfigLevel.APP)
        val sources = DefaultPropertySources(mutableListOf(source))
        val interpolator = DefaultPropertyInterpolator()

        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.APP,
                interpolator = interpolator,
                interpolationPolicyProvider =
                    FixedInterpolationPolicyProvider(InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
            )

        val result = resolver.getSubProperties(setOf("api"), stripPrefix = true)
        assertEquals("https://api.example.com", result["base"])
        assertEquals("https://api.example.com/users", result["users"])
        assertEquals("https://api.example.com/products", result["products"])
    }

    @Test
    fun resolvePropertyWithScopeReturnsInterpolatedValueAndProvenance() {
        val source =
            ProtectedMutableMapPropertySource("test", ConfigLevel.APP).apply {
                addProperty("base.value", "resolved")
                addProperty("feature.name", "\${base.value}")
            }
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = DefaultPropertySources(mutableListOf(source)),
                snapshotCache = InMemorySyncSnapshotCache(),
                level = ConfigLevel.APP,
                interpolator = DefaultPropertyInterpolator(),
                interpolationPolicyProvider =
                    FixedInterpolationPolicyProvider(InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
            )

        val resolved = resolver.resolvePropertyWithScope("feature.name", requiredScope = null)

        assertNotNull(resolved)
        assertEquals("resolved", resolved.value)
        assertEquals(ConfigLevel.APP, resolved.sourceScope)
        assertTrue(resolved.provenance.hasTaint(ResolutionTaint.INTERPOLATED))
    }

    @Test
    fun interpolatorWithDefaultValueResolution() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "endpoint" to "\${missing.host:localhost}/api",
            )
        val source = ScopedPropertySourceWrapper(createTestPropertySource("test", properties), ConfigLevel.APP)
        val sources = DefaultPropertySources(mutableListOf(source))
        val interpolator = DefaultPropertyInterpolator()

        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.APP,
                interpolator = interpolator,
                interpolationPolicyProvider =
                    FixedInterpolationPolicyProvider(InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
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
    fun interpolatorRejectsProviderBackedSecretReferences() {
        val cache = InMemorySyncSnapshotCache()
        val properties =
            mapOf(
                "public.ref" to "\${secret:@env:PATH}",
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

        val denial =
            assertFailsWith<IllegalStateException> {
                resolver.getPropertyAsString("public.ref")
            }
        assertEquals("Configuration value is not permitted", denial.message)
    }

    @Test
    fun tenantCachingResolverRejectsRecursivelyProducedEnvironmentReference() {
        val cache = InMemorySyncSnapshotCache()
        val tenant =
            ProtectedMutableMapPropertySource("tenant", ConfigLevel.TENANT).apply {
                addProperty("service.placeholder", "env:PATH")
                addProperty("service.endpoint", "\${\${service.placeholder}}")
            }
        val sources = DefaultPropertySources(mutableListOf(tenant))
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.TENANT,
                tenantId = "tenant-a",
                interpolator = DefaultPropertyInterpolator(),
            )

        assertFailsWith<IllegalStateException> {
            resolver.getPropertyAsString("service.endpoint")
        }
        assertFailsWith<IllegalStateException> {
            resolver.getSubProperties(setOf("service"), stripPrefix = true)
        }
        assertNull(cache.getSnapshot(SnapshotKey(ConfigLevel.TENANT, "tenant-a", null, "service")))
    }

    @Test
    fun cachedPlaceholderIsRevalidatedInsteadOfReturned() {
        val cache = InMemorySyncSnapshotCache()
        val tenant =
            ProtectedMutableMapPropertySource("tenant", ConfigLevel.TENANT).apply {
                addProperty("service.endpoint", "safe")
            }
        val sources = DefaultPropertySources(mutableListOf(tenant))
        val now = Clock.System.now()
        val key = SnapshotKey(ConfigLevel.TENANT, "tenant-a", null, "service")
        cache.putSnapshot(
            key,
            ConfigSnapshot(
                values =
                    mapOf(
                        "service.endpoint" to
                            CachedConfigValue(
                                value = "\${env:PATH}",
                                metadata =
                                    ResolutionMetadata(
                                        source = "stale",
                                        scope = ConfigLevel.TENANT,
                                        originalKey = "service.endpoint",
                                        normalizedKey = "service.endpoint",
                                        order = 0,
                                        isSecret = false,
                                        isInterpolated = false,
                                        resolvedAt = now,
                                        ttl = 1.hours,
                                    ),
                                cachedAt = now,
                                expiresAt = now + 1.hours,
                                preserveType = true,
                            ),
                    ),
                createdAt = now,
                expiresAt = now + 1.hours,
            ),
        )
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.TENANT,
                tenantId = "tenant-a",
                interpolator = DefaultPropertyInterpolator(),
            )

        val result = resolver.getSubProperties(setOf("service"), stripPrefix = true)

        assertEquals("safe", result["endpoint"])
        assertFalse(result.values.any { it.toString().contains("\${env:") })
    }

    @Test
    fun cachedMaterializedPlaintextAndNestedValuesAreNeverTrusted() {
        val cache = InMemorySyncSnapshotCache()
        val tenant =
            ProtectedMutableMapPropertySource("tenant", ConfigLevel.TENANT).apply {
                addProperty("service.endpoint", "safe-current")
                addProperty("service.nested", mapOf("value" to "safe-current"))
            }
        val sources = DefaultPropertySources(mutableListOf(tenant))
        val now = Clock.System.now()
        val key = SnapshotKey(ConfigLevel.TENANT, "tenant-a", null, "service")
        cache.putSnapshot(
            key,
            ConfigSnapshot(
                values =
                    mapOf(
                        "service.endpoint" to
                            CachedConfigValue(
                                value = "stale-plaintext",
                                metadata =
                                    ResolutionMetadata(
                                        source = "publicly-injected",
                                        scope = ConfigLevel.TENANT,
                                        originalKey = "service.endpoint",
                                        normalizedKey = "service.endpoint",
                                        order = 0,
                                        isSecret = false,
                                        isInterpolated = true,
                                        resolvedAt = now,
                                        ttl = 1.hours,
                                    ),
                                cachedAt = now,
                                expiresAt = now + 1.hours,
                                preserveType = true,
                            ),
                        "service.nested" to
                            CachedConfigValue(
                                value = mapOf("value" to "stale-nested"),
                                metadata =
                                    ResolutionMetadata(
                                        source = "publicly-injected",
                                        scope = ConfigLevel.TENANT,
                                        originalKey = "service.nested",
                                        normalizedKey = "service.nested",
                                        order = 0,
                                        isSecret = false,
                                        isInterpolated = true,
                                        resolvedAt = now,
                                        ttl = 1.hours,
                                    ),
                                cachedAt = now,
                                expiresAt = now + 1.hours,
                                preserveType = true,
                            ),
                    ),
                createdAt = now,
                expiresAt = now + 1.hours,
            ),
        )
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.TENANT,
                tenantId = "tenant-a",
                interpolator = DefaultPropertyInterpolator(),
            )

        val result = resolver.getSubProperties(setOf("service"), stripPrefix = true)

        assertEquals("safe-current", result["endpoint"])
        assertEquals(mapOf("value" to "safe-current"), result["nested"])
        assertFalse(result.values.any { it.toString().contains("stale-") })
    }

    @Test
    fun tenantCachingResolverHidesProtectedAppAndEnvironmentSources() {
        val cache = InMemorySyncSnapshotCache()
        val app =
            ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                addProtectedProperty("service.token", "server-owned", PropertyProtection.PROTECTED)
                addProperty("service.public", "visible")
            }
        val sources =
            DefaultPropertySources(
                mutableListOf(
                    StaticProtectedEnvPropertySourceObject,
                    app,
                ),
            )
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.TENANT,
                tenantId = "tenant-a",
                interpolator = DefaultPropertyInterpolator(),
            )

        assertFalse(resolver.containsProperty("service.token"))
        assertNull(resolver.getPropertyAsString("service.token"))
        assertFalse(resolver.getAllProperties().containsKey("service.token"))
        assertFalse(resolver.getSubProperties(setOf("service"), stripPrefix = false).containsKey("service.token"))
        assertFalse(resolver.containsProperty("PATH"))
        assertNull(resolver.getPropertyAsString("PATH"))
        assertEquals("visible", resolver.getPropertyAsString("service.public"))
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

    @Test
    fun safeWarmSnapshotIsReusedAndStructuralRevisionCannotHitStaleEntry() {
        val cache = InMemorySyncSnapshotCache()
        val source =
            CountingProtectedPropertySource("counting", ConfigLevel.APP).apply {
                addProperty("feature.name", "initial")
            }
        val sources = DefaultPropertySources(mutableListOf(source))
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.APP,
            )

        val first = resolver.getSubProperties(setOf("feature"), stripPrefix = true)
        val readsAfterFirst = source.readCount
        val second = resolver.getSubProperties(setOf("feature"), stripPrefix = true)

        assertEquals(mapOf("name" to "initial"), first)
        assertEquals(first, second)
        assertEquals(readsAfterFirst, source.readCount)
        val revisionOneContent = sources.refreshableContentRevision(refresh = false)
        val revisionOneKey =
            SnapshotKey(
                ConfigLevel.APP,
                null,
                null,
                "feature",
                sourceRevision = 1L,
                contentRevision = revisionOneContent,
            )
        assertNotNull(cache.getSnapshot(revisionOneKey))

        sources.add(
            ProtectedMutableMapPropertySource("additional", ConfigLevel.APP).apply {
                addProperty("feature.extra", "new")
            },
        )
        val afterRevision = resolver.getSubProperties(setOf("feature"), stripPrefix = true)

        assertEquals("new", afterRevision["extra"])
        assertTrue(source.readCount > readsAfterFirst)
        val revisionTwoContent = sources.refreshableContentRevision(refresh = false)
        val revisionTwoKey =
            SnapshotKey(
                ConfigLevel.APP,
                null,
                null,
                "feature",
                sourceRevision = 2L,
                contentRevision = revisionTwoContent,
            )
        assertNotNull(cache.getSnapshot(revisionTwoKey))
    }

    @Test
    fun cachedBulkStringDiagnosticsReuseCachedMetadataWithoutSourceReads() {
        val source =
            CountingProtectedPropertySource("cached-diagnostics", ConfigLevel.APP).apply {
                addProperty("feature.name", "visible")
            }
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = DefaultPropertySources(mutableListOf(source)),
                snapshotCache = InMemorySyncSnapshotCache(),
                level = ConfigLevel.APP,
            )

        val first =
            resolver.getSubPropertiesAsString(
                prefixes = setOf("feature"),
                stripPrefix = true,
                redact = true,
            )
        val readsAfterFirst = source.readCount
        val second =
            resolver.getSubPropertiesAsString(
                prefixes = setOf("feature"),
                stripPrefix = true,
                redact = true,
            )

        assertEquals(mapOf("name" to "visible"), first)
        assertEquals(first, second)
        assertEquals(readsAfterFirst, source.readCount)
    }

    @Test
    fun canonicalInterpolatingBulkReadsEachRootOnceAndCacheHitDoesNotReopen() {
        val source =
            CountingProtectedPropertySource("canonical-bulk", ConfigLevel.APP).apply {
                addProperty("defaults.label", "canonical-public")
                addProperty("public.alias", "\${defaults.label}")
            }
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = DefaultPropertySources(mutableListOf(source)),
                snapshotCache = InMemorySyncSnapshotCache(),
                level = ConfigLevel.APP,
                interpolator = DefaultPropertyInterpolator(),
                interpolationPolicyProvider =
                    DefaultInterpolationPolicyProvider(
                        mapOf("public.alias" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
                    ),
            )

        val first =
            resolver.getSubPropertiesAsString(
                prefixes = setOf("public"),
                stripPrefix = true,
                redact = true,
            )
        val readsAfterFirst = source.readCount

        assertEquals("canonical-public", first["alias"])
        assertEquals(1, source.valueReadCount("public.alias"))
        assertEquals(1, source.valueReadCount("defaults.label"))

        val second =
            resolver.getSubPropertiesAsString(
                prefixes = setOf("public"),
                stripPrefix = true,
                redact = true,
            )

        assertEquals(first, second)
        assertEquals(readsAfterFirst, source.readCount)
        assertEquals(1, source.valueReadCount("public.alias"))
        assertEquals(1, source.valueReadCount("defaults.label"))
    }

    @Test
    fun sensitiveInterpolatedAliasIsRedactedNeverCachedAndReread() {
        val cache = InMemorySyncSnapshotCache()
        val source =
            CountingProtectedPropertySource("sensitive-canonical-bulk", ConfigLevel.APP).apply {
                addProperty("credentials.password", "canonical-secret")
                addProperty("public.alias", "\${credentials.password}")
            }
        val sources = DefaultPropertySources(mutableListOf(source))
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.APP,
                interpolator = DefaultPropertyInterpolator(),
                interpolationPolicyProvider =
                    DefaultInterpolationPolicyProvider(
                        mapOf("public.alias" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
                    ),
            )

        val first =
            resolver.getSubPropertiesAsString(
                prefixes = setOf("public"),
                stripPrefix = true,
                redact = true,
            )
        val readsAfterFirst = source.readCount
        val firstAliasReads = source.valueReadCount("public.alias")
        val firstSecretReads = source.valueReadCount("credentials.password")
        val second =
            resolver.getSubPropertiesAsString(
                prefixes = setOf("public"),
                stripPrefix = true,
                redact = true,
            )

        assertEquals(mapOf("alias" to "***REDACTED***"), first)
        assertEquals(first, second)
        assertTrue(source.readCount > readsAfterFirst)
        assertTrue(source.valueReadCount("public.alias") > firstAliasReads)
        assertTrue(source.valueReadCount("credentials.password") > firstSecretReads)
        assertNull(cache.getSnapshot(resolver.currentSnapshotKey(setOf("public"))))
    }

    @Test
    fun protectedMutableSourceMutationInvalidatesRealSyncSnapshot() {
        val cache = InMemorySyncSnapshotCache()
        val source =
            ProtectedMutableMapPropertySource("mutable-app", ConfigLevel.APP).apply {
                addProperty("feature.name", "initial")
            }
        val sources = DefaultPropertySources(mutableListOf(source))
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.APP,
            )

        assertEquals(
            "initial",
            resolver.getSubProperties(setOf("feature"), stripPrefix = true)["name"],
        )
        val firstKey =
            SnapshotKey(
                scope = ConfigLevel.APP,
                tenantId = null,
                principalId = null,
                prefix = "feature",
                sourceRevision = sources.revision,
                contentRevision = sources.refreshableContentRevision(refresh = false),
            )
        assertNotNull(cache.getSnapshot(firstKey))

        source.addProperty("feature.name", "updated")
        assertEquals(
            "updated",
            resolver.getSubProperties(setOf("feature"), stripPrefix = true)["name"],
        )
        val updatedKey =
            firstKey.copy(contentRevision = sources.refreshableContentRevision(refresh = false))
        assertTrue(updatedKey != firstKey)
        assertNull(cache.getSnapshot(firstKey))
        assertNotNull(cache.getSnapshot(updatedKey))

        source.deleteProperty("feature.name")
        assertTrue(resolver.getSubProperties(setOf("feature"), stripPrefix = true).isEmpty())
        assertNull(cache.getSnapshot(updatedKey))
    }

    @Test
    fun firstRefreshObservationReusesExactPrewarmedSnapshot() {
        val cache = InMemorySyncSnapshotCache()
        val source =
            CountingProtectedPropertySource("prewarmed-source", ConfigLevel.APP).apply {
                addProperty("feature.name", "authoritative")
            }
        val sources = DefaultPropertySources(mutableListOf(source))
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.APP,
            )
        val key = resolver.currentSnapshotKey(setOf("feature"))
        val now = Clock.System.now()
        cache.putSnapshot(
            key,
            ConfigSnapshot(
                values =
                    mapOf(
                        "feature.name" to
                            CachedConfigValue(
                                value = "prewarmed",
                                metadata =
                                    ResolutionMetadata(
                                        source = "prewarmed",
                                        scope = ConfigLevel.APP,
                                        originalKey = "feature.name",
                                        normalizedKey = "feature.name",
                                        order = 0,
                                        isSecret = false,
                                        isInterpolated = false,
                                        resolvedAt = now,
                                        ttl = 1.hours,
                                        provenance = ResolutionProvenance.known(ConfigLevel.APP),
                                    ),
                                cachedAt = now,
                                expiresAt = now + 1.hours,
                                preserveType = true,
                            ),
                    ),
                createdAt = now,
                expiresAt = now + 1.hours,
            ),
        )

        val result = resolver.getSubProperties(setOf("feature"), stripPrefix = true)

        assertEquals("prewarmed", result["name"])
        assertEquals(0, source.readCount)
        assertNotNull(cache.getSnapshot(key))
    }

    @Test
    fun cacheRejectsSelfConsistentNoncanonicalEntryForRequestedPrefix() {
        val cache = InMemorySyncSnapshotCache()
        val source =
            CountingProtectedPropertySource("canonical-source", ConfigLevel.APP).apply {
                addProperty("feature.name", "authoritative")
            }
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = DefaultPropertySources(mutableListOf(source)),
                snapshotCache = cache,
                level = ConfigLevel.APP,
            )
        cache.putSnapshot(
            resolver.currentSnapshotKey(setOf("feature")),
            forgedSnapshot("feature.Name", "forged-noncanonical"),
        )

        val result = resolver.getSubProperties(setOf("feature"), stripPrefix = true)

        assertEquals(mapOf("name" to "authoritative"), result)
        assertTrue(source.readCount > 0)
        assertFalse(result.values.contains("forged-noncanonical"))
    }

    @Test
    fun cacheRejectsSelfConsistentEntryOutsideRequestedPrefix() {
        val cache = InMemorySyncSnapshotCache()
        val source =
            CountingProtectedPropertySource("prefix-source", ConfigLevel.APP).apply {
                addProperty("feature.name", "authoritative")
            }
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = DefaultPropertySources(mutableListOf(source)),
                snapshotCache = cache,
                level = ConfigLevel.APP,
            )
        cache.putSnapshot(
            resolver.currentSnapshotKey(setOf("feature")),
            forgedSnapshot("other.name", "forged-outside-prefix"),
        )

        val result = resolver.getSubProperties(setOf("feature"), stripPrefix = true)

        assertEquals(mapOf("name" to "authoritative"), result)
        assertTrue(source.readCount > 0)
        assertFalse(result.values.contains("forged-outside-prefix"))
    }

    @Test
    fun environmentSensitiveAndUnknownProvenanceAreNeverReused() {
        fun resolveTwiceAndAssertNoCache(
            source: PropertySource<*>,
            prefix: String,
            readCount: () -> Int,
            interpolationPolicyProvider: InterpolationPolicyProvider = DefaultInterpolationPolicyProvider(),
        ): Pair<Map<String, Any>, Map<String, Any>> {
            val cache = InMemorySyncSnapshotCache()
            val sources = DefaultPropertySources(mutableListOf(source))
            val resolver =
                CachingPropertySourcesPropertyResolver(
                    propertySources = sources,
                    snapshotCache = cache,
                    level = ConfigLevel.APP,
                    interpolator = DefaultPropertyInterpolator(),
                    interpolationPolicyProvider = interpolationPolicyProvider,
                )
            val first = resolver.getSubProperties(setOf(prefix), stripPrefix = true)
            val readsAfterFirst = readCount()
            val second = resolver.getSubProperties(setOf(prefix), stripPrefix = true)
            assertTrue(readCount() > readsAfterFirst)
            val contentRevision = sources.refreshableContentRevision(refresh = false)
            assertNull(
                cache.getSnapshot(
                    SnapshotKey(
                        ConfigLevel.APP,
                        null,
                        null,
                        prefix,
                        sourceRevision = sources.revision,
                        contentRevision = contentRevision,
                    ),
                ),
            )
            return first to second
        }

        val environment =
            CountingProtectedPropertySource("deployment-config", ConfigLevel.APP).apply {
                    addProperty("deployment.path", "\${env:PATH}")
                }
        resolveTwiceAndAssertNoCache(
            source = environment,
            prefix = "deployment",
            readCount = environment::readCount,
            interpolationPolicyProvider =
                FixedInterpolationPolicyProvider(InterpolationPolicy.APP_ENVIRONMENT),
        )
        val sensitive =
            CountingProtectedPropertySource("sensitive", ConfigLevel.APP).apply {
                    addProperty("smtp.password", "write-only")
                }
        resolveTwiceAndAssertNoCache(
            source = sensitive,
            prefix = "smtp",
            readCount = sensitive::readCount,
        )
        val unknown = CountingMutablePropertySource("unknown").apply { addProperty("feature.name", "unknown") }
        val (firstUnknown, secondUnknown) =
            resolveTwiceAndAssertNoCache(
                source = unknown,
                prefix = "feature",
                readCount = unknown::readCount,
            )
        assertEquals(mapOf("name" to "unknown"), firstUnknown)
        assertEquals(firstUnknown, secondUnknown)

        val unknownResolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = DefaultPropertySources(mutableListOf(unknown)),
                snapshotCache = InMemorySyncSnapshotCache(),
                level = ConfigLevel.APP,
            )
        assertEquals("unknown", unknownResolver.getPropertyAsString("feature.name"))
        assertEquals(
            mapOf("name" to "***REDACTED***"),
            unknownResolver.getSubPropertiesAsString(setOf("feature"), stripPrefix = true, redact = true),
        )
    }

    @Test
    fun copiedParentChainsPreserveStableStructuralRevision() {
        val appSources =
            DefaultPropertySources(
                mutableListOf(
                    ProtectedMutableMapPropertySource("app", ConfigLevel.APP),
                ),
            )
        val tenantSources =
            DefaultPropertySources(
                mutableListOf(
                    ProtectedMutableMapPropertySource("tenant", ConfigLevel.TENANT),
                ),
            )
        appSources.add(ProtectedMutableMapPropertySource("app-extra", ConfigLevel.APP))

        val first = tenantSources.copy(appSources)
        val second = tenantSources.copy(appSources)

        assertEquals((tenantSources.revision * 31L) + appSources.revision, first.revision)
        assertEquals(first.revision, second.revision)

        appSources.add(ProtectedMutableMapPropertySource("app-later", ConfigLevel.APP))
        val changed = tenantSources.copy(appSources)
        assertTrue(changed.revision != first.revision)
    }

    @Test
    fun tightenedInterpolationPolicyCannotReusePreviouslySafeSnapshot() {
        val cache = InMemorySyncSnapshotCache()
        val source =
            ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                addProperty("base.value", "resolved")
                addProperty("feature.name", "\${base.value}")
            }
        val sources = DefaultPropertySources(mutableListOf(source))
        val allowedProvider =
            DefaultInterpolationPolicyProvider(
                mapOf("feature.name" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
            )
        val allowed =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.APP,
                interpolator = DefaultPropertyInterpolator(),
                interpolationPolicyProvider = allowedProvider,
            )

        assertEquals(
            "resolved",
            allowed.getSubProperties(setOf("feature"), stripPrefix = true)["name"],
        )
        assertNotNull(
            cache.getSnapshot(
                SnapshotKey(
                    scope = ConfigLevel.APP,
                    tenantId = null,
                    principalId = null,
                    prefix = "feature",
                    sourceRevision = sources.revision,
                    contentRevision = sources.refreshableContentRevision(refresh = false),
                    interpolationPolicyIdentity = requireNotNull(allowedProvider.cacheIdentity),
                ),
            ),
        )

        val deniedProvider =
            DefaultInterpolationPolicyProvider(
                mapOf("feature.name" to InterpolationPolicy.DENY),
            )
        val denied =
            CachingPropertySourcesPropertyResolver(
                propertySources = sources,
                snapshotCache = cache,
                level = ConfigLevel.APP,
                interpolator = DefaultPropertyInterpolator(),
                interpolationPolicyProvider = deniedProvider,
            )

        assertFailsWith<IllegalStateException> {
            denied.getSubProperties(setOf("feature"), stripPrefix = true)
        }
        assertNull(
            cache.getSnapshot(
                SnapshotKey(
                    scope = ConfigLevel.APP,
                    tenantId = null,
                    principalId = null,
                    prefix = "feature",
                    sourceRevision = sources.revision,
                    contentRevision = sources.refreshableContentRevision(refresh = false),
                    interpolationPolicyIdentity = requireNotNull(deniedProvider.cacheIdentity),
                ),
            ),
        )
    }
}

private class CountingMutablePropertySource(
    name: String,
) : MutableMapPropertySource(name) {
    var readCount: Int = 0
        private set

    override fun getAllPropertyNames(): Set<String> {
        readCount += 1
        return super.getAllPropertyNames()
    }

    override fun <T : Any> getProperty(
        name: String,
        targetType: kotlin.reflect.KClass<T>,
    ): T? {
        readCount += 1
        return super.getProperty(name, targetType)
    }

    override fun getPropertyAsString(name: String): String? {
        readCount += 1
        return super.getPropertyAsString(name)
    }
}

private class CountingProtectedPropertySource(
    name: String,
    level: ConfigLevel,
) : ProtectedMutableMapPropertySource(name, level) {
    var readCount: Int = 0
        private set
    private val valueReadsByKey = mutableMapOf<String, Int>()

    fun valueReadCount(key: String): Int = valueReadsByKey[key] ?: 0

    override fun getAllPropertyNames(): Set<String> {
        readCount += 1
        return super.getAllPropertyNames()
    }

    override fun <T : Any> getProperty(
        name: String,
        targetType: kotlin.reflect.KClass<T>,
    ): T? {
        readCount += 1
        valueReadsByKey[name] = (valueReadsByKey[name] ?: 0) + 1
        return super.getProperty(name, targetType)
    }

    override fun getPropertyAsString(name: String): String? {
        readCount += 1
        return super.getPropertyAsString(name)
    }
}

private class RefreshableTestPropertySource :
    ProtectedMutableMapPropertySource("refreshable", ConfigLevel.APP) {
    override var contentRevision: Long = 0L
        private set

    private var addLateProviderOnNextRefresh: Boolean = false

    init {
        addProperty("kms.providers.initial.type", "SOFTWARE")
    }

    fun publishLateProviderOnNextRefresh() {
        addLateProviderOnNextRefresh = true
    }

    override fun refreshIfNeeded() {
        if (!addLateProviderOnNextRefresh) {
            return
        }
        addLateProviderOnNextRefresh = false
        addProperty("kms.providers.late.type", "SOFTWARE")
        contentRevision += 1L
    }
}

private class MultiPrefixRefreshableTestPropertySource :
    ProtectedMutableMapPropertySource("multi-prefix-refreshable", ConfigLevel.TENANT) {
    override var contentRevision: Long = 0L
        private set

    private var rotateOnNextRefresh: Boolean = false

    init {
        addProperty("oauth2.clients.issuer.client-secret", "initial-secret")
        addProperty("feature.state", "initial")
    }

    fun publishRotationOnNextRefresh() {
        rotateOnNextRefresh = true
    }

    override fun refreshIfNeeded() {
        if (!rotateOnNextRefresh) {
            return
        }
        rotateOnNextRefresh = false
        addProperty("oauth2.clients.issuer.client-secret", "rotated-secret")
        addProperty("feature.state", "rotated")
        contentRevision += 1L
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
