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

package com.sphereon.jsonld

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A JSON-LD document obtained by a [com.sphereon.jsonld.loader.LinkedDataDocumentLoader].
 *
 * Mirrors the `RemoteDocument` shape defined by the W3C JSON-LD 1.1 Processing
 * API, simplified for IDK use:
 *
 * - [documentUrl] is the IRI the document was loaded from. After redirects this
 *   is the final URL.
 * - [contextUrl] is set only when the document is plain JSON served with a
 *   `Link: rel="http://www.w3.org/ns/json-ld#context"` header that references
 *   an external `@context`. For `application/ld+json` responses this is null.
 * - [content] is the parsed JSON payload as a [JsonElement] (typically a
 *   [kotlinx.serialization.json.JsonObject]).
 * - [contentType] is the IANA media type as reported by the source, e.g.
 *   `"application/ld+json"`. Null when the source is the built-in registry or
 *   any other in-memory loader.
 * - [profile] is the optional `profile` parameter of the media type.
 */
@JsExportCompat
@Serializable
data class LinkedDataDocument(
    val documentUrl: String,
    val content: JsonElement,
    val contextUrl: String? = null,
    val contentType: String? = null,
    val profile: String? = null,
) {
    /** Convenience: [documentUrl] parsed as an [Iri], or null if malformed. */
    fun documentIri(): Iri? = Iri.tryParse(documentUrl)

    /** Convenience: [contextUrl] parsed as an [Iri], or null when absent or malformed. */
    fun contextIri(): Iri? = contextUrl?.let { Iri.tryParse(it) }
}
