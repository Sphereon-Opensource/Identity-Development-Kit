/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.data.store.credential.design.impl.mapper

import com.sphereon.data.store.credential.design.model.ClaimPathSegment
import com.sphereon.data.store.credential.design.model.DesignClaimPath
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Maps between credential-design's [DesignClaimPath] and OID4VCI 1.1 (Appendix C)
 * claim path format ([List<JsonElement>]).
 *
 * Mapping rules:
 *   - [ClaimPathSegment.Property] ↔ [JsonPrimitive] (string)
 *   - [ClaimPathSegment.Index]    ↔ [JsonPrimitive] (integer)
 *   - [ClaimPathSegment.AnyArrayElement] ↔ [JsonNull]
 */
object Oid4vciClaimPathMapper {
    fun toOid4vciPath(designPath: DesignClaimPath): List<JsonElement> =
        designPath.map { segment ->
            when (segment) {
                is ClaimPathSegment.Property -> JsonPrimitive(segment.name)
                is ClaimPathSegment.Index -> JsonPrimitive(segment.index)
                ClaimPathSegment.AnyArrayElement -> JsonNull
            }
        }

    fun toDesignPath(oid4vciPath: List<JsonElement>): DesignClaimPath =
        oid4vciPath.map { element ->
            when (element) {
                is JsonNull -> {
                    ClaimPathSegment.AnyArrayElement
                }

                else -> {
                    val intVal = element.jsonPrimitive.intOrNull
                    if (intVal != null) {
                        ClaimPathSegment.Index(intVal)
                    } else {
                        ClaimPathSegment.Property(element.jsonPrimitive.content)
                    }
                }
            }
        }
}
