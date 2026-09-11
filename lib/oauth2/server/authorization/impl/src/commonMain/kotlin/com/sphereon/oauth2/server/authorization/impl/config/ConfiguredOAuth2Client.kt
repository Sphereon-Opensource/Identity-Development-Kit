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

package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.ResponseType
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.model.ClientType

internal data class ConfiguredOAuth2Client(
    val configKey: String,
    val enabled: Boolean = true,
    val clientId: String,
    val clientSecretId: String? = null,
    val clientName: String? = null,
    val clientType: ClientType = ClientType.CONFIDENTIAL,
    val grantTypes: List<GrantType>,
    val responseTypes: List<ResponseType> = emptyList(),
    val redirectUris: List<String> = emptyList(),
    val allowedScopes: List<String>? = null,
    val defaultAccessTokenAudience: String? = null,
    val allowedAccessTokenAudiences: Set<String> = emptySet(),
    val principalRoles: List<String> = emptyList(),
    val tokenEndpointAuthMethod: ClientAuthenticationMethod = ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
    val tokenEndpointAuthSigningAlg: List<String>? = null,
    val jwks: List<Jwk>? = null,
    val jwksUri: String? = null,
    val requirePkce: Boolean = clientType == ClientType.PUBLIC,
    val requirePushedAuthorizationRequests: Boolean = false,
    val dpopBoundAccessTokens: Boolean = false,
    val accessTokenLifetime: Int = 3600,
    val refreshTokenLifetime: Int? = null,
    val authorizationCodeLifetime: Int = 600,
    val trustedAttesterIssuers: List<String>? = null,
    val trustedAttesterJwks: List<Jwk>? = null,
    val trustedAttesterJwksUris: List<String>? = null,
    val postLogoutRedirectUris: List<String> = emptyList(),
    val frontchannelLogoutUri: String? = null,
    val frontchannelLogoutSessionRequired: Boolean = false,
    val idTokenSignedResponseAlg: String? = null,
    val backchannelLogoutUri: String? = null,
    val backchannelLogoutSessionRequired: Boolean = false,
    val authorizationSignedResponseAlg: String? = null,
    val authorizationEncryptedResponseAlg: String? = null,
    val authorizationEncryptedResponseEnc: String? = null,
    val requestObjectSigningAlg: String? = null,
    val requestUris: List<String> = emptyList(),
    val tlsClientAuthSubjectDn: String? = null,
    val tlsClientAuthSanDns: String? = null,
    val tlsClientAuthSanEmail: String? = null,
    val tlsClientAuthSanIp: String? = null,
    val tlsClientAuthSanUri: String? = null,
    val tlsClientCertificateBoundAccessTokens: Boolean = false,
) {
    fun toClientRegistration(resolvedClientSecret: String?): ClientRegistration =
        ClientRegistration(
            clientId = clientId,
            clientSecret = resolvedClientSecret,
            clientName = clientName,
            clientType = clientType,
            grantTypes = grantTypes,
            responseTypes = responseTypes,
            redirectUris = redirectUris,
            allowedScopes = allowedScopes,
            defaultAccessTokenAudience = defaultAccessTokenAudience,
            allowedAccessTokenAudiences = allowedAccessTokenAudiences,
            principalRoles = principalRoles,
            tokenEndpointAuthMethod = tokenEndpointAuthMethod,
            tokenEndpointAuthSigningAlg = tokenEndpointAuthSigningAlg,
            jwks = jwks,
            jwksUri = jwksUri,
            requirePkce = requirePkce,
            requirePushedAuthorizationRequests = requirePushedAuthorizationRequests,
            dpopBoundAccessTokens = dpopBoundAccessTokens,
            accessTokenLifetime = accessTokenLifetime,
            refreshTokenLifetime = refreshTokenLifetime,
            authorizationCodeLifetime = authorizationCodeLifetime,
            trustedAttesterIssuers = trustedAttesterIssuers,
            trustedAttesterJwks = trustedAttesterJwks,
            trustedAttesterJwksUris = trustedAttesterJwksUris,
            postLogoutRedirectUris = postLogoutRedirectUris,
            frontchannelLogoutUri = frontchannelLogoutUri,
            frontchannelLogoutSessionRequired = frontchannelLogoutSessionRequired,
            idTokenSignedResponseAlg = idTokenSignedResponseAlg,
            backchannelLogoutUri = backchannelLogoutUri,
            backchannelLogoutSessionRequired = backchannelLogoutSessionRequired,
            authorizationSignedResponseAlg = authorizationSignedResponseAlg,
            authorizationEncryptedResponseAlg = authorizationEncryptedResponseAlg,
            authorizationEncryptedResponseEnc = authorizationEncryptedResponseEnc,
            requestObjectSigningAlg = requestObjectSigningAlg,
            requestUris = requestUris,
            tlsClientAuthSubjectDn = tlsClientAuthSubjectDn,
            tlsClientAuthSanDns = tlsClientAuthSanDns,
            tlsClientAuthSanEmail = tlsClientAuthSanEmail,
            tlsClientAuthSanIp = tlsClientAuthSanIp,
            tlsClientAuthSanUri = tlsClientAuthSanUri,
            tlsClientCertificateBoundAccessTokens = tlsClientCertificateBoundAccessTokens,
        )
}
