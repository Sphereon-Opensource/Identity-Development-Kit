package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.common.model.ResponseType
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestData
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.model.ClientType
import com.sphereon.oauth2.server.authorization.provider.CredentialIssuerAudience
import com.sphereon.oauth2.server.authorization.provider.UnregisteredClientAdmission
import com.sphereon.oauth2.server.authorization.provider.UnregisteredClientAdmissionRule
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UnregisteredClientAdmissionRule>())
class Oid4vciUnregisteredWalletAdmissionRule : UnregisteredClientAdmissionRule {
    override suspend fun admit(
        request: AuthorizationRequestData,
        boundCredentialIssuers: List<CredentialIssuerAudience>,
        isPushedAuthorizationRequest: Boolean,
    ): UnregisteredClientAdmission? {
        if (boundCredentialIssuers.isEmpty()) return null
        if (!isPushedAuthorizationRequest && request.requestUri?.startsWith(PAR_REQUEST_URI_PREFIX) != true) return null
        if (request.codeChallenge.isNullOrBlank() || request.codeChallengeMethod != PkceMethod.S256) return null
        if (request.responseType != listOf(ResponseType.CODE)) return null

        val issuerScopes = boundCredentialIssuers.flatMap { it.credentialScopes }.toSet()
        val requestedScopes = request.scope.orEmpty().split(' ').filter(String::isNotBlank).toSet()
        val hasCredentialScope = requestedScopes.any { it in issuerScopes }
        val hasCredentialAuthorizationDetails = request.additionalParameters[AUTHORIZATION_DETAILS]?.let { raw ->
            runCatching {
                Json.parseToJsonElement(raw).jsonArray.any { entry ->
                    entry.jsonObject["type"]?.jsonPrimitive?.content == OPENID_CREDENTIAL
                }
            }.getOrDefault(false)
        } == true
        if (!hasCredentialScope && !hasCredentialAuthorizationDetails) return null
        if (requestedScopes.any { it !in issuerScopes }) return null

        val allowedAudiences = boundCredentialIssuers.map { it.audience }.toSet()
        if (request.resource.orEmpty().any { it !in allowedAudiences }) return null

        return UnregisteredClientAdmission(
            client = ClientRegistration(
                clientId = request.clientId,
                clientType = ClientType.PUBLIC,
                grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
                responseTypes = listOf(ResponseType.CODE),
                redirectUris = emptyList(),
                allowedScopes = issuerScopes.toList(),
                defaultAccessTokenAudience = allowedAudiences.singleOrNull(),
                tokenEndpointAuthMethod = ClientAuthenticationMethod.NONE,
                requirePkce = true,
                requirePushedAuthorizationRequests = true,
            ),
            audiences = allowedAudiences,
        )
    }

    private companion object {
        const val AUTHORIZATION_DETAILS = "authorization_details"
        const val OPENID_CREDENTIAL = "openid_credential"
        const val PAR_REQUEST_URI_PREFIX = "urn:ietf:params:oauth:request_uri:"
    }
}
