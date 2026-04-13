/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.command

import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.trust.core.model.DiscoveredEntityInfo
import com.sphereon.trust.core.model.EntityDiscoveryOptions
import com.sphereon.trust.core.model.TrustAnchor
import com.sphereon.trust.core.model.TrustAnchorType
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustValidationResult
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// ============================================================================
// Discover Entity Info Command
// ============================================================================

interface DiscoverEntityInfoCommand : ServiceCommand<DiscoverEntityInfoArgs, DiscoverEntityInfoResult> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE

    companion object {
        const val COMMAND_ID = "trust.discovery.entityinfo"
    }
}

@Serializable
data class DiscoverEntityInfoArgs(
    val contextType: String,
    val entityIdentifier: String,
    val options: EntityDiscoveryOptions = EntityDiscoveryOptions(enabled = true),
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
data class DiscoverEntityInfoResult(
    val entities: List<DiscoveredEntityInfo>,
    val sourceType: TrustAnchorType,
    val details: String? = null,
)

// ============================================================================
// Trust Validation Command
// ============================================================================

interface ValidateTrustCommand : ServiceCommand<ValidateTrustArgs, TrustValidationResult> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE

    companion object {
        const val COMMAND_ID = "trust.validation.validate"
    }
}

@Serializable
data class ValidateTrustArgs(
    val contextType: String,
    val framework: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val identifierJson: String,
    val validationTime: Instant? = null,
    val checkRevocation: Boolean = true,
    val entityDiscovery: EntityDiscoveryOptions? = null,
)

// ============================================================================
// Get Trust Anchors Command
// ============================================================================

interface GetTrustAnchorsCommand : ServiceCommand<GetTrustAnchorsArgs, TrustAnchorListResult> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.LIST

    companion object {
        const val COMMAND_ID = "trust.anchors.list"
    }
}

@Serializable
data class GetTrustAnchorsArgs(
    val anchorType: TrustAnchorType? = null,
    val contextType: String? = null,
)

@Serializable
data class TrustAnchorListResult(
    val anchors: List<TrustAnchor>,
    val totalCount: Int,
)

// ============================================================================
// Refresh Trust Anchors Command
// ============================================================================

interface RefreshTrustAnchorsCommand : ServiceCommand<RefreshTrustArgs, RefreshTrustResult> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE

    companion object {
        const val COMMAND_ID = "trust.anchors.refresh"
    }
}

@Serializable
data class RefreshTrustArgs(
    val anchorType: TrustAnchorType? = null,
    val forceRefresh: Boolean = false,
)

@Serializable
data class RefreshTrustResult(
    val refreshed: Boolean,
    val anchorTypesRefreshed: List<String> = emptyList(),
    val details: String? = null,
)

// ============================================================================
// Check Revocation Command
// ============================================================================

interface CheckRevocationCommand : ServiceCommand<CheckRevocationArgs, RevocationCheckCommandResult> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE

    companion object {
        const val COMMAND_ID = "trust.revocation.check"
    }
}

@Serializable
data class CheckRevocationArgs(
    val certificateDer: ByteArray,
    val issuerCertificateDer: ByteArray? = null,
    val checkOcsp: Boolean = true,
    val checkCrl: Boolean = true,
    val preferOcsp: Boolean = true,
    val timeoutMs: Long = 10000,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }
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
    val errorMessage: String? = null,
)
