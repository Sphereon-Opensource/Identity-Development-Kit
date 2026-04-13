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
import com.sphereon.oauth2.server.resource.model.ResourceRequest
import com.sphereon.oauth2.server.resource.model.VerifiedResourceRequest
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

/**
 * Arguments for ValidateAccessTokenCommand
 *
 * @property request The HTTP request containing Authorization and optional DPoP headers
 * @property requiredScope Required scope for this resource (optional, comma/space-separated)
 * @property requiredAudience Required audience for this resource (optional)
 */
data class ValidateAccessTokenArgs(
    val request: ResourceRequest,
    val requiredScope: String? = null,
    val requiredAudience: String? = null
)

/**
 * Command: Validate Access Token
 *
 * Validates an access token from an HTTP request according to RFC 6750 (Bearer)
 * and RFC 9449 (DPoP).
 *
 * This is the primary entry point for resource server authentication.
 *
 * **Flow**:
 * 1. Extract Authorization header (Bearer or DPoP)
 * 2. Extract DPoP proof header (if DPoP scheme)
 * 3. Validate token signature and claims (JWT or introspection)
 * 4. Validate DPoP proof and binding (if applicable)
 * 5. Return verified request with token payload
 *
 * **Security considerations**:
 * - MUST validate token signature and expiration
 * - MUST validate audience claim matches this resource server
 * - MUST validate DPoP binding for DPoP-bound tokens
 * - MUST validate DPoP proof for DPoP authentication scheme
 * - SHOULD cache validated tokens to reduce authorization server load
 * - SHOULD implement replay protection for DPoP proofs
 *
 * @see ResourceRequest Input HTTP request
 * @see VerifiedResourceRequest Output verified request
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ValidateAccessTokenCommand", exact = true)
//@JsExportCompat
interface ValidateAccessTokenCommand : ServiceCommand<ValidateAccessTokenArgs, VerifiedResourceRequest> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.resource.validatetoken"
    }
}
