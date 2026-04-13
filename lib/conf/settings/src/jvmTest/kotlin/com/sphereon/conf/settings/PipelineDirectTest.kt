/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.conf.settings

import com.sphereon.core.api.conf.DefaultPropertyInterpolator
import com.sphereon.core.api.conf.DefaultPropertySources
import com.sphereon.core.api.conf.MapPropertySource
import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.conf.PropertyResolverFactory
import com.sphereon.core.api.conf.createDefaultSecretResolver
import com.sphereon.di.Order
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests that verify the ConfigResolutionPipeline works correctly
 * when constructed manually (bypassing DI).
 *
 * This isolates pipeline functionality from DI wiring issues.
 */
class PipelineDirectTest {
    @Test
    fun testPipelineInterpolationDirect() {
        // Create a simple property source with test data
        val properties =
            mapOf(
                "base.url" to "https://api.example.com",
                "users.endpoint" to "\${base.url}/users",
            )
        val source = MapPropertySource("test", properties, Order.MEDIUM.orderValue)
        val sources = DefaultPropertySources(mutableListOf(source))

        // Create interpolator with default secret resolver
        val secretResolver = createDefaultSecretResolver()
        val interpolator = DefaultPropertyInterpolator(secretResolver = secretResolver)

        // Create pipeline-backed resolver
        val resolver: PropertyResolver =
            PropertyResolverFactory.create(
                propertySources = sources,
                interpolator = interpolator,
            )

        // Verify base value
        val baseUrl = resolver.getPropertyAsString("base.url")
        assertEquals("https://api.example.com", baseUrl)

        // Verify interpolation works
        val usersEndpoint = resolver.getPropertyAsString("users.endpoint")
        assertEquals("https://api.example.com/users", usersEndpoint)
    }

    @Test
    fun testPipelineInterpolationWithDefault() {
        val properties =
            mapOf(
                "endpoint" to "\${missing.host:localhost}/api",
            )
        val source = MapPropertySource("test", properties, Order.MEDIUM.orderValue)
        val sources = DefaultPropertySources(mutableListOf(source))

        val interpolator = DefaultPropertyInterpolator()
        val resolver = PropertyResolverFactory.create(sources, interpolator)

        // Verify default value is used
        val endpoint = resolver.getPropertyAsString("endpoint")
        assertEquals("localhost/api", endpoint)
    }

    @Test
    fun testPipelineMultipleInterpolations() {
        val properties =
            mapOf(
                "greeting" to "Hello",
                "name" to "World",
                "message" to "\${greeting}, \${name}!",
            )
        val source = MapPropertySource("test", properties, Order.MEDIUM.orderValue)
        val sources = DefaultPropertySources(mutableListOf(source))

        val interpolator = DefaultPropertyInterpolator()
        val resolver = PropertyResolverFactory.create(sources, interpolator)

        val message = resolver.getPropertyAsString("message")
        assertEquals("Hello, World!", message)
    }

    @Test
    fun testPipelineNestedInterpolation() {
        val properties =
            mapOf(
                "level1" to "value1",
                "level2" to "\${level1}-extended",
                "level3" to "\${level2}-final",
            )
        val source = MapPropertySource("test", properties, Order.MEDIUM.orderValue)
        val sources = DefaultPropertySources(mutableListOf(source))

        val interpolator = DefaultPropertyInterpolator()
        val resolver = PropertyResolverFactory.create(sources, interpolator)

        val result = resolver.getPropertyAsString("level3")
        assertEquals("value1-extended-final", result)
    }

    @Test
    fun testPipelineEnvInterpolation() {
        // Use PATH which exists on all systems
        val properties =
            mapOf(
                "env.value" to "\${env:PATH}",
            )
        val source = MapPropertySource("test", properties, Order.MEDIUM.orderValue)
        val sources = DefaultPropertySources(mutableListOf(source))

        val interpolator = DefaultPropertyInterpolator()
        val resolver = PropertyResolverFactory.create(sources, interpolator)

        val result = resolver.getPropertyAsString("env.value")
        assertNotNull(result)
        // PATH should be resolved, not the literal placeholder
        assert(!result.contains("\${env:PATH}")) { "env:PATH should be resolved" }
    }

    @Test
    fun testPipelineSecretEnvResolution() {
        val secretResolver = createDefaultSecretResolver()
        val properties =
            mapOf(
                "secret.value" to "\${secret:env:PATH}",
            )
        val source = MapPropertySource("test", properties, Order.MEDIUM.orderValue)
        val sources = DefaultPropertySources(mutableListOf(source))

        val interpolator = DefaultPropertyInterpolator(secretResolver = secretResolver)
        val resolver = PropertyResolverFactory.create(sources, interpolator)

        val result = resolver.getPropertyAsString("secret.value")
        assertNotNull(result)
        // Secret should be resolved
        assert(!result.contains("\${secret:")) { "secret reference should be resolved" }
    }

    @Test
    fun testPipelineNoInterpolationWithoutInterpolator() {
        // Test without interpolator - should return raw values
        val properties =
            mapOf(
                "base.url" to "https://api.example.com",
                "users.endpoint" to "\${base.url}/users",
            )
        val source = MapPropertySource("test", properties, Order.MEDIUM.orderValue)
        val sources = DefaultPropertySources(mutableListOf(source))

        // Create resolver WITHOUT interpolator
        val resolver =
            PropertyResolverFactory.create(
                propertySources = sources,
                interpolator = null,
            )

        // Without interpolator, value should be returned as-is
        val usersEndpoint = resolver.getPropertyAsString("users.endpoint")
        assertEquals("\${base.url}/users", usersEndpoint)
    }
}
