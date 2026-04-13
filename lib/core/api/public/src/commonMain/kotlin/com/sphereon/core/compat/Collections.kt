/*
 * © 2026 Sphereon International B.V.
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
 *
 */

package com.sphereon.core.compat

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.js.ExperimentalJsCollectionsApi

@JsExportCompat
@ExperimentalJsCollectionsApi
fun <T> kmpListOf(elements: Array<T>): List<T> = elements.toList()

@JsExportCompat
@ExperimentalJsCollectionsApi
fun <T> kmpSetOf(elements: Array<T>): Set<T> = elements.toSet()

@JsExportCompat
@ExperimentalJsCollectionsApi
fun <K, V> kmpMapOf(): MutableMap<K, V> = mutableMapOf()

fun JsonObject.mergeJsonElement(
    key: String,
    value: JsonElement,
): JsonObject {
    val result = this.toMutableMap()
    result.putAll(mapOf(Pair(key, value)))
    return JsonObject(result)
}

/**
 * Extension function for json object, which internally is a map, hence why it is found in this file
 * Adds properties to an existing json object, as the map is immutable. Returns a new object with the additional properties. Keys overwrite exiting keys!
 */
fun JsonObject.mergeJsonObject(newProperties: JsonObject): JsonObject {
    val result = this.toMutableMap()
    result.putAll(newProperties.toMap())
    return JsonObject(result)
}
