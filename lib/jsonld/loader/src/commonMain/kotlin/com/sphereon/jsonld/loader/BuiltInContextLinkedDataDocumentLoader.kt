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

package com.sphereon.jsonld.loader

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.jsonld.JsonLdError
import com.sphereon.jsonld.LinkedDataDocument

/**
 * Resolves `@context` IRIs from a [BuiltInContextRegistry] of bundled
 * documents.
 *
 * On hit, returns immediately without touching the network or the cache. On
 * miss, delegates to [next] when configured; otherwise returns
 * [JsonLdError.BuiltInContextNotFound]. Composes as the first decorator in
 * the [DefaultLinkedDataDocumentLoader] chain so that canonical W3C and UNTP
 * contexts are served from the bundle without an HTTP fetch.
 */
class BuiltInContextLinkedDataDocumentLoader(
    private val registry: BuiltInContextRegistry,
    private val next: LinkedDataDocumentLoader? = null,
) : LinkedDataDocumentLoader {
    override suspend fun loadDocument(iri: String): IdkResult<LinkedDataDocument, JsonLdError> {
        val content = registry.get(iri)
        if (content != null) {
            return Ok(
                LinkedDataDocument(
                    documentUrl = iri,
                    content = content,
                    contentType = "application/ld+json",
                ),
            )
        }
        return next?.loadDocument(iri) ?: Err(JsonLdError.BuiltInContextNotFound(iri = iri))
    }
}
