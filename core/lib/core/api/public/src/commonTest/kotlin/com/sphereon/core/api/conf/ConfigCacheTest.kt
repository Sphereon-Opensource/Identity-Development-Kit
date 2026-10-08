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

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ConfigCacheKeyTest {
    @Test
    fun appKeyHasCorrectValues() {
        val key = ConfigCacheKey.app("my.key")

        assertEquals(ConfigLevel.APP, key.scope)
        assertNull(key.tenantId)
        assertNull(key.principalId)
        assertNull(key.sessionId)
        assertEquals("my.key", key.key)
    }

    @Test
    fun tenantKeyHasCorrectValues() {
        val key = ConfigCacheKey.tenant("tenant-123", "my.key")

        assertEquals(ConfigLevel.TENANT, key.scope)
        assertEquals("tenant-123", key.tenantId)
        assertNull(key.principalId)
        assertEquals("my.key", key.key)
    }

    @Test
    fun principalKeyHasCorrectValues() {
        val key = ConfigCacheKey.principal("tenant-123", "user-456", "my.key")

        assertEquals(ConfigLevel.PRINCIPAL, key.scope)
        assertEquals("tenant-123", key.tenantId)
        assertEquals("user-456", key.principalId)
        assertEquals("my.key", key.key)
    }

    @Test
    fun fromContextCreatesCorrectKey() {
        val context = ResolutionContext.principal("tenant-123", "user-456")
        val key = ConfigCacheKey.fromContext(context, "my.key")

        assertEquals(ConfigLevel.PRINCIPAL, key.scope)
        assertEquals("tenant-123", key.tenantId)
        assertEquals("user-456", key.principalId)
        assertEquals("my.key", key.key)
    }

    @Test
    fun toStringKeyGeneratesUniqueKey() {
        val appKey = ConfigCacheKey.app("key")
        val tenantKey = ConfigCacheKey.tenant("t1", "key")
        val principalKey = ConfigCacheKey.principal("t1", "p1", "key")

        // All should be different
        assertTrue(appKey.toStringKey() != tenantKey.toStringKey())
        assertTrue(tenantKey.toStringKey() != principalKey.toStringKey())
        assertTrue(appKey.toStringKey() != principalKey.toStringKey())

        // Same keys should match
        assertEquals(appKey.toStringKey(), ConfigCacheKey.app("key").toStringKey())
    }
}

class CachedConfigValueTest {
    @Test
    fun ofCreatesFromResolvedValue() {
        val resolved =
            ResolvedValue.of(
                value = "test-value",
                source = "test",
                scope = ConfigLevel.APP,
                originalKey = "my.key",
            )

        val cached = CachedConfigValue.of(resolved)

        assertEquals("test-value", cached.value)
        assertEquals("test", cached.metadata.source)
        assertNotNull(cached.cachedAt)
        assertNull(cached.expiresAt)
        assertFalse(cached.isNegativeCache)
    }

    @Test
    fun ofWithTtlSetsExpiration() {
        val resolved =
            ResolvedValue.of(
                value = "test-value",
                source = "test",
                scope = ConfigLevel.APP,
                originalKey = "my.key",
            )

        val cached = CachedConfigValue.of(resolved, ttl = 1.hours)

        assertNotNull(cached.expiresAt)
        assertTrue(cached.expiresAt!! > cached.cachedAt)
    }

    @Test
    fun negativeCreatesNegativeCacheEntry() {
        val cached =
            CachedConfigValue.negative(
                key = "missing.key",
                scope = ConfigLevel.APP,
                source = "test",
            )

        assertNull(cached.value)
        assertTrue(cached.isNegativeCache)
    }

    @Test
    fun isExpiredReturnsFalseForNoExpiration() {
        val resolved =
            ResolvedValue.of(
                value = "test",
                source = "test",
                scope = ConfigLevel.APP,
                originalKey = "key",
            )
        val cached = CachedConfigValue.of(resolved)

        assertFalse(cached.isExpired())
    }

    @Test
    fun isExpiredReturnsFalseForFutureExpiration() {
        val resolved =
            ResolvedValue.of(
                value = "test",
                source = "test",
                scope = ConfigLevel.APP,
                originalKey = "key",
            )
        val cached = CachedConfigValue.of(resolved, ttl = 1.hours)

        assertFalse(cached.isExpired())
    }

    @Test
    fun toResolvedValueReturnsNullForNegativeCache() {
        val cached = CachedConfigValue.negative("key", ConfigLevel.APP, "test")

        assertNull(cached.toResolvedValue<String>())
    }

    @Test
    fun toResolvedValueReturnsValueWhenValid() {
        val resolved =
            ResolvedValue.of(
                value = "test-value",
                source = "test",
                scope = ConfigLevel.APP,
                originalKey = "key",
            )
        val cached = CachedConfigValue.of(resolved, ttl = 1.hours)

        val result = cached.toResolvedValue<String>()

        assertNotNull(result)
        assertEquals("test-value", result.value)
    }
}

class CacheStatsTest {
    @Test
    fun hitRateCalculatesCorrectly() {
        val stats = CacheStats(hits = 80, misses = 20, evictions = 5, size = 100)

        assertEquals(0.8, stats.hitRate, 0.001)
    }

    @Test
    fun hitRateHandlesZeroTotal() {
        val stats = CacheStats(hits = 0, misses = 0, evictions = 0, size = 0)

        assertEquals(0.0, stats.hitRate)
    }

    @Test
    fun hitRateHandles100Percent() {
        val stats = CacheStats(hits = 100, misses = 0, evictions = 0, size = 50)

        assertEquals(1.0, stats.hitRate)
    }
}

class NoOpConfigCacheTest {
    @Test
    fun getReturnsNull() =
        runTest {
            val key = ConfigCacheKey.app("test")

            assertNull(NoOpConfigCache.get(key))
        }

    @Test
    fun getManyReturnsNullForAllKeys() =
        runTest {
            val keys =
                listOf(
                    ConfigCacheKey.app("key1"),
                    ConfigCacheKey.app("key2"),
                )

            val results = NoOpConfigCache.getMany(keys)

            assertEquals(2, results.size)
            assertTrue(results.values.all { it == null })
        }

    @Test
    fun getStatsReturnsZeros() {
        val stats = NoOpConfigCache.getStats()

        assertEquals(0L, stats.hits)
        assertEquals(0L, stats.misses)
        assertEquals(0L, stats.size)
    }
}

class InMemoryConfigCacheTest {
    @Test
    fun putAndGetWorks() =
        runTest {
            val cache = InMemoryConfigCache()
            val key = ConfigCacheKey.app("my.key")
            val resolved =
                ResolvedValue.of(
                    value = "test-value",
                    source = "test",
                    scope = ConfigLevel.APP,
                    originalKey = "my.key",
                )
            val value = CachedConfigValue.of(resolved)

            cache.put(key, value, 5.minutes)
            val result = cache.get(key)

            assertNotNull(result)
            assertEquals("test-value", result.value)
        }

    @Test
    fun getReturnsNullForMissingKey() =
        runTest {
            val cache = InMemoryConfigCache()
            val key = ConfigCacheKey.app("missing")

            assertNull(cache.get(key))
        }

    @Test
    fun statsTrackHitsAndMisses() =
        runTest {
            val cache = InMemoryConfigCache()
            val key = ConfigCacheKey.app("key")
            val resolved =
                ResolvedValue.of(
                    value = "value",
                    source = "test",
                    scope = ConfigLevel.APP,
                    originalKey = "key",
                )

            // Miss
            cache.get(key)
            // Put
            cache.put(key, CachedConfigValue.of(resolved), 5.minutes)
            // Hit
            cache.get(key)
            // Hit
            cache.get(key)

            val stats = cache.getStats()
            assertEquals(2L, stats.hits)
            assertEquals(1L, stats.misses)
            assertEquals(1L, stats.size)
        }

    @Test
    fun invalidateTenantRemovesTenantEntries() =
        runTest {
            val cache = InMemoryConfigCache()
            val tenant1Key = ConfigCacheKey.tenant("tenant-1", "key")
            val tenant2Key = ConfigCacheKey.tenant("tenant-2", "key")
            val resolved =
                ResolvedValue.of(
                    value = "value",
                    source = "test",
                    scope = ConfigLevel.TENANT,
                    originalKey = "key",
                )

            cache.put(tenant1Key, CachedConfigValue.of(resolved), 5.minutes)
            cache.put(tenant2Key, CachedConfigValue.of(resolved), 5.minutes)

            cache.invalidateTenant("tenant-1")

            assertNull(cache.get(tenant1Key))
            assertNotNull(cache.get(tenant2Key))
            assertEquals(1L, cache.getStats().invalidations)
        }

    @Test
    fun invalidatePrincipalRemovesPrincipalEntries() =
        runTest {
            val cache = InMemoryConfigCache()
            val principal1Key = ConfigCacheKey.principal("tenant-1", "user-1", "key")
            val principal2Key = ConfigCacheKey.principal("tenant-1", "user-2", "key")
            val resolved =
                ResolvedValue.of(
                    value = "value",
                    source = "test",
                    scope = ConfigLevel.PRINCIPAL,
                    originalKey = "key",
                )

            cache.put(principal1Key, CachedConfigValue.of(resolved), 5.minutes)
            cache.put(principal2Key, CachedConfigValue.of(resolved), 5.minutes)

            cache.invalidatePrincipal("tenant-1", "user-1")

            assertNull(cache.get(principal1Key))
            assertNotNull(cache.get(principal2Key))
            assertEquals(1L, cache.getStats().invalidations)
        }

    @Test
    fun clearRemovesAllEntries() =
        runTest {
            val cache = InMemoryConfigCache()
            val key1 = ConfigCacheKey.app("key1")
            val key2 = ConfigCacheKey.app("key2")
            val resolved =
                ResolvedValue.of(
                    value = "value",
                    source = "test",
                    scope = ConfigLevel.APP,
                    originalKey = "key",
                )

            cache.put(key1, CachedConfigValue.of(resolved), 5.minutes)
            cache.put(key2, CachedConfigValue.of(resolved), 5.minutes)

            cache.clear()

            assertNull(cache.get(key1))
            assertNull(cache.get(key2))
            assertEquals(0L, cache.getStats().size)
        }

    @Test
    fun evictsWhenAtCapacity() =
        runTest {
            val cache = InMemoryConfigCache(maxEntries = 2)
            val resolved =
                ResolvedValue.of(
                    value = "value",
                    source = "test",
                    scope = ConfigLevel.APP,
                    originalKey = "key",
                )

            cache.put(ConfigCacheKey.app("key1"), CachedConfigValue.of(resolved), 5.minutes)
            cache.put(ConfigCacheKey.app("key2"), CachedConfigValue.of(resolved), 5.minutes)
            cache.put(ConfigCacheKey.app("key3"), CachedConfigValue.of(resolved), 5.minutes)

            val stats = cache.getStats()
            assertEquals(2L, stats.size)
            assertEquals(1L, stats.evictions)
        }

