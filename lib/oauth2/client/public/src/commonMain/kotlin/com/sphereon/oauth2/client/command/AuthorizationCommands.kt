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

package com.sphereon.oauth2.client.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.oauth2.client.model.AuthorizationRequestUrlResult
import com.sphereon.oauth2.common.model.AuthorizationErrorResponse
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import kotlin.jvm.JvmOverloads

/**
 * Source of an authorization response — controls how [ParseAuthorizationResponseArgs.redirectUrl]
 * is interpreted.
 */
@JsExportCompat
enum class AuthorizationResponseSource {
    /** Response parameters appended to the redirect URI as query string. */
    QUERY,

    /** Response parameters delivered as an `application/x-www-form-urlencoded` POST body. */
    FORM_POST,

    /** Response parameters appended as URL fragment. Not supported for the first OIDF pass. */
    FRAGMENT,
}

@JsExportCompat
data class ParseAuthorizationResponseArgs
    @JvmOverloads
    constructor(
        /**
         * Location of the response.
         * - [AuthorizationResponseSource.QUERY]: full redirect URL with query parameters.
         * - [AuthorizationResponseSource.FORM_POST]: redirect URI (no query), with [formBody] set.
         * - [AuthorizationResponseSource.FRAGMENT]: full redirect URL (only for future use).
         */
        val redirectUrl: String,
        val source: AuthorizationResponseSource = AuthorizationResponseSource.QUERY,
        /** Raw `application/x-www-form-urlencoded` body — required when [source] is FORM_POST. */
        val formBody: String? = null,
    )

/**
 * Command for parsing an authorization response redirect URL
 *
 * Parses the query parameters from the redirect URL and validates them
 * as either a success response (with code) or error response.
 */
@JsExportCompat
interface ParseAuthorizationResponseCommand : ServiceCommand<ParseAuthorizationResponseArgs, ParsedAuthorizationResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.authresponse.parse"
    }
}

/**
 * Result of parsing authorization response
 *
 * Can be either a success response with code or an error response
 */
sealed interface ParsedAuthorizationResponse {
    data class Success(
        val response: AuthorizationResponse,
    ) : ParsedAuthorizationResponse

    data class Error(
        val response: AuthorizationErrorResponse,
    ) : ParsedAuthorizationResponse
}

/**
 * Options for creating authorization request URL
 */
@JsExportCompat
data class CreateAuthorizationRequestUrlOptions
    @JvmOverloads
    constructor(
        /**
         * Authorization server metadata
         */
        val authorizationServerMetadata: AuthorizationServerMetadata,
        /**
         * Base authorization request parameters
         */
        val authorizationRequest: AuthorizationRequest,
        /**
         * Optional PKCE code verifier
         * If not provided and PKCE is supported, one will be generated
         */
        val pkceCodeVerifier: String? = null,
        /**
         * Client authentication configuration for PAR requests
         * Required if the authorization server requires client authentication for PAR
         */
        val clientAuthentication: ClientAuthenticationConfig? = null,
    /*
     * TODO: DPoP options (Phase 3)
     * val dpopOptions: DpopOptions? = null
     */
    )

/**
 * Command for creating an authorization request URL
 *
 * This is a complex operation that:
 * 1. Checks if PKCE is supported and generates challenge if needed
 * 2. Checks if PAR is required/supported and pushes request if needed
 * 3. Builds the final authorization URL with all parameters
 */
@JsExportCompat
interface CreateAuthorizationRequestUrlCommand : ServiceCommand<CreateAuthorizationRequestUrlOptions, AuthorizationRequestUrlResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.authrequest.create"
    }
}
