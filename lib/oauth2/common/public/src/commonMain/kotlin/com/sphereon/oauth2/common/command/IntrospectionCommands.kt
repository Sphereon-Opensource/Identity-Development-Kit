package com.sphereon.oauth2.common.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata

/**
 * Arguments for introspecting an OAuth 2.0 token
 *
 * @property authorizationServerMetadata The authorization server metadata
 * @property token The token to introspect
 * @property clientAuthentication The client authentication configuration
 * @property tokenTypeHint Optional hint about the type of token
 * @property additionalParameters Additional parameters to include in the request
 */
data class IntrospectTokenArgs(
    val authorizationServerMetadata: AuthorizationServerMetadata,
    val token: String,
    val clientAuthentication: ClientAuthenticationConfig,
    val tokenTypeHint: String? = null,
    val additionalParameters: Map<String, String> = emptyMap()
)

/**
 * Command to introspect an OAuth 2.0 token.
 * RFC 7662 - OAuth 2.0 Token Introspection
 *
 * The introspection endpoint allows a resource server to query the
 * authorization server about the state and metadata of a token.
 */
interface IntrospectTokenCommand : ServiceCommand<IntrospectTokenArgs, TokenIntrospectionResponse> {
    override val commandId: String get() = COMMAND_ID
    companion object {
        const val COMMAND_ID = "oauth2.token.introspect"
    }
}
