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
import kotlin.concurrent.Volatile
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
    private val interpolator: PropertyInterpolator? = null,
    private val redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy(),
    private val interpolationPolicyProvider: InterpolationPolicyProvider = DefaultInterpolationPolicyProvider(),
) : ScopeAwarePropertyResolver,
    ProtectedPropertyResolver {
    private val delegate: ProtectedPropertyResolver =
        PropertyResolverFactory.create(
            propertySources = propertySources,
            interpolator = interpolator,
            redactionPolicy = redactionPolicy,
            resolverLevel = level,
            interpolationPolicyProvider = interpolationPolicyProvider,
        )
    private val protectedDelegate = ProtectedPropertySourcesResolver(propertySources, level, redactionPolicy)
    private val canonicalDelegate: ProtectedPropertyResolver = delegate
    private val keyNormalizer = PropertyKeyNormalizerImpl.Default

    internal fun currentSnapshotKey(prefixes: Set<String>): SnapshotKey {
        val normalizedPrefixes = prefixes.map { keyNormalizer.normalize(it) }.toSet()
        return SnapshotKey(
            scope = level,
            tenantId = tenantId,
            principalId = principalId,
            prefix = normalizedPrefixes.sorted().joinToString("|"),
            sourceRevision = propertySources.revision,
            contentRevision = propertySources.refreshableContentRevision(refresh = false),
            interpolationPolicyIdentity =
                interpolationPolicyProvider.cacheIdentity ?: "interpolation-policy:unavailable",
        )
    }

    init {
        if (level != ConfigLevel.APP) {
            propertySources
                .filterNot { it is ScopedPropertySource<*> }
                .forEach { source ->
                    if (source.isPlatformSupported) {
                        source.getAllPropertyNames().forEach { key ->
                            validateConfigurationValueForRead(
                                value =
                                    runCatching { source.getProperty(key, Any::class) }.getOrNull()
                                        ?: runCatching { source.getPropertyAsString(key) }.getOrNull(),
                                sourceScope = level,
                            )
                        }
                    }
                }
        }
    }

    override val resolverLevel: ConfigLevel
        get() = level

    override fun canSetProperty(key: String) = protectedDelegate.canSetProperty(key)

    override fun canInterpolateProperty(
        key: String,
        fromScope: ConfigLevel,
    ) = protectedDelegate.canInterpolateProperty(key, fromScope)

    override fun canReadProperty(
        key: String,
        fromScope: ConfigLevel,
    ) = protectedDelegate.canReadProperty(key, fromScope)

    override fun canInterpolateEnvironment(
        name: String,
        fromScope: ConfigLevel,
    ) = protectedDelegate.canInterpolateEnvironment(name, fromScope)

    override fun getProtection(key: String): PropertyProtection? = protectedDelegate.getProtection(key)

    override fun resolvePropertyWithScope(
        key: String,
        requiredScope: ConfigLevel?,
    ): ResolvedPropertyWithScope? =
        resolveCanonicalPropertyInternal(key, requiredScope)?.toResolvedPropertyWithScope()

    internal fun resolveCanonicalPropertyInternal(
        key: String,
        requiredScope: ConfigLevel?,
    ): ResolvedValue<Any>? = canonicalDelegate.resolveCanonicalPropertyInternal(key, requiredScope)

    internal fun resolveCanonicalPropertiesInternal(prefixes: Set<String>?): Map<String, ResolvedValue<Any>> {
        if (prefixes == null) {
            return canonicalDelegate.resolveCanonicalPropertiesInternal()
        }
        val resolved = resolvePrefix(prefixes)
        return resolved.values.mapNotNull { (key, value) ->
            resolved.metadata[key]?.let { metadata -> key to ResolvedValue(value, metadata) }
        }.toMap()
    }

    @Volatile
    private var lastRefreshableContentRevision: Long = Long.MIN_VALUE

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
    ): T? = protectedDelegate.getPropertyAtScope(key, targetType, scope)

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
        val resolved = resolvePrefix(prefixes)
        return if (stripPrefix) {
            stripPrefixes(resolved.values, prefixes.map(keyNormalizer::normalize).toSet())
        } else {
            resolved.values
        }
    }

    private data class ResolvedPrefix(
        val values: Map<String, Any>,
        val metadata: Map<String, ResolutionMetadata>,
    )

    private fun resolvePrefix(prefixes: Set<String>): ResolvedPrefix {
        val normalizedPrefixes = prefixes.map { keyNormalizer.normalize(it) }.toSet()
        val cachePrefix = normalizedPrefixes.sorted().joinToString("|")
        val interpolationPolicyIdentity = interpolationPolicyProvider.cacheIdentity
        val refreshableRevision = propertySources.refreshableContentRevision(refresh = true)
        if (
            lastRefreshableContentRevision != Long.MIN_VALUE &&
            refreshableRevision != lastRefreshableContentRevision
        ) {
            when {
                tenantId != null && principalId != null -> snapshotCache.invalidatePrincipal(tenantId, principalId)
                tenantId != null -> snapshotCache.invalidateTenant(tenantId)
                else -> snapshotCache.clear()
            }
        }
        lastRefreshableContentRevision = refreshableRevision

        val cacheKey =
            SnapshotKey(
                scope = level,
                tenantId = tenantId,
                principalId = principalId,
                prefix = cachePrefix,
                sourceRevision = propertySources.revision,
                contentRevision = refreshableRevision,
                interpolationPolicyIdentity =
                    interpolationPolicyIdentity ?: "interpolation-policy:unavailable",
            )

        val cached = interpolationPolicyIdentity?.let { snapshotCache.getSnapshot(cacheKey) }
        if (
            cached != null &&
            !cached.isExpired() &&
            cached.isSafeForNormalizedPrefixes(normalizedPrefixes)
        ) {
            val cachedResult =
                cached.values.mapValues { (_, cachedValue) ->
                    cachedValue.value
                        ?: return@mapValues MissingCachedValue
                }
            if (cachedResult.values.none { it === MissingCachedValue }) {
                @Suppress("UNCHECKED_CAST")
                val typeSafeResult = cachedResult as Map<String, Any>
                return ResolvedPrefix(
                    values = typeSafeResult,
                    metadata = cached.values.mapValues { (_, cachedValue) -> cachedValue.metadata },
                )
            }
        }

        // Cache miss: the canonical hook performs winner enumeration and one typed value read
        // per key, carrying the same read's metadata through interpolation and caching.
        val canonicalResult = canonicalDelegate.resolveCanonicalPropertiesInternal(normalizedPrefixes)
        val ttl = ttlConfig.forScope(level)
        val now = Clock.System.now()
        val atomicEntries =
            canonicalResult.mapValues { (_, resolved) ->
                CachedConfigValue(
                    value = resolved.value,
                    metadata = resolved.metadata.copy(resolvedAt = now, ttl = ttl),
                    cachedAt = now,
                    expiresAt = now + ttl,
                    preserveType = true,
                )
            }

        val endingSourceRevision = propertySources.revision
        val endingContentRevision = propertySources.refreshableContentRevision(refresh = true)
        val revisionsStable =
            endingSourceRevision == cacheKey.sourceRevision &&
                endingContentRevision == cacheKey.contentRevision
        if (
            revisionsStable &&
            interpolationPolicyIdentity != null &&
            atomicEntries.isNotEmpty()
        ) {
            val snapshot =
                ConfigSnapshot(
                    values = atomicEntries,
                    createdAt = now,
                    expiresAt = now + ttl,
                )
            if (snapshot.isSafeForNormalizedPrefixes(normalizedPrefixes)) {
                snapshotCache.putSnapshot(cacheKey, snapshot)
            }
        }

        return ResolvedPrefix(
            values = canonicalResult.mapValues { (_, resolved) -> resolved.value },
            metadata = canonicalResult.mapValues { (_, resolved) -> resolved.metadata },
        )
    }

    override fun getSubPropertiesAsString(
        prefixes: Set<String>,
        stripPrefix: Boolean,
        redact: Boolean,
    ): Map<String, String> {
        val resolved = resolvePrefix(prefixes)
        val rendered =
            resolved.values.mapValues { (canonicalKey, value) ->
                val metadata = resolved.metadata[canonicalKey]
                if (!redact) {
                    value.toString()
                } else if (
                    metadata == null ||
                    metadata.provenance.sourceScope == null ||
                    metadata.provenance.hasTaint(ResolutionTaint.UNKNOWN) ||
                    metadata.provenance.hasTaint(ResolutionTaint.SENSITIVE) ||
                    redactionPolicy.shouldRedact(canonicalKey, metadata)
                ) {
                    redactionPolicy.redact(value.toString())
                } else {
                    value.toString()
                }
            }
        return if (stripPrefix) {
            stripPrefixes(rendered, prefixes.map(keyNormalizer::normalize).toSet())
        } else {
            rendered
        }
    }

    private fun <T> stripPrefixes(
        props: Map<String, T>,
        prefixes: Set<String>,
    ): Map<String, T> =
        props.mapKeys { (key, _) ->
            val normalizedKey = keyNormalizer.normalize(key)
            prefixes
                .find { normalizedKey.startsWith("$it.") }
                ?.let { normalizedKey.removePrefix("$it.").removePrefix(".") }
                ?: key
            }

    private companion object {
        private val MissingCachedValue = Any()
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
