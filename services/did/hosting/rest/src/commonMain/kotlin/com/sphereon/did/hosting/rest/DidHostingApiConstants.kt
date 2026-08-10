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

package com.sphereon.did.hosting.rest

/**
 * Constants for the public DID-document hosting surface.
 *
 * did:web / did:webvh documents are served at the root of the host: `/.well-known/did.json` when the
 * DID has no path, or `/<segment>/.../did.json` when it does. The path is encoded in the DID itself as
 * colon-delimited segments, so a request at `/a/b/did.json` maps to the web location `host:a:b`.
 *
 * The router's tail wildcard must be the LAST token, so a single `/{path...}/did.json` pattern is
 * illegal. We therefore enumerate one explicit pattern per depth up to [MAX_PATH_DEPTH]; the literal
 * `did.json` suffix keeps these collision-safe against other root routes.
 */
object DidHostingApiConstants {
    const val DID_JSON_FILE: String = "did.json"
    const val WELL_KNOWN_DID_JSON: String = "/.well-known/$DID_JSON_FILE"

    /** Canonical DID-document media type (W3C DID Core / DID Resolution). */
    const val DID_JSON_MEDIA_TYPE: String = "application/did+json"

    /** Fallback `Cache-Control: max-age` when a provider supplies no method TTL. */
    const val DEFAULT_CACHE_MAX_AGE_SECONDS: Long = 300

    /**
     * Maximum number of did:web path segments served. did:web identifiers are typically shallow
     * (a host plus a handful of segments); deeper DIDs are not served. The descriptor enumerates one
     * pattern per depth, so this bounds the registered route count.
     */
    const val MAX_PATH_DEPTH: Int = 6

    /** The path-parameter names used by the depth patterns, in order: `s1`, `s2`, … */
    val PATH_SEGMENT_PARAMS: List<String> = (1..MAX_PATH_DEPTH).map { "s$it" }

    /**
     * Every URL the did.json endpoint answers: the `.well-known` form (no path) plus one
     * `/{s1}/.../{sN}/did.json` form per depth `1..MAX_PATH_DEPTH`.
     */
    val DID_JSON_PATH_PATTERNS: List<String> =
        buildList {
            add(WELL_KNOWN_DID_JSON)
            for (depth in 1..MAX_PATH_DEPTH) {
                add("/" + (1..depth).joinToString("/") { "{s$it}" } + "/$DID_JSON_FILE")
            }
        }

    object Tags {
        const val DID_HOSTING: String = "DID Hosting"
    }

    object CommandIds {
        // <module>.<service>.<command>
        const val HTTP_GET_DID_JSON: String = "did.hosting.document-get"
    }
}
