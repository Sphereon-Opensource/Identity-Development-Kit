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
import com.sphereon.attribute.pipeline.LookupKey
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.credential.issuance.pipeline.PipelineConfiguration
import com.sphereon.credential.issuance.pipeline.PlatformEncryptedMode
import com.sphereon.credential.issuance.pipeline.SessionEncryptionMode
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding
import kotlinx.serialization.Serializable

/**
 * Creates and persists a new `IssuancePipelineSession` for a registered [PipelineConfiguration],
 * seeded with any initial attributes / lookup keys, encrypted per the requested mode.
 *
 * No phase runs here — this only allocates the session. The caller drives phases afterward via
 * [ContributeAttributesCommand].
 */
interface InitPipelineSessionCommand : ServiceCommand<InitPipelineSessionArgs, InitPipelineSessionResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE

    companion object {
        const val COMMAND_ID: String = "issuance.pipeline.init-session"
    }
}

/**
 * Exposes [InitPipelineSessionCommand] as an optional graph accessor so consumers declaring
 * `InitPipelineSessionCommand? = null` constructor parameters resolve cleanly under the Metro
 * `nullable type key`. Pure-IDK deployments without the EDK pipeline-impl on the classpath fall
 * through to the `null` default body. The EDK supplier adds a second
 * `@ContributesBinding(SessionScope::class, binding = binding<InitPipelineSessionCommand?>())`.
 */
@ContributesTo(SessionScope::class)
interface InitPipelineSessionCommandOptionalProvider {
    @OptionalBinding
    val optionalInitPipelineSessionCommand: InitPipelineSessionCommand? get() = null
}

@Serializable
data class InitPipelineSessionArgs(
    /** The pipeline this session runs. */
    val pipelineConfiguration: PipelineConfiguration,
    /** External correlation handle; the command generates one when absent. */
    val correlationId: String? = null,
    /** Attributes known at session init (invitation context, static-offer config, ...). */
    val initialAttributes: List<AttributeRecord> = emptyList(),
    /** Lookup keys known at session init — these satisfy a source's `consumedLookupKeys` directly. */
    val initialLookupKeys: List<LookupKey> = emptyList(),
    /** How the session payload is protected at rest. */
    val encryptionMode: SessionEncryptionMode = PlatformEncryptedMode,
    /** Session lifetime; the command applies a configured default when absent. */
    val ttlSeconds: Long? = null,
)

@Serializable
data class InitPipelineSessionResult(
    val sessionId: String,
    /** The correlation handle — generated when the caller did not supply one. */
    val correlationId: String,
)
