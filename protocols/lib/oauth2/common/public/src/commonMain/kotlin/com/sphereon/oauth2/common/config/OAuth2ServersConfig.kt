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

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * Top-level container for all authorization server configurations.
 *
 * Supports multiple named server instances (e.g., "primary" for hosted AS,
 * "keycloak" for external AS).
 */
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
@Serializable
data class OAuth2ServersConfig(
    val defaultServer: String = "default",
    val servers: Map<String, OAuth2ServerInstanceConfig> =
        mapOf(
            "default" to OAuth2ServerInstanceConfig(),
        ),
    /**
     * `false` ONLY for the binder's synthesized bare default — the AS impl is on the classpath but
     * the process declares no server under the `oauth2.servers.*` keyspace (OID4VCI issuer, OID4VP
     * verifier, monolith). `true` for every genuinely-configured AS, whether loaded from config or
     * built directly in code. Lets sign paths tell a hosted AS — which may resolve its `issuer`
     * per-request and therefore run with `issuer == null` — apart from a service that merely bundles
     * the impl. Not part of the on-disk config; defaults to `true` so any hand-constructed config is
     * treated as a real AS, and only the binder's empty-keyspace synthesis opts out (it sets `false`).
     */
    val explicitlyConfigured: Boolean = true,
) {
    fun getServer(id: String): OAuth2ServerInstanceConfig? = servers[id]

    fun getDefaultServer(): OAuth2ServerInstanceConfig =
        servers[defaultServer]
            ?: error("Default server '$defaultServer' not found in oauth2.servers config")

    fun hostedServers(): Map<String, OAuth2ServerInstanceConfig> = servers.filterValues { it.mode == AuthorizationServerMode.HOSTED }

    fun externalServers(): Map<String, OAuth2ServerInstanceConfig> = servers.filterValues { it.mode == AuthorizationServerMode.EXTERNAL }
}
