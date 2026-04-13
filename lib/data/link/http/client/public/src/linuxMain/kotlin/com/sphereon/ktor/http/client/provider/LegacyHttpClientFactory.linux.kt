/*
 * (c) 2025 Sphereon International B.V.
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

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import com.sphereon.ktor.http.client.config.LinuxSslProviderImpl

actual class LegacyHttpClientFactory {

    /**
     * Creates an HTTP client based on the specified options.
     *
     * @param options the [LegacyHttpClientOptions] containing engine type and SSL config
     * @return A [HttpClient]
     */
    actual fun createClient(options: LegacyHttpClientOptions): HttpClient {
        with(options) {
            val engine = when (engine) {
                HttpClientEngineType.CIO -> createCioEngine(sslConfig as LinuxSslProviderImpl?)
                HttpClientEngineType.OKHTTP -> TODO("OKHTTP is not supported on Linux Native")
                HttpClientEngineType.DARWIN -> TODO("Darwin is not supported on Linux Native")
                HttpClientEngineType.JS -> TODO("JS engine is not supported on Linux Native, use CIO")
            }
            return HttpClient(engine) {
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
                        loggingConfig?.invoke(this)
                    }
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
    actual fun getSupportedEngineTypes() = listOf(HttpClientEngineType.CIO)

    actual companion object {
        actual fun newInstance(): LegacyHttpClientFactory = LegacyHttpClientFactory()
        actual fun createClient(options: LegacyHttpClientOptions) = newInstance().createClient(options)
    }

    /**
     * Create a CIO engine with the specified [LinuxSslProviderImpl] options.
     *
     * @param config The SSL provider configuration
     * @return The configured HTTP client engine
     */
    private fun createCioEngine(config: LinuxSslProviderImpl?): HttpClientEngine =
        CIO.create {
            https {
                // No SSL customization support on Linux CIO yet
            }
        }
}
