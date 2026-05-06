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

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand

// ============================================================================
// 1. ParseRevocationRequestCommand
// ============================================================================

/**
 * Arguments for parsing a revocation request
 */
data class ParseRevocationRequestArgs(
    val requestBody: Map<String, List<String>>,
)

/**
 * Parse revocation request command
 *
 * RFC 7009 Section 2.1: Revocation Request
 *
 * Parses the token revocation request from the client.
 */
interface ParseRevocationRequestCommand : ServiceCommand<ParseRevocationRequestArgs, RevocationRequestData, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.revocation.parse"
    }
}

// ============================================================================
// 2. RevokeTokenCommand
// ============================================================================

/**
 * Arguments for revoking a token
 */
data class RevokeTokenArgs(
    val token: String,
    val tokenTypeHint: String? = null,
    val clientId: String,
)

/**
 * Revoke token command
 *
 * RFC 7009 Section 2: Token Revocation
 *
 * Revokes an access or refresh token. Per RFC 7009:
 * - Always returns success (even for invalid/unknown tokens)
 * - Revocation of refresh tokens cascades to associated access tokens
 * - Client must be authorized to revoke the token
 */
interface RevokeTokenCommand : ServiceCommand<RevokeTokenArgs, Unit, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.revocation.execute"
    }
}

// ============================================================================
// Data Models
// ============================================================================

/**
 * Revocation request data
 */
data class RevocationRequestData(
    val token: String,
    val tokenTypeHint: String? = null,
    val clientId: String,
)
