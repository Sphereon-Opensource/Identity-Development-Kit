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

package com.sphereon.crypto.kms.keystore.software

import com.sphereon.crypto.core.kms.KeyStoreLoaderOpts
import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes
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
    private val loaders: Map<String, KeyStoreLoader> = mapOf(
        PredefinedKeyStoreTypes.JKS.keyStoreType to JksLoader(),
        PredefinedKeyStoreTypes.PKCS12.keyStoreType to Pkcs12Loader(),
    )

    /**
     * Load a [KeyStore] using the provided [com.sphereon.crypto.kms.keystore.KeyStoreLoaderOpts].
     *
     * Delegates to the appropriate [KeyStoreLoader] based on [opts.type].
     *
     * @param opts configuration options for the KeyStore to load
     * @return the initialized [KeyStore]
     * @throws UnsupportedOperationException if no loader exists for the given type
     */
    suspend fun load(opts: KeyStoreLoaderOpts): KeyStore =
        with(opts) {
            loaders[type]?.load(opts)
                ?: throw UnsupportedOperationException("Unsupported keystore type: $type")
        }
}



data class KeyStoreCacheKey(
    val source: KeyStoreLoaderOpts.Source,
    val type: String,
    val password: String?,
)


object KeyManagerFactoryCache {
    private val kmfCache = ConcurrentHashMap<KeyStoreCacheKey, KeyManagerFactory>()
    private val mutex = Mutex()

    suspend fun getOrLoad(opts: KeyStoreLoaderOpts): KeyManagerFactory {
        val key = KeyStoreCacheKey(
            source = opts.source,
            type = opts.type,
            password = opts.keyStorePassword,
        )

        kmfCache[key]?.let { return it }

        // Only one coroutine at a time loads a given key
        return mutex.withLock {
            kmfCache[key] ?: run {
                val ks = KeyStoreLoaderFactory.load(opts)
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                    init(ks, opts.keyStorePassword?.toCharArray())
                }
                kmfCache[key] = kmf
                kmf
            }
        }
    }
}