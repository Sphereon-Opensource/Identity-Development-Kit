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

package com.sphereon.oauth2.jwt.validation.impl

import com.sphereon.oauth2.jwt.validation.IdpConfig
import com.sphereon.oauth2.jwt.validation.IdpRegistry
import com.sphereon.oauth2.jwt.validation.JwtValidationConfig
import com.sphereon.oauth2.jwt.validation.JwtValidationError
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default implementation of IdpRegistry.
 *
 * Manages Identity Provider configurations with support for:
 * - Default IdP for general requests
 * - Per-tenant IdP overrides
 * - Dynamic IdP discovery by issuer
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<IdpRegistry>())
class DefaultIdpRegistry(
    private val config: JwtValidationConfig
) : IdpRegistry {

    // Mutable registries for dynamic registration
    private val idpsById = mutableMapOf<String, IdpConfig>()
    private val tenantIdps = mutableMapOf<String, IdpConfig>()

    init {
        // Register configured IdPs
        config.defaultIdp?.let {
            idpsById[it.id] = it
        }
        config.tenantIdps.forEach { (tenantId, idpConfig) ->
            idpsById[idpConfig.id] = idpConfig
            tenantIdps[tenantId] = idpConfig
        }
    }

    override fun getDefaultIdp(): IdkResult<IdpConfig, JwtValidationError> {
        return config.defaultIdp?.let { Ok(it) }
            ?: Err(JwtValidationError.idpConfigurationError("No default IdP configured"))
    }

    override fun getIdpForTenant(tenantId: String): IdkResult<IdpConfig, JwtValidationError> {
        // First check tenant-specific override
        tenantIdps[tenantId]?.let { return Ok(it) }

        // Fall back to default
        return getDefaultIdp()
    }

    override fun getIdpByIssuer(issuer: String): IdkResult<IdpConfig, JwtValidationError> {
        // Normalize issuer (remove trailing slash)
        val normalizedIssuer = issuer.trimEnd('/')

        // Find IdP matching this issuer
        val matchingIdp = idpsById.values.firstOrNull { idp ->
            idp.issuer.trimEnd('/') == normalizedIssuer
        }

        return matchingIdp?.let { Ok(it) }
            ?: config.defaultIdp?.let { Ok(it) }
            ?: Err(JwtValidationError.untrustedIssuer(issuer, idpsById.values.map { it.issuer }))
    }

    override fun getIdpById(idpId: String): IdkResult<IdpConfig, JwtValidationError> {
        return idpsById[idpId]?.let { Ok(it) }
            ?: Err(JwtValidationError.idpConfigurationError("IdP not found: $idpId"))
    }

    override fun getAllIdps(): List<IdpConfig> {
        return idpsById.values.toList()
    }

    override fun isTrustedIssuer(issuer: String): Boolean {
        val normalizedIssuer = issuer.trimEnd('/')
        return idpsById.values.any { idp ->
            idp.issuer.trimEnd('/') == normalizedIssuer
        }
    }

    override fun registerIdp(config: IdpConfig) {
        idpsById[config.id] = config
    }

    override fun registerTenantIdp(tenantId: String, config: IdpConfig) {
        idpsById[config.id] = config
        tenantIdps[tenantId] = config
    }

    override fun removeIdp(idpId: String): Boolean {
        // Remove from tenant mappings first
        val tenantsToRemove = tenantIdps.entries.filter { it.value.id == idpId }.map { it.key }
        tenantsToRemove.forEach { tenantIdps.remove(it) }
        return idpsById.remove(idpId) != null
    }
}
