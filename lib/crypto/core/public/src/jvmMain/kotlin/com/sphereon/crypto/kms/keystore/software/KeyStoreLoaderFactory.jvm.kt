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

package com.sphereon.crypto.kms.keystore.software

import com.sphereon.crypto.core.kms.KeyStoreLoaderOpts
import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.KeyManagerFactory

/**
 * Factory for loading KeyStore instances based on given options.
 *
 * Maintains a map of supported [PredefinedKeyStoreTypes] to corresponding [KeyStoreLoader] implementations.
 */
object KeyStoreLoaderFactory {
    private val loaders: Map<String, KeyStoreLoader> =
        mapOf(
            PredefinedKeyStoreTypes.JKS.keyStoreType to JksLoader(),
            PredefinedKeyStoreTypes.PKCS12.keyStoreType to Pkcs12Loader(),
        )
    private val keyStoreCache = atomic<Map<KeyStoreCacheKey, KeyStore>>(emptyMap())
    private val inFlightLoads = atomic<Map<KeyStoreCacheKey, CompletableDeferred<KeyStore>>>(emptyMap())

    /**
     * Load a [KeyStore] using the provided [com.sphereon.crypto.kms.keystore.KeyStoreLoaderOpts].
     *
     * Delegates to the appropriate [KeyStoreLoader] based on [opts.type].
     *
     * @param opts configuration options for the KeyStore to load
     * @return the initialized [KeyStore]
     * @throws UnsupportedOperationException if no loader exists for the given type
     */
    suspend fun load(
        opts: KeyStoreLoaderOpts,
        forceReload: Boolean = false,
    ): KeyStore {
        val cacheKey = opts.cacheKeyOrNull()
        if (cacheKey == null) {
            return loadUncached(opts)
        }
        if (!forceReload) {
            keyStoreCache.value[cacheKey]?.let { return it }
        }

        return loadSingleFlight(cacheKey, opts, forceReload)
    }

    internal fun clearCache() {
        keyStoreCache.value = emptyMap()
        inFlightLoads.value = emptyMap()
    }

    internal fun invalidateTenant(tenantId: String): KeyStoreLoaderCacheInvalidationResult {
        val tenantSegment = TenantKeyStorePathResolver.tenantPathSegment(tenantId)
        val keyStoreInvalidation = removeKeyStoreEntries { it.matchesTenantSegment(tenantSegment) }
        val inFlightInvalidation = removeInFlightEntries { it.matchesTenantSegment(tenantSegment) }
        return KeyStoreLoaderCacheInvalidationResult(
            keyStoresBefore = keyStoreInvalidation.before,
            keyStoresAfter = keyStoreInvalidation.after,
            keyStoresEvicted = keyStoreInvalidation.evicted,
            inFlightBefore = inFlightInvalidation.before,
            inFlightAfter = inFlightInvalidation.after,
            inFlightEvicted = inFlightInvalidation.evicted,
        )
    }

    private suspend fun loadSingleFlight(
        cacheKey: KeyStoreCacheKey,
        opts: KeyStoreLoaderOpts,
        forceReload: Boolean,
    ): KeyStore {
        while (true) {
            if (!forceReload) {
                keyStoreCache.value[cacheKey]?.let { return it }
            }

            val currentInFlight = inFlightLoads.value
            currentInFlight[cacheKey]?.let { return it.await() }

            val load = CompletableDeferred<KeyStore>()
            if (inFlightLoads.compareAndSet(currentInFlight, currentInFlight + (cacheKey to load))) {
                try {
                    val keyStore = loadUncached(opts)
                    if (inFlightLoads.value[cacheKey] === load) {
                        putCached(cacheKey, keyStore)
                    }
                    load.complete(keyStore)
                    return keyStore
                } catch (t: Throwable) {
                    load.completeExceptionally(t)
                    throw t
                } finally {
                    removeInFlight(cacheKey, load)
                }
            }
        }
    }

    private suspend fun loadUncached(opts: KeyStoreLoaderOpts): KeyStore =
        with(opts) {
            loaders[type]?.load(opts)
                ?: throw UnsupportedOperationException("Unsupported keystore type: $type")
        }

    private fun putCached(
        key: KeyStoreCacheKey,
        keyStore: KeyStore,
    ) {
        while (true) {
            val current = keyStoreCache.value
            if (keyStoreCache.compareAndSet(current, current + (key to keyStore))) {
                return
            }
        }
    }

    private fun removeKeyStoreEntries(matches: (KeyStoreCacheKey) -> Boolean): CacheInvalidationCounters =
        removeKeyStoreCacheEntries(matches)

    private fun removeInFlightEntries(matches: (KeyStoreCacheKey) -> Boolean): CacheInvalidationCounters =
        removeInFlightLoadEntries(matches)

