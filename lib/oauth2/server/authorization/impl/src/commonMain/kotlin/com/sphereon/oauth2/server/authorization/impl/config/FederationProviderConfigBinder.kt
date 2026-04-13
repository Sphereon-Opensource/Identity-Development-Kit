/*
 * Copyright 2023-2026 Sphereon International B.V.
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

import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import com.sphereon.oauth2.server.authorization.config.StateMode

/**
 * Binds federation provider configuration from properties.
 *
 * Properties are expected at:
 * ```
 * federation.providers.{name}.issuer-url=...
 * federation.providers.{name}.client-id=...
 * federation.providers.{name}.client-secret=...
 * federation.providers.{name}.scopes=openid,profile,email
 * federation.providers.{name}.identifier-claim-name=sub
 * federation.providers.{name}.enabled=true
 * ```
 */
class FederationProviderConfigBinder(
    private val configService: PropertyResolver,
) {
    fun bind(): List<FederationProviderConfig> {
        val prefix = "federation.providers"
        val providerNames =
            configService
                .getPropertyAsString("$prefix.names", null)
                ?.split(",")
                ?.map { it.trim() }
                ?: return emptyList()

        return providerNames.mapNotNull { name ->
            val providerPrefix = "$prefix.$name"
            val issuerUrl =
                configService.getPropertyAsString("$providerPrefix.issuer-url", null)
                    ?: return@mapNotNull null
            val clientId =
                configService.getPropertyAsString("$providerPrefix.client-id", null)
                    ?: return@mapNotNull null

            FederationProviderConfig(
                id = name,
                name = configService.getPropertyAsString("$providerPrefix.name", name) ?: name,
                issuerUrl = issuerUrl,
                clientId = clientId,
                clientSecret = configService.getPropertyAsString("$providerPrefix.client-secret", null),
                scopes =
                    configService
                        .getPropertyAsString("$providerPrefix.scopes", null)
                        ?.split(",")
                        ?.map { it.trim() }
                        ?: listOf("openid", "profile", "email"),
                identifierClaimName = configService.getPropertyAsString("$providerPrefix.identifier-claim-name", "sub") ?: "sub",
                enabled = configService.getPropertyAsString("$providerPrefix.enabled", "true")?.toBoolean() != false,
                authorizationEndpointOverride = configService.getPropertyAsString("$providerPrefix.authorization-endpoint-override", null)?.ifBlank { null },
                tokenEndpointOverride = configService.getPropertyAsString("$providerPrefix.token-endpoint-override", null),
                userinfoEndpointOverride = configService.getPropertyAsString("$providerPrefix.userinfo-endpoint-override", null),
                callbackPath = configService.getPropertyAsString("$providerPrefix.callback-path", "/federation/callback") ?: "/federation/callback",
                stateMode =
                    configService.getPropertyAsString("$providerPrefix.state-mode", "jwt")?.let {
                        try {
                            StateMode.valueOf(it.uppercase())
                        } catch (_: Exception) {
                            // Ignored: invalid state-mode value, defaulting to JWT
                            StateMode.JWT
                        }
                    } ?: StateMode.JWT,
            )
        }
    }
}
