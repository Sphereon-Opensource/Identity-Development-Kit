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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.identity.idv.model.AttributePredicate
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

@JsExportCompat
@Serializable
data class ReconciliationSelectorRule
    @JvmOverloads
    constructor(
        val id: String,
        val enabled: Boolean = true,
        val priority: Int = 0,
        @JsExportIgnoreCompat
        val tenants: Set<String>? = null,
        @JsExportIgnoreCompat
        val entryPointTypes: Set<String>? = null,
        @JsExportIgnoreCompat
        val triggerTypes: Set<String>? = null,
        @JsExportIgnoreCompat
        val queryIds: Set<String>? = null,
        @JsExportIgnoreCompat
        val dcqlCredentialQueryIds: Set<String>? = null,
        @JsExportIgnoreCompat
        val dcqlCredentialSetRefs: Set<String>? = null,
        @JsExportIgnoreCompat
        val credentialTypes: Set<String>? = null,
        @JsExportIgnoreCompat
        val issuers: Set<String>? = null,
        val attributePredicates: List<AttributePredicate>? = null,
        @JsExportIgnoreCompat
        val knownHolderStates: Set<KnownHolderState>? = null,
        @JsExportIgnoreCompat
        val requestedProjections: Set<String>? = null,
        val plan: ReconciliationPlanTemplate,
    )
