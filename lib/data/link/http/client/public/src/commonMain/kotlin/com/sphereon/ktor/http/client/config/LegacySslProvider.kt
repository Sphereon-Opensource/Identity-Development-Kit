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

package com.sphereon.ktor.http.client.config

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * SSL options for the HTTP client.
 *
 * Implement this interface to supply custom SSL parameters such as
 * key stores, trust managers, and SSL contexts for secure connections.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("LegacySslProvider", exact = true)
interface LegacySslProvider {

    /**
     * Load and return the TrustManager for verifying server certificates.
     *
     * Usually this comes from a `TrustManagerFactory` initialized with
     * the KeyStore returned by [getKeyStore], for example via
     * `trustManagerFactory.init(getKeyStore())`.
     *
     * @return the loaded TrustManager (often an `X509TrustManager`), or `null` if not provided.
     */
    suspend fun getTrustManager(): Any? = null

    /**
     * Create and return the KeyManager for client certificate authentication.
     *
     * Normally you'd use a `KeyManagerFactory` initialized with
     * your KeyStore and its password to obtain one or more `KeyManager` instances.
     *
     * @return the configured KeyManager, or `null` if not provided.
     */
    fun getKeyManager(): Any? = null

    /**
     * Build and return the SSLContext.
     *
     * @return the initialized SSLContext, or `null` if not provided.
     */
    fun getSslContext(): Any? = null

    /**
     * Return the raw certificates (e.g. `X509Certificate[]`) that you want
     * to present or trust during the SSL handshake.
     *
     * This might read them directly from the KeyStore or from separate
     * certificate files.
     *
     * @return your client or CA certificates, or `null` if not provided.
     */
    suspend fun getCertificates(): Any? = null
}

expect fun createLegacySslProvider(opts: LegacySslConfig): LegacySslProvider
