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

package com.sphereon.core.api.conf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock

/**
 * Convert a flat map of dot‐separated keys into a nested JsonObject.
 *
 * Example:
 *   mapOf("a.b" to 1, "a.c" to 2, "d" to "hello")
 * becomes
 *   {"a":{"b":1,"c":2},"d":"hello"}
 *
 * When keyDenormalizer is provided with CamelCaseKeyDenormalizerImpl:
 *   mapOf("expose.private.keys" to true)
 * becomes
 *   {"exposePrivateKeys":true}
 *
 * @param redact Whether to redact sensitive values (defaults to true)
 * @param redactionPolicy The policy to use for redaction (defaults to DefaultSecretRedactionPolicy)
 * @param keyDenormalizer Optional denormalizer to transform keys (e.g., dot-separated to camelCase)
 */
fun Map<String, Any?>.toJsonObject(
    redact: Boolean = true,
    redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy(),
    keyDenormalizer: PropertyKeyDenormalizer? = null,
): JsonObject {
    // Helper to check if a key should be redacted
    fun shouldRedactKey(flatKey: String): Boolean {
        if (!redact) {
            return false
        }
        val metadata =
            ResolutionMetadata(
                source = "JsonMapping",
                scope = ConfigLevel.APP,
                originalKey = flatKey,
                normalizedKey = flatKey,
                order = 0,
                isSecret = false,
                isInterpolated = false,
                resolvedAt = Clock.System.now(),
                ttl = null,
            )
        return redactionPolicy.shouldRedact(flatKey, metadata)
    }

    // 1) Build a nested MutableMap tree, applying redaction to leaf values
    // When a denormalizer is provided, keys are denormalized to flat format (e.g., camelCase)
    val root = mutableMapOf<String, Any?>()
    for ((flatKey, value) in this) {
        // Apply redaction to the leaf value
        val finalValue =
            if (shouldRedactKey(flatKey) && value != null) {
                redactionPolicy.redact(value.toString())
            } else {
                value
            }

        if (keyDenormalizer != null) {
            // With denormalizer: convert to flat camelCase key (no nesting)
            val denormalizedKey = keyDenormalizer.denormalize(flatKey)
            root[denormalizedKey] = finalValue
        } else {
            // Without denormalizer: build nested structure from dot-separated key
            val parts = flatKey.split('.')
            var current = root
            for (segment in parts.dropLast(1)) {
                val nxt = current[segment]
                if (nxt is MutableMap<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    current = nxt as MutableMap<String, Any?>
                } else {
                    val child = mutableMapOf<String, Any?>()
                    current[segment] = child
                    current = child
                }
            }
            current[parts.last()] = finalValue
        }
    }

    // 2) Recursively turn that tree into JsonElements
    fun Any?.toJsonElement(): JsonElement =
        when (this) {
            null -> {
                JsonNull
            }

            is JsonElement -> {
                this
            }

            is String -> {
                JsonPrimitive(this)
            }

            is Number -> {
                JsonPrimitive(this)
            }

            is Boolean -> {
                JsonPrimitive(this)
            }

            is Map<*, *> -> {
                buildJsonObject {
                    for ((k, v) in this@toJsonElement) {
                        @Suppress("UNCHECKED_CAST")
                        put(k as String, (v as Any?).toJsonElement())
                    }
                }
            }

            is Iterable<*> -> {
                buildJsonArray {
                    for (item in this@toJsonElement) add(item.toJsonElement())
                }
            }

            else -> {
                JsonPrimitive(this.toString())
            }
        }

    return buildJsonObject {
        for ((k, v) in root) put(k, v.toJsonElement())
    }
}

/**
 * Shortcut to get the compact JSON string.
 *
 * @param redact Whether to redact sensitive values (defaults to true)
 * @param redactionPolicy The policy to use for redaction (defaults to DefaultSecretRedactionPolicy)
 * @param keyDenormalizer Optional denormalizer to transform keys (e.g., dot-separated to camelCase)
 */
fun Map<String, Any?>.toJsonString(
    redact: Boolean = true,
    redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy(),
    keyDenormalizer: PropertyKeyDenormalizer? = null,
): String = Json.encodeToString(JsonObject.serializer(), toJsonObject(redact, redactionPolicy, keyDenormalizer))
