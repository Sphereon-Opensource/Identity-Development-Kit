package com.sphereon.oauth2.common.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationResult

/**
 * Arguments for applying client authentication to HTTP requests
 *
 * @property config The client authentication configuration
 * @property tokenEndpoint The token endpoint URL (used for JWT audience)
 */
data class ApplyClientAuthenticationArgs(
    val config: ClientAuthenticationConfig,
    val tokenEndpoint: String
)

/**
 * Command for applying client authentication to HTTP requests
 *
 * Implements OAuth 2.0 client authentication methods:
 * - client_secret_basic (RFC 6749 Section 2.3.1)
 * - client_secret_post (RFC 6749 Section 2.3.1)
 * - client_secret_jwt (RFC 7523)
 * - private_key_jwt (RFC 7523)
 * - none (public clients)
 * - attest_jwt_client_auth (draft spec)
 *
 * The command takes a ClientAuthenticationConfig and returns headers and body parameters
 * that should be added to the HTTP request.
 */
interface ApplyClientAuthenticationCommand : ServiceCommand<ApplyClientAuthenticationArgs, ClientAuthenticationResult> {
    override val commandId: String get() = COMMAND_ID
    companion object {
        const val COMMAND_ID = "oauth2.clientauth.apply"
    }
}
