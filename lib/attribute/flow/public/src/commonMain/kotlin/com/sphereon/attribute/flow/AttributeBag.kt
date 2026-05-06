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

/**
 * An immutable map of resolved attribute values keyed by [AttributePath], with optional
 * per-entry provenance pointing at the producer (via [AttributeProvenanceRef]).
 *
 * Flow-agnostic: this is the base attribute container used by IDV graph execution, issuance
 * pipelines, presentation harvests, and any other flow that resolves attribute values
 * incrementally. Consumers add entries with [with] / [withAll] and read via [get].
 */
@JsExportCompat
@Serializable
data class AttributeBag(
    @JsExportIgnoreCompat
    val attributes: Map<AttributePath, JsonElement> = emptyMap(),
    @JsExportIgnoreCompat
    val provenance: Map<AttributePath, AttributeProvenanceRef> = emptyMap(),
) {
    companion object {
        fun empty(): AttributeBag = AttributeBag()
    }

    operator fun get(key: AttributePath): JsonElement? = attributes[key]

    fun with(
        key: AttributePath,
        value: JsonElement,
        source: AttributeProvenanceRef? = null,
    ): AttributeBag =
        AttributeBag(
            attributes = attributes + (key to value),
            provenance =
                if (source != null) {
                    provenance + (key to source)
                } else {
                    provenance
                },
        )

    @JsExportIgnoreCompat
    fun withAll(
        values: Map<AttributePath, JsonElement>,
        source: AttributeProvenanceRef? = null,
    ): AttributeBag =
        AttributeBag(
            attributes = attributes + values,
            provenance =
                if (source != null) {
                    provenance + values.keys.associateWith { source }
                } else {
                    provenance
                },
        )
}
