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

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Validate a credential payload against the JSON Schema registered for its
 * type.
 *
 * Track A bundles UNTP 0.7.0 schemas (`DigitalProductPassport`,
 * `ConformityCredential`, `DigitalFacilityRecord`, `DigitalIdentityAnchor`,
 * `RegisteredIdentity`) plus a generic VCDM 2.0 envelope schema. The
 * implementation resolves the schema by [ValidateJsonLdSchemaInput.credentialType]
 * and applies it to [ValidateJsonLdSchemaInput.payload].
 *
 * Failures surface as [com.sphereon.jsonld.JsonLdError.JsonSchemaValidationFailed]
 * with one [com.sphereon.jsonld.JsonLdError.JsonSchemaValidationFailed.SchemaViolation]
 * entry per offending JSON Pointer. Recover via
 * `IdkError.sourceAs<JsonLdError.JsonSchemaValidationFailed>()`.
 */
interface ValidateJsonLdSchemaServiceCommand : ServiceCommand<ValidateJsonLdSchemaInput, ValidateJsonLdSchemaOutput, IdkError> {
    companion object {
        const val COMMAND_ID = "jsonld.schema.validate"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
}

/**
 * @param payload the credential body to validate. Typically a
 *   [kotlinx.serialization.json.JsonObject].
 * @param credentialType the credential `type` to resolve a schema for, e.g.
 *   `"DigitalProductPassport"`. The implementation looks this up in the
 *   bundled schema registry; an unrecognised value yields
 *   [com.sphereon.jsonld.JsonLdError.JsonSchemaValidationFailed] with a single
 *   "schema not found" violation rather than a separate error variant, so
 *   callers handle "no schema bundled" and "schema mismatch" through a single
 *   path.
 */
@Serializable
data class ValidateJsonLdSchemaInput(
    val payload: JsonElement,
    val credentialType: String,
)

/**
 * @param schemaUri the IRI of the schema that was applied. Useful for audit
 *   trails and for distinguishing which version of a UNTP schema validated a
 *   given credential.
 */
@Serializable
data class ValidateJsonLdSchemaOutput(
    val schemaUri: String,
)
