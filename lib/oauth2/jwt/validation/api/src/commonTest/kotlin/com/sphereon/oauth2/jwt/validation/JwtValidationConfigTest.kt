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

package com.sphereon.oauth2.jwt.validation

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwtValidationConfigTest {
    @Test
    fun testDefaultConfig() {
        val config = JwtValidationConfig()

        assertTrue(config.enabled)
        assertNull(config.defaultIdp)
        assertTrue(config.tenantIdps.isEmpty())
        assertFalse(config.anonymous.allowed)
    }

    @Test
    fun testConfigWithDefaultIdp() {
        val defaultIdp =
            IdpConfig.keycloak(
                id = "default",
                baseUrl = "https://auth.example.com",
                realm = "master",
            )

        val config =
            JwtValidationConfig(
                enabled = true,
                defaultIdp = defaultIdp,
            )

        assertEquals(defaultIdp, config.defaultIdp)
        assertTrue(config.tenantIdps.isEmpty())
    }

    @Test
    fun testConfigWithTenantIdps() {
        val defaultIdp =
            IdpConfig.keycloak(
                id = "default",
                baseUrl = "https://auth.example.com",
                realm = "master",
            )

        val tenantAIdp =
            IdpConfig.azureAd(
                id = "tenant-a",
                tenantId = "azure-tenant-a",
            )

        val tenantBIdp =
            IdpConfig.auth0(
                id = "tenant-b",
                domain = "tenant-b.auth0.com",
            )

        val config =
            JwtValidationConfig(
                enabled = true,
                defaultIdp = defaultIdp,
                tenantIdps =
                    mapOf(
                        "tenant-a" to tenantAIdp,
                        "tenant-b" to tenantBIdp,
                    ),
            )

        assertEquals(2, config.tenantIdps.size)
        assertEquals(tenantAIdp, config.tenantIdps["tenant-a"])
        assertEquals(tenantBIdp, config.tenantIdps["tenant-b"])
    }

    @Test
    fun testDisabledConfig() {
        val config = JwtValidationConfig(enabled = false)

        assertFalse(config.enabled)
    }

    @Test
    fun testSerialization() {
        val config =
            JwtValidationConfig(
                enabled = true,
                defaultIdp =
                    IdpConfig.oidc(
                        id = "default",
                        issuer = "https://auth.example.com",
                        audience = "my-api",
                    ),
                tenantIdps =
                    mapOf(
                        "tenant-a" to
                            IdpConfig.azureAd(
                                id = "azure",
                                tenantId = "azure-tenant",
                            ),
                    ),
                anonymous =
                    AnonymousAccessConfig(
                        allowed = false,
                        allowedPaths = listOf("/health", "/public"),
                    ),
            )

        val json = Json.encodeToString(config)
        val deserialized = Json.decodeFromString<JwtValidationConfig>(json)

        assertEquals(config.enabled, deserialized.enabled)
        assertEquals(config.defaultIdp?.id, deserialized.defaultIdp?.id)
        assertEquals(config.tenantIdps.size, deserialized.tenantIdps.size)
    }
}

class AnonymousAccessConfigTest {
    @Test
    fun testDefaultConfig() {
        val config = AnonymousAccessConfig()

        assertFalse(config.allowed)
        assertTrue(config.allowedPaths.isEmpty())
    }

    @Test
    fun testAllowedGlobally() {
        val config = AnonymousAccessConfig(allowed = true)

        assertTrue(config.allowed)
    }

    @Test
    fun testAllowedPaths() {
        val config =
            AnonymousAccessConfig(
                allowed = false,
                allowedPaths = listOf("/health", "/public/docs", "/api/v1/status"),
            )

        assertFalse(config.allowed)
        assertEquals(3, config.allowedPaths.size)
        assertTrue(config.allowedPaths.contains("/health"))
        assertTrue(config.allowedPaths.contains("/public/docs"))
        assertTrue(config.allowedPaths.contains("/api/v1/status"))
    }

    @Test
    fun testSerialization() {
        val config =
            AnonymousAccessConfig(
                allowed = true,
                allowedPaths = listOf("/health", "/status"),
            )

        val json = Json.encodeToString(config)
        val deserialized = Json.decodeFromString<AnonymousAccessConfig>(json)

        assertEquals(config, deserialized)
    }
}
