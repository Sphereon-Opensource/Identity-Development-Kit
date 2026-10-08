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

package com.sphereon.did.methods.webvh

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.idn.Idna

/**
 * Maps a `did:webvh:` DID identifier to and from its HTTPS resolver URL per
 * spec §3.1 (Path-to-URL mapping).
 *
 * Rules:
 * - Strip `did:webvh:` then drop the SCID segment.
 * - Domain segment up to first `:`. Decode `%3A<port>` if present. Apply
 *   IDNA2008 punycode via `:lib:core:idn:public`. Reject literal IPs.
 * - Percent-encode each remaining `:`-separated path segment (RFC 3986).
 * - Path empty -> `https://{host}[:{port}]/.well-known/did.jsonl`.
 * - Path non-empty -> `https://{host}[:{port}]/{joined}/did.jsonl`.
 * - Witness file -> trailing `did.jsonl` replaced with `did-witness.json`.
 */
object WebvhDidUrlBuilder {
    private const val DID_PREFIX = "did:webvh:"
    private const val DID_WEB_PREFIX = "did:web:"
    private const val LOG_FILE = "did.jsonl"
    private const val WELL_KNOWN_LOG = "/.well-known/did.jsonl"
    private const val WITNESS_FILE = "did-witness.json"
    private const val DID_WEB_FILE = "did.json"
    private const val PORT_ENCODED = "%3A"
    private const val MIN_PORT = 1
    private const val MAX_PORT = 65535
    private const val IPV4_OCTETS = 4
    private const val IPV4_OCTET_MAX = 255
    private const val NIBBLE_BITS = 4
    private const val LOW_NIBBLE_MASK = 0x0F
    private const val BYTE_MASK = 0xFF
    private val HEX = "0123456789ABCDEF".toCharArray()

    /**
     * Result of decomposing a `did:webvh` DID. The [host] is the punycode
     * (ASCII) form ready for use in an HTTPS URL.
     */
    data class Decomposed(
        val scid: String,
        val host: String,
        val port: Int?,
        val pathSegments: List<String>,
    )

