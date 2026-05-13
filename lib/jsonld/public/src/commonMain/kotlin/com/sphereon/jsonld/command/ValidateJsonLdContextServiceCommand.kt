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
 * Validate a credential's `@context` value for UNTP 0.7.0 conformance.
 *
 * Walks the supplied `@context` (which may be a string, an array, or an
 * embedded object) and resolves each remote reference through the configured
 * [com.sphereon.jsonld.loader.LinkedDataDocumentLoader]. Every resolved
 * context is then scanned for the `@vocab` keyword, which UNTP 0.7.0 VCP
 * MUST-NOT permits. A finding is reported as
 * [com.sphereon.jsonld.JsonLdError.InvalidVocabMapping] with the offending
 * location.
 *
 * Loader failures and malformed `@context` shapes surface as the matching
 * [com.sphereon.jsonld.JsonLdError] variants. Recover the typed shape via
 * `IdkError.sourceAs<JsonLdError.X>()`.
 */
interface ValidateJsonLdContextServiceCommand : ServiceCommand<ValidateJsonLdContextInput, ValidateJsonLdContextOutput, IdkError> {
    companion object {
        const val COMMAND_ID = "jsonld.context.validate"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
}

/**
 * @param context the raw `@context` value. May be a [kotlinx.serialization.json.JsonPrimitive]
 *   (string IRI), [kotlinx.serialization.json.JsonArray] of mixed entries, or
 *   [kotlinx.serialization.json.JsonObject] (embedded definition).
 * @param baseIri optional base IRI to resolve relative references against. May
 *   be `null` when every entry is absolute (the UNTP-recommended shape).
 */
@Serializable
data class ValidateJsonLdContextInput(
    val context: JsonElement,
    val baseIri: String? = null,
)

/**
 * @param resolvedContextIris absolute IRIs of every remote `@context` document
 *   the validator dereferenced. Useful for audit trails.
 */
@Serializable
data class ValidateJsonLdContextOutput(
    val resolvedContextIris: List<String>,
)
