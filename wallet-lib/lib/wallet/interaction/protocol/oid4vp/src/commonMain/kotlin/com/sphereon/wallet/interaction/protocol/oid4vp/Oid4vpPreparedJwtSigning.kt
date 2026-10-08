/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningRequest
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningResult
import com.sphereon.wallet.unit.WalletAttestedKeyRef
import kotlinx.serialization.Serializable

/** Durable facts only. WSCA's instance-local prepared authority is never serialized. */
@Serializable
data class Oid4vpPreparedJwtSigning(
    val request: HolderJwtVpSigningRequest,
    val key: WalletAttestedKeyRef,
    val protectedSegment: String,
    val payloadSegment: String,
    val walletUnitId: String,
    val walletAccountId: String?,
    val operationBinding: String,
    val operationType: String,
    val operationHash: String,
    val nonce: String,
    val audience: String,
    /** Unkeyed SHA-256 corruption detection, not authentication; persisted approval and HSM consumption remain authoritative. */
    val integrityBinding: String = "",
)

interface Oid4vpPreparedJwtSigningProvider {
    suspend fun prepare(request: HolderJwtVpSigningRequest): IdkResult<Oid4vpPreparedJwtSigning, IdkError>
    suspend fun finalize(prepared: Oid4vpPreparedJwtSigning): IdkResult<HolderJwtVpSigningResult, IdkError>
}
