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

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.AuthorizationServerMode
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.PublicClientConfig
import com.sphereon.oauth2.common.config.TokenFormat
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Binds OAuth2 server configuration from IDK's ConfigService.
 *
 * Configuration uses the prefix "oauth2.servers":
 *
 * ```properties
 * sphereon.app.oauth2.servers.default-server=primary
 * sphereon.app.oauth2.servers.primary.mode=HOSTED
 * sphereon.app.oauth2.servers.primary.issuer-template=https://auth.example.com/{tenant-id}
 * sphereon.app.oauth2.servers.primary.base-url=https://auth.example.com
 * sphereon.app.oauth2.servers.primary.access-token-lifetime-seconds=1800
 * sphereon.app.oauth2.servers.primary.revocation=SUPPORTED
 * ```
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OAuth2ServersConfigProvider>())
class OAuth2ServersConfigBinder(
    private val execution: SessionExecution,
) : OAuth2ServersConfigProvider {
    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    private val prefix: String
        get() = OAuth2ServerInstanceConfig.CONFIG_PREFIX

    private val _config: OAuth2ServersConfig by lazy { loadConfig() }

    override fun getConfig(): OAuth2ServersConfig = _config

    override fun getServer(id: String): OAuth2ServerInstanceConfig? = _config.getServer(id)

    override fun getDefaultServer(): OAuth2ServerInstanceConfig = _config.getDefaultServer()

    override fun resolveIssuer(
        serverId: String,
        tenantId: String,
    ): String {
        val server = _config.getServer(serverId) ?: return "http://localhost:8080"
        val issuer = server.issuer
        val template = server.issuerTemplate
        return when {
            issuer != null -> issuer
            template != null -> template.replace("{tenant-id}", tenantId)
            else -> server.baseUrl
        }
    }

    private fun loadConfig(): OAuth2ServersConfig {
        val defaultServer =
            configService.getPropertyAsString(
                "$prefix.default-server",
                "default",
            ) ?: "default"

        // Discover server IDs by scanning known config keys
        val serverIds = discoverServerIds()

        val servers =
            if (serverIds.isEmpty()) {
                mapOf("default" to OAuth2ServerInstanceConfig())
            } else {
                serverIds.associateWith { id -> loadServerConfig(id) }
            }

        return OAuth2ServersConfig(
            defaultServer = defaultServer,
            servers = servers,
        )
    }

    private fun discoverServerIds(): Set<String> {
        val ids = mutableSetOf<String>()

        // Try the default server name
        val defaultId = configService.getPropertyAsString("$prefix.default-server", "default") ?: "default"
        if (hasServerConfig(defaultId)) {
            ids.add(defaultId)
        }

        // Try common server IDs
        for (candidateId in listOf("default", "primary", "keycloak")) {
            if (hasServerConfig(candidateId)) {
                ids.add(candidateId)
            }
        }

        // If no servers found, return empty (will use default)
        return ids
    }

    private fun hasServerConfig(id: String): Boolean {
        // A server config exists if at least one property is set
        return configService.getPropertyAsString("$prefix.$id.mode", null) != null ||
            configService.getPropertyAsString("$prefix.$id.base-url", null) != null ||
            configService.getPropertyAsString("$prefix.$id.issuer", null) != null ||
            configService.getPropertyAsString("$prefix.$id.issuer-template", null) != null
    }

    private fun loadServerConfig(id: String): OAuth2ServerInstanceConfig {
        val serverPrefix = "$prefix.$id"
        val defaults = OAuth2ServerInstanceConfig()

        return OAuth2ServerInstanceConfig(
            mode =
                configService
                    .getPropertyAsString("$serverPrefix.mode", null)
                    ?.let { runCatching { AuthorizationServerMode.valueOf(it.uppercase()) }.getOrNull() }
                    ?: defaults.mode,
            issuerTemplate = configService.getPropertyAsString("$serverPrefix.issuer-template", null),
            issuer = configService.getPropertyAsString("$serverPrefix.issuer", null),
            baseUrl =
                configService.getPropertyAsString("$serverPrefix.base-url", null)
                    ?: defaults.baseUrl,
            accessTokenLifetimeSeconds =
                configService.getProperty(
                    "$serverPrefix.access-token-lifetime-seconds",
                    Int::class,
                    defaults.accessTokenLifetimeSeconds,
                ) ?: defaults.accessTokenLifetimeSeconds,
            refreshTokenLifetimeSeconds =
                configService.getProperty(
                    "$serverPrefix.refresh-token-lifetime-seconds",
                    Int::class,
                    defaults.refreshTokenLifetimeSeconds,
                ) ?: defaults.refreshTokenLifetimeSeconds,
            authorizationCodeLifetimeSeconds =
                configService.getProperty(
                    "$serverPrefix.authorization-code-lifetime-seconds",
                    Int::class,
                    defaults.authorizationCodeLifetimeSeconds,
                ) ?: defaults.authorizationCodeLifetimeSeconds,
            tokenFormat =
                configService
                    .getPropertyAsString("$serverPrefix.token-format", null)
                    ?.let { runCatching { TokenFormat.valueOf(it.uppercase()) }.getOrNull() }
                    ?: defaults.tokenFormat,
            refreshTokenRotation =
                configService.getProperty(
                    "$serverPrefix.refresh-token-rotation",
                    Boolean::class,
                    defaults.refreshTokenRotation,
                ) ?: defaults.refreshTokenRotation,
            grantTypesEnabled =
                configService
                    .getPropertyAsString("$serverPrefix.grant-types-enabled", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet()
                    ?: defaults.grantTypesEnabled,
            responseTypesSupported =
                configService
                    .getPropertyAsString("$serverPrefix.response-types-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet()
                    ?: defaults.responseTypesSupported,
            scopesSupported =
                configService
                    .getPropertyAsString("$serverPrefix.scopes-supported", null)
                    ?.split(",")
                    ?.map { it.trim() },
            // OpenID Connect
            oidc = readFeaturePolicy("$serverPrefix.oidc", defaults.oidc),
            idTokenLifetimeSeconds =
                configService.getProperty(
                    "$serverPrefix.id-token-lifetime-seconds",
                    Int::class,
                    defaults.idTokenLifetimeSeconds,
                ) ?: defaults.idTokenLifetimeSeconds,
            subjectTypesSupported =
                configService
                    .getPropertyAsString("$serverPrefix.subject-types-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?: defaults.subjectTypesSupported,
            claimsSupported =
                configService
                    .getPropertyAsString("$serverPrefix.claims-supported", null)
                    ?.split(",")
                    ?.map { it.trim() },
            userinfoSigningAlgValuesSupported =
                configService
                    .getPropertyAsString("$serverPrefix.userinfo-signing-alg-values-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet(),
            introspection = readFeaturePolicy("$serverPrefix.introspection", defaults.introspection),
            revocation = readFeaturePolicy("$serverPrefix.revocation", defaults.revocation),
            par = readFeaturePolicy("$serverPrefix.par", defaults.par),
            tokenExchange = readFeaturePolicy("$serverPrefix.token-exchange", defaults.tokenExchange),
            pkce = readFeaturePolicy("$serverPrefix.pkce", defaults.pkce),
            dpop = readFeaturePolicy("$serverPrefix.dpop", defaults.dpop),
            pkceMethodsSupported =
                configService
                    .getPropertyAsString("$serverPrefix.pkce-methods-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet()
                    ?: defaults.pkceMethodsSupported,
            tokenEndpointAuthMethodsSupported =
                configService
                    .getPropertyAsString("$serverPrefix.token-endpoint-auth-methods-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet()
                    ?: defaults.tokenEndpointAuthMethodsSupported,
            introspectionEndpointAuthMethodsSupported =
                configService
                    .getPropertyAsString("$serverPrefix.introspection-endpoint-auth-methods-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet()
                    ?: defaults.introspectionEndpointAuthMethodsSupported,
            revocationEndpointAuthMethodsSupported =
                configService
                    .getPropertyAsString("$serverPrefix.revocation-endpoint-auth-methods-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet()
                    ?: defaults.revocationEndpointAuthMethodsSupported,
            dpopSigningAlgValuesSupported =
                configService
                    .getPropertyAsString("$serverPrefix.dpop-signing-alg-values-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet(),
            signingKeyAlias = configService.getPropertyAsString("$serverPrefix.signing-key-alias", null),
            signingAlgorithmsSupported =
                configService
                    .getPropertyAsString("$serverPrefix.signing-algorithms-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet(),
            idTokenSigningAlgValuesSupported =
                configService
                    .getPropertyAsString("$serverPrefix.id-token-signing-alg-values-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet(),
            requestObjectSigningAlgValuesSupported =
                configService
                    .getPropertyAsString("$serverPrefix.request-object-signing-alg-values-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet(),
            // EXTERNAL mode fields
            tokenEndpointAuthMethod = configService.getPropertyAsString("$serverPrefix.token-endpoint-auth-method", null),
            clientId = configService.getPropertyAsString("$serverPrefix.client-id", null),
            clientSecret = configService.getPropertyAsString("$serverPrefix.client-secret", null),
            tokenEndpoint = configService.getPropertyAsString("$serverPrefix.token-endpoint", null),
            introspectionEndpoint = configService.getPropertyAsString("$serverPrefix.introspection-endpoint", null),
            revocationEndpoint = configService.getPropertyAsString("$serverPrefix.revocation-endpoint", null),
            jwksUri = configService.getPropertyAsString("$serverPrefix.jwks-uri", null),
            internalClients = loadInternalClients(serverPrefix),
            publicClients = loadPublicClients(serverPrefix),
        )
    }

    private fun loadInternalClients(serverPrefix: String): Map<String, Pair<String, String>> {
        val clients = mutableMapOf<String, Pair<String, String>>()
        for (role in listOf("issuer", "verifier")) {
            val clientId = configService.getPropertyAsString("$serverPrefix.internal-clients.$role.client-id", null)
            val clientSecret = configService.getPropertyAsString("$serverPrefix.internal-clients.$role.client-secret", null)
            if (clientId != null && clientSecret != null) {
                clients[role] = clientId to clientSecret
            }
        }
        return clients
    }

    private fun loadPublicClients(serverPrefix: String): PublicClientConfig {
        val allowAny =
            configService.getProperty(
                "$serverPrefix.public-clients.allow-any",
                Boolean::class,
                false,
            ) ?: false
        val allowedClientIds =
            configService
                .getPropertyAsString("$serverPrefix.public-clients.allowed-client-ids", null)
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        return PublicClientConfig(allowAny = allowAny, allowedClientIds = allowedClientIds)
    }

    private fun readFeaturePolicy(
        key: String,
        default: FeaturePolicy,
    ): FeaturePolicy =
        configService
            .getPropertyAsString(key, null)
            ?.let { runCatching { FeaturePolicy.valueOf(it.uppercase()) }.getOrNull() }
            ?: default
}
