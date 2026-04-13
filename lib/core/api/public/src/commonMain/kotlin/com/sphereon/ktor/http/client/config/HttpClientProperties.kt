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

package com.sphereon.ktor.http.client.config

import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.api.log.LogOutputFormat
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.ktor.http.client.provider.UrlValidationPolicy
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Serializable, config-driven HTTP client settings.
 *
 * All fields use nullable types so that layered merging can distinguish
 * "not set at this level" (null) from "explicitly set". The final resolved
 * config is converted to [HttpClientOptions] via [toOptions].
 *
 * Property layout example:
 * ```properties
 * http.client.content.negotiation=true
 * http.client.logging.enabled=true
 * http.client.logging.min.level=DEBUG
 * http.client.timeout.connect.ms=30000
 * http.client.timeout.request.ms=60000
 * http.client.cache.enabled=false
 * http.client.url.validation.policy=block.private
 * http.client.base.url=https://api.example.com
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("HttpClientProperties", exact = true)
@Serializable
data class HttpClientProperties(
    val engine: HttpClientEngineType? = null,
    val contentNegotiation: Boolean? = null,
    val cache: HttpCacheProperties? = null,
    val logging: HttpLoggingProperties? = null,
    val timeout: HttpTimeoutProperties? = null,
    val retry: HttpRetryProperties? = null,
    val urlValidation: HttpUrlValidationProperties? = null,
    val ssl: HttpSslProperties? = null,
    val headers: Map<String, String>? = null,
    val baseUrl: String? = null,
) {
    companion object {
        const val CONFIG_SUFFIX = "http.client"
    }
}

@Serializable
data class HttpCacheProperties(
    val enabled: Boolean? = null,
)

@Serializable
data class HttpLoggingProperties(
    val enabled: Boolean? = null,
    val minLevel: LogLevel? = null,
    val tag: String? = null,
    val outputFormat: LogOutputFormat? = null,
    val includeTimestamp: Boolean? = null,
)

/**
 * Timeout configuration. Values are in milliseconds.
 * These values are carried through to the caller for plugin installation
 * (e.g., Ktor HttpTimeout) since the core API module does not depend on
 * the timeout plugin directly.
 */
@Serializable
data class HttpTimeoutProperties(
    val connectMs: Long? = null,
    val requestMs: Long? = null,
    val socketMs: Long? = null,
)

@Serializable
data class HttpRetryProperties(
    val maxRetries: Int? = null,
    val delayMs: Long? = null,
)

@Serializable
data class HttpUrlValidationProperties(
    val policy: String? = null,
)

@Serializable
data class HttpSslProperties(
    val defaultCertificate: HttpKeystoreCertRef? = null,
    val perHostCertificate: Map<String, HttpKeystoreCertRef>? = null,
    val includePlatformCas: Boolean? = null,
    val additionalCas: List<HttpKeystoreCertRef>? = null,
)

@Serializable
data class HttpKeystoreCertRef(
    val keystoreId: String,
    val certificateAlias: String,
)

/**
 * Merge [overlay] on top of this config. Non-null fields in overlay win.
 * Nested objects are recursively merged.
 */
fun HttpClientProperties.mergeWith(overlay: HttpClientProperties): HttpClientProperties =
    HttpClientProperties(
        engine = overlay.engine ?: engine,
        contentNegotiation = overlay.contentNegotiation ?: contentNegotiation,
        cache =
            mergeNested(cache, overlay.cache) { base, over ->
                HttpCacheProperties(enabled = over.enabled ?: base.enabled)
            },
        logging =
            mergeNested(logging, overlay.logging) { base, over ->
                HttpLoggingProperties(
                    enabled = over.enabled ?: base.enabled,
                    minLevel = over.minLevel ?: base.minLevel,
                    tag = over.tag ?: base.tag,
                    outputFormat = over.outputFormat ?: base.outputFormat,
                    includeTimestamp = over.includeTimestamp ?: base.includeTimestamp,
                )
            },
        timeout =
            mergeNested(timeout, overlay.timeout) { base, over ->
                HttpTimeoutProperties(
                    connectMs = over.connectMs ?: base.connectMs,
                    requestMs = over.requestMs ?: base.requestMs,
                    socketMs = over.socketMs ?: base.socketMs,
                )
            },
        retry =
            mergeNested(retry, overlay.retry) { base, over ->
                HttpRetryProperties(
                    maxRetries = over.maxRetries ?: base.maxRetries,
                    delayMs = over.delayMs ?: base.delayMs,
                )
            },
        urlValidation =
            mergeNested(urlValidation, overlay.urlValidation) { base, over ->
                HttpUrlValidationProperties(policy = over.policy ?: base.policy)
            },
        ssl = overlay.ssl ?: ssl,
        headers = mergeHeaders(headers, overlay.headers),
        baseUrl = overlay.baseUrl ?: baseUrl,
    )

private inline fun <T> mergeNested(
    base: T?,
    overlay: T?,
    merge: (T, T) -> T,
): T? =
    when {
        overlay == null -> base
        base == null -> overlay
        else -> merge(base, overlay)
    }

private fun mergeHeaders(
    base: Map<String, String>?,
    overlay: Map<String, String>?,
): Map<String, String>? =
    when {
        overlay == null -> base
        base == null -> overlay
        else -> base + overlay
    }

/**
 * Convert resolved config-driven properties to [HttpClientOptions].
 *
 * Lambda-based options (contentNegotiationConfig, httpCacheConfig, additionalConfig)
 * cannot come from config and must be provided programmatically.
 *
 * @param additionalConfig Optional lambda for additional client configuration (e.g., HttpTimeout plugin)
 * @param defaultRequest Optional lambda for additional default request config beyond baseUrl/headers
 */
fun HttpClientProperties.toOptions(
    additionalConfig: (io.ktor.client.HttpClientConfig<*>.() -> Unit)? = null,
    defaultRequest: (io.ktor.client.plugins.DefaultRequest.DefaultRequestBuilder.() -> Unit)? = null,
): HttpClientOptions {
    val config = this
    val loggingConfig =
        LoggerConfig(
            minLevel = config.logging?.minLevel ?: LogLevel.DEBUG,
            tag = config.logging?.tag ?: "sphereon",
            outputFormat = config.logging?.outputFormat ?: LogOutputFormat.TEXT,
            includeTimestamp = config.logging?.includeTimestamp ?: false,
        )

    val sslConfig =
        config.ssl?.let { ssl ->
            SslConfig(
                client =
                    ClientSslConfig(
                        defaultCertificate = ssl.defaultCertificate?.toKeystoreCertificateOpts(),
                        perHostCertificate =
                            ssl.perHostCertificate?.mapValues { it.value.toKeystoreCertificateOpts() }
                                ?: emptyMap(),
                    ),
                server =
                    ServerSslConfig(
                        ca =
                            CaOpts(
                                includePlatformDefaults = ssl.includePlatformCas ?: true,
                                additionalCAs =
                                    ssl.additionalCas?.map { it.toKeystoreCertificateOpts() }?.toSet()
                                        ?: emptySet(),
                            ),
                    ),
            )
        } ?: SslConfig()

    val urlValidation =
        config.urlValidation?.policy?.let { policy ->
            when (
                policy
                    .lowercase()
                    .replace(".", "")
                    .replace("-", "")
                    .replace("_", "")
            ) {
                "none" -> UrlValidationPolicy.NONE
                "blockprivate" -> UrlValidationPolicy.BLOCK_PRIVATE
                else -> null
            }
        }

    val configBaseUrl = config.baseUrl
    val configHeaders = config.headers

    val combinedDefaultRequest: (io.ktor.client.plugins.DefaultRequest.DefaultRequestBuilder.() -> Unit)? =
        if (configBaseUrl != null || !configHeaders.isNullOrEmpty() || defaultRequest != null) {
            {
                if (configBaseUrl != null) {
                    url(configBaseUrl)
                }
                if (!configHeaders.isNullOrEmpty()) {
                    configHeaders.forEach { (k, v) -> headers.append(k, v) }
                }
                defaultRequest?.invoke(this)
            }
        } else {
            null
        }

    return HttpClientOptions(
        engine = config.engine,
        enableContentNegotiation = config.contentNegotiation ?: true,
        sslConfig = sslConfig,
        enableHttpCache = config.cache?.enabled ?: false,
        enableLogging = config.logging?.enabled ?: true,
        loggingConfig = loggingConfig,
        urlValidation = urlValidation,
        defaultRequest = combinedDefaultRequest,
        additionalConfig = additionalConfig,
    )
}

private fun HttpKeystoreCertRef.toKeystoreCertificateOpts() =
    KeystoreCertificateOpts(
        certificateAlias = certificateAlias,
        keyStoreId = keystoreId,
    )
