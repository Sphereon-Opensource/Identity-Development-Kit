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

package com.sphereon.oauth2.server.authorization.command.revocation

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand

/**
 * Args for [HandleRevocationRequestCommand]. Carries the form-encoded request body, raw HTTP
 * headers (for client authentication), and the full URL of the `/revoke` endpoint (for DPoP
 * `htu` binding).
 */
data class HandleRevocationRequestArgs(
    val requestBody: Map<String, List<String>>,
    val requestHeaders: Map<String, String>,
    val httpUrl: String,
)

/**
 * Orchestration command for `POST /revoke` (RFC 7009: Token Revocation). Authenticates the
 * caller, parses the revocation request, and delegates revocation to the lower-level
 * [com.sphereon.oauth2.server.authorization.command.RevokeTokenCommand].
 */
interface HandleRevocationRequestCommand : ServiceCommand<HandleRevocationRequestArgs, Unit, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.revocation.handle-revocation-request"
    }
}
