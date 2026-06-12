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

package com.sphereon.openid.oid4vci.issuer.impl.config

import com.sphereon.data.store.credential.design.CredentialDesignService
import com.sphereon.data.store.credential.design.impl.mapper.Oid4vciDesignMapper
import com.sphereon.data.store.credential.design.model.DesignBinding
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * [Oid4vciIssuerConfigProvider] backed by the credential-design store.
 *
 * For each credential design that has at least one [com.sphereon.data.store.credential.design.model.DesignBinding]
 * with a non-null `credentialConfigurationId`, this provider:
 * 1. Resolves the full design (including render variants).
 * 2. Converts it to a [CredentialConfigurationSupported] entry via [Oid4vciDesignMapper].
 * 3. Returns the aggregated map as [credentialConfigurations].
 *
 * NOT annotated with `@ContributesBinding` — deployment wiring decides which
 * [Oid4vciIssuerConfigProvider] implementation is active in a given session.
 *
 * ## Per-instance targeting
 * Unlike the config-backed providers ([AbstractConfigOid4vciIssuerConfigProvider] and its
 * subclasses), this provider has no config namespace to route: its data source is the
 * credential-design store, scoped by [tenantId], and its issuer identity ([issuerIdentifier],
 * [authorizationServers], [display]) is supplied at construction rather than read from a
 * `oid4vci.issuer(.s.<id>)` namespace. It is therefore already instance-relative through its
 * constructor — the deployment wiring that knows the active issuer instance passes that instance's
 * identifier/authorization-servers/display (and the per-instance [tenantId]) when constructing it,
 * so design aggregation reads under exactly the selected instance. There is no shared config-read
 * body to extract into the abstract base.
 */
@Inject
@SingleIn(SessionScope::class)
class DesignBackedOid4vciIssuerConfigProvider(
    override val issuerIdentifier: String,
    private val tenantId: String,
    private val designService: CredentialDesignService,
    override val authorizationServers: List<String>? = null,
    override val display: List<DisplayProperties>? = null,
) : Oid4vciIssuerConfigProvider {
    /**
     * Credential configurations built from the credential-design store.
     *
     * Must be populated by calling [buildCredentialConfigurationsAsync] before access.
     * Defaults to an empty map until [buildCredentialConfigurationsAsync] is called.
     */
    private var _credentialConfigurations: Map<String, CredentialConfigurationSupported> = emptyMap()

    override val credentialConfigurations: Map<String, CredentialConfigurationSupported>
        get() = _credentialConfigurations

    /**
     * Loads all credential designs for the tenant, filters those with a
     * `credentialConfigurationId` binding, resolves each, and converts to
     * OID4VCI credential configurations. Stores the result in [credentialConfigurations].
     *
     * Callers must invoke this before accessing [credentialConfigurations].
     */
    suspend fun buildCredentialConfigurationsAsync(): Map<String, CredentialConfigurationSupported> {
        val allDesigns =
            designService
                .listCredentialDesigns(tenantId)
                .getOrElse { return emptyMap() }

        val result = mutableMapOf<String, CredentialConfigurationSupported>()

        for (designRecord in allDesigns) {
            // Only process designs that have at least one credentialConfigurationId binding
            val configIdBindings = designRecord.bindings.filter { it.credentialConfigurationId != null }
            if (configIdBindings.isEmpty()) {
                continue
            }

            val resolved =
                designService
                    .resolveCredentialDesign(tenantId, ResolveCredentialDesignInput(designId = designRecord.id))
                    .getOrElse { continue }

            for (binding in configIdBindings) {
                val configId = binding.credentialConfigurationId ?: continue
                val format = resolveFormat(binding)
                val config = Oid4vciDesignMapper.toCredentialConfiguration(resolved, format)
                result[configId] = config
            }
        }

        _credentialConfigurations = result
        return result
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Determines the OID4VCI format string from a [DesignBinding].
     *
     * Priority:
     * 1. If [DesignBinding.vct] is set → `dc+sd-jwt`
     * 2. If [DesignBinding.docType] is set → `mso_mdoc`
     * 3. If [DesignBinding.vcContext] is set → `ldp_vc`
     * 4. If [DesignBinding.vcType] is set → `jwt_vc_json`
     * 5. Default → `dc+sd-jwt`
     */
    private fun resolveFormat(binding: DesignBinding): String =
        when {
            binding.vct != null -> CredentialFormat.SD_JWT_DC.value
            binding.docType != null -> CredentialFormat.MSO_MDOC.value
            binding.vcContext != null -> "ldp_vc"
            binding.vcType != null -> CredentialFormat.JWT_VC_JSON.value
            else -> CredentialFormat.SD_JWT_DC.value
        }
}
