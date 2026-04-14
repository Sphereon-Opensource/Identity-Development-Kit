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

import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.reflect.KClass
import kotlin.time.Clock

/**
 * Wraps PropertySourcesPropertyResolver with scope-partitioned snapshot caching.
 *
 * Caching is applied at the `getSubProperties()` level where expensive iteration
 * over all property sources occurs. Individual property lookups remain uncached
 * as they are typically cheap.
 *
 * Cache keys are partitioned by scope (APP/TENANT/PRINCIPAL) and tenant/principal IDs,
 * ensuring proper isolation between different execution contexts.
 *
 * Uses [SyncConfigSnapshotCache] for synchronous, non-blocking cache access that works
 * on all platforms including JavaScript.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CachingPropertySourcesPropertyResolver", exact = true)
class CachingPropertySourcesPropertyResolver(
    private val propertySources: PropertySources,
    private val snapshotCache: SyncConfigSnapshotCache,
    private val level: ConfigLevel,
    private val tenantId: String? = null,
    private val principalId: String? = null,
    private val ttlConfig: TtlConfig = TtlConfig(),
    interpolator: PropertyInterpolator? = null,
    private val redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy(),
) : ScopeAwarePropertyResolver {
    private val delegate: PropertyResolver = PropertyResolverFactory.create(propertySources, interpolator, redactionPolicy)
    private val keyNormalizer = PropertyKeyNormalizerImpl.Default

    override fun containsProperty(key: String) = delegate.containsProperty(key)

    override fun <T : Any> getProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ) = delegate.getProperty(key, targetType, defaultValue)

    override fun getPropertyAsString(
        key: String,
        defaultValue: String?,
    ) = delegate.getPropertyAsString(key, defaultValue)

    override fun <T : Any> getRequiredProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ) = delegate.getRequiredProperty(key, targetType, defaultValue)

    override fun getRequiredPropertyAsString(
        key: String,
        defaultValue: String?,
    ) = delegate.getRequiredPropertyAsString(key, defaultValue)

    override fun <T : Any> getPropertyAtScope(
        key: String,
        targetType: KClass<T>,
        scope: ConfigLevel,
    ): T? {
        val normalizedKey = keyNormalizer.normalize(key)
        for (source in sourcesForScope(propertySources, scope)) {
            if (!source.isPlatformSupported) {
                continue
            }
            val prop = source.getProperty(normalizedKey, targetType)
            if (prop != null) {
                return prop
            }
        }
        return null
    }

    override fun getPropertyAsStringAtScope(
        key: String,
        scope: ConfigLevel,
    ): String? = getPropertyAtScope(key, String::class, scope)

    override fun getAllProperties() = delegate.getAllProperties()

    override fun getAllPropertiesAsString(redact: Boolean) = delegate.getAllPropertiesAsString(redact)

    override fun getSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean,
    ): Map<String, Any> {
        val normalizedPrefixes = prefixes.map { keyNormalizer.normalize(it) }.toSet()
        val cacheKey =
            SnapshotKey(
                scope = level,
                tenantId = tenantId,
                principalId = principalId,
                prefix = normalizedPrefixes.sorted().joinToString("|"),
            )

        // Check cache using synchronous access (works on all platforms including JS)
        val cached = snapshotCache.getSnapshot(cacheKey)
        if (cached != null && !cached.isExpired()) {
            return extractFromSnapshot(cached, stripPrefix, normalizedPrefixes)
        }

        // Cache miss - resolve from delegate and cache
        val result = delegate.getSubProperties(prefixes, stripPrefix = false)
        val ttl = ttlConfig.forScope(level)
        val now = Clock.System.now()

        val snapshot =
            ConfigSnapshot(
                values =
                    result.mapValues { (k, v) ->
                        CachedConfigValue(
                            value = v,
                            metadata =
                                ResolutionMetadata(
                                    source = "PropertySourcesPropertyResolver",
                                    scope = level,
                                    originalKey = k,
                                    normalizedKey = keyNormalizer.normalize(k),
                                    order = 0,
                                    isSecret = false,
                                    isInterpolated = false,
                                    resolvedAt = now,
                                    ttl = ttl,
                                ),
                            cachedAt = now,
                            expiresAt = now + ttl,
                            preserveType = true,
                        )
                    },
                createdAt = now,
                expiresAt = now + ttl,
            )
        snapshotCache.putSnapshot(cacheKey, snapshot)

        return if (stripPrefix) {
            stripPrefixes(result, normalizedPrefixes)
        } else {
            result
        }
    }

    override fun getSubPropertiesAsString(
        prefixes: Set<String>,
        stripPrefix: Boolean,
        redact: Boolean,
    ): Map<String, String> =
        getSubProperties(prefixes, stripPrefix).mapValues { (key, value) ->
            redactIfNeeded(key, value, redact, scope = level, redactionPolicy = redactionPolicy)
        }

    private fun extractFromSnapshot(
        snapshot: ConfigSnapshot,
        stripPrefix: Boolean,
        normalizedPrefixes: Set<String>,
    ): Map<String, Any> {
        val values =
            snapshot.values
                .mapNotNull { (k, v) ->
                    v.value?.let { k to it }
                }.toMap()
        return if (stripPrefix) {
            stripPrefixes(values, normalizedPrefixes)
        } else {
            values
        }
    }

    private fun stripPrefixes(
        props: Map<String, Any>,
        prefixes: Set<String>,
    ): Map<String, Any> =
        props.mapKeys { (key, _) ->
            val normalizedKey = keyNormalizer.normalize(key)
            prefixes
                .find { normalizedKey.startsWith("$it.") }
                ?.let { normalizedKey.removePrefix("$it.").removePrefix(".") }
                ?: key
        }

    /**
     * Invalidate cached entries by prefix or clear all cached entries.
     *
     * @param prefix If provided, only entries matching this prefix will be invalidated.
     *               If null, all cached entries will be cleared.
     */
    fun invalidate(prefix: String? = null) {
        if (prefix != null) {
            snapshotCache.invalidateByPrefix(prefix)
        } else {
            snapshotCache.clear()
        }
    }
}
