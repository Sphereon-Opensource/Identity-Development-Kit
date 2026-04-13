/*
 * © 2025 Sphereon International B.V.
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

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.server.resource.model.TokenPayload
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

/**
 * Arguments for VerifyJwtCommand
 *
 * @property jwt The JWT string (compact serialization)
 * @property authorizationServer The expected issuer (authorization server URL)
 * @property expectedAudience The expected audience (this resource server)
 */
data class VerifyJwtArgs(
    val jwt: String,
    val authorizationServer: String,
    val expectedAudience: String? = null,
    val jwksUri: String? = null
)

/**
 * Command: Verify JWT Access Token
 *
 * Verifies a JWT access token according to RFC 9068 (JWT Profile for OAuth 2.0 Access Tokens).
 *
 * **Verification steps**:
 * 1. Parse JWT (decode header and payload)
 * 2. Validate JWT signature using authorization server's public key (JWKS)
 * 3. Validate standard claims:
 *    - typ header MUST be "at+jwt"
 *    - iss (issuer) MUST match authorization server
 *    - exp (expiration) MUST be in the future
 *    - aud (audience) MUST include this resource server
 * 4. Extract DPoP binding (cnf.jkt) if present
 *
 * **Caching**:
 * - SHOULD cache JWT verification results until expiration
 * - Cache key: JWT string (or hash)
 * - Cache invalidation: Token expiration time
 *
 * **Security considerations**:
 * - MUST validate signature before trusting any claims
 * - MUST validate issuer to prevent token substitution attacks
 * - MUST validate audience to prevent token reuse across resource servers
 * - MUST validate expiration to prevent replay of expired tokens
 * - SHOULD use time window for expiration checks (clock skew tolerance)
 *
 * @see TokenPayload.Jwt Output JWT payload
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyJwtCommand", exact = true)
//@JsExportCompat
interface VerifyJwtCommand : ServiceCommand<VerifyJwtArgs, TokenPayload.Jwt> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.resource.verifyjwt"
    }
}
