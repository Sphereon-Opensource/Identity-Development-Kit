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

package com.sphereon.oauth2.server.authorization.impl.command.clientauth

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.server.authorization.command.clientauth.ClientCertificateExtractor
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError

/**
 * Reads the TLS client certificate from a forwarded header set by an upstream TLS-terminating
 * proxy. The IDK convention is the simple `X-Forwarded-Client-Cert` value carrying URL-encoded
 * PEM (one line break decoded as `%0A`); a fallback raw base64 DER is also accepted for
 * deployments that prefer the more compact form.
 *
 * The richer nginx multi-element format (`By=...;Hash=...;Subject=...;Cert="..."`) is
 * intentionally NOT decoded here: deployments using that format MUST normalize the cert into
 * either the URL-encoded PEM or raw base64 form at the proxy layer (or substitute their own
 * extractor). Keeping this implementation narrow avoids parser drift between gateways.
 *
 * Not contributed by default; deployments that run behind a trusted proxy bind this in their
 * AS-side DI module in place of [HttpRequestClientCertificateExtractorImpl] and ensure the
 * proxy is the only path through which the header can be set.
 */
class XForwardedClientCertExtractorImpl(
    /**
     * Header name to read. Defaults to `X-Forwarded-Client-Cert`. Operators using a different
     * gateway-specific header (e.g. `X-SSL-Client-Cert`) override this at construction time.
     */
    private val headerName: String = DEFAULT_HEADER_NAME,
) : ClientCertificateExtractor {
    override suspend fun extractCertificate(request: GenericHttpRequest): IdkResult<ByteArray?, AuthorizationServerError> {
        val raw = findHeader(request, headerName) ?: return Ok(null)

        return try {
            val der = decodeForwardedCert(raw)
            Ok(der)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.InvalidClient(
                    details = "Forwarded client certificate header '$headerName' is not decodable: ${expected.message}",
                ),
            )
        }
    }

    /**
     * Header lookup is case-insensitive because [GenericHttpRequest.headers] is populated by
     * adapters that may preserve case and may not.
     */
    private fun findHeader(
        request: GenericHttpRequest,
        name: String,
    ): String? {
        val lc = name.lowercase()
        return request.headers.entries
            .firstOrNull { it.key.lowercase() == lc }
            ?.value
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Decode the header value. Three accepted shapes:
     *  1. URL-encoded PEM with `%0A` line breaks (nginx default)
     *  2. Plain PEM (already includes BEGIN/END markers)
     *  3. Raw base64 DER (single string, no headers)
     */
    private fun decodeForwardedCert(value: String): ByteArray {
        val decoded =
            if (value.contains('%')) {
                decodePercentEncoded(value)
            } else {
                value
            }
        val trimmed = decoded.trim()
        return if (trimmed.startsWith(BEGIN_CERT_MARKER)) {
            val base64 =
                trimmed
                    .removePrefix(BEGIN_CERT_MARKER)
                    .substringBefore(END_CERT_MARKER)
                    .replace("\\s".toRegex(), "")
            base64.decodeFromBase64()
        } else {
            trimmed.replace("\\s".toRegex(), "").decodeFromBase64()
        }
    }

    /**
     * Minimal RFC 3986 percent decode for the forwarded-cert use case (PEM line breaks only).
     */
    private fun decodePercentEncoded(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val hex = value.substring(i + 1, i + 3)
                val byte = hex.toInt(16).toChar()
                sb.append(byte)
                i += 3
            } else {
                sb.append(c)
                i += 1
            }
        }
        return sb.toString()
    }

    companion object {
        const val DEFAULT_HEADER_NAME: String = "X-Forwarded-Client-Cert"
        private const val BEGIN_CERT_MARKER: String = "-----BEGIN CERTIFICATE-----"
        private const val END_CERT_MARKER: String = "-----END CERTIFICATE-----"
    }
}
