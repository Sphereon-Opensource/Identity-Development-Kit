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
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * VP Token (Verifiable Presentation Token) - OpenID4VP 1.0 Final DCQL Format.
 *
 * OpenID4VP 1.0 §8.1 (Response Parameters): when the Authorization Request used
 * `dcql_query`, the `vp_token` is a JSON object whose keys are the DCQL credential-query
 * `id`s and whose values are arrays of one or more Presentations matching that query. When
 * the query's `multiple` property is omitted or false, the array MUST contain exactly one
 * Presentation. A scalar Presentation value is never valid in the DCQL VP Token object.
 *
 * Critically, the *type of each Presentation value is Credential-Format dependent* (§8.1,
 * Appendix B):
 *
 *  - Compact / string formats — the Presentation is a JSON **string**:
 *      - `dc+sd-jwt` (IETF SD-JWT VC, with optional KB-JWT) — Appendix B.4
 *      - `jwt_vc_json` (W3C VC secured as a JWT/JWS) — Appendix B.1
 *      - `mso_mdoc` (base64url-encoded ISO 18013-5 `DeviceResponse`) — Appendix B.3
 *  - W3C Data Integrity formats — the Presentation is a JSON **object**:
 *      - `ldp_vc` (JSON-LD Verifiable Credential or holder-bound Presentation with a Data Integrity proof) — Appendix B.1.3.2
 *
 * Therefore each Presentation is modelled as a [JsonElement] that may be a
 * [JsonPrimitive] (string) OR a [JsonObject]. Code MUST NOT assume the string form and
 * MUST NOT blanket-cast to `jsonPrimitive` — branch on the element type / Credential
 * Format instead.
 *
 * DCQL vp_token wire shapes:
 * ```json
 * { "credential_query_id_1": ["eyJhbGc..."] }               // single compact presentation
 * { "credential_query_id_2": ["eyJhbGc...", "eyJhbGc..."] } // multiple compact presentations
 * { "credential_query_id_3": [{ "@context": [...], ... }] }  // single ldp_vc Presentation (JSON object)
 * ```
 *
 * @property presentationElements Canonical map from credential query ID to the list of
 *   Presentation elements. Each element is a string ([JsonPrimitive]) for compact formats
 *   or a [JsonObject] for `ldp_vc`. Even single presentations are single-element lists.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VpToken", exact = true)
@JsExportCompat
@Serializable(with = VpTokenSerializer::class)
data class VpToken(
    @JsExportIgnoreCompat
    val presentationElements: Map<String, List<JsonElement>>,
) {
    init {
        require(presentationElements.isNotEmpty()) { "VP Token must contain at least one credential query" }
        presentationElements.forEach { (queryId, presentationList) ->
            require(queryId.isNotBlank()) { "Credential query ID cannot be blank" }
            require(presentationList.isNotEmpty()) { "Presentations list for query '$queryId' cannot be empty" }
            presentationList.forEach { presentation ->
                requirePresentationShape(queryId, presentation)
            }
        }
    }

    /**
     * Compact-format view of the presentations: each [JsonElement] is rendered to a [String].
     *
     * String ([JsonPrimitive]) presentations (`dc+sd-jwt`, `jwt_vc_json`, `mso_mdoc`) yield
     * their raw content. Object (`ldp_vc`) presentations are rendered as compact JSON
     * so legacy string-based call sites never crash with a [ClassCastException]; consumers that
     * must verify or inspect an LDP presentation should use [presentationElements] /
     * [getPresentationElements] and branch on the element type instead.
     */
    val presentations: Map<String, List<String>>
        get() = presentationElements.mapValues { (_, list) -> list.map { it.asPresentationString() } }

    /**
     * Get all presentations (as strings) as a flat list (ignoring query IDs).
     */
    val allPresentations: List<String>
        get() = presentationElements.values.flatten().map { it.asPresentationString() }

    /**
     * Get all Presentation elements as a flat list (ignoring query IDs), preserving the
     * JSON shape (string vs object) so format-specific verification can branch correctly.
     */
    val allPresentationElements: List<JsonElement>
        get() = presentationElements.values.flatten()

    /**
     * Get the total number of presentations across all queries.
     */
    val presentationCount: Int
        get() = presentationElements.values.sumOf { it.size }

    /**
     * Get all credential query IDs present in this VP token.
     */
    val queryIds: Set<String>
        get() = presentationElements.keys

    /**
     * Get presentations (as strings) for a specific credential query ID.
     *
     * @param queryId The credential query ID from the DCQL query
     * @return List of presentations for this query, or null if not found
     */
    fun getPresentation(queryId: String): List<String>? = presentationElements[queryId]?.map { it.asPresentationString() }

    /**
     * Get Presentation elements for a specific credential query ID, preserving the JSON shape.
     *
     * @param queryId The credential query ID from the DCQL query
     * @return List of presentation elements for this query, or null if not found
     */
    fun getPresentationElements(queryId: String): List<JsonElement>? = presentationElements[queryId]

    /**
     * Get a single presentation (as string) for a query ID (first one if multiple).
     *
     * @param queryId The credential query ID
     * @return The first presentation for this query, or null if not found
     */
    fun getSinglePresentation(queryId: String): String? = presentationElements[queryId]?.firstOrNull()?.asPresentationString()

    /**
     * Get a single Presentation element for a query ID (first one if multiple), preserving shape.
     *
     * @param queryId The credential query ID
     * @return The first presentation element for this query, or null if not found
     */
    fun getSinglePresentationElement(queryId: String): JsonElement? = presentationElements[queryId]?.firstOrNull()

    companion object {
        /**
         * Construct a [VpToken] from a map of query IDs to compact (string) presentations.
         * Each string is wrapped as a [JsonPrimitive]. Use the primary constructor directly
         * when any presentation is an `ldp_vc` JSON object.
         */
        @JvmStatic
        fun fromStrings(presentations: Map<String, List<String>>): VpToken = VpToken(presentations.mapValues { (_, list) -> list.map { JsonPrimitive(it) } })

        /**
         * Create a VP Token from a JSON element.
         *
         * Parses the DCQL object format where keys are credential query IDs and every value is
         * an array of one or more Presentations. Each Presentation is a string (compact formats)
         * or a JSON object (`ldp_vc`) per OID4VP 1.0 Final section 8.1.
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

            val presentations = mutableMapOf<String, List<JsonElement>>()

            for ((queryId, value) in jsonObject) {
                require(value is JsonArray) {
                    "VP Token value for '$queryId' must be an array of Presentations"
                }
                val presentationList: List<JsonElement> = value.toList()

                require(presentationList.isNotEmpty()) {
                    "VP Token presentations for '$queryId' cannot be empty"
                }
                presentationList.forEach { requirePresentationShape(queryId, it) }

                presentations[queryId] = presentationList
            }

            return VpToken(presentations)
        }

        /**
         * A Presentation value is either a non-blank string (compact formats) or a JSON
         * object (`ldp_vc`). Anything else (number, boolean, null, nested array,
         * blank string) is not a valid OID4VP §8.1 Presentation.
         */
        private fun requirePresentationShape(
            queryId: String,
            element: JsonElement,
        ) {
            when (element) {
                is JsonPrimitive -> {
                    require(element.isString) {
                        "VP Token presentation for '$queryId' must be a string or a JSON object, got a non-string primitive"
                    }
                    require(element.content.isNotBlank()) {
                        "Presentation in query '$queryId' cannot be blank"
                    }
                }

                is JsonObject -> {
                    Unit
                }

                // ldp_vc

                else -> {
                    throw IllegalArgumentException(
                        "VP Token presentation for '$queryId' must be a string or a JSON object, got: ${element::class.simpleName}",
                    )
                }
            }
        }

        /**
         * Convert VP Token to JSON element.
         *
         * Serializes to the OID4VP 1.0 Final DCQL object format. Every credential-query ID
         * maps to an array, including queries with exactly one Presentation.
         *
         * @return JSON object with credential query IDs as keys
         */
        fun VpToken.toJson(): JsonElement {
            val entries =
                presentationElements.mapValues { (_, presentationList) -> JsonArray(presentationList) }
            return JsonObject(entries)
        }

        /**
         * Render a Presentation element to its compact string form: string content for
         * [JsonPrimitive], compact JSON for objects/arrays.
         */
        private fun JsonElement.asPresentationString(): String =
            when (this) {
                is JsonPrimitive -> if (isString) content else toString()
                else -> COMPACT_JSON.encodeToString(JsonElement.serializer(), this)
            }

        private val COMPACT_JSON = Json
    }
}

