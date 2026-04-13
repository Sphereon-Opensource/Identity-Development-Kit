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
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.PropertyKeyNormalizerImpl
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.ResponseType
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.model.ClientType
import dev.zacsweers.metro.Inject

@Inject
class OAuth2ClientsConfigBinder(
    private val execution: SessionExecution,
) {
    private val keyNormalizer = PropertyKeyNormalizerImpl.Default

    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    fun loadClientRegistrations(): IdkResult<Map<String, ClientRegistration>, AuthorizationServerError.StorageError> =
        try {
            val configuredClients = loadConfiguredClients()
            val registrations = linkedMapOf<String, ClientRegistration>()
            val ownersByClientId = linkedMapOf<String, String>()

            configuredClients.values
                .filter { it.enabled }
                .forEach { client ->
                    val registration = client.toClientRegistration()
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

    private fun loadConfiguredClients(): Map<String, ConfiguredOAuth2Client> {
        val properties = configService.getSubProperties(setOf(CONFIG_PREFIX), stripPrefix = true)
        if (properties.isEmpty()) {
            return emptyMap()
        }

        val grouped = linkedMapOf<String, MutableMap<String, Any>>()
        properties.forEach { (key, value) ->
            val dotIndex = key.indexOf('.')
            val entryKey =
                if (dotIndex >= 0) {
                    key.substring(0, dotIndex)
                } else {
                    key
                }
            val nestedKey =
                if (dotIndex >= 0) {
                    key.substring(dotIndex + 1)
                } else {
                    ""
                }
            grouped.getOrPut(entryKey) { linkedMapOf() }[nestedKey] = value
        }

        return grouped.mapValues { (entryKey, entryProperties) ->
            parseClient(entryKey, entryProperties)
        }
    }

    private fun parseClient(
        entryKey: String,
        properties: Map<String, Any>,
    ): ConfiguredOAuth2Client {
        val clientType = parseClientType(readString(properties, "clientType") ?: ClientType.CONFIDENTIAL.name, entryKey)
        val grantTypes =
            readStringList(properties, "grantTypes")
                ?.map { parseGrantType(it, entryKey) }
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Missing required property '$CONFIG_PREFIX.$entryKey.grant-types'")
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
                    ?: throw IllegalArgumentException("Missing required property '$CONFIG_PREFIX.$entryKey.client-id'"),
            clientSecret = readString(properties, "clientSecret"),
            clientName = readString(properties, "clientName"),
            clientType = clientType,
            grantTypes = grantTypes,
            responseTypes = responseTypes,
            redirectUris = readStringList(properties, "redirectUris").orEmpty(),
            allowedScopes = readStringList(properties, "allowedScopes"),
            tokenEndpointAuthMethod = tokenEndpointAuthMethod,
            requirePkce = readBoolean(properties, "requirePkce") ?: (clientType == ClientType.PUBLIC),
            requirePushedAuthorizationRequests = readBoolean(properties, "requirePushedAuthorizationRequests") ?: false,
            dpopBoundAccessTokens = readBoolean(properties, "dpopBoundAccessTokens") ?: false,
            accessTokenLifetime = readInt(properties, "accessTokenLifetime") ?: 3600,
            refreshTokenLifetime = readInt(properties, "refreshTokenLifetime"),
            authorizationCodeLifetime = readInt(properties, "authorizationCodeLifetime") ?: 600,
            trustedAttesterIssuers = readStringList(properties, "trustedAttesterIssuers"),
            trustedAttesterJwksUris = readStringMap(properties, "trustedAttesterJwksUris"),
        )
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

    private fun readStringMap(
        properties: Map<String, Any>,
        fieldName: String,
    ): Map<String, String>? {
        val normalizedFieldName = keyNormalizer.normalize(fieldName)
        val directValue = properties[normalizedFieldName]
        if (directValue is Map<*, *>) {
            return directValue.entries.associate { (key, value) ->
                key.toString() to value.toString()
            }
        }

        val prefixedValues =
            properties.entries
                .mapNotNull { (key, value) ->
                    if (!key.startsWith("$normalizedFieldName.")) {
                        return@mapNotNull null
                    }
                    val mapKey = key.substring(normalizedFieldName.length + 1)
                    if (mapKey.isBlank()) {
                        return@mapNotNull null
                    }
                    mapKey to value.toString()
                }.toMap()

        return prefixedValues.ifEmpty { null }
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
    }
}
