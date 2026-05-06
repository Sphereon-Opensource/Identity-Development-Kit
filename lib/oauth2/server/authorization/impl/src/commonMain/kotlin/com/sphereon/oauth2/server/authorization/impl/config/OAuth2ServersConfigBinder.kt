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
import com.sphereon.oauth2.common.config.SessionConfig
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
 * sphereon.app.oauth2.servers.primary.issuer=https://auth.example.com
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
        val server =
            _config.getServer(serverId)
                ?: error("OAuth2 server '$serverId' not found in configuration")
        val issuer = server.issuer
        val template = server.issuerTemplate
        return when {
            issuer != null -> {
                issuer
            }

            template != null -> {
                template.replace("{tenant-id}", tenantId)
            }

            else -> {
                error(
                    "OAuth2 server '$serverId' has no issuer configured; " +
                        "set oauth2.servers.$serverId.issuer or oauth2.servers.$serverId.issuer-template",
                )
            }
        }
    }

    private fun loadConfig(): OAuth2ServersConfig {
        val defaultServer =
            configService.getPropertyAsString(
                "$prefix.default-server",
                "default",
            ) ?: "default"

        // Scan the oauth2.servers.* keyspace to find every configured server id.
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

    /**
     * Discovers configured server ids by scanning every property under the `oauth2.servers.`
     * keyspace and collecting the first path segment of each stripped key.
     *
     * For a property map of the form
     * ```
     * oauth2.servers.default-server = production
     * oauth2.servers.production.issuer = https://auth.example.com
     * oauth2.servers.production.mode  = HOSTED
     * oauth2.servers.auth-eu.issuer   = https://auth.eu.example.com
     * ```
     * the scan strips the `oauth2.servers.` prefix, takes the first dotted segment of each
     * stripped key (`default-server`, `production`, `production`, `auth-eu`), deduplicates, and
     * filters out the reserved [DEFAULT_SERVER_KEY] segment (which selects the default server,
     * not a server named `default-server`). The remaining set is the discovered server ids.
     *
     * The reserved [DEFAULT_SERVER_KEY] is the only sibling-of-server-id key under
     * `oauth2.servers.`; every other key path is `oauth2.servers.<id>.<...>` per the
     * [OAuth2ServerInstanceConfig] property layout.
     */
    private fun discoverServerIds(): Set<String> {
        val stripped = configService.getSubProperties(prefixes = setOf(prefix), stripPrefix = true)
        if (stripped.isEmpty()) {
            return emptySet()
        }
        return stripped.keys
            .asSequence()
            .map { it.substringBefore('.') }
            .filter { it.isNotEmpty() }
            .filter { it != DEFAULT_SERVER_KEY }
            .toSet()
    }

    private companion object {
        /**
         * Reserved sibling key under `oauth2.servers.` that selects which discovered server id is
         * the default. Excluded from the discovered-id set so an operator's
         * `oauth2.servers.default-server=production` does not synthesise a phantom server named
         * `default-server`.
         */
        const val DEFAULT_SERVER_KEY = "default-server"
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
            // OIDC RP-Initiated Logout 1.0 + Front-Channel Logout 1.0 + Back-Channel Logout 1.0
            logout = readFeaturePolicy("$serverPrefix.logout", defaults.logout),
            idTokenLifetimeSeconds =
                configService.getProperty(
                    "$serverPrefix.id-token-lifetime-seconds",
                    Int::class,
                    defaults.idTokenLifetimeSeconds,
                ) ?: defaults.idTokenLifetimeSeconds,
            // Optional knob (deviates from OIDC §5.4 — see model docs):
            // `embed-userinfo-claims-in-id-token: true` forces all projected user
            // claims into the id_token in addition to the standard /userinfo response.
            embedUserinfoClaimsInIdToken =
                configService.getProperty(
                    "$serverPrefix.embed-userinfo-claims-in-id-token",
                    Boolean::class,
                    defaults.embedUserinfoClaimsInIdToken,
                ) ?: defaults.embedUserinfoClaimsInIdToken,
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
            // RFC 8628 (Device Authorization Grant): feature gate plus the two timing knobs that
            // shape the issued device-authorization record (`expires_in`, `interval`).
            deviceFlow = readFeaturePolicy("$serverPrefix.device-flow", defaults.deviceFlow),
            deviceCodeLifetimeSeconds =
                configService.getProperty(
                    "$serverPrefix.device-code-lifetime-seconds",
                    Int::class,
                    defaults.deviceCodeLifetimeSeconds,
                ) ?: defaults.deviceCodeLifetimeSeconds,
            devicePollIntervalSeconds =
                configService.getProperty(
                    "$serverPrefix.device-poll-interval-seconds",
                    Int::class,
                    defaults.devicePollIntervalSeconds,
                ) ?: defaults.devicePollIntervalSeconds,
            pkce = readFeaturePolicy("$serverPrefix.pkce", defaults.pkce),
            dpop = readFeaturePolicy("$serverPrefix.dpop", defaults.dpop),
            dpopNonceRequired =
                configService.getProperty(
                    "$serverPrefix.dpop-nonce-required",
                    Boolean::class,
                    defaults.dpopNonceRequired,
                ) ?: defaults.dpopNonceRequired,
            iae = readFeaturePolicy("$serverPrefix.iae", defaults.iae),
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
            // signing-key-alias removed (P0-K4): the AS now owns a SigningKeyStore SPI; per-
            // server signing-key configuration moved to that store and is no longer
            // expressed via this config field.
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
            // OAuth2 Attestation-Based Client Authentication
            // (draft-ietf-oauth-attestation-based-client-auth). Discovery hides the surface
            // entirely until [attestation] is opted in; [attestationChallengeRequired] toggles
            // the /attestation-challenge endpoint and forces the verifier to demand a fresh
            // server-issued nonce in the PoP. The two `*-signing-alg-values-supported` lists
            // cap which JOSE algs the AS will accept on the attestation and PoP JWTs.
            attestation = readFeaturePolicy("$serverPrefix.attestation", defaults.attestation),
            attestationChallengeRequired =
                configService.getProperty(
                    "$serverPrefix.attestation-challenge-required",
                    Boolean::class,
                    defaults.attestationChallengeRequired,
                ) ?: defaults.attestationChallengeRequired,
            clientAttestationSigningAlgValuesSupported =
                configService
                    .getPropertyAsString("$serverPrefix.client-attestation-signing-alg-values-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet(),
            clientAttestationPopSigningAlgValuesSupported =
                configService
                    .getPropertyAsString("$serverPrefix.client-attestation-pop-signing-alg-values-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet(),
            attestationMaxLifetimeSeconds =
                configService.getProperty(
                    "$serverPrefix.attestation-max-lifetime-seconds",
                    Int::class,
                    defaults.attestationMaxLifetimeSeconds,
                ) ?: defaults.attestationMaxLifetimeSeconds,
            attestationPopMaxAgeSeconds =
                configService.getProperty(
                    "$serverPrefix.attestation-pop-max-age-seconds",
                    Int::class,
                    defaults.attestationPopMaxAgeSeconds,
                ) ?: defaults.attestationPopMaxAgeSeconds,
            attestationPopJtiReplayWindowSeconds =
                configService.getProperty(
                    "$serverPrefix.attestation-pop-jti-replay-window-seconds",
                    Int::class,
                    defaults.attestationPopJtiReplayWindowSeconds,
                ) ?: defaults.attestationPopJtiReplayWindowSeconds,
            // RFC 8705 (OAuth 2.0 Mutual-TLS Client Authentication and Certificate-Bound Access
            // Tokens). [mtls] gates discovery's `mtls_endpoint_aliases` and the AS's acceptance
            // of `tls_client_auth` / `self_signed_tls_client_auth`. The bound flag is the
            // server-wide default for cnf.x5t#S256 binding on issued access tokens; per-client
            // opt-in on [ClientRegistration.tlsClientCertificateBoundAccessTokens] overrides.
            // [mtlsEndpointHostOverride] is the hostname inserted into `mtls_endpoint_aliases`
            // when the operator deploys a separate TLS-terminating front door.
            mtls = readFeaturePolicy("$serverPrefix.mtls", defaults.mtls),
            tlsClientCertificateBoundAccessTokens =
                configService.getProperty(
                    "$serverPrefix.tls-client-certificate-bound-access-tokens",
                    Boolean::class,
                    defaults.tlsClientCertificateBoundAccessTokens,
                ) ?: defaults.tlsClientCertificateBoundAccessTokens,
            mtlsEndpointHostOverride =
                configService.getPropertyAsString("$serverPrefix.mtls-endpoint-host-override", null),
            // OIDF JARM (https://openid.net/specs/oauth-v2-jarm.html)
            jarm = readFeaturePolicy("$serverPrefix.jarm", defaults.jarm),
            authorizationSigningAlgValuesSupported =
                configService
                    .getPropertyAsString("$serverPrefix.authorization-signing-alg-values-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet(),
            authorizationEncryptionAlgValuesSupported =
                configService
                    .getPropertyAsString("$serverPrefix.authorization-encryption-alg-values-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet(),
            authorizationEncryptionEncValuesSupported =
                configService
                    .getPropertyAsString("$serverPrefix.authorization-encryption-enc-values-supported", null)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toSet(),
            jarmExpirationSeconds =
                configService.getProperty(
                    "$serverPrefix.jarm-expiration-seconds",
                    Long::class,
                    defaults.jarmExpirationSeconds,
                ) ?: defaults.jarmExpirationSeconds,
            // RFC 9101 JAR feature gate + request_uri pre-registration policy.
            jar = readFeaturePolicy("$serverPrefix.jar", defaults.jar),
            requireRequestUriRegistration =
                configService.getProperty(
                    "$serverPrefix.require-request-uri-registration",
                    Boolean::class,
                    defaults.requireRequestUriRegistration,
                ) ?: defaults.requireRequestUriRegistration,
            session = loadSessionConfig(serverPrefix),
        )
    }

    private fun loadSessionConfig(serverPrefix: String): SessionConfig {
        val defaults = SessionConfig()
        return SessionConfig(
            idleTtlSeconds =
                configService.getProperty(
                    "$serverPrefix.session.idle-ttl-seconds",
                    Int::class,
                    defaults.idleTtlSeconds,
                ) ?: defaults.idleTtlSeconds,
            absoluteTtlSeconds =
                configService.getProperty(
                    "$serverPrefix.session.absolute-ttl-seconds",
                    Int::class,
                    defaults.absoluteTtlSeconds,
                ) ?: defaults.absoluteTtlSeconds,
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
        val defaults = PublicClientConfig()
        val allowAny =
            configService.getProperty(
                "$serverPrefix.public-clients.allow-any",
                Boolean::class,
                defaults.allowAny,
            ) ?: defaults.allowAny
        val allowedClientIds =
            configService
                .getPropertyAsString("$serverPrefix.public-clients.allowed-client-ids", null)
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: defaults.allowedClientIds
        val permissiveRedirectUri =
            configService.getProperty(
                "$serverPrefix.public-clients.permissive-redirect-uri",
                Boolean::class,
                defaults.permissiveRedirectUri,
            ) ?: defaults.permissiveRedirectUri
        return PublicClientConfig(
            allowAny = allowAny,
            allowedClientIds = allowedClientIds,
            permissiveRedirectUri = permissiveRedirectUri,
        )
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
