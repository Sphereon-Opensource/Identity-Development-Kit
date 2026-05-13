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
import com.sphereon.jsonld.LinkedDataDocument
import kotlinx.serialization.Serializable

/**
 * Resolve a JSON-LD `@context` IRI to its document content via the configured
 * [com.sphereon.jsonld.loader.LinkedDataDocumentLoader] chain.
 *
 * Failure surfaces as an [IdkError] whose `source` is a typed
 * [com.sphereon.jsonld.JsonLdError] (commonly
 * `BuiltInContextNotFound`, `LoadingDocumentFailed`, or `IntegrityPinMismatch`).
 * Recover the typed shape via `IdkError.sourceAs<JsonLdError.X>()`.
 */
interface ResolveContextServiceCommand : ServiceCommand<ResolveContextInput, LinkedDataDocument, IdkError> {
    companion object {
        const val COMMAND_ID = "jsonld.context.resolve"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
}

/**
 * @param iri the absolute IRI of the context to resolve. Callers SHOULD
 *   pre-validate via `Iri.tryParse`; the implementation re-validates and
 *   surfaces `JsonLdError.InvalidIri` on failure.
 */
@Serializable
data class ResolveContextInput(
    val iri: String,
)
