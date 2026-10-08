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
 *
 */

package com.sphereon.ktor.http.client.provider

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging

actual class LegacyHttpClientFactory {
    actual fun createClient(options: LegacyHttpClientOptions): HttpClient {
        with(options) {
            val engine =
                when (engine) {
                    HttpClientEngineType.CIO -> createCioEngine()
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

    actual fun getSupportedEngineTypes() = listOf(HttpClientEngineType.CIO)

    actual companion object {
        actual fun newInstance(): LegacyHttpClientFactory = LegacyHttpClientFactory()

        actual fun createClient(options: LegacyHttpClientOptions) = newInstance().createClient(options)
    }

    private fun createCioEngine(): HttpClientEngine = CIO.create {}
}
