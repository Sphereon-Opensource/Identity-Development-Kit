/*
 * Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.ProtocolExecutionOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkSourcePrivacyFixtureTest {
    @Test
    fun backendExchangeFromApprovedEgressIsRecordedWithoutDeviceMetadata() {
        val fixture = NetworkSourcePrivacyFixture()

        val result =
            fixture.recordIssuerOrRpExchange(
                owner = ProtocolExecutionOwner.WALLET_BACKEND,
                peerAddress = NetworkSourcePrivacyFixture.DEFAULT_BACKEND_EGRESS,
                headers = mapOf("accept" to "application/json"),
            )

        assertTrue(result.isOk)
        assertEquals(listOf(result.value), fixture.observations)
        assertEquals(NetworkSourcePrivacyFixture.DEFAULT_BACKEND_EGRESS, result.value.peerAddress)
        assertEquals(null, result.value.forwardedFor)
        assertEquals(null, result.value.userAgent)
        assertEquals(null, result.value.appRegistrationId)
    }

    @Test
    fun backendExchangeFromUnapprovedAddressFailsClosed() {
        val fixture = NetworkSourcePrivacyFixture()

        val result =
            fixture.recordIssuerOrRpExchange(
                owner = ProtocolExecutionOwner.WALLET_BACKEND,
                peerAddress = "198.51.100.20",
            )

        assertTrue(result.isErr)
        assertEquals("wallet_backend_unapproved_egress", result.error.code)
        assertTrue(fixture.observations.isEmpty())
    }

    @Test
    fun backendExchangeWithForwardedForFailsClosed() {
        val fixture = NetworkSourcePrivacyFixture()

        val result =
            fixture.recordIssuerOrRpExchange(
                owner = ProtocolExecutionOwner.WALLET_BACKEND,
                peerAddress = NetworkSourcePrivacyFixture.DEFAULT_BACKEND_EGRESS,
                headers = mapOf("X-Forwarded-For" to "203.0.113.50"),
            )

        assertTrue(result.isErr)
        assertEquals("wallet_backend_forwarded_for_forbidden", result.error.code)
        assertTrue(fixture.observations.isEmpty())
    }

    @Test
    fun backendExchangeWithMobileUserAgentFailsClosed() {
        val fixture = NetworkSourcePrivacyFixture()

        val result =
            fixture.recordIssuerOrRpExchange(
                owner = ProtocolExecutionOwner.WALLET_BACKEND,
                peerAddress = NetworkSourcePrivacyFixture.DEFAULT_BACKEND_EGRESS,
                headers = mapOf("User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)"),
            )

        assertTrue(result.isErr)
        assertEquals("wallet_backend_mobile_user_agent_forbidden", result.error.code)
        assertTrue(fixture.observations.isEmpty())
    }

    @Test
    fun backendExchangeWithAppRegistrationHeaderFailsClosed() {
        val fixture = NetworkSourcePrivacyFixture()

        val result =
            fixture.recordIssuerOrRpExchange(
                owner = ProtocolExecutionOwner.WALLET_BACKEND,
                peerAddress = NetworkSourcePrivacyFixture.DEFAULT_BACKEND_EGRESS,
                headers = mapOf("X-App-Registration-Id" to "app-registration-a"),
            )

        assertTrue(result.isErr)
        assertEquals("wallet_backend_app_registration_header_forbidden", result.error.code)
        assertTrue(fixture.observations.isEmpty())
    }

    @Test
    fun walletAppExchangeMustRecordThatItUsedAppNetworking() {
        val fixture = NetworkSourcePrivacyFixture()

        val result =
            fixture.recordIssuerOrRpExchange(
                owner = ProtocolExecutionOwner.WALLET_APP,
                peerAddress = "192.0.2.40",
                headers = mapOf("User-Agent" to "SphereonWallet/1.0 (Android)"),
                appRegistrationId = "app-registration-mobile",
                usesAppNetworking = true,
            )

        assertTrue(result.isOk)
        assertEquals(1, fixture.observations.size)
        assertEquals(ProtocolExecutionOwner.WALLET_APP, result.value.owner)
        assertEquals("app-registration-mobile", result.value.appRegistrationId)
        assertEquals("SphereonWallet/1.0 (Android)", result.value.userAgent)
    }

    @Test
    fun walletAppExchangeWithoutRecordedAppNetworkingFailsClosed() {
        val fixture = NetworkSourcePrivacyFixture()

        val result =
            fixture.recordIssuerOrRpExchange(
                owner = ProtocolExecutionOwner.WALLET_APP,
                peerAddress = "192.0.2.40",
                usesAppNetworking = false,
            )

        assertTrue(result.isErr)
        assertEquals("wallet_app_network_source_unrecorded", result.error.code)
        assertTrue(fixture.observations.isEmpty())
    }

    @Test
    fun backendPlanThatWouldLoadRpControlledContentInTheAppIsRejectedWithoutWeakening() {
        val fixture = NetworkSourcePrivacyFixture()

        val result =
            fixture.recordIssuerOrRpExchange(
                owner = ProtocolExecutionOwner.WALLET_BACKEND,
                peerAddress = NetworkSourcePrivacyFixture.DEFAULT_BACKEND_EGRESS,
                loadsRpControlledContentInApp = true,
            )

        assertTrue(result.isErr)
        assertEquals(NetworkSourcePrivacyFixture.STRICT_PRIVACY_REMOTE_CONTENT_FORBIDDEN, result.error.code)
        assertTrue(fixture.observations.isEmpty())

        val stillRejected =
            fixture.observe(
                ObservedRemoteExchange(
                    owner = ProtocolExecutionOwner.WALLET_BACKEND,
                    peerAddress = NetworkSourcePrivacyFixture.DEFAULT_BACKEND_EGRESS,
                    requestHeaderNames = emptySet(),
                    forwardedFor = null,
                    userAgent = null,
                    appRegistrationId = null,
                ),
                loadsRpControlledContentInApp = true,
            )
        assertTrue(stillRejected.isErr)
        assertEquals(NetworkSourcePrivacyFixture.STRICT_PRIVACY_REMOTE_CONTENT_FORBIDDEN, stillRejected.error.code)
        assertTrue(fixture.observations.isEmpty())
    }
}
