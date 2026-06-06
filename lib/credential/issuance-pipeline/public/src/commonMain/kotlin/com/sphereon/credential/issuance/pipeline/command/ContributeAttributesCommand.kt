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

import com.sphereon.attribute.flow.AttributeRecord
import com.sphereon.attribute.flow.PipelinePhase
import com.sphereon.attribute.pipeline.LookupKey
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.credential.issuance.pipeline.IssuancePipelineStatus
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding
import kotlinx.serialization.Serializable

/**
 * Runs one pipeline phase for a session: loads the session, feeds the supplied [attributes] /
 * [lookupKeys] in as phase input, runs the phase's bound sources in lookup-key dependency order,
 * folds the result back into the session's bag, and persists.
 *
 * This is the single re-entrant entry point every ingress path uses — initial phase execution,
 * deferred re-execution, and inbound external contributions all funnel through it. A phase may
 * be run more than once for the same session.
 */
interface ContributeAttributesCommand : ServiceCommand<ContributeAttributesArgs, ContributeAttributesResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.UPDATE

    companion object {
        const val COMMAND_ID: String = "issuance.pipeline.contribute-attributes"
    }
}

/**
 * Exposes [ContributeAttributesCommand] as an optional graph accessor so consumers declaring
 * `ContributeAttributesCommand? = null` constructor parameters resolve cleanly under the Metro
 * `nullable type key`. Pure-IDK deployments without the EDK pipeline-impl on the classpath fall
 * through to the `null` default body. The EDK supplier adds a second
 * `@ContributesBinding(SessionScope::class, binding = binding<ContributeAttributesCommand?>())`.
 */
@ContributesTo(SessionScope::class)
interface ContributeAttributesCommandOptionalProvider {
    @OptionalBinding
    val optionalContributeAttributesCommand: ContributeAttributesCommand? get() = null
}

@Serializable
data class ContributeAttributesArgs(
    /** The session to contribute to. */
    val correlationId: String,
    /** The phase to run. */
    val phase: PipelinePhase,
    /** Attributes pushed in as input for this phase (from a backend push, a callback, ...). */
    val attributes: List<AttributeRecord> = emptyList(),
    /** Lookup keys pushed in as input for this phase. */
    val lookupKeys: List<LookupKey> = emptyList(),
)

@Serializable
data class ContributeAttributesResult(
    val sessionId: String,
    /** The session status after the phase ran. */
    val status: IssuancePipelineStatus,
    /** Phases completed for the session, including this one. */
    val completedPhases: Set<PipelinePhase>,
)
