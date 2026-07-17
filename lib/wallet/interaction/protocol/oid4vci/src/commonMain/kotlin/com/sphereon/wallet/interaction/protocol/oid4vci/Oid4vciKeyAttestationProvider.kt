/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.common.model.KeyAttestationsRequired

/**
 * Everything [Oid4vciKeyAttestationProvider] needs to obtain a compact key-attestation JWT for one
 * credential-request proof (OID4VCI 1.0 KA-on-demand: proof_types_supported.jwt.
 * key_attestations_required, carried on the proof JWT's `key_attestation` header per TS 119 472-3).
 *
 * [signingKeyId] identifies the SAME key [Oid4vciHolderIssuanceExecutor] signs the credential-
 * request proof with - the issuer-side verifier requires the proof's holder-binding key to be
 * present in the attestation's `attested_keys` (RFC 7638 thumbprint match), so a provider MUST
 * attest exactly this key, not a different one.
 */
data class Oid4vciKeyAttestationRequest(
    val walletUnitId: String,
    val operationBinding: String,
    val signingKeyId: String,
    val signingAlgorithm: String,
    val audience: String,
    val nonce: String,
    val requirement: KeyAttestationsRequired? = null,
) {
    init {
        require(operationBinding.isNotBlank()) { "oid4vci_key_attestation_operation_binding_blank" }
    }
}

/**
 * Obtains a compact key-attestation JWT for [Oid4vciHolderIssuanceExecutor]'s KA-on-demand proof
 * path. Kept as an injectable seam - mirroring [Oid4vciRefreshTokenGrantProvider] - rather than a
 * hard dependency on any specific wallet-unit/WSCA type: the actual attestation mechanics (WSCA
 * key resolution, `Wsca.attestKeys`/`WalletProvider.issueKeyAttestation`) belong at the composition-
 * root layer that already assembles the wallet unit, not duplicated inside the wallet-interaction
 * module.
 */
fun interface Oid4vciKeyAttestationProvider {
    suspend fun attest(request: Oid4vciKeyAttestationRequest): IdkResult<String, IdkError>

    companion object {
        /** Safe default: key attestation fails with a typed, non-throwing error when no provider is wired. */
        val unsupported: Oid4vciKeyAttestationProvider =
            Oid4vciKeyAttestationProvider { request ->
                Err(
                    IdkError.fromString(
                        code = "oid4vci.key_attestation_provider_unsupported",
                        message =
                            "Credential configuration requires a key attestation for wallet unit " +
                                "'${request.walletUnitId}' but no Oid4vciKeyAttestationProvider is configured " +
                                "for this wallet deployment",
                    ),
                )
            }
    }
}
