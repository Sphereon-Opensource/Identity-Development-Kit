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

package com.sphereon.core.defaults.context

import com.sphereon.di.Order
import com.sphereon.di.context.TenantAware
import com.sphereon.di.context.TenantContextData
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwtClaimsParserTest {
    // Sample JWT: header.payload.signature
    // Payload: {"sub":"user123","tenant_id":"acme","email":"user@acme.com","name":"Test User"}
    private val validJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwidGVuYW50X2lkIjoiYWNtZSIsImVtYWlsIjoidXNlckBhY21lLmNvbSIsIm5hbWUiOiJUZXN0IFVzZXIifQ.signature"

    // Payload: {"sub":"system-001","tid":"contoso","preferred_username":"svc-account"}
    private val azureStyleJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJzeXN0ZW0tMDAxIiwidGlkIjoiY29udG9zbyIsInByZWZlcnJlZF91c2VybmFtZSI6InN2Yy1hY2NvdW50In0.signature"

    // Payload: {"sub":"user456","org_id":"org-xyz"}
    private val auth0StyleJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyNDU2Iiwib3JnX2lkIjoib3JnLXh5eiJ9.signature"

    @Test
    fun parseClaimsOrNullReturnsClaimsForValidJwt() {
        val claims = JwtClaimsParser.parseClaimsOrNull(validJwt)
        assertNotNull(claims)
        assertEquals("user123", claims["sub"]?.toString()?.removeSurrounding("\""))
        assertEquals("acme", claims["tenant_id"]?.toString()?.removeSurrounding("\""))
    }

    @Test
    fun parseClaimsOrNullReturnsNullForInvalidJwt() {
        assertNull(JwtClaimsParser.parseClaimsOrNull("not-a-jwt"))
        assertNull(JwtClaimsParser.parseClaimsOrNull("only.two"))
        assertNull(JwtClaimsParser.parseClaimsOrNull(""))
        assertNull(JwtClaimsParser.parseClaimsOrNull("a.b.c.d")) // too many parts
    }

    @Test
    fun parseClaimsOrNullReturnsNullForMalformedBase64() {
        // Valid structure but invalid base64 payload
        assertNull(JwtClaimsParser.parseClaimsOrNull("header.!!!invalid!!!.signature"))
    }

    @Test
    fun parseClaimsOrNullHandlesBase64WithoutPadding() {
        // JWT payloads typically don't have padding - verify parser handles this
        val claims = JwtClaimsParser.parseClaimsOrNull(validJwt)
        assertNotNull(claims)
    }

    @Test
    fun toJwtClaimsInputReturnsInputForValidJwt() {
        val input = JwtClaimsParser.toJwtClaimsInput(validJwt)
        assertNotNull(input)
        assertEquals(validJwt, input.rawToken)
        assertTrue(input.claims.containsKey("sub"))
    }

    @Test
    fun toJwtClaimsInputReturnsNullForInvalidJwt() {
        assertNull(JwtClaimsParser.toJwtClaimsInput("invalid"))
    }
}

class JwtClaimsInputTest {
    @Test
    fun implementsTenantInput() {
        val claims = mapOf("tenant_id" to JsonPrimitive("acme"))
        val input = JwtClaimsInput(claims)
        assertEquals(claims, input.tenant)
    }

    @Test
    fun implementsPrincipalInput() {
        val claims = mapOf("sub" to JsonPrimitive("user123"))
        val input = JwtClaimsInput(claims)
        assertEquals(claims, input.principal)
    }

    @Test
    fun rawTokenIsOptional() {
        val input = JwtClaimsInput(mapOf("sub" to JsonPrimitive("user")))
        assertNull(input.rawToken)
    }

    @Test
    fun rawTokenIsStored() {
        val input = JwtClaimsInput(mapOf("sub" to JsonPrimitive("user")), rawToken = "jwt-token")
        assertEquals("jwt-token", input.rawToken)
    }

    @Test
    fun toStringShowsClaimKeysAndTokenPresence() {
        val input = JwtClaimsInput(mapOf("sub" to JsonPrimitive("user")), rawToken = "token")
        val str = input.toString()
        assertTrue(str.contains("sub"))
        assertTrue(str.contains("hasRawToken=true"))
    }

    @Test
    fun toStringShowsNoTokenWhenAbsent() {
        val input = JwtClaimsInput(mapOf("sub" to JsonPrimitive("user")))
        assertTrue(input.toString().contains("hasRawToken=false"))
    }
}

class OidcTenantResolverTest {
    private val resolver = OidcTenantResolver()

    @Test
    fun orderIsHigh() {
        assertEquals(Order.HIGH.orderValue, resolver.order)
    }

