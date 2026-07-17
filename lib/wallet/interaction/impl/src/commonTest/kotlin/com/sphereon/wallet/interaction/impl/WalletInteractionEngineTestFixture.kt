/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.*
import com.sphereon.wallet.interaction.impl.DefaultWalletInteractionEngine as CoreDefaultWalletInteractionEngine

/** Explicit test composition with a private, atomic one-use sensitive-input authority. */
@Suppress("FunctionName")
internal fun DefaultWalletInteractionEngine(
    adapters: List<WalletInteractionProtocolAdapter> = emptyList(),
    sessionIdGenerator: WalletInteractionSessionIdGenerator = RandomWalletInteractionSessionIdGenerator(),
    protocolExecutor: WalletProtocolExecutor = WalletProtocolExecutor.local,
    trustResolver: WalletCounterpartyTrustResolver = WalletCounterpartyTrustResolver.unresolved,
    trustPolicy: WalletTrustPolicy = WalletTrustPolicy.warn,
    securityGate: WalletSecurityGate = WalletSecurityGate.allow,
    privateSessionStore: WalletInteractionPrivateSessionStore = InMemoryWalletInteractionPrivateSessionStore(),
    sessionStore: WalletInteractionSessionStore = InMemoryWalletInteractionSessionStore(),
): CoreDefaultWalletInteractionEngine {
    val sensitiveStore = InMemoryWalletInteractionPrivateSessionStore()
    return CoreDefaultWalletInteractionEngine(
        adapters = adapters,
        sessionIdGenerator = sessionIdGenerator,
        protocolExecutor = protocolExecutor,
        trustResolver = trustResolver,
        trustPolicy = trustPolicy,
        securityGate = securityGate,
        sensitiveInputAuthority = StoreBackedWalletInteractionSensitiveInputAuthority(sensitiveStore),
        privateSessionStore = privateSessionStore,
        sessionStore = sessionStore,
    )
}
