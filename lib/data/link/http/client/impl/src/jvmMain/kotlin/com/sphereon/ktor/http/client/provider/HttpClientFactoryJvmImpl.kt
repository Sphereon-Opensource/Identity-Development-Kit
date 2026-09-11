/*
 * Â© 2026 Sphereon International B.V.
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
import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.api.log.LoggerConfig
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
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import io.ktor.client.plugins.logging.LogLevel as KtorLogLevel

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class HttpClientFactoryJvmImpl(
    private val execution: SessionExecution,
    private val kms: Provider<KeyManagerService>,
    private val keyStoreManager: Provider<KeyStoreManager>,
) : HttpClientFactory {
    val keyStores: MutableSet<com.sphereon.crypto.core.kms.KeyStore> = mutableSetOf()

    private var configuredKeyStoresLoaded = false
    private var providerKeyStoresLoaded = false

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
                    HttpClientEngineType.OKHTTP -> createOkHttpEngine(options)
                    HttpClientEngineType.DARWIN -> TODO("Jvm darwin not supported yet. Please use native, or CIO/OKHTTP with Jvm on MacOs")
                    HttpClientEngineType.JS -> TODO("JS engine is not supported on JVM, use CIO or OKHTTP")
                }

            return HttpClient(engine) {
                followRedirects = options.followRedirects
                // install cache if requested
                if (enableHttpCache) {
                    install(HttpCache) {
                        // apply userâ€supplied cache config, if any
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
                        level = loggingConfig.toKtorHttpClientLogLevel()
                        sanitizeHeader { header -> header.isSensitiveHttpClientLogHeader() }
                    }
                }

                val defaultReq = options.defaultRequest
                if (defaultReq != null) {
                    defaultRequest(defaultReq)
                }

                additionalConfig?.invoke(this)

                // Keep the caller-selected option last so an arbitrary additionalConfig cannot
                // silently re-enable auto-follow for a governed (false) client.
                followRedirects = options.followRedirects
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
        if (caOpts.includePlatformDefaults && !caOpts.hasCustomTrust()) {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?) // use system defaults
            return tmf.trustManagers.filterIsInstance<X509TrustManager>().toSet()
        }

        // Otherwise, weâ€™re building a custom or combined trust store
        if (caOpts.additionalCAs.isNotEmpty()) {
            addConfiguredKeystores()
            addProviderKeystores()
        }
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

        // TrustDomain resolution returns public CA material directly. Parse it into this
        // execution-only JVM trust store; it is never persisted in connector descriptors.
        val certificateFactory = CertificateFactory.getInstance("X.509")
        for (ca in caOpts.resolvedCertificates) {
            val certificate = ByteArrayInputStream(ca.certificatePem.encodeToByteArray()).use { input ->
                certificateFactory.generateCertificate(input) as X509Certificate
            }
            val normalizedFingerprint = ca.certificateFingerprint.filter { it.isLetterOrDigit() }.lowercase()
            val (fingerprintAlgorithm, expectedFingerprint) = when {
                normalizedFingerprint.startsWith("sha256") -> "SHA-256" to normalizedFingerprint.removePrefix("sha256")
                normalizedFingerprint.startsWith("sha1") -> "SHA-1" to normalizedFingerprint.removePrefix("sha1")
                normalizedFingerprint.length == SHA1_HEX_LENGTH -> "SHA-1" to normalizedFingerprint
                else -> "SHA-256" to normalizedFingerprint
            }
            val actualFingerprint = MessageDigest.getInstance(fingerprintAlgorithm)
                .digest(certificate.encoded)
                .joinToString("") { "%02x".format(it) }
            require(actualFingerprint == expectedFingerprint) {
                "Resolved CA certificate '${ca.certificateAlias}' fingerprint does not match its public material"
            }
            trustStore.setCertificateEntry("resolved-${ca.certificateAlias}", certificate)
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
        val clientKeyStoreIds = sslConfig.client.allKeyStoreIds()
        // The overwhelmingly common case uses the platform trust store without mTLS. Do not
        // enter the KMS graph for that case: infrastructure clients such as workload-token minting
        // may need this HTTP client in order to authenticate the very KMS lookup this used to make.
        if (clientKeyStoreIds.isNotEmpty()) {
            addConfiguredKeystores()
            addProviderKeystores()
        }
        val keyManagers = mutableSetOf<X509KeyManager>()
        for (id in clientKeyStoreIds) {
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
    private fun createOkHttpEngine(options: HttpClientOptions): HttpClientEngine =
        runBlocking {
            val sslConfig = options.sslConfig
            val trustManagers = buildTrustManagers(sslConfig.server.ca)
            val sslContext = buildClientSslContext(sslConfig, trustManagers)
            val httpClientEngine =
                OkHttp.create {
                    config {
                        sslSocketFactory(sslContext.socketFactory, CompositeTrustManager(trustManagers))
                        followRedirects(options.followRedirects)
                        followSslRedirects(options.followRedirects)
                        val versions = when (sslConfig.client.minimumTlsVersion) {
                            HttpMinimumTlsVersion.TLS_1_2 -> arrayOf(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                            HttpMinimumTlsVersion.TLS_1_3 -> arrayOf(TlsVersion.TLS_1_3)
                        }
                        connectionSpecs(
                            listOf(
                                ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS)
                                    .tlsVersions(*versions)
                                    .build(),
                                // Preserve the legacy generic HTTP-client surface. Governed request
                                // contexts reject non-HTTPS destinations before this factory is called.
                                ConnectionSpec.CLEARTEXT,
                            ),
                        )
                    }
                }
            httpClientEngine
        }

    private fun addConfiguredKeystores() {
        if (configuredKeyStoresLoaded) return
        configuredKeyStoresLoaded = true
        val manager = keyStoreManager()
        keyStores.addAll(manager.createFromProperties(execution.conf.conf(ConfigLevel.APP)))
        keyStores.addAll(manager.createFromProperties(execution.conf.conf(ConfigLevel.TENANT)))
        keyStores.addAll(manager.createFromProperties(execution.conf.conf(ConfigLevel.PRINCIPAL)))
    }

    private companion object {
        const val SHA1_HEX_LENGTH: Int = 40
    }

    /**
     * Adds the keystores that KMS providers own. Resolving a provider suspends, so this cannot run
     * from the constructor and instead runs on the first path that needs a keystore by id.
     */
    private suspend fun addProviderKeystores() {
        if (providerKeyStoresLoaded) return
        providerKeyStoresLoaded = true
        val manager = kms()
        for (providerId in manager.getProviderIds()) {
            val provider = manager.getProviderById(providerId)
            if (provider is HasKeyStoreService && provider.keyStore is SoftwareKeyStoreService) {
                keyStores.add(provider.keyStore as SoftwareKeyStoreService)
            }
        }
    }
}

internal fun LoggerConfig.toKtorHttpClientLogLevel(): KtorLogLevel =
    when (minLevel) {
        LogLevel.TRACE -> KtorLogLevel.ALL
        LogLevel.DEBUG, LogLevel.INFO -> KtorLogLevel.INFO
        LogLevel.WARN, LogLevel.ERROR, LogLevel.OFF -> KtorLogLevel.NONE
    }

internal fun String.isSensitiveHttpClientLogHeader(): Boolean =
    equals(HttpHeaders.Authorization, ignoreCase = true) ||
        equals("Proxy-Authorization", ignoreCase = true) ||
        equals(HttpHeaders.Cookie, ignoreCase = true) ||
        equals(HttpHeaders.SetCookie, ignoreCase = true) ||
        equals("X-Api-Key", ignoreCase = true)
