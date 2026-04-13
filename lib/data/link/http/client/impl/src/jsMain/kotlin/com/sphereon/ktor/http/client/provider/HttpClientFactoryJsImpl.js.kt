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
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyStore
import com.sphereon.crypto.core.kms.KeyStoreManager
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class HttpClientFactoryJsImpl(
    private val execution: SessionExecution,
    val kms: KeyManagerService,
    private val keyStoreManager: KeyStoreManager,
) : HttpClientFactory {
    val keyStores: MutableSet<KeyStore> = mutableSetOf()

    init {
        keyStores.addAll(keyStoreManager.createFromProperties(execution.conf.conf(ConfigLevel.TENANT)))
    }

    private val log = execution.log.logManager.withTag("HttpClientFactory")

    /**
     * Creates an HTTP client based on the specified options.
     *
     * @param options the [HttpClientOptions] containing engine type and SSL config
     * @return A [HttpClient]
     */
    override fun createClient(options: HttpClientOptions): HttpClient {
        require(isSupportedOptions(options)) { "Provided http client options are not supported on this platform" }

        with(options) {
            if (sslConfig.client.isMtls()) {
                log.warn("mTLS client certificate configuration is ignored on JS — TLS is handled natively by Node.js/browser")
            }

            val engine =
                when ((engine ?: getEngineTypeDefault())) {
                    HttpClientEngineType.JS -> createJsEngine()
                    else -> TODO("$engine not supported yet. Please use JS engine on this platform")
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

                if (options.defaultRequest != null) {
                    defaultRequest(options.defaultRequest!!)
                }

                additionalConfig?.invoke(this)
            }.also { client ->
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
    override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.JS)

    override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.JS

    override fun isSupportedOptions(options: HttpClientOptions): Boolean {
        if (!getEngineTypesSupported().contains(options.engine ?: getEngineTypeDefault())) {
            log.error("Http client engine type ${options.engine} not supported on JS")
            return false
        }
        return true
    }

    /**
     * Create a JS engine that delegates TLS to Node.js/browser native HTTP stack.
     *
     * @return A [HttpClientEngine]
     */
    private fun createJsEngine(): HttpClientEngine = Js.create {}
}
