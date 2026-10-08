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

package com.sphereon.oauth2.jwt.validation.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.jwt.validation.IdpConfig
import com.sphereon.oauth2.jwt.validation.JwtValidationConfig
import com.sphereon.oauth2.jwt.validation.JwtValidationErrorType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class DefaultIdpRegistryTest {
    private val defaultIdp =
        IdpConfig.keycloak(
            id = "default-keycloak",
            baseUrl = "https://auth.example.com",
            realm = "master",
            audience = "my-api",
        )

    private val tenantAIdp =
        IdpConfig.azureAd(
            id = "tenant-a-azure",
            tenantId = "azure-tenant-a",
            audience = "api://tenant-a",
        )

    private val tenantBIdp =
        IdpConfig.auth0(
            id = "tenant-b-auth0",
            domain = "tenant-b.auth0.com",
            audience = "https://api.tenant-b.com",
        )

    @Test
    fun testGetDefaultIdp() {
        val config =
            JwtValidationConfig(
                enabled = true,
                defaultIdp = defaultIdp,
            )
        val registry = DefaultIdpRegistry(config)

        when (val result = registry.getDefaultIdp()) {
            is Ok -> assertEquals(defaultIdp, result.value)
            is Err -> fail("Expected Ok but got Err: ${result.error}")
        }
    }

    @Test
    fun testGetDefaultIdpWhenNotConfigured() {
        val config = JwtValidationConfig(enabled = true)
        val registry = DefaultIdpRegistry(config)

        when (val result = registry.getDefaultIdp()) {
            is Ok -> fail("Expected Err but got Ok")
            is Err -> assertEquals(JwtValidationErrorType.IDP_CONFIGURATION_ERROR, result.error.type)
        }
    }

    @Test
    fun testGetIdpForTenantWithOverride() {
        val config =
            JwtValidationConfig(
                enabled = true,
                defaultIdp = defaultIdp,
                tenantIdps = mapOf("tenant-a" to tenantAIdp),
            )
        val registry = DefaultIdpRegistry(config)

        when (val result = registry.getIdpForTenant("tenant-a")) {
            is Ok -> assertEquals(tenantAIdp, result.value)
            is Err -> fail("Expected Ok but got Err: ${result.error}")
        }
    }

    @Test
    fun testGetIdpForTenantFallsBackToDefault() {
        val config =
            JwtValidationConfig(
                enabled = true,
                defaultIdp = defaultIdp,
                tenantIdps = mapOf("tenant-a" to tenantAIdp),
            )
        val registry = DefaultIdpRegistry(config)

        when (val result = registry.getIdpForTenant("tenant-x")) {
            is Ok -> assertEquals(defaultIdp, result.value)
            is Err -> fail("Expected Ok but got Err: ${result.error}")
        }
    }

    @Test
    fun testGetIdpByIssuer() {
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
        val registry = DefaultIdpRegistry(config)

        // Test Azure AD issuer
        when (
            val azureResult =
                registry.getIdpByIssuer(
                    "https://login.microsoftonline.com/azure-tenant-a/v2.0",
                )
        ) {
            is Ok -> assertEquals(tenantAIdp, azureResult.value)
            is Err -> fail("Expected Ok but got Err: ${azureResult.error}")
        }

        // Test Auth0 issuer
        when (val auth0Result = registry.getIdpByIssuer("https://tenant-b.auth0.com/")) {
            is Ok -> assertEquals(tenantBIdp, auth0Result.value)
            is Err -> fail("Expected Ok but got Err: ${auth0Result.error}")
        }
    }

    @Test
    fun testGetIdpByIssuerNormalizesTrailingSlash() {
        val config =
            JwtValidationConfig(
                enabled = true,
                defaultIdp = defaultIdp,
            )
        val registry = DefaultIdpRegistry(config)

        // Test with trailing slash
        when (val result1 = registry.getIdpByIssuer("https://auth.example.com/realms/master/")) {
            is Ok -> { /* expected */ }

            is Err -> {
                fail("Expected Ok but got Err: ${result1.error}")
            }
        }

        // Test without trailing slash
        when (val result2 = registry.getIdpByIssuer("https://auth.example.com/realms/master")) {
            is Ok -> { /* expected */ }

            is Err -> {
                fail("Expected Ok but got Err: ${result2.error}")
            }
        }
    }

    @Test
    fun testGetIdpByIssuerFallsBackToDefaultInLaxMode() {
        val config =
            JwtValidationConfig(
                enabled = true,
                defaultIdp = defaultIdp,
                strictIssuerMatching = false,
            )
        val registry = DefaultIdpRegistry(config)

        when (val result = registry.getIdpByIssuer("https://unknown-issuer.com")) {
            is Ok -> assertEquals(defaultIdp, result.value)
            is Err -> fail("Expected Ok but got Err: ${result.error}")
        }
    }

    @Test
    fun testGetIdpByIssuerStrictModeRejectsUnknownIssuerEvenWithDefault() {
        val config =
            JwtValidationConfig(
                enabled = true,
                defaultIdp = defaultIdp,
            )
        val registry = DefaultIdpRegistry(config)

        when (val result = registry.getIdpByIssuer("https://unknown-issuer.com")) {
            is Ok -> fail("Expected Err(UntrustedIssuer) in strict mode but got Ok=${result.value}")
            is Err -> assertEquals(JwtValidationErrorType.UNTRUSTED_ISSUER, result.error.type)
        }
    }

    @Test
    fun testGetIdpByIssuerReturnsErrorWhenNoMatchAndNoDefault() {
        val config = JwtValidationConfig(enabled = true)
        val registry = DefaultIdpRegistry(config)

        when (val result = registry.getIdpByIssuer("https://unknown-issuer.com")) {
            is Ok -> fail("Expected Err but got Ok")
            is Err -> assertEquals(JwtValidationErrorType.UNTRUSTED_ISSUER, result.error.type)
        }
    }

    @Test
    fun testGetIdpById() {
        val config =
            JwtValidationConfig(
                enabled = true,
                defaultIdp = defaultIdp,
                tenantIdps = mapOf("tenant-a" to tenantAIdp),
            )
        val registry = DefaultIdpRegistry(config)

        when (val result = registry.getIdpById("tenant-a-azure")) {
            is Ok -> assertEquals(tenantAIdp, result.value)
            is Err -> fail("Expected Ok but got Err: ${result.error}")
        }
    }

    @Test
    fun testGetIdpByIdNotFound() {
        val config = JwtValidationConfig(enabled = true, defaultIdp = defaultIdp)
        val registry = DefaultIdpRegistry(config)

        when (val result = registry.getIdpById("non-existent")) {
            is Ok -> fail("Expected Err but got Ok")
            is Err -> assertEquals(JwtValidationErrorType.IDP_CONFIGURATION_ERROR, result.error.type)
        }
    }

    @Test
    fun testGetAllIdps() {
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
        val registry = DefaultIdpRegistry(config)

        val allIdps = registry.getAllIdps()

        assertEquals(3, allIdps.size)
        assertTrue(allIdps.any { it.id == "default-keycloak" })
        assertTrue(allIdps.any { it.id == "tenant-a-azure" })
        assertTrue(allIdps.any { it.id == "tenant-b-auth0" })
    }

    @Test
    fun testIsTrustedIssuer() {
        val config =
            JwtValidationConfig(
                enabled = true,
                defaultIdp = defaultIdp,
                tenantIdps = mapOf("tenant-a" to tenantAIdp),
            )
        val registry = DefaultIdpRegistry(config)

        assertTrue(registry.isTrustedIssuer("https://auth.example.com/realms/master"))
        assertTrue(registry.isTrustedIssuer("https://login.microsoftonline.com/azure-tenant-a/v2.0"))
        assertFalse(registry.isTrustedIssuer("https://evil.com"))
    }

    @Test
    fun testRegisterIdp() {
        val config = JwtValidationConfig(enabled = true, defaultIdp = defaultIdp)
        val registry = DefaultIdpRegistry(config)

        val newIdp =
            IdpConfig.oidc(
                id = "new-idp",
                issuer = "https://new-idp.com",
                audience = "new-api",
            )
        registry.registerIdp(newIdp)

        when (val result = registry.getIdpById("new-idp")) {
            is Ok -> assertEquals(newIdp, result.value)
            is Err -> fail("Expected Ok but got Err: ${result.error}")
        }
    }

    @Test
    fun testRegisterTenantIdp() {
        val config = JwtValidationConfig(enabled = true, defaultIdp = defaultIdp)
        val registry = DefaultIdpRegistry(config)

        val newIdp =
            IdpConfig.oidc(
                id = "tenant-x-idp",
                issuer = "https://tenant-x.com",
                audience = "tenant-x-api",
            )
        registry.registerTenantIdp("tenant-x", newIdp)

        when (val result = registry.getIdpForTenant("tenant-x")) {
            is Ok -> assertEquals(newIdp, result.value)
            is Err -> fail("Expected Ok but got Err: ${result.error}")
        }
    }

    @Test
    fun testRemoveIdp() {
        val config =
            JwtValidationConfig(
                enabled = true,
                defaultIdp = defaultIdp,
                tenantIdps = mapOf("tenant-a" to tenantAIdp),
            )
        val registry = DefaultIdpRegistry(config)

        assertTrue(registry.removeIdp("tenant-a-azure"))

        // IdP should no longer be accessible
        when (val result = registry.getIdpById("tenant-a-azure")) {
            is Ok -> {
                fail("Expected Err but got Ok")
            }

            is Err -> { /* expected */ }
        }

        // Tenant should fall back to default
        when (val tenantResult = registry.getIdpForTenant("tenant-a")) {
            is Ok -> assertEquals(defaultIdp, tenantResult.value)
            is Err -> fail("Expected Ok but got Err: ${tenantResult.error}")
        }
    }

    @Test
    fun testRemoveNonExistentIdp() {
        val config = JwtValidationConfig(enabled = true, defaultIdp = defaultIdp)
        val registry = DefaultIdpRegistry(config)

        assertFalse(registry.removeIdp("non-existent"))
    }

    @Test
    fun testRegisteredIdpPersistsAcrossSessions() {
        // The registry is AppScope, so a single instance serves every session.
        // Simulate "session A" registering a dynamic IdP and "session B" resolving it:
        // both sessions see the same registry instance, and the registration persists.
        val config = JwtValidationConfig(enabled = true, defaultIdp = defaultIdp)
        val registry = DefaultIdpRegistry(config)

        // Session A: register a dynamic IdP whose issuer does not match the default.
        val dynamicIssuer = "https://dynamic-idp.example.com"
        val dynamicIdp =
            IdpConfig.oidc(
                id = "dynamic-idp",
                issuer = dynamicIssuer,
                audience = "dynamic-api",
            )
        registry.registerIdp(dynamicIdp)

        // Session B: resolve the IdP by issuer and by id against the same instance.
        when (val byIssuer = registry.getIdpByIssuer(dynamicIssuer)) {
            is Ok -> assertEquals(dynamicIdp, byIssuer.value)
            is Err -> fail("Expected Ok but got Err: ${byIssuer.error}")
        }
        when (val byId = registry.getIdpById("dynamic-idp")) {
            is Ok -> assertEquals(dynamicIdp, byId.value)
            is Err -> fail("Expected Ok but got Err: ${byId.error}")
        }
        assertTrue(registry.isTrustedIssuer(dynamicIssuer))
    }
}
