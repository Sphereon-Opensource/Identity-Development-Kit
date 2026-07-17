/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.provider.local

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Bootstrap-facing configuration for [LocalWalletProvider]: the OSS wallet-provider identity
 * (CIR (EU) 2024/2981 Art. 2: "a natural or legal person who provides wallet solutions") it
 * self-signs every WIA/KA under, and the signature algorithm its provider key uses. Mirrors the
 * plain, KMS-free shape of [com.sphereon.wallet.wscd.WscdConfig]: product/runner bootstraps hold
 * this directly; [LocalWalletProvider] never touches the KMS, only [com.sphereon.wallet.wsca.Wsca].
 */
interface LocalWalletProviderConfig {
    /** Stable issuer identity (`iss` claim, and the [com.sphereon.wallet.wsca.Wsca.ensureKey] key scope) for every WIA/KA this provider self-signs. */
    val providerId: String

    /** TS03 `wallet_name` fallback when a request's `walletSolution.name` is blank. */
    val walletName: String

    /** TS03 `wallet_version` fallback when a request's `walletSolution.version` is blank. */
    val walletVersion: String

    /**
     * Signature algorithm the provider's own Wsca-held signing key uses. Only the TS03-allowed
     * ECDSA variants ([SignatureAlgorithm.ECDSA_SHA256]/384/512) are meaningful here; any other
     * [SignatureAlgorithm] value falls back to ES256 in [LocalWalletProvider]'s private
     * `toWalletAttestationSigningAlgorithm` mapper.
     */
    val providerKeyAlgorithm: SignatureAlgorithm

    companion object {
        const val DEFAULT_PROVIDER_ID: String = "local-wallet-provider"
        const val DEFAULT_WALLET_NAME: String = "Sphereon OSS Wallet"
        const val DEFAULT_WALLET_VERSION: String = "0.1.0"
    }
}

/**
 * Built-in defaults. An EDK/VDX assembly that wants a distinct self-managed provider identity
 * contributes a property-driven [LocalWalletProviderConfig] binding that replaces this one - the
 * same pattern `DefaultFederationFlowConfig`
 * (`lib-oauth2-server-authorization-impl.provider.DefaultFederationFlowConfig`) already
 * establishes for "built-in defaults, replaceable by a property-driven binding".
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<LocalWalletProviderConfig>())
class DefaultLocalWalletProviderConfig : LocalWalletProviderConfig {
    override val providerId: String = LocalWalletProviderConfig.DEFAULT_PROVIDER_ID
    override val walletName: String = LocalWalletProviderConfig.DEFAULT_WALLET_NAME
    override val walletVersion: String = LocalWalletProviderConfig.DEFAULT_WALLET_VERSION
    override val providerKeyAlgorithm: SignatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
}

/**
 * Session-graph accessor for the LOCAL [com.sphereon.wallet.provider.WalletProviderBinding.providerId]:
 * `LocalProfileProvisioner` (`wallet-profile-impl`, AppScope) cannot inject [LocalWalletProviderConfig]
 * by plain constructor injection (SessionScope dependency, see [com.sphereon.wallet.provider.WalletProviderGraph]'s
 * KDoc for the scope-bridge rationale), so it casts `SessionInstance.graph` to this interface instead.
 * [walletProviderId] is a plain Kotlin default-getter property (not a separate Metro binding): Metro
 * only needs to resolve the already-bound [localWalletProviderConfig], the getter merely projects its
 * `providerId` - this avoids introducing a second, ambiguity-prone raw-`String` binding into the graph.
 */
@ContributesTo(SessionScope::class)
interface LocalWalletProviderIdentityGraph {
    val localWalletProviderConfig: LocalWalletProviderConfig

    val walletProviderId: String get() = localWalletProviderConfig.providerId
}
