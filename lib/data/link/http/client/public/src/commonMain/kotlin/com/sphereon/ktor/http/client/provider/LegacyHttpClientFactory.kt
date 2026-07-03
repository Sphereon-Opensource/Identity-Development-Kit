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

import com.sphereon.core.api.log.LogService
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.ktor.http.client.config.LegacySslProvider
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingConfig
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Configuration options for creating an HTTP client.
 *
 * @property engine The engine to use for the HTTP client.
 * @property sslConfig SSL configuration for the HTTP client.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("LegacyHttpClientOptions", exact = true)
data class LegacyHttpClientOptions
    @JvmOverloads
    constructor(
        val engine: HttpClientEngineType,
        val sslConfig: LegacySslProvider? = null,
        val enableContentNegotiation: Boolean = false,
        @JsExportIgnoreCompat
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
        @JsExportIgnoreCompat
        val httpCacheConfig: (HttpCache.Config.() -> Unit)? = null,
        val enableLogging: Boolean = true,
        val httpClientLogger: HttpClientLogger? = null,
        @JsExportIgnoreCompat
        val loggingConfig: (LoggingConfig.() -> Unit)? = {
            logger = httpClientLogger ?: Logger.DEFAULT
            level = LogLevel.INFO
            sanitizeHeader { header ->
                header.equals(HttpHeaders.Authorization, ignoreCase = true) ||
                    header.equals("Proxy-Authorization", ignoreCase = true) ||
                    header.equals(HttpHeaders.Cookie, ignoreCase = true) ||
                    header.equals(HttpHeaders.SetCookie, ignoreCase = true) ||
                    header.equals("X-Api-Key", ignoreCase = true)
            }
        },
        @JsExportIgnoreCompat
        val defaultRequest: (DefaultRequest.DefaultRequestBuilder.() -> Unit)? = null,
        @JsExportIgnoreCompat
        val additionalConfig: (HttpClientConfig<*>.() -> Unit)? = null,
    ) {
        companion object {
            @JvmStatic
            @JvmOverloads
            @JsExportIgnoreCompat
            fun createDefault(
                sslConfig: LegacySslProvider? = null,
                clientLogger: HttpClientLogger? = null,
                loggingConfig: (LoggingConfig.() -> Unit)? = null,
            ): LegacyHttpClientOptions =
                LegacyHttpClientOptions(
                    engine = HttpClientEngineType.CIO,
                    sslConfig = sslConfig,
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

expect class LegacyHttpClientFactory {
    /**
     * Creates an HTTP client based on the specified options.
     *
     * @return A [HttpClient]
     */
    fun createClient(options: LegacyHttpClientOptions): HttpClient

    /**
     * Returns the list of [HttpClientEngineType]s supported by this provider on the current platform.
     *
     * @return A list of [HttpClientEngineType]
     */
    fun getSupportedEngineTypes(): List<HttpClientEngineType>

    companion object {
        fun newInstance(): LegacyHttpClientFactory

        fun createClient(options: LegacyHttpClientOptions): HttpClient
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("HttpClientLogger", exact = true)
data class HttpClientLogger(
    private val logService: LogService,
) : Logger {
    override fun log(message: String) {
        logService.debug(message)
    }
}
