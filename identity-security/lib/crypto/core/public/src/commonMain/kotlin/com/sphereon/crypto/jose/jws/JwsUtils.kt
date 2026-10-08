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
 */

package com.sphereon.crypto.jose.jws

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.crypto.core.json.cryptoJsonSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Utility object for JWS operations
 */
object JwsUtils {
    /**
     * Converts payload to ByteArray
     */
    fun payloadToBytes(payload: Any): ByteArray =
        when (payload) {
            is ByteArray -> {
                payload
            }

            is String -> {
                // Try to detect if it's base64url encoded or plain string
                if (payload.matches(Regex("^[A-Za-z0-9_-]+$"))) {
                    try {
                        payload.decodeFrom(Encoding.BASE64URL)
                    } catch (_: Exception) {
                        payload.encodeToByteArray()
                    }
                } else {
                    payload.encodeToByteArray()
                }
            }

            is JsonObject -> {
                cryptoJsonSerializer.encodeToString(JsonObject.serializer(), payload).encodeToByteArray()
            }

            is Map<*, *> -> {
                // Convert Map to JsonObject
                val jsonObject =
                    buildJsonObject {
                        payload.forEach { (key, value) ->
                            val keyStr = key.toString()
                            when (value) {
                                is String -> {
                                    put(keyStr, value)
                                }

                                is Number -> {
                                    put(keyStr, JsonPrimitive(value))
                                }

                                is Boolean -> {
                                    put(keyStr, value)
                                }

                                null -> {
                                    put(keyStr, JsonNull)
                                }

                                is Map<*, *> -> {
                                    // Recursively handle nested maps
                                    val nested =
                                        buildJsonObject {
                                            (value as Map<*, *>).forEach { (k, v) ->
                                                put(k.toString(), JsonPrimitive(v.toString()))
                                            }
                                        }
                                    put(keyStr, nested)
                                }

                                is List<*> -> {
                                    val array =
                                        buildJsonArray {
                                            value.forEach { item ->
                                                add(JsonPrimitive(item.toString()))
                                            }
                                        }
                                    put(keyStr, array)
                                }

                                else -> {
                                    put(keyStr, JsonPrimitive(value.toString()))
                                }
                            }
                        }
                    }
                cryptoJsonSerializer.encodeToString(JsonObject.serializer(), jsonObject).encodeToByteArray()
            }

            else -> {
                // For any other type, convert to string
                payload.toString().encodeToByteArray()
            }
        }

    /**
     * Encodes a JSON object to base64url
     */
    fun encodeJsonToBase64Url(json: JsonObject): String {
        val jsonString = cryptoJsonSerializer.encodeToString(JsonObject.serializer(), json)
        return jsonString.encodeToByteArray().encodeTo(Encoding.BASE64URL)
    }

    /**
     * Encodes bytes to base64url
     */
    fun encodeBytesToBase64Url(bytes: ByteArray): String = bytes.encodeTo(Encoding.BASE64URL)

    /**
     * Decodes a base64url string to JSON object
     */
    fun decodeBase64UrlToJson(base64Url: String): JsonObject {
        val bytes = base64Url.decodeFrom(Encoding.BASE64URL)
        val jsonString = bytes.decodeToString()
        return cryptoJsonSerializer.parseToJsonElement(jsonString).jsonObject
    }

    /**
     * Decodes a base64url string to bytes
     */
    fun decodeBase64UrlToBytes(base64Url: String): ByteArray = base64Url.decodeFrom(Encoding.BASE64URL)

    /**
     * Creates the signing input for a JWS (base64url(header).base64url(payload))
     */
    fun createSigningInput(
        base64UrlHeader: String,
        base64UrlPayload: String,
    ): ByteArray = "$base64UrlHeader.$base64UrlPayload".encodeToByteArray()

    /**
     * Converts a compact JWS to general JSON format
     */
    fun compactToGeneral(compact: JwsCompact): JwsJsonGeneral {
        val parts = compact.value.split(".")
        require(parts.size == 3) { "Invalid compact JWS format" }

        return JwsJsonGeneral(
            payload = parts[1],
            signatures =
                listOf(
                    JwsJsonSignature(
                        protected = parts[0],
                        signature = parts[2],
                    ),
                ),
        )
    }

    /**
     * Converts a flattened JSON JWS to general JSON format
     */
    fun flattenedToGeneral(flattened: JwsJsonFlattened): JwsJsonGeneral =
        JwsJsonGeneral(
            payload = flattened.payload,
            signatures =
                listOf(
                    JwsJsonSignature(
                        protected = flattened.protected,
                        header = flattened.header,
                        signature = flattened.signature,
                    ),
                ),
        )

    /**
     * Converts any JWS format to general JSON format
     */
    fun toGeneral(jws: Jws): JwsJsonGeneral =
        when (jws) {
            is JwsCompact -> compactToGeneral(jws)
            is JwsJsonFlattened -> flattenedToGeneral(jws)
            is JwsJsonGeneral -> jws
            else -> throw IllegalArgumentException("Unsupported JWS type: ${jws::class}")
        }
}
