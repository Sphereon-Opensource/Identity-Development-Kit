/*
 * © 2025 Sphereon International B.V.
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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RefreshResultTest {

    @Test
    fun hasChangesReturnsTrueWhenKeysChanged() {
        val result = RefreshResult(
            refreshedAt = Clock.System.now(),
            changedKeys = setOf("key1", "key2"),
            totalKeys = 10
        )

        assertTrue(result.hasChanges)
    }

    @Test
    fun hasChangesReturnsFalseWhenNoKeysChanged() {
        val result = RefreshResult(
            refreshedAt = Clock.System.now(),
            changedKeys = emptySet(),
            totalKeys = 10
        )

        assertFalse(result.hasChanges)
    }

    @Test
    fun fromCacheDefaultsToFalse() {
        val result = RefreshResult(
            refreshedAt = Clock.System.now(),
            changedKeys = emptySet(),
            totalKeys = 5
        )

        assertFalse(result.fromCache)
    }
}

class ConfigChangeEventTest {

    @Test
    fun eventHasCorrectProperties() {
        val now = Clock.System.now()
        val event = ConfigChangeEvent(
            providerId = "test-provider",
            changedKeys = setOf("key1"),
            timestamp = now,
            changeType = ConfigChangeType.UPDATE
        )

        assertEquals("test-provider", event.providerId)
        assertEquals(setOf("key1"), event.changedKeys)
        assertEquals(now, event.timestamp)
        assertEquals(ConfigChangeType.UPDATE, event.changeType)
    }
}

class ConfigChangeTypeTest {

    @Test
    fun allChangeTypesExist() {
        val types = ConfigChangeType.entries

        assertTrue(types.contains(ConfigChangeType.INITIAL))
        assertTrue(types.contains(ConfigChangeType.UPDATE))
        assertTrue(types.contains(ConfigChangeType.DELETE))
        assertTrue(types.contains(ConfigChangeType.REFRESH))
        assertTrue(types.contains(ConfigChangeType.SENTINEL_TRIGGERED))
    }
}

class CloudProviderHealthTest {

    @Test
    fun healthyProviderHasCorrectStatus() {
        val health = CloudProviderHealth(
            providerId = "test",
            isHealthy = true,
            message = "All good",
            checkedAt = Clock.System.now(),
            latencyMs = 50,
            configKeyCount = 100
        )

        assertTrue(health.isHealthy)
        assertEquals("All good", health.message)
        assertEquals(50, health.latencyMs)
        assertEquals(100, health.configKeyCount)
    }

    @Test
    fun unhealthyProviderHasCorrectStatus() {
        val health = CloudProviderHealth(
            providerId = "test",
            isHealthy = false,
            message = "Connection failed",
            checkedAt = Clock.System.now()
        )

        assertFalse(health.isHealthy)
        assertEquals("Connection failed", health.message)
    }
}

class CloudConfigProviderConfigTest {

    @Test
    fun defaultValuesAreCorrect() {
        val config = CloudConfigProviderConfig()

        assertTrue(config.enabled)
        assertEquals(30.seconds, config.refreshInterval)
        assertTrue(config.cacheEnabled)
        assertEquals(5.seconds, config.cacheTtl)
        assertEquals(FailureMode.CACHE_FALLBACK, config.failureMode)
    }

    @Test
    fun customValuesArePreserved() {
        val config = CloudConfigProviderConfig(
            enabled = false,
            refreshInterval = 1.minutes,
            cacheEnabled = false,
            failureMode = FailureMode.FAIL_FAST
        )

        assertFalse(config.enabled)
        assertEquals(1.minutes, config.refreshInterval)
        assertFalse(config.cacheEnabled)
        assertEquals(FailureMode.FAIL_FAST, config.failureMode)
    }
}

class RetryConfigTest {

    @Test
    fun defaultValuesAreCorrect() {
        val config = RetryConfig()

        assertEquals(3, config.maxRetries)
        assertEquals(1.seconds, config.initialDelay)
        assertEquals(30.seconds, config.maxDelay)
        assertEquals(2.0, config.multiplier)
    }
}

class FailureModeTest {

    @Test
    fun allModesExist() {
        val modes = FailureMode.entries

        assertTrue(modes.contains(FailureMode.CACHE_FALLBACK))
        assertTrue(modes.contains(FailureMode.FAIL_FAST))
        assertTrue(modes.contains(FailureMode.EMPTY_ON_FAILURE))
    }
}

class CloudConfigProviderRegistryTest {

    @Test
    fun registerAddsProvider() {
        val registry = CloudConfigProviderRegistry()
        val provider = TestCloudConfigProvider("test-provider")

        registry.register(provider)

        assertTrue(registry.hasProvider("test-provider"))
        assertEquals(provider, registry.get("test-provider"))
    }

    @Test
    fun getReturnsNullForUnknownProvider() {
        val registry = CloudConfigProviderRegistry()

        assertEquals(null, registry.get("unknown"))
    }

    @Test
    fun getAllReturnsAllProviders() {
        val registry = CloudConfigProviderRegistry()
        val provider1 = TestCloudConfigProvider("provider1")
        val provider2 = TestCloudConfigProvider("provider2")

        registry.register(provider1)
        registry.register(provider2)

        val all = registry.getAll()
        assertEquals(2, all.size)
    }

    @Test
    fun getForLevelFiltersCorrectly() {
        val registry = CloudConfigProviderRegistry()
        val appProvider = TestCloudConfigProvider("app", ConfigLevel.APP)
        val tenantProvider = TestCloudConfigProvider("tenant", ConfigLevel.TENANT)

        registry.register(appProvider)
        registry.register(tenantProvider)

        val appProviders = registry.getForLevel(ConfigLevel.APP)
        val tenantProviders = registry.getForLevel(ConfigLevel.TENANT)

        assertEquals(1, appProviders.size)
        assertEquals("app", appProviders[0].providerId)
        assertEquals(1, tenantProviders.size)
        assertEquals("tenant", tenantProviders[0].providerId)
    }

    @Test
    fun refreshAllRefreshesAllProviders() = runTest {
        val registry = CloudConfigProviderRegistry()
        val provider1 = TestCloudConfigProvider("provider1")
        val provider2 = TestCloudConfigProvider("provider2")

        registry.register(provider1)
        registry.register(provider2)

        val results = registry.refreshAll()

        assertEquals(2, results.size)
        assertTrue(results["provider1"]?.isOk ?: false)
        assertTrue(results["provider2"]?.isOk ?: false)
    }

    @Test
    fun healthCheckAllChecksAllProviders() = runTest {
        val registry = CloudConfigProviderRegistry()
        val provider1 = TestCloudConfigProvider("provider1")
        val provider2 = TestCloudConfigProvider("provider2")

        registry.register(provider1)
        registry.register(provider2)

        val results = registry.healthCheckAll()

        assertEquals(2, results.size)
        assertTrue(results["provider1"]?.isOk ?: false)
        assertTrue(results["provider2"]?.isOk ?: false)
    }
}

class AbstractCloudConfigProviderTest {

    @Test
    fun extractChangedKeysDetectsNewKeys() {
        val provider = TestCloudConfigProvider("test")
        val oldConfig = mapOf("key1" to "value1")
        val newConfig = mapOf("key1" to "value1", "key2" to "value2")

        val changed = provider.testExtractChangedKeys(oldConfig, newConfig)

        assertEquals(setOf("key2"), changed)
    }

    @Test
    fun extractChangedKeysDetectsModifiedKeys() {
        val provider = TestCloudConfigProvider("test")
        val oldConfig = mapOf("key1" to "value1")
        val newConfig = mapOf("key1" to "value1-modified")

        val changed = provider.testExtractChangedKeys(oldConfig, newConfig)

        assertEquals(setOf("key1"), changed)
    }

    @Test
    fun extractChangedKeysDetectsDeletedKeys() {
        val provider = TestCloudConfigProvider("test")
        val oldConfig = mapOf("key1" to "value1", "key2" to "value2")
        val newConfig = mapOf("key1" to "value1")

        val changed = provider.testExtractChangedKeys(oldConfig, newConfig)

        assertEquals(setOf("key2"), changed)
    }

    @Test
    fun extractChangedKeysReturnsEmptyWhenNoChanges() {
        val provider = TestCloudConfigProvider("test")
        val config = mapOf("key1" to "value1")

        val changed = provider.testExtractChangedKeys(config, config)

        assertTrue(changed.isEmpty())
    }

    @Test
    fun getSourceReturnsCache() {
        val provider = TestCloudConfigProvider("test", initialConfig = mapOf("key" to "value"))

        assertEquals("value", provider.getSource()["key"])
    }

    @Test
    fun refreshUpdatesCache() = runTest {
        val provider = TestCloudConfigProvider(
            "test",
            fetchResult = Ok(mapOf("new-key" to "new-value"))
        )

        val result = provider.refresh()

        assertTrue(result.isOk)
        assertEquals("new-value", provider.getSource()["new-key"])
        assertNotNull(provider.lastRefreshedAt)
    }
}

/**
 * Tests for data class equals, hashCode, and copy methods to ensure branch coverage.
 */
