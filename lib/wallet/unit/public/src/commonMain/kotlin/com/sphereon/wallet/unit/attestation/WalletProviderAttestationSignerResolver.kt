/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.unit.attestation

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.unit.WalletProviderAttestationSignerRef
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** Resolves the signer that owns the Wallet Provider attestation key referenced by [signer]. */
interface WalletProviderAttestationSignerResolver {
    suspend fun resolve(signer: WalletProviderAttestationSignerRef): IdkResult<WalletAttestationSigner, IdkError>
}

/** Standalone/local default. Production deployments replace this binding with their configured signer resolver. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletProviderAttestationSignerResolver>())
class LocalWalletProviderAttestationSignerResolver : WalletProviderAttestationSignerResolver {
    override suspend fun resolve(signer: WalletProviderAttestationSignerRef): IdkResult<WalletAttestationSigner, IdkError> =
        when (val profile = parseSignerProfile(signer).getOrElse { return Err(it) }) {
            WalletAttestationSignerProfile.LOCAL_EVALUATION ->
                Ok(LocalEvaluationWalletAttestationSigner(signerId = signer.signerId))

            WalletAttestationSignerProfile.REMOTE_WSCD,
            WalletAttestationSignerProfile.EXTERNAL_PROVIDER,
            WalletAttestationSignerProfile.LOCAL_WSCD,
            ->
                Err(
                    IdkError.UNSUPPORTED_OPERATION_ERROR(
                        operation = "wallet-provider-attestation-signer-resolution",
                        reason = "No Wallet Provider attestation signer implementation is configured for ${profile.name}",
                    ),
                )
        }
}

fun parseWalletAttestationSigningAlgorithm(signer: WalletProviderAttestationSignerRef): IdkResult<WalletAttestationSigningAlgorithm, IdkError> =
    runCatching { WalletAttestationSigningAlgorithm.valueOf(signer.signingAlgorithm) }
        .fold(
            onSuccess = { Ok(it) },
            onFailure = { Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported TS03 signing algorithm '${signer.signingAlgorithm}'")) },
        )

fun parseSignerProfile(signer: WalletProviderAttestationSignerRef): IdkResult<WalletAttestationSignerProfile, IdkError> =
    runCatching { WalletAttestationSignerProfile.valueOf(signer.signerProfile) }
        .fold(
            onSuccess = { Ok(it) },
            onFailure = { Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported TS03 signer profile '${signer.signerProfile}'")) },
        )