    @Test
    fun getManyReturnsMultipleValues() =
        runTest {
            val cache = InMemoryConfigCache()
            val key1 = ConfigCacheKey.app("key1")
            val key2 = ConfigCacheKey.app("key2")
            val key3 = ConfigCacheKey.app("key3")

            val resolved1 = ResolvedValue.of("value1", "test", ConfigLevel.APP, "key1")
            val resolved2 = ResolvedValue.of("value2", "test", ConfigLevel.APP, "key2")

            cache.put(key1, CachedConfigValue.of(resolved1), 5.minutes)
            cache.put(key2, CachedConfigValue.of(resolved2), 5.minutes)

            val results = cache.getMany(listOf(key1, key2, key3))

            assertEquals(3, results.size)
            assertNotNull(results[key1])
            assertNotNull(results[key2])
            assertNull(results[key3])
        }

    @Test
    fun invalidateByPrefixDoesNotRemoveSimilarPrefix() =
        runTest {
            val cache = InMemoryConfigCache()
            val dbKey = ConfigCacheKey.app("db.host")
            val databaseKey = ConfigCacheKey.app("database.host")
            val resolved = ResolvedValue.of("value", "test", ConfigLevel.APP, "key")

            cache.put(dbKey, CachedConfigValue.of(resolved), 5.minutes)
            cache.put(databaseKey, CachedConfigValue.of(resolved), 5.minutes)

            cache.invalidateByPrefix("db")

            assertNull(cache.get(dbKey))
            assertNotNull(cache.get(databaseKey))
            assertEquals(1L, cache.getStats().invalidations)
        }

    @Test
    fun expiredEntriesIncreaseExpiredCounter() =
        runTest {
            val cache = InMemoryConfigCache()
            val key = ConfigCacheKey.app("short.ttl")
            val resolved = ResolvedValue.of("value", "test", ConfigLevel.APP, "short.ttl")

            cache.put(key, CachedConfigValue.of(resolved), 1.milliseconds)
            // Use a real-time busy-wait instead of delay() because runTest advances
            // virtual time only, while InMemoryConfigCache.isExpired() checks Clock.System (wall clock).
            // On single-threaded platforms (wasmJs/JS), delay() doesn't advance wall clock time.
            val deadline =
                kotlin.time.Clock.System
                    .now() + 5.milliseconds
            while (kotlin.time.Clock.System
                    .now() < deadline
            ) { /* spin */ }
            val result = cache.get(key)

            assertNull(result)
            assertEquals(1L, cache.getStats().expired)
        }

    @Test
    fun clearCountsAsInvalidationForExistingEntries() =
        runTest {
            val cache = InMemoryConfigCache()
            val resolved = ResolvedValue.of("value", "test", ConfigLevel.APP, "key")
            cache.put(ConfigCacheKey.app("k1"), CachedConfigValue.of(resolved), 5.minutes)
            cache.put(ConfigCacheKey.app("k2"), CachedConfigValue.of(resolved), 5.minutes)

            cache.clear()

            val stats = cache.getStats()
            assertEquals(0L, stats.size)
            assertEquals(2L, stats.invalidations)
        }
}

class SnapshotKeyTest {
    @Test
    fun toStringKeyGeneratesUniqueKey() {
        val key1 = SnapshotKey(ConfigLevel.APP, null, null, "db")
        val key2 = SnapshotKey(ConfigLevel.TENANT, "t1", null, "db")
        val key3 = SnapshotKey(ConfigLevel.PRINCIPAL, "t1", "p1", "db")

        assertTrue(key1.toStringKey() != key2.toStringKey())
        assertTrue(key2.toStringKey() != key3.toStringKey())
    }
}

class InMemorySnapshotCacheTest {
    @Test
    fun putAndGetSnapshotWorks() =
        runTest {
            val cache = InMemorySnapshotCache()
            val key = SnapshotKey(ConfigLevel.APP, null, null, "db")
            val snapshot =
                ConfigSnapshot(
                    values = emptyMap(),
                    createdAt = Clock.System.now(),
                    expiresAt = Clock.System.now() + 1.hours,
                )

            cache.putSnapshot(key, snapshot)
            val result = cache.getSnapshot(key)

            assertNotNull(result)
        }

    @Test
    fun getSnapshotReturnsNullForMissing() =
        runTest {
            val cache = InMemorySnapshotCache()
            val key = SnapshotKey(ConfigLevel.APP, null, null, "missing")

            assertNull(cache.getSnapshot(key))
        }

    @Test
    fun clearRemovesAllSnapshots() =
        runTest {
            val cache = InMemorySnapshotCache()
            val key = SnapshotKey(ConfigLevel.APP, null, null, "db")
            val snapshot =
                ConfigSnapshot(
                    values = emptyMap(),
                    createdAt = Clock.System.now(),
                    expiresAt = Clock.System.now() + 1.hours,
                )

            cache.putSnapshot(key, snapshot)
            cache.clear()

            assertNull(cache.getSnapshot(key))
        }

    @Test
    fun statsTrackHitsMissesEvictionsAndInvalidations() =
        runTest {
            val cache = InMemorySnapshotCache(maxEntries = 1)
            val key1 = SnapshotKey(ConfigLevel.APP, null, null, "db.one")
            val key2 = SnapshotKey(ConfigLevel.APP, null, null, "db.two")
            val snapshot =
                ConfigSnapshot(
                    values = emptyMap(),
                    createdAt = Clock.System.now(),
                    expiresAt = Clock.System.now() + 1.hours,
                )

            cache.getSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "missing"))
            cache.putSnapshot(key1, snapshot)
            cache.getSnapshot(key1)
            cache.putSnapshot(key2, snapshot)
            cache.invalidateByPrefix("db")

            val stats = cache.getStats()
            assertEquals(1L, stats.hits)
            assertEquals(1L, stats.misses)
            assertEquals(1L, stats.evictions)
            assertEquals(1L, stats.invalidations)
            assertEquals(0L, stats.size)
        }

    @Test
    fun invalidateTenantRemovesTenantAndPrincipalSnapshots() =
        runTest {
            val cache = InMemorySnapshotCache()
            val tenantKey = SnapshotKey(ConfigLevel.TENANT, "tenant-1", null, "kms.providers")
            val principalKey = SnapshotKey(ConfigLevel.PRINCIPAL, "tenant-1", "user-1", "kms.providers")
            val otherTenantKey = SnapshotKey(ConfigLevel.TENANT, "tenant-2", null, "kms.providers")
            val snapshot = ConfigSnapshot(values = emptyMap(), createdAt = Clock.System.now(), expiresAt = Clock.System.now() + 1.hours)

            cache.putSnapshot(tenantKey, snapshot)
            cache.putSnapshot(principalKey, snapshot)
            cache.putSnapshot(otherTenantKey, snapshot)

            cache.invalidateTenant("tenant-1")

            assertNull(cache.getSnapshot(tenantKey))
            assertNull(cache.getSnapshot(principalKey))
            assertNotNull(cache.getSnapshot(otherTenantKey))
            assertEquals(2L, cache.getStats().invalidations)
        }

    @Test
    fun invalidatePrincipalRemovesOnlyMatchingPrincipalSnapshots() =
        runTest {
            val cache = InMemorySnapshotCache()
            val principalKey = SnapshotKey(ConfigLevel.PRINCIPAL, "tenant-1", "user-1", "kms.providers")
            val otherPrincipalKey = SnapshotKey(ConfigLevel.PRINCIPAL, "tenant-1", "user-2", "kms.providers")
            val tenantKey = SnapshotKey(ConfigLevel.TENANT, "tenant-1", null, "kms.providers")
            val snapshot = ConfigSnapshot(values = emptyMap(), createdAt = Clock.System.now(), expiresAt = Clock.System.now() + 1.hours)

            cache.putSnapshot(principalKey, snapshot)
            cache.putSnapshot(otherPrincipalKey, snapshot)
            cache.putSnapshot(tenantKey, snapshot)

            cache.invalidatePrincipal("tenant-1", "user-1")

            assertNull(cache.getSnapshot(principalKey))
            assertNotNull(cache.getSnapshot(otherPrincipalKey))
            assertNotNull(cache.getSnapshot(tenantKey))
            assertEquals(1L, cache.getStats().invalidations)
        }
}

class TtlConfigTest {
    @Test
    fun forScopeReturnsCorrectTtl() {
        val config =
            TtlConfig(
                app = 10.minutes,
                tenant = 5.minutes,
                principal = 2.minutes,
            )

        assertEquals(10.minutes, config.forScope(ConfigLevel.APP))
        assertEquals(5.minutes, config.forScope(ConfigLevel.TENANT))
        assertEquals(2.minutes, config.forScope(ConfigLevel.PRINCIPAL))
    }

    @Test
    fun defaultValuesAreCorrect() {
        val config = TtlConfig()

        assertEquals(10.minutes, config.app)
        assertEquals(5.minutes, config.tenant)
        assertEquals(2.minutes, config.principal)
    }
}

class CacheConfigTest {
    @Test
    fun defaultValuesAreCorrect() {
        val config = CacheConfig()

        assertTrue(config.enabled)
        assertEquals(10000, config.maxEntries)
        assertEquals(EvictionPolicy.LRU, config.evictionPolicy)
        assertTrue(config.snapshotEnabled)
        assertEquals(30.minutes, config.snapshotTtl)
    }
}

class ConfigSnapshotTest {
    @Test
    fun isExpiredReturnsFalseForNoExpiration() {
        val snapshot =
            ConfigSnapshot(
                values = emptyMap(),
                createdAt = Clock.System.now(),
                expiresAt = null,
            )

        assertFalse(snapshot.isExpired())
    }

    @Test
    fun isExpiredReturnsFalseForFutureExpiration() {
        val snapshot =
            ConfigSnapshot(
                values = emptyMap(),
                createdAt = Clock.System.now(),
                expiresAt = Clock.System.now() + 1.hours,
            )

        assertFalse(snapshot.isExpired())
    }

    @Test
    fun structuralConsistencyRejectsMapKeyMetadataMismatch() {
        val now = Clock.System.now()
        val snapshot =
            ConfigSnapshot(
                values =
                    mapOf(
                        "feature.name" to
                            CachedConfigValue(
                                value = "visible",
                                metadata =
                                    ResolutionMetadata(
                                        source = "test",
                                        scope = ConfigLevel.APP,
                                        originalKey = "other.name",
                                        normalizedKey = "other.name",
                                        order = 0,
                                        isSecret = false,
                                        isInterpolated = false,
                                        resolvedAt = now,
                                        ttl = 1.hours,
                                        provenance = ResolutionProvenance.known(ConfigLevel.APP),
                                    ),
                                cachedAt = now,
                                expiresAt = now + 1.hours,
                            ),
                    ),
                createdAt = now,
                expiresAt = now + 1.hours,
            )

        assertFalse(snapshot.isStructurallyConsistent())
        assertNull(snapshot.safeCopyOrNull())
    }

