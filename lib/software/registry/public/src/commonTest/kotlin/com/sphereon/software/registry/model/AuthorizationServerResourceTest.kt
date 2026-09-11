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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class AuthorizationServerResourceTest {
    @Test
    fun discoveryRejectsNonHttpsUserinfoEndpoint() {
        val snapshot = requireNotNull(externalResource().discovery)

        val error = assertFailsWith<IllegalArgumentException> {
            snapshot.copy(userinfoEndpoint = "http://login.example.test/userinfo")
        }

        assertEquals("Discovered UserInfo endpoint must be an HTTPS URL", error.message)
    }

    @Test
    fun discoveryMayExpireAtValidationTimeWhenHttpSourceRequiresImmediateRevalidation() {
        val snapshot = requireNotNull(externalResource().discovery)

        val immediatelyExpired = snapshot.copy(validUntil = snapshot.validatedAt)

        assertEquals(snapshot.validatedAt, immediatelyExpired.validUntil)
    }


    @Test
    fun externalSelectionsMustBeSubsetsOfValidatedDiscovery() {
        val error = assertFailsWith<IllegalArgumentException> {
            externalResource(
                usages = setOf(AuthorizationServerUsage.HOSTED_LOGIN_UPSTREAM),
                discoveredCapabilities = setOf(AuthorizationServerCapability.OAUTH2),
            )
        }

        assertEquals(
            "Hosted login usage requires a validated OIDC capability",
            error.message,
        )
    }

    @Test
    fun externalGrantAllowlistMustBeDiscovered() {
        val error = assertFailsWith<IllegalArgumentException> {
            externalResource(
                allowedGrantTypes = setOf(AuthorizationServerGrantType.PRE_AUTHORIZED_CODE),
                discoveredGrantTypes = setOf(AuthorizationServerGrantType.AUTHORIZATION_CODE),
            )
        }

        assertEquals(
            "Allowed grant types must be a subset of the last validated discovery snapshot",
            error.message,
        )
    }

    @Test
    fun expectedCapabilitiesRemainDistinctFromDiscoveredEffectiveCapabilities() {
        val resource = externalResource(
            usages = setOf(AuthorizationServerUsage.OID4VCI_AUTHORIZATION_SERVER),
            discoveredCapabilities = setOf(AuthorizationServerCapability.OAUTH2),
        )

        assertEquals(setOf(AuthorizationServerCapability.OAUTH2), resource.capabilities)
        assertEquals(
            setOf(AuthorizationServerCapability.OAUTH2, AuthorizationServerCapability.OIDC),
            resource.expectedCapabilities,
        )
    }

    @Test
    fun hostedAuthenticationConfigurationIsRequiredOnlyForHostedResources() {
        assertFailsWith<IllegalArgumentException> {
            hostedResource(authenticationMode = null)
        }
        assertFailsWith<IllegalArgumentException> {
            externalResource(authenticationMode = HostedAuthenticationMode.HYBRID)
        }
    }

    @Test
    fun migratedExternalResourceMayRemainSuspendedUntilFirstValidation() {
        val migrated = AuthorizationServerResource(
            id = EXTERNAL_ID,
            tenantId = "tenant-a",
            slug = "migrated-upstream",
            displayName = "Migrated upstream",
            issuer = "https://login.example.test/realm/",
            lifecycle = AuthorizationServerLifecycle.SUSPENDED,
            deployment = AuthorizationServerDeployment.EXTERNAL,
            purposes = setOf(AuthorizationServerPurpose.GENERAL),
            usages = emptySet(),
            allowedGrantTypes = emptySet(),
            capabilities = emptySet(),
            expectedCapabilities = setOf(AuthorizationServerCapability.OAUTH2, AuthorizationServerCapability.OIDC),
            system = false,
            defaultForPurposes = emptySet(),
            discovery = null,
            revision = 0,
            createdAt = NOW,
            updatedAt = NOW,
        )

        assertEquals(null, migrated.discovery)
        assertFailsWith<IllegalArgumentException> {
            migrated.copy(lifecycle = AuthorizationServerLifecycle.ACTIVE)
        }
        assertFailsWith<IllegalArgumentException> {
            migrated.copy(usages = setOf(AuthorizationServerUsage.HOSTED_LOGIN_UPSTREAM))
        }
    }

    @Test
    fun platformTenantAndNonUuidResourceIdentityAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            hostedResource(tenantId = "platform")
        }
        assertFailsWith<IllegalArgumentException> {
            hostedResource(id = "tenant-as")
        }
    }

    @Test
    fun walletFacingBrokerIsCompositionNotADeploymentKind() {
        val resource = hostedResource(
            authenticationMode = HostedAuthenticationMode.FEDERATED_ONLY,
            purposes = setOf(AuthorizationServerPurpose.CREDENTIAL_ISSUANCE),
        )

        assertEquals(AuthorizationServerDeployment.HOSTED, resource.deployment)
        assertEquals(setOf(AuthorizationServerPurpose.CREDENTIAL_ISSUANCE), resource.purposes)
        assertEquals(HostedAuthenticationMode.FEDERATED_ONLY, resource.authenticationMode)
    }

    @Test
    fun decommissionedLifecycleIsTerminalAndSuspendedCanReactivate() {
        val suspended = hostedResource().transitionTo(AuthorizationServerLifecycle.SUSPENDED, LATER)
        assertEquals(AuthorizationServerLifecycle.SUSPENDED, suspended.lifecycle)
        assertEquals(1, suspended.revision)

        val activeAgain = suspended.transitionTo(AuthorizationServerLifecycle.ACTIVE, LATER)
        assertEquals(AuthorizationServerLifecycle.ACTIVE, activeAgain.lifecycle)
        assertEquals(2, activeAgain.revision)

        val terminal = activeAgain.transitionTo(AuthorizationServerLifecycle.DECOMMISSIONED, LATER)
        assertFailsWith<IllegalStateException> {
            terminal.transitionTo(AuthorizationServerLifecycle.ACTIVE, LATER)
        }
    }

    private fun hostedResource(
        id: String = HOSTED_ID,
        tenantId: String = "tenant-a",
        authenticationMode: HostedAuthenticationMode? = HostedAuthenticationMode.LOCAL_ONLY,
        purposes: Set<AuthorizationServerPurpose> = setOf(AuthorizationServerPurpose.GENERAL),
    ) = AuthorizationServerResource(
        id = id,
        tenantId = tenantId,
        slug = "hosted",
        displayName = "Hosted AS",
        issuer = "https://tenant-a.example.test",
        lifecycle = AuthorizationServerLifecycle.ACTIVE,
        deployment = AuthorizationServerDeployment.HOSTED,
        authenticationMode = authenticationMode,
        purposes = purposes,
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

    private fun externalResource(
        usages: Set<AuthorizationServerUsage> = setOf(AuthorizationServerUsage.OID4VCI_AUTHORIZATION_SERVER),
        allowedGrantTypes: Set<AuthorizationServerGrantType> = setOf(AuthorizationServerGrantType.AUTHORIZATION_CODE),
        discoveredCapabilities: Set<AuthorizationServerCapability> = setOf(
            AuthorizationServerCapability.OAUTH2,
            AuthorizationServerCapability.OIDC,
        ),
        discoveredGrantTypes: Set<AuthorizationServerGrantType> = setOf(AuthorizationServerGrantType.AUTHORIZATION_CODE),
        authenticationMode: HostedAuthenticationMode? = null,
    ) = AuthorizationServerResource(
        id = EXTERNAL_ID,
        tenantId = "tenant-a",
        slug = "external",
        displayName = "External AS",
        issuer = "https://login.example.test",
        lifecycle = AuthorizationServerLifecycle.ACTIVE,
        deployment = AuthorizationServerDeployment.EXTERNAL,
        authenticationMode = authenticationMode,
        purposes = setOf(AuthorizationServerPurpose.CREDENTIAL_ISSUANCE),
        usages = usages,
        allowedGrantTypes = allowedGrantTypes,
        capabilities = discoveredCapabilities,
        expectedCapabilities = setOf(AuthorizationServerCapability.OAUTH2, AuthorizationServerCapability.OIDC),
        discovery = AuthorizationServerDiscoverySnapshot(
            capabilities = discoveredCapabilities,
            grantTypes = discoveredGrantTypes,
            tokenEndpointAuthMethodsSupported = setOf(UpstreamClientAuthenticationMethod.NONE),
            issuer = "https://login.example.test",
            authorizationEndpoint = "https://login.example.test/authorize",
            tokenEndpoint = "https://login.example.test/token",
            scopesSupported = emptySet(),
            idTokenSigningAlgorithms = emptySet(),
            sourceUrls = listOf("https://login.example.test/.well-known/oauth-authorization-server"),
            digest = "a".repeat(64),
            validatedAt = Instant.parse("2026-08-23T12:00:00Z"),
            freshness = DiscoveryFreshness.CURRENT,
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
        val NOW = Instant.parse("2026-08-23T12:00:00Z")
        val LATER = Instant.parse("2026-08-23T13:00:00Z")
    }
}
