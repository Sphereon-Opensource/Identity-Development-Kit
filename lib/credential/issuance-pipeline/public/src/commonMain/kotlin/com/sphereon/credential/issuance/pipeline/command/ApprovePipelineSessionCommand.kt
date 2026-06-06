/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.credential.issuance.pipeline.command

import com.sphereon.attribute.flow.AttributeEvidence
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.credential.issuance.pipeline.IssuancePipelineStatus
import kotlinx.serialization.Serializable

/**
 * Applies an approval-gate decision (approve or reject) to a pipeline session that is in
 * [IssuancePipelineStatus.AWAITING_APPROVAL].
 *
 * On approval the session transitions to [IssuancePipelineStatus.READY] and the session's
 * [com.sphereon.credential.issuance.pipeline.ApprovalState] is set to
 * [com.sphereon.credential.issuance.pipeline.ApprovalApproved]. When supporting evidence is
 * supplied it is recorded in the attribute bag at path `evidence.approval`.
 *
 * On rejection the session transitions to [IssuancePipelineStatus.FAILED] and
 * [com.sphereon.credential.issuance.pipeline.ApprovalRejected] is set.
 *
 * The approver identity is taken from the [com.sphereon.core.api.context.SessionExecution];
 * it is never supplied as an argument.
 */
interface ApprovePipelineSessionCommand : ServiceCommand<ApprovePipelineSessionArgs, ApprovePipelineSessionResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.UPDATE

    companion object {
        const val COMMAND_ID: String = "issuance.pipeline.approve-session"
    }
}

/** Whether the approver grants or denies issuance for the pipeline session. */
@Serializable
enum class ApprovalDecision {
    APPROVE,
    REJECT,
}

@Serializable
data class ApprovePipelineSessionArgs(
    /** The external correlation handle identifying the session to approve or reject. */
    val correlationId: String,
    /** The decision taken by the approver. */
    val decision: ApprovalDecision,
    /** Human-readable reason; required on [ApprovalDecision.REJECT], optional on [ApprovalDecision.APPROVE]. */
    val reason: String? = null,
    /** Supporting evidence captured at approval time; recorded in the bag on [ApprovalDecision.APPROVE]. */
    val evidence: AttributeEvidence? = null,
)

@Serializable
data class ApprovePipelineSessionResult(
    /** The session's correlation id, echoed back for convenience. */
    val correlationId: String,
    /** The new lifecycle status after the decision was applied. */
    val status: IssuancePipelineStatus,
)