    private fun removeKeyStoreCacheEntries(matches: (KeyStoreCacheKey) -> Boolean): CacheInvalidationCounters {
        while (true) {
            val current = keyStoreCache.value
            val updated = current.filterKeys { !matches(it) }
            if (updated.size == current.size) {
                return CacheInvalidationCounters(
                    before = current.size,
                    after = current.size,
                    evicted = 0,
                )
            }
            if (keyStoreCache.compareAndSet(current, updated)) {
                return CacheInvalidationCounters(
                    before = current.size,
                    after = updated.size,
                    evicted = current.size - updated.size,
                )
            }
        }
    }

    private fun removeInFlightLoadEntries(matches: (KeyStoreCacheKey) -> Boolean): CacheInvalidationCounters {
        while (true) {
            val current = inFlightLoads.value
            val updated = current.filterKeys { !matches(it) }
            if (updated.size == current.size) {
                return CacheInvalidationCounters(
                    before = current.size,
                    after = current.size,
                    evicted = 0,
                )
            }
            if (inFlightLoads.compareAndSet(current, updated)) {
                return CacheInvalidationCounters(
                    before = current.size,
                    after = updated.size,
                    evicted = current.size - updated.size,
                )
            }
        }
    }

    private fun removeInFlight(
        key: KeyStoreCacheKey,
        load: CompletableDeferred<KeyStore>,
    ) {
        while (true) {
            val current = inFlightLoads.value
            if (current[key] !== load) {
                return
            }
            if (inFlightLoads.compareAndSet(current, current - key)) {
                return
            }
        }
    }

    private fun KeyStoreLoaderOpts.cacheKeyOrNull(): KeyStoreCacheKey? =
        when (source) {
            is KeyStoreLoaderOpts.Source.Channel -> null
            else ->
                KeyStoreCacheKey(
                    source = source.canonicalCacheSource(),
                    type = type,
                    password = keyStorePassword,
                )
        }

    private fun KeyStoreLoaderOpts.Source.canonicalCacheSource(): KeyStoreLoaderOpts.Source =
        when (this) {
            is KeyStoreLoaderOpts.Source.File -> {
                val resolvedFile = PathPlaceholderInterpreter.resolve(path) ?: java.io.File(path)
                copy(path = resolvedFile.absolutePath)
            }

            else -> this
        }

}

internal fun KeyStoreCacheKey.matchesTenantSegment(tenantSegment: String): Boolean {
    val path = (source as? KeyStoreLoaderOpts.Source.File)?.path ?: return false
    return path
        .replace('\\', '/')
        .split('/')
        .any { it == tenantSegment }
}

internal data class KeyStoreLoaderCacheInvalidationResult(
    val keyStoresBefore: Int,
    val keyStoresAfter: Int,
    val keyStoresEvicted: Int,
    val inFlightBefore: Int,
    val inFlightAfter: Int,
    val inFlightEvicted: Int,
)

private data class CacheInvalidationCounters(
    val before: Int,
    val after: Int,
    val evicted: Int,
)

data class KeyStoreCacheKey(
    val source: KeyStoreLoaderOpts.Source,
    val type: String,
    val password: String?,
)

object KeyManagerFactoryCache {
    private val kmfCache = ConcurrentHashMap<KeyStoreCacheKey, KeyManagerFactory>()
    private val mutex = Mutex()

    suspend fun getOrLoad(opts: KeyStoreLoaderOpts): KeyManagerFactory {
        val key =
            KeyStoreCacheKey(
                source = opts.source,
                type = opts.type,
                password = opts.keyStorePassword,
            )

        kmfCache[key]?.let { return it }

        // Only one coroutine at a time loads a given key
        return mutex.withLock {
            kmfCache[key] ?: run {
                val ks = KeyStoreLoaderFactory.load(opts)
                val kmf =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                        init(ks, opts.keyStorePassword?.toCharArray())
                    }
                kmfCache[key] = kmf
                kmf
            }
        }
    }

    internal fun invalidateTenant(tenantId: String): KeyManagerFactoryCacheInvalidationResult {
        val tenantSegment = TenantKeyStorePathResolver.tenantPathSegment(tenantId)
        val before = kmfCache.size
        val evicted =
            kmfCache.keys
                .filter { it.matchesTenantSegment(tenantSegment) }
                .count { key -> kmfCache.remove(key) != null }
        val after = kmfCache.size
        return KeyManagerFactoryCacheInvalidationResult(
            before = before,
            after = after,
            evicted = evicted,
        )
    }
}

internal data class KeyManagerFactoryCacheInvalidationResult(
    val before: Int,
    val after: Int,
    val evicted: Int,
)
