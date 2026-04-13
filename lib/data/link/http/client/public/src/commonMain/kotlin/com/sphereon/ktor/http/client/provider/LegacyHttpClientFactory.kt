/*
 * © 2025 Sphereon International B.V.
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


import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.api.log.LogService
import com.sphereon.ktor.http.client.config.LegacySslProvider
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClientConfig


/**
 * Configuration options for creating an HTTP client.
 *
 * @property engine The engine to use for the HTTP client.
 * @property sslConfig SSL configuration for the HTTP client.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("LegacyHttpClientOptions", exact = true)
data class LegacyHttpClientOptions(
    val engine: HttpClientEngineType,

    val sslConfig: LegacySslProvider? = null,
    val enableContentNegotiation: Boolean = false,

    val contentNegotiationConfig: (ContentNegotiationConfig.() -> Unit)? = {
        json(Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = false
        })
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

    val httpClientLogger: HttpClientLogger? = null,

    val loggingConfig: (LoggingConfig.() -> Unit)? = {
        logger = httpClientLogger ?: Logger.DEFAULT
        level = LogLevel.ALL
    },

    val defaultRequest: (DefaultRequest.DefaultRequestBuilder.() -> Unit)? = null,

    val additionalConfig: (HttpClientConfig<*>.() -> Unit)? = null
) {
    companion object {
        fun createDefault(sslConfig: LegacySslProvider? = null, clientLogger: HttpClientLogger? = null, loggingConfig: (LoggingConfig.() -> Unit)? = null): LegacyHttpClientOptions = LegacyHttpClientOptions(
            engine = HttpClientEngineType.CIO,
            sslConfig = sslConfig,
            enableContentNegotiation = true,
            contentNegotiationConfig = {
                json(Json {
                    encodeDefaults = true
                    ignoreUnknownKeys = true
                    prettyPrint = false
                })
            },
            enableHttpCache = false,
            enableLogging = true,
            loggingConfig = loggingConfig
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


data class HttpClientLogger(private val logService: LogService) : Logger {
    override fun log(message: String) {
        logService.debug(message)
    }
}
