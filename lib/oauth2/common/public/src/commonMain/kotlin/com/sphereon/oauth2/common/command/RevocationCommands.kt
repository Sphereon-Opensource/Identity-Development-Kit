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

package com.sphereon.oauth2.common.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig

/**
 * Arguments for revoking an OAuth 2.0 token against an external AS.
 *
 * @property authorizationServerMetadata The authorization server metadata (for endpoint discovery)
 * @property token The token to revoke
 * @property clientAuthentication The client authentication configuration
 * @property tokenTypeHint Optional hint about the type of token ("access_token" or "refresh_token")
 * @property revocationEndpointOverride Optional explicit endpoint URL (overrides metadata discovery)
 */
data class ClientRevokeTokenArgs(
    val authorizationServerMetadata: AuthorizationServerMetadata? = null,
    val token: String,
    val clientAuthentication: ClientAuthenticationConfig,
    val tokenTypeHint: String? = null,
    val revocationEndpointOverride: String? = null
)

/**
 * Command to revoke an OAuth 2.0 token against an external authorization server.
 * RFC 7009 - OAuth 2.0 Token Revocation
 *
 * The revocation endpoint allows a client to notify the AS that a previously
 * obtained token is no longer needed. Per RFC 7009, the AS responds with
 * HTTP 200 regardless of whether the revocation was successful.
 */
interface ClientRevokeTokenCommand : ServiceCommand<ClientRevokeTokenArgs, Unit> {
    override val commandId: String get() = COMMAND_ID
    companion object {
        const val COMMAND_ID = "oauth2.client.revocation.execute"
    }
}
