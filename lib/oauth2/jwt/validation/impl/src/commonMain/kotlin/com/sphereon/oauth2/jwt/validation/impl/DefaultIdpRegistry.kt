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
 *
 */

package com.sphereon.oauth2.jwt.validation.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.jwt.validation.IdpConfig
import com.sphereon.oauth2.jwt.validation.IdpRegistry
import com.sphereon.oauth2.jwt.validation.JwtValidationConfig
import com.sphereon.oauth2.jwt.validation.JwtValidationError
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Default implementation of IdpRegistry.
 *
 * Manages Identity Provider configurations with support for:
 * - Default IdP for general requests
 * - Per-tenant IdP overrides
 * - Dynamic IdP discovery by issuer
 *
 * Scoped to [AppScope] so configured IdPs are loaded once at startup and
 * `registerIdp` mutations persist across sessions. Reads and writes are
 * guarded by [SynchronizedObject] so the mutable maps are safe under
 * concurrent request traffic. Dynamic per-tenant overrides beyond the
 * config-loaded set are provided by an EDK wrapper, not this default.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<IdpRegistry>())
class DefaultIdpRegistry(
    private val config: JwtValidationConfig,
) : SynchronizedObject(),
    IdpRegistry {
    private val idpsById = mutableMapOf<String, IdpConfig>()
    private val tenantIdps = mutableMapOf<String, IdpConfig>()

    init {
        config.defaultIdp?.let {
            idpsById[it.id] = it
        }
        config.tenantIdps.forEach { (tenantId, idpConfig) ->
            idpsById[idpConfig.id] = idpConfig
            tenantIdps[tenantId] = idpConfig
        }
    }

    override fun getDefaultIdp(): IdkResult<IdpConfig, JwtValidationError> =
        config.defaultIdp?.let { Ok(it) }
            ?: Err(JwtValidationError.idpConfigurationError("No default IdP configured"))

    override fun getIdpForTenant(tenantId: String): IdkResult<IdpConfig, JwtValidationError> {
        val tenantIdp = synchronized(this) { tenantIdps[tenantId] }
        tenantIdp?.let { return Ok(it) }
        return getDefaultIdp()
    }

    override fun getIdpByIssuer(issuer: String): IdkResult<IdpConfig, JwtValidationError> {
        val normalizedIssuer = issuer.trimEnd('/')

        val snapshot = synchronized(this) { idpsById.values.toList() }
        val matchingIdp =
            snapshot.firstOrNull { idp ->
                idp.issuer.trimEnd('/') == normalizedIssuer
            }

        if (matchingIdp != null) {
            return Ok(matchingIdp)
        }

        // In strict mode, an unknown issuer never falls back to the default IdP — the
        // caller gets UntrustedIssuer so upstream token validation fails closed.
        if (config.strictIssuerMatching) {
            return Err(JwtValidationError.untrustedIssuer(issuer, snapshot.map { it.issuer }))
        }

        return config.defaultIdp?.let { Ok(it) }
            ?: Err(JwtValidationError.untrustedIssuer(issuer, snapshot.map { it.issuer }))
    }

    override fun getIdpById(idpId: String): IdkResult<IdpConfig, JwtValidationError> {
        val idp = synchronized(this) { idpsById[idpId] }
        return idp?.let { Ok(it) }
            ?: Err(JwtValidationError.idpConfigurationError("IdP not found: $idpId"))
    }

    override fun getAllIdps(): List<IdpConfig> = synchronized(this) { idpsById.values.toList() }

    override fun isTrustedIssuer(issuer: String): Boolean {
        val normalizedIssuer = issuer.trimEnd('/')
        val snapshot = synchronized(this) { idpsById.values.toList() }
        return snapshot.any { idp ->
            idp.issuer.trimEnd('/') == normalizedIssuer
        }
    }

    override fun registerIdp(config: IdpConfig) {
        synchronized(this) {
            idpsById[config.id] = config
        }
    }

    override fun registerTenantIdp(
        tenantId: String,
        config: IdpConfig,
    ) {
        synchronized(this) {
            idpsById[config.id] = config
            tenantIdps[tenantId] = config
        }
    }

    override fun removeIdp(idpId: String): Boolean =
        synchronized(this) {
            val tenantsToRemove = tenantIdps.entries.filter { it.value.id == idpId }.map { it.key }
            tenantsToRemove.forEach { tenantIdps.remove(it) }
            idpsById.remove(idpId) != null
        }
}
