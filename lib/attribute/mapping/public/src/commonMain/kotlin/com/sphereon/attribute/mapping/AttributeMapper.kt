/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.mapping

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.json.JsonElement

/**
 * Apply [mappings] to [attributes], producing a new map that contains both the
 * original entries and a copy of each mapped value under its target name.
 *
 * Behaviour:
 *  - Result starts as a copy of [attributes] (sources are preserved alongside
 *    targets — the function is additive, not transformative).
 *  - For each mapping, if `attributes[mapping.source]` exists, the value is
 *    written under `mapping.target` (later mappings to the same target win).
 *  - If a mapping marked `required = true` has no matching source, the call
 *    fails with `code = "missing_required_mapping_attributes"` and the offending
 *    `source -> target` pairs in the message.
 *  - An empty [mappings] list returns [attributes] unchanged.
 */
fun applyAttributeMappings(
    attributes: Map<String, JsonElement>,
    mappings: List<AttributeMapping>,
): IdkResult<Map<String, JsonElement>, IdkError> {
    if (mappings.isEmpty()) {
        return Ok(attributes)
    }

    val missingRequired = mutableListOf<String>()
    val result =
        buildMap {
            putAll(attributes)
            for (mapping in mappings) {
                val value = attributes[mapping.source]
                if (value != null) {
                    put(mapping.target, value)
                } else if (mapping.required) {
                    missingRequired.add("${mapping.source} -> ${mapping.target}")
                }
            }
        }

    if (missingRequired.isNotEmpty()) {
        return Err(
            IdkError.fromString(
                code = "missing_required_mapping_attributes",
                message = "Required attribute mappings missing source values: $missingRequired",
                category = ErrorCategory.VALIDATION,
            ),
        )
    }

    return Ok(result)
}
