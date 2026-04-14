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
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

@JsExportCompat
@Serializable
enum class MappingMode {
    /** Use inline attribute-mappings from config. */
    EXPLICIT,

    /** Derive attribute mappings at runtime from canonical attribute rules. */
    DERIVE_FROM_RULES,
}

@JsExportCompat
@Serializable
data class ReconciliationProvider
    @JvmOverloads
    constructor(
        val id: String,
        val name: String? = null,
        val oidcClientId: String,
        val identifierAttributeName: String = "sub",
        val enabled: Boolean = true,
        val mappingMode: MappingMode = MappingMode.EXPLICIT,
        val attributeMappings: List<ReconciliationAttributeMapping> = emptyList(),
        val userInfoAttributeMappings: List<ReconciliationAttributeMapping> = emptyList(),
        val assuranceAcr: String? = null,
        val assuranceAmr: List<String>? = null,
    )
