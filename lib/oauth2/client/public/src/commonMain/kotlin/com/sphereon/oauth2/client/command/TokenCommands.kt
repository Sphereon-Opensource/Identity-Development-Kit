package com.sphereon.oauth2.client.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.common.model.TokenResponse

/**
 * Arguments for exchanging a token at the token endpoint
 *
 * @property tokenEndpoint The token endpoint URL (HTTPS URL)
 * @property request The token request with grant-specific parameters
 */
data class ExchangeTokenArgs(
    val tokenEndpoint: String,
    val request: TokenRequest
)

/**
 * Command for exchanging tokens at the token endpoint
 *
 * Implements RFC 6749 OAuth 2.0 token endpoint with support for:
 * - authorization_code grant (RFC 6749 Section 4.1.3)
 * - refresh_token grant (RFC 6749 Section 6)
 * - client_credentials grant (RFC 6749 Section 4.4.2)
 * - pre-authorized_code grant (OpenID4VCI)
 *
 * Includes support for:
 * - PKCE verification (RFC 7636)
 * - Resource Indicators (RFC 8707)
 * - DPoP (RFC 9449)
 */
interface ExchangeTokenCommand : ServiceCommand<ExchangeTokenArgs, TokenResponse> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.token.exchange"
    }
}
