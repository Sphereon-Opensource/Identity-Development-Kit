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
 */
package com.sphereon.ktor.http.client.provider

import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class HttpClientFactoryWasmJsImpl : HttpClientFactory {

    override fun createClient(options: HttpClientOptions): HttpClient {
        require(isSupportedOptions(options)) { "Provided http client options are not supported on this platform" }
        require(options.followRedirects) {
            "Wasm browser HTTP engine cannot construct a client with automatic redirects disabled"
        }
        return HttpClient(Js) {
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

            // Keep the caller-selected option last so additionalConfig cannot re-enable
            // auto-follow when a platform can expose manual redirects.
            followRedirects = options.followRedirects
        }.also { client ->
            val validationPolicy = options.urlValidation
            if (validationPolicy != null) {
                client.requestPipeline.intercept(io.ktor.client.request.HttpRequestPipeline.Before) {
                    validationPolicy.validate(context.url.build())
                }
            }
        }
    }

    override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.JS)

    override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.JS

    override fun isSupportedOptions(options: HttpClientOptions): Boolean = options.followRedirects
}
