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

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.ResponseType
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.model.ClientType

/**
 * Permissive-fallback resolver for public clients that are NOT in the client registry but match
 * the `publicClients.allowAny` / `publicClients.allowedClientIds` config. Gated behind
 * [com.sphereon.oauth2.common.config.PublicClientConfig.permissiveRedirectUri] .
 *
 * Single source of truth for both [VerifyAuthorizationRequestCommandImpl] and the authorization
 * endpoint's error-routing helper in `OAuth2Handlers`, so a change to the synthesised
 * registration (scope, auth method, required PKCE) automatically propagates to both. Returns
 * `null` when the fallback is disabled or the client is not on the allow-list.
 */
internal fun resolvePublicClientFallback(
    clientId: String,
    serversConfigProvider: OAuth2ServersConfigProvider,
): ClientRegistration? {
    val pc = serversConfigProvider.serverConfig.publicClients
    if (!pc.permissiveRedirectUri) return null
    if (!pc.allowAny && clientId !in pc.allowedClientIds) return null

    return ClientRegistration(
        clientId = clientId,
        clientType = ClientType.PUBLIC,
        grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
        responseTypes = listOf(ResponseType.CODE),
        redirectUris = emptyList(),
        tokenEndpointAuthMethod = ClientAuthenticationMethod.NONE,
        requirePkce = true,
    )
}
