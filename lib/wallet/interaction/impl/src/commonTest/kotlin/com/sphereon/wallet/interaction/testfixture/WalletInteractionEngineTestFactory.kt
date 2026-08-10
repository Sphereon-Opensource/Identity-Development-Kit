/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.testfixture

import com.sphereon.wallet.interaction.WalletCounterpartyTrustResolver
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.WalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputAuthority
import com.sphereon.wallet.interaction.WalletProtocolExecutor
import com.sphereon.wallet.interaction.WalletSecurityGate
import com.sphereon.wallet.interaction.WalletTrustPolicy
import com.sphereon.wallet.interaction.impl.DefaultWalletInteractionEngine
import com.sphereon.wallet.interaction.impl.WalletInteractionSessionIdGenerator
import com.sphereon.wallet.interaction.impl.WalletInteractionSessionStore

object WalletInteractionEngineTestFactory {
    fun create(
        adapters: List<WalletInteractionProtocolAdapter>,
        sessionIdGenerator: WalletInteractionSessionIdGenerator,
        protocolExecutor: WalletProtocolExecutor,
        trustResolver: WalletCounterpartyTrustResolver,
        trustPolicy: WalletTrustPolicy,
        securityGate: WalletSecurityGate,
        sensitiveInputAuthority: WalletInteractionSensitiveInputAuthority,
        privateSessionStore: WalletInteractionPrivateSessionStore,
        sessionStore: WalletInteractionSessionStore,
    ): DefaultWalletInteractionEngine =
        DefaultWalletInteractionEngine(
            adapters = adapters,
            sessionIdGenerator = sessionIdGenerator,
            protocolExecutor = protocolExecutor,
            trustResolver = trustResolver,
            trustPolicy = trustPolicy,
            securityGate = securityGate,
            sensitiveInputAuthority = sensitiveInputAuthority,
            privateSessionStore = privateSessionStore,
            sessionStore = sessionStore,
        )
}