    @Test
    fun supportsJwtClaimsInput() {
        val input = JwtClaimsInput(mapOf("tenant_id" to JsonPrimitive("acme")))
        assertTrue(resolver.supports(input))
    }

    @Test
    fun doesNotSupportStringInput() {
        val input = DefaultTenantInputString("acme")
        assertFalse(resolver.supports(input))
    }

    @Test
    fun resolveTenantFromTenantIdClaim() {
        val input = JwtClaimsInput(mapOf("tenant_id" to JsonPrimitive("ACME")))
        assertEquals("acme", resolver.resolveTenant(input))
    }

    @Test
    fun resolveTenantFromTidClaim() {
        val input = JwtClaimsInput(mapOf("tid" to JsonPrimitive("Contoso")))
        assertEquals("contoso", resolver.resolveTenant(input))
    }

    @Test
    fun resolveTenantFromTenantIdCamelCaseClaim() {
        val input = JwtClaimsInput(mapOf("tenantId" to JsonPrimitive("MyTenant")))
        assertEquals("mytenant", resolver.resolveTenant(input))
    }

    @Test
    fun resolveTenantFromOrgIdClaim() {
        val input = JwtClaimsInput(mapOf("org_id" to JsonPrimitive("org-123")))
        assertEquals("org-123", resolver.resolveTenant(input))
    }

    @Test
    fun resolveTenantFromOrganizationIdClaim() {
        val input = JwtClaimsInput(mapOf("organization_id" to JsonPrimitive("ORG-ABC")))
        assertEquals("org-abc", resolver.resolveTenant(input))
    }

    @Test
    fun resolveTenantFromTenantClaim() {
        val input = JwtClaimsInput(mapOf("tenant" to JsonPrimitive("MyTenant")))
        assertEquals("mytenant", resolver.resolveTenant(input))
    }

    @Test
    fun resolveTenantPrefersFirstMatchingClaim() {
        // tenant_id comes before tid in the claim list
        val input =
            JwtClaimsInput(
                mapOf(
                    "tid" to JsonPrimitive("azure-tenant"),
                    "tenant_id" to JsonPrimitive("preferred-tenant"),
                ),
            )
        assertEquals("preferred-tenant", resolver.resolveTenant(input))
    }

    @Test
    fun resolveTenantTrimsWhitespace() {
        val input = JwtClaimsInput(mapOf("tenant_id" to JsonPrimitive("  acme  ")))
        assertEquals("acme", resolver.resolveTenant(input))
    }

    @Test
    fun resolveTenantThrowsWhenNoTenantClaim() {
        val input = JwtClaimsInput(mapOf("sub" to JsonPrimitive("user123")))
        assertFailsWith<IllegalArgumentException> {
            resolver.resolveTenant(input)
        }
    }

    @Test
    fun resolveTenantSkipsBlankClaims() {
        val input =
            JwtClaimsInput(
                mapOf(
                    "tenant_id" to JsonPrimitive("   "),
                    "tid" to JsonPrimitive("valid-tenant"),
                ),
            )
        assertEquals("valid-tenant", resolver.resolveTenant(input))
    }
}

class OidcPrincipalResolverTest {
    private val resolver = OidcPrincipalResolver()

    private val testTenant =
        object : TenantAware {
            override val tenant: TenantContextData = TenantContextDataImpl("test-tenant")
        }

    @Test
    fun priorityIsHigh() {
        assertEquals(Order.HIGH.orderValue, resolver.priority)
    }

    @Test
    fun supportsJwtClaimsInput() {
        val input = JwtClaimsInput(mapOf("sub" to JsonPrimitive("user123")))
        assertTrue(resolver.supports(input))
    }

    @Test
    fun doesNotSupportStringInput() {
        val input = DefaultPrincipalInputString("user123")
        assertFalse(resolver.supports(input))
    }

    @Test
    fun resolvePrincipalFromSubClaim() {
        val input = JwtClaimsInput(mapOf("sub" to JsonPrimitive("user-abc-123")))
        assertEquals("user-abc-123", resolver.resolvePrincipal(input, testTenant))
    }

    @Test
    fun resolvePrincipalFromEmailClaim() {
        val input = JwtClaimsInput(mapOf("email" to JsonPrimitive("user@example.com")))
        assertEquals("user@example.com", resolver.resolvePrincipal(input, testTenant))
    }

    @Test
    fun resolvePrincipalFromPreferredUsernameClaim() {
        val input = JwtClaimsInput(mapOf("preferred_username" to JsonPrimitive("jdoe")))
        assertEquals("jdoe", resolver.resolvePrincipal(input, testTenant))
    }

    @Test
    fun resolvePrincipalFromUserIdClaim() {
        val input = JwtClaimsInput(mapOf("user_id" to JsonPrimitive("uid-999")))
        assertEquals("uid-999", resolver.resolvePrincipal(input, testTenant))
    }

