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

import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.ktor.http.client.config.SslConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Configuration options for creating an HTTP client.
 *
 * This type lives in `core/api` so other modules can depend on the *interfaces* without introducing
 * build-time cycles. Concrete engines and platform-specific behavior are provided by host applications.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("HttpClientOptions", exact = true)
data class HttpClientOptions(
    val engine: HttpClientEngineType? = null,
    val enableContentNegotiation: Boolean = false,
    val sslConfig: SslConfig = SslConfig(),
    val contentNegotiationConfig: (ContentNegotiationConfig.() -> Unit)? = {
        json(
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
                prettyPrint = false
            },
        )
    },
    /**
     * If true, will install the [HttpCache] plugin.
     */
    val enableHttpCache: Boolean = false,
    /**
     * Optional lambda to further configure the cache plugin.
     */
    val httpCacheConfig: (HttpCache.Config.() -> Unit)? = null,
    val enableLogging: Boolean = true,
    val loggingConfig: LoggerConfig = LoggerConfig.Default,
    val defaultRequest: (DefaultRequest.DefaultRequestBuilder.() -> Unit)? = null,
    val additionalConfig: (HttpClientConfig<*>.() -> Unit)? = null,
    /**
     * URL validation policy for SSRF protection. When set, every request URL is validated
     * against this policy before the request is sent. Violations throw [UrlValidationException].
     *
     * Use [UrlValidationPolicy.BLOCK_PRIVATE] for external-facing clients that should not
     * access private networks, or [UrlValidationPolicy.NONE] to disable validation.
     */
    val urlValidation: UrlValidationPolicy? = null,
) {
    companion object {
        @JvmStatic
        fun createDefault(loggingConfig: LoggerConfig = LoggerConfig.Default): HttpClientOptions =
            HttpClientOptions(
                // Do not force an engine here.
                // Platform factories decide the default engine (for example OKHTTP on JVM, DARWIN on Apple).
                engine = null,
                enableContentNegotiation = true,
                contentNegotiationConfig = {
                    json(
                        Json {
                            encodeDefaults = true
                            ignoreUnknownKeys = true
                            prettyPrint = false
                        },
                    )
                },
                enableHttpCache = false,
                enableLogging = true,
                loggingConfig = loggingConfig,
            )
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("HttpClientFactory", exact = true)
interface HttpClientFactory {
    fun createClient(options: HttpClientOptions): HttpClient

    fun isSupportedOptions(options: HttpClientOptions): Boolean

    /**
     * Returns the list of [HttpClientEngineType]s supported by this provider on the current platform.
     */
    fun getEngineTypesSupported(): List<HttpClientEngineType>

    fun getEngineTypeDefault(): HttpClientEngineType
}
