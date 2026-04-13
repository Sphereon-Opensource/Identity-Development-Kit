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

package com.sphereon.identity.reconciliation.model

import kotlinx.serialization.Serializable

@Serializable
enum class CanonicalMergeMode {
    WALLET_ONLY,
    OIDC_ONLY,
    OIDC_WINS,
    WALLET_WINS,
    MERGE_ALL,
}

@Serializable
data class CanonicalAttributeRule(
    val canonicalName: String,
    val mergeMode: CanonicalMergeMode,
    val required: Boolean = false,
    val persist: Boolean = false,
    val project: Boolean = false,
    /**
     * Per-provider source aliases. Key = provider ID (e.g., "surf", "keycloak").
     * Value = source claim name at that provider.
     * Keys are strictly provider IDs — no reserved names.
     * When no alias exists for a provider, the canonical name is used as source.
     */
    val sourceAliases: Map<String, String> = emptyMap(),
)
