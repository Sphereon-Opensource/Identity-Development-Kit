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

package com.sphereon.oauth2.server.authorization.provider

import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig

/**
 * Tenant-scoped registry of upstream federation providers. Replaces what used to be a raw
 * `Map<String, FederationProviderConfig>` plus a `defaultProviderId: String` constructor argument
 * on `AbstractFederatedUserAuthenticationProvider` so EDK can override the source of provider
 * configuration without subclassing the federation flow.
 *
 * Pure-IDK deployments get the default empty registry (federation effectively disabled). EDK
 * contributes a registry backed by `ConfigService` / `PropertyResolver`, replacing the IDK default
 * via `replaces = [DefaultEmptyFederationProviderRegistry::class]`.
 */
interface FederationProviderRegistry {
    /** Resolve a single provider by id, or `null` when no such provider exists. */
    fun findById(providerId: String): FederationProviderConfig?

    /** Return all configured providers (enabled or disabled). */
    fun all(): List<FederationProviderConfig>

    /** Subset of [all] limited to enabled providers. Used by the `/federation/providers` endpoint. */
    fun enabled(): List<FederationProviderConfig> = all().filter { it.enabled }

    /**
     * Default provider id used when an authentication request specifies no explicit provider.
     * Returns `null` when no default has been configured; the federation flow then errors out
     * unless the caller supplies a `providerId` in the authentication hint.
     */
    fun defaultProviderId(): String?
}
