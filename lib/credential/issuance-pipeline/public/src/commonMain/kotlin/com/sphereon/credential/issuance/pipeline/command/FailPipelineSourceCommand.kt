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

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import kotlinx.serialization.Serializable

/**
 * Marks a single attribute source's contribution to a pipeline session as failed.
 *
 * Records the failure on the session bag so subsequent completeness evaluations can take the
 * failure into account: an unsatisfied required source with a recorded failure is a hard miss
 * that callers can promote into a `not-deferrable` verdict rather than waiting indefinitely.
 *
 * The session is resolved by the same HMAC-correlationId path the rest of the pipeline
 * commands use; the source is identified by its [AttributeSource.sourceId] string value.
 */
interface FailPipelineSourceCommand : ServiceCommand<FailPipelineSourceArgs, FailPipelineSourceResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.UPDATE

    companion object {
        const val COMMAND_ID: String = "issuance.pipeline.fail-source"
    }
}

@Serializable
data class FailPipelineSourceArgs(
    /** The external correlation handle identifying the pipeline session. */
    val correlationId: String,
    /** The opaque source identifier (matches `AttributeProvenanceRef.value`). */
    val sourceId: String,
    /** Optional human-readable reason recorded with the failure marker. */
    val reason: String? = null,
)

@Serializable
data class FailPipelineSourceResult(
    /** The session's correlation id, echoed back for convenience. */
    val correlationId: String,
    /** The source id whose contribution was marked as failed. */
    val sourceId: String,
)
