/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.wallet.credential.KeyRef
import com.sphereon.wallet.credential.WalletHolderVerificationMethod
import com.sphereon.wallet.unit.WalletAttestedKeyRef

/**
 * Resolves the wallet identity that is allowed to act with a selected holder key.
 *
 * The key reference is deliberately an input to this boundary, but its alias/kid is never an
 * identity. Implementations must use an explicit wallet-owned association (for example a wallet
 * unit's holder-party role) and return null when that association is absent.
 */
fun interface WalletHolderIdentityResolver {
    suspend fun resolve(
        walletUnitId: String,
        holderKeyRef: KeyRef,
    ): String?

    companion object {
        val none: WalletHolderIdentityResolver = WalletHolderIdentityResolver { _, _ -> null }
    }
}

/**
 * Resolves the controlled verification method explicitly associated with a wallet holder key.
 *
 * Implementations must use server-controlled wallet-unit/profile policy. A key alias is only a
 * lookup key and MUST NOT be transformed into a KID, DID, URL, or verification method.
 */
fun interface WalletHolderVerificationMethodResolver {
    suspend fun resolve(
        walletUnitId: String,
        holderKeyRef: KeyRef,
    ): WalletHolderVerificationMethod?

    companion object {
        val none: WalletHolderVerificationMethodResolver = WalletHolderVerificationMethodResolver { _, _ -> null }
    }
}

/**
 * Server-controlled policy assigning a verifier-resolvable DID, JWKS, managed/KMS, or X.509
 * identifier to a fresh opaque WSCA key. Implementations must never derive policy from alias text.
 */
fun interface WalletHolderVerificationMethodProvisioner {
    suspend fun provision(
        walletUnitId: String,
        keyAlias: String,
        attestedKey: WalletAttestedKeyRef,
        signingAlgorithm: SignatureAlgorithm,
    ): WalletHolderVerificationMethod
}

/** Durable exact-alias registration boundary used after public identifier provisioning. */
fun interface WalletHolderVerificationMethodRegistrar {
    suspend fun register(
        walletUnitId: String,
        keyAlias: String,
        method: WalletHolderVerificationMethod,
    ): IdkResult<Unit, IdkError>
}
