package com.sphereon.oauth2.client.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.model.AuthorizationErrorResponse
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.client.model.AuthorizationRequestUrlResult
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig

data class ParseAuthorizationResponseArgs(val redirectUrl: String)

/**
 * Command for parsing an authorization response redirect URL
 *
 * Parses the query parameters from the redirect URL and validates them
 * as either a success response (with code) or error response.
 */
interface ParseAuthorizationResponseCommand : ServiceCommand<ParseAuthorizationResponseArgs, ParsedAuthorizationResponse> {
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
    data class Success(val response: AuthorizationResponse) : ParsedAuthorizationResponse
    data class Error(val response: AuthorizationErrorResponse) : ParsedAuthorizationResponse
}

/**
 * Options for creating authorization request URL
 */
data class CreateAuthorizationRequestUrlOptions(
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

    /**
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
interface CreateAuthorizationRequestUrlCommand : ServiceCommand<CreateAuthorizationRequestUrlOptions, AuthorizationRequestUrlResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.authrequest.create"
    }
}
