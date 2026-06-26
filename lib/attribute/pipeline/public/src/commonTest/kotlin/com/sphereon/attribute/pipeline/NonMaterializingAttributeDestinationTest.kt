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

import com.sphereon.attribute.flow.AttributeBag
import com.sphereon.attribute.flow.AttributePath
import com.sphereon.attribute.flow.AttributeProvenanceRef
import com.sphereon.attribute.flow.PipelinePhase
import com.sphereon.data.integration.MaterializationMode
import com.sphereon.data.integration.MaterializationPolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class NonMaterializingAttributeDestinationTest {
    @Test
    fun acceptsPayloadWithoutPersistingWhenPolicyIsNone() =
        runTest {
            val bag = bagOf("given_name")
            val destination = NonMaterializingAttributeDestination()
            val result =
                destination.write(
                    context = TestDestinationContext(bag = bag),
                    phase = PipelinePhase.RESOLUTION,
                    payload = DestinationPayload(attributes = bag),
                )

            assertTrue(result.isOk)
            assertEquals(DestinationWriteStatus.ACCEPTED, result.value.status)
            assertEquals(1L, result.value.recordsAccepted)
            assertEquals(0L, result.value.recordsCommitted)
            assertEquals("false", result.value.metadata["materialized"])
        }

    @Test
    fun failsWhenBindingRequestsMaterialization() =
        runTest {
            val bag = bagOf("given_name")
            val binding =
                AttributeDestinationBinding(
                    destinationId = NonMaterializingAttributeDestinationIds.DEFAULT,
                    phases = setOf(PipelinePhase.RESOLUTION),
                    materializationPolicy = MaterializationPolicy(mode = MaterializationMode.PERSIST),
                )
            val destination = NonMaterializingAttributeDestination()
            val result =
                destination.write(
                    context = TestDestinationContext(bag = bag, destinationBinding = binding),
                    phase = PipelinePhase.RESOLUTION,
                    payload = DestinationPayload(attributes = bag),
                )

            assertTrue(result.isOk)
            assertEquals(DestinationWriteStatus.FAILED, result.value.status)
            assertEquals(0L, result.value.recordsAccepted)
            assertEquals(0L, result.value.recordsCommitted)
            assertEquals("PERSIST", result.value.metadata["requestedMaterializationMode"])
        }

    private fun bagOf(path: String): AttributeBag =
        AttributeBag.of(
            values = mapOf(AttributePath(path) to JsonPrimitive("Jane")),
            sourceId = AttributeProvenanceRef("test-source"),
            timestamp = Instant.fromEpochSeconds(0),
            phase = PipelinePhase.RESOLUTION,
        )

    private class TestDestinationContext(
        override val bag: AttributeBag,
        override val destinationBinding: AttributeDestinationBinding? = null,
    ) : PipelineDestinationContext {
        override val correlationId: String = "corr-1"
        override val tenantId: String = "tenant-a"
        override val phase: PipelinePhase = PipelinePhase.RESOLUTION
        override val lookupKeys: LookupKeySet = LookupKeySet.empty()
        override val phaseInput: AttributeBag = bag
    }
}
