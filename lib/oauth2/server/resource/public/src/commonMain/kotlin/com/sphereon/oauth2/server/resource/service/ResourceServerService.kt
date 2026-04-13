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

package com.sphereon.oauth2.server.resource.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.oauth2.server.resource.command.IntrospectTokenCommand
import com.sphereon.oauth2.server.resource.command.ValidateAccessTokenArgs
import com.sphereon.oauth2.server.resource.command.ValidateAccessTokenCommand
import com.sphereon.oauth2.server.resource.command.VerifyDpopProofCommand
import com.sphereon.oauth2.server.resource.command.VerifyJwtCommand
import com.sphereon.oauth2.server.resource.model.ResourceRequest
import com.sphereon.oauth2.server.resource.model.VerifiedResourceRequest
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * OAuth2 Resource Server Service
 *
 * High-level service for validating access tokens and protecting resources.
 *
 * This service orchestrates the various commands to validate access tokens
 * according to RFC 6750 (Bearer), RFC 9068 (JWT Access Tokens), and
 * RFC 9449 (DPoP).
 *
 * **Typical usage**:
 * ```kotlin
 * val request = ResourceRequest(
 *     method = "GET",
 *     url = "https://api.example.com/resource",
 *     headers = mapOf("Authorization" to "Bearer xxx")
 * )
 *
 * resourceServerService.validateRequest(
 *     request = request,
 *     requiredScope = "read:resource",
 *     requiredAudience = "https://api.example.com"
 * ).fold(
 *     success = { verified ->
 *         // Access granted - proceed with resource access
 *         val subject = verified.tokenPayload.sub
 *         // ...
 *     },
 *     failure = { error ->
 *         // Access denied - return 401 Unauthorized with WWW-Authenticate header
 *         // ...
 *     }
 * )
 * ```
 *
 * **Configuration**:
 * - Authorization server URLs and metadata
 * - Token validation strategy (JWT-first, introspection-only, etc.)
 * - Cache configuration (TTL, size)
 * - DPoP configuration (replay protection window)
 *
 * **Delegation**:
 * This service delegates to command interfaces for actual implementation:
 * - ValidateAccessTokenCommand: Main validation orchestration
 * - VerifyJwtCommand: JWT signature and claims verification
 * - IntrospectTokenCommand: Token introspection (RFC 7662)
 * - VerifyDpopProofCommand: DPoP proof verification (RFC 9449)
 */

@OptIn(ExperimentalObjCName::class)
@ObjCName("ResourceServerService", exact = true)
interface ResourceServerService {
    /**
     * Command: Validate Access Token
     */
    val validateAccessTokenCommand: ValidateAccessTokenCommand

    /**
     * Command: Verify JWT
     */
    val verifyJwtCommand: VerifyJwtCommand

    /**
     * Command: Introspect Token
     */
    val introspectTokenCommand: IntrospectTokenCommand

    /**
     * Command: Verify DPoP Proof
     */
    val verifyDpopProofCommand: VerifyDpopProofCommand

    /**
     * Validates an incoming HTTP request
     *
     * Convenience method that delegates to ValidateAccessTokenCommand.
     *
     * @param request The HTTP request containing Authorization and optional DPoP headers
     * @param requiredScope Required scope for this resource (optional)
     * @param requiredAudience Required audience for this resource (optional)
     * @return Verified request with token payload, or error
     */
    suspend fun validateRequest(
        request: ResourceRequest,
        requiredScope: String? = null,
        requiredAudience: String? = null,
    ): IdkResult<VerifiedResourceRequest, IdkError> =
        validateAccessTokenCommand.execute(
            ValidateAccessTokenArgs(
                request = request,
                requiredScope = requiredScope,
                requiredAudience = requiredAudience,
            ),
        )
}
