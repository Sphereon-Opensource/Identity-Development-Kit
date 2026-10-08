package com.sphereon.oauth2.server.authorization.provider

import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestData
import com.sphereon.oauth2.server.authorization.model.ClientRegistration

data class UnregisteredClientAdmission(
    val client: ClientRegistration,
    val audiences: Set<String>,
)

interface UnregisteredClientAdmissionRule {
    suspend fun admit(
        request: AuthorizationRequestData,
        boundCredentialIssuers: List<CredentialIssuerAudience>,
        isPushedAuthorizationRequest: Boolean = false,
    ): UnregisteredClientAdmission?
}
