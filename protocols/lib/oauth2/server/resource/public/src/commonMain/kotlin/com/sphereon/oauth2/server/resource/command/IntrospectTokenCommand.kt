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

package com.sphereon.oauth2.server.resource.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.oauth2.server.resource.model.TokenPayload
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Arguments for IntrospectTokenCommand
 *
 * @property token The access token string (JWT or opaque)
 * @property authorizationServer The authorization server URL
 */
@JsExportCompat
data class IntrospectTokenArgs(
    val token: String,
    val authorizationServer: String,
)

/**
 * Command: Introspect Token
 *
 * Introspects an access token using RFC 7662 (OAuth 2.0 Token Introspection).
 *
 * This command is used when:
 * - Token is not a JWT (opaque token)
 * - JWT verification fails (e.g., unknown issuer, missing public key)
 * - Resource server prefers centralized token validation
 *
 * **Flow**:
 * 1. Send HTTP POST to authorization server's introspection endpoint
 * 2. Authenticate as resource server (client credentials)
 * 3. Receive introspection response (active, sub, scope, etc.)
 * 4. Validate response claims (active=true, audience, scope)
 *
 * **Caching**:
 * - SHOULD cache introspection results to reduce authorization server load
 * - Cache key: Token string (or hash)
 * - Cache TTL: Short (e.g., 60 seconds) to allow revocation detection
 * - MUST NOT cache inactive tokens (active=false)
 *
 * **Security considerations**:
 * - MUST authenticate to introspection endpoint (client credentials)
 * - MUST use HTTPS for introspection requests
 * - MUST validate introspection response (active=true)
 * - SHOULD implement rate limiting to prevent abuse
 * - Cache TTL creates revocation detection delay - balance performance vs security
 *
 * @see TokenPayload.Introspection Output introspection payload
 */

@OptIn(ExperimentalObjCName::class)
@ObjCName("IntrospectTokenCommand", exact = true)
@JsExportCompat
interface IntrospectTokenCommand : ServiceCommand<IntrospectTokenArgs, TokenPayload.Introspection, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.resource.introspect"
    }
}
