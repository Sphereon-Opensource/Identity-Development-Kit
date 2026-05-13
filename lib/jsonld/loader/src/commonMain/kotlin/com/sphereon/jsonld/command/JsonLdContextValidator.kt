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

package com.sphereon.jsonld.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.di.session.SessionScope
import com.sphereon.jsonld.Iri
import com.sphereon.jsonld.JsonLdError
import com.sphereon.jsonld.loader.LinkedDataDocumentLoader
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pure-algorithm context validator for UNTP 0.7.0 `@vocab` MUST-NOT.
 *
 * Stateless apart from the injected [loader]. Lives in `commonMain` and is
 * directly instantiable in tests, separate from the
 * [TypedServiceCommandAdapter]-derived
 * [ValidateJsonLdContextServiceCommandImpl] so the algorithm can be exercised
 * without a `SessionExecution`.
 *
 * The companion command impl wraps this validator; both share this code path.
 *
 * Recursion is capped at [MAX_DEPTH]; legitimate UNTP credentials nest at
 * most a couple of levels.
 */
@Inject
@SingleIn(SessionScope::class)
class JsonLdContextValidator(
    private val loader: LinkedDataDocumentLoader,
    private val maxDepth: Int = MAX_DEPTH,
) {
    /**
     * Validate [input] in full. Resolves remote `@context` references through
     * [loader] and rejects any embedded `@vocab` keyword in the chain.
     *
     * Returns the absolute IRIs of every remote document the validator
     * dereferenced. Failure surfaces as a typed [JsonLdError] (callers
     * typically wrap via `IdkError.fromDTO`).
     */
    suspend fun validate(input: ValidateJsonLdContextInput): IdkResult<ValidateJsonLdContextOutput, JsonLdError> {
        val baseIri = input.baseIri?.let { Iri.tryParse(it) }
        val resolved = mutableListOf<String>()
        val outcome =
            visit(
                value = input.context,
                location = "$.@context",
                baseIri = baseIri,
                depth = 0,
                resolved = resolved,
            )
        if (outcome.isErr) return Err(outcome.error)
        return Ok(ValidateJsonLdContextOutput(resolvedContextIris = resolved.toList()))
    }

    private suspend fun visit(
        value: JsonElement,
        location: String,
        baseIri: Iri?,
        depth: Int,
        resolved: MutableList<String>,
    ): IdkResult<Unit, JsonLdError> {
        if (depth > maxDepth) {
            return Err(
                JsonLdError.InvalidLocalContext(
                    location = location,
                    reason = "@context nesting exceeds maximum depth of $maxDepth",
                ),
            )
        }
        return when (value) {
            is JsonPrimitive -> visitRemote(value, location, baseIri, depth, resolved)
            is JsonArray -> visitArray(value, location, baseIri, depth, resolved)
            is JsonObject -> visitObject(value, location)
        }
    }

    private suspend fun visitRemote(
        value: JsonPrimitive,
        location: String,
        baseIri: Iri?,
        depth: Int,
        resolved: MutableList<String>,
    ): IdkResult<Unit, JsonLdError> {
        if (!value.isString) {
            return Err(
                JsonLdError.InvalidLocalContext(location = location, reason = "expected string IRI"),
            )
        }
        val raw = value.content
        val parsed =
            Iri.tryParse(raw)
                ?: return Err(JsonLdError.InvalidIri(rawValue = raw))
        val absolute = if (parsed.isAbsolute || baseIri == null) parsed else baseIri.resolve(parsed)
        resolved.add(absolute.value)

        val loaded = loader.loadDocument(absolute.value)
        if (loaded.isErr) return Err(loaded.error)

        val nested =
            loaded.value.content as? JsonObject
                ?: return Err(
                    JsonLdError.InvalidLocalContext(
                        location = location,
                        reason = "loaded document <${absolute.value}> is not a JSON object",
                    ),
                )
        val innerContext = nested["@context"] ?: return Ok(Unit)
        return visit(innerContext, "$location > <${absolute.value}>", absolute, depth + 1, resolved)
    }

    private suspend fun visitArray(
        value: JsonArray,
        location: String,
        baseIri: Iri?,
        depth: Int,
        resolved: MutableList<String>,
    ): IdkResult<Unit, JsonLdError> {
        for ((index, entry) in value.withIndex()) {
            val outcome = visit(entry, "$location[$index]", baseIri, depth, resolved)
            if (outcome.isErr) return outcome
        }
        return Ok(Unit)
    }

    private fun visitObject(
        value: JsonObject,
        location: String
    ): IdkResult<Unit, JsonLdError> {
        if (value.containsKey("@vocab")) {
            return Err(JsonLdError.InvalidVocabMapping(location = location))
        }
        return Ok(Unit)
    }

    companion object {
        const val MAX_DEPTH: Int = 8
    }
}