/**
 * Custom serializer for VpToken that handles the DCQL object format.
 */
object VpTokenSerializer : KSerializer<VpToken> {
    override val descriptor: SerialDescriptor =
        MapSerializer(
            String.serializer(),
            ListSerializer(JsonElement.serializer()),
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
    private val presentations = mutableMapOf<String, MutableList<JsonElement>>()

    /**
     * Add a compact (string) presentation for a credential query ID.
     *
     * @param queryId The credential query ID from the DCQL query
     * @param presentation The compact presentation string
     */
    fun presentation(
        queryId: String,
        presentation: String,
    ) = apply {
        presentations.getOrPut(queryId) { mutableListOf() }.add(JsonPrimitive(presentation))
    }

    /**
     * Add a presentation element (string or `ldp_vc` JSON object) for a query ID.
     */
    fun presentationElement(
        queryId: String,
        presentation: JsonElement,
    ) = apply {
        presentations.getOrPut(queryId) { mutableListOf() }.add(presentation)
    }

    /**
     * Add multiple compact (string) presentations for a credential query ID.
     *
     * @param queryId The credential query ID
     * @param presentationList List of compact presentations
     */
    fun presentations(
        queryId: String,
        presentationList: List<String>,
    ) = apply {
        presentations.getOrPut(queryId) { mutableListOf() }.addAll(presentationList.map { JsonPrimitive(it) })
    }

    /**
     * Add a presentation entry (query ID to compact presentation mapping).
     */
    fun entry(
        queryId: String,
        vararg presentationValues: String,
    ) = apply {
        presentations.getOrPut(queryId) { mutableListOf() }.addAll(presentationValues.map { JsonPrimitive(it) })
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
 * Create a VP Token with a single compact (string) presentation.
 *
 * @param queryId The credential query ID
 * @param presentation The compact presentation string
 */
fun vpTokenOf(
    queryId: String,
    presentation: String,
): VpToken = VpToken.fromStrings(mapOf(queryId to listOf(presentation)))

/**
 * Create a VP Token from a map of query IDs to compact (string) presentations.
 *
 * @param entries Map of credential query IDs to presentation lists
 */
fun vpTokenOf(entries: Map<String, List<String>>): VpToken = VpToken.fromStrings(entries)

/**
 * Create a VP Token from pairs of query IDs to compact (string) presentations.
 *
 * @param pairs Pairs of (queryId, presentation)
 */
fun vpTokenOf(vararg pairs: Pair<String, String>): VpToken {
    val grouped = pairs.groupBy({ it.first }, { it.second })
    return VpToken.fromStrings(grouped)
}
