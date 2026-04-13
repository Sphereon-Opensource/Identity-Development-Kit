package com.sphereon.oauth2.server.authorization.config

/**
 * Configuration for an upstream federated identity provider.
 *
 * Used when the authorization server acts as an STS (Security Token Service)
 * fronting an upstream IdP like Keycloak, SURF, or any OIDC Provider.
 */
data class FederationProviderConfig(
    val id: String,
    val name: String,
    val issuerUrl: String,
    val clientId: String,
    val clientSecret: String? = null,
    val scopes: List<String> = listOf("openid", "profile", "email"),
    val identifierClaimName: String = "sub",
    val enabled: Boolean = true,
    val authorizationEndpointOverride: String? = null,
    val tokenEndpointOverride: String? = null,
    val userinfoEndpointOverride: String? = null,
    val callbackPath: String = "/federation/callback"
)
