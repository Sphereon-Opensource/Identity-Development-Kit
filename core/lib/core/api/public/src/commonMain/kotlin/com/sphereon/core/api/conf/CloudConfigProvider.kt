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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Interface for cloud-based configuration providers.
 * Extends PropertySource with cloud-specific capabilities like refresh and change watching.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CloudConfigProvider", exact = true)
interface CloudConfigProvider : PropertySource<Map<String, Any>> {
    /**
     * Unique identifier for this provider (e.g., "azure.app.config", "aws.appconfig").
     */
    val providerId: String

    /**
     * Whether this provider is available and operational.
     */
    val isAvailable: Boolean

    /**
     * Configuration level this provider serves.
     */
    val configLevel: ConfigLevel

    /**
     * Time of last successful refresh.
     */
    val lastRefreshedAt: Instant?

    /**
     * Refresh configuration from the cloud source.
     *
     * @return Result indicating success or failure
     */
    suspend fun refresh(): IdkResult<RefreshResult, IdkError>

    /**
     * Watch for configuration changes.
     * Returns a flow that emits change events when configuration is updated.
     *
     * @return Flow of change events
     */
    fun watchForChanges(): Flow<ConfigChangeEvent>

    /**
     * Start watching for changes (if supported).
     * This enables the change detection mechanism (e.g., sentinel key polling).
     */
    suspend fun startWatching()

    /**
     * Stop watching for changes.
     */
    suspend fun stopWatching()

    /**
     * Check health of the cloud configuration service.
     */
    suspend fun healthCheck(): IdkResult<CloudProviderHealth, IdkError>
}

/**
 * Result of a refresh operation.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("RefreshResult", exact = true)
@CoverageExcludedDataClass
data class RefreshResult(
    val refreshedAt: Instant,
    val changedKeys: Set<String>,
    val totalKeys: Int,
    val fromCache: Boolean = false,
) {
    val hasChanges: Boolean get() = changedKeys.isNotEmpty()
}

/**
 * Event emitted when configuration changes.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ConfigChangeEvent", exact = true)
@CoverageExcludedDataClass
data class ConfigChangeEvent(
    val providerId: String,
    val changedKeys: Set<String>,
    val timestamp: Instant,
    val changeType: ConfigChangeType,
)

/**
 * Type of configuration change.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ConfigChangeType", exact = true)
enum class ConfigChangeType {
    /** Initial load of configuration. */
    INITIAL,

    /** Configuration was updated. */
    UPDATE,

    /** Configuration key was deleted. */
    DELETE,

    /** Full refresh triggered. */
    REFRESH,

    /** Sentinel key change detected. */
    SENTINEL_TRIGGERED,
}

/**
 * Health status for cloud config provider.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CloudProviderHealth", exact = true)
@CoverageExcludedDataClass
data class CloudProviderHealth(
    val providerId: String,
    val isHealthy: Boolean,
    val message: String? = null,
    val checkedAt: Instant,
    val latencyMs: Long? = null,
    val configKeyCount: Int? = null,
)

/**
 * Base configuration for cloud config providers.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CloudConfigProviderConfig", exact = true)
@CoverageExcludedDataClass
data class CloudConfigProviderConfig(
    val enabled: Boolean = true,
    val refreshInterval: Duration = 30.seconds,
    val cacheEnabled: Boolean = true,
    val cacheTtl: Duration = 5.seconds,
    val failureMode: FailureMode = FailureMode.CACHE_FALLBACK,
    val retryConfig: RetryConfig = RetryConfig(),
)

/**
 * Behavior when cloud config provider fails.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("FailureMode", exact = true)
enum class FailureMode {
    /** Return cached values on failure. */
    CACHE_FALLBACK,

    /** Propagate errors to callers. */
    FAIL_FAST,

    /** Return empty/default values on failure. */
    EMPTY_ON_FAILURE,
}

/**
 * Retry configuration for cloud operations.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("RetryConfig", exact = true)
@CoverageExcludedDataClass
data class RetryConfig(
    val maxRetries: Int = 3,
    val initialDelay: Duration = 1.seconds,
    val maxDelay: Duration = 30.seconds,
    val multiplier: Double = 2.0,
)

/**
 * Registry for cloud configuration providers.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CloudConfigProviderRegistry", exact = true)
class CloudConfigProviderRegistry {
    private val providers = mutableMapOf<String, CloudConfigProvider>()

    /**
     * Register a cloud config provider.
     */
    fun register(provider: CloudConfigProvider) {
        providers[provider.providerId] = provider
    }

    /**
     * Get a provider by ID.
     */
    fun get(providerId: String): CloudConfigProvider? = providers[providerId]

    /**
     * Get all registered providers.
     */
    fun getAll(): Collection<CloudConfigProvider> = providers.values

    /**
     * Get providers for a specific config level.
     */
    fun getForLevel(level: ConfigLevel): List<CloudConfigProvider> = providers.values.filter { it.configLevel == level }

    /**
     * Check if a provider is registered.
     */
    fun hasProvider(providerId: String): Boolean = providers.containsKey(providerId)

    /**
     * Refresh all providers.
     */
    suspend fun refreshAll(): Map<String, IdkResult<RefreshResult, IdkError>> = providers.mapValues { (_, provider) -> provider.refresh() }

    /**
     * Health check all providers.
     */
    suspend fun healthCheckAll(): Map<String, IdkResult<CloudProviderHealth, IdkError>> = providers.mapValues { (_, provider) -> provider.healthCheck() }
}

/**
 * Abstract base class for cloud config providers.
 * Provides common functionality for caching and error handling.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AbstractCloudConfigProvider", exact = true)
abstract class AbstractCloudConfigProvider(
    name: String,
    initialSource: Map<String, Any> = emptyMap(),
    order: Int,
    protected val config: CloudConfigProviderConfig = CloudConfigProviderConfig(),
) : MapPropertySource(name, initialSource, order),
    CloudConfigProvider {
    protected val cachedConfig: MutableMap<String, Any> = initialSource.toMutableMap()
    private var _lastRefreshedAt: Instant? = null

    override val lastRefreshedAt: Instant?
        get() = _lastRefreshedAt

    override fun getSource(): Map<String, Any> = cachedConfig

    /**
     * Fetch configuration from the cloud source.
     * Implementations should override this to perform actual fetching.
     */
    protected abstract suspend fun fetchFromCloud(): IdkResult<Map<String, Any>, IdkError>

    /**
     * Extract changed keys between old and new configuration.
     */
    protected fun extractChangedKeys(
        oldConfig: Map<String, Any>,
        newConfig: Map<String, Any>,
    ): Set<String> {
        val changedKeys = mutableSetOf<String>()

        // Check for new or changed keys
        newConfig.forEach { (key, value) ->
            val oldValue = oldConfig[key]
            if (oldValue != value) {
                changedKeys.add(key)
            }
        }

        // Check for deleted keys
        oldConfig.keys.forEach { key ->
            if (!newConfig.containsKey(key)) {
                changedKeys.add(key)
            }
        }

        return changedKeys
    }

    /**
     * Update the last refreshed timestamp.
     */
    protected fun updateLastRefreshed(instant: Instant) {
        _lastRefreshedAt = instant
    }

    /**
     * Update the cached configuration.
     */
    protected fun updateCache(newConfig: Map<String, Any>) {
        cachedConfig.clear()
        cachedConfig.putAll(newConfig)
    }
}
