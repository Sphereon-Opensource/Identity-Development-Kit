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

import kotlinx.serialization.json.JsonObject

/**
 * In-memory mapping of `@context` IRI → bundled document content. Backs
 * [BuiltInContextLinkedDataDocumentLoader].
 *
 * The default IDK build ships a registry pre-loaded with the W3C VCDM 2.0,
 * VC Data Integrity, VC JOSE/COSE, and UNTP 0.7.0 (DPP, DCC, DTE, DFR, DIA)
 * contexts so that issuance and verification do not have to make outbound
 * HTTP calls for canonical, well-known schemas. Per the IDK convention
 * `feedback_builtin_vocabs.md`, standard vocabularies ship pre-loaded; users
 * never have to import them explicitly.
 *
 * Implementations MUST be immutable and safe to share across tenants and
 * sessions. The IRI key is matched verbatim; resolution does not normalise
 * trailing slashes or percent-encoding, so the bundled IRI must match the
 * canonical form in the upstream specification.
 *
 * Custom entries (e.g. tenant-specific vocabularies) layer on through Metro
 * multibinding rather than mutating a shared registry.
 */
interface BuiltInContextRegistry {
    /**
     * Look up a bundled context by absolute IRI. Returns the parsed JSON-LD
     * document body, or null if the IRI is not registered.
     */
    fun get(iri: String): JsonObject?

    /** Every IRI registered in the bundle, in unspecified order. */
    fun listIris(): Set<String>
}
