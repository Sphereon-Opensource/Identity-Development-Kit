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

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging

/**
 * Default (crypto-free) Darwin HTTP client factory.
 *
 * mTLS and keystore-backed custom trust require the KMS HTTP client factory on the classpath.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class HttpClientFactoryIosImpl(
    private val execution: SessionExecution,
) : HttpClientFactory {
    private val log = execution.log.logManager.withTag("HttpClientFactory")

    override fun createClient(options: HttpClientOptions): HttpClient {
        require(isSupportedOptions(options)) { "Provided http client options are not supported on this platform" }

        if (options.sslConfig.client.isMtls()) {
            error(
                "mTLS client certificates require the KMS HTTP client factory " +
                    "(lib-data-link-http-client-kms-impl) on the classpath",
            )
        }
        if (options.sslConfig.server.ca.additionalCAs.isNotEmpty() ||
            options.sslConfig.server.ca.resolvedCertificates.isNotEmpty()
        ) {
            error(
                "Custom connector server trust requires the KMS HTTP client factory " +
                    "(lib-data-link-http-client-kms-impl) on the classpath",
            )
        }

        with(options) {
            return HttpClient(Darwin) {
                if (enableHttpCache) {
                    install(HttpCache) {
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
                    }
                }

                if (options.defaultRequest != null) {
                    defaultRequest(options.defaultRequest!!)
                }

                if (urlValidation?.blocksAddressRanges() == true) {
                    engine { connectDirectly() }
                }

                additionalConfig?.invoke(this)
                followRedirects = options.followRedirects
            }.also { client ->
                val validationPolicy = urlValidation
                if (validationPolicy != null) {
                    client.installIosEgressPolicy(validationPolicy)
                }
            }
        }
    }

    override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.DARWIN)

    override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.DARWIN

    override fun isSupportedOptions(options: HttpClientOptions): Boolean {
        if (!getEngineTypesSupported().contains(options.engine ?: getEngineTypeDefault())) {
            log.error("Http client engine type ${options.engine} not supported on iOS/Darwin")
            return false
        }
        return true
    }
}
