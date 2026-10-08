/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.client.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlCommand
import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlOptions
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseCommand
import com.sphereon.oauth2.client.command.ParsedAuthorizationResponse
import com.sphereon.oauth2.client.model.AuthorizationRequestUrlResult
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Service for OAuth 2.0 authorization operations
 *
 * Provides:
 * - Creating authorization request URLs (with PKCE and PAR support)
 * - Parsing authorization response redirect URLs
 *
 * This service follows the Command/Service pattern, delegating to command implementations
 * for testability and consistency with other IDK services.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationService", exact = true)
interface AuthorizationService {
    /**
     * Provides access to the underlying commands for advanced usage scenarios
     */
    val commands: Commands

    /**
     * Container for all authorization commands
     */
    interface Commands {
        val createAuthorizationRequestUrl: CreateAuthorizationRequestUrlCommand
        val parseAuthorizationResponse: ParseAuthorizationResponseCommand
    }

    /**
     * Create authorization request URL
     *
     * This operation:
     * 1. Generates PKCE challenge if supported by the authorization server
     * 2. Pushes authorization request (PAR) if required/supported
     * 3. Builds the final authorization URL with all parameters
     *
     * @param options Options for creating the authorization request URL
     * @return IdkResult containing the authorization URL and associated data (PKCE, DPoP nonce)
     */
    suspend fun createAuthorizationRequestUrl(options: CreateAuthorizationRequestUrlOptions): IdkResult<AuthorizationRequestUrlResult, IdkError>

    /**
     * Parse authorization response from redirect URL
     *
     * Extracts and validates the query parameters from the redirect URL.
     * Returns either a success response (with code) or error response.
     *
     * @param redirectUrl The full redirect URL with query parameters
     * @return IdkResult containing parsed authorization response (success or error)
     */
    suspend fun parseAuthorizationResponse(redirectUrl: String): IdkResult<ParsedAuthorizationResponse, IdkError>
}
