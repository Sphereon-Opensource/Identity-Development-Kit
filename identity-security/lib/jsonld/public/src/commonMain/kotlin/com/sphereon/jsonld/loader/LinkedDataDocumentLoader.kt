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

import com.sphereon.core.api.IdkResult
import com.sphereon.jsonld.LinkedDataDocument

/**
 * Resolves a JSON-LD `@context` IRI to its document content.
 *
 * Implementations compose via decoration. Track A ships:
 *
 * - `BuiltInContextLinkedDataDocumentLoader`: classpath-bundled W3C and UNTP
 *   contexts; SHA-256 verified against a manifest.
 * - `CachedLinkedDataDocumentLoader`: tenant-scoped in-memory cache.
 * - `IntegrityPinningLinkedDataDocumentLoader`: enforces a configured
 *   `sha256` digest on the wire before returning.
 * - `HttpLinkedDataDocumentLoader`: fetches via
 *   [com.sphereon.ktor.http.client.provider.HttpClientFactory] with manual
 *   redirect handling; it is an internal production terminator, not an
 *   arbitrary consumer-composable HTTP client.
 * - `DefaultLinkedDataDocumentLoader`: composes the five-stage production
 *   chain in the order BuiltIn → Allowlist → IntegrityPinning → Cached → lazy
 *   Http. The HTTP terminator is constructed only when remote resolution
 *   reaches it; unsupported manual-redirect construction fails closed as a
 *   typed result, while bundled contexts remain available.
 *
 * The HTTP terminator is an internal production detail with manual redirect
 * handling; consumers receive the [LinkedDataDocumentLoader] or the default
 * production composition rather than an arbitrary auto-following client. The
 * default chain constructs that terminator lazily, after bundled, allowlist,
 * integrity, and cache short-circuits have missed. Platforms that cannot
 * expose manual 3xx responses therefore serve bundled contexts and return a
 * typed failure for remote contexts.
 *
 * Implementations MUST be safe to invoke from any [SessionScope] coroutine,
 * MUST NOT block, and MUST surface failure as an [IdkResult] error rather than
 * throwing. When the underlying HTTP/disk operation fails, return
 * [com.sphereon.jsonld.JsonLdError.LoadingDocumentFailed]; when the loader
 * does not recognise the IRI at all, return
 * [com.sphereon.jsonld.JsonLdError.BuiltInContextNotFound] (built-in only) or
 * a structured `LoadingDocumentFailed` (everything else).
 */
fun interface LinkedDataDocumentLoader {
    /**
     * Resolve [iri] to a [LinkedDataDocument].
     *
     * @param iri an absolute IRI per RFC 3987 (callers MUST pre-validate via
     *   `com.sphereon.jsonld.Iri.tryParse`).
     */
    suspend fun loadDocument(iri: String): IdkResult<LinkedDataDocument, com.sphereon.jsonld.JsonLdError>
}
