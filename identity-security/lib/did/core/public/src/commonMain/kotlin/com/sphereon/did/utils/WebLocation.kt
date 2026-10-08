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

package com.sphereon.did.utils

import com.sphereon.core.compat.JsExportCompat
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * The shared "web location" of a did:web / did:webvh DID: the did:web method-specific
 * identifier (`host[%3Aport][:segment...]`) that both methods map to the same HTTPS
 * document location for.
 *
 * `did:web:example.com:a:b` and `did:webvh:<scid>:example.com:a:b` share the web location
 * `example.com:a:b` — which is precisely why a system that hosts these documents must never
 * let both methods manage the same location. The web location is therefore used as a
 * method/SCID-independent persistence + hosting lookup key (and the basis for cross-method
 * uniqueness).
 *
 * Normalisation:
 * - the host segment is lowercased (DNS is case-insensitive); a `%3A<port>` suffix keeps its
 *   uppercase `%3A` and literal port,
 * - path segments are left byte-for-byte (RFC 3986 path segments are case-sensitive),
 * - the value is the colon-joined method-specific-id form (NOT slash-joined), matching how the
 *   DID itself encodes the location.
 */
@JsExportCompat
object WebLocation {
    private const val DID_WEB_PREFIX = "did:web:"
    private const val DID_WEBVH_PREFIX = "did:webvh:"
    private const val PORT_ENCODED = "%3A"

    /** DID methods whose documents live at a web location. */
    val HOSTABLE_METHODS: Set<String> = setOf("web", "webvh")

    /**
     * The web location for a stored DID, or `null` when [method] is not web/webvh or [did] is
     * not a well-formed identifier for that method.
     *
     * - `web`   → method-specific id verbatim, e.g. `did:web:example.com:a:b` → `example.com:a:b`
     * - `webvh` → method-specific id with the leading SCID segment dropped, e.g.
     *   `did:webvh:Qm…:example.com:a:b` → `example.com:a:b`
     */
    @JvmStatic
    fun fromDid(
        method: String,
        did: String
    ): String? =
        when (method) {
            "web" -> {
                did
                    .takeIf { it.startsWith(DID_WEB_PREFIX) }
                    ?.removePrefix(DID_WEB_PREFIX)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { normalize(it) }
            }

            "webvh" -> {
                did
                    .takeIf { it.startsWith(DID_WEBVH_PREFIX) }
                    ?.removePrefix(DID_WEBVH_PREFIX)
                    ?.substringAfter(":", missingDelimiterValue = "")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { normalize(it) }
            }

            else -> {
                null
            }
        }

    /**
     * The web location for an inbound hosting request.
     *
     * @param host the request authority host (expected already punycode-ASCII at the edge).
     * @param port optional authority port; encoded as `%3A<port>` to mirror the did:web id.
     * @param pathSegments the URL path segments BETWEEN the authority and the trailing
     *   `did.json` / `did.jsonl` filename, already percent-decoded by the routing layer. Empty
     *   for a `/.well-known/...` request.
     */
    @JvmStatic
    @JvmOverloads
    fun fromRequest(
        host: String,
        port: Int? = null,
        pathSegments: List<String> = emptyList(),
    ): String {
        val hostPart =
            if (port != null) {
                "${host.lowercase()}$PORT_ENCODED$port"
            } else {
                host.lowercase()
            }
        return if (pathSegments.isEmpty()) {
            hostPart
        } else {
            hostPart + ":" + pathSegments.joinToString(":")
        }
    }

    private fun normalize(methodSpecificId: String): String {
        val firstColon = methodSpecificId.indexOf(':')
        val hostSeg = if (firstColon < 0) methodSpecificId else methodSpecificId.substring(0, firstColon)
        val rest = if (firstColon < 0) "" else methodSpecificId.substring(firstColon)
        return normalizeHostSegment(hostSeg) + rest
    }

    private fun normalizeHostSegment(hostSeg: String): String {
        val portIdx = hostSeg.indexOf(PORT_ENCODED, ignoreCase = true)
        return if (portIdx < 0) {
            hostSeg.lowercase()
        } else {
            hostSeg.substring(0, portIdx).lowercase() + PORT_ENCODED + hostSeg.substring(portIdx + PORT_ENCODED.length)
        }
    }
}
