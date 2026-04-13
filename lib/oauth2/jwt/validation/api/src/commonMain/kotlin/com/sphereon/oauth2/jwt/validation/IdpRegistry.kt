/*
 * © 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.oauth2.jwt.validation

import com.sphereon.core.api.IdkResult

/**
 * Registry for Identity Provider configurations.
 *
 * Supports multi-IdP environments with tenant-specific routing:
 * - Default IdP for general requests
 * - Per-tenant IdP overrides
 * - Dynamic IdP discovery from token issuer
 *
 * Designed for multiple deployment scenarios:
 * - Kubernetes (configmap-based config)
 * - Docker Compose (environment variables)
 * - Bare Metal (file-based config)
 */
interface IdpRegistry {
    /**
     * Get the default IdP configuration.
     *
     * @return Default IdP config or error if not configured
     */
    fun getDefaultIdp(): IdkResult<IdpConfig, JwtValidationError>

    /**
     * Get IdP configuration for a specific tenant.
     *
     * Falls back to default IdP if no tenant-specific config exists.
     *
     * @param tenantId The tenant identifier
     * @return IdP config for the tenant
     */
    fun getIdpForTenant(tenantId: String): IdkResult<IdpConfig, JwtValidationError>

    /**
     * Get IdP configuration by issuer URL.
     *
     * Used for dynamic IdP selection based on token's iss claim.
     *
     * @param issuer The issuer URL from the token
     * @return IdP config matching the issuer, or default if no match
     */
    fun getIdpByIssuer(issuer: String): IdkResult<IdpConfig, JwtValidationError>

    /**
     * Get a specific IdP configuration by its ID.
     *
     * @param idpId The IdP configuration ID
     * @return IdP config or error if not found
     */
    fun getIdpById(idpId: String): IdkResult<IdpConfig, JwtValidationError>

    /**
     * Get all registered IdP configurations.
     *
     * @return List of all IdP configs
     */
    fun getAllIdps(): List<IdpConfig>

    /**
     * Check if an issuer is trusted by any registered IdP.
     *
     * @param issuer The issuer URL to check
     * @return true if the issuer matches any registered IdP
     */
    fun isTrustedIssuer(issuer: String): Boolean

    /**
     * Register a new IdP configuration.
     *
     * @param config The IdP configuration to register
     */
    fun registerIdp(config: IdpConfig)

    /**
     * Register a tenant-specific IdP override.
     *
     * @param tenantId The tenant identifier
     * @param config The IdP configuration for this tenant
     */
    fun registerTenantIdp(tenantId: String, config: IdpConfig)

    /**
     * Remove an IdP configuration.
     *
     * @param idpId The IdP configuration ID to remove
     * @return true if the IdP was removed, false if not found
     */
    fun removeIdp(idpId: String): Boolean
}

/**
 * Builder for constructing an IdpRegistry with fluent API.
 */
class IdpRegistryBuilder {
    private var defaultIdp: IdpConfig? = null
    private val idps = mutableMapOf<String, IdpConfig>()
    private val tenantIdps = mutableMapOf<String, IdpConfig>()

    /**
     * Set the default IdP configuration.
     */
    fun withDefaultIdp(config: IdpConfig): IdpRegistryBuilder {
        this.defaultIdp = config
        this.idps[config.id] = config
        return this
    }

    /**
     * Add an IdP configuration.
     */
    fun withIdp(config: IdpConfig): IdpRegistryBuilder {
        this.idps[config.id] = config
        return this
    }

    /**
     * Add a tenant-specific IdP configuration.
     */
    fun withTenantIdp(tenantId: String, config: IdpConfig): IdpRegistryBuilder {
        this.idps[config.id] = config
        this.tenantIdps[tenantId] = config
        return this
    }

    /**
     * Build the configuration.
     */
    fun build(): JwtValidationConfig = JwtValidationConfig(
        enabled = true,
        defaultIdp = defaultIdp,
        tenantIdps = tenantIdps.toMap()
    )
}
