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
import com.sphereon.jsonld.JsonLdError
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.SchemaType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Looks up a JSON Schema by credential type and validates a payload against
 * it.
 *
 * Schemas are sourced from a [JsonLdSchemaRegistry], typically the
 * [DefaultJsonLdSchemaRegistry] which bundles UNTP 0.7.0 schemas (DPP, DCC,
 * DTE, DFR, DIA) at build time. Validation is delegated to
 * `io.github.optimumcode:json-schema-validator` (KMP-native, Draft 2020-12,
 * MIT licensed).
 *
 * Pure algorithm — no DI dependencies. The DI-bound
 * [ValidateJsonLdSchemaServiceCommandImpl] wraps this validator with a
 * SessionExecution adapter.
 */
@Inject
@SingleIn(SessionScope::class)
class JsonLdSchemaValidator(
    private val registry: JsonLdSchemaRegistry,
) {
    /**
     * Validate [input.payload] against the schema registered for
     * [input.credentialType].
     *
     * Returns the schema IRI on success. On schema lookup failure or any
     * validation violation, returns [JsonLdError.JsonSchemaValidationFailed]
     * with one [JsonLdError.JsonSchemaValidationFailed.SchemaViolation] per
     * offending JSON Pointer.
     */
    fun validate(input: ValidateJsonLdSchemaInput): IdkResult<ValidateJsonLdSchemaOutput, JsonLdError> {
        val entry =
            registry.get(input.credentialType)
                ?: return Err(JsonLdError.NoSchemaRegistered(credentialType = input.credentialType))

        val violations = mutableListOf<JsonLdError.JsonSchemaValidationFailed.SchemaViolation>()
        val ok =
            entry.compiled.validate(input.payload) { error ->
                violations.add(
                    JsonLdError.JsonSchemaValidationFailed.SchemaViolation(
                        pointer = error.objectPath.toString(),
                        message = error.message,
                    ),
                )
            }
        return if (ok) {
            Ok(ValidateJsonLdSchemaOutput(schemaUri = entry.schemaUri))
        } else {
            Err(
                JsonLdError.JsonSchemaValidationFailed(
                    schemaUri = entry.schemaUri,
                    violations = violations.toList(),
                ),
            )
        }
    }
}

/**
 * Lookup of bundled JSON Schemas by credential type name.
 *
 * Track A wires this to [DefaultJsonLdSchemaRegistry], which bundles the
 * UNTP 0.7.0 schemas. Custom schemas layer on through Metro multibinding.
 */
interface JsonLdSchemaRegistry {
    /** Look up a schema by `credentialType` (e.g. `"DigitalProductPassport"`). */
    fun get(credentialType: String): JsonLdSchemaEntry?

    /** Every credential type registered. */
    fun listTypes(): Set<String>
}

/**
 * A registered JSON Schema. Carries the IRI it was sourced from, the source
 * document, and a pre-compiled [JsonSchema] used by the validator on every
 * `validate()` call.
 *
 * Construct via [jsonLdSchemaEntry] so that compilation failures surface at
 * registry-load time (typically AppScope startup) rather than silently on
 * every validation. Compilation is deterministic and idempotent, so caching
 * the compiled form per entry eliminates the per-validate parse cost.
 */
data class JsonLdSchemaEntry(
    val schemaUri: String,
    val schema: JsonElement,
    val compiled: JsonSchema,
)

/**
 * Build a [JsonLdSchemaEntry] from a JSON Schema document plus its source
 * IRI, eagerly compiling the schema. Throws [IllegalStateException] if the
 * source is not a valid Draft 2020-12 schema; callers can catch and surface
 * as a typed [JsonLdError] if they want to handle invalid bundled schemas
 * gracefully.
 */
fun jsonLdSchemaEntry(
    schemaUri: String,
    schema: JsonElement
): JsonLdSchemaEntry {
    val compiled =
        try {
            JsonSchema.fromJsonElement(schema, SchemaType.DRAFT_2020_12)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("JSON Schema at <$schemaUri> is not a valid Draft 2020-12 schema", e)
        }
    return JsonLdSchemaEntry(schemaUri = schemaUri, schema = schema, compiled = compiled)
}
