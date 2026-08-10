/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.*
import com.sphereon.wallet.interaction.testfixture.WalletInteractionEngineTestFactory

/** Explicit test composition with a private, atomic one-use sensitive-input authority. */
@Suppress("FunctionName")
internal fun testWalletInteractionEngine(
    adapters: List<WalletInteractionProtocolAdapter> = emptyList(),
    sessionIdGenerator: WalletInteractionSessionIdGenerator = RandomWalletInteractionSessionIdGenerator(),
    protocolExecutor: WalletProtocolExecutor = WalletProtocolExecutor.walletApp,
    trustResolver: WalletCounterpartyTrustResolver = WalletCounterpartyTrustResolver.unresolved,
    trustPolicy: WalletTrustPolicy = WalletTrustPolicy.warn,
    securityGate: WalletSecurityGate = WalletSecurityGate.allow,
    privateSessionStore: WalletInteractionPrivateSessionStore = InMemoryWalletInteractionPrivateSessionStore(),
    sessionStore: WalletInteractionSessionStore = InMemoryWalletInteractionSessionStore(),
    sensitiveInputAuthority: WalletInteractionSensitiveInputAuthority =
        StoreBackedWalletInteractionSensitiveInputAuthority(InMemoryWalletInteractionPrivateSessionStore()),
): DefaultWalletInteractionEngine {
    return WalletInteractionEngineTestFactory.create(
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
