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

import com.sphereon.core.api.conf.CommandConfigScope
import com.sphereon.core.api.conf.CommandScopedConfigBinder
import com.sphereon.core.api.conf.DefaultPropertySources
import com.sphereon.core.api.conf.MutableMapPropertySource
import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.PropertySourcesPropertyResolver
import com.sphereon.core.api.conf.getConfig
import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.api.log.LogOutputFormat
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpClientPropertiesMergeTest {
    @Test
    fun mergeWithOverlayNonNullFieldsWin() {
        val base =
            HttpClientProperties(
                contentNegotiation = true,
                timeout = HttpTimeoutProperties(connectMs = 30000, requestMs = 60000),
                logging = HttpLoggingProperties(enabled = true, tag = "global"),
            )
        val overlay =
            HttpClientProperties(
                timeout = HttpTimeoutProperties(connectMs = 10000),
                logging = HttpLoggingProperties(tag = "kms"),
            )

        val merged = base.mergeWith(overlay)

        assertEquals(true, merged.contentNegotiation, "Base value kept when overlay is null")
        assertEquals(10000L, merged.timeout?.connectMs, "Overlay wins for connectMs")
        assertEquals(60000L, merged.timeout?.requestMs, "Base kept for requestMs")
        assertEquals(true, merged.logging?.enabled, "Base kept for logging.enabled")
        assertEquals("kms", merged.logging?.tag, "Overlay wins for logging.tag")
    }

    @Test
    fun mergeWithNullOverlayKeepsBase() {
        val base =
            HttpClientProperties(
                engine = HttpClientEngineType.OKHTTP,
                baseUrl = "https://api.example.com",
            )
        val overlay = HttpClientProperties()

        val merged = base.mergeWith(overlay)

        assertEquals(HttpClientEngineType.OKHTTP, merged.engine)
        assertEquals("https://api.example.com", merged.baseUrl)
    }

    @Test
    fun mergeWithNullBaseUsesOverlay() {
        val base = HttpClientProperties()
        val overlay =
            HttpClientProperties(
                cache = HttpCacheProperties(enabled = true),
                retry = HttpRetryProperties(maxRetries = 3, delayMs = 1000),
            )

        val merged = base.mergeWith(overlay)

        assertEquals(true, merged.cache?.enabled)
        assertEquals(3, merged.retry?.maxRetries)
        assertEquals(1000L, merged.retry?.delayMs)
    }

    @Test
    fun mergeHeadersCombines() {
        val base =
            HttpClientProperties(
                headers = mapOf("X-Platform" to "vdx", "Accept" to "application/json"),
            )
        val overlay =
            HttpClientProperties(
                headers = mapOf("X-Platform" to "vdx-kms", "Authorization" to "Bearer token"),
            )

        val merged = base.mergeWith(overlay)

        assertEquals("vdx-kms", merged.headers?.get("X-Platform"), "Overlay header wins")
        assertEquals("Bearer token", merged.headers?.get("Authorization"), "Overlay header added")
        assertEquals("application/json", merged.headers?.get("Accept"), "Base header kept")
    }

    @Test
    fun sslReplacedWholeSale() {
        val base =
            HttpClientProperties(
                ssl =
                    HttpSslProperties(
                        defaultCertificate = HttpKeystoreCertRef("store1", "cert1"),
                        includePlatformCas = true,
                    ),
            )
        val overlay =
            HttpClientProperties(
                ssl =
                    HttpSslProperties(
                        defaultCertificate = HttpKeystoreCertRef("store2", "cert2"),
                    ),
            )

        val merged = base.mergeWith(overlay)

        assertEquals("store2", merged.ssl?.defaultCertificate?.keystoreId)
        assertNull(merged.ssl?.includePlatformCas, "SSL is replaced, not merged")
    }
}

class HttpClientPropertiesToOptionsTest {
    @Test
    fun toOptionsConvertsBasicFields() {
        val props =
            HttpClientProperties(
                engine = HttpClientEngineType.CIO,
                contentNegotiation = true,
                cache = HttpCacheProperties(enabled = true),
                logging =
                    HttpLoggingProperties(
                        enabled = true,
                        minLevel = LogLevel.INFO,
                        tag = "test",
                        outputFormat = LogOutputFormat.JSON,
                        includeTimestamp = true,
                    ),
            )

        val options = props.toOptions()

        assertEquals(HttpClientEngineType.CIO, options.engine)
        assertTrue(options.enableContentNegotiation)
        assertTrue(options.enableHttpCache)
        assertTrue(options.enableLogging)
        assertEquals(LogLevel.INFO, options.loggingConfig.minLevel)
        assertEquals("test", options.loggingConfig.tag)
        assertEquals(LogOutputFormat.JSON, options.loggingConfig.outputFormat)
        assertTrue(options.loggingConfig.includeTimestamp)
    }

