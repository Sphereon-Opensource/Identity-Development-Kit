/* Copyright 2026 Sphereon International B.V. Licensed under Apache-2.0. */
package com.sphereon.oauth2.server.authorization.impl.command.federation

import com.sphereon.oauth2.server.authorization.routing.AuthenticationRoute
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteBinding
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteDecision
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InitiateProviderAuthenticationRouteSelectionTest {
    @Test
    fun refreshedMetadataMustKeepTheValidatedAuthorizationEndpoint() {
        val mismatch = federationMetadataPinMismatch(
            providerConfig = providerConfig(),
            metadata = metadata(authorizationEndpoint = "https://changed.example/authorize"),
        )

        assertEquals("Resolved upstream authorization endpoint does not match the validated federation binding", mismatch)
    }

    @Test
    fun refreshedMetadataMayCarryCapabilitiesNotStoredInThePinnedSnapshot() {
        val mismatch = federationMetadataPinMismatch(
            providerConfig = providerConfig(),
            metadata = metadata(),
        )

        assertEquals(null, mismatch)
    }

    @Test
    fun chooserAcceptsTheExactEligibleBindingSelectedByTheUser() {
        val selected = pinFederationRouteBinding(chooser(), BINDING_B)

        assertTrue(selected.isOk)
        assertEquals(AuthenticationRoute.UPSTREAM_REDIRECT, selected.value.route)
        assertEquals(BINDING_B, selected.value.selectedBindingId)
        assertEquals(listOf(BINDING_A, BINDING_B), selected.value.eligibleBindings.map(AuthenticationRouteBinding::bindingId))
    }

    @Test
    fun preselectedRouteRefusesAContradictingBinding() {
        val selected = pinFederationRouteBinding(preselected(BINDING_A), BINDING_B)

        assertTrue(selected.isErr)
        assertTrue(selected.error.description.contains("does not match"))
    }

    @Test
    fun chooserRefusesABindingThatWasNotFrozenIntoTheTransaction() {
        val selected = pinFederationRouteBinding(chooser(), UNKNOWN_BINDING)

        assertTrue(selected.isErr)
        assertTrue(selected.error.description.contains("not eligible"))
    }

    private fun chooser() =
        AuthenticationRouteDecision(
            route = AuthenticationRoute.CHOOSER,
            hostedAuthorizationServerId = HOSTED_ID,
            hostedAuthorizationServerRevision = 7,
            localLoginAllowed = true,
            eligibleBindings = listOf(binding(BINDING_A), binding(BINDING_B)),
        )

    private fun preselected(bindingId: String) =
        AuthenticationRouteDecision(
            route = AuthenticationRoute.UPSTREAM_REDIRECT,
            hostedAuthorizationServerId = HOSTED_ID,
            hostedAuthorizationServerRevision = 7,
            localLoginAllowed = false,
            eligibleBindings = listOf(binding(BINDING_A), binding(BINDING_B)),
            selectedBindingId = bindingId,
        )

    private fun binding(id: String) =
        AuthenticationRouteBinding(
            bindingId = id,
            upstreamResourceId = UPSTREAM_ID,
            displayName = "Fixture",
            upstreamIssuer = "https://idp.example.com",
            bindingRevision = 3,
            upstreamResourceRevision = 5,
            claimsMapping = emptyMap(),
        )

    private fun providerConfig() = FederationProviderConfig(
        id = BINDING_A,
        name = "Fixture",
        issuerUrl = "https://idp.example.com",
        clientId = "fixture-client",
        authorizationEndpointOverride = "https://idp.example.com/authorize",
        tokenEndpointOverride = "https://idp.example.com/token",
        discoveryEnabled = true,
    )

    private fun metadata(authorizationEndpoint: String = "https://idp.example.com/authorize") = AuthorizationServerMetadata(
        issuer = "https://idp.example.com",
        authorizationEndpoint = authorizationEndpoint,
        tokenEndpoint = "https://idp.example.com/token",
        codeChallengeMethodsSupported = listOf("S256"),
    )

    private companion object {
        const val HOSTED_ID = "10000000-0000-4000-8000-000000000001"
        const val UPSTREAM_ID = "20000000-0000-4000-8000-000000000001"
        const val BINDING_A = "30000000-0000-4000-8000-000000000001"
        const val BINDING_B = "30000000-0000-4000-8000-000000000002"
        const val UNKNOWN_BINDING = "30000000-0000-4000-8000-000000000099"
    }
}
