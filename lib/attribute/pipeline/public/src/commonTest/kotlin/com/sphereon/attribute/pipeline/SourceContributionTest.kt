/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.pipeline

import com.sphereon.attribute.flow.AttributeData
import com.sphereon.attribute.flow.AttributePath
import com.sphereon.attribute.flow.AttributeProvenanceRef
import com.sphereon.attribute.flow.AttributeRecord
import com.sphereon.attribute.flow.PipelinePhase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SourceContributionTest {
    @Test
    fun serializationRoundTrip() {
        val contribution =
            SourceContribution(
                attributes =
                    listOf(
                        AttributeRecord(
                            path = AttributePath("given_name"),
                            value = AttributeData(JsonPrimitive("Jane")),
                            sourceId = AttributeProvenanceRef("hr-api"),
                            phase = PipelinePhase.CREDENTIAL_ASSEMBLY,
                            timestamp = Instant.fromEpochSeconds(0),
                        ),
                    ),
                lookupKeys =
                    listOf(
                        LookupKey(
                            name = "employee_id",
                            value = "E-1",
                            type = LookupKeyType.EMPLOYEE_ID,
                            producedBy = AttributeProvenanceRef("hr-api"),
                            phase = PipelinePhase.CREDENTIAL_ASSEMBLY,
                            timestamp = Instant.fromEpochSeconds(0),
                        ),
                    ),
                deferralSignal = DeferralSignal(expectedInterval = 60.seconds, reason = "nightly batch"),
            )
        val json = Json
        val decoded =
            json.decodeFromString(
                SourceContribution.serializer(),
                json.encodeToString(SourceContribution.serializer(), contribution),
            )
        assertEquals(contribution, decoded)
    }
}
