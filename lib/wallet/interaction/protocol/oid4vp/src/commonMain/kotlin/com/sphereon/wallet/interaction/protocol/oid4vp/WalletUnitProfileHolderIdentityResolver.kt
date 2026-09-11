/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.wallet.WalletHolderIdentityResolver
import com.sphereon.wallet.WalletHolderVerificationMethodResolver
import com.sphereon.wallet.credential.KeyRef
import com.sphereon.wallet.credential.WalletUnitStore

/**
 * Resolves the explicit holder-party identity configured on a wallet unit.
 *
 * The selected key is passed through this boundary so a product can replace this implementation
 * with a key-specific authority. This default implementation intentionally never interprets the
 * key alias as a DID or other holder identifier; [WalletUnitStore]'s holder-party association is
 * the only source of identity.
 */
class WalletUnitProfileHolderIdentityResolver(
    private val walletUnitStore: WalletUnitStore,
) : WalletHolderIdentityResolver {
    override suspend fun resolve(
        walletUnitId: String,
        holderKeyRef: KeyRef,
    ): String? {
        val profile =
            walletUnitStore
                .getWalletUnit(walletUnitId)
                .getOrElse { error("Failed to resolve wallet holder identity: ${it.code}") }
                ?: return null
        return profile.holderVerificationMethods[holderKeyRef.alias]?.controller
    }

    /** Resolve only the exact profile association; never derive a method from alias or key id. */
    suspend fun resolveVerificationMethod(
        walletUnitId: String,
        holderKeyRef: KeyRef,
    ): com.sphereon.wallet.credential.WalletHolderVerificationMethod? {
        val profile =
            walletUnitStore
                .getWalletUnit(walletUnitId)
                .getOrElse { error("Failed to resolve wallet holder verification method: ${it.code}") }
                ?: return null
        return profile.holderVerificationMethods[holderKeyRef.alias]
    }
}

/** Explicitly exposes the profile-backed verification-method association to protocol stores. */
class WalletUnitProfileHolderVerificationMethodResolver(
    private val delegate: WalletUnitProfileHolderIdentityResolver,
) : WalletHolderVerificationMethodResolver {
    override suspend fun resolve(
        walletUnitId: String,
        holderKeyRef: KeyRef,
    ): com.sphereon.wallet.credential.WalletHolderVerificationMethod? =
        delegate.resolveVerificationMethod(walletUnitId, holderKeyRef)
}
