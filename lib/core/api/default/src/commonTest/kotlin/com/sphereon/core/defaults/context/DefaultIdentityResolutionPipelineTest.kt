package com.sphereon.core.defaults.context

import com.sphereon.di.context.IdentityResolutionInput
import com.sphereon.di.context.PrincipalType
import com.sphereon.di.context.ResolutionSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultIdentityResolutionPipelineTest {
    private fun pipeline() = DefaultIdentityResolutionPipeline(
        tenantResolvers = setOf(OidcTenantResolver(), StaticTenantResolver(), EmailDomainTenantResolver()),
        principalResolvers = setOf(OidcPrincipalResolver(), StaticPrincipalResolver(), EmailPrincipalResolver()),
    )

    @Test
    fun jwtClaimsResolveTenantAndUser() = runTest {
        val result = pipeline().resolve(
            IdentityResolutionInput(
                tokenClaims = mapOf("tenant_id" to JsonPrimitive("acme"), "sub" to JsonPrimitive("user-1")),
            ),
        )
        assertEquals("acme", result.tenantId)
        assertEquals("user-1", result.principalId)
        assertEquals(PrincipalType.USER, result.principalType)
        assertEquals(ResolutionSource.TOKEN, result.metadata.resolvedFrom)
    }

    @Test
    fun missingJwtCannotEstablishIdentity() = runTest {
        val result = pipeline().resolve(IdentityResolutionInput())
        assertNull(result.tenantId)
        assertNull(result.principalId)
        assertEquals(PrincipalType.ANONYMOUS, result.principalType)
        assertEquals(ResolutionSource.UNKNOWN, result.metadata.resolvedFrom)
    }

    @Test
    fun jwtClaimsRemainAuthoritative() = runTest {
        val result = pipeline().resolve(
            IdentityResolutionInput(
                tokenClaims = mapOf("tenant_id" to JsonPrimitive("jwt-tenant"), "sub" to JsonPrimitive("jwt-user")),
            ),
        )
        assertEquals("jwt-tenant", result.tenantId)
        assertEquals("jwt-user", result.principalId)
    }

    @Test
    fun missingJwtIdentityClaimsRemainAnonymous() = runTest {
        val result = pipeline().resolve(
            IdentityResolutionInput(
                tokenClaims = mapOf("iss" to JsonPrimitive("https://issuer.example")),
            ),
        )
        assertNull(result.tenantId)
        assertNull(result.principalId)
        assertEquals(PrincipalType.ANONYMOUS, result.principalType)
    }

    @Test
    fun clientCredentialsJwtIsWorkload() = runTest {
        val result = pipeline().resolve(
            IdentityResolutionInput(
                tokenClaims = mapOf(
                    "tenant_id" to JsonPrimitive("acme"),
                    "sub" to JsonPrimitive("service-a"),
                    "grant_type" to JsonPrimitive("client_credentials"),
                ),
            ),
        )
        assertEquals(PrincipalType.WORKLOAD, result.principalType)
    }

    @Test
    fun issuerAndAudienceRemainJwtMetadata() = runTest {
        val result = pipeline().resolve(
            IdentityResolutionInput(
                tokenClaims = mapOf(
                    "tenant_id" to JsonPrimitive("acme"),
                    "iss" to JsonPrimitive("https://issuer.example"),
                    "aud" to JsonPrimitive("api"),
                ),
            ),
        )
        assertEquals("https://issuer.example", result.metadata.issuer)
        assertEquals("api", result.metadata.audience)
    }
}
