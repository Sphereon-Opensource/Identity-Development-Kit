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
package com.sphereon.ktor.http.client.provider

import com.sphereon.ktor.http.client.config.LegacyCertificateAndKeyJvm
import com.sphereon.ktor.http.client.config.LegacyJvmSslProviderImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.random.Random

actual class LegacyHttpClientFactory {
    /**
     * Creates an HTTP client based on the specified options.
     *
     * @param options the [LegacyHttpClientOptions] containing engine type and SSL config
     * @return A [HttpClient]
     */
    actual fun createClient(options: LegacyHttpClientOptions): HttpClient {
        with(options) {
            val engine =
                when (engine) {
                    HttpClientEngineType.CIO -> createCioEngine(sslConfig as? LegacyJvmSslProviderImpl?)
                    HttpClientEngineType.OKHTTP -> createOkHttpEngine(sslConfig as? LegacyJvmSslProviderImpl?)
                    HttpClientEngineType.DARWIN -> TODO("Jvm darwin not supported yet. Please use native, or CIO/OKHTTP with Jvm on MacOs")
                    HttpClientEngineType.JS -> TODO("JS engine is not supported on JVM, use CIO or OKHTTP")
                }

            return HttpClient(engine) {
                // install cache if requested
                if (enableHttpCache) {
                    install(HttpCache) {
                        // apply user‐supplied cache config, if any
                        httpCacheConfig?.invoke(this)
                    }
                }

                if (enableContentNegotiation) {
                    install(ContentNegotiation) {
                        contentNegotiationConfig?.invoke(this)
                    }
                }

                if (enableLogging) {
                    install(Logging) {
                        loggingConfig?.invoke(this)
                    }
                }

                if (options.defaultRequest != null) {
                    defaultRequest(options.defaultRequest)
                }

                additionalConfig?.invoke(this)
            }
        }
    }

    /**
     * Returns the list of [HttpClientEngineType] supported by this provider on the current platform.
     *
     * @return A list of [HttpClientEngineType]
     */
    actual fun getSupportedEngineTypes(): List<HttpClientEngineType> = listOf(HttpClientEngineType.CIO, HttpClientEngineType.OKHTTP)

    /**
     * Create a CIO engine with the specified [LegacyJvmSslProviderImpl] options.
     *
     * @param sslOptions the SSL configuration options used to set up the engine
     * @return A [HttpClientEngine]
     */
    private fun createCioEngine(sslOptions: LegacyJvmSslProviderImpl?): HttpClientEngine =
        CIO.create {
            sslOptions?.let { clientSslOptions ->
                https {
                    val (certs: List<LegacyCertificateAndKeyJvm>, trustMgr) =
                        runBlocking {
                            clientSslOptions.getCertificates() to clientSslOptions.getTrustManager()
                        }

                    certificates +=
                        certs.map {
                            val ktorCert = it.toKtor()
                            ktorCert.certificateChain.forEach { cert ->
                                if (cert.publicKey.algorithm != "RSA" || cert.publicKey.algorithm != "DSS") {
                                    throw UnsupportedOperationException(
                                        "Only RSA & DSS certificates are supported on KTOR CIO",
                                    )
                                }
                            }
                            ktorCert
                        }
                    trustManager = trustMgr
                }
            }
        }

    /**
     * Create an OkHttp engine with the specified [LegacyJvmSslProviderImpl] options,
     * using Java's SSLContext for EC certificate support.
     *
     * @param sslOptions the SSL configuration options used to set up the engine
     * @return A [HttpClientEngine]
     */
    private fun createOkHttpEngine(sslOptions: LegacyJvmSslProviderImpl?): HttpClientEngine =
        OkHttp.create {
            sslOptions?.let { clientSslOptions ->
                this.config {
                    val (clientCertificatesAndKeys, trustManager) =
                        runBlocking {
                            clientSslOptions.getCertificates() to clientSslOptions.getTrustManager()
                        }

                    val keyManagers =
                        when {
                            clientCertificatesAndKeys.isEmpty() -> {
                                null
                            }

                            else -> {
                                val ksTempPassword = generateKeystorePassword()
                                val clientKeyStore =
                                    KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                                        load(null, null)
                                        clientCertificatesAndKeys.forEachIndexed { index, certAndKey ->
                                            val alias = "client_key_$index"
                                            setKeyEntry(alias, certAndKey.key, ksTempPassword, certAndKey.certificateChain as Array<X509Certificate>)
                                        }
                                    }
                                KeyManagerFactory
                                    .getInstance(KeyManagerFactory.getDefaultAlgorithm())
                                    .apply {
                                        init(clientKeyStore, ksTempPassword)
                                    }.keyManagers
                            }
                        }

                    val sslContext =
                        SSLContext.getInstance("TLS").apply {
                            init(keyManagers, arrayOf(trustManager), SecureRandom())
                        }

                    this.sslSocketFactory(sslContext.socketFactory, trustManager)
                }
            }
        }

    private fun generateKeystorePassword(length: Int = 24): CharArray =
        (1..length)
            .map { passwordChars[Random.nextInt(passwordChars.size)] }
            .joinToString("")
            .toCharArray()

    actual companion object {
        actual fun newInstance(): LegacyHttpClientFactory = LegacyHttpClientFactory()

        actual fun createClient(options: LegacyHttpClientOptions) = newInstance().createClient(options)
    }
}

private const val ASCII_PRINTABLE_START = 33
private const val ASCII_PRINTABLE_END = 126
val passwordChars = (ASCII_PRINTABLE_START..ASCII_PRINTABLE_END).map { it.toChar() }.toCharArray()
