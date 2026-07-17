/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.interaction.WalletInteractionExecutionMode
import com.sphereon.wallet.interaction.WalletProtocolExecutionDecision
import com.sphereon.wallet.interaction.WalletProtocolExecutionRequest
import com.sphereon.wallet.interaction.WalletProtocolExecutor
import com.sphereon.wallet.interaction.WalletSecurityAssurance
import com.sphereon.wallet.interaction.WalletSecurityOperation
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wscd.WscdProfile
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Resolves the [WscdProfile] backing a wallet-protocol-execution request, if known.
 *
 * A `null` result means the profile is unknown for this request; [WscdAwareExecutionPlanner]
 * then leaves the delegate's decision untouched (no security overlay applied).
 */
fun interface WscdExecutionProfileSource {
    suspend fun profileFor(request: WalletProtocolExecutionRequest): WscdProfile?
}

/**
 * Overlays a [WalletProtocolExecutor]'s decision with the user-authorization path implied by the
 * WSCD custody profile actually backing the wallet unit's secure component.
 *
 * Replaces the earlier keyRef string-prefix heuristic (the former
 * `SecureComponentAwareWalletProtocolExecutor` / `RequestHintWalletSecureComponentCapabilityResolver`)
 * with a typed [WscdProfile] resolved by [profileSource]. Decision table:
 * - [WscdProfile.Remote]: provider-side HSM reached over a network -> the caller must obtain remote
 *   key authorization ([WalletSecurityOperation.REMOTE_KEY_AUTHORIZATION]) with
 *   [WalletSecurityAssurance.REMOTE_AUTHORIZED].
 * - [WscdProfile.LocalNative], [WscdProfile.LocalInternal], [WscdProfile.LocalExternal]: hardware-backed
 *   custody local to the device -> a local HSM/secure-area unlock
 *   ([WalletSecurityOperation.LOCAL_HSM_UNLOCK]) with [WalletSecurityAssurance.HARDWARE_BACKED].
 * - [WscdProfile.Software]: development / OSS web-fallback custody with no hardware or remote backing ->
 *   no security-gate overlay; the delegate's own decision stands unchanged.
 * - Unknown profile ([profileSource] returns `null`): same as [WscdProfile.Software], the delegate's
 *   decision stands unchanged.
 */
class WscdAwareExecutionPlanner(
    private val profileSource: WscdExecutionProfileSource,
    private val delegate: WalletProtocolExecutor = WalletProtocolExecutor.split,
) : WalletProtocolExecutor {
    override val executionMode: WalletInteractionExecutionMode
        get() = delegate.executionMode

    override fun withExecutionMode(mode: WalletInteractionExecutionMode): WalletProtocolExecutor =
        WscdAwareExecutionPlanner(profileSource, delegate.withExecutionMode(mode))

    override suspend fun plan(request: WalletProtocolExecutionRequest): WalletProtocolExecutionDecision {
        val decision = delegate.plan(request)
        val profile = profileSource.profileFor(request) ?: return decision
        val overlay = profile.securityOverlay() ?: return decision
        return decision.copy(
            securityGateRequired = true,
            securityOperation = overlay.operation,
            requiredAssurance = overlay.assurance,
            keyRef = decision.keyRef ?: request.keyRef,
            walletUnitId = decision.walletUnitId ?: request.walletUnitId,
            walletAccountId = decision.walletAccountId ?: request.walletAccountId,
            activationDecisionId = decision.activationDecisionId ?: request.activationDecisionId,
            operationType = decision.operationType ?: request.operationType,
            operationHash = decision.operationHash ?: request.operationHash,
            nonce = decision.nonce ?: request.nonce,
        )
    }

    private data class SecurityOverlay(
        val operation: WalletSecurityOperation,
        val assurance: WalletSecurityAssurance,
    )

    private fun WscdProfile.securityOverlay(): SecurityOverlay? =
        when (this) {
            WscdProfile.Remote ->
                SecurityOverlay(
                    operation = WalletSecurityOperation.REMOTE_KEY_AUTHORIZATION,
                    assurance = WalletSecurityAssurance.REMOTE_AUTHORIZED,
                )

            WscdProfile.LocalNative, WscdProfile.LocalInternal, WscdProfile.LocalExternal ->
                SecurityOverlay(
                    operation = WalletSecurityOperation.LOCAL_HSM_UNLOCK,
                    assurance = WalletSecurityAssurance.HARDWARE_BACKED,
                )

            WscdProfile.Software -> null
        }
}

/**
 * Default [WscdExecutionProfileSource] sourced from the wallet unit's own [Wsca]. The single-WSCA-per-unit
 * model means the profile does not vary per request, so every request resolves to the
 * same [Wsca.wscdProfile].
 *
 * Not auto-contributed to the DI graph: the executor this replaces was never wired into production DI
 * via an automatic binding either. Callers construct this explicitly alongside [WscdAwareExecutionPlanner]
 * (as tests do today), or wire their own [WscdExecutionProfileSource].
 */
@Inject
@SingleIn(SessionScope::class)
class WscaBackedWscdExecutionProfileSource(
    private val wsca: Wsca,
) : WscdExecutionProfileSource {
    override suspend fun profileFor(request: WalletProtocolExecutionRequest): WscdProfile = wsca.wscdProfile
}
