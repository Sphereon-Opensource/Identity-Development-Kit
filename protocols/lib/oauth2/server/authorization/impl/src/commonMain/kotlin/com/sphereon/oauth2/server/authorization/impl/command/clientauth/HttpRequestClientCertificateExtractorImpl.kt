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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.server.authorization.command.clientauth.ClientCertificateExtractor
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Reads the TLS client certificate from [GenericHttpRequest.clientCertificateChain]. Used when
 * the AS terminates TLS itself: the platform HTTP adapter (e.g. the Ktor adapter) populates the
 * chain from the engine's `ClientCertificatePrincipal`, leaf-first DER.
 *
 * No header-based proxy decoding here; if a deployment runs behind a TLS-terminating proxy it
 * binds the proxy-aware extractor instead.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ClientCertificateExtractor>())
class HttpRequestClientCertificateExtractorImpl : ClientCertificateExtractor {
    override suspend fun extractCertificate(request: GenericHttpRequest): IdkResult<ByteArray?, AuthorizationServerError> {
        val leaf = request.clientCertificateChain?.firstOrNull()
        return Ok(leaf)
    }
}
