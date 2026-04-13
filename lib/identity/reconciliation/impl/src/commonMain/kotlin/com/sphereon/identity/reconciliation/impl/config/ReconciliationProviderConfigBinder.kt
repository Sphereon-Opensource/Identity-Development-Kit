/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.identity.reconciliation.impl.config

import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.identity.idv.model.ConfigReference
import com.sphereon.identity.idv.model.SecretReference
import com.sphereon.identity.reconciliation.model.OidcClientConfig
import com.sphereon.identity.reconciliation.model.ReconciliationAttributeMapping
import com.sphereon.identity.reconciliation.model.ReconciliationProvider

/**
 * Binds reconciliation provider and OIDC client configuration from properties.
 *
 * Properties for OIDC clients:
 * ```
 * identity.reconciliation.oidc-clients.names=surf-oidc
 * identity.reconciliation.oidc-clients.surf-oidc.discovery-url=https://connect.surfconext.nl/.well-known/openid-configuration
 * identity.reconciliation.oidc-clients.surf-oidc.client-id-ref.key=SURF_CLIENT_ID
 * identity.reconciliation.oidc-clients.surf-oidc.client-secret-ref.path=SURF_CLIENT_SECRET
 * identity.reconciliation.oidc-clients.surf-oidc.client-secret-ref.provider-id=env
 * identity.reconciliation.oidc-clients.surf-oidc.scopes=openid,profile,email
 * ```
 *
 * Properties for providers:
 * ```
 * identity.reconciliation.providers.names=surf
 * identity.reconciliation.providers.surf.id=surf
 * identity.reconciliation.providers.surf.oidc-client-id=surf-oidc
 * identity.reconciliation.providers.surf.identifier-claim-name=sub
 * identity.reconciliation.providers.surf.attribute-mappings[0].source=sub
 * identity.reconciliation.providers.surf.attribute-mappings[0].target=eduid
 * ```
 */
class ReconciliationProviderConfigBinder(
    private val configService: PropertyResolver,
) {
    companion object {
        const val CONFIG_PREFIX = "identity.reconciliation"
        const val PROVIDERS_PREFIX = "$CONFIG_PREFIX.providers"
        const val OIDC_CLIENTS_PREFIX = "$CONFIG_PREFIX.oidc-clients"
    }

    fun bindOidcClients(): List<OidcClientConfig> {
        val clientNames = configService.getPropertyAsString("$OIDC_CLIENTS_PREFIX.names", null)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: return emptyList()

        return clientNames.mapNotNull { name ->
            val prefix = "$OIDC_CLIENTS_PREFIX.$name"
            val discoveryUrl = configService.getPropertyAsString("$prefix.discovery-url", null)
                ?: return@mapNotNull null

            val clientIdKey = configService.getPropertyAsString("$prefix.client-id-ref.key", null)
                ?: configService.getPropertyAsString("$prefix.client-id-ref", null)
                ?: return@mapNotNull null

            val secretPath = configService.getPropertyAsString("$prefix.client-secret-ref.path", null)
                ?: configService.getPropertyAsString("$prefix.client-secret-ref", null)
                ?: return@mapNotNull null

            OidcClientConfig(
                id = name,
                discoveryUrl = discoveryUrl,
                clientIdRef = ConfigReference(key = clientIdKey),
                clientSecretRef = SecretReference(
                    path = secretPath,
                    providerId = configService.getPropertyAsString("$prefix.client-secret-ref.provider-id", null),
                    key = configService.getPropertyAsString("$prefix.client-secret-ref.key", null),
                ),
                scopes = configService.getPropertyAsString("$prefix.scopes", null)
                    ?.split(",")?.map { it.trim() }
                    ?: listOf("openid"),
                userInfoEnabled = configService.getPropertyAsString("$prefix.user-info-enabled", "false")?.toBoolean() == true,
                authorizationEndpointOverride = configService.getPropertyAsString("$prefix.authorization-endpoint-override", null)?.ifBlank { null },
                tokenEndpointOverride = configService.getPropertyAsString("$prefix.token-endpoint-override", null)?.ifBlank { null },
            )
        }
    }

    /**
     * Read structured attribute mappings from indexed config entries.
     *
     * Supports two formats:
     * 1. Indexed: `attribute-mappings[0].source=sub`, `attribute-mappings[0].target=eduid`
     * 2. Named entries: `attribute-mappings.entries=sub,eduid` with sub-properties
     */
    fun readAttributeMappings(providerPrefix: String, mappingSuffix: String): List<ReconciliationAttributeMapping> {
        val mappings = mutableListOf<ReconciliationAttributeMapping>()

        // Try indexed format first
        var index = 0
        while (true) {
            val source = configService.getPropertyAsString("$providerPrefix.$mappingSuffix[$index].source", null)
                ?: break
            val target = configService.getPropertyAsString("$providerPrefix.$mappingSuffix[$index].target", null)
                ?: break
            mappings.add(
                ReconciliationAttributeMapping(
                    source = source,
                    target = target,
                    identifierType = configService.getPropertyAsString("$providerPrefix.$mappingSuffix[$index].identifier-type", null),
                    required = configService.getPropertyAsString("$providerPrefix.$mappingSuffix[$index].required", "false")?.toBoolean() == true,
                ),
            )
            index++
        }

        if (mappings.isNotEmpty()) return mappings

        // Try named entries format
        val entryNames = configService.getPropertyAsString("$providerPrefix.$mappingSuffix.entries", null)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: return emptyList()

        for (entryName in entryNames) {
            val entryPrefix = "$providerPrefix.$mappingSuffix.$entryName"
            val source = configService.getPropertyAsString("$entryPrefix.source", null) ?: continue
            val target = configService.getPropertyAsString("$entryPrefix.target", null) ?: continue
            mappings.add(
                ReconciliationAttributeMapping(
                    source = source,
                    target = target,
                    identifierType = configService.getPropertyAsString("$entryPrefix.identifier-type", null),
                    required = configService.getPropertyAsString("$entryPrefix.required", "false")?.toBoolean() == true,
                ),
            )
        }

        return mappings
    }

    fun bindProviders(): List<ReconciliationProvider> {
        val providerNames = configService.getPropertyAsString("$PROVIDERS_PREFIX.names", null)
            ?.split(",")?.map { it.trim() }
            ?: return emptyList()

        return providerNames.mapNotNull { name ->
            val providerPrefix = "$PROVIDERS_PREFIX.$name"
            val oidcClientId = configService.getPropertyAsString("$providerPrefix.oidc-client-id", null)
                ?: return@mapNotNull null

            val attributeMappings = readAttributeMappings(providerPrefix, "claim-mappings")
            val userInfoAttributeMappings = readAttributeMappings(providerPrefix, "userinfo-claim-mappings")

            ReconciliationProvider(
                id = configService.getPropertyAsString("$providerPrefix.id", name) ?: name,
                name = configService.getPropertyAsString("$providerPrefix.name", null),
                oidcClientId = oidcClientId,
                identifierAttributeName = configService.getPropertyAsString("$providerPrefix.identifier-claim-name", "sub") ?: "sub",
                enabled = configService.getPropertyAsString("$providerPrefix.enabled", "true")?.toBoolean() != false,
                attributeMappings = attributeMappings,
                userInfoAttributeMappings = userInfoAttributeMappings,
                assuranceAcr = configService.getPropertyAsString("$providerPrefix.assurance-acr", null),
                assuranceAmr = configService.getPropertyAsString("$providerPrefix.assurance-amr", null)
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() },
            )
        }
    }
}
