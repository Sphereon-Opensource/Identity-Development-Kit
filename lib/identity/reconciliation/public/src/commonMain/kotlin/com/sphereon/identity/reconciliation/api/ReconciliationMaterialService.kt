/*
 * Copyright 2026 Sphereon International B.V.
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

import com.sphereon.identity.idv.model.AttributeBag
import com.sphereon.identity.matching.crypto.HashedIdentifier
import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.reconciliation.model.CanonicalAttributeBag
import com.sphereon.identity.reconciliation.model.ReconciliationMaterialProfile
import kotlinx.serialization.Serializable

interface ReconciliationMaterialService {

    suspend fun deriveMaterials(
        profile: ReconciliationMaterialProfile,
        holderKey: String?,
        providerSubject: String?,
        canonicalAttributes: CanonicalAttributeBag,
        credentialScopedAttributes: Map<String, AttributeBag>? = null,
    ): List<DerivedMaterial>

    suspend fun deriveMaterialsWithPrevious(
        profile: ReconciliationMaterialProfile,
        holderKey: String?,
        providerSubject: String?,
        canonicalAttributes: CanonicalAttributeBag,
        credentialScopedAttributes: Map<String, AttributeBag>? = null,
    ): List<DerivedMaterial>
}

@Serializable
data class DerivedMaterial(
    val hash: HashedIdentifier,
    val identifierType: IdentifierType,
    val materialType: String,
    val profileId: String,
    val profileVersion: String,
)
