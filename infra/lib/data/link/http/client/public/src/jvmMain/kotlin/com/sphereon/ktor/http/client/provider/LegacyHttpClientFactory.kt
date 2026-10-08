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

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging

actual class LegacyHttpClientFactory {
    /**
     * Creates a plain HTTP client based on the specified options (no custom SSL / mTLS).
     */
    actual fun createClient(options: LegacyHttpClientOptions): HttpClient {
        with(options) {
            val engine =
                when (engine) {
                    HttpClientEngineType.CIO -> createCioEngine()
                    HttpClientEngineType.OKHTTP -> createOkHttpEngine()
                    HttpClientEngineType.DARWIN -> TODO("Jvm darwin not supported yet. Please use native, or CIO/OKHTTP with Jvm on MacOs")
                    HttpClientEngineType.JS -> TODO("JS engine is not supported on JVM, use CIO or OKHTTP")
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

                if (options.defaultRequest != null) {
                    defaultRequest(options.defaultRequest)
                }

                additionalConfig?.invoke(this)
            }
        }
    }

    actual fun getSupportedEngineTypes(): List<HttpClientEngineType> = listOf(HttpClientEngineType.CIO, HttpClientEngineType.OKHTTP)

    private fun createCioEngine(): HttpClientEngine = CIO.create {}

    private fun createOkHttpEngine(): HttpClientEngine = OkHttp.create {}

    actual companion object {
        actual fun newInstance(): LegacyHttpClientFactory = LegacyHttpClientFactory()

        actual fun createClient(options: LegacyHttpClientOptions) = newInstance().createClient(options)
    }
}