    @Test
    fun structuralConsistencyRejectsTypeAndProvenanceMismatch() {
        val now = Clock.System.now()
        val metadata =
            ResolutionMetadata(
                source = "test",
                scope = ConfigLevel.APP,
                originalKey = "feature.enabled",
                normalizedKey = "feature.enabled",
                order = 0,
                isSecret = false,
                isInterpolated = false,
                resolvedAt = now,
                ttl = 1.hours,
                provenance = ResolutionProvenance.known(ConfigLevel.TENANT),
            )
        val snapshot =
            ConfigSnapshot(
                values =
                    mapOf(
                        "feature.enabled" to
                            CachedConfigValue(
                                stringValue = "not-a-boolean",
                                metadata = metadata,
                                cachedAt = now,
                                expiresAt = now + 1.hours,
                                valueType = CachedConfigValueType.BOOLEAN,
                            ),
                    ),
                createdAt = now,
                expiresAt = now + 1.hours,
            )

        assertFalse(snapshot.isStructurallyConsistent())
        assertNull(snapshot.safeCopyOrNull())
    }

    @Test
    fun syncCacheNeverReturnsStructurallyInconsistentSnapshot() {
        val now = Clock.System.now()
        val cache = InMemorySyncSnapshotCache()
        val key = SnapshotKey(ConfigLevel.APP, null, null, "feature")
        val malformed =
            ConfigSnapshot(
                values =
                    mapOf(
                        "feature.name" to
                            CachedConfigValue(
                                value = "forged",
                                metadata =
                                    ResolutionMetadata(
                                        source = "test",
                                        scope = ConfigLevel.APP,
                                        originalKey = "feature.name",
                                        normalizedKey = "different.name",
                                        order = 0,
                                        isSecret = false,
                                        isInterpolated = false,
                                        resolvedAt = now,
                                        ttl = 1.hours,
                                        provenance = ResolutionProvenance.known(ConfigLevel.APP),
                                    ),
                                cachedAt = now,
                                expiresAt = now + 1.hours,
                            ),
                    ),
                createdAt = now,
                expiresAt = now + 1.hours,
            )

        cache.putSnapshot(key, malformed)

        assertNull(cache.getSnapshot(key))
    }
}

class NoOpSnapshotCacheTest {
    @Test
    fun getSnapshotReturnsNull() =
        runTest {
            val key = SnapshotKey(ConfigLevel.APP, null, null, "test")

            assertNull(NoOpSnapshotCache.getSnapshot(key))
        }

    @Test
    fun putSnapshotDoesNothing() =
        runTest {
            val key = SnapshotKey(ConfigLevel.APP, null, null, "test")
            val snapshot =
                ConfigSnapshot(
                    values = emptyMap(),
                    createdAt = Clock.System.now(),
                    expiresAt = Clock.System.now() + 1.hours,
                )

            NoOpSnapshotCache.putSnapshot(key, snapshot)

            // Still returns null after put
            assertNull(NoOpSnapshotCache.getSnapshot(key))
        }

    @Test
    fun invalidateByPrefixDoesNothing() =
        runTest {
            NoOpSnapshotCache.invalidateByPrefix("test")
            // No exception
        }

    @Test
    fun clearDoesNothing() =
        runTest {
            NoOpSnapshotCache.clear()
            // No exception
        }

    @Test
    fun getStatsReturnsZeros() {
        val stats = NoOpSnapshotCache.getStats()

        assertEquals(0L, stats.hits)
        assertEquals(0L, stats.misses)
        assertEquals(0L, stats.evictions)
        assertEquals(0L, stats.size)
    }
}

class NoOpSyncSnapshotCacheTest {
    @Test
    fun getSnapshotReturnsNull() {
        val key = SnapshotKey(ConfigLevel.APP, null, null, "test")

        assertNull(NoOpSyncSnapshotCache.getSnapshot(key))
    }

    @Test
    fun putSnapshotDoesNothing() {
        val key = SnapshotKey(ConfigLevel.APP, null, null, "test")
        val snapshot =
            ConfigSnapshot(
                values = emptyMap(),
                createdAt = Clock.System.now(),
                expiresAt = Clock.System.now() + 1.hours,
            )

        NoOpSyncSnapshotCache.putSnapshot(key, snapshot)

        // Still returns null after put
        assertNull(NoOpSyncSnapshotCache.getSnapshot(key))
    }

    @Test
    fun invalidateByPrefixDoesNothing() {
        NoOpSyncSnapshotCache.invalidateByPrefix("test")
        // No exception
    }

    @Test
    fun invalidateTenantDoesNothing() {
        NoOpSyncSnapshotCache.invalidateTenant("tenant-123")
        // No exception
    }

    @Test
    fun invalidatePrincipalDoesNothing() {
        NoOpSyncSnapshotCache.invalidatePrincipal("tenant-123", "user-456")
        // No exception
    }

    @Test
    fun clearDoesNothing() {
        NoOpSyncSnapshotCache.clear()
        // No exception
    }

    @Test
    fun getStatsReturnsZeros() {
        val stats = NoOpSyncSnapshotCache.getStats()

        assertEquals(0L, stats.hits)
        assertEquals(0L, stats.misses)
        assertEquals(0L, stats.evictions)
        assertEquals(0L, stats.size)
    }
}

class InMemorySyncSnapshotCacheTest {
    @Test
    fun putAndGetSnapshotWorks() {
        val cache = InMemorySyncSnapshotCache()
        val key = SnapshotKey(ConfigLevel.APP, null, null, "db")
        val snapshot =
            ConfigSnapshot(
                values = emptyMap(),
                createdAt = Clock.System.now(),
                expiresAt = Clock.System.now() + 1.hours,
            )

        cache.putSnapshot(key, snapshot)
        val result = cache.getSnapshot(key)

        assertNotNull(result)
    }

    @Test
    fun getSnapshotReturnsNullForMissing() {
        val cache = InMemorySyncSnapshotCache()
        val key = SnapshotKey(ConfigLevel.APP, null, null, "missing")

        assertNull(cache.getSnapshot(key))
    }

    @Test
    fun clearRemovesAllSnapshots() {
        val cache = InMemorySyncSnapshotCache()
        val key = SnapshotKey(ConfigLevel.APP, null, null, "db")
        val snapshot =
            ConfigSnapshot(
                values = emptyMap(),
                createdAt = Clock.System.now(),
                expiresAt = Clock.System.now() + 1.hours,
            )

        cache.putSnapshot(key, snapshot)
        cache.clear()

        assertNull(cache.getSnapshot(key))
    }

    @Test
    fun invalidateByPrefixRemovesMatchingEntries() {
        val cache = InMemorySyncSnapshotCache()
        val key1 = SnapshotKey(ConfigLevel.APP, null, null, "db.config")
        val key2 = SnapshotKey(ConfigLevel.APP, null, null, "kms.providers")
        val snapshot =
            ConfigSnapshot(
                values = emptyMap(),
                createdAt = Clock.System.now(),
                expiresAt = Clock.System.now() + 1.hours,
            )

        cache.putSnapshot(key1, snapshot)
        cache.putSnapshot(key2, snapshot)

        cache.invalidateByPrefix("db")

        assertNull(cache.getSnapshot(key1))
        assertNotNull(cache.getSnapshot(key2))
    }

    @Test
    fun invalidateByPrefixDoesNotRemoveSimilarPrefix() {
        val cache = InMemorySyncSnapshotCache()
        val dbKey = SnapshotKey(ConfigLevel.APP, null, null, "db.config")
        val databaseKey = SnapshotKey(ConfigLevel.APP, null, null, "database.config")
        val snapshot =
            ConfigSnapshot(
                values = emptyMap(),
                createdAt = Clock.System.now(),
                expiresAt = Clock.System.now() + 1.hours,
            )

        cache.putSnapshot(dbKey, snapshot)
        cache.putSnapshot(databaseKey, snapshot)

        cache.invalidateByPrefix("db")

        assertNull(cache.getSnapshot(dbKey))
        assertNotNull(cache.getSnapshot(databaseKey))
    }

    @Test
    fun evictsWhenAtCapacity() {
        val cache = InMemorySyncSnapshotCache(maxEntries = 2)
        val snapshot =
            ConfigSnapshot(
                values = emptyMap(),
                createdAt = Clock.System.now(),
                expiresAt = Clock.System.now() + 1.hours,
            )

        cache.putSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "key1"), snapshot)
        cache.putSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "key2"), snapshot)
        cache.putSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "key3"), snapshot)

        // Should have evicted oldest entry (key1)
        assertNull(cache.getSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "key1")))
        assertNotNull(cache.getSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "key3")))
    }

    @Test
    fun putSnapshotWithoutExpirationUsesDefaultTtl() {
        val cache = InMemorySyncSnapshotCache(defaultTtl = 1.hours)
        val key = SnapshotKey(ConfigLevel.APP, null, null, "test")
        val snapshot =
            ConfigSnapshot(
                values = emptyMap(),
                createdAt = Clock.System.now(),
                expiresAt = null, // No expiration
            )

        cache.putSnapshot(key, snapshot)
        val result = cache.getSnapshot(key)

        assertNotNull(result)
        assertNotNull(result.expiresAt) // Should have been set
    }

    @Test
    fun statsTrackHitsMissesEvictionsAndInvalidations() {
        val cache = InMemorySyncSnapshotCache(maxEntries = 1)
        val key1 = SnapshotKey(ConfigLevel.APP, null, null, "db.one")
        val key2 = SnapshotKey(ConfigLevel.APP, null, null, "db.two")
        val snapshot =
            ConfigSnapshot(
                values = emptyMap(),
                createdAt = Clock.System.now(),
                expiresAt = Clock.System.now() + 1.hours,
            )

        cache.getSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "missing"))
        cache.putSnapshot(key1, snapshot)
        cache.getSnapshot(key1)
        cache.putSnapshot(key2, snapshot)
        cache.invalidateByPrefix("db")

        val stats = cache.getStats()
        assertEquals(1L, stats.hits)
        assertEquals(1L, stats.misses)
        assertEquals(1L, stats.evictions)
        assertEquals(1L, stats.invalidations)
        assertEquals(0L, stats.size)
    }

    @Test
    fun invalidateTenantRemovesTenantAndPrincipalSnapshots() {
        val cache = InMemorySyncSnapshotCache()
        val tenantKey = SnapshotKey(ConfigLevel.TENANT, "tenant-1", null, "kms.providers")
        val principalKey = SnapshotKey(ConfigLevel.PRINCIPAL, "tenant-1", "user-1", "kms.providers")
        val otherTenantKey = SnapshotKey(ConfigLevel.TENANT, "tenant-2", null, "kms.providers")
        val snapshot = ConfigSnapshot(values = emptyMap(), createdAt = Clock.System.now(), expiresAt = Clock.System.now() + 1.hours)

        cache.putSnapshot(tenantKey, snapshot)
        cache.putSnapshot(principalKey, snapshot)
        cache.putSnapshot(otherTenantKey, snapshot)

        cache.invalidateTenant("tenant-1")

        assertNull(cache.getSnapshot(tenantKey))
        assertNull(cache.getSnapshot(principalKey))
        assertNotNull(cache.getSnapshot(otherTenantKey))
        assertEquals(2L, cache.getStats().invalidations)
    }

    @Test
    fun invalidatePrincipalRemovesOnlyMatchingPrincipalSnapshots() {
        val cache = InMemorySyncSnapshotCache()
        val principalKey = SnapshotKey(ConfigLevel.PRINCIPAL, "tenant-1", "user-1", "kms.providers")
        val otherPrincipalKey = SnapshotKey(ConfigLevel.PRINCIPAL, "tenant-1", "user-2", "kms.providers")
        val tenantKey = SnapshotKey(ConfigLevel.TENANT, "tenant-1", null, "kms.providers")
        val snapshot = ConfigSnapshot(values = emptyMap(), createdAt = Clock.System.now(), expiresAt = Clock.System.now() + 1.hours)

        cache.putSnapshot(principalKey, snapshot)
        cache.putSnapshot(otherPrincipalKey, snapshot)
        cache.putSnapshot(tenantKey, snapshot)

        cache.invalidatePrincipal("tenant-1", "user-1")

        assertNull(cache.getSnapshot(principalKey))
        assertNotNull(cache.getSnapshot(otherPrincipalKey))
        assertNotNull(cache.getSnapshot(tenantKey))
        assertEquals(1L, cache.getStats().invalidations)
    }
}