    fun decompose(did: String): IdkResult<Decomposed, IdkError> {
        if (!did.startsWith(DID_PREFIX)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Not a did:webvh DID: $did"))
        }
        val withoutPrefix = did.substring(DID_PREFIX.length)
        val segments = withoutPrefix.split(":")
        if (segments.size < 2) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "did:webvh requires SCID and host: $did"))
        }
        val scid = segments[0]
        if (scid.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "did:webvh SCID is blank: $did"))
        }

        val rawHostAndPort = segments[1]
        val (rawHost, port) =
            parseHostAndPort(rawHostAndPort)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid did:webvh host segment: '$rawHostAndPort'"))
        if (looksLikeIp(rawHost)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "did:webvh host MUST NOT be a literal IP: $rawHost"))
        }

        val asciiHost = Idna.toAscii(rawHost).getOrElseErr { return Err(it) }
        val tail = segments.drop(2)
        return Ok(Decomposed(scid = scid, host = asciiHost, port = port, pathSegments = tail))
    }

    /** Resolver URL for the log file (`did.jsonl` or `/.well-known/did.jsonl`). */
    fun toLogUrl(did: String): IdkResult<String, IdkError> {
        val parts = decompose(did).getOrElseErr { return Err(it) }
        return Ok(buildBaseUrl(parts) + logPath(parts.pathSegments))
    }

    /** Witness file URL (sibling to the log URL, named `did-witness.json`). */
    fun toWitnessUrl(did: String): IdkResult<String, IdkError> {
        val parts = decompose(did).getOrElseErr { return Err(it) }
        val base = buildBaseUrl(parts)
        return Ok(base + witnessPath(parts.pathSegments))
    }

    /**
     * URL for the optional companion `did:web` document (`did.json`),
     * sibling to `did.jsonl` per webvh v1.0 §"Publishing a Parallel did:web DID".
     * Resolves to `https://<host>[:<port>]/.well-known/did.json` when the DID
     * has no path, or `https://<host>[:<port>]/<path>/did.json` otherwise.
     */
    fun toDidWebDocumentUrl(did: String): IdkResult<String, IdkError> {
        val parts = decompose(did).getOrElseErr { return Err(it) }
        return Ok(buildBaseUrl(parts) + didWebPath(parts.pathSegments))
    }

    /**
     * Companion `did:web` identifier derived by stripping the SCID. Per spec:
     * `did:webvh:<scid>:<host>[:<path>]` -> `did:web:<host>[:<path>]`. The
     * host is already in punycode-ASCII form via [decompose].
     */
    fun toDidWebDid(webvhDid: String): IdkResult<String, IdkError> {
        val parts = decompose(webvhDid).getOrElseErr { return Err(it) }
        val hostSegment =
            if (parts.port != null) {
                "${parts.host}$PORT_ENCODED${parts.port}"
            } else {
                parts.host
            }
        val tail =
            if (parts.pathSegments.isEmpty()) {
                ""
            } else {
                ":" + parts.pathSegments.joinToString(":")
            }
        return Ok("$DID_WEB_PREFIX$hostSegment$tail")
    }

    /**
     * Build a `did:webvh` DID identifier from its components. Used by the
     * provider after computing the SCID.
     */
    fun toDid(
        scid: String,
        host: String,
        port: Int? = null,
        pathSegments: List<String> = emptyList()
    ): String {
        val hostSegment =
            if (port != null) {
                "$host$PORT_ENCODED$port"
            } else {
                host
            }
        val tail =
            if (pathSegments.isEmpty()) {
                ""
            } else {
                ":" + pathSegments.joinToString(":")
            }
        return "$DID_PREFIX$scid:$hostSegment$tail"
    }

    private fun parseHostAndPort(raw: String): Pair<String, Int?>? {
        if (raw.isBlank()) {
            return null
        }
        val portIdx = raw.indexOf(PORT_ENCODED)
        if (portIdx < 0) {
            return raw to null
        }
        val host = raw.substring(0, portIdx)
        val portStr = raw.substring(portIdx + PORT_ENCODED.length)
        val port = portStr.toIntOrNull() ?: return null
        if (port !in MIN_PORT..MAX_PORT) {
            return null
        }
        return host to port
    }

    private fun looksLikeIp(host: String): Boolean {
        val isIpv4 =
            host.split('.').let { parts ->
                parts.size == IPV4_OCTETS && parts.all { p -> p.toIntOrNull()?.let { v -> v in 0..IPV4_OCTET_MAX } == true }
            }
        val isIpv6Bracketed = host.startsWith("[") && host.endsWith("]")
        return isIpv4 || isIpv6Bracketed
    }

    private fun buildBaseUrl(parts: Decomposed): String {
        val portPart =
            if (parts.port != null) {
                ":${parts.port}"
            } else {
                ""
            }
        return "https://${parts.host}$portPart"
    }

    private fun logPath(pathSegments: List<String>): String =
        if (pathSegments.isEmpty()) {
            WELL_KNOWN_LOG
        } else {
            "/" + pathSegments.joinToString("/") { percentEncodePathSegment(it) } + "/" + LOG_FILE
        }

    private fun witnessPath(pathSegments: List<String>): String =
        if (pathSegments.isEmpty()) {
            "/.well-known/$WITNESS_FILE"
        } else {
            "/" + pathSegments.joinToString("/") { percentEncodePathSegment(it) } + "/" + WITNESS_FILE
        }

    private fun didWebPath(pathSegments: List<String>): String =
        if (pathSegments.isEmpty()) {
            "/.well-known/$DID_WEB_FILE"
        } else {
            "/" + pathSegments.joinToString("/") { percentEncodePathSegment(it) } + "/" + DID_WEB_FILE
        }

    /**
     * Percent-encode a path segment per RFC 3986: only unreserved and a few
     * sub-delims pass through; everything else is `%HH`-encoded.
     */
    internal fun percentEncodePathSegment(segment: String): String {
        val out = StringBuilder()
        for (byte in segment.encodeToByteArray()) {
            val b = byte.toInt() and BYTE_MASK
            val ch = b.toChar()
            val unreserved = ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~'
            if (unreserved) {
                out.append(ch)
            } else {
                out.append('%')
                out.append(HEX[b ushr NIBBLE_BITS])
                out.append(HEX[b and LOW_NIBBLE_MASK])
            }
        }
        return out.toString()
    }
}

private inline fun <V, E> IdkResult<V, E>.getOrElseErr(onErr: (E) -> Nothing): V =
    if (isOk) {
        value
    } else {
        onErr(error)
    }
