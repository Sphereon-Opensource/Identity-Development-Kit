/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.session

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.service.ServiceCommand

/** A reference to a specification section implemented by a journey. */
data class JourneySpecReference(
    val document: String,
    val section: String,
)

/** Metadata for one executable command in a journey. */
data class JourneyStepContract(
    val id: String,
    val commandId: String,
    val specifications: List<JourneySpecReference>,
    val extensionPoint: String? = null,
)

/** The ordered protocol contract. Its steps are captured from the commands the pipeline executes. */
data class JourneyContract(
    val id: String,
    val specifications: List<JourneySpecReference>,
    val steps: List<JourneyStepContract>,
)

/** Compile-time profile identity; profiles never provide a runtime step list. */
interface JourneyProfile {
    val id: String
}

/** A ServiceCommand that exposes the specification contract and selected compile-time profile. */
interface JourneyServiceCommand<I : Any, O : Any, E : IdkErrorType> : ServiceCommand<I, O, E> {
    val journeyContract: JourneyContract
    val journeyProfile: JourneyProfile
}

/**
 * A command and the metadata that describes that exact command in the journey. When the command
 * carries its own id, the contract must name it, so metadata cannot describe a different command
 * than the one that runs.
 */
class JourneyStep<I : Any, O : Any, E : IdkErrorType>(
    val contract: JourneyStepContract,
    val command: BaseCommand<I, O, E>,
) {
    init {
        require(contract.id.isNotBlank()) { "Journey step id must not be blank" }
        require(contract.commandId.isNotBlank()) { "Journey step '${contract.id}' must name its command" }
        val executedId = (command as? Command<*, *, *>)?.id
        require(executedId == null || executedId == contract.commandId) {
            "Journey step '${contract.id}' declares command '${contract.commandId}' but executes '$executedId'"
        }
    }
}

/** A typed command pipeline whose contract is built from its executed steps. */
interface JourneyPipeline<I : Any, O : Any, E : IdkErrorType> : Command<I, O, E> {
    val contract: JourneyContract
}

/**
 * Builds a journey on top of the existing PipeBuilder chain. The private constructor and
 * type-changing [then] operation keep metadata and executable commands in the same immutable
 * builder value. Each step's supports() check and error propagation are those of [ChainedCommand].
 */
class JourneyBuilder<I : Any, Current : Any, E : IdkErrorType> private constructor(
    private val id: String,
    private val specifications: List<JourneySpecReference>,
    private val pipeline: PipeBuilder<I, Current, E>,
    private val steps: List<JourneyStepContract>,
) {
    fun <Next : Any> then(step: JourneyStep<Current, Next, E>): JourneyBuilder<I, Next, E> {
        require(steps.none { it.id == step.contract.id }) { "Journey '$id' already contains step '${step.contract.id}'" }
        return JourneyBuilder(
            id = id,
            specifications = specifications,
            pipeline = pipeline.then(step.command),
            steps = steps + step.contract,
        )
    }

    fun build(): JourneyPipeline<I, Current, E> =
        BuiltJourneyPipeline(
            delegate = pipeline.build(),
            contract = JourneyContract(id = id, specifications = specifications.toList(), steps = steps.toList()),
        )

    companion object {
        fun <I : Any, O : Any, E : IdkErrorType> start(
            id: String,
            specifications: List<JourneySpecReference>,
            first: JourneyStep<I, O, E>,
            errorMapper: CommandErrorMapper<E>,
        ): JourneyBuilder<I, O, E> {
            require(id.isNotBlank()) { "Journey id must not be blank" }
            return JourneyBuilder(
                id = id,
                specifications = specifications.toList(),
                pipeline = PipeBuilder.start(id, first.command, errorMapper),
                steps = listOf(first.contract),
            )
        }
    }
}

private class BuiltJourneyPipeline<I : Any, O : Any, E : IdkErrorType>(
    private val delegate: Command<I, O, E>,
    override val contract: JourneyContract,
) : JourneyPipeline<I, O, E> {
    override val id: String get() = delegate.id
    override val isEnabled: Boolean get() = delegate.isEnabled
    override val subsystem: EventSubsystem get() = delegate.subsystem

    override suspend fun supports(args: Any): Boolean = delegate.supports(args)

    override suspend fun execute(args: I): IdkResult<O, E> = delegate.execute(args)
}
