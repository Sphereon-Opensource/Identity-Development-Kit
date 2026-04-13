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

package com.sphereon.identity.reconciliation.impl.crypto

import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.kms.command.GenerateMacArgs
import com.sphereon.crypto.core.kms.command.GenerateMacCommand
import com.sphereon.identity.idv.model.AttributeBag
import com.sphereon.identity.idv.model.AttributePath
import com.sphereon.identity.matching.crypto.HashedIdentifier
import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.reconciliation.api.DerivedMaterial
import com.sphereon.identity.reconciliation.api.NormalizationService
import com.sphereon.identity.reconciliation.api.ReconciliationMaterialService
import com.sphereon.identity.reconciliation.model.AttributeTupleMaterial
import com.sphereon.identity.reconciliation.model.CanonicalAttributeBag
import com.sphereon.identity.reconciliation.model.CredentialAttributeTupleMaterial
import com.sphereon.identity.reconciliation.model.HolderKeyMaterial
import com.sphereon.identity.reconciliation.model.ProviderSubjectMaterial
import com.sphereon.identity.reconciliation.model.ReconciliationMaterial
import com.sphereon.identity.reconciliation.model.ReconciliationMaterialProfile
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class KmsBackedReconciliationMaterialService(
    private val generateMacCommand: GenerateMacCommand,
    private val normalizationService: NormalizationService,
    private val keyAliasPrefix: String = "reconciliation",
    private val keyVersionsByDomain: Map<String, String> =
        mapOf(
            "holder" to "v1",
            "institution" to "v1",
            "material" to "v1",
        ),
    private val previousKeyVersionsByDomain: Map<String, String> = emptyMap(),
    private val providerId: String = "software",
) : ReconciliationMaterialService {
    override suspend fun deriveMaterials(
        profile: ReconciliationMaterialProfile,
        holderKey: String?,
        providerSubject: String?,
        canonicalAttributes: CanonicalAttributeBag,
        credentialScopedAttributes: Map<String, AttributeBag>?,
    ): List<DerivedMaterial> =
        profile.materials.mapNotNull { material ->
            deriveSingle(material, holderKey, providerSubject, canonicalAttributes, credentialScopedAttributes, profile, current = true)
        }

    override suspend fun deriveMaterialsWithPrevious(
        profile: ReconciliationMaterialProfile,
        holderKey: String?,
        providerSubject: String?,
        canonicalAttributes: CanonicalAttributeBag,
        credentialScopedAttributes: Map<String, AttributeBag>?,
    ): List<DerivedMaterial> =
        profile.materials.mapNotNull { material ->
            deriveSingle(material, holderKey, providerSubject, canonicalAttributes, credentialScopedAttributes, profile, current = false)
        }

    private suspend fun deriveSingle(
        material: ReconciliationMaterial,
        holderKey: String?,
        providerSubject: String?,
        canonicalAttributes: CanonicalAttributeBag,
        credentialScopedAttributes: Map<String, AttributeBag>?,
        profile: ReconciliationMaterialProfile,
        current: Boolean,
    ): DerivedMaterial? {
        return when (material) {
            is HolderKeyMaterial -> {
                holderKey ?: return null
                val hash = hmac(holderKey, material.hmacDomain, current)
                DerivedMaterial(hash, IdentifierType.KEY, "holder_key", profile.id, profile.version)
            }

            is ProviderSubjectMaterial -> {
                providerSubject ?: return null
                val hash = hmac(providerSubject, material.hmacDomain, current)
                DerivedMaterial(hash, IdentifierType.SUBJECT_ID, "provider_subject", profile.id, profile.version)
            }

            is AttributeTupleMaterial -> {
                val input =
                    buildTupleInput(
                        material.attributePaths,
                        material.normalizationProfile,
                        material.saltRef,
                        canonicalAttributes.attributes,
                        material.minRequiredAttributes,
                    ) ?: return null
                val hash = hmac(input, material.hmacDomain, current)
                DerivedMaterial(hash, IdentifierType.CLAIM_TUPLE, "attribute_tuple", profile.id, profile.version)
            }

            is CredentialAttributeTupleMaterial -> {
                val scopedAttributes =
                    resolveCredentialAttributes(material, credentialScopedAttributes)
                        ?: return null
                val input =
                    buildTupleInput(
                        material.attributePaths,
                        material.normalizationProfile,
                        material.saltRef,
                        scopedAttributes,
                        material.minRequiredAttributes,
                    ) ?: return null
                val hash = hmac(input, material.hmacDomain, current)
                DerivedMaterial(hash, IdentifierType.CLAIM_TUPLE, "credential_attribute_tuple", profile.id, profile.version)
            }
        }
    }

    private fun resolveCredentialAttributes(
        material: CredentialAttributeTupleMaterial,
        credentialScopedAttributes: Map<String, AttributeBag>?,
    ): Map<String, JsonElement>? {
        if (credentialScopedAttributes == null) {
            return null
        }
        val key = material.credentialQueryId ?: material.credentialId ?: return null
        val bag = credentialScopedAttributes[key] ?: return null
        return bag.attributes.mapKeys { (k, _) -> k.value }
    }

    private fun buildTupleInput(
        attributePaths: List<String>,
        normalizationProfile: String,
        saltRef: String,
        attributes: Map<String, JsonElement>,
        minRequiredAttributes: Int,
    ): String? {
        val values =
            attributePaths.mapNotNull { path ->
                attributes[path]?.let { element ->
                    val raw =
                        when (element) {
                            is JsonPrimitive -> element.content
                            else -> element.toString()
                        }
                    normalizationService.normalize(raw, normalizationProfile)
                }
            }
        if (values.size < minRequiredAttributes) {
            return null
        }
        // Sort normalized values for deterministic HMAC regardless of attribute path ordering
        return saltRef + "|" + values.sorted().joinToString("|")
    }

    private suspend fun hmac(
        input: String,
        domain: String,
        current: Boolean,
    ): HashedIdentifier {
        val alias = "$keyAliasPrefix:$domain"
        val versions =
            if (current) {
                keyVersionsByDomain
            } else {
                previousKeyVersionsByDomain
            }
        val version =
            checkNotNull(versions[domain]) {
                "No ${if (current) {
                    "current"
                } else {
                    "previous"
                }} key version for domain '$domain'"
            }
        val args =
            GenerateMacArgs(
                keyId = alias,
                message = input.encodeToByteArray(),
                digestAlgorithm = DigestAlg.SHA256,
                providerId = providerId,
            )
        val result = generateMacCommand.execute(args)
        val macResult =
            checkNotNull(result.getOrNull()) { "HMAC failed for domain '$domain': ${result.errorOrNull()}" }
        return HashedIdentifier(hash = macResult.macMultibase, keyVersion = version)
    }
}
