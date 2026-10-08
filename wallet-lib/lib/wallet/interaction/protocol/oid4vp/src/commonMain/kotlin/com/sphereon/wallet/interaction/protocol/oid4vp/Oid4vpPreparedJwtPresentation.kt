/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.encodeToHex
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.openid.oid4vp.holder.*
import com.sphereon.wallet.interaction.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Private protocol facts, not authorization. The existing listed grant authorizes every signature. */
@Serializable
internal data class Oid4vpPreparedJwtPresentation(
    val sessionId: WalletInteractionSessionId,
    val walletUnitId: String,
    val operationBinding: String,
    val resolvedRequest: ResolvedOid4vpRequest,
    val selection: String,
    val selectedIds: Map<String, List<String>>,
    val issuedAt: Long,
    val expiresAt: Long,
    val prepared: HolderPreparedJwtVpResponse,
    val signing: List<Oid4vpPreparedJwtSigning>,
    val operations: List<WalletApprovalOperation>,
    /** Unkeyed SHA-256 corruption detection, not authentication; persisted approval and HSM consumption remain authoritative. */
    val integrity: String = "",
) {
    fun sealed(): Oid4vpPreparedJwtPresentation = copy(integrity = checksum())

    fun requireUsable(
        context: WalletInteractionContext,
        request: ResolvedOid4vpRequest,
        credentials: List<SelectedCredential>,
        ids: Map<String, List<String>>,
        binding: String?,
        now: Long,
    ) {
        require(integrity == checksum()) { "Prepared JWT presentation is corrupt" }
        require(context.executionOwner == ProtocolExecutionOwner.WALLET_BACKEND &&
            sessionId == context.sessionId && walletUnitId == context.walletUnitId &&
            operationBinding == binding && resolvedRequest == request && selectedIds == ids &&
            selection == selectionFingerprint(credentials)) { "Prepared JWT presentation context changed" }
        require(now < expiresAt && issuedAt <= now && expiresAt <= issuedAt + 120) { "Prepared JWT presentation expired" }
        require(signing.isNotEmpty() && signing.map { it.request } == prepared.presentations.map { it.signingRequest })
        require(signing.all { it.walletUnitId == walletUnitId && it.operationBinding == operationBinding })
        require(operations.takeLast(signing.size) == signing.map { it.approvalOperation() })
    }

    private fun checksum(): String = hash(Json.encodeToString(copy(integrity = "")).encodeToByteArray(), DigestAlg.SHA256).encodeToHex()

    companion object {
        fun selectionFingerprint(credentials: List<SelectedCredential>): String {
            // SelectedCredential's signing metadata is transient; include it explicitly in the binding.
            val jwtMetadata = credentials.map { credential ->
                listOf(credential.holderJwtVpSigningIdentifier?.let { Json.encodeToString(it) }, credential.holderSigningAlgorithm?.toString())
            }
            return hash((Json.encodeToString(credentials) + Json.encodeToString(jwtMetadata)).encodeToByteArray(), DigestAlg.SHA256).encodeToHex()
        }
    }
}

internal fun Oid4vpPreparedJwtSigning.approvalOperation(): WalletApprovalOperation =
    WalletApprovalOperation(keyRef = key.keyRef ?: key.keyId, operationHash = operationHash)