class RefreshResultDataClassTest {

    @Test
    fun equalsReturnsTrueForSameValues() {
        val now = Clock.System.now()
        val result1 = RefreshResult(refreshedAt = now, changedKeys = setOf("key1"), totalKeys = 10, fromCache = false)
        val result2 = RefreshResult(refreshedAt = now, changedKeys = setOf("key1"), totalKeys = 10, fromCache = false)
        assertEquals(result1, result2)
        assertEquals(result1.hashCode(), result2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentRefreshedAt() {
        val now = Clock.System.now()
        val result1 = RefreshResult(refreshedAt = now, changedKeys = emptySet(), totalKeys = 10)
        val result2 = RefreshResult(refreshedAt = now + 1.minutes, changedKeys = emptySet(), totalKeys = 10)
        assertFalse(result1 == result2)
    }

    @Test
    fun equalsReturnsFalseForDifferentChangedKeys() {
        val now = Clock.System.now()
        val result1 = RefreshResult(refreshedAt = now, changedKeys = setOf("key1"), totalKeys = 10)
        val result2 = RefreshResult(refreshedAt = now, changedKeys = setOf("key2"), totalKeys = 10)
        assertFalse(result1 == result2)
    }

    @Test
    fun equalsReturnsFalseForDifferentTotalKeys() {
        val now = Clock.System.now()
        val result1 = RefreshResult(refreshedAt = now, changedKeys = emptySet(), totalKeys = 10)
        val result2 = RefreshResult(refreshedAt = now, changedKeys = emptySet(), totalKeys = 20)
        assertFalse(result1 == result2)
    }

    @Test
    fun equalsReturnsFalseForDifferentFromCache() {
        val now = Clock.System.now()
        val result1 = RefreshResult(refreshedAt = now, changedKeys = emptySet(), totalKeys = 10, fromCache = false)
        val result2 = RefreshResult(refreshedAt = now, changedKeys = emptySet(), totalKeys = 10, fromCache = true)
        assertFalse(result1 == result2)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val now = Clock.System.now()
        val original = RefreshResult(refreshedAt = now, changedKeys = setOf("key1"), totalKeys = 10)
        val copy = original.copy(totalKeys = 20)
        assertEquals(20, copy.totalKeys)
        assertEquals(setOf("key1"), copy.changedKeys)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val result = RefreshResult(refreshedAt = Clock.System.now(), changedKeys = emptySet(), totalKeys = 10)
        assertFalse(result.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val result = RefreshResult(refreshedAt = Clock.System.now(), changedKeys = emptySet(), totalKeys = 10)
        assertFalse(result.equals("not a result"))
    }
}

class ConfigChangeEventDataClassTest {

    @Test
    fun equalsReturnsTrueForSameValues() {
        val now = Clock.System.now()
        val event1 = ConfigChangeEvent(providerId = "p1", changedKeys = setOf("k1"), timestamp = now, changeType = ConfigChangeType.UPDATE)
        val event2 = ConfigChangeEvent(providerId = "p1", changedKeys = setOf("k1"), timestamp = now, changeType = ConfigChangeType.UPDATE)
        assertEquals(event1, event2)
        assertEquals(event1.hashCode(), event2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentProviderId() {
        val now = Clock.System.now()
        val event1 = ConfigChangeEvent(providerId = "p1", changedKeys = emptySet(), timestamp = now, changeType = ConfigChangeType.UPDATE)
        val event2 = ConfigChangeEvent(providerId = "p2", changedKeys = emptySet(), timestamp = now, changeType = ConfigChangeType.UPDATE)
        assertFalse(event1 == event2)
    }

    @Test
    fun equalsReturnsFalseForDifferentChangedKeys() {
        val now = Clock.System.now()
        val event1 = ConfigChangeEvent(providerId = "p1", changedKeys = setOf("k1"), timestamp = now, changeType = ConfigChangeType.UPDATE)
        val event2 = ConfigChangeEvent(providerId = "p1", changedKeys = setOf("k2"), timestamp = now, changeType = ConfigChangeType.UPDATE)
        assertFalse(event1 == event2)
    }

    @Test
    fun equalsReturnsFalseForDifferentTimestamp() {
        val now = Clock.System.now()
        val event1 = ConfigChangeEvent(providerId = "p1", changedKeys = emptySet(), timestamp = now, changeType = ConfigChangeType.UPDATE)
        val event2 = ConfigChangeEvent(providerId = "p1", changedKeys = emptySet(), timestamp = now + 1.minutes, changeType = ConfigChangeType.UPDATE)
        assertFalse(event1 == event2)
    }

    @Test
    fun equalsReturnsFalseForDifferentChangeType() {
        val now = Clock.System.now()
        val event1 = ConfigChangeEvent(providerId = "p1", changedKeys = emptySet(), timestamp = now, changeType = ConfigChangeType.UPDATE)
        val event2 = ConfigChangeEvent(providerId = "p1", changedKeys = emptySet(), timestamp = now, changeType = ConfigChangeType.DELETE)
        assertFalse(event1 == event2)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val now = Clock.System.now()
        val original = ConfigChangeEvent(providerId = "p1", changedKeys = setOf("k1"), timestamp = now, changeType = ConfigChangeType.UPDATE)
        val copy = original.copy(changeType = ConfigChangeType.REFRESH)
        assertEquals(ConfigChangeType.REFRESH, copy.changeType)
        assertEquals("p1", copy.providerId)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val event = ConfigChangeEvent(providerId = "p1", changedKeys = emptySet(), timestamp = Clock.System.now(), changeType = ConfigChangeType.UPDATE)
        assertFalse(event.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val event = ConfigChangeEvent(providerId = "p1", changedKeys = emptySet(), timestamp = Clock.System.now(), changeType = ConfigChangeType.UPDATE)
        assertFalse(event.equals("not an event"))
    }
}

class CloudProviderHealthDataClassTest {

    @Test
    fun equalsReturnsTrueForSameValues() {
        val now = Clock.System.now()
        val health1 = CloudProviderHealth(providerId = "p1", isHealthy = true, message = "ok", checkedAt = now, latencyMs = 50, configKeyCount = 100)
        val health2 = CloudProviderHealth(providerId = "p1", isHealthy = true, message = "ok", checkedAt = now, latencyMs = 50, configKeyCount = 100)
        assertEquals(health1, health2)
        assertEquals(health1.hashCode(), health2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentProviderId() {
        val now = Clock.System.now()
        val health1 = CloudProviderHealth(providerId = "p1", isHealthy = true, checkedAt = now)
        val health2 = CloudProviderHealth(providerId = "p2", isHealthy = true, checkedAt = now)
        assertFalse(health1 == health2)
    }

    @Test
    fun equalsReturnsFalseForDifferentIsHealthy() {
        val now = Clock.System.now()
        val health1 = CloudProviderHealth(providerId = "p1", isHealthy = true, checkedAt = now)
        val health2 = CloudProviderHealth(providerId = "p1", isHealthy = false, checkedAt = now)
        assertFalse(health1 == health2)
    }

    @Test
    fun equalsReturnsFalseForDifferentMessage() {
        val now = Clock.System.now()
        val health1 = CloudProviderHealth(providerId = "p1", isHealthy = true, message = "ok", checkedAt = now)
        val health2 = CloudProviderHealth(providerId = "p1", isHealthy = true, message = "not ok", checkedAt = now)
        assertFalse(health1 == health2)
    }

    @Test
    fun equalsReturnsFalseForDifferentCheckedAt() {
        val now = Clock.System.now()
        val health1 = CloudProviderHealth(providerId = "p1", isHealthy = true, checkedAt = now)
        val health2 = CloudProviderHealth(providerId = "p1", isHealthy = true, checkedAt = now + 1.minutes)
        assertFalse(health1 == health2)
    }

    @Test
    fun equalsReturnsFalseForDifferentLatencyMs() {
        val now = Clock.System.now()
        val health1 = CloudProviderHealth(providerId = "p1", isHealthy = true, checkedAt = now, latencyMs = 50)
        val health2 = CloudProviderHealth(providerId = "p1", isHealthy = true, checkedAt = now, latencyMs = 100)
        assertFalse(health1 == health2)
    }

    @Test
    fun equalsReturnsFalseForDifferentConfigKeyCount() {
        val now = Clock.System.now()
        val health1 = CloudProviderHealth(providerId = "p1", isHealthy = true, checkedAt = now, configKeyCount = 50)
        val health2 = CloudProviderHealth(providerId = "p1", isHealthy = true, checkedAt = now, configKeyCount = 100)
        assertFalse(health1 == health2)
    }

    @Test
    fun equalsHandlesNullableFields() {
        val now = Clock.System.now()
        val health1 = CloudProviderHealth(providerId = "p1", isHealthy = true, message = null, checkedAt = now, latencyMs = null, configKeyCount = null)
        val health2 = CloudProviderHealth(providerId = "p1", isHealthy = true, message = null, checkedAt = now, latencyMs = null, configKeyCount = null)
        assertEquals(health1, health2)
    }

    @Test
    fun equalsReturnsFalseForNullVsNonNullMessage() {
        val now = Clock.System.now()
        val health1 = CloudProviderHealth(providerId = "p1", isHealthy = true, message = null, checkedAt = now)
        val health2 = CloudProviderHealth(providerId = "p1", isHealthy = true, message = "ok", checkedAt = now)
        assertFalse(health1 == health2)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val now = Clock.System.now()
        val original = CloudProviderHealth(providerId = "p1", isHealthy = true, checkedAt = now)
        val copy = original.copy(isHealthy = false, message = "failed")
        assertFalse(copy.isHealthy)
        assertEquals("failed", copy.message)
        assertEquals("p1", copy.providerId)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val health = CloudProviderHealth(providerId = "p1", isHealthy = true, checkedAt = Clock.System.now())
        assertFalse(health.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val health = CloudProviderHealth(providerId = "p1", isHealthy = true, checkedAt = Clock.System.now())
        assertFalse(health.equals("not a health"))
    }
}

class CloudConfigProviderConfigDataClassTest {

    @Test
    fun equalsReturnsTrueForSameValues() {
        val config1 = CloudConfigProviderConfig(enabled = true, refreshInterval = 30.seconds, cacheEnabled = true, cacheTtl = 5.seconds, failureMode = FailureMode.CACHE_FALLBACK, retryConfig = RetryConfig())
        val config2 = CloudConfigProviderConfig(enabled = true, refreshInterval = 30.seconds, cacheEnabled = true, cacheTtl = 5.seconds, failureMode = FailureMode.CACHE_FALLBACK, retryConfig = RetryConfig())
        assertEquals(config1, config2)
        assertEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentEnabled() {
        val config1 = CloudConfigProviderConfig(enabled = true)
        val config2 = CloudConfigProviderConfig(enabled = false)
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentRefreshInterval() {
        val config1 = CloudConfigProviderConfig(refreshInterval = 30.seconds)
        val config2 = CloudConfigProviderConfig(refreshInterval = 60.seconds)
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentCacheEnabled() {
        val config1 = CloudConfigProviderConfig(cacheEnabled = true)
        val config2 = CloudConfigProviderConfig(cacheEnabled = false)
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentCacheTtl() {
        val config1 = CloudConfigProviderConfig(cacheTtl = 5.seconds)
        val config2 = CloudConfigProviderConfig(cacheTtl = 10.seconds)
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentFailureMode() {
        val config1 = CloudConfigProviderConfig(failureMode = FailureMode.CACHE_FALLBACK)
        val config2 = CloudConfigProviderConfig(failureMode = FailureMode.FAIL_FAST)
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentRetryConfig() {
        val config1 = CloudConfigProviderConfig(retryConfig = RetryConfig(maxRetries = 3))
        val config2 = CloudConfigProviderConfig(retryConfig = RetryConfig(maxRetries = 5))
        assertFalse(config1 == config2)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val original = CloudConfigProviderConfig(enabled = true, refreshInterval = 30.seconds)
        val copy = original.copy(enabled = false, refreshInterval = 60.seconds)
        assertFalse(copy.enabled)
        assertEquals(60.seconds, copy.refreshInterval)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val config = CloudConfigProviderConfig()
        assertFalse(config.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val config = CloudConfigProviderConfig()
        assertFalse(config.equals("not a config"))
    }
}

class RetryConfigDataClassTest {

    @Test
    fun equalsReturnsTrueForSameValues() {
        val config1 = RetryConfig(maxRetries = 3, initialDelay = 1.seconds, maxDelay = 30.seconds, multiplier = 2.0)
        val config2 = RetryConfig(maxRetries = 3, initialDelay = 1.seconds, maxDelay = 30.seconds, multiplier = 2.0)
        assertEquals(config1, config2)
        assertEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentMaxRetries() {
        val config1 = RetryConfig(maxRetries = 3)
        val config2 = RetryConfig(maxRetries = 5)
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentInitialDelay() {
        val config1 = RetryConfig(initialDelay = 1.seconds)
        val config2 = RetryConfig(initialDelay = 2.seconds)
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentMaxDelay() {
        val config1 = RetryConfig(maxDelay = 30.seconds)
        val config2 = RetryConfig(maxDelay = 60.seconds)
        assertFalse(config1 == config2)
    }

    @Test
    fun equalsReturnsFalseForDifferentMultiplier() {
        val config1 = RetryConfig(multiplier = 2.0)
        val config2 = RetryConfig(multiplier = 3.0)
        assertFalse(config1 == config2)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val original = RetryConfig(maxRetries = 3, initialDelay = 1.seconds)
        val copy = original.copy(maxRetries = 5)
        assertEquals(5, copy.maxRetries)
        assertEquals(1.seconds, copy.initialDelay)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val config = RetryConfig()
        assertFalse(config.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val config = RetryConfig()
        assertFalse(config.equals("not a config"))
    }
}

/**
 * Test implementation of CloudConfigProvider for testing.
 */
private class TestCloudConfigProvider(
    override val providerId: String,
    override val configLevel: ConfigLevel = ConfigLevel.APP,
    private val fetchResult: IdkResult<Map<String, Any>, IdkError> = Ok(emptyMap()),
    initialConfig: Map<String, Any> = emptyMap()
) : AbstractCloudConfigProvider(
    name = providerId,
    initialSource = initialConfig,
    order = 30
) {
    override var isAvailable: Boolean = true

    private val _changeFlow = MutableSharedFlow<ConfigChangeEvent>()

    override suspend fun fetchFromCloud(): IdkResult<Map<String, Any>, IdkError> = fetchResult

    override fun watchForChanges(): Flow<ConfigChangeEvent> = _changeFlow.asSharedFlow()

    override suspend fun startWatching() {}

    override suspend fun stopWatching() {}

    override suspend fun refresh(): IdkResult<RefreshResult, IdkError> {
        val now = Clock.System.now()
        val oldConfig = cachedConfig.toMap()

        val result = fetchFromCloud()
        return if (result.isOk) {
            val newConfig = result.value
            val changedKeys = extractChangedKeys(oldConfig, newConfig)
            updateCache(newConfig)
            updateLastRefreshed(now)
            Ok(RefreshResult(
                refreshedAt = now,
                changedKeys = changedKeys,
                totalKeys = newConfig.size
            ))
        } else {
            com.sphereon.core.api.Err(result.error)
        }
    }

    override suspend fun healthCheck(): IdkResult<CloudProviderHealth, IdkError> {
        return Ok(CloudProviderHealth(
            providerId = providerId,
            isHealthy = true,
            checkedAt = Clock.System.now()
        ))
    }

    // Expose protected method for testing
    fun testExtractChangedKeys(oldConfig: Map<String, Any>, newConfig: Map<String, Any>): Set<String> {
        return extractChangedKeys(oldConfig, newConfig)
    }
}
