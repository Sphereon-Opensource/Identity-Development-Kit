/*
 * Copyright 2025 Sphereon International B.V.
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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class IdpConfigTest {

    @Test
    fun testOidcFactory() {
        val config = IdpConfig.oidc(
            id = "test-oidc",
            issuer = "https://auth.example.com",
            audience = "my-api"
        )

        assertEquals("test-oidc", config.id)
        assertEquals(IdpType.OIDC, config.type)
        assertEquals("https://auth.example.com", config.issuer)
        assertEquals("my-api", config.audience)
        assertNull(config.jwksUri)
    }

    @Test
    fun testKeycloakFactory() {
        val config = IdpConfig.keycloak(
            id = "keycloak-prod",
            baseUrl = "https://auth.example.com",
            realm = "master",
            audience = "my-api"
        )

        assertEquals("keycloak-prod", config.id)
        assertEquals(IdpType.KEYCLOAK, config.type)
        assertEquals("https://auth.example.com/realms/master", config.issuer)
        assertEquals("my-api", config.audience)
        assertEquals("tenant_id", config.tenantClaim)
        assertTrue(config.tenantClaimAlternatives.contains("azp"))
    }

    @Test
    fun testAzureAdFactory() {
        val config = IdpConfig.azureAd(
            id = "azure-prod",
            tenantId = "12345678-1234-1234-1234-123456789abc",
            audience = "api://my-api"
        )

        assertEquals("azure-prod", config.id)
        assertEquals(IdpType.AZURE_AD, config.type)
        assertEquals("https://login.microsoftonline.com/12345678-1234-1234-1234-123456789abc/v2.0", config.issuer)
        assertEquals("api://my-api", config.audience)
        assertEquals("tid", config.tenantClaim)
        assertTrue(config.tenantClaimAlternatives.contains("oid"))
    }

    @Test
    fun testAuth0Factory() {
        val config = IdpConfig.auth0(
            id = "auth0-prod",
            domain = "my-tenant.auth0.com",
            audience = "https://api.example.com"
        )

        assertEquals("auth0-prod", config.id)
        assertEquals(IdpType.AUTH0, config.type)
        assertEquals("https://my-tenant.auth0.com/", config.issuer)
        assertEquals("https://api.example.com", config.audience)
        assertEquals("org_id", config.tenantClaim)
    }

    @Test
    fun testCustomFactory() {
        val config = IdpConfig.custom(
            id = "custom-idp",
            issuer = "https://legacy.example.com",
            jwksUri = "https://legacy.example.com/.well-known/jwks.json",
            audience = "legacy-api"
        )

        assertEquals("custom-idp", config.id)
        assertEquals(IdpType.CUSTOM, config.type)
        assertEquals("https://legacy.example.com", config.issuer)
        assertEquals("https://legacy.example.com/.well-known/jwks.json", config.jwksUri)
    }

    @Test
    fun testGetEffectiveDiscoveryUri() {
        val configWithoutDiscovery = IdpConfig.oidc(
            id = "test",
            issuer = "https://auth.example.com"
        )
        assertEquals(
            "https://auth.example.com/.well-known/openid-configuration",
            configWithoutDiscovery.getEffectiveDiscoveryUri()
        )

        val configWithDiscovery = IdpConfig(
            id = "test",
            issuer = "https://auth.example.com",
            discoveryUri = "https://custom.example.com/discovery"
        )
        assertEquals(
            "https://custom.example.com/discovery",
            configWithDiscovery.getEffectiveDiscoveryUri()
        )
    }

    @Test
    fun testDefaultValues() {
        val config = IdpConfig(
            id = "test",
            issuer = "https://auth.example.com"
        )

        assertEquals(IdpType.OIDC, config.type)
        assertEquals("tenant_id", config.tenantClaim)
        assertEquals(listOf("azp", "client_id"), config.tenantClaimAlternatives)
        assertEquals(listOf("RS256", "ES256"), config.allowedAlgorithms)
        assertEquals(60L, config.clockSkewSeconds)
        assertEquals(3600L, config.jwksCacheTtlSeconds)
        assertTrue(config.requiredClaims.isEmpty())
    }

    @Test
    fun testSerialization() {
        val config = IdpConfig.keycloak(
            id = "keycloak-test",
            baseUrl = "https://auth.example.com",
            realm = "test",
            audience = "test-api"
        )

        val json = Json.encodeToString(config)
        val deserialized = Json.decodeFromString<IdpConfig>(json)

        assertEquals(config, deserialized)
    }
}
