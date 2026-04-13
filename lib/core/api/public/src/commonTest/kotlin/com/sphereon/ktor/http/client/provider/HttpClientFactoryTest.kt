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
 */

package com.sphereon.ktor.http.client.provider

import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.api.log.LoggerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpClientEngineTypeTest {

    @Test
    fun allEngineTypesAreDefined() {
        val types = HttpClientEngineType.entries
        assertEquals(4, types.size)
        assertTrue(types.contains(HttpClientEngineType.CIO))
        assertTrue(types.contains(HttpClientEngineType.OKHTTP))
        assertTrue(types.contains(HttpClientEngineType.DARWIN))
    }

    @Test
    fun engineTypeNameIsCorrect() {
        assertEquals("CIO", HttpClientEngineType.CIO.name)
        assertEquals("OKHTTP", HttpClientEngineType.OKHTTP.name)
        assertEquals("DARWIN", HttpClientEngineType.DARWIN.name)
    }
}

class HttpClientOptionsTest {

    @Test
    fun defaultOptionsHaveExpectedValues() {
        val options = HttpClientOptions()

        assertNull(options.engine)
        assertFalse(options.enableContentNegotiation)
        assertNotNull(options.sslConfig)
        assertNotNull(options.contentNegotiationConfig)
        assertFalse(options.enableHttpCache)
        assertNull(options.httpCacheConfig)
        assertTrue(options.enableLogging)
        assertEquals(LoggerConfig.Default, options.loggingConfig)
        assertNull(options.defaultRequest)
        assertNull(options.additionalConfig)
    }

    @Test
    fun createDefaultReturnsConfiguredOptions() {
        val options = HttpClientOptions.createDefault()

        assertNull(options.engine)
        assertTrue(options.enableContentNegotiation)
        assertNotNull(options.contentNegotiationConfig)
        assertFalse(options.enableHttpCache)
        assertTrue(options.enableLogging)
        assertEquals(LoggerConfig.Default, options.loggingConfig)
    }

    @Test
    fun createDefaultWithCustomLoggerConfig() {
        val customLoggerConfig = LoggerConfig(
            minLevel = LogLevel.DEBUG,
            tag = "custom-tag"
        )
        val options = HttpClientOptions.createDefault(customLoggerConfig)

        assertEquals(customLoggerConfig, options.loggingConfig)
    }

    @Test
    fun optionsCanBeCreatedWithCustomValues() {
        val options = HttpClientOptions(
            engine = HttpClientEngineType.OKHTTP,
            enableContentNegotiation = true,
            enableHttpCache = true,
            enableLogging = false
        )

        assertEquals(HttpClientEngineType.OKHTTP, options.engine)
        assertTrue(options.enableContentNegotiation)
        assertTrue(options.enableHttpCache)
        assertFalse(options.enableLogging)
    }

    @Test
    fun optionsEquality() {
        val options1 = HttpClientOptions(engine = HttpClientEngineType.CIO)
        val options2 = HttpClientOptions(engine = HttpClientEngineType.CIO)
        val options3 = HttpClientOptions(engine = HttpClientEngineType.OKHTTP)

        assertEquals(options1, options2)
        assertTrue(options1 != options3)
    }

    @Test
    fun optionsCopy() {
        val original = HttpClientOptions(
            engine = HttpClientEngineType.CIO,
            enableLogging = true
        )
        val copied = original.copy(enableLogging = false)

        assertEquals(HttpClientEngineType.CIO, copied.engine)
        assertFalse(copied.enableLogging)
        assertTrue(original.enableLogging) // Original unchanged
    }

    @Test
    fun optionsWithAllEngineTypes() {
        val cioOptions = HttpClientOptions(engine = HttpClientEngineType.CIO)
        val okhttpOptions = HttpClientOptions(engine = HttpClientEngineType.OKHTTP)
        val darwinOptions = HttpClientOptions(engine = HttpClientEngineType.DARWIN)

        assertEquals(HttpClientEngineType.CIO, cioOptions.engine)
        assertEquals(HttpClientEngineType.OKHTTP, okhttpOptions.engine)
        assertEquals(HttpClientEngineType.DARWIN, darwinOptions.engine)
    }

    @Test
    fun optionsToStringContainsAllFields() {
        val options = HttpClientOptions(
            engine = HttpClientEngineType.CIO,
            enableContentNegotiation = true,
            enableHttpCache = true,
            enableLogging = false
        )

        val str = options.toString()
        assertTrue(str.contains("HttpClientOptions"))
        assertTrue(str.contains("CIO"))
        assertTrue(str.contains("enableContentNegotiation=true"))
        assertTrue(str.contains("enableHttpCache=true"))
        assertTrue(str.contains("enableLogging=false"))
    }

    @Test
    fun optionsHashCodeIsConsistent() {
        val options1 = HttpClientOptions(engine = HttpClientEngineType.CIO)
        val options2 = HttpClientOptions(engine = HttpClientEngineType.CIO)

        assertEquals(options1.hashCode(), options2.hashCode())
    }

    @Test
    fun optionsWithCustomHttpCacheConfig() {
        val options = HttpClientOptions(
            enableHttpCache = true,
            httpCacheConfig = {
                // Config lambda is set
                isShared = true
            }
        )

        assertTrue(options.enableHttpCache)
        assertNotNull(options.httpCacheConfig)
    }

    @Test
    fun optionsWithCustomDefaultRequest() {
        var defaultRequestCalled = false
        val options = HttpClientOptions(
            defaultRequest = {
                defaultRequestCalled = true
            }
        )

        assertNotNull(options.defaultRequest)
    }

    @Test
    fun optionsWithCustomAdditionalConfig() {
        var additionalConfigCalled = false
        val options = HttpClientOptions(
            additionalConfig = {
                additionalConfigCalled = true
            }
        )

        assertNotNull(options.additionalConfig)
    }

    @Test
    fun optionsWithNullContentNegotiationConfig() {
        val options = HttpClientOptions(
            enableContentNegotiation = false,
            contentNegotiationConfig = null
        )

        assertFalse(options.enableContentNegotiation)
        assertNull(options.contentNegotiationConfig)
    }
}
