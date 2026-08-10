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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.OpaqueSecretResolver
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.PropertyKeyNormalizerImpl
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.ResponseType
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.model.ClientType
import com.sphereon.oauth2.server.authorization.impl.storage.memory.OpaqueInternalClientCredential
import dev.zacsweers.metro.Inject
import kotlin.time.TimeSource

@Inject
class OAuth2ClientsConfigBinder(
    private val execution: SessionExecution,
    private val opaqueSecretResolver: OpaqueSecretResolver,
) {
    private val keyNormalizer = PropertyKeyNormalizerImpl.Default

    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    suspend fun loadClientRegistrations(serverId: String? = null): IdkResult<Map<String, ClientRegistration>, AuthorizationServerError.StorageError> =
        try {
            val configuredClients = loadConfiguredClients(serverId)
            val registrations = linkedMapOf<String, ClientRegistration>()
            val ownersByClientId = linkedMapOf<String, String>()

            configuredClients.values
                .filter { it.enabled }
                .forEach { client ->
                    val registration = client.toClientRegistration(resolveClientSecret(client))
                    val previous = registrations[registration.clientId]
                    if (previous == null) {
                        registrations[registration.clientId] = registration
                    }
                    require(previous == null) {
                        val previousOwner = ownersByClientId[registration.clientId] ?: "unknown"
                        "Duplicate clientId '${registration.clientId}' configured under " +
                            "'$previousOwner' and '${client.configKey}'"
                    }
                    ownersByClientId[registration.clientId] = client.configKey
                }

            Ok(registrations)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "loadClientRegistryConfig",
                    details = expected.message ?: "Failed to load oauth2 client registry configuration",
                    exception = expected,
                ),
            )
        }

    /**
     * Loads server-to-server clients whose credentials are persisted outside configuration.
     * An entry either names an opaque tenant-secret handle via `client-secret-id`, or carries no
     * locator at all: the verifier then derives the secret binding from the tenant and client
     * identity. Deployment bootstrap clients with a plaintext `client-secret` originate from the
     * server configuration model and are loaded separately by the authorization-server registry.
     *
     * This loader deliberately re-reads principal configuration for every registry operation.
     * Tenant onboarding can therefore publish a new tenant-bound workload client and have the
     * platform authorization server recognize it after config invalidation, without a process
     * restart or a shared ambient client.
     */
    suspend fun loadOpaqueInternalClientRegistrations(serverId: String): IdkResult<Map<String, OpaqueInternalClientRegistration>, AuthorizationServerError.StorageError> =
        try {
            val configPrefix = internalClientsConfigPrefix(serverId)
            val registrations = linkedMapOf<String, OpaqueInternalClientRegistration>()
            val ownersByClientId = linkedMapOf<String, String>()

            loadGroupedProperties(configPrefix).forEach { (entryKey, properties) ->
                val clientSecretId = readString(properties, "clientSecretId")
                if (readString(properties, "clientSecret") != null) {
                    require(clientSecretId == null) {
                        "Internal client '$entryKey' cannot configure both client-secret and client-secret-id"
                    }
                    // Deployment bootstrap client owned by the typed server configuration model;
                    // the authorization-server registry loads it from there.
                    return@forEach
                }
                val clientId =
                    readString(properties, "clientId")
                        ?: throw IllegalArgumentException("Missing required property '$configPrefix.$entryKey.client-id'")
                val tenantId =
                    readString(properties, "tenantId")
                        ?: throw IllegalArgumentException("Missing required property '$configPrefix.$entryKey.tenant-id'")
                val registration =
                    ClientRegistration(
                        clientId = clientId,
                        clientSecret = null,
                        clientType = ClientType.CONFIDENTIAL,
                        grantTypes =
                            readStringList(properties, "grantTypes")
                                ?.map { parseGrantType(it, entryKey) }
                                ?.takeIf { it.isNotEmpty() }
                                ?: listOf(GrantType.CLIENT_CREDENTIALS),
                        defaultAccessTokenAudience = readString(properties, "defaultAccessTokenAudience"),
                        allowedAccessTokenAudiences = readStringList(properties, "allowedAccessTokenAudiences")?.toSet().orEmpty(),
                        tokenEndpointAuthMethod = ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                        additionalMetadata = mapOf(TENANT_ID_CLAIM to tenantId),
                    )
                val previous = registrations[clientId]
                require(previous == null) {
                    val previousOwner = ownersByClientId[clientId] ?: "unknown"
                    "Duplicate internal clientId '$clientId' configured under '$previousOwner' and '$entryKey'"
                }
                registrations[clientId] =
                    OpaqueInternalClientRegistration(
                        registration = registration,
                        credential =
                            OpaqueInternalClientCredential(
                                clientId = clientId,
                                tenantId = tenantId,
                                secretId = clientSecretId,
                            ),
                    )
                ownersByClientId[clientId] = entryKey
            }

            Ok(registrations)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "loadInternalClientRegistryConfig",
                    details = expected.message ?: "Failed to load internal oauth2 client registry configuration",
                    exception = expected,
                ),
            )
        }

    /** Resolves only a validated opaque handle in the authenticated runtime session. */
    private suspend fun resolveClientSecret(client: ConfiguredOAuth2Client): String? {
        val secretId = client.clientSecretId ?: return null
        require(OPAQUE_SECRET_ID_PATTERN.matches(secretId)) {
            "Client secret id for '${client.clientId}' is not an opaque server-generated identifier"
        }
        val resolved = opaqueSecretResolver.resolve(secretId)
        require(resolved.isOk) {
            "Client secret for '${client.clientId}' could not be resolved"
        }
        return resolved.value
    }

    private fun loadConfiguredClients(serverId: String?): Map<String, ConfiguredOAuth2Client> {
        val configPrefix = configPrefix(serverId)
        return loadGroupedProperties(configPrefix).mapValues { (entryKey, entryProperties) ->
            parseClient(configPrefix, entryKey, entryProperties)
        }
    }

    private fun loadGroupedProperties(configPrefix: String): Map<String, Map<String, Any>> {
        val started = TimeSource.Monotonic.markNow()
        val properties = configService.getSubProperties(setOf(configPrefix), stripPrefix = true)
        execution.log.debug(
            "OAuth2ClientConfig read prefix '$configPrefix' in " +
                "${started.elapsedNow().inWholeMilliseconds}ms (properties=${properties.size})",
        )
        if (properties.isEmpty()) {
            return emptyMap()
        }

        // The PropertyKeyNormalizer turns hyphens, underscores, dots, and spaces into the dot
        // delimiter, so a hyphenated client id like `oidf-op-basic` is stored as `oidf.op.basic`
        // in the property keyspace. Splitting on the first dot would misread that as a group
        // named `oidf` with `op.basic.*` nested under it. Instead, anchor on each `.client.id`
        // property: the segments before it are the group prefix, and the value of that property
        // is the canonical client id. Then carve every other key under the same group prefix.
        val groupPrefixes = discoverGroupPrefixes(properties.keys)
        val grouped = linkedMapOf<String, MutableMap<String, Any>>()
        properties.forEach { (key, value) ->
            val groupPrefix =
                groupPrefixes.firstOrNull { prefix ->
                    key == prefix || key.startsWith("$prefix.")
                } ?: return@forEach
            val nestedKey = if (key == groupPrefix) "" else key.substring(groupPrefix.length + 1)
            grouped.getOrPut(groupPrefix) { linkedMapOf() }[nestedKey] = value
        }

        return grouped
    }

    /**
     * A client config block is identified by the presence of an explicit `client-id` property at
     * the deepest leaf. After [PropertyKeyNormalizerImpl] flattens hyphens to dots, a configured
     * `oauth2.clients.oidf-op-basic.client-id=oidf-op-basic` ends up as the key
     * `oidf.op.basic.client.id` (after stripping the `oauth2.clients` prefix). Walk every key,
     * keep the ones ending in `.client.id` or the bare value `client.id`, and use everything to
     * the left as the group prefix.
     */
    private fun discoverGroupPrefixes(keys: Set<String>): List<String> =
        keys
            .mapNotNull { key ->
                when {
                    key == CLIENT_ID_LEAF -> ""
                    key.endsWith(".$CLIENT_ID_LEAF") -> key.substring(0, key.length - CLIENT_ID_LEAF.length - 1)
                    else -> null
                }
            }.distinct()
            // Longest prefix first so an outer `client-id` doesn't shadow a deeper match.
            .sortedByDescending { it.length }

    private fun parseClient(
        configPrefix: String,
        entryKey: String,
        properties: Map<String, Any>,
    ): ConfiguredOAuth2Client {
        require(keyNormalizer.normalize("clientSecret") !in properties) {
            "Legacy plaintext/reference client-secret configuration is not supported for '$entryKey'"
        }
        val clientType = parseClientType(readString(properties, "clientType") ?: ClientType.CONFIDENTIAL.name, entryKey)
        val grantTypes =
            readStringList(properties, "grantTypes")
                ?.map { parseGrantType(it, entryKey) }
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Missing required property '$configPrefix.$entryKey.grant-types'")
        val responseTypes =
            readStringList(properties, "responseTypes")
                ?.map { parseResponseType(it, entryKey) }
                ?: defaultResponseTypes(grantTypes)
        val tokenEndpointAuthMethod =
            readString(properties, "tokenEndpointAuthMethod")
                ?.let { parseClientAuthenticationMethod(it, entryKey) }
                ?: defaultAuthMethod(clientType)

        return ConfiguredOAuth2Client(
            configKey = entryKey,
            enabled = readBoolean(properties, "enabled") ?: true,
            clientId =
                readString(properties, "clientId")
                    ?: throw IllegalArgumentException("Missing required property '$configPrefix.$entryKey.client-id'"),
            clientSecretId = readString(properties, "clientSecretId"),
            clientName = readString(properties, "clientName"),
            clientType = clientType,
            grantTypes = grantTypes,
            responseTypes = responseTypes,
            redirectUris = readStringList(properties, "redirectUris").orEmpty(),
            allowedScopes = readStringList(properties, "allowedScopes"),
            defaultAccessTokenAudience = readString(properties, "defaultAccessTokenAudience"),
            allowedAccessTokenAudiences = readStringList(properties, "allowedAccessTokenAudiences")?.toSet().orEmpty(),
            tokenEndpointAuthMethod = tokenEndpointAuthMethod,
            tokenEndpointAuthSigningAlg = readStringList(properties, "tokenEndpointAuthSigningAlg"),
            jwks = readJwks(configPrefix, properties, "jwks", entryKey),
            jwksUri = readString(properties, "jwksUri"),
            requirePkce = readBoolean(properties, "requirePkce") ?: (clientType == ClientType.PUBLIC),
            requirePushedAuthorizationRequests = readBoolean(properties, "requirePushedAuthorizationRequests") ?: false,
            dpopBoundAccessTokens = readBoolean(properties, "dpopBoundAccessTokens") ?: false,
            accessTokenLifetime = readInt(properties, "accessTokenLifetime") ?: 3600,
            refreshTokenLifetime = readInt(properties, "refreshTokenLifetime"),
            authorizationCodeLifetime = readInt(properties, "authorizationCodeLifetime") ?: 600,
            trustedAttesterIssuers = readStringList(properties, "trustedAttesterIssuers"),
            trustedAttesterJwks = readJwks(configPrefix, properties, "trustedAttesterJwks", entryKey),
            trustedAttesterJwksUris = readStringList(properties, "trustedAttesterJwksUris"),
            postLogoutRedirectUris = readStringList(properties, "postLogoutRedirectUris").orEmpty(),
            frontchannelLogoutUri = readString(properties, "frontchannelLogoutUri"),
            frontchannelLogoutSessionRequired = readBoolean(properties, "frontchannelLogoutSessionRequired") ?: false,
            backchannelLogoutUri = readString(properties, "backchannelLogoutUri"),
            backchannelLogoutSessionRequired = readBoolean(properties, "backchannelLogoutSessionRequired") ?: false,
            authorizationSignedResponseAlg = readString(properties, "authorizationSignedResponseAlg"),
            authorizationEncryptedResponseAlg = readString(properties, "authorizationEncryptedResponseAlg"),
            authorizationEncryptedResponseEnc = readString(properties, "authorizationEncryptedResponseEnc"),
            requestObjectSigningAlg = readString(properties, "requestObjectSigningAlg"),
            requestUris = readStringList(properties, "requestUris").orEmpty(),
            tlsClientAuthSubjectDn = readString(properties, "tlsClientAuthSubjectDn"),
            tlsClientAuthSanDns = readString(properties, "tlsClientAuthSanDns"),
            tlsClientAuthSanEmail = readString(properties, "tlsClientAuthSanEmail"),
            tlsClientAuthSanIp = readString(properties, "tlsClientAuthSanIp"),
            tlsClientAuthSanUri = readString(properties, "tlsClientAuthSanUri"),
            tlsClientCertificateBoundAccessTokens = readBoolean(properties, "tlsClientCertificateBoundAccessTokens") ?: false,
        )
    }

    /**
     * Reads inline JWKs from `oauth2.clients.<id>.<field-name>.<n>.<param>` keys. Each indexed
     * group contributes one [Jwk] (RSA, EC, or OKP). Returns `null` when no `<field-name>.<n>.kty`
     * keys are present so an unset list stays distinct from an explicitly empty list at the
     * registration layer. Per-key parsing is strict: missing required parameters or unknown
     * `kty`/`crv` values throw, matching how the rest of the binder surfaces fatal
     * misconfiguration.
     *
     * Supported `kty` values:
     *  - `RSA`: requires `n` and `e` (base64url).
     *  - `EC`: requires `crv` plus `x` and `y` (base64url).
     *  - `OKP`: requires `crv` plus `x` (base64url): Ed25519 / X25519.
     */
    private fun readJwks(
        configPrefix: String,
        properties: Map<String, Any>,
        fieldName: String,
        entryKey: String,
    ): List<Jwk>? {
        val normalizedFieldName = keyNormalizer.normalize(fieldName)
        val compactJwkSet = properties[normalizedFieldName]?.toString()?.trim().orEmpty()
        if (compactJwkSet.isNotEmpty()) {
            return runCatching { JwkSet.fromJsonString(compactJwkSet).keys.toList() }
                .getOrElse {
                    throw IllegalArgumentException(
                        "Invalid JWK Set JSON at '$configPrefix.$entryKey.$fieldName': ${it.message}",
                        it,
                    )
                }
                .takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException(
                    "JWK Set at '$configPrefix.$entryKey.$fieldName' must contain at least one key",
                )
        }
        val grouped = linkedMapOf<Int, MutableMap<String, Any>>()
        properties.forEach { (key, value) ->
            if (!key.startsWith("$normalizedFieldName.")) return@forEach
            val tail = key.substring(normalizedFieldName.length + 1)
            val dotIdx = tail.indexOf('.')
            if (dotIdx <= 0) return@forEach
            val index = tail.substring(0, dotIdx).toIntOrNull() ?: return@forEach
            val sub = tail.substring(dotIdx + 1)
            grouped.getOrPut(index) { linkedMapOf() }[sub] = value
        }
        if (grouped.isEmpty()) {
            return null
        }
        return grouped.entries
            .sortedBy { it.key }
            .map { (index, jwkProperties) -> parseJwk(configPrefix, jwkProperties, entryKey, fieldName, index) }
    }

    private fun parseJwk(
        configPrefix: String,
        properties: Map<String, Any>,
        entryKey: String,
        fieldName: String,
        index: Int,
    ): Jwk {
        val keyPath = "$configPrefix.$entryKey.$fieldName.$index"
        val keyTypeStr =
            readString(properties, "kty")
                ?: throw IllegalArgumentException(
                    "Missing required property '$keyPath.kty'",
                )
        val kty =
            try {
                JwaKeyType.fromValue(keyTypeStr)
            } catch (expected: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Unsupported JWK kty '$keyTypeStr' for '$keyPath'",
                    expected,
                )
            }

        val alg = readString(properties, "alg")?.let { JwaAlgorithm.fromValue(it) }
        val use = readString(properties, "use")
        val kid = readString(properties, "kid")
        val keyOps =
            readStringList(properties, "keyOps")?.map { JoseKeyOperations.fromValue(it) }?.toTypedArray()

        return when (kty) {
            JwaKeyType.RSA -> {
                val n =
                    readString(properties, "n")
                        ?: throw IllegalArgumentException(
                            "Missing required property '$keyPath.n' for RSA JWK",
                        )
                val e =
                    readString(properties, "e")
                        ?: throw IllegalArgumentException(
                            "Missing required property '$keyPath.e' for RSA JWK",
                        )
                Jwk(
                    kty = JwaKeyType.RSA,
                    alg = alg,
                    use = use,
                    kid = kid,
                    key_ops = keyOps,
                    n = n,
                    e = e,
                )
            }

            JwaKeyType.EC -> {
                val crvStr =
                    readString(properties, "crv")
                        ?: throw IllegalArgumentException(
                            "Missing required property '$keyPath.crv' for EC JWK",
                        )
                val crv =
                    JwaCurve.fromValue(crvStr)
                        ?: throw IllegalArgumentException(
                            "Unsupported JWK crv '$crvStr' for '$keyPath'",
                        )
                val x =
                    readString(properties, "x")
                        ?: throw IllegalArgumentException(
                            "Missing required property '$keyPath.x' for EC JWK",
                        )
                val y =
                    readString(properties, "y")
                        ?: throw IllegalArgumentException(
                            "Missing required property '$keyPath.y' for EC JWK",
                        )
                Jwk(
                    kty = JwaKeyType.EC,
                    alg = alg,
                    use = use,
                    kid = kid,
                    key_ops = keyOps,
                    crv = crv,
                    x = x,
                    y = y,
                )
            }

            JwaKeyType.OKP -> {
                val crvStr =
                    readString(properties, "crv")
                        ?: throw IllegalArgumentException(
                            "Missing required property '$keyPath.crv' for OKP JWK",
                        )
                val crv =
                    JwaCurve.fromValue(crvStr)
                        ?: throw IllegalArgumentException(
                            "Unsupported JWK crv '$crvStr' for '$keyPath'",
                        )
                val x =
                    readString(properties, "x")
                        ?: throw IllegalArgumentException(
                            "Missing required property '$keyPath.x' for OKP JWK",
                        )
                Jwk(
                    kty = JwaKeyType.OKP,
                    alg = alg,
                    use = use,
                    kid = kid,
                    key_ops = keyOps,
                    crv = crv,
                    x = x,
                )
            }

            JwaKeyType.oct -> {
                throw IllegalArgumentException(
                    "Symmetric JWK (kty=oct) is not supported in inline client JWKS for '$keyPath'",
                )
            }
        }
    }

    private fun readString(
        properties: Map<String, Any>,
        fieldName: String,
    ): String? {
        val value = properties[keyNormalizer.normalize(fieldName)] ?: return null
        return value.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun readBoolean(
        properties: Map<String, Any>,
        fieldName: String,
    ): Boolean? {
        val value = properties[keyNormalizer.normalize(fieldName)] ?: return null
        return when (value) {
            is Boolean -> {
                value
            }

            is Number -> {
                value.toInt() != 0
            }

            is String -> {
                when (value.trim().lowercase()) {
                    "true", "1", "yes", "on" -> true
                    "false", "0", "no", "off" -> false
                    else -> throw IllegalArgumentException("Invalid boolean value '$value' for field '$fieldName'")
                }
            }

            else -> {
                throw IllegalArgumentException("Invalid boolean value '$value' for field '$fieldName'")
            }
        }
    }

    private fun readInt(
        properties: Map<String, Any>,
        fieldName: String,
    ): Int? {
        val value = properties[keyNormalizer.normalize(fieldName)] ?: return null
        return when (value) {
            is Int -> {
                value
            }

            is Number -> {
                value.toInt()
            }

            is String -> {
                value.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid integer value '$value' for field '$fieldName'")
            }

            else -> {
                throw IllegalArgumentException("Invalid integer value '$value' for field '$fieldName'")
            }
        }
    }

    private fun readStringList(
        properties: Map<String, Any>,
        fieldName: String,
    ): List<String>? {
        val normalizedFieldName = keyNormalizer.normalize(fieldName)
        val directValue = properties[normalizedFieldName]
        if (directValue != null) {
            return toStringList(directValue)
        }

        val indexedValues =
            properties.entries
                .mapNotNull { (key, value) ->
                    when {
                        key.startsWith("$normalizedFieldName.") -> {
                            val index = key.substring(normalizedFieldName.length + 1).toIntOrNull() ?: return@mapNotNull null
                            index to value.toString().trim()
                        }

                        key.startsWith("$normalizedFieldName[") && key.endsWith("]") -> {
                            val index = key.substringAfter('[').substringBefore(']').toIntOrNull() ?: return@mapNotNull null
                            index to value.toString().trim()
                        }

                        else -> {
                            null
                        }
                    }
                }.sortedBy { it.first }
                .map { it.second }
                .filter { it.isNotEmpty() }

        return indexedValues.ifEmpty { null }
    }

    private fun toStringList(value: Any): List<String> =
        when (value) {
            is List<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
            is Array<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
            is String -> value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            else -> listOf(value.toString().trim()).filter { it.isNotEmpty() }
        }

    private fun parseClientType(
        value: String,
        entryKey: String,
    ): ClientType =
        ClientType.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
            ?: throw IllegalArgumentException("Unsupported client type '$value' for '$CONFIG_PREFIX.$entryKey'")

    private fun parseGrantType(
        value: String,
        entryKey: String,
    ): GrantType =
        GrantType.entries.firstOrNull {
            it.value.equals(value.trim(), ignoreCase = true) || it.name.equals(value.trim(), ignoreCase = true)
        } ?: throw IllegalArgumentException("Unsupported grant type '$value' for '$CONFIG_PREFIX.$entryKey'")

    private fun parseResponseType(
        value: String,
        entryKey: String,
    ): ResponseType =
        ResponseType.entries.firstOrNull {
            it.value.equals(value.trim(), ignoreCase = true) || it.name.equals(value.trim(), ignoreCase = true)
        } ?: throw IllegalArgumentException("Unsupported response type '$value' for '$CONFIG_PREFIX.$entryKey'")

    private fun parseClientAuthenticationMethod(
        value: String,
        entryKey: String,
    ): ClientAuthenticationMethod =
        ClientAuthenticationMethod.entries.firstOrNull {
            it.value.equals(value.trim(), ignoreCase = true) || it.name.equals(value.trim(), ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "Unsupported token endpoint auth method '$value' for '$CONFIG_PREFIX.$entryKey'",
        )

    private fun defaultResponseTypes(grantTypes: List<GrantType>): List<ResponseType> =
        if (grantTypes.contains(GrantType.AUTHORIZATION_CODE)) {
            listOf(ResponseType.CODE)
        } else {
            emptyList()
        }

    private fun defaultAuthMethod(clientType: ClientType): ClientAuthenticationMethod =
        if (clientType == ClientType.PUBLIC) {
            ClientAuthenticationMethod.NONE
        } else {
            ClientAuthenticationMethod.CLIENT_SECRET_BASIC
        }

    companion object {
        const val CONFIG_PREFIX = "oauth2.clients"
        const val SERVER_CLIENTS_SUFFIX = "clients"
        const val INTERNAL_CLIENTS_SUFFIX = "internal-clients"

        fun configPrefix(serverId: String?): String = serverId?.let { "oauth2.servers.$it.$SERVER_CLIENTS_SUFFIX" } ?: CONFIG_PREFIX

        fun internalClientsConfigPrefix(serverId: String): String = "oauth2.servers.$serverId.$INTERNAL_CLIENTS_SUFFIX"

        /**
         * Normalized form of the `client-id` config leaf used as the discovery anchor for client
         * group prefixes. `PropertyKeyNormalizerImpl` collapses hyphens to dots, so the literal
         * source key `client-id` is stored as `client.id`.
         */
        private const val CLIENT_ID_LEAF: String = "client.id"
        private const val TENANT_ID_CLAIM: String = "tenant_id"
        private val OPAQUE_SECRET_ID_PATTERN = Regex("^sec_[A-Za-z0-9_-]{16,128}$")
    }
}

data class OpaqueInternalClientRegistration(
    val registration: ClientRegistration,
    val credential: OpaqueInternalClientCredential,
) {
    override fun toString(): String = "OpaqueInternalClientRegistration([REDACTED])"
}
