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

import com.sphereon.identity.idv.model.AttributePredicate
import kotlinx.serialization.Serializable

@Serializable
data class ReconciliationSelectorRule(
    val id: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val tenants: Set<String>? = null,
    val entryPointTypes: Set<String>? = null,
    val triggerTypes: Set<String>? = null,
    val queryIds: Set<String>? = null,
    val dcqlCredentialQueryIds: Set<String>? = null,
    val dcqlCredentialSetRefs: Set<String>? = null,
    val credentialTypes: Set<String>? = null,
    val issuers: Set<String>? = null,
    val attributePredicates: List<AttributePredicate>? = null,
    val knownHolderStates: Set<KnownHolderState>? = null,
    val requestedProjections: Set<String>? = null,
    val plan: ReconciliationPlanTemplate,
)
