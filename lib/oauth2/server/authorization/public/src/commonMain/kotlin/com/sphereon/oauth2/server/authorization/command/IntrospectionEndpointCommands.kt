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

package com.sphereon.oauth2.server.authorization.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse

// ============================================================================
// 1. ParseIntrospectionRequestCommand
// ============================================================================

/**
 * Arguments for parsing an introspection request
 */
data class ParseIntrospectionRequestArgs(
    val requestBody: Map<String, List<String>>
)

/**
 * Parse introspection request command
 *
 * RFC 7662 Section 2.1: Introspection Request
 *
 * Parses the token introspection request from the client.
 */
interface ParseIntrospectionRequestCommand : ServiceCommand<ParseIntrospectionRequestArgs, IntrospectionRequestData> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.introspection.parse"
    }
}

// ============================================================================
// 2. IntrospectTokenCommand
// ============================================================================

/**
 * Arguments for introspecting a token (authorization server version)
 */
data class IntrospectTokenArgs(
    val token: String,
    val tokenTypeHint: String? = null,
    val clientId: String
)

/**
 * Introspect token command
 *
 * RFC 7662 Section 2.2: Introspection Response
 *
 * Introspects a token and returns its metadata and validity status.
 * This command:
 * - Validates client authentication
 * - Looks up the token
 * - Checks if token is active (not expired, not revoked)
 * - Returns token metadata
 */
interface IntrospectTokenCommand : ServiceCommand<IntrospectTokenArgs, TokenIntrospectionResponse> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.introspection.evaluate"
    }
}

// ============================================================================
// Data Models
// ============================================================================

/**
 * Introspection request data
 */
data class IntrospectionRequestData(
    /**
     * The token to introspect
     */
    val token: String,

    /**
     * Token type hint (access_token, refresh_token)
     */
    val tokenTypeHint: String? = null,

    /**
     * Client ID (from authentication)
     */
    val clientId: String
)