class InMemorySnapshotCacheExtendedTest {
    @Test
    fun invalidateByPrefixRemovesMatchingEntries() =
        runTest {
            val cache = InMemorySnapshotCache()
            val key1 = SnapshotKey(ConfigLevel.APP, null, null, "db.config")
            val key2 = SnapshotKey(ConfigLevel.APP, null, null, "kms.providers")
            val snapshot =
                ConfigSnapshot(
                    values = emptyMap(),
                    createdAt = Clock.System.now(),
                    expiresAt = Clock.System.now() + 1.hours,
                )

            cache.putSnapshot(key1, snapshot)
            cache.putSnapshot(key2, snapshot)

            cache.invalidateByPrefix("db")

            assertNull(cache.getSnapshot(key1))
            assertNotNull(cache.getSnapshot(key2))
        }

    @Test
    fun invalidateByPrefixDoesNotRemoveSimilarPrefix() =
        runTest {
            val cache = InMemorySnapshotCache()
            val dbKey = SnapshotKey(ConfigLevel.APP, null, null, "db.config")
            val databaseKey = SnapshotKey(ConfigLevel.APP, null, null, "database.config")
            val snapshot =
                ConfigSnapshot(
                    values = emptyMap(),
                    createdAt = Clock.System.now(),
                    expiresAt = Clock.System.now() + 1.hours,
                )

            cache.putSnapshot(dbKey, snapshot)
            cache.putSnapshot(databaseKey, snapshot)

            cache.invalidateByPrefix("db")

            assertNull(cache.getSnapshot(dbKey))
            assertNotNull(cache.getSnapshot(databaseKey))
        }

    @Test
    fun evictsWhenAtCapacity() =
        runTest {
            val cache = InMemorySnapshotCache(maxEntries = 2)
            val snapshot =
                ConfigSnapshot(
                    values = emptyMap(),
                    createdAt = Clock.System.now(),
                    expiresAt = Clock.System.now() + 1.hours,
                )

            cache.putSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "key1"), snapshot)
            cache.putSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "key2"), snapshot)
            cache.putSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "key3"), snapshot)

            // Should have evicted oldest entry (key1)
            assertNull(cache.getSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "key1")))
            assertNotNull(cache.getSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "key3")))
        }

    @Test
    fun putSnapshotWithoutExpirationUsesDefaultTtl() =
        runTest {
            val cache = InMemorySnapshotCache(defaultTtl = 1.hours)
            val key = SnapshotKey(ConfigLevel.APP, null, null, "test")
            val snapshot =
                ConfigSnapshot(
                    values = emptyMap(),
                    createdAt = Clock.System.now(),
                    expiresAt = null, // No expiration
                )

            cache.putSnapshot(key, snapshot)
            val result = cache.getSnapshot(key)

            assertNotNull(result)
            assertNotNull(result.expiresAt) // Should have been set
        }
}

class AsyncToSyncCacheAdapterTest {
    @Test
    fun warmupAsyncPopulatesSyncCache() =
        runTest {
            val asyncCache = InMemorySnapshotCache()
            val syncCache = InMemorySyncSnapshotCache()
            val adapter = AsyncToSyncCacheAdapter(asyncCache, syncCache)

            val key = SnapshotKey(ConfigLevel.APP, null, null, "test.prefix")
            val snapshot =
                ConfigSnapshot(
                    values = emptyMap(),
                    createdAt = Clock.System.now(),
                    expiresAt = Clock.System.now() + 1.hours,
                )

            // Put in async cache
            asyncCache.putSnapshot(key, snapshot)

            // Warmup should transfer to sync cache
            adapter.warmupAsync(setOf("test.prefix"), ConfigLevel.APP, null, null)

            // Should now be in sync cache via adapter
            assertNotNull(adapter.getSnapshot(key))
            assertTrue(adapter.isWarmedUp(key))
            assertTrue(adapter.hasWarmedUp())
        }

    @Test
    fun hasWarmedUpReturnsFalseBeforeWarmup() {
        val adapter = AsyncToSyncCacheAdapter(InMemorySnapshotCache(), InMemorySyncSnapshotCache())

        assertFalse(adapter.hasWarmedUp())
    }

    @Test
    fun isWarmedUpReturnsFalseForUnwarmedKey() {
        val adapter = AsyncToSyncCacheAdapter(InMemorySnapshotCache(), InMemorySyncSnapshotCache())
        val key = SnapshotKey(ConfigLevel.APP, null, null, "unwarmed")

        assertFalse(adapter.isWarmedUp(key))
    }
}

class DefaultConfigCacheWarmupTest {
    @Test
    fun warmupAsyncCompletesWithoutError() =
        runTest {
            val syncCache = InMemorySyncSnapshotCache()
            val warmup = DefaultConfigCacheWarmup(syncCache)

            warmup.warmupAsync(setOf("test"), ConfigLevel.APP, null, null)

            // Should not throw
        }

    @Test
    fun isWarmedUpAlwaysReturnsTrue() {
        val syncCache = InMemorySyncSnapshotCache()
        val warmup = DefaultConfigCacheWarmup(syncCache)
        val key = SnapshotKey(ConfigLevel.APP, null, null, "any")

        assertTrue(warmup.isWarmedUp(key))
    }

    @Test
    fun hasWarmedUpReturnsFalseBeforeWarmup() {
        val syncCache = InMemorySyncSnapshotCache()
        val warmup = DefaultConfigCacheWarmup(syncCache)

        assertFalse(warmup.hasWarmedUp())
    }

    @Test
    fun hasWarmedUpReturnsTrueAfterWarmup() =
        runTest {
            val syncCache = InMemorySyncSnapshotCache()
            val warmup = DefaultConfigCacheWarmup(syncCache)

            warmup.warmupAsync(setOf("test"), ConfigLevel.APP, null, null)

            assertTrue(warmup.hasWarmedUp())
        }
}

class InMemoryConfigCacheExtendedTest {
    @Test
    fun putManyAddsMultipleEntries() =
        runTest {
            val cache = InMemoryConfigCache()
            val key1 = ConfigCacheKey.app("key1")
            val key2 = ConfigCacheKey.app("key2")

            val resolved1 = ResolvedValue.of("value1", "test", ConfigLevel.APP, "key1")
            val resolved2 = ResolvedValue.of("value2", "test", ConfigLevel.APP, "key2")

            cache.putMany(
                mapOf(
                    key1 to CachedConfigValue.of(resolved1),
                    key2 to CachedConfigValue.of(resolved2),
                ),
            )

            assertNotNull(cache.get(key1))
            assertNotNull(cache.get(key2))
        }

    @Test
    fun invalidateByPrefixRemovesMatchingKeys() =
        runTest {
            val cache = InMemoryConfigCache()
            val key1 = ConfigCacheKey.app("db.host")
            val key2 = ConfigCacheKey.app("kms.type")

            val resolved = ResolvedValue.of("value", "test", ConfigLevel.APP, "key")

            cache.put(key1, CachedConfigValue.of(resolved), 5.minutes)
            cache.put(key2, CachedConfigValue.of(resolved), 5.minutes)

            cache.invalidateByPrefix("db")

            assertNull(cache.get(key1))
            assertNotNull(cache.get(key2))
        }

    @Test
    fun prefetchIsNoOpForInMemoryCache() =
        runTest {
            val cache = InMemoryConfigCache()

            // Should not throw
            cache.prefetch(ConfigLevel.APP, null, null, "test")
        }

    @Test
    fun putWithExistingExpirationDoesNotOverride() =
        runTest {
            val cache = InMemoryConfigCache()
            val key = ConfigCacheKey.app("key")
            val now = Clock.System.now()
            val customExpiration = now + 10.hours

            val resolved = ResolvedValue.of("value", "test", ConfigLevel.APP, "key")
            val value =
                CachedConfigValue(
                    value = "value",
                    metadata = resolved.metadata,
                    cachedAt = now,
                    expiresAt = customExpiration,
                    preserveType = true,
                )

            cache.put(key, value, 5.minutes)
            val result = cache.get(key)

            assertNotNull(result)
            assertEquals(customExpiration, result.expiresAt)
        }
}

class CachedConfigValueExtendedTest {
    @Test
    fun toResolvedValueReturnsNullWhenExpired() {
        val now = Clock.System.now()
        val resolved = ResolvedValue.of("test-value", "test", ConfigLevel.APP, "key")

        // Create with past expiration
        val cached =
            CachedConfigValue(
                value = "test-value",
                metadata = resolved.metadata,
                cachedAt = now - 2.hours,
                expiresAt = now - 1.hours, // Already expired
                preserveType = true,
            )

        // Should be expired
        assertTrue(cached.isExpired())
        assertNull(cached.toResolvedValue<String>())
    }

    @Test
    fun toResolvedValueReturnsNullForNullValue() {
        val now = Clock.System.now()
        val cached =
            CachedConfigValue(
                value = null,
                metadata =
                    ResolutionMetadata(
                        source = "test",
                        scope = ConfigLevel.APP,
                        originalKey = "key",
                        normalizedKey = "key",
                        order = 0,
                        isSecret = false,
                        isInterpolated = false,
                        resolvedAt = now,
                        ttl = 1.hours,
                    ),
                cachedAt = now,
                expiresAt = now + 1.hours,
                preserveType = true,
            )

        assertNull(cached.toResolvedValue<String>())
    }

    @Test
    fun negativeWithTtlSetsExpiration() {
        val cached =
            CachedConfigValue.negative(
                key = "missing.key",
                scope = ConfigLevel.APP,
                source = "test",
                ttl = 30.seconds,
            )

        assertNotNull(cached.expiresAt)
        assertTrue(cached.isNegativeCache)
    }
}

class NoOpConfigCacheExtendedTest {
    @Test
    fun putDoesNotStore() =
        runTest {
            val key = ConfigCacheKey.app("test")
            val resolved = ResolvedValue.of("value", "test", ConfigLevel.APP, "test")
            val value = CachedConfigValue.of(resolved)

            NoOpConfigCache.put(key, value, 5.minutes)

            // Still returns null after put
            assertNull(NoOpConfigCache.get(key))
        }

