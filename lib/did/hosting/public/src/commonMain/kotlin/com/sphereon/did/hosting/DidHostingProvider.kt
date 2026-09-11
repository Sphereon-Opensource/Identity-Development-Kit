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
 *
 */

package com.sphereon.did.hosting

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * A DID document a hosting provider serves at a web location.
 *
 * Phase A serves the `did.json` representation only: for did:web the stored document verbatim,
 * for did:webvh the did:web companion translation (SCID stripped, references rewritten, plus an
 * `alsoKnownAs` back to the did:webvh). The verifiable log (`did.jsonl`) and witness file are a
 * later phase.
 *
 * @property json the canonical DID document JSON (minified; no defaults, no nulls).
 * @property method the DID method that manages the location (`web` / `webvh`) — informational.
 * @property deactivated when `true` the DID was deactivated and the host should answer `410 Gone`.
 * @property cacheMaxAgeSeconds optional `Cache-Control: max-age` hint from the method's cache TTL.
 */
data class HostedDid(
    val json: String,
    val method: String,
    val deactivated: Boolean = false,
    val cacheMaxAgeSeconds: Long? = null,
)

/**
 * SPI a DID method contributes to expose its persisted documents over HTTP hosting.
 *
 * Providers are discovered via DI multibinding (`Set<DidHostingProvider>`) so the hosting
 * service depends on no method module directly — a method opts into hosting purely by
 * contributing one. Only did:web and did:webvh provide one today; a future hostable method
 * adds an implementation and is picked up automatically.
 */
interface DidHostingProvider {
    /** The DID method this provider hosts (e.g. `web`, `webvh`). */
    val method: String

    /**
     * Authority precedence when multiple providers can host the same web location.
     *
     * Persisted tenant DID state must win over a service-local document synthesized from runtime
     * configuration. The default keeps existing providers as fallbacks while an authoritative
     * repository-backed provider can opt into a higher value.
     */
    val authorityPriority: Int
        get() = 0

    /**
     * Resolves the `did.json` for [webLocation] within [tenantId], or `Ok(null)` if this provider
     * does not manage that location. [webLocation] is the normalised did:web method-specific id
     * shared by web and webvh (see [com.sphereon.did.utils.WebLocation]). A storage/translation
     * failure returns `Err`.
     */
    suspend fun resolveDidJson(
        tenantId: String?,
        webLocation: String,
    ): IdkResult<HostedDid?, IdkError>
}
