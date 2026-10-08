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
data class ReconciliationMaterialProfile(
    val id: String,
    val version: String,
    val materials: List<ReconciliationMaterial>,
)

@JsExportCompat
@Serializable
sealed interface ReconciliationMaterial

@Serializable
data class HolderKeyMaterial
    @JvmOverloads
    constructor(
        val hmacDomain: String = "holder",
    ) : ReconciliationMaterial

@Serializable
data class ProviderSubjectMaterial
    @JvmOverloads
    constructor(
        val providerId: String,
        val hmacDomain: String = "institution",
    ) : ReconciliationMaterial

@Serializable
data class AttributeTupleMaterial(
    val attributePaths: List<String>,
    val normalizationProfile: String,
    val saltRef: String,
    val hmacDomain: String,
    val minRequiredAttributes: Int,
) : ReconciliationMaterial

@Serializable
data class CredentialAttributeTupleMaterial
    @JvmOverloads
    constructor(
        val credentialQueryId: String? = null,
        val credentialId: String? = null,
        val attributePaths: List<String>,
        val normalizationProfile: String,
        val saltRef: String,
        val hmacDomain: String,
        val minRequiredAttributes: Int,
    ) : ReconciliationMaterial
