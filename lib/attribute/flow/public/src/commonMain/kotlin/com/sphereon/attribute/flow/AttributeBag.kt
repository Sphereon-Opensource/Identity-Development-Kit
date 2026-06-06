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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/**
 * An immutable container of resolved attributes keyed by [AttributePath].
 *
 * Flow-agnostic: the base attribute container used by IDV graph execution, issuance pipelines,
 * presentation harvests, and any other flow that resolves attribute values incrementally.
 *
 * Each entry is a full [AttributeRecord] — value plus provenance, phase, timestamp, assurance,
 * priority and retention — rather than a bare `JsonElement`. [attributes] holds the *effective*
 * record per path (priority-resolved); [history] holds every record ever contributed, for audit
 * and conflict-resolution review. Add records with [with] / [withAll]; read the effective record
 * with [get] or its plain value with [getValue].
 */
@JsExportCompat
@Serializable
data class AttributeBag(
    @JsExportIgnoreCompat
    val attributes: Map<AttributePath, AttributeRecord> = emptyMap(),
    @JsExportIgnoreCompat
    val history: List<AttributeRecord> = emptyList(),
) {
    companion object {
        fun empty(): AttributeBag = AttributeBag()

        /**
         * Build a bag from plain values, wrapping each in a minimal [AttributeRecord]. For
         * initial-input bags (caller-supplied attributes, test fixtures) where the values have
         * no richer provenance yet. [timestamp] is explicit — this is a pure data type and does
         * not reach for a clock.
         */
        fun of(
            values: Map<AttributePath, JsonElement>,
            sourceId: AttributeProvenanceRef,
            timestamp: Instant,
            phase: PipelinePhase = PipelinePhase.SESSION_INIT,
        ): AttributeBag =
            empty().withAll(
                values.map { (path, value) ->
                    AttributeRecord(
                        path = path,
                        value = AttributeData(value),
                        sourceId = sourceId,
                        phase = phase,
                        timestamp = timestamp,
                    )
                },
            )
    }

    /** The effective record at [key], or `null`. */
    operator fun get(key: AttributePath): AttributeRecord? = attributes[key]

    /** The effective plain value at [key] when it is data, else `null` (see [AttributeRecord.jsonValue]). */
    fun getValue(key: AttributePath): JsonElement? = attributes[key]?.jsonValue

    /**
     * Add or update a record. The incoming record wins for its [AttributeRecord.path] when there
     * is no existing record, when it has a higher [AttributeRecord.priority], or — at equal
     * priority — when its [AttributeRecord.timestamp] is not older. Every record is appended to
     * [history] regardless of whether it became effective.
     */
    fun with(record: AttributeRecord): AttributeBag {
        val existing = attributes[record.path]
        val wins =
            existing == null ||
                record.priority > existing.priority ||
                (record.priority == existing.priority && record.timestamp >= existing.timestamp)
        return AttributeBag(
            attributes = if (wins) attributes + (record.path to record) else attributes,
            history = history + record,
        )
    }

    /** Apply [with] for each record, in order. */
    @JsExportIgnoreCompat
    fun withAll(records: List<AttributeRecord>): AttributeBag = records.fold(this) { bag, record -> bag.with(record) }
}
