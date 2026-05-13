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
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.api.json.jcs.Jcs
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.jsonld.JsonLdError
import com.sphereon.jsonld.LinkedDataDocument
import kotlinx.serialization.json.JsonObject

/**
 * Resolves the expected SHA-256 pin for an IRI, or `null` when no pin is
 * configured for that IRI.
 *
 * The pin format is the lowercase hex encoding of the SHA-256 of the JCS
 * (RFC 8785) canonical form of the document content. JCS is used so that
 * pins are stable across upstream whitespace or key-order variation;
 * structural changes still flip the pin.
 *
 * Production wires this through `ConfigService`; tests typically pass a
 * `Map<String, String>` literal.
 */
fun interface IntegrityPinResolver {
    /** Returns the expected SHA-256 hex digest for [iri], or null when unpinned. */
    suspend fun pinFor(iri: String): String?

    companion object {
        /** Convenience: resolver backed by an in-memory map. */
        fun of(pins: Map<String, String>): IntegrityPinResolver = IntegrityPinResolver { iri -> pins[iri] }

        /** A resolver that pins nothing, leaving every IRI unverified. */
        val NONE: IntegrityPinResolver = IntegrityPinResolver { null }
    }
}

/**
 * Decorator that, after [next] returns a document, optionally verifies a
 * SHA-256 pin against the JCS-canonicalized content.
 *
 * Pin semantics:
 * - **No pin** (resolver returns null) → pass through.
 * - **Pin present, JCS-SHA-256 matches** → pass through.
 * - **Pin present, mismatch** → [JsonLdError.IntegrityPinMismatch] (FATAL).
 *
 * The decorator never *adds* trust; it only *removes* it on mismatch. Bundled
 * built-in contexts are not re-verified at runtime: their bytes are pinned at
 * build time by the `generateBundledContexts` Gradle task and cannot drift
 * inside the JAR/klib.
 *
 * Per the IDK convention, a pin mismatch is FATAL, not an ordinary
 * `LoadingDocumentFailed`: an attacker who can serve a different `@context`
 * body could change the meaning of every credential verified against it.
 */
class IntegrityPinningLinkedDataDocumentLoader(
    private val next: LinkedDataDocumentLoader,
    private val pins: IntegrityPinResolver,
) : LinkedDataDocumentLoader {
    override suspend fun loadDocument(iri: String): IdkResult<LinkedDataDocument, JsonLdError> {
        val downstream = next.loadDocument(iri)
        if (downstream.isErr) return downstream

        val expected = pins.pinFor(iri) ?: return downstream
        val doc = downstream.value
        val content = doc.content as? JsonObject ?: return downstream

        val canonical = Jcs.canonicalize(content)
        val actualHex = hash(canonical, DigestAlg.SHA256).encodeToHex()

        return if (actualHex.equals(expected, ignoreCase = true)) {
            downstream
        } else {
            Err(
                JsonLdError.IntegrityPinMismatch(
                    iri = iri,
                    expectedSha256 = expected.lowercase(),
                    actualSha256 = actualHex,
                ),
            )
        }
    }
}
