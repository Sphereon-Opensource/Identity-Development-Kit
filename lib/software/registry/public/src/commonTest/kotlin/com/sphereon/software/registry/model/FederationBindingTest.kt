/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.software.registry.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class FederationBindingTest {

    @Test
    fun canonicalSerializationExposesStatusWithoutTenantOwnership() {
        val serialized = Json.encodeToString(binding(enabled = false, status = FederationBindingStatus.DISABLED, lastValidatedAt = null))

        assertTrue("\"status\":\"DISABLED\"" in serialized)
        assertFalse("tenantId" in serialized)
        assertFalse("validationStatus" in serialized)
    }

    @Test
    fun bindingRequiresHostedSourceAndExternalOidcTargetInSameTenant() {
        val error = assertFailsWith<IllegalArgumentException> {
            binding(
                hostedAuthorizationServerId = EXTERNAL_ID,
                externalAuthorizationServerId = HOSTED_ID,
            ).validateAgainst(source = external(), target = hosted(), at = NOW)
        }

        assertEquals("Federation binding source must be a hosted authorization server", error.message)

        assertFailsWith<IllegalArgumentException> {
            binding().validateAgainst(source = hosted(), target = external(capabilities = setOf(AuthorizationServerCapability.OAUTH2)), at = NOW)
        }
        assertFailsWith<IllegalArgumentException> {
            binding().validateAgainst(source = hosted(), target = external(tenantId = "tenant-b"), at = NOW)
        }

    }

    @Test
    fun oidcOpenidScopeIsEffectiveWhenDiscoveryOmitsScopesSupported() {
        binding(scopes = setOf("openid")).validateAgainst(
            source = hosted(),
            target = external(scopesSupported = emptySet()),
            at = NOW,
        )
    }

    @Test
    fun secretAuthenticationStoresOnlyTypedReference() {
        assertFailsWith<IllegalArgumentException> {
            binding(
                clientAuthentication = FederationClientAuthentication(
                    method = UpstreamClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                    clientId = "tenant-client",
                ),
            )
        }

        val binding = binding(
            clientAuthentication = FederationClientAuthentication(
                method = UpstreamClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                clientId = "tenant-client",
                secretReference = TypedSecretReference(
                    resourceHandle = "secret-management/default",
                    purpose = SecretReferencePurpose.OAUTH_CLIENT_SECRET,
                ),
            ),
        )

        assertEquals(SecretReferencePurpose.OAUTH_CLIENT_SECRET, binding.clientAuthentication.secretReference?.purpose)
    }

    @Test
    fun privateKeyJwtRequiresKmsReferenceAndNoneRejectsCredentials() {
        assertFailsWith<IllegalArgumentException> {
            binding(
                clientAuthentication = FederationClientAuthentication(
                    method = UpstreamClientAuthenticationMethod.PRIVATE_KEY_JWT,
                    clientId = "tenant-client",
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            FederationClientAuthentication(
                method = UpstreamClientAuthenticationMethod.PRIVATE_KEY_JWT,
                clientId = "tenant-client",
                kmsReference = TypedKmsReference(
                    kmsResourceHandle = "kms/default",
                    kmsKeyAlias = "token-signing",
                    purpose = KmsReferencePurpose.TOKEN_SIGNING,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            binding(
                clientAuthentication = FederationClientAuthentication(
                    method = UpstreamClientAuthenticationMethod.NONE,
                    clientId = "public-client",
                    secretReference = TypedSecretReference("secret-management/default", SecretReferencePurpose.OAUTH_CLIENT_SECRET),
                ),
            )
        }
    }

    @Test
    fun enabledBindingRequiresCurrentValidationAndActiveEndpoints() {
        assertEquals("Enabled federation binding must have current VALID validation", assertFailsWith<IllegalArgumentException> {
            binding(status = FederationBindingStatus.DISABLED)
                .validateAgainst(source = hosted(), target = external(), at = NOW)
        }.message)
        assertEquals("Federation binding scopes must be a subset of discovered scopes", assertFailsWith<IllegalArgumentException> {
            binding(scopes = setOf("openid", "email"))
                .validateAgainst(source = hosted(), target = external(), at = NOW)
        }.message)
        assertEquals("Federation binding endpoints must both be active", assertFailsWith<IllegalArgumentException> {
            binding().validateAgainst(
                source = hosted(lifecycle = AuthorizationServerLifecycle.SUSPENDED),
                target = external(),
                at = NOW,
            )
        }.message)
        assertEquals("Federation target discovery must be fresh", assertFailsWith<IllegalArgumentException> {
            binding().validateAgainst(
                source = hosted(),
                target = external(freshness = DiscoveryFreshness.STALE),
                at = NOW,
            )
        }.message)
        assertEquals("Federation binding validation timestamp must match the current discovery window", assertFailsWith<IllegalArgumentException> {
            binding(lastValidatedAt = Instant.parse("2026-08-25T12:00:00Z"))
                .validateAgainst(source = hosted(), target = external(), at = NOW)
        }.message)
    }

    private fun binding(
        hostedAuthorizationServerId: String = HOSTED_ID,
        externalAuthorizationServerId: String = EXTERNAL_ID,
        enabled: Boolean = true,
        status: FederationBindingStatus = FederationBindingStatus.VALID,
        lastValidatedAt: Instant? = NOW,
        scopes: Set<String> = setOf("openid"),
        clientAuthentication: FederationClientAuthentication = FederationClientAuthentication(
            method = UpstreamClientAuthenticationMethod.NONE,
            clientId = "public-client",
        ),
    ) = FederationBinding(
        id = BINDING_ID,
        hostedAuthorizationServerId = hostedAuthorizationServerId,
        externalAuthorizationServerId = externalAuthorizationServerId,
        order = 0,
        enabled = enabled,
        scopes = scopes,
        claimsMapping = mapOf("sub" to "subject"),
        clientAuthentication = clientAuthentication,
        status = status,
        lastValidatedAt = lastValidatedAt,
        revision = 0,
    )

    private fun hosted(lifecycle: AuthorizationServerLifecycle = AuthorizationServerLifecycle.ACTIVE) = AuthorizationServerResource(
        id = HOSTED_ID,
        tenantId = "tenant-a",
        slug = "hosted",
        displayName = "Hosted",
        issuer = "https://tenant-a.example.test",
        lifecycle = lifecycle,
        deployment = AuthorizationServerDeployment.HOSTED,
        authenticationMode = HostedAuthenticationMode.HYBRID,
        purposes = setOf(AuthorizationServerPurpose.GENERAL),
        usages = emptySet(),
        allowedGrantTypes = emptySet(),
        capabilities = setOf(AuthorizationServerCapability.OAUTH2, AuthorizationServerCapability.OIDC),
        expectedCapabilities = emptySet(),
        system = false,
        defaultForPurposes = emptySet(),
        revision = 0,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun external(
        tenantId: String = "tenant-a",
        capabilities: Set<AuthorizationServerCapability> = setOf(AuthorizationServerCapability.OAUTH2, AuthorizationServerCapability.OIDC),
        lifecycle: AuthorizationServerLifecycle = AuthorizationServerLifecycle.ACTIVE,
        freshness: DiscoveryFreshness = DiscoveryFreshness.CURRENT,
        scopesSupported: Set<String> = setOf("openid"),
    ) =
        AuthorizationServerResource(
            id = EXTERNAL_ID,
            tenantId = tenantId,
            slug = "external",
            displayName = "External",
            issuer = "https://login.example.test",
            lifecycle = lifecycle,
            deployment = AuthorizationServerDeployment.EXTERNAL,
            purposes = setOf(AuthorizationServerPurpose.GENERAL),
            capabilities = capabilities,
            expectedCapabilities = setOf(AuthorizationServerCapability.OAUTH2, AuthorizationServerCapability.OIDC),
            usages = if (AuthorizationServerCapability.OIDC in capabilities) setOf(AuthorizationServerUsage.HOSTED_LOGIN_UPSTREAM) else emptySet(),
            allowedGrantTypes = emptySet(),
            discovery = AuthorizationServerDiscoverySnapshot(
                capabilities = capabilities,
                grantTypes = emptySet(),
                tokenEndpointAuthMethodsSupported = setOf(UpstreamClientAuthenticationMethod.NONE),
                scopesSupported = scopesSupported,
                idTokenSigningAlgorithms = emptySet(),
                issuer = "https://login.example.test",
                sourceUrls = listOf("https://login.example.test/.well-known/openid-configuration"),
                digest = "b".repeat(64),
                validatedAt = NOW,
                validUntil = Instant.parse("2026-08-24T12:00:00Z"),
                freshness = freshness,
            ),
            system = false,
            defaultForPurposes = emptySet(),
            revision = 0,
            createdAt = NOW,
            updatedAt = NOW,
        )

    private companion object {
        const val HOSTED_ID = "11111111-1111-4111-8111-111111111111"
        const val EXTERNAL_ID = "22222222-2222-4222-8222-222222222222"
        const val BINDING_ID = "33333333-3333-4333-8333-333333333333"
        val NOW = Instant.parse("2026-08-23T12:00:00Z")
    }
}
