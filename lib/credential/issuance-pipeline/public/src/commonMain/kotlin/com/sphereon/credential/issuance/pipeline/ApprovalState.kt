/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.credential.issuance.pipeline

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Approval-gate state on an [IssuancePipelineSession] — present when any bound credential's
 * `DeferralPolicy.approvalRequired` is true.
 *
 * Sealed; variants are top-level (mirroring [com.sphereon.attribute.flow.AttributeRetentionPolicy])
 * so kotlinx-serialization and JS export stay well-behaved. The approver identity is encrypted
 * with the rest of the session payload at rest.
 */
@JsExportCompat
@Serializable
sealed interface ApprovalState

/** Attributes are complete; an approver has not yet decided. */
@Serializable
@SerialName("pending")
data object ApprovalPending : ApprovalState

/** An approver granted issuance. */
@Serializable
@SerialName("approved")
data class ApprovalApproved(
    val approver: String,
    val at: Instant,
    /** Reference to the approval evidence record, if one was captured. */
    val evidenceId: String? = null,
) : ApprovalState

/** An approver rejected issuance. */
@Serializable
@SerialName("rejected")
data class ApprovalRejected(
    val approver: String,
    val at: Instant,
    val reason: String? = null,
) : ApprovalState