    @Test
    fun putManyDoesNotStore() =
        runTest {
            val key1 = ConfigCacheKey.app("key1")
            val key2 = ConfigCacheKey.app("key2")
            val resolved = ResolvedValue.of("value", "test", ConfigLevel.APP, "key")

            NoOpConfigCache.putMany(
                mapOf(
                    key1 to CachedConfigValue.of(resolved),
                    key2 to CachedConfigValue.of(resolved),
                ),
            )

            // Still returns null after putMany
            assertNull(NoOpConfigCache.get(key1))
            assertNull(NoOpConfigCache.get(key2))
        }

    @Test
    fun invalidateTenantDoesNotThrow() =
        runTest {
            NoOpConfigCache.invalidateTenant("tenant-123")
            // Should complete without error
        }

    @Test
    fun invalidatePrincipalDoesNotThrow() =
        runTest {
            NoOpConfigCache.invalidatePrincipal("tenant-123", "user-456")
            // Should complete without error
        }

    @Test
    fun invalidateByPrefixDoesNotThrow() =
        runTest {
            NoOpConfigCache.invalidateByPrefix("test.prefix")
            // Should complete without error
        }

    @Test
    fun prefetchDoesNotThrow() =
        runTest {
            NoOpConfigCache.prefetch(ConfigLevel.APP, null, null, "test")
            // Should complete without error
        }

    @Test
    fun clearDoesNotThrow() =
        runTest {
            NoOpConfigCache.clear()
            // Should complete without error
        }
}

class ConfigCacheKeyStringKeyTest {
    @Test
    fun toStringKeyWithSessionId() {
        val key =
            ConfigCacheKey(
                scope = ConfigLevel.PRINCIPAL,
                tenantId = "t1",
                principalId = "p1",
                sessionId = "s1",
                key = "my.key",
            )

        val stringKey = key.toStringKey()

        assertTrue(stringKey.contains("t:t1"))
        assertTrue(stringKey.contains("p:p1"))
        assertTrue(stringKey.contains("s:s1"))
        assertTrue(stringKey.contains("my.key"))
    }

    @Test
    fun toStringKeyAppLevelHasNoIds() {
        val key = ConfigCacheKey.app("my.key")
        val stringKey = key.toStringKey()

        assertTrue(stringKey.contains("APP"))
        assertTrue(stringKey.contains("my.key"))
        assertFalse(stringKey.contains("t:"))
        assertFalse(stringKey.contains("p:"))
    }
}

class InMemoryConfigCacheExpirationTest {
    @Test
    fun expiredEntriesAreRemovedOnGet() =
        runTest {
            val cache = InMemoryConfigCache()
            val key = ConfigCacheKey.app("key")
            val now = Clock.System.now()

            // Create expired cache entry
            val resolved = ResolvedValue.of("value", "test", ConfigLevel.APP, "key")
            val expiredValue =
                CachedConfigValue(
                    value = "value",
                    metadata = resolved.metadata,
                    cachedAt = now - 2.hours,
                    expiresAt = now - 1.hours, // Already expired
                    preserveType = true,
                )

            // Manually put the expired value (bypassing TTL calculation)
            // Use internal put which accepts the value with existing expiration
            cache.put(key, expiredValue, 5.minutes)

            // Get should return null and remove the entry
            val result = cache.get(key)
            assertNull(result)
        }
}

class EvictionPolicyTest {
    @Test
    fun allPoliciesHaveExpectedValues() {
        assertEquals("LRU", EvictionPolicy.LRU.name)
        assertEquals("LFU", EvictionPolicy.LFU.name)
        assertEquals("FIFO", EvictionPolicy.FIFO.name)
    }

    @Test
    fun enumValuesContainsAllPolicies() {
        val values = EvictionPolicy.entries
        assertEquals(3, values.size)
    }
}

class SnapshotKeyToStringKeyExtendedTest {
    @Test
    fun toStringKeyWithTenantOnly() {
        val key =
            SnapshotKey(
                scope = ConfigLevel.TENANT,
                tenantId = "tenant-123",
                principalId = null,
                prefix = "db",
            )

        val stringKey = key.toStringKey()
        val segments = stringKey.split("::")

        assertTrue(stringKey.contains("TENANT"))
        assertTrue(stringKey.contains("t:tenant-123"))
        assertTrue(stringKey.contains("db"))
        assertFalse(segments.any { it.startsWith("p:") })
    }

    @Test
    fun toStringKeyWithPrincipal() {
        val key =
            SnapshotKey(
                scope = ConfigLevel.PRINCIPAL,
                tenantId = "tenant-123",
                principalId = "user-456",
                prefix = "settings",
            )

        val stringKey = key.toStringKey()

        assertTrue(stringKey.contains("PRINCIPAL"))
        assertTrue(stringKey.contains("t:tenant-123"))
        assertTrue(stringKey.contains("p:user-456"))
        assertTrue(stringKey.contains("settings"))
    }

    @Test
    fun toStringKeyAppLevelHasNoIds() {
        val key =
            SnapshotKey(
                scope = ConfigLevel.APP,
                tenantId = null,
                principalId = null,
                prefix = "app.config",
            )

        val stringKey = key.toStringKey()
        val segments = stringKey.split("::")

        assertTrue(stringKey.contains("APP"))
        assertTrue(stringKey.contains("app.config"))
        assertFalse(segments.any { it.startsWith("t:") })
        assertFalse(segments.any { it.startsWith("p:") })
    }
}

class InMemorySyncSnapshotCacheExpiredEntryTest {
    @Test
    fun expiredSnapshotIsRemovedOnGet() {
        val cache = InMemorySyncSnapshotCache()
        val key = SnapshotKey(ConfigLevel.APP, null, null, "test")
        val now = Clock.System.now()

        // Create expired snapshot
        val expiredSnapshot =
            ConfigSnapshot(
                values = emptyMap(),
                createdAt = now - 2.hours,
                expiresAt = now - 1.hours, // Already expired
            )

        cache.putSnapshot(key, expiredSnapshot)

        // Get should return null and remove the entry
        val result = cache.getSnapshot(key)
        assertNull(result)
        assertEquals(1L, cache.getStats().expired)
        assertEquals(1L, cache.getStats().misses)
    }

    @Test
    fun updateExistingEntryDoesNotTriggerEviction() {
        val cache = InMemorySyncSnapshotCache(maxEntries = 2)
        val key = SnapshotKey(ConfigLevel.APP, null, null, "test")
        val snapshot1 =
            ConfigSnapshot(
                values = emptyMap(),
                createdAt = Clock.System.now(),
                expiresAt = Clock.System.now() + 1.hours,
            )
        val snapshot2 =
            ConfigSnapshot(
                values = mapOf("key" to CachedConfigValue.of(ResolvedValue.of("val", "src", ConfigLevel.APP, "key"))),
                createdAt = Clock.System.now(),
                expiresAt = Clock.System.now() + 2.hours,
            )

        cache.putSnapshot(key, snapshot1)
        cache.putSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "other"), snapshot1)

        // Update existing key - should not trigger eviction
        cache.putSnapshot(key, snapshot2)

        // Both should still be present
        assertNotNull(cache.getSnapshot(key))
        assertNotNull(cache.getSnapshot(SnapshotKey(ConfigLevel.APP, null, null, "other")))
    }
}

class InMemorySnapshotCacheExpiredEntryTest {
    @Test
    fun expiredSnapshotIsRemovedOnGet() =
        runTest {
            val cache = InMemorySnapshotCache()
            val key = SnapshotKey(ConfigLevel.APP, null, null, "test")
            val now = Clock.System.now()

            // Create expired snapshot
            val expiredSnapshot =
                ConfigSnapshot(
                    values = emptyMap(),
                    createdAt = now - 2.hours,
                    expiresAt = now - 1.hours, // Already expired
                )

            cache.putSnapshot(key, expiredSnapshot)

            // Get should return null and remove the entry
            val result = cache.getSnapshot(key)
            assertNull(result)
            assertEquals(1L, cache.getStats().expired)
            assertEquals(1L, cache.getStats().misses)
        }

    @Test
    fun updateExistingEntryDoesNotTriggerEviction() =
        runTest {
            val cache = InMemorySnapshotCache(maxEntries = 2)
            val key = SnapshotKey(ConfigLevel.APP, null, null, "test")
            val otherKey = SnapshotKey(ConfigLevel.APP, null, null, "other")
            val snapshot =
                ConfigSnapshot(
                    values = emptyMap(),
                    createdAt = Clock.System.now(),
                    expiresAt = Clock.System.now() + 1.hours,
                )

            cache.putSnapshot(key, snapshot)
            cache.putSnapshot(otherKey, snapshot)

            // Update existing key - should not trigger eviction
            cache.putSnapshot(key, snapshot)

            // Both should still be present
            assertNotNull(cache.getSnapshot(key))
            assertNotNull(cache.getSnapshot(otherKey))
        }
}

class AsyncToSyncCacheAdapterWarmupMultiplePrefixesTest {
    @Test
    fun warmupMultiplePrefixes() =
        runTest {
            val asyncCache = InMemorySnapshotCache()
            val syncCache = InMemorySyncSnapshotCache()
            val adapter = AsyncToSyncCacheAdapter(asyncCache, syncCache)

            // Put multiple snapshots in async cache
            val key1 = SnapshotKey(ConfigLevel.APP, null, null, "db")
            val key2 = SnapshotKey(ConfigLevel.APP, null, null, "kms")
            val snapshot =
                ConfigSnapshot(
                    values = emptyMap(),
                    createdAt = Clock.System.now(),
                    expiresAt = Clock.System.now() + 1.hours,
                )

            asyncCache.putSnapshot(key1, snapshot)
            asyncCache.putSnapshot(key2, snapshot)

            // Warmup both prefixes
            adapter.warmupAsync(setOf("db", "kms"), ConfigLevel.APP, null, null)

            // Both should be accessible via sync cache
            assertTrue(adapter.isWarmedUp(key1))
            assertTrue(adapter.isWarmedUp(key2))
            assertNotNull(adapter.getSnapshot(key1))
            assertNotNull(adapter.getSnapshot(key2))
        }

    @Test
    fun warmupSkipsMissingSnapshots() =
        runTest {
            val asyncCache = InMemorySnapshotCache()
            val syncCache = InMemorySyncSnapshotCache()
            val adapter = AsyncToSyncCacheAdapter(asyncCache, syncCache)

            // Only put one snapshot
            val key1 = SnapshotKey(ConfigLevel.APP, null, null, "db")
            val snapshot =
                ConfigSnapshot(
                    values = emptyMap(),
                    createdAt = Clock.System.now(),
                    expiresAt = Clock.System.now() + 1.hours,
                )
            asyncCache.putSnapshot(key1, snapshot)

            // Warmup including non-existent prefix
            adapter.warmupAsync(setOf("db", "nonexistent"), ConfigLevel.APP, null, null)

            // db should be warmed up, nonexistent should not
            assertTrue(adapter.isWarmedUp(key1))
            assertFalse(adapter.isWarmedUp(SnapshotKey(ConfigLevel.APP, null, null, "nonexistent")))
            assertTrue(adapter.hasWarmedUp())
        }
}

