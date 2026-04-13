/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.command

import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.trust.core.model.TrustAnchor
import com.sphereon.trust.core.model.TrustAnchorType
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustValidationResult
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

// ============================================================================
// Trust Validation Command
// ============================================================================

interface ValidateTrustCommand : ServiceCommand<ValidateTrustArgs, TrustValidationResult> {
    companion object {
        const val COMMAND_ID = "trust.validation.validate"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE
}

@Serializable
data class ValidateTrustArgs(
    val contextType: String,
    val framework: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val identifierJson: String,
    val validationTime: Instant? = null,
    val checkRevocation: Boolean = true
)

// ============================================================================
// Get Trust Anchors Command
// ============================================================================

interface GetTrustAnchorsCommand : ServiceCommand<GetTrustAnchorsArgs, TrustAnchorListResult> {
    companion object {
        const val COMMAND_ID = "trust.anchors.list"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.LIST
}

@Serializable
data class GetTrustAnchorsArgs(
    val anchorType: TrustAnchorType? = null,
    val contextType: String? = null
)

@Serializable
data class TrustAnchorListResult(
    val anchors: List<TrustAnchor>,
    val totalCount: Int
)

// ============================================================================
// Refresh Trust Anchors Command
// ============================================================================

interface RefreshTrustAnchorsCommand : ServiceCommand<RefreshTrustArgs, RefreshTrustResult> {
    companion object {
        const val COMMAND_ID = "trust.anchors.refresh"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE
}

@Serializable
data class RefreshTrustArgs(
    val anchorType: TrustAnchorType? = null,
    val forceRefresh: Boolean = false
)

@Serializable
data class RefreshTrustResult(
    val refreshed: Boolean,
    val anchorTypesRefreshed: List<String> = emptyList(),
    val details: String? = null
)

// ============================================================================
// Check Revocation Command
// ============================================================================

interface CheckRevocationCommand : ServiceCommand<CheckRevocationArgs, RevocationCheckCommandResult> {
    companion object {
        const val COMMAND_ID = "trust.revocation.check"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE
}

@Serializable
data class CheckRevocationArgs(
    val certificateDer: ByteArray,
    val issuerCertificateDer: ByteArray? = null,
    val checkOcsp: Boolean = true,
    val checkCrl: Boolean = true,
    val preferOcsp: Boolean = true,
    val timeoutMs: Long = 10000
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as CheckRevocationArgs
        return certificateDer.contentEquals(other.certificateDer) &&
                issuerCertificateDer.contentEquals(other.issuerCertificateDer)
    }

    override fun hashCode(): Int {
        var result = certificateDer.contentHashCode()
        result = 31 * result + (issuerCertificateDer?.contentHashCode() ?: 0)
        return result
    }
}

@Serializable
data class RevocationCheckCommandResult(
    val status: String,
    val method: String,
    val checkedAt: Long,
    val fromCache: Boolean = false,
    val revocationTime: Long? = null,
    val revocationReason: String? = null,
    val errorMessage: String? = null
)
