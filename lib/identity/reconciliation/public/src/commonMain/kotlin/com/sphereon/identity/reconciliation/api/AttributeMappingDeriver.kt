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

package com.sphereon.identity.reconciliation.api

import com.sphereon.attribute.mapping.AttributeMapping
import com.sphereon.identity.reconciliation.model.CanonicalAttributeRule
import com.sphereon.identity.reconciliation.model.CanonicalMergeMode
import com.sphereon.identity.reconciliation.model.CanonicalMergeMode.MERGE_ALL
import com.sphereon.identity.reconciliation.model.CanonicalMergeMode.OIDC_ONLY
import com.sphereon.identity.reconciliation.model.CanonicalMergeMode.OIDC_WINS
import com.sphereon.identity.reconciliation.model.CanonicalMergeMode.WALLET_ONLY
import com.sphereon.identity.reconciliation.model.CanonicalMergeMode.WALLET_WINS

/**
 * Derives attribute mappings from canonical attribute rules for a specific provider.
 *
 * Instead of maintaining duplicate mapping lists in config, mappings are derived
 * from the single source of truth: canonical attribute rules with per-provider
 * source aliases.
 */
object AttributeMappingDeriver {
    /** Merge modes that include OIDC-sourced attributes. */
    val OIDC_MODES: Set<CanonicalMergeMode> = setOf(OIDC_ONLY, OIDC_WINS, WALLET_WINS, MERGE_ALL)

    /** Merge modes that include wallet-sourced attributes. */
    val WALLET_MODES: Set<CanonicalMergeMode> = setOf(WALLET_ONLY, WALLET_WINS, OIDC_WINS, MERGE_ALL)

    /**
     * Derive attribute mappings for a specific provider from canonical rules.
     *
     * For each rule whose merge-mode is in [allowedModes]:
     * - If a source-alias exists for [providerId], use it as the source name
     * - Otherwise, use the canonical name as the source name (identity mapping)
     *
     * @param rules The canonical attribute rules
     * @param providerId The provider ID to look up aliases for (e.g., "surf", "keycloak")
     * @param allowedModes Which merge modes to include (use [OIDC_MODES] or [WALLET_MODES])
     */
    fun deriveForProvider(
        rules: List<CanonicalAttributeRule>,
        providerId: String,
        allowedModes: Set<CanonicalMergeMode>,
    ): List<AttributeMapping> =
        rules
            .filter { it.mergeMode in allowedModes }
            .map { rule ->
                AttributeMapping(
                    source = rule.sourceAliases[providerId] ?: rule.canonicalName,
                    target = rule.canonicalName,
                    required = rule.required,
                )
            }
}
