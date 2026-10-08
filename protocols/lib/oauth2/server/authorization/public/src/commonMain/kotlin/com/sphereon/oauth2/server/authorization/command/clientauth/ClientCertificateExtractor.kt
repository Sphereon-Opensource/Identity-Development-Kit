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

package com.sphereon.oauth2.server.authorization.command.clientauth

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError

/**
 * Pulls the TLS client certificate associated with an HTTP request so RFC 8705 client
 * authentication and the `cnf.x5t#S256` access-token binding can act on it.
 *
 * Two implementations are wired in [com.sphereon.oauth2.server.authorization.impl]:
 *  - the direct extractor reads from [GenericHttpRequest.clientCertificateChain], populated by
 *    a Ktor (or other) adapter when the AS terminates TLS itself with `verifyClient = true`.
 *  - the proxy extractor reads from a forwarded header (e.g. `X-Forwarded-Client-Cert`) so the
 *    AS can sit behind a TLS-terminating front door.
 *
 * Returns:
 *  - `Ok(null)` when no cert is present (the common case for non-mTLS requests).
 *  - `Ok(der bytes)` when a cert is present and decodable as DER.
 *  - `Err(AuthorizationServerError.InvalidClient)` when the cert source is present but
 *    syntactically invalid (e.g. malformed forwarded-cert header), so the caller can reject the
 *    request before attempting auth-method dispatch.
 */
interface ClientCertificateExtractor {
    suspend fun extractCertificate(request: GenericHttpRequest): IdkResult<ByteArray?, AuthorizationServerError>
}
