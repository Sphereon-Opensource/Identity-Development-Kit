/*
 * (c) 2026 Sphereon International B.V.
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
 */
package com.sphereon.ktor.http.client.provider

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyStore
import com.sphereon.crypto.core.kms.KeyStoreManager
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class HttpClientFactoryLinuxImpl(
    private val execution: SessionExecution,
    val kms: KeyManagerService,
    private val keyStoreManager: KeyStoreManager,
) : HttpClientFactory {
    val keyStores: MutableSet<KeyStore> = mutableSetOf()

    init {
        keyStores.addAll(keyStoreManager.createFromProperties(execution.conf.conf(ConfigLevel.TENANT)))
    }

    private val log = execution.log.logManager.withTag("HttpClientFactory")

    override fun createClient(options: HttpClientOptions): HttpClient {
        require(isSupportedOptions(options)) { "Provided http client options are not supported on this platform" }

        if (options.sslConfig.client.isMtls()) {
            log.warn("mTLS client certificate configuration is not yet supported on Linux Native")
        }

        return HttpClient(CIO) {
            if (options.enableHttpCache) {
                install(HttpCache) {
                    options.httpCacheConfig?.invoke(this)
                }
            }

            if (options.enableContentNegotiation) {
                install(ContentNegotiation) {
                    options.contentNegotiationConfig?.invoke(this)
                }
            }

            if (options.defaultRequest != null) {
                defaultRequest(options.defaultRequest!!)
            }

            options.additionalConfig?.invoke(this)
        }.also { client ->
            val validationPolicy = options.urlValidation
            if (validationPolicy != null) {
                client.requestPipeline.intercept(io.ktor.client.request.HttpRequestPipeline.Before) {
                    validationPolicy.validate(context.url.build())
                }
            }
        }
    }

    override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.CIO)

    override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO

    override fun isSupportedOptions(options: HttpClientOptions): Boolean = true
}
