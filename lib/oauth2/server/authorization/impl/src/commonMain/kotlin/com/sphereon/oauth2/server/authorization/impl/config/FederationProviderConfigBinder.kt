package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig

/**
 * Binds federation provider configuration from properties.
 *
 * Properties are expected at:
 * ```
 * {namespace}.federation.providers.{name}.issuer-url=...
 * {namespace}.federation.providers.{name}.client-id=...
 * {namespace}.federation.providers.{name}.client-secret=...
 * {namespace}.federation.providers.{name}.scopes=openid,profile,email
 * {namespace}.federation.providers.{name}.identifier-claim-name=sub
 * {namespace}.federation.providers.{name}.enabled=true
 * ```
 */
class FederationProviderConfigBinder(
    private val configService: PropertyResolver
) {
    fun bind(): List<FederationProviderConfig> {
        val namespace = (configService as? ConfigService)?.getNamespace() ?: "sphereon.app"
        val prefix = "$namespace.federation.providers"
        val providerNames = configService.getPropertyAsString("$prefix.names", null)
            ?.split(",")?.map { it.trim() }
            ?: return emptyList()

        return providerNames.mapNotNull { name ->
            val providerPrefix = "$prefix.$name"
            val issuerUrl = configService.getPropertyAsString("$providerPrefix.issuer-url", null)
                ?: return@mapNotNull null
            val clientId = configService.getPropertyAsString("$providerPrefix.client-id", null)
                ?: return@mapNotNull null

            FederationProviderConfig(
                id = name,
                name = configService.getPropertyAsString("$providerPrefix.name", name) ?: name,
                issuerUrl = issuerUrl,
                clientId = clientId,
                clientSecret = configService.getPropertyAsString("$providerPrefix.client-secret", null),
                scopes = configService.getPropertyAsString("$providerPrefix.scopes", null)
                    ?.split(",")?.map { it.trim() }
                    ?: listOf("openid", "profile", "email"),
                identifierClaimName = configService.getPropertyAsString("$providerPrefix.identifier-claim-name", "sub") ?: "sub",
                enabled = configService.getPropertyAsString("$providerPrefix.enabled", "true")?.toBoolean() != false,
                authorizationEndpointOverride = configService.getPropertyAsString("$providerPrefix.authorization-endpoint-override", null)?.ifBlank { null },
                tokenEndpointOverride = configService.getPropertyAsString("$providerPrefix.token-endpoint-override", null),
                userinfoEndpointOverride = configService.getPropertyAsString("$providerPrefix.userinfo-endpoint-override", null),
                callbackPath = configService.getPropertyAsString("$providerPrefix.callback-path", "/federation/callback") ?: "/federation/callback"
            )
        }
    }
}