    @Test
    fun toOptionsUsesDefaultsForNulls() {
        val props = HttpClientProperties()
        val options = props.toOptions()

        assertNull(options.engine)
        assertTrue(options.enableContentNegotiation)
        assertFalse(options.enableHttpCache)
        assertTrue(options.enableLogging)
        assertEquals(LogLevel.DEBUG, options.loggingConfig.minLevel)
    }

    @Test
    fun toOptionsConvertsSslConfig() {
        val props =
            HttpClientProperties(
                ssl =
                    HttpSslProperties(
                        defaultCertificate = HttpKeystoreCertRef("mystore", "mycert"),
                        perHostCertificate =
                            mapOf(
                                "api.example.com" to HttpKeystoreCertRef("hoststore", "hostcert"),
                            ),
                        includePlatformCas = false,
                    ),
            )

        val options = props.toOptions()

        assertNotNull(options.sslConfig.client.defaultCertificate)
        assertEquals(
            "mycert",
            options.sslConfig.client.defaultCertificate
                ?.certificateAlias,
        )
        assertEquals(
            "mystore",
            options.sslConfig.client.defaultCertificate
                ?.keyStoreId,
        )
        assertEquals(1, options.sslConfig.client.perHostCertificate.size)
        assertFalse(options.sslConfig.server.ca.includePlatformDefaults)
    }

    @Test
    fun toOptionsConvertsUrlValidationPolicy() {
        val none = HttpClientProperties(urlValidation = HttpUrlValidationProperties(policy = "none"))
        assertNotNull(none.toOptions().urlValidation)

        val blockPrivate = HttpClientProperties(urlValidation = HttpUrlValidationProperties(policy = "block.private"))
        assertNotNull(blockPrivate.toOptions().urlValidation)

        val noPolicy = HttpClientProperties()
        assertNull(noPolicy.toOptions().urlValidation)
    }
}

class HttpClientPropertiesConfigBindingTest {
    private fun createResolver(vararg properties: Pair<String, Any>): PropertyResolver {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        return PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
    }

    @Test
    fun bindsFromGlobalProperties() {
        val resolver =
            createResolver(
                "http.client.content.negotiation" to "true",
                "http.client.timeout.connect.ms" to "30000",
                "http.client.timeout.request.ms" to "60000",
                "http.client.logging.enabled" to "true",
                "http.client.logging.min.level" to "INFO",
                "http.client.base.url" to "https://api.example.com",
            )
        val binder =
            CommandScopedConfigBinder(
                resolver = resolver,
                scope = CommandConfigScope.GLOBAL,
            )

        val config = binder.getConfig<HttpClientProperties>(HttpClientProperties.CONFIG_SUFFIX)
        assertNotNull(config)
        assertEquals(true, config.contentNegotiation)
        assertEquals(30000L, config.timeout?.connectMs)
        assertEquals(60000L, config.timeout?.requestMs)
        assertEquals(true, config.logging?.enabled)
        assertEquals(LogLevel.INFO, config.logging?.minLevel)
        assertEquals("https://api.example.com", config.baseUrl)
    }

    @Test
    fun commandOverridesGlobalProperties() {
        val resolver =
            createResolver(
                "http.client.timeout.connect.ms" to "30000",
                "http.client.logging.enabled" to "true",
                "cmd.kms.default.default.http.client.timeout.connect.ms" to "10000",
                "cmd.kms.keys.get.http.client.logging.enabled" to "false",
            )
        val binder =
            CommandScopedConfigBinder(
                resolver = resolver,
                scope = CommandConfigScope.fromCommandId("kms.keys.get"),
            )

        val config = binder.getConfig<HttpClientProperties>(HttpClientProperties.CONFIG_SUFFIX)
        assertNotNull(config)
        assertEquals(10000L, config.timeout?.connectMs, "Module override wins")
        assertEquals(false, config.logging?.enabled, "Command override wins")
    }
}
