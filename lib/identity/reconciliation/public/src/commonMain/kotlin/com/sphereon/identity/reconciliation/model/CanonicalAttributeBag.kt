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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmOverloads

@JsExportCompat
@Serializable
data class CanonicalAttributeBag
    @JvmOverloads
    constructor(
        @JsExportIgnoreCompat
        val attributes: Map<String, JsonElement>,
        val provenance: AttributeProvenanceSummary,
        val canonicalSchemaVersion: String = "1",
        val selectorRuleVersion: String? = null,
        val materialProfileVersion: String? = null,
    )

@JsExportCompat
@Serializable
data class AttributeProvenanceSummary
    @JvmOverloads
    constructor(
        @JsExportIgnoreCompat
        val sources: Map<String, Set<String>> = emptyMap(),
        @JsExportIgnoreCompat
        val providerIds: Set<String> = emptySet(),
        @JsExportIgnoreCompat
        val walletCredentialIds: Set<String> = emptySet(),
    )
