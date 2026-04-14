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

package com.sphereon.openid.oid4vp.common

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * VP Token (Verifiable Presentation Token) - OpenID4VP 1.0 Final DCQL Format
 *
 * OpenID4VP 1.0 Final Section 6.4:
 * "When using DCQL, the vp_token is a JSON object where each key is a credential
 * query ID from the dcql_query, and the value is either a single presentation
 * string or an array of presentation strings."
 *
 * DCQL vp_token format:
 * ```json
 * {
 *   "credential_query_id_1": "eyJhbGc...",
 *   "credential_query_id_2": ["eyJhbGc...", "eyJhbGc..."]
 * }
 * ```
 *
 * Each key corresponds to an `id` from a credential query in the DCQL query.
 * Each value contains the presentation(s) matching that query.
 *
 * Presentations can be in various formats:
 * - SD-JWT DC (dc+sd-jwt) - RFC 9901
 * - mDoc (mso_mdoc) - ISO 18013-5
 * - JWT VP (jwt_vp_json) - W3C VC Data Model
 *
 * @property presentations Map from credential query ID to list of presentations.
 *                         Note: Even single presentations are stored as single-element lists.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VpToken", exact = true)
@JsExportCompat
@Serializable(with = VpTokenSerializer::class)
data class VpToken(
    @JsExportIgnoreCompat
    val presentations: Map<String, List<String>>,
) {
    init {
        require(presentations.isNotEmpty()) { "VP Token must contain at least one credential query" }
        presentations.forEach { (queryId, presentationList) ->
            require(queryId.isNotBlank()) { "Credential query ID cannot be blank" }
            require(presentationList.isNotEmpty()) { "Presentations list for query '$queryId' cannot be empty" }
            presentationList.forEach { presentation ->
                require(presentation.isNotBlank()) { "Presentation in query '$queryId' cannot be blank" }
            }
        }
    }

    /**
     * Get all presentations as a flat list (ignoring query IDs).
     */
    val allPresentations: List<String>
        get() = presentations.values.flatten()

    /**
     * Get the total number of presentations across all queries.
     */
    val presentationCount: Int
        get() = presentations.values.sumOf { it.size }

    /**
     * Get all credential query IDs present in this VP token.
     */
    val queryIds: Set<String>
        get() = presentations.keys

    /**
     * Get presentations for a specific credential query ID.
     *
     * @param queryId The credential query ID from the DCQL query
     * @return List of presentations for this query, or null if not found
     */
    fun getPresentation(queryId: String): List<String>? = presentations[queryId]

    /**
     * Get a single presentation for a query ID (first one if multiple).
     *
     * @param queryId The credential query ID
     * @return The first presentation for this query, or null if not found
     */
    fun getSinglePresentation(queryId: String): String? = presentations[queryId]?.firstOrNull()

    companion object {
        /**
         * Create a VP Token from a JSON element.
         *
         * Parses both the DCQL object format:
         * ```json
         * { "query_id": "presentation" }
         * { "query_id": ["presentation1", "presentation2"] }
         * ```
         *
         * @param json JSON element representing the vp_token
         * @return Parsed VpToken
         * @throws IllegalArgumentException if JSON format is invalid
         */
        @JvmStatic
        fun fromJson(json: JsonElement): VpToken =
            when (json) {
                is JsonObject -> parseObjectFormat(json)

                else -> throw IllegalArgumentException(
                    "VP Token must be a JSON object with credential query IDs as keys. Got: ${json::class.simpleName}",
                )
            }

        /**
         * Parse DCQL object format where keys are credential query IDs.
         */
        private fun parseObjectFormat(jsonObject: JsonObject): VpToken {
            require(jsonObject.isNotEmpty()) { "VP Token object cannot be empty" }

            val presentations = mutableMapOf<String, List<String>>()

            for ((queryId, value) in jsonObject) {
                val presentationList =
                    when (value) {
                        is JsonPrimitive -> {
                            require(value.isString) {
                                "VP Token presentation for '$queryId' must be a string, got: ${value::class.simpleName}"
                            }
                            listOf(value.content)
                        }

                        is JsonArray -> {
                            value.jsonArray.map { element ->
                                require(element is JsonPrimitive && element.isString) {
                                    "VP Token array element for '$queryId' must be a string"
                                }
                                element.jsonPrimitive.content
                            }
                        }

                        else -> {
                            throw IllegalArgumentException(
                                "VP Token value for '$queryId' must be a string or array, got: ${value::class.simpleName}",
                            )
                        }
                    }

                require(presentationList.isNotEmpty()) {
                    "VP Token presentations for '$queryId' cannot be empty"
                }

                presentations[queryId] = presentationList
            }

            return VpToken(presentations)
        }

        /**
         * Convert VP Token to JSON element.
         *
         * Serializes to DCQL object format:
         * - Single presentations are serialized as strings
         * - Multiple presentations are serialized as arrays
         *
         * @return JSON object with credential query IDs as keys
         */
        fun VpToken.toJson(): JsonElement {
            val entries =
                presentations.mapValues { (_, presentationList) ->
                    if (presentationList.size == 1) {
                        JsonPrimitive(presentationList.first())
                    } else {
                        JsonArray(presentationList.map { JsonPrimitive(it) })
                    }
                }
            return JsonObject(entries)
        }
    }
}

