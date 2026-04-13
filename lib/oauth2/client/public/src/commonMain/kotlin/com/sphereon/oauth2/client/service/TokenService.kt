package com.sphereon.oauth2.client.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Service for OAuth 2.0 token endpoint operations
 *
 * Provides access to:
 * - Token exchange for multiple grant types
 *
 * Supports grant types:
 * - authorization_code (RFC 6749 Section 4.1.3)
 * - refresh_token (RFC 6749 Section 6)
 * - client_credentials (RFC 6749 Section 4.4.2)
 * - pre-authorized_code (OpenID4VCI)
 *
 * This service follows the Command/Service pattern, delegating to command implementations
 * for testability and consistency with other IDK services.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TokenService", exact = true)
@JsExportCompat
interface TokenService {

    /**
     * Provides access to the underlying commands for advanced usage scenarios
     */
    val commands: Commands

    /**
     * Container for all token commands
     */
    @JsExportIgnoreCompat
    interface Commands {
        val exchangeToken: ExchangeTokenCommand
    }

    /**
     * Exchanges a token at the token endpoint
     *
     * @param tokenEndpoint The token endpoint URL (HTTPS URL)
     * @param request The token request with grant-specific parameters
     * @return IdkResult containing token response or error
     */
    suspend fun exchangeToken(
        tokenEndpoint: String,
        request: TokenRequest
    ): IdkResult<TokenResponse, IdkError>
}
