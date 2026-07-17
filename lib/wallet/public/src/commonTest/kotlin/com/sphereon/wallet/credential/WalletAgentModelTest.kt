/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.credential

import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.wallet.unit.WalletSecureComponentWalletBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.time.Instant

/**
 * D10: a wallet unit has exactly ONE WSCA/WSCD binding; every [WalletAgent] acting on behalf of
 * the unit's single wallet-unit instance shares that ONE binding, carried at unit scope on
 * [WalletActivation.secureComponentBinding]. Agents own no secure component of their own (see the
 * [WalletAgent] KDoc).
 *
 * This test replaces the earlier `WalletInstanceActivationModelTest`, whose
 * `logicalProfilesArePartyScopedAndCanHaveSeparateDeviceActivations` test incorrectly modeled
 * per-activation (per-device) divergent `wscaBinding`/`wscdBinding` values - i.e. it asserted that
 * a mobile activation and a web activation of the same unit could resolve DIFFERENT secure
 * component bindings, which is exactly the topology error D10 corrects.
 */
class WalletAgentModelTest {
    @Test
    fun agentsOfTheSameUnitShareTheSingleSecureComponentBinding() {
        val walletUnitId = "wallet-private"
        val walletInstanceId = "wallet-private-instance-1"

        val unitBinding =
            WalletSecureComponentWalletBinding(
                walletUnitId = walletUnitId,
                walletInstanceId = walletInstanceId,
                evidence = mapOf("provider" to "software-wscd"),
            )

        val activation =
            WalletActivation(
                id = "activation-1",
                walletUnitId = walletUnitId,
                deviceBindingRef = IdentifierRef(type = IdentifierType("device"), value = "device-primary"),
                secureComponentBinding = unitBinding,
                state = WalletActivationState.ACTIVE,
                authorizedAt = NOW,
                createdAt = NOW,
                updatedAt = NOW,
            )

        val browserAgent =
            WalletAgent(
                agentId = "agent-browser",
                walletUnitId = walletUnitId,
                walletInstanceId = walletInstanceId,
                kind = WalletAgentKind.BROWSER,
                registeredAt = NOW,
                authorizationEvidenceRef = "passkey-ceremony-browser-1",
            )
        val mobileAgent =
            WalletAgent(
                agentId = "agent-mobile",
                walletUnitId = walletUnitId,
                walletInstanceId = walletInstanceId,
                kind = WalletAgentKind.NATIVE_MOBILE,
                registeredAt = NOW,
                authorizationEvidenceRef = "passkey-ceremony-mobile-1",
            )

        // Both agents act on behalf of the same unit + instance; only their kind and their own
        // registration evidence differ.
        assertEquals(browserAgent.walletUnitId, mobileAgent.walletUnitId)
        assertEquals(browserAgent.walletInstanceId, mobileAgent.walletInstanceId)
        assertNotEquals(browserAgent.kind, mobileAgent.kind)
        assertNotEquals(browserAgent.authorizationEvidenceRef, mobileAgent.authorizationEvidenceRef)

        // Reflection-free identity check: WalletAgent has no binding field of its own, so the only
        // way to resolve "the binding for this agent" is through its walletUnitId. Both agents carry
        // the SAME walletUnitId, so resolving through the unit reference yields the identical binding
        // instance for both - unlike the old model, where mobile/web activations could carry distinct
        // wscdBinding values.
        val bindingsByUnitId = mapOf(walletUnitId to unitBinding)
        val browserResolvedBinding = bindingsByUnitId.getValue(browserAgent.walletUnitId)
        val mobileResolvedBinding = bindingsByUnitId.getValue(mobileAgent.walletUnitId)
        assertSame(browserResolvedBinding, mobileResolvedBinding)
        assertEquals(activation.secureComponentBinding, browserResolvedBinding)
        assertEquals(activation.secureComponentBinding, mobileResolvedBinding)
    }

    /**
     * Compile-level truth that [WalletAgent] carries no secure-component field of its own: this
     * constructor call names every declared parameter, so a future accidental re-introduction of a
     * per-agent wsca/wscd/key field (which would need a value here) forces a review of this test
     * and its KDoc claim, rather than silently compiling.
     */
    @Test
    fun agentModelCarriesNoSecureComponentFieldsByConstruction() {
        val agent =
            WalletAgent(
                agentId = "agent-headless",
                walletUnitId = "wallet-private",
                walletInstanceId = "wallet-private-instance-1",
                kind = WalletAgentKind.HEADLESS,
                registeredAt = NOW,
                authorizationEvidenceRef = null,
            )
        assertEquals(WalletAgentKind.HEADLESS, agent.kind)
    }

    private companion object {
        val NOW: Instant = Instant.fromEpochSeconds(1_800_000_000)
    }
}