    @Test
    fun resolvePrincipalFromUserIdCamelCaseClaim() {
        val input = JwtClaimsInput(mapOf("userId" to JsonPrimitive("UID-888")))
        assertEquals("UID-888", resolver.resolvePrincipal(input, testTenant))
    }

    @Test
    fun resolvePrincipalPrefersSubOverEmail() {
        // sub comes before email in the claim list
        val input =
            JwtClaimsInput(
                mapOf(
                    "email" to JsonPrimitive("user@example.com"),
                    "sub" to JsonPrimitive("preferred-sub"),
                ),
            )
        assertEquals("preferred-sub", resolver.resolvePrincipal(input, testTenant))
    }

    @Test
    fun resolvePrincipalTrimsWhitespace() {
        val input = JwtClaimsInput(mapOf("sub" to JsonPrimitive("  user123  ")))
        assertEquals("user123", resolver.resolvePrincipal(input, testTenant))
    }

    @Test
    fun resolvePrincipalPreservesCase() {
        // Principal case is preserved (unlike tenant which is lowercased)
        val input = JwtClaimsInput(mapOf("sub" to JsonPrimitive("UserName")))
        assertEquals("UserName", resolver.resolvePrincipal(input, testTenant))
    }

    @Test
    fun resolvePrincipalThrowsWhenNoPrincipalClaim() {
        val input = JwtClaimsInput(mapOf("tenant_id" to JsonPrimitive("acme")))
        assertFailsWith<IllegalArgumentException> {
            resolver.resolvePrincipal(input, testTenant)
        }
    }

    @Test
    fun resolvePrincipalSkipsBlankClaims() {
        val input =
            JwtClaimsInput(
                mapOf(
                    "sub" to JsonPrimitive("   "),
                    "email" to JsonPrimitive("valid@example.com"),
                ),
            )
        assertEquals("valid@example.com", resolver.resolvePrincipal(input, testTenant))
    }
}

class OidcResolverIntegrationTest {
    private val testTenant =
        object : TenantAware {
            override val tenant: TenantContextData = TenantContextDataImpl("test-tenant")
        }

    @Test
    fun oidcResolversHaveHigherPriorityThanStaticResolvers() {
        val oidcTenantResolver = OidcTenantResolver()
        val staticTenantResolver = StaticTenantResolver()

        // OIDC resolver should have lower order value (higher priority)
        assertTrue(oidcTenantResolver.order < staticTenantResolver.order)

        val oidcPrincipalResolver = OidcPrincipalResolver()
        val staticPrincipalResolver = StaticPrincipalResolver()

        assertTrue(oidcPrincipalResolver.priority < staticPrincipalResolver.priority)
    }

    @Test
    fun tenantResolutionHandlerUsesOidcResolverForJwtInput() {
        val oidcResolver = OidcTenantResolver()
        val staticResolver = StaticTenantResolver()
        val handler = TenantResolutionHandlerImpl(setOf(oidcResolver, staticResolver))

        val jwtInput = JwtClaimsInput(mapOf("tenant_id" to JsonPrimitive("jwt-tenant")))
        val result = handler.resolveTenant(jwtInput)

        assertEquals("jwt-tenant", result.tenant.tenantId)
    }

    @Test
    fun principalResolutionHandlerUsesOidcResolverForJwtInput() {
        val oidcResolver = OidcPrincipalResolver()
        val staticResolver = StaticPrincipalResolver()
        val handler = PrincipalResolutionHandlerImpl(setOf(oidcResolver, staticResolver))

        val jwtInput = JwtClaimsInput(mapOf("sub" to JsonPrimitive("jwt-user")))
        val result = handler.resolvePrincipal(jwtInput, testTenant)

        assertEquals("jwt-user", result.principal)
    }

    @Test
    fun fullFlowWithJwtClaimsParser() {
        // Simulated JWT payload: {"sub":"user123","tenant_id":"acme"}
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMTIzIiwidGVuYW50X2lkIjoiYWNtZSJ9.sig"

        val jwtInput = JwtClaimsParser.toJwtClaimsInput(jwt)
        assertNotNull(jwtInput)

        val tenantHandler = TenantResolutionHandlerImpl(setOf(OidcTenantResolver(), StaticTenantResolver()))
        val principalHandler = PrincipalResolutionHandlerImpl(setOf(OidcPrincipalResolver(), StaticPrincipalResolver()))

        val tenantAware = tenantHandler.resolveTenant(jwtInput)
        assertEquals("acme", tenantAware.tenant.tenantId)

        val principalAware = principalHandler.resolvePrincipal(jwtInput, tenantAware)
        assertEquals("user123", principalAware.principal)
    }
}
