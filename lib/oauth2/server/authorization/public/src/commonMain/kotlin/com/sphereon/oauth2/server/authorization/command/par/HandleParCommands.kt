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

package com.sphereon.oauth2.server.authorization.command.par

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.server.authorization.command.PushedAuthorizationResponse

/**
 * Args for [HandlePushedAuthorizationRequestCommand]. Carries the form-encoded request body and
 * raw HTTP headers (for client authentication). The optional [baseUrlOverride] feeds JAR audience
 * validation when the AS runs without a pinned `issuer` (RFC 9101 §10.2): the HTTP shell derives
 * the public-facing AS URL from `Host` + `X-Forwarded-Proto` and hands it down so the verifier
 * can match `aud` against the same value the rest of the AS advertises.
 */
data class HandlePushedAuthorizationRequestArgs(
    val requestBody: Map<String, List<String>>,
    val requestHeaders: Map<String, String>,
    val baseUrlOverride: String? = null,
    /**
     * The full URL the PAR request landed on (e.g. `https://as.example.com/par`). Threaded into
     * `verifyClientAuthentication` so attestation-PoP / DPoP audience binding can be checked
     * against this exact URL, per draft-ietf-oauth-attestation-based-client-auth §5.2 and
     * RFC 9449 §4.3.
     */
    val parEndpointUrl: String? = null,
)

/**
 * Orchestration command for `POST /par` (RFC 9126: Pushed Authorization Requests). Extracts
 * client authentication from headers/body, parses and verifies the pushed request, then
 * issues a request_uri via the lower-level
 * [com.sphereon.oauth2.server.authorization.command.CreateRequestUriCommand] and
 * [com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseCommand].
 */
interface HandlePushedAuthorizationRequestCommand : ServiceCommand<HandlePushedAuthorizationRequestArgs, PushedAuthorizationResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.par.handle-pushed-authorization-request"
    }
}
