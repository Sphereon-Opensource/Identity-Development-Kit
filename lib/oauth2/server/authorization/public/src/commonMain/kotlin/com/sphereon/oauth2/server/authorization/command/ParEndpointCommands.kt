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

package com.sphereon.oauth2.server.authorization.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig

// ============================================================================
// ParsePushedAuthorizationRequestCommand
// ============================================================================

/**
 * Arguments for parsing a pushed authorization request
 */
data class ParsePushedAuthorizationRequestArgs(
    val requestBody: Map<String, List<String>>,
    val clientAuthentication: ClientAuthenticationConfig,
)

/**
 * Parse pushed authorization request command
 *
 * RFC 9126: Pushed Authorization Requests (PAR)
 *
 * Parses the pushed authorization request from the client.
 * PAR allows clients to push authorization request parameters to the AS
 * via a direct HTTP POST before redirecting the user.
 */
interface ParsePushedAuthorizationRequestCommand : ServiceCommand<ParsePushedAuthorizationRequestArgs, AuthorizationRequestData> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.par.parse"
    }
}

// ============================================================================
// VerifyPushedAuthorizationRequestCommand
// ============================================================================

/**
 * Arguments for verifying a pushed authorization request
 */
data class VerifyPushedAuthorizationRequestArgs(
    val request: AuthorizationRequestData,
    val clientId: String,
)

/**
 * Verify pushed authorization request command
 *
 * RFC 9126 Section 3: Pushed Authorization Request
 *
 * Verifies the pushed authorization request including:
 * - Client authentication
 * - Request parameter validation
 * - JAR (if present)
 */
interface VerifyPushedAuthorizationRequestCommand : ServiceCommand<VerifyPushedAuthorizationRequestArgs, VerifiedAuthorizationRequest> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.par.verify"
    }
}

// ============================================================================
// CreateRequestUriCommand
// ============================================================================

/**
 * Create request URI command
 *
 * RFC 9126 Section 2.2: Successful Response
 *
 * Generates a unique request_uri that references the stored authorization request.
 * The request_uri is short-lived (typically 90 seconds) and single-use.
 */
interface CreateRequestUriCommand : ServiceCommand<VerifiedAuthorizationRequest, RequestUriData> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.par.createuri"
    }
}

// ============================================================================
// CreatePushedAuthorizationResponseCommand
// ============================================================================

/**
 * Arguments for creating a pushed authorization response
 */
data class CreatePushedAuthorizationResponseArgs(
    val requestUri: String,
    val expiresIn: Int = 90,
)

/**
 * Create pushed authorization response command
 *
 * RFC 9126 Section 2.2: Successful Response
 *
 * Creates the PAR response containing the request_uri and expires_in.
 */
interface CreatePushedAuthorizationResponseCommand : ServiceCommand<CreatePushedAuthorizationResponseArgs, PushedAuthorizationResponse> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.par.respond"
    }
}

// ============================================================================
// RetrieveAuthorizationRequestByUriCommand
// ============================================================================

/**
 * Arguments for retrieving an authorization request by request URI
 */
data class RetrieveByRequestUriArgs(
    val requestUri: String,
)

/**
 * Retrieve authorization request by URI command
 *
 * RFC 9126 Section 2.3: Authorization Request
 *
 * Retrieves the stored authorization request using the request_uri parameter.
 * The request_uri is single-use and expires quickly.
 *
 * Note: The output may be null if the request_uri is not found or expired.
 * The nullable semantics are handled at the implementation level.
 */
interface RetrieveAuthorizationRequestByUriCommand : ServiceCommand<RetrieveByRequestUriArgs, VerifiedAuthorizationRequest> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.par.retrieve"
    }
}

// ============================================================================
// Data Models
// ============================================================================

/**
 * Request URI data
 */
data class RequestUriData(
    /**
     * The request_uri value
     * Format: urn:ietf:params:oauth:request_uri:<unique-id>
     */
    val requestUri: String,
    /**
     * Expiration time in seconds
     * RFC 9126 recommends a short lifetime (e.g., 90 seconds)
     */
    val expiresIn: Int = 90,
    /**
     * Associated authorization request
     */
    val authorizationRequest: VerifiedAuthorizationRequest,
)

/**
 * Pushed authorization response
 *
 * RFC 9126 Section 2.2: Successful Response
 */
data class PushedAuthorizationResponse(
    /**
     * The request URI
     */
    val requestUri: String,
    /**
     * Expiration time in seconds
     */
    val expiresIn: Int,
)
