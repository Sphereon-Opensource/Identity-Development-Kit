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

package com.sphereon.oauth2.server.authorization.command.introspection

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse

/**
 * Args for [HandleIntrospectionRequestCommand]. Carries the form-encoded request body, raw HTTP
 * headers (for client authentication), and the full URL of the `/introspect` endpoint (for DPoP
 * `htu` binding).
 */
data class HandleIntrospectionRequestArgs(
    val requestBody: Map<String, List<String>>,
    val requestHeaders: Map<String, String>,
    val httpUrl: String,
)

/**
 * Orchestration command for `POST /introspect` (RFC 7662: Token Introspection). Authenticates
 * the caller before doing anything else (RFC 7662 §2.1) and delegates the introspection lookup
 * to the lower-level [com.sphereon.oauth2.server.authorization.command.IntrospectTokenCommand].
 */
interface HandleIntrospectionRequestCommand : ServiceCommand<HandleIntrospectionRequestArgs, TokenIntrospectionResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.introspection.handle-introspection-request"
    }
}
