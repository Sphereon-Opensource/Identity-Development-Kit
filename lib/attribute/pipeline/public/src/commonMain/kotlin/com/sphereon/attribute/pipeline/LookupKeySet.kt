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
import com.sphereon.attribute.flow.AttributeRecord
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * An immutable set of [LookupKey]s held alongside the [com.sphereon.attribute.flow.AttributeBag]
 * for a pipeline session.
 *
 * [keys] holds the effective key per name (last-write-wins, same as the bag's priority model
 * at equal priority); [history] holds every key ever contributed.
 */
@JsExportCompat
@Serializable
data class LookupKeySet(
    @JsExportIgnoreCompat
    val keys: Map<String, LookupKey> = emptyMap(),
    @JsExportIgnoreCompat
    val history: List<LookupKey> = emptyList(),
) {
    companion object {
        fun empty(): LookupKeySet = LookupKeySet()
    }

    /** The effective value for [name], or `null`. */
    operator fun get(name: String): String? = keys[name]?.value

    /** Add or update a key (last write wins); every key is appended to [history]. */
    fun with(key: LookupKey): LookupKeySet =
        LookupKeySet(
            keys = keys + (key.name to key),
            history = history + key,
        )

    /** Apply [with] for each key, in order. */
    @JsExportIgnoreCompat
    fun withAll(keys: List<LookupKey>): LookupKeySet = keys.fold(this) { set, key -> set.with(key) }

    /**
     * Materialise the keys that declared a [LookupKey.promotedToAttributePath] as
     * [AttributeRecord]s, so they can be folded into the bag before credential assembly.
     */
    @JsExportIgnoreCompat
    fun promotedAttributeRecords(): List<AttributeRecord> =
        keys.values.mapNotNull { key ->
            key.promotedToAttributePath?.let { path ->
                AttributeRecord(
                    path = path,
                    value = AttributeData(JsonPrimitive(key.value)),
                    sourceId = key.producedBy,
                    sourceDetail = key.sourceDetail,
                    phase = key.phase,
                    timestamp = key.timestamp,
                )
            }
        }
}