class InMemorySyncSnapshotCacheInvalidateEmptyPrefixTest {
    @Test
    fun invalidateByPrefixWithNoMatchesDoesNothing() {
        val cache = InMemorySyncSnapshotCache()
        val key = SnapshotKey(ConfigLevel.APP, null, null, "db")
        val snapshot =
            ConfigSnapshot(
                values = emptyMap(),
                createdAt = Clock.System.now(),
                expiresAt = Clock.System.now() + 1.hours,
            )

        cache.putSnapshot(key, snapshot)

        // Invalidate non-matching prefix
        cache.invalidateByPrefix("nonexistent")

        // Original entry should still be present
        assertNotNull(cache.getSnapshot(key))
    }
}

class InMemoryConfigCacheEvictionWithKeyAlreadyExistsTest {
    @Test
    fun putExistingKeyDoesNotTriggerEviction() =
        runTest {
            val cache = InMemoryConfigCache(maxEntries = 2)
            val key1 = ConfigCacheKey.app("key1")
            val key2 = ConfigCacheKey.app("key2")

            val resolved = ResolvedValue.of("value", "test", ConfigLevel.APP, "key")

            cache.put(key1, CachedConfigValue.of(resolved), 5.minutes)
            cache.put(key2, CachedConfigValue.of(resolved), 5.minutes)

            // Update key1 - should not trigger eviction even though at capacity
            val updatedResolved = ResolvedValue.of("new-value", "test", ConfigLevel.APP, "key1")
            cache.put(key1, CachedConfigValue.of(updatedResolved), 5.minutes)

            // Both keys should still exist
            assertNotNull(cache.get(key1))
            assertNotNull(cache.get(key2))
            assertEquals(0L, cache.getStats().evictions)
        }
}

class InMemoryConfigCacheEvictionPolicyTest {
    @Test
    fun lruEvictsLeastRecentlyUsedEntry() =
        runTest {
            val cache = InMemoryConfigCache(maxEntries = 2, evictionPolicy = EvictionPolicy.LRU)
            val key1 = ConfigCacheKey.app("key1")
            val key2 = ConfigCacheKey.app("key2")
            val key3 = ConfigCacheKey.app("key3")
            val resolved = ResolvedValue.of("value", "test", ConfigLevel.APP, "key")

            cache.put(key1, CachedConfigValue.of(resolved), 5.minutes)
            cache.put(key2, CachedConfigValue.of(resolved), 5.minutes)
            cache.get(key1) // Make key2 least recently used
            cache.put(key3, CachedConfigValue.of(resolved), 5.minutes)

            assertNotNull(cache.get(key1))
            assertNull(cache.get(key2))
            assertNotNull(cache.get(key3))
        }

    @Test
    fun lfuEvictsLeastFrequentlyUsedEntry() =
        runTest {
            val cache = InMemoryConfigCache(maxEntries = 2, evictionPolicy = EvictionPolicy.LFU)
            val key1 = ConfigCacheKey.app("key1")
            val key2 = ConfigCacheKey.app("key2")
            val key3 = ConfigCacheKey.app("key3")
            val resolved = ResolvedValue.of("value", "test", ConfigLevel.APP, "key")

            cache.put(key1, CachedConfigValue.of(resolved), 5.minutes)
            cache.put(key2, CachedConfigValue.of(resolved), 5.minutes)
            cache.get(key1)
            cache.get(key1) // key1 usage > key2 usage
            cache.put(key3, CachedConfigValue.of(resolved), 5.minutes)

            assertNotNull(cache.get(key1))
            assertNull(cache.get(key2))
            assertNotNull(cache.get(key3))
        }

    @Test
    fun fifoEvictsOldestInsertedEntry() =
        runTest {
            val cache = InMemoryConfigCache(maxEntries = 2, evictionPolicy = EvictionPolicy.FIFO)
            val key1 = ConfigCacheKey.app("key1")
            val key2 = ConfigCacheKey.app("key2")
            val key3 = ConfigCacheKey.app("key3")
            val resolved = ResolvedValue.of("value", "test", ConfigLevel.APP, "key")

            cache.put(key1, CachedConfigValue.of(resolved), 5.minutes)
            cache.put(key2, CachedConfigValue.of(resolved), 5.minutes)
            cache.get(key1) // Access should not affect FIFO ordering
            cache.put(key3, CachedConfigValue.of(resolved), 5.minutes)

            assertNull(cache.get(key1))
            assertNotNull(cache.get(key2))
            assertNotNull(cache.get(key3))
        }
}

class InMemorySnapshotCacheEvictionPolicyTest {
    @Test
    fun fifoEvictsOldestInsertedSnapshot() =
        runTest {
            val cache = InMemorySnapshotCache(maxEntries = 2, evictionPolicy = EvictionPolicy.FIFO)
            val key1 = SnapshotKey(ConfigLevel.APP, null, null, "key1")
            val key2 = SnapshotKey(ConfigLevel.APP, null, null, "key2")
            val key3 = SnapshotKey(ConfigLevel.APP, null, null, "key3")
            val snapshot =
                ConfigSnapshot(
                    values = emptyMap(),
                    createdAt = Clock.System.now(),
                    expiresAt = Clock.System.now() + 1.hours,
                )

            cache.putSnapshot(key1, snapshot)
            cache.putSnapshot(key2, snapshot)
            cache.getSnapshot(key1) // Access should not affect FIFO ordering
            cache.putSnapshot(key3, snapshot)

            assertNull(cache.getSnapshot(key1))
            assertNotNull(cache.getSnapshot(key2))
            assertNotNull(cache.getSnapshot(key3))
        }
}

class InMemorySyncSnapshotCacheEvictionPolicyTest {
    @Test
    fun lruEvictsLeastRecentlyUsedSnapshot() {
        val cache = InMemorySyncSnapshotCache(maxEntries = 2, evictionPolicy = EvictionPolicy.LRU)
        val key1 = SnapshotKey(ConfigLevel.APP, null, null, "key1")
        val key2 = SnapshotKey(ConfigLevel.APP, null, null, "key2")
        val key3 = SnapshotKey(ConfigLevel.APP, null, null, "key3")
        val snapshot =
            ConfigSnapshot(
                values = emptyMap(),
                createdAt = Clock.System.now(),
                expiresAt = Clock.System.now() + 1.hours,
            )

        cache.putSnapshot(key1, snapshot)
        cache.putSnapshot(key2, snapshot)
        cache.getSnapshot(key1) // Make key2 least recently used
        cache.putSnapshot(key3, snapshot)

        assertNotNull(cache.getSnapshot(key1))
        assertNull(cache.getSnapshot(key2))
        assertNotNull(cache.getSnapshot(key3))
    }
}

class CacheConfigRuntimeLoaderTest {
    @Test
    fun loadFromEnvironmentParsesEvictionPolicyAndSnapshotSettings() {
        val env =
            mapOf(
                CacheConfigRuntimeLoader.ENV_CACHE_EVICTION_POLICY to "fifo",
                CacheConfigRuntimeLoader.ENV_CACHE_SNAPSHOT_ENABLED to "true",
                CacheConfigRuntimeLoader.ENV_CACHE_SNAPSHOT_MAX_ENTRIES to "2",
                CacheConfigRuntimeLoader.ENV_CACHE_SNAPSHOT_TTL to "45m",
            )

        val config = CacheConfigRuntimeLoader.loadFromEnvironment(getEnv = { key -> env[key] })

        assertEquals(EvictionPolicy.FIFO, config.evictionPolicy)
        assertTrue(config.snapshotEnabled)
        assertEquals(2, config.snapshotMaxEntries)
        assertEquals(45.minutes, config.snapshotTtl)
    }

    @Test
    fun loadFromEnvironmentIgnoresInvalidValuesAndKeepsDefaults() {
        val env =
            mapOf(
                CacheConfigRuntimeLoader.ENV_CACHE_EVICTION_POLICY to "unknown",
                CacheConfigRuntimeLoader.ENV_CACHE_SNAPSHOT_MAX_ENTRIES to "-1",
                CacheConfigRuntimeLoader.ENV_CACHE_SNAPSHOT_TTL to "not-a-duration",
            )
        val defaults = CacheConfig(evictionPolicy = EvictionPolicy.LFU, snapshotMaxEntries = 7, snapshotTtl = 5.minutes)

        val config = CacheConfigRuntimeLoader.loadFromEnvironment(getEnv = { key -> env[key] }, defaults = defaults)

        assertEquals(EvictionPolicy.LFU, config.evictionPolicy)
        assertEquals(7, config.snapshotMaxEntries)
        assertEquals(5.minutes, config.snapshotTtl)
    }
}

class DefaultSnapshotCacheCompositionTest {
    @Test
    fun defaultConfigSnapshotCacheUsesConfiguredEvictionPolicy() =
        runTest {
            val cache =
                createConfigSnapshotCache(
                    CacheConfig(
                        evictionPolicy = EvictionPolicy.FIFO,
                        snapshotMaxEntries = 2,
                        snapshotTtl = 1.hours,
                    ),
                )
            val snapshot = ConfigSnapshot(values = emptyMap(), createdAt = Clock.System.now(), expiresAt = Clock.System.now() + 1.hours)
            val key1 = SnapshotKey(ConfigLevel.APP, null, null, "key1")
            val key2 = SnapshotKey(ConfigLevel.APP, null, null, "key2")
            val key3 = SnapshotKey(ConfigLevel.APP, null, null, "key3")

            cache.putSnapshot(key1, snapshot)
            cache.putSnapshot(key2, snapshot)
            cache.getSnapshot(key1) // Should not affect FIFO order
            cache.putSnapshot(key3, snapshot)

            assertNull(cache.getSnapshot(key1))
            assertNotNull(cache.getSnapshot(key2))
            assertNotNull(cache.getSnapshot(key3))
        }

    @Test
    fun defaultSyncSnapshotCacheUsesConfiguredEvictionPolicy() {
        val cache =
            createSyncConfigSnapshotCache(
                CacheConfig(
                    evictionPolicy = EvictionPolicy.FIFO,
                    snapshotMaxEntries = 2,
                    snapshotTtl = 1.hours,
                ),
            )
        val snapshot = ConfigSnapshot(values = emptyMap(), createdAt = Clock.System.now(), expiresAt = Clock.System.now() + 1.hours)
        val key1 = SnapshotKey(ConfigLevel.APP, null, null, "key1")
        val key2 = SnapshotKey(ConfigLevel.APP, null, null, "key2")
        val key3 = SnapshotKey(ConfigLevel.APP, null, null, "key3")

        cache.putSnapshot(key1, snapshot)
        cache.putSnapshot(key2, snapshot)
        cache.getSnapshot(key1) // Should not affect FIFO order
        cache.putSnapshot(key3, snapshot)

        assertNull(cache.getSnapshot(key1))
        assertNotNull(cache.getSnapshot(key2))
        assertNotNull(cache.getSnapshot(key3))
    }
}

/**
 * Tests for data class equals, hashCode, and copy methods to ensure branch coverage.
 */
