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

import com.sphereon.attribute.flow.AttributeBag
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

@JsExportCompat
@Serializable
data class ReconciliationSelectorInput
    @JvmOverloads
    constructor(
        val tenantId: String,
        val entryPointType: String? = null,
        val queryId: String? = null,
        val triggerType: String? = null,
        @JsExportIgnoreCompat
        val dcqlCredentialQueryIds: Set<String> = emptySet(),
        @JsExportIgnoreCompat
        val dcqlCredentialSetRefs: Set<String> = emptySet(),
        @JsExportIgnoreCompat
        val presentedCredentialIds: Set<String> = emptySet(),
        @JsExportIgnoreCompat
        val presentedCredentialTypes: Set<String> = emptySet(),
        @JsExportIgnoreCompat
        val issuers: Set<String> = emptySet(),
        val availableAttributes: AttributeBag? = null,
        val knownHolderState: KnownHolderState? = null,
        val requestedProjection: String? = null,
    )
