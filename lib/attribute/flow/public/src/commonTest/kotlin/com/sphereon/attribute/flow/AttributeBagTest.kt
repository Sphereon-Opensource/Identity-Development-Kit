/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.flow

import com.sphereon.core.api.compliance.LegalBasis
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class AttributeBagTest {
    private val t0 = Instant.fromEpochSeconds(0)
    private val t1 = Instant.fromEpochSeconds(100)

    private fun rec(
        path: String,
        value: String,
        priority: Int = 0,
        timestamp: Instant = t0,
    ): AttributeRecord =
        AttributeRecord(
            path = AttributePath(path),
            value = AttributeData(JsonPrimitive(value)),
            sourceId = AttributeProvenanceRef("src"),
            phase = PipelinePhase.SESSION_INIT,
            timestamp = timestamp,
            priority = priority,
        )

    @Test
    fun higherPriorityWins() {
        val bag =
            AttributeBag
                .empty()
                .with(rec("email", "low", priority = 0))
                .with(rec("email", "high", priority = 5))
        assertEquals(JsonPrimitive("high"), bag.getValue(AttributePath("email")))
    }

    @Test
    fun lowerPriorityDoesNotOverride() {
        val bag =
            AttributeBag
                .empty()
                .with(rec("email", "high", priority = 5))
                .with(rec("email", "low", priority = 0))
        assertEquals(JsonPrimitive("high"), bag.getValue(AttributePath("email")))
    }

    @Test
    fun equalPriorityLaterTimestampWins() {
        val bag =
            AttributeBag
                .empty()
                .with(rec("x", "old", priority = 0, timestamp = t0))
                .with(rec("x", "new", priority = 0, timestamp = t1))
        assertEquals(JsonPrimitive("new"), bag.getValue(AttributePath("x")))
    }

    @Test
    fun historyKeepsEveryRecordEvenWhenNotEffective() {
        val bag =
            AttributeBag
                .empty()
                .with(rec("x", "high", priority = 5))
                .with(rec("x", "low", priority = 0))
        assertEquals(2, bag.history.size)
        assertEquals(1, bag.attributes.size)
    }

    @Test
    fun ofFactoryWrapsPlainValues() {
        val bag =
            AttributeBag.of(
                values = mapOf(AttributePath("a") to JsonPrimitive("1")),
                sourceId = AttributeProvenanceRef("src"),
                timestamp = t0,
            )
        assertEquals(JsonPrimitive("1"), bag.getValue(AttributePath("a")))
        assertEquals(PipelinePhase.SESSION_INIT, bag[AttributePath("a")]?.phase)
    }

    @Test
    fun getValueIsNullForAbsentPath() {
        assertNull(AttributeBag.empty().getValue(AttributePath("missing")))
    }

    @Test
    fun serializationRoundTrip() {
        val bag =
            AttributeBag
                .empty()
                .with(rec("email", "a@example.com", priority = 1, timestamp = t1))
                .with(
                    AttributeRecord(
                        path = AttributePath("doc"),
                        value =
                            AttributeEvidence(
                                evidenceId = "ev-1",
                                evidenceType = "document_scan",
                            ),
                        sourceId = AttributeProvenanceRef("idv"),
                        phase = PipelinePhase.IDV_COMPLETED,
                        timestamp = t1,
                        retention = RetainedRetention(retentionDays = 30, legalBasis = LegalBasis.GDPR_ART6_1C_LEGAL_OBLIGATION),
                        verified = true,
                    ),
                )
        // AttributeBag keys its map by AttributePath (a structured type), so JSON encoding needs
        // allowStructuredMapKeys — the spec-blessed kotlinx-serialization setting for non-primitive
        // map keys. This is a pre-existing characteristic of the bag's map-key type, unchanged here.
        val json = Json { allowStructuredMapKeys = true }
        val decoded = json.decodeFromString(AttributeBag.serializer(), json.encodeToString(AttributeBag.serializer(), bag))
        assertEquals(bag, decoded)
    }
}
