/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.credential.design.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

@Serializable
data class CreateCredentialDesignInput(
    val bindings: List<DesignBinding>,
    val alias: String? = null,
    val hostingMode: DesignHostingMode = DesignHostingMode.LOCAL,
    val credentialTemplateId: Uuid? = null,
    val issuerDesignId: Uuid? = null,
    val displays: List<LocalizedCredentialDisplay>,
    val claims: List<ClaimPresentation> = emptyList(),
    val renderVariantIds: List<Uuid> = emptyList(),
)

@Serializable
data class UpdateCredentialDesignInput(
    val alias: String? = null,
    val bindings: List<DesignBinding>? = null,
    val credentialTemplateId: Uuid? = null,
    val issuerDesignId: Uuid? = null,
    val displays: List<LocalizedCredentialDisplay>? = null,
    val claims: List<ClaimPresentation>? = null,
    val renderVariantIds: List<Uuid>? = null,
    val hostingMode: DesignHostingMode? = null,
)

@Serializable
data class CreateIssuerDesignInput(
    val bindings: List<DesignBinding>,
    val partyId: Uuid? = null,
    val alias: String? = null,
    val hostingMode: DesignHostingMode = DesignHostingMode.LOCAL,
    val displays: List<EntityLocaleDesign>,
    val renderVariantIds: List<Uuid> = emptyList(),
)

@Serializable
data class UpdateIssuerDesignInput(
    val alias: String? = null,
    val bindings: List<DesignBinding>? = null,
    val partyId: Uuid? = null,
    val displays: List<EntityLocaleDesign>? = null,
    val renderVariantIds: List<Uuid>? = null,
)

@Serializable
data class CreateVerifierDesignInput(
    val bindings: List<DesignBinding>,
    val partyId: Uuid? = null,
    val alias: String? = null,
    val hostingMode: DesignHostingMode = DesignHostingMode.LOCAL,
    val displays: List<EntityLocaleDesign>,
    val renderVariantIds: List<Uuid> = emptyList(),
)

@Serializable
data class UpdateVerifierDesignInput(
    val alias: String? = null,
    val bindings: List<DesignBinding>? = null,
    val partyId: Uuid? = null,
    val displays: List<EntityLocaleDesign>? = null,
    val renderVariantIds: List<Uuid>? = null,
)

@Serializable
data class CreateRenderVariantInput(
    val kind: RenderVariantKind,
    val alias: String? = null,
    val localeApplicability: List<String> = emptyList(),
    val logo: AssetReference? = null,
    val backgroundImage: AssetReference? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
    val accentColor: String? = null,
    val svgTemplate: SvgTemplate? = null,
    val w3cRenderMethod: W3cRenderMethodReference? = null,
)

@Serializable
data class ImportExternalDesignInput(
    val entityType: DesignEntityType,
    val bindings: List<DesignBinding>,
    val alias: String? = null,
    val sourceUrl: String,
    val sourceType: DesignSourceType,
)

@Serializable
data class ResolveCredentialDesignInput(
    val designId: Uuid? = null,
    val binding: DesignBinding? = null,
    val bindingKey: DesignBindingKey? = null,
    val bindingValue: String? = null,
    val preferredLocales: List<String> = emptyList(),
    val renderTarget: RenderVariantKind? = null,
    val externalMetadata: ExternalDesignMetadata? = null,
)

@Serializable
data class ResolveEntityDesignInput(
    val designId: Uuid? = null,
    val binding: DesignBinding? = null,
    val bindingKey: DesignBindingKey? = null,
    val bindingValue: String? = null,
    val preferredLocales: List<String> = emptyList(),
    val externalMetadata: ExternalDesignMetadata? = null,
)

@Serializable
data class ExternalDesignMetadata(
    val sdJwtVctMetadata: JsonObject? = null,
    val oid4vciCredentialConfiguration: JsonObject? = null,
    val oid4vciIssuerMetadata: JsonObject? = null,
    val sdJwtIssuerMetadata: JsonObject? = null,
    val jsonSchema: JsonObject? = null,
    val jsonLdContext: JsonObject? = null,
    val oidcDiscovery: JsonObject? = null,
    val openIdFederationEntity: JsonObject? = null,
    val oauthClientRegistration: JsonObject? = null,
    val eidasRegistryData: JsonObject? = null,
    val eidasCatalogueData: JsonObject? = null,
    val w3cRenderMethod: JsonObject? = null,
    val ocaBundle: JsonObject? = null,
    val ocaCaptureBase: JsonObject? = null,
    val ocaOverlays: List<JsonObject> = emptyList(),
    val ocaFile: String? = null,
    val overlayFiles: List<String> = emptyList(),
)

@Serializable
data class DesignFilter(
    val entityType: DesignEntityType? = null,
    val hostingMode: DesignHostingMode? = null,
    val binding: DesignBinding? = null,
    val bindingKey: DesignBindingKey? = null,
    val bindingValue: String? = null,
    val aliasContains: String? = null,
    val sourceType: DesignSourceType? = null,
)

@Serializable
data class UploadDesignAssetInput(
    val designId: Uuid,
    val locale: String,
    val assetType: DesignAssetType,
    val data: ByteArray,
    val contentType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is UploadDesignAssetInput) {
            return false
        }
        return designId == other.designId && locale == other.locale && assetType == other.assetType &&
            data.contentEquals(other.data) && contentType == other.contentType
    }

    override fun hashCode(): Int {
        var result = designId.hashCode()
        result = 31 * result + locale.hashCode()
        result = 31 * result + assetType.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }
}

@Serializable
data class GetDesignAssetInput(
    val designId: Uuid,
    val locale: String,
    val assetType: DesignAssetType,
)

@Serializable
data class ResolvedDesignAsset(
    val data: ByteArray,
    val contentType: String,
    val reference: AssetReference,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ResolvedDesignAsset) {
            return false
        }
        return data.contentEquals(other.data) && contentType == other.contentType && reference == other.reference
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + reference.hashCode()
        return result
    }
}
