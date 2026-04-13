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

package com.sphereon.oauth2.common.config

/**
 * Provider for OAuth2 authorization server configuration.
 *
 * Implementations resolve configuration from the App-Tenant-Principal hierarchy.
 *
 * The [serverConfig] property provides the active/default configuration profile
 * for the current context. Commands should use this rather than resolving
 * server IDs manually.
 */
interface OAuth2ServersConfigProvider {
    /**
     * The active server configuration profile.
     *
     * This is the resolved default server config, acting as the current
     * config profile. Commands and handlers should use this property for
     * all config lookups (lifetimes, feature policies, auth methods, etc.).
     */
    val serverConfig: OAuth2ServerInstanceConfig
        get() = getDefaultServer()

    fun getConfig(): OAuth2ServersConfig

    fun getServer(id: String): OAuth2ServerInstanceConfig?

    fun getDefaultServer(): OAuth2ServerInstanceConfig

    /**
     * Resolve the effective issuer for a server instance.
     *
     * Resolution order:
     * 1. Explicit `issuer` on the config (takes precedence)
     * 2. `issuerTemplate` with `{tenant-id}` interpolated
     * 3. Fall back to `baseUrl`
     */
    fun resolveIssuer(
        serverId: String,
        tenantId: String,
    ): String
}