/**
 * Custom serializer for VpToken that handles the DCQL object format.
 */
object VpTokenSerializer : KSerializer<VpToken> {
    override val descriptor: SerialDescriptor =
        MapSerializer(
            String.serializer(),
            ListSerializer(String.serializer()),
        ).descriptor

    override fun serialize(
        encoder: Encoder,
        value: VpToken,
    ) {
        val jsonEncoder = encoder as kotlinx.serialization.json.JsonEncoder
        jsonEncoder.encodeJsonElement(VpToken.run { value.toJson() })
    }

    override fun deserialize(decoder: Decoder): VpToken {
        val jsonDecoder = decoder as kotlinx.serialization.json.JsonDecoder
        return VpToken.fromJson(jsonDecoder.decodeJsonElement())
    }
}

// =============================================================================
// Builder DSL
// =============================================================================

/**
 * Builder for creating VP Tokens with DCQL format.
 */
@JsExportCompat
class VpTokenBuilder {
    private val presentations = mutableMapOf<String, MutableList<String>>()

    /**
     * Add a presentation for a credential query ID.
     *
     * @param queryId The credential query ID from the DCQL query
     * @param presentation The presentation string
     */
    fun presentation(
        queryId: String,
        presentation: String,
    ) = apply {
        presentations.getOrPut(queryId) { mutableListOf() }.add(presentation)
    }

    /**
     * Add multiple presentations for a credential query ID.
     *
     * @param queryId The credential query ID
     * @param presentationList List of presentations
     */
    fun presentations(
        queryId: String,
        presentationList: List<String>,
    ) = apply {
        presentations.getOrPut(queryId) { mutableListOf() }.addAll(presentationList)
    }

    /**
     * Add a presentation entry (query ID to presentation mapping).
     */
    fun entry(
        queryId: String,
        vararg presentationValues: String,
    ) = apply {
        presentations.getOrPut(queryId) { mutableListOf() }.addAll(presentationValues)
    }

    /**
     * Build the VP Token.
     */
    fun build(): VpToken {
        require(presentations.isNotEmpty()) { "VP Token must contain at least one presentation" }
        return VpToken(presentations.mapValues { it.value.toList() })
    }
}

/**
 * Build a VP Token using a type-safe builder DSL.
 *
 * Example:
 * ```kotlin
 * val vpToken = buildVpToken {
 *     presentation("driver_license_query", sdJwtPresentation)
 *     presentation("age_verification_query", mdocPresentation)
 *     // Multiple presentations for same query
 *     presentations("employment_query", listOf(credential1, credential2))
 * }
 * ```
 */
inline fun buildVpToken(block: VpTokenBuilder.() -> Unit): VpToken = VpTokenBuilder().apply(block).build()

/**
 * Create a VP Token with a single presentation.
 *
 * @param queryId The credential query ID
 * @param presentation The presentation string
 */
fun vpTokenOf(
    queryId: String,
    presentation: String,
): VpToken = VpToken(mapOf(queryId to listOf(presentation)))

/**
 * Create a VP Token from a map of query IDs to presentations.
 *
 * @param entries Map of credential query IDs to presentation lists
 */
fun vpTokenOf(entries: Map<String, List<String>>): VpToken = VpToken(entries)

/**
 * Create a VP Token from pairs of query IDs to presentations.
 *
 * @param pairs Pairs of (queryId, presentation)
 */
fun vpTokenOf(vararg pairs: Pair<String, String>): VpToken {
    val grouped = pairs.groupBy({ it.first }, { it.second })
    return VpToken(grouped)
}
