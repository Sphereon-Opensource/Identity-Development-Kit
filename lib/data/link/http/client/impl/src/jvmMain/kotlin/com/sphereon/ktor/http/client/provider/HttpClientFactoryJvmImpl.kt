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

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.kms.HasKeyStoreService
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyStoreManager
import com.sphereon.crypto.kms.keystore.software.SoftwareKeyStoreService
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.config.CaOpts
import com.sphereon.ktor.http.client.config.LegacyCertificateAndKeyJvm
import com.sphereon.ktor.http.client.config.LegacyJvmSslProviderImpl
import com.sphereon.ktor.http.client.config.SslConfig
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
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
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class HttpClientFactoryJvmImpl(
    private val execution: SessionExecution,
    val kms: KeyManagerService,
    private val keyStoreManager: KeyStoreManager,
) : HttpClientFactory {
    val keyStores: MutableSet<com.sphereon.crypto.core.kms.KeyStore> = mutableSetOf()

    init {
        getAllKeystores()
    }

    private val log = execution.log.logManager.withTag("HttpClientFactory")

    /**
     * Creates an HTTP client based on the specified options.
     *
     * @param options the [LegacyHttpClientOptions] containing engine type and SSL config
     * @return A [HttpClient]
     */
    override fun createClient(options: HttpClientOptions): HttpClient {
        require(isSupportedOptions(options)) { "Provided http client options are not supported on this platform" }

        with(options) {
            val engine =
                when (engine ?: getEngineTypeDefault()) {
                    HttpClientEngineType.CIO -> TODO("CIO engine is only supported via LegacyHttpClientFactory at present")
                    HttpClientEngineType.OKHTTP -> createOkHttpEngine(options.sslConfig)
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
//                        loggingConfig?.invoke(this)
                    }
                }

                val defaultReq = options.defaultRequest
                if (defaultReq != null) {
                    defaultRequest(defaultReq)
                }

                additionalConfig?.invoke(this)
            }.also { client ->
                // Install URL validation for SSRF protection via request pipeline
                val validationPolicy = urlValidation
                if (validationPolicy != null) {
                    client.requestPipeline.intercept(io.ktor.client.request.HttpRequestPipeline.Before) {
                        validationPolicy.validate(context.url.build())
                    }
                }
            }
        }
    }

    /**
     * Returns the list of [HttpClientEngineType] supported by this provider on the current platform.
     *
     * @return A list of [HttpClientEngineType]
     */
    override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.CIO, HttpClientEngineType.OKHTTP)

    override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.OKHTTP

    override fun isSupportedOptions(options: HttpClientOptions): Boolean {
        if (!getEngineTypesSupported().contains(options.engine ?: getEngineTypeDefault())) {
            log.error("Http client engine type ${options.engine} not supported on Jvm")
            return false
        }
        return true
    }

    /**
     * Create a CIO engine with the specified [LegacyJvmSslProviderImpl] options.
     *
     * @param sslOptions the SSL configuration options used to set up the engine
     * @return A [HttpClientEngine]
     */
    private fun createCioEngine(sslOptions: LegacyJvmSslProviderImpl? = null): HttpClientEngine =
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

    private suspend fun buildTrustManagers(caOpts: CaOpts): Set<X509TrustManager> {
        // only platform defaults, no additional CAs
        if (caOpts.includePlatformDefaults && caOpts.additionalCAs.isEmpty()) {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?) // use system defaults
            return tmf.trustManagers.filterIsInstance<X509TrustManager>().toSet()
        }

        // Otherwise, we’re building a custom or combined trust store
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }

        // merge system defaults first if enabled
        if (caOpts.includePlatformDefaults) {
            val systemTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            systemTmf.init(null as KeyStore?)
            systemTmf.trustManagers.filterIsInstance<X509TrustManager>().flatMap { it.acceptedIssuers.asIterable() }.forEachIndexed { idx, cert ->
                val alias = "system-$idx-${cert.subjectX500Principal.name.hashCode()}"
                trustStore.setCertificateEntry(alias, cert)
            }
        }

        // Add additional (custom) CAs
        for (ca in caOpts.additionalCAs) {
            val keyStore = getSupportedKeyStore(ca.keyStoreId).platformKeyStore
            val cert = keyStore.getCertificate(ca.certificateAlias) ?: error("Certificate alias ${ca.certificateAlias} not found in ${ca.keyStoreId}")
            trustStore.setCertificateEntry(ca.certificateAlias, cert)
        }

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(trustStore)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().toSet()
    }

    private fun getSupportedKeyStore(id: String): SoftwareKeyStoreService {
        val keyStore = keyStores.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Keystore $id not found. Available: ${keyStores.joinToString(",") { it.id }}}")
        require(keyStore is SoftwareKeyStoreService) { "Only Software keystores can be used for SSL client certificates. Keystore $id was of type ${keyStore::class.simpleName}" }
        return keyStore
    }

    private suspend fun buildClientSslContext(
        sslConfig: SslConfig,
        trustManagers: Set<X509TrustManager>? = null,
    ): SSLContext {
        val keyManagers = mutableSetOf<X509KeyManager>()
        for (id in sslConfig.client.allKeyStoreIds()) {
            val keyStore = getSupportedKeyStore(id)
            keyManagers.addAll(keyStore.platformKeyManagerFactory.keyManagers.filterIsInstance<X509KeyManager>())
        }
        val compositeKeyManager = CompositeKeyManager(keyManagers)
        val hostBasedKeyManager =
            HostBasedKeyManager(delegate = compositeKeyManager, hostNameToAlias = sslConfig.client.hostNameToAlias(), defaultAlias = sslConfig.client.defaultAlias())

        // Trust managers (server-side CA)
        val tms = trustManagers ?: buildTrustManagers(sslConfig.server.ca)
        return SSLContext.getInstance("TLS").apply { init(arrayOf(hostBasedKeyManager), tms.toTypedArray(), null) }
    }

    /**
     * Create an OkHttp engine with the specified [SslConfig] options,
     * using Java's SSLContext for EC certificate support.
     *
     * @param sslConfig the SSL configuration options used to set up the engine
     * @return A [HttpClientEngine]
     */
    private fun createOkHttpEngine(sslConfig: SslConfig): HttpClientEngine =
        runBlocking {
            val trustManagers = buildTrustManagers(sslConfig.server.ca)
            val sslContext = buildClientSslContext(sslConfig, trustManagers)
            val httpClientEngine =
                OkHttp.create {
                    config {
                        sslSocketFactory(sslContext.socketFactory, CompositeTrustManager(trustManagers))
                    }
                }
            httpClientEngine
        }

    private fun getAllKeystores() {
        keyStores.addAll(keyStoreManager.createFromProperties(execution.conf.conf(ConfigLevel.APP)))
        keyStores.addAll(keyStoreManager.createFromProperties(execution.conf.conf(ConfigLevel.TENANT)))
        keyStores.addAll(keyStoreManager.createFromProperties(execution.conf.conf(ConfigLevel.PRINCIPAL)))
        for (providerId in kms.getProviderIds()) {
            val provider = kms.getProviderById(providerId)
            if (provider is HasKeyStoreService && provider.keyStore is SoftwareKeyStoreService) {
                keyStores.add(provider.keyStore as SoftwareKeyStoreService)
            }
        }
    }
}
