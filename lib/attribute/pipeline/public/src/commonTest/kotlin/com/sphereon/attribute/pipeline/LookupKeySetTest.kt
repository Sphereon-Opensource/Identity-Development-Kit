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

import com.sphereon.attribute.flow.AttributePath
import com.sphereon.attribute.flow.AttributeProvenanceRef
import com.sphereon.attribute.flow.PipelinePhase
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class LookupKeySetTest {
    private val t0 = Instant.fromEpochSeconds(0)

    private fun key(
        name: String,
        value: String,
        promoted: AttributePath? = null,
    ): LookupKey =
        LookupKey(
            name = name,
            value = value,
            producedBy = AttributeProvenanceRef("src"),
            phase = PipelinePhase.SESSION_INIT,
            timestamp = t0,
            promotedToAttributePath = promoted,
        )

    @Test
    fun getReturnsEffectiveValue() {
        val set = LookupKeySet.empty().with(key("email", "a@example.com"))
        assertEquals("a@example.com", set["email"])
        assertNull(set["missing"])
    }

    @Test
    fun lastWriteWinsAndHistoryIsKept() {
        val set =
            LookupKeySet
                .empty()
                .with(key("email", "first"))
                .with(key("email", "second"))
        assertEquals("second", set["email"])
        assertEquals(2, set.history.size)
        assertEquals(1, set.keys.size)
    }

    @Test
    fun promotedAttributeRecordsMaterialiseOnlyPromotedKeys() {
        val set =
            LookupKeySet
                .empty()
                .with(key("email", "a@example.com"))
                .with(key("employee_id", "E-1", promoted = AttributePath("employee_id")))
        val records = set.promotedAttributeRecords()
        assertEquals(1, records.size)
        assertEquals(AttributePath("employee_id"), records.single().path)
        assertEquals(AttributeProvenanceRef("src"), records.single().sourceId)
    }

    @Test
    fun serializationRoundTrip() {
        val set =
            LookupKeySet
                .empty()
                .with(key("email", "a@example.com"))
                .with(key("employee_id", "E-1", promoted = AttributePath("employee_id")))
        val json = Json
        val decoded = json.decodeFromString(LookupKeySet.serializer(), json.encodeToString(LookupKeySet.serializer(), set))
        assertEquals(set, decoded)
    }
}