class CacheConfigDataClassTest {
    @Test
    fun equalsReturnsTrueForSameValues() {
        val config1 = CacheConfig(enabled = true, maxEntries = 1000)
        val config2 = CacheConfig(enabled = true, maxEntries = 1000)
        assertEquals(config1, config2)
        assertEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentEnabled() {
        val config1 = CacheConfig(enabled = true)
        val config2 = CacheConfig(enabled = false)
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentMaxEntries() {
        val config1 = CacheConfig(maxEntries = 1000)
        val config2 = CacheConfig(maxEntries = 2000)
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentTtl() {
        val config1 = CacheConfig(ttl = TtlConfig(app = 10.minutes))
        val config2 = CacheConfig(ttl = TtlConfig(app = 20.minutes))
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentEvictionPolicy() {
        val config1 = CacheConfig(evictionPolicy = EvictionPolicy.LRU)
        val config2 = CacheConfig(evictionPolicy = EvictionPolicy.FIFO)
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentSnapshotEnabled() {
        val config1 = CacheConfig(snapshotEnabled = true)
        val config2 = CacheConfig(snapshotEnabled = false)
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentSnapshotMaxEntries() {
        val config1 = CacheConfig(snapshotMaxEntries = 1000)
        val config2 = CacheConfig(snapshotMaxEntries = 500)
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentSnapshotTtl() {
        val config1 = CacheConfig(snapshotTtl = 30.minutes)
        val config2 = CacheConfig(snapshotTtl = 60.minutes)
        assertFalse(config1 == config2)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val original = CacheConfig(enabled = true, maxEntries = 1000)
        val copy = original.copy(enabled = false)
        assertFalse(copy.enabled)
        assertEquals(1000, copy.maxEntries)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val config = CacheConfig()
        assertFalse(config.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val config = CacheConfig()
        assertFalse(config.equals("not a config"))
    }
}

class TtlConfigDataClassTest {
    @Test
    fun equalsReturnsTrueForSameValues() {
        val config1 = TtlConfig(app = 10.minutes, tenant = 5.minutes, principal = 2.minutes)
        val config2 = TtlConfig(app = 10.minutes, tenant = 5.minutes, principal = 2.minutes)
        assertEquals(config1, config2)
        assertEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentApp() {
        val config1 = TtlConfig(app = 10.minutes)
        val config2 = TtlConfig(app = 20.minutes)
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentTenant() {
        val config1 = TtlConfig(tenant = 5.minutes)
        val config2 = TtlConfig(tenant = 10.minutes)
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentPrincipal() {
        val config1 = TtlConfig(principal = 2.minutes)
        val config2 = TtlConfig(principal = 4.minutes)
        assertFalse(config1 == config2)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val original = TtlConfig(app = 10.minutes)
        val copy = original.copy(app = 20.minutes)
        assertEquals(20.minutes, copy.app)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val config = TtlConfig()
        assertFalse(config.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val config = TtlConfig()
        assertFalse(config.equals("not a config"))
    }
}

class CacheStatsDataClassTest {
    @Test
    fun equalsReturnsTrueForSameValues() {
        val stats1 = CacheStats(hits = 10, misses = 5, evictions = 2, size = 100, maxSize = 1000)
        val stats2 = CacheStats(hits = 10, misses = 5, evictions = 2, size = 100, maxSize = 1000)
        assertEquals(stats1, stats2)
        assertEquals(stats1.hashCode(), stats2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentHits() {
        val stats1 = CacheStats(hits = 10, misses = 5, evictions = 2, size = 100)
        val stats2 = CacheStats(hits = 20, misses = 5, evictions = 2, size = 100)
        assertFalse(stats1 == stats2)
    }

    @Test
    fun equalsReturnsFalseForDifferentMisses() {
        val stats1 = CacheStats(hits = 10, misses = 5, evictions = 2, size = 100)
        val stats2 = CacheStats(hits = 10, misses = 10, evictions = 2, size = 100)
        assertFalse(stats1 == stats2)
    }

    @Test
    fun equalsReturnsFalseForDifferentEvictions() {
        val stats1 = CacheStats(hits = 10, misses = 5, evictions = 2, size = 100)
        val stats2 = CacheStats(hits = 10, misses = 5, evictions = 5, size = 100)
        assertFalse(stats1 == stats2)
    }

    @Test
    fun equalsReturnsFalseForDifferentSize() {
        val stats1 = CacheStats(hits = 10, misses = 5, evictions = 2, size = 100)
        val stats2 = CacheStats(hits = 10, misses = 5, evictions = 2, size = 200)
        assertFalse(stats1 == stats2)
    }

    @Test
    fun equalsReturnsFalseForDifferentMaxSize() {
        val stats1 = CacheStats(hits = 10, misses = 5, evictions = 2, size = 100, maxSize = 1000)
        val stats2 = CacheStats(hits = 10, misses = 5, evictions = 2, size = 100, maxSize = 2000)
        assertFalse(stats1 == stats2)
    }

    @Test
    fun equalsReturnsFalseForDifferentExpired() {
        val stats1 = CacheStats(hits = 10, misses = 5, evictions = 2, size = 100, expired = 1)
        val stats2 = CacheStats(hits = 10, misses = 5, evictions = 2, size = 100, expired = 2)
        assertFalse(stats1 == stats2)
    }

    @Test
    fun equalsReturnsFalseForDifferentInvalidations() {
        val stats1 = CacheStats(hits = 10, misses = 5, evictions = 2, size = 100, invalidations = 1)
        val stats2 = CacheStats(hits = 10, misses = 5, evictions = 2, size = 100, invalidations = 2)
        assertFalse(stats1 == stats2)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val original = CacheStats(hits = 10, misses = 5, evictions = 2, size = 100)
        val copy = original.copy(hits = 20)
        assertEquals(20, copy.hits)
        assertEquals(5, copy.misses)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val stats = CacheStats(hits = 10, misses = 5, evictions = 2, size = 100)
        assertFalse(stats.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val stats = CacheStats(hits = 10, misses = 5, evictions = 2, size = 100)
        assertFalse(stats.equals("not stats"))
    }
}

class ConfigCacheKeyDataClassTest {
    @Test
    fun equalsReturnsTrueForSameValues() {
        val key1 = ConfigCacheKey(ConfigLevel.APP, null, null, null, "key")
        val key2 = ConfigCacheKey(ConfigLevel.APP, null, null, null, "key")
        assertEquals(key1, key2)
        assertEquals(key1.hashCode(), key2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentScope() {
        val key1 = ConfigCacheKey(ConfigLevel.APP, null, null, null, "key")
        val key2 = ConfigCacheKey(ConfigLevel.TENANT, "t1", null, null, "key")
        assertFalse(key1 == key2)
    }

    @Test
    fun equalsReturnsFalseForDifferentTenantId() {
        val key1 = ConfigCacheKey(ConfigLevel.TENANT, "t1", null, null, "key")
        val key2 = ConfigCacheKey(ConfigLevel.TENANT, "t2", null, null, "key")
        assertFalse(key1 == key2)
    }

    @Test
    fun equalsReturnsFalseForDifferentPrincipalId() {
        val key1 = ConfigCacheKey(ConfigLevel.PRINCIPAL, "t1", "p1", null, "key")
        val key2 = ConfigCacheKey(ConfigLevel.PRINCIPAL, "t1", "p2", null, "key")
        assertFalse(key1 == key2)
    }

    @Test
    fun equalsReturnsFalseForDifferentSessionId() {
        val key1 = ConfigCacheKey(ConfigLevel.PRINCIPAL, "t1", "p1", "s1", "key")
        val key2 = ConfigCacheKey(ConfigLevel.PRINCIPAL, "t1", "p1", "s2", "key")
        assertFalse(key1 == key2)
    }

    @Test
    fun equalsReturnsFalseForDifferentKey() {
        val key1 = ConfigCacheKey(ConfigLevel.APP, null, null, null, "key1")
        val key2 = ConfigCacheKey(ConfigLevel.APP, null, null, null, "key2")
        assertFalse(key1 == key2)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val original = ConfigCacheKey(ConfigLevel.APP, null, null, null, "key")
        val copy = original.copy(scope = ConfigLevel.TENANT, tenantId = "t1")
        assertEquals(ConfigLevel.TENANT, copy.scope)
        assertEquals("t1", copy.tenantId)
        assertEquals("key", copy.key)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val key = ConfigCacheKey.app("key")
        assertFalse(key.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val key = ConfigCacheKey.app("key")
        assertFalse(key.equals("not a key"))
    }
}

class SnapshotKeyDataClassTest {
    @Test
    fun equalsReturnsTrueForSameValues() {
        val key1 = SnapshotKey(ConfigLevel.APP, null, null, "prefix")
        val key2 = SnapshotKey(ConfigLevel.APP, null, null, "prefix")
        assertEquals(key1, key2)
        assertEquals(key1.hashCode(), key2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentScope() {
        val key1 = SnapshotKey(ConfigLevel.APP, null, null, "prefix")
        val key2 = SnapshotKey(ConfigLevel.TENANT, "t1", null, "prefix")
        assertFalse(key1 == key2)
    }

    @Test
    fun equalsReturnsFalseForDifferentTenantId() {
        val key1 = SnapshotKey(ConfigLevel.TENANT, "t1", null, "prefix")
        val key2 = SnapshotKey(ConfigLevel.TENANT, "t2", null, "prefix")
        assertFalse(key1 == key2)
    }

    @Test
    fun equalsReturnsFalseForDifferentPrincipalId() {
        val key1 = SnapshotKey(ConfigLevel.PRINCIPAL, "t1", "p1", "prefix")
        val key2 = SnapshotKey(ConfigLevel.PRINCIPAL, "t1", "p2", "prefix")
        assertFalse(key1 == key2)
    }

    @Test
    fun equalsReturnsFalseForDifferentPrefix() {
        val key1 = SnapshotKey(ConfigLevel.APP, null, null, "prefix1")
        val key2 = SnapshotKey(ConfigLevel.APP, null, null, "prefix2")
        assertFalse(key1 == key2)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val original = SnapshotKey(ConfigLevel.APP, null, null, "prefix")
        val copy = original.copy(prefix = "new-prefix")
        assertEquals("new-prefix", copy.prefix)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val key = SnapshotKey(ConfigLevel.APP, null, null, "prefix")
        assertFalse(key.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val key = SnapshotKey(ConfigLevel.APP, null, null, "prefix")
        assertFalse(key.equals("not a key"))
    }
}

class ConfigSnapshotDataClassTest {
    @Test
    fun equalsReturnsTrueForSameValues() {
        val now = Clock.System.now()
        val snapshot1 = ConfigSnapshot(values = emptyMap(), createdAt = now, expiresAt = null)
        val snapshot2 = ConfigSnapshot(values = emptyMap(), createdAt = now, expiresAt = null)
        assertEquals(snapshot1, snapshot2)
        assertEquals(snapshot1.hashCode(), snapshot2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentCreatedAt() {
        val now = Clock.System.now()
        val snapshot1 = ConfigSnapshot(values = emptyMap(), createdAt = now, expiresAt = null)
        val snapshot2 = ConfigSnapshot(values = emptyMap(), createdAt = now + 1.hours, expiresAt = null)
        assertFalse(snapshot1 == snapshot2)
    }

    @Test
    fun equalsReturnsFalseForDifferentExpiresAt() {
        val now = Clock.System.now()
        val snapshot1 = ConfigSnapshot(values = emptyMap(), createdAt = now, expiresAt = now + 1.hours)
        val snapshot2 = ConfigSnapshot(values = emptyMap(), createdAt = now, expiresAt = now + 2.hours)
        assertFalse(snapshot1 == snapshot2)
    }

    @Test
    fun equalsReturnsFalseForDifferentValues() {
        val now = Clock.System.now()
        val resolved = ResolvedValue.of("val", "src", ConfigLevel.APP, "key")
        val snapshot1 = ConfigSnapshot(values = emptyMap(), createdAt = now, expiresAt = null)
        val snapshot2 = ConfigSnapshot(values = mapOf("key" to CachedConfigValue.of(resolved)), createdAt = now, expiresAt = null)
        assertFalse(snapshot1 == snapshot2)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val now = Clock.System.now()
        val original = ConfigSnapshot(values = emptyMap(), createdAt = now, expiresAt = null)
        val copy = original.copy(expiresAt = now + 1.hours)
        assertNotNull(copy.expiresAt)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val snapshot = ConfigSnapshot(values = emptyMap(), createdAt = Clock.System.now(), expiresAt = null)
        assertFalse(snapshot.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val snapshot = ConfigSnapshot(values = emptyMap(), createdAt = Clock.System.now(), expiresAt = null)
        assertFalse(snapshot.equals("not a snapshot"))
    }
}

class CachedConfigValueDataClassTest {
    @Test
    fun equalsReturnsTrueForSameValues() {
        val now = Clock.System.now()
        val metadata =
            ResolutionMetadata(
                source = "test",
                scope = ConfigLevel.APP,
                originalKey = "key",
                normalizedKey = "key",
                order = 0,
                isSecret = false,
                isInterpolated = false,
                resolvedAt = now,
                ttl = 1.hours,
            )
        val value1 = CachedConfigValue(stringValue = "value", metadata = metadata, cachedAt = now, expiresAt = now + 1.hours, isNegativeCache = false)
        val value2 = CachedConfigValue(stringValue = "value", metadata = metadata, cachedAt = now, expiresAt = now + 1.hours, isNegativeCache = false)
        assertEquals(value1, value2)
        assertEquals(value1.hashCode(), value2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentStringValue() {
        val now = Clock.System.now()
        val metadata =
            ResolutionMetadata(
                source = "test",
                scope = ConfigLevel.APP,
                originalKey = "key",
                normalizedKey = "key",
                order = 0,
                isSecret = false,
                isInterpolated = false,
                resolvedAt = now,
                ttl = null,
            )
        val value1 = CachedConfigValue(stringValue = "value1", metadata = metadata, cachedAt = now, expiresAt = null)
        val value2 = CachedConfigValue(stringValue = "value2", metadata = metadata, cachedAt = now, expiresAt = null)
        assertFalse(value1 == value2)
    }

    @Test
    fun equalsReturnsFalseForDifferentCachedAt() {
        val now = Clock.System.now()
        val metadata =
            ResolutionMetadata(
                source = "test",
                scope = ConfigLevel.APP,
                originalKey = "key",
                normalizedKey = "key",
                order = 0,
                isSecret = false,
                isInterpolated = false,
                resolvedAt = now,
                ttl = null,
            )
        val value1 = CachedConfigValue(stringValue = "value", metadata = metadata, cachedAt = now, expiresAt = null)
        val value2 = CachedConfigValue(stringValue = "value", metadata = metadata, cachedAt = now + 1.hours, expiresAt = null)
        assertFalse(value1 == value2)
    }

    @Test
    fun equalsReturnsFalseForDifferentExpiresAt() {
        val now = Clock.System.now()
        val metadata =
            ResolutionMetadata(
                source = "test",
                scope = ConfigLevel.APP,
                originalKey = "key",
                normalizedKey = "key",
                order = 0,
                isSecret = false,
                isInterpolated = false,
                resolvedAt = now,
                ttl = null,
            )
        val value1 = CachedConfigValue(stringValue = "value", metadata = metadata, cachedAt = now, expiresAt = now + 1.hours)
        val value2 = CachedConfigValue(stringValue = "value", metadata = metadata, cachedAt = now, expiresAt = now + 2.hours)
        assertFalse(value1 == value2)
    }

    @Test
    fun equalsReturnsFalseForDifferentIsNegativeCache() {
        val now = Clock.System.now()
        val metadata =
            ResolutionMetadata(
                source = "test",
                scope = ConfigLevel.APP,
                originalKey = "key",
                normalizedKey = "key",
                order = 0,
                isSecret = false,
                isInterpolated = false,
                resolvedAt = now,
                ttl = null,
            )
        val value1 = CachedConfigValue(stringValue = "value", metadata = metadata, cachedAt = now, expiresAt = null, isNegativeCache = false)
        val value2 = CachedConfigValue(stringValue = "value", metadata = metadata, cachedAt = now, expiresAt = null, isNegativeCache = true)
        assertFalse(value1 == value2)
    }

    @Test
    fun equalsHandlesNullStringValue() {
        val now = Clock.System.now()
        val metadata =
            ResolutionMetadata(
                source = "test",
                scope = ConfigLevel.APP,
                originalKey = "key",
                normalizedKey = "key",
                order = 0,
                isSecret = false,
                isInterpolated = false,
                resolvedAt = now,
                ttl = null,
            )
        val value1 = CachedConfigValue(stringValue = null, metadata = metadata, cachedAt = now, expiresAt = null)
        val value2 = CachedConfigValue(stringValue = null, metadata = metadata, cachedAt = now, expiresAt = null)
        assertEquals(value1, value2)
    }

    @Test
    fun equalsReturnsFalseForNullVsNonNullStringValue() {
        val now = Clock.System.now()
        val metadata =
            ResolutionMetadata(
                source = "test",
                scope = ConfigLevel.APP,
                originalKey = "key",
                normalizedKey = "key",
                order = 0,
                isSecret = false,
                isInterpolated = false,
                resolvedAt = now,
                ttl = null,
            )
        val value1 = CachedConfigValue(stringValue = null, metadata = metadata, cachedAt = now, expiresAt = null)
        val value2 = CachedConfigValue(stringValue = "value", metadata = metadata, cachedAt = now, expiresAt = null)
        assertFalse(value1 == value2)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val now = Clock.System.now()
        val metadata =
            ResolutionMetadata(
                source = "test",
                scope = ConfigLevel.APP,
                originalKey = "key",
                normalizedKey = "key",
                order = 0,
                isSecret = false,
                isInterpolated = false,
                resolvedAt = now,
                ttl = null,
            )
        val original = CachedConfigValue(stringValue = "value", metadata = metadata, cachedAt = now, expiresAt = null)
        val copy = original.copy(isNegativeCache = true)
        assertTrue(copy.isNegativeCache)
        assertEquals("value", copy.stringValue)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val resolved = ResolvedValue.of("value", "test", ConfigLevel.APP, "key")
        val value = CachedConfigValue.of(resolved)
        assertFalse(value.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val resolved = ResolvedValue.of("value", "test", ConfigLevel.APP, "key")
        val value = CachedConfigValue.of(resolved)
        assertFalse(value.equals("not a cached value"))
    }
}

class ProvenanceSafeSnapshotCacheTest {
    private fun cachedValue(
        key: String,
        value: String,
        provenance: ResolutionProvenance,
    ): CachedConfigValue {
        val now = Clock.System.now()
        return CachedConfigValue(
            value = value,
            metadata =
                ResolutionMetadata(
                    source = "test",
                    scope = provenance.sourceScope ?: ConfigLevel.APP,
                    originalKey = key,
                    normalizedKey = key,
                    order = 0,
                    isSecret = false,
                    isInterpolated = provenance.hasTaint(ResolutionTaint.INTERPOLATED),
                    resolvedAt = now,
                    ttl = 1.hours,
                    provenance = provenance,
                ),
            cachedAt = now,
            expiresAt = now + 1.hours,
            preserveType = true,
        )
    }

    @Test
    fun mixedSafeAndUnsafePrefixSnapshotIsRejectedAtomically() {
        val cache = InMemorySyncSnapshotCache()
        val key = SnapshotKey(ConfigLevel.APP, null, null, "mixed", sourceRevision = 4L, contentRevision = 9L)
        val now = Clock.System.now()
        val snapshot =
            ConfigSnapshot(
                values =
                    mapOf(
                        "mixed.safe" to cachedValue("mixed.safe", "safe", ResolutionProvenance.known(ConfigLevel.APP)),
                        "mixed.environment" to
                            cachedValue(
                                "mixed.environment",
                                "materialized",
                                ResolutionProvenance
                                    .known(ConfigLevel.APP)
                                    .withTaint(ResolutionTaint.ENVIRONMENT),
                            ),
                        "mixed.sensitive" to
                            cachedValue(
                                "mixed.sensitive",
                                "materialized",
                                ResolutionProvenance
                                    .known(ConfigLevel.APP)
                                    .withTaint(ResolutionTaint.SENSITIVE),
                            ),
                    ),
                createdAt = now,
                expiresAt = now + 1.hours,
            )

        cache.putSnapshot(key, snapshot)

        assertNull(cache.getSnapshot(key))
        assertEquals(0L, cache.getStats().size)
    }

    @Test
    fun warmupUsesCompleteRevisionIdentityAndDoesNotReopenAsyncCache() =
        runTest {
            val asyncCache = InMemorySnapshotCache()
            val syncCache = InMemorySyncSnapshotCache()
            val adapter = AsyncToSyncCacheAdapter(asyncCache, syncCache)
            val currentKey =
                SnapshotKey(
                    ConfigLevel.APP,
                    null,
                    null,
                    "feature",
                    sourceRevision = 7L,
                    contentRevision = 11L,
                )
            val staleKey = currentKey.copy(contentRevision = 10L)
            val now = Clock.System.now()
            asyncCache.putSnapshot(
                currentKey,
                ConfigSnapshot(
                    values =
                        mapOf(
                            "feature.name" to
                                cachedValue(
                                    "feature.name",
                                    "safe",
                                    ResolutionProvenance.known(ConfigLevel.APP),
                                ),
                        ),
                    createdAt = now,
                    expiresAt = now + 1.hours,
                ),
            )

            adapter.warmupAsync(
                prefixes = setOf("feature"),
                level = ConfigLevel.APP,
                tenantId = null,
                principalId = null,
                sourceRevision = 7L,
                contentRevision = 11L,
            )
            val asyncStatsAfterWarmup = asyncCache.getStats()

            assertTrue(adapter.isWarmedUp(currentKey))
            assertFalse(adapter.isWarmedUp(staleKey))
            assertNotNull(adapter.getSnapshot(currentKey))
            assertNotNull(adapter.getSnapshot(currentKey))
            assertNull(adapter.getSnapshot(staleKey))
            assertEquals(asyncStatsAfterWarmup.hits, asyncCache.getStats().hits)
            assertEquals(asyncStatsAfterWarmup.misses, asyncCache.getStats().misses)
        }
}
