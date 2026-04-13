/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.core.api.cache

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CacheInfrastructureTest {

    // ========== ScopedKey Tests ==========

    @Test
    fun scopedKeyAppCreatesCorrectKey() {
        val key = ScopedKey.app("config", "db.pool.size")

        assertEquals("config", key.namespace)
        assertEquals(CacheScope.APP, key.scope)
        assertNull(key.tenantId)
        assertNull(key.principalId)
        assertEquals("db.pool.size", key.key)
    }

    @Test
    fun scopedKeyTenantCreatesCorrectKey() {
        val key = ScopedKey.tenant("config", "tenant-a", "feature.enabled")

        assertEquals("config", key.namespace)
        assertEquals(CacheScope.TENANT, key.scope)
        assertEquals("tenant-a", key.tenantId)
        assertNull(key.principalId)
        assertEquals("feature.enabled", key.key)
    }

    @Test
    fun scopedKeyPrincipalCreatesCorrectKey() {
        val key = ScopedKey.principal("tokens", "tenant-a", "user-123", "access-token")

        assertEquals("tokens", key.namespace)
        assertEquals(CacheScope.PRINCIPAL, key.scope)
        assertEquals("tenant-a", key.tenantId)
        assertEquals("user-123", key.principalId)
        assertEquals("access-token", key.key)
    }

    @Test
    fun scopedKeyToStringKeyFormatsCorrectly() {
        // Format: namespace::scope::tenantId::principalId::key (5 parts, 4 separators)
        // Empty parts are represented as empty strings between separators

        val appKey = ScopedKey.app("config", "key1")
        // APP scope has no tenantId or principalId, so: config::APP::::key1
        assertEquals("config::APP::::::key1", appKey.toStringKey())

        val tenantKey = ScopedKey.tenant("config", "tenant-a", "key2")
        // TENANT scope has tenantId but no principalId: config::TENANT::tenant-a::::key2
        assertEquals("config::TENANT::tenant-a::::key2", tenantKey.toStringKey())

        val principalKey = ScopedKey.principal("tokens", "tenant-a", "user-1", "key3")
        // PRINCIPAL scope has all parts
        assertEquals("tokens::PRINCIPAL::tenant-a::user-1::key3", principalKey.toStringKey())
    }

    @Test
    fun scopedKeyParseStringRoundTrips() {
        val original = ScopedKey.tenant("namespace", "tenant", "mykey")
        val stringKey = original.toStringKey()
        val parsed = ScopedKey.parseString(stringKey)

        assertNotNull(parsed)
        assertEquals(original.namespace, parsed.namespace)
        assertEquals(original.scope, parsed.scope)
        assertEquals(original.tenantId, parsed.tenantId)
        assertEquals(original.principalId, parsed.principalId)
        assertEquals(original.key, parsed.key)
    }

    // ========== CacheRequirements Tests ==========

    @Test
    fun cacheRequirementsLocalOnlyCreatesCorrectRequirements() {
        val req = CacheRequirements.localOnly("config")

        assertEquals("config", req.namespace)
        assertEquals(CacheLocality.LOCAL_ONLY, req.locality)
        assertFalse(req.distributedFallback)
        assertFalse(req.writeThrough)
    }

    @Test
    fun cacheRequirementsDistributedOnlyCreatesCorrectRequirements() {
        val req = CacheRequirements.distributedOnly("tokens")

        assertEquals("tokens", req.namespace)
        assertEquals(CacheLocality.DISTRIBUTED_ONLY, req.locality)
    }

    @Test
    fun cacheRequirementsHybridWriteThroughCreatesCorrectRequirements() {
        val req = CacheRequirements.hybridWriteThrough("sessions")

        assertEquals("sessions", req.namespace)
        assertEquals(CacheLocality.LOCAL_PREFERRED, req.locality)
        assertTrue(req.distributedFallback)
        assertTrue(req.writeThrough)
    }

    @Test
    fun cacheTtlConfigReturnsCorrectTtlForScope() {
        val config = CacheTtlConfig(
            app = 10.minutes,
            tenant = 5.minutes,
            principal = 2.minutes
        )

        assertEquals(10.minutes, config.forScope(CacheScope.APP))
        assertEquals(5.minutes, config.forScope(CacheScope.TENANT))
        assertEquals(2.minutes, config.forScope(CacheScope.PRINCIPAL))
    }

    // ========== CacheSerializer Tests ==========

    @Test
    fun stringSerializerRoundTrips() {
        val serializer = CacheSerializers.string
        val value = "test value"

        val bytes = serializer.serialize(value)
        val result = serializer.deserialize(bytes)

        assertEquals(value, result)
    }

    @Test
    fun intSerializerRoundTrips() {
        val serializer = CacheSerializers.int
        val value = 42

        val bytes = serializer.serialize(value)
        val result = serializer.deserialize(bytes)

        assertEquals(value, result)
    }

    @Test
    fun booleanSerializerRoundTrips() {
        val serializer = CacheSerializers.boolean

        val trueBytes = serializer.serialize(true)
        val falseBytes = serializer.serialize(false)

        assertTrue(serializer.deserialize(trueBytes))
        assertFalse(serializer.deserialize(falseBytes))
    }

    // ========== BackendCapabilities Tests ==========

    @Test
    fun inMemoryCapabilitiesAreCorrect() {
        val caps = BackendCapabilities.IN_MEMORY

        assertTrue(caps.isLocal)
        assertFalse(caps.isDistributed)
        assertTrue(caps.supportsTtl)
        assertTrue(caps.supportsPatternDelete)
        assertTrue(caps.supportsBatchOps)
        assertFalse(caps.isPersistent)
    }

    @Test
    fun distributedCapabilitiesAreCorrect() {
        val caps = BackendCapabilities.DISTRIBUTED

        assertFalse(caps.isLocal)
        assertTrue(caps.isDistributed)
        assertTrue(caps.supportsTtl)
        assertTrue(caps.supportsPatternDelete)
        assertTrue(caps.supportsBatchOps)
        assertFalse(caps.isPersistent)
    }

    // ========== NoOpCacheBackend Tests ==========

    @Test
    fun noOpBackendReturnsNullOnGet() = runTest {
        val backend = NoOpCacheBackend

        assertNull(backend.get("any-key"))
    }

    @Test
    fun noOpBackendReturnsFalseOnDelete() = runTest {
        val backend = NoOpCacheBackend

        assertFalse(backend.delete("any-key"))
    }

    @Test
    fun noOpBackendIsAlwaysHealthy() = runTest {
        val backend = NoOpCacheBackend

        assertTrue(backend.isHealthy())
    }

    // ========== CacheStatistics Tests ==========

    @Test
    fun cacheStatisticsHitRateCalculatesCorrectly() {
        val stats = CacheStatistics(hits = 80, misses = 20)

        assertEquals(0.8, stats.hitRate, 0.001)
        assertEquals(0.2, stats.missRate, 0.001)
        assertEquals(100L, stats.totalRequests)
    }

    @Test
    fun cacheStatisticsHitRateHandlesZeroRequests() {
        val stats = CacheStatistics(hits = 0, misses = 0)

        assertEquals(0.0, stats.hitRate)
        assertEquals(1.0, stats.missRate)
        assertEquals(0L, stats.totalRequests)
    }

    @Test
    fun cacheStatisticsUtilizationCalculatesCorrectly() {
        val stats = CacheStatistics(size = 500, maxSize = 1000)

        assertEquals(0.5, stats.utilization, 0.001)
    }

    // ========== CacheGetArgs Tests ==========

    @Test
    fun cacheGetArgsAppCreatesCorrectly() {
        val args = CacheGetArgs.app("namespace", "key")

        assertEquals("namespace", args.namespace)
        assertEquals(CacheScope.APP, args.scope)
        assertNull(args.tenantId)
        assertNull(args.principalId)
        assertEquals("key", args.key)
    }

    @Test
    fun cacheGetArgsTenantCreatesCorrectly() {
        val args = CacheGetArgs.tenant("namespace", "tenant-1", "key")

        assertEquals("namespace", args.namespace)
        assertEquals(CacheScope.TENANT, args.scope)
        assertEquals("tenant-1", args.tenantId)
        assertNull(args.principalId)
        assertEquals("key", args.key)
    }

    @Test
    fun cacheGetArgsPrincipalCreatesCorrectly() {
        val args = CacheGetArgs.principal("namespace", "tenant-1", "user-1", "key")

        assertEquals("namespace", args.namespace)
        assertEquals(CacheScope.PRINCIPAL, args.scope)
        assertEquals("tenant-1", args.tenantId)
        assertEquals("user-1", args.principalId)
        assertEquals("key", args.key)
    }

    // ========== CachePutArgs Tests ==========

    @Test
    fun cachePutArgsWithTtlSetsCorrectly() {
        val args = CachePutArgs.app("namespace", "key", "value", 5.minutes)

        assertEquals("namespace", args.namespace)
        assertEquals("key", args.key)
        assertEquals("value", args.value)
        assertEquals(5.minutes.inWholeMilliseconds, args.ttlMs)
    }

    // ========== CacheInvalidateArgs Tests ==========

    @Test
    fun cacheInvalidateArgsNamespaceCreatesCorrectly() {
        val args = CacheInvalidateArgs.namespace("config")

        assertEquals("config", args.namespace)
        assertNull(args.tenantId)
        assertNull(args.principalId)
        assertNull(args.keyPattern)
    }

    @Test
    fun cacheInvalidateArgsTenantCreatesCorrectly() {
        val args = CacheInvalidateArgs.tenant("tenant-a")

        assertNull(args.namespace)
        assertEquals("tenant-a", args.tenantId)
        assertNull(args.principalId)
    }

    @Test
    fun cacheInvalidateArgsPatternCreatesCorrectly() {
        val args = CacheInvalidateArgs.pattern("config", "db.*")

        assertEquals("config", args.namespace)
        assertEquals("db.*", args.keyPattern)
    }
}
