/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.pipeline

import com.sphereon.attribute.flow.AttributeProvenanceRef
import com.sphereon.attribute.flow.PipelinePhase
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.integration.MaterializationMode

object NonMaterializingAttributeDestinationIds {
    val DEFAULT = AttributeProvenanceRef("attribute-destination:non-materializing")
}

/**
 * Default destination used when a pipeline wants to complete a destination edge without retaining
 * or projecting attribute values. It accepts payloads only while the binding remains explicitly
 * non-materializing; bindings that request persistence must use a durable destination instead.
 */
class NonMaterializingAttributeDestination(
    override val destinationId: AttributeProvenanceRef = NonMaterializingAttributeDestinationIds.DEFAULT,
    override val supportedPhases: Set<PipelinePhase> = WellKnownPipelinePhases.ALL,
) : AttributeDestination {
    override suspend fun write(
        context: PipelineDestinationContext,
        phase: PipelinePhase,
        payload: DestinationPayload,
    ): IdkResult<DestinationWriteResult, IdkError> {
        val materializationMode = context.destinationBinding?.materializationPolicy?.mode ?: MaterializationMode.NONE
        if (materializationMode != MaterializationMode.NONE) {
            return Ok(
                DestinationWriteResult(
                    status = DestinationWriteStatus.FAILED,
                    recordsAccepted = 0,
                    recordsCommitted = 0,
                    metadata =
                        resultMetadata(
                            "requestedMaterializationMode" to materializationMode.name,
                            "reason" to "non-materializing-destination-cannot-persist",
                        ),
                ),
            )
        }

        return Ok(
            DestinationWriteResult(
                status = DestinationWriteStatus.ACCEPTED,
                recordsAccepted =
                    payload.attributes.attributes.size
                        .toLong(),
                recordsCommitted = 0,
                metadata = resultMetadata("phase" to phase.value),
            ),
        )
    }

    private fun resultMetadata(vararg entries: Pair<String, String>): Map<String, String> =
        mapOf(
            "destination.kind" to "non-materializing",
            "materialization.mode" to MaterializationMode.NONE.name,
            "materialized" to "false",
        ) + entries.toMap()
}
