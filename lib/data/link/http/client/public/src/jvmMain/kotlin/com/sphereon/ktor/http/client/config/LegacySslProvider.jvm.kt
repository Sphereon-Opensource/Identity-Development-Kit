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

package com.sphereon.ktor.http.client.config

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.x509.convertToJavaPrivateKey
import com.sphereon.crypto.core.x509.javaX509CertificateFromDer
import com.sphereon.crypto.kms.keystore.software.KeyStoreLoaderFactory
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * SSL configuration for the HTTP client.
 *
 * @param opts the SSL configuration options, including certificate aliases,
 *        key store settings, and trust store settings
 */
class LegacyJvmSslProviderImpl(
    private val opts: LegacySslConfig,
) : LegacySslProvider {
    /**
     * Return the raw certificates to present during the SSL handshake.
     *
     * @return A list of [LegacyCertificateAndKeyJvm].
     */
    override suspend fun getCertificates(): List<LegacyCertificateAndKeyJvm> {
        with(opts) {
            return certificateAliases.map { alias ->
                val certChain = certificateStoreService.getCertificateChain(alias)
                val privateKey = keyStoreService.getKey(keyInfo = KeyInfo<Jwk>(alias = alias))

                val certificateAndKey =
                    LegacyCertificateAndKeyJvm(
                        certChain.map { certificate -> javaX509CertificateFromDer(certificate.der) }.toTypedArray(),
                        convertToJavaPrivateKey(privateKey),
                    )
                certificateAndKey
            }
        }
    }

    /* Code should move to file KMS
                        val keystore: KeyStore = getJavaKeyStore() ?: return emptyList()
                    certificateAliases.map { alias ->
                        with(keystore) {
                            val privateKey = getKey(alias, keyStoreOpts.keyStorePassword.toCharArray()) as? PrivateKey
                                ?: throw IllegalStateException("No private key found for alias: $alias")

                            val certChain = getCertificateChain(alias)
                                ?.map { it as X509Certificate }
                                ?: throw IllegalStateException("No certificate chain found for alias: $alias")

                            CertificateAndKey(
                                certChain.toTypedArray(),
                                privateKey
                            )
                        }
                    }
*/

    /**
     * Return the TrustManager for verifying server certificates.
     *
     * @return A [X509TrustManager] instance.
     */
    override suspend fun getTrustManager(): X509TrustManager {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        if (opts.trustStoreOpts == null) {
            // Return the default X509TrustManager when no custom trust store is configured
            trustManagerFactory.init(null as KeyStore?) // Initialize with default CAs from JVM
        } else {
            val trustStore = KeyStoreLoaderFactory.load(opts.trustStoreOpts)
            trustManagerFactory.apply {
                init(trustStore)
            }
        }
        val allTrustManagers =
            trustManagerFactory.trustManagers
                .filterIsInstance<X509TrustManager>()
                .ifEmpty { throw IllegalStateException("No default X509TrustManager found in TrustManagerFactory.") }

        @Suppress("CustomX509TrustManager")
        val combinedTrustManager =
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = allTrustManagers.flatMap { it.acceptedIssuers.asList() }.toTypedArray()

                override fun checkClientTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) {
                    var lastException: CertificateException? = null
                    for (tm in allTrustManagers) {
                        try {
                            tm.checkClientTrusted(chain, authType)
                            return // Success: one trust manager accepted
                        } catch (e: CertificateException) {
                            lastException = e
                        }
                    }
                    throw lastException ?: CertificateException("None of the TrustManagers trust this client certificate chain")
                }

                override fun checkServerTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) {
                    var lastException: CertificateException? = null
                    for (tm in allTrustManagers) {
                        try {
                            tm.checkServerTrusted(chain, authType)
                            return // one trust manager accepted
                        } catch (e: CertificateException) {
                            lastException = e
                        }
                    }
                    throw lastException ?: CertificateException("None of the TrustManagers trust this server certificate chain")
                }
            }

        return combinedTrustManager
    }
}

actual fun createLegacySslProvider(opts: LegacySslConfig): LegacySslProvider = LegacyJvmSslProviderImpl(opts)
