/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.sphereon.wallet.interaction.holder.wiring

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.holder.HolderCredentialProofSigner
import com.sphereon.openid.oid4vci.holder.HolderPreparedSigning
import com.sphereon.openid.oid4vci.holder.HolderSigningRequest
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.unit.WalletAttestedKeyRef
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wsca.WscaSigningRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Wallet-side adapter: OID4VCI holder proof signing is mediated exclusively by WSCA.
 * Keeps protocols free of a direct `lib-wallet-wsca-*` edge (Aug 13 port inversion).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HolderCredentialProofSigner>())
class WscaHolderCredentialProofSigner(
    private val wsca: Wsca,
) : HolderCredentialProofSigner {
    override suspend fun ensureKey(
        walletUnitId: String,
        usage: SecureComponentUsage,
        algorithm: SignatureAlgorithm,
        keyAlias: String?,
    ): IdkResult<WalletAttestedKeyRef, IdkError> =
        wsca.ensureKey(
            walletUnitId = walletUnitId,
            usage = usage,
            algorithm = algorithm,
            keyAlias = keyAlias,
        )

    override suspend fun prepareSign(request: HolderSigningRequest): IdkResult<HolderPreparedSigning, IdkError> {
        val prepared = wsca.prepareSign(request.toWsca()).getOrElse { return Err(it) }
        return Ok(
            HolderPreparedSigning(
                walletUnitId = prepared.walletUnitId,
                keyRef = prepared.keyRef,
                walletAccountId = prepared.walletAccountId,
                operationBinding = prepared.operationBinding,
                operationType = prepared.operationType,
                digestBinding = prepared.digestBinding,
                nonce = prepared.nonce,
                audience = prepared.audience,
                signingInput = prepared.signingInput,
            ),
        )
    }

    override suspend fun sign(
        prepared: HolderPreparedSigning,
        request: HolderSigningRequest,
    ): IdkResult<ByteArray, IdkError> {
        val wscaRequest = request.toWsca()
        // Re-prepare under WSCA so sign() receives a provenance-owned context.
        val wscaPrepared = wsca.prepareSign(wscaRequest).getOrElse { return Err(it) }
        if (wscaPrepared.walletUnitId != prepared.walletUnitId ||
            wscaPrepared.keyRef != prepared.keyRef ||
            wscaPrepared.walletAccountId != prepared.walletAccountId ||
            wscaPrepared.operationBinding != prepared.operationBinding ||
            wscaPrepared.operationType != prepared.operationType ||
            wscaPrepared.digestBinding != prepared.digestBinding ||
            wscaPrepared.nonce != prepared.nonce ||
            wscaPrepared.audience != prepared.audience ||
            !wscaPrepared.signingInput.contentEquals(prepared.signingInput)
        ) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "WSCA prepared context does not match holder signing snapshot"))
        }
        return wsca.sign(wscaPrepared, wscaRequest)
    }

    private fun HolderSigningRequest.toWsca(): WscaSigningRequest =
        WscaSigningRequest(
            walletUnitId = walletUnitId,
            keyRef = keyRef,
            signingInput = signingInput,
            operationBinding = operationBinding,
            walletAccountId = walletAccountId,
            audience = audience,
            nonce = nonce,
        )
}
