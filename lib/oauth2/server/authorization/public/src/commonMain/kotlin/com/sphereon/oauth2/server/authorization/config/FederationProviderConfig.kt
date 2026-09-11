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

package com.sphereon.oauth2.server.authorization.config

/**
 * State parameter encoding mode for the OIDC authorization request.
 *
 * - [JWT]: State is a signed JWT containing flow, provider ID, session ID, and optional
 *   OID4VP session ID. The single `/federation/callback` endpoint inspects the JWT
 *   to determine flow type and provider. This is the default.
 * - [OPAQUE]: State is a random UUID stored in a server-side pending sessions map.
 *   Requires per-provider callback paths (`/federation/callback/{providerId}`) since
 *   the provider cannot be derived from the state alone.
 */
enum class StateMode { JWT, OPAQUE }

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
    val scopes: List<String> = listOf("openid", "profile", "email"),
    val identifierClaimName: String = "sub",
    val enabled: Boolean = true,
    val authorizationEndpointOverride: String? = null,
    val tokenEndpointOverride: String? = null,
    val userinfoEndpointOverride: String? = null,
    val callbackPath: String = "/federation/callback",
    val stateMode: StateMode = StateMode.JWT,
    /**
     * Whether to fetch the upstream `.well-known/openid-configuration` on first use
     * (the default). When false, callers must supply the `*Override` fields for any
     * endpoint they need + the provider is used in "manual metadata" mode. Primarily
     * useful for non-spec-compliant upstreams or air-gapped deployments.
     */
    val discoveryEnabled: Boolean = true,
)
