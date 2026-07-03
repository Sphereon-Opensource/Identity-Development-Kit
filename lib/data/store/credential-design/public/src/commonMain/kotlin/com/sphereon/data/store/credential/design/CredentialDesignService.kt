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

package com.sphereon.data.store.credential.design

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.store.credential.design.model.AssetFilter
import com.sphereon.data.store.credential.design.model.AssetInfo
import com.sphereon.data.store.credential.design.model.AssetReference
import com.sphereon.data.store.credential.design.model.CreateCredentialDesignInput
import com.sphereon.data.store.credential.design.model.CreateIssuerDesignInput
import com.sphereon.data.store.credential.design.model.CreateRenderVariantInput
import com.sphereon.data.store.credential.design.model.CreateVerifierDesignInput
import com.sphereon.data.store.credential.design.model.CredentialDesignRecord
import com.sphereon.data.store.credential.design.model.DesignBinding
import com.sphereon.data.store.credential.design.model.DesignBindingKey
import com.sphereon.data.store.credential.design.model.DesignFilter
import com.sphereon.data.store.credential.design.model.GetDesignAssetInput
import com.sphereon.data.store.credential.design.model.ImportExternalDesignInput
import com.sphereon.data.store.credential.design.model.IssuerDesignRecord
import com.sphereon.data.store.credential.design.model.RenderVariantRecord
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.data.store.credential.design.model.ResolveEntityDesignInput
import com.sphereon.data.store.credential.design.model.ResolvedCredentialDesign
import com.sphereon.data.store.credential.design.model.ResolvedDesignAsset
import com.sphereon.data.store.credential.design.model.ResolvedIssuerDesign
import com.sphereon.data.store.credential.design.model.ResolvedVerifierDesign
import com.sphereon.data.store.credential.design.model.SourceSnapshotRecord
import com.sphereon.data.store.credential.design.model.UpdateCredentialDesignInput
import com.sphereon.data.store.credential.design.model.UpdateIssuerDesignInput
import com.sphereon.data.store.credential.design.model.UpdateVerifierDesignInput
import com.sphereon.data.store.credential.design.model.UploadDesignAssetInput
import com.sphereon.data.store.credential.design.model.UploadTenantAssetInput
import com.sphereon.data.store.credential.design.model.VerifierDesignRecord
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding
import kotlin.uuid.Uuid

@JsExportCompat
interface CredentialDesignService {
    // Credential designs
    suspend fun createCredentialDesign(
        tenantId: String,
        input: CreateCredentialDesignInput,
    ): IdkResult<CredentialDesignRecord, IdkError>

    suspend fun getCredentialDesign(
        tenantId: String,
        id: Uuid,
    ): IdkResult<CredentialDesignRecord, IdkError>

    suspend fun findCredentialDesignByBinding(
        tenantId: String,
        binding: DesignBinding,
    ): IdkResult<List<CredentialDesignRecord>, IdkError>

    suspend fun findCredentialDesignByBindingKey(
        tenantId: String,
        bindingKey: DesignBindingKey,
        bindingValue: String,
    ): IdkResult<List<CredentialDesignRecord>, IdkError>

    suspend fun listCredentialDesigns(
        tenantId: String,
        filter: DesignFilter = DesignFilter(),
    ): IdkResult<List<CredentialDesignRecord>, IdkError>

    suspend fun updateCredentialDesign(
        tenantId: String,
        id: Uuid,
        input: UpdateCredentialDesignInput,
    ): IdkResult<CredentialDesignRecord, IdkError>

    suspend fun deleteCredentialDesign(
        tenantId: String,
        id: Uuid,
    ): IdkResult<Boolean, IdkError>

    // Issuer designs
    suspend fun createIssuerDesign(
        tenantId: String,
        input: CreateIssuerDesignInput,
    ): IdkResult<IssuerDesignRecord, IdkError>

    suspend fun getIssuerDesign(
        tenantId: String,
        id: Uuid,
    ): IdkResult<IssuerDesignRecord, IdkError>

    suspend fun findIssuerDesignByBinding(
        tenantId: String,
        binding: DesignBinding,
    ): IdkResult<List<IssuerDesignRecord>, IdkError>

    suspend fun findIssuerDesignByBindingKey(
        tenantId: String,
        bindingKey: DesignBindingKey,
        bindingValue: String,
    ): IdkResult<List<IssuerDesignRecord>, IdkError>

    suspend fun listIssuerDesigns(
        tenantId: String,
        filter: DesignFilter = DesignFilter(),
    ): IdkResult<List<IssuerDesignRecord>, IdkError>

    suspend fun updateIssuerDesign(
        tenantId: String,
        id: Uuid,
        input: UpdateIssuerDesignInput,
    ): IdkResult<IssuerDesignRecord, IdkError>

    suspend fun deleteIssuerDesign(
        tenantId: String,
        id: Uuid,
    ): IdkResult<Boolean, IdkError>

    // Verifier designs
    suspend fun createVerifierDesign(
        tenantId: String,
        input: CreateVerifierDesignInput,
    ): IdkResult<VerifierDesignRecord, IdkError>

    suspend fun getVerifierDesign(
        tenantId: String,
        id: Uuid,
    ): IdkResult<VerifierDesignRecord, IdkError>

    suspend fun findVerifierDesignByBinding(
        tenantId: String,
        binding: DesignBinding,
    ): IdkResult<List<VerifierDesignRecord>, IdkError>

    suspend fun findVerifierDesignByBindingKey(
        tenantId: String,
        bindingKey: DesignBindingKey,
        bindingValue: String,
    ): IdkResult<List<VerifierDesignRecord>, IdkError>

    suspend fun listVerifierDesigns(
        tenantId: String,
        filter: DesignFilter = DesignFilter(),
    ): IdkResult<List<VerifierDesignRecord>, IdkError>

    suspend fun updateVerifierDesign(
        tenantId: String,
        id: Uuid,
        input: UpdateVerifierDesignInput,
    ): IdkResult<VerifierDesignRecord, IdkError>

    suspend fun deleteVerifierDesign(
        tenantId: String,
        id: Uuid,
    ): IdkResult<Boolean, IdkError>

    // Render variants
    suspend fun createRenderVariant(
        tenantId: String,
        input: CreateRenderVariantInput,
    ): IdkResult<RenderVariantRecord, IdkError>

    suspend fun getRenderVariant(
        tenantId: String,
        id: Uuid,
    ): IdkResult<RenderVariantRecord, IdkError>

    suspend fun updateRenderVariant(
        tenantId: String,
        id: Uuid,
        input: CreateRenderVariantInput,
    ): IdkResult<RenderVariantRecord, IdkError>

    suspend fun listRenderVariants(
        tenantId: String,
        filter: DesignFilter = DesignFilter(),
    ): IdkResult<List<RenderVariantRecord>, IdkError>

    suspend fun deleteRenderVariant(
        tenantId: String,
        id: Uuid,
    ): IdkResult<Boolean, IdkError>

    // Import and refresh
    suspend fun importExternalDesign(
        tenantId: String,
        input: ImportExternalDesignInput,
    ): IdkResult<CredentialDesignRecord, IdkError>

    suspend fun importIssuerDesign(
        tenantId: String,
        input: ImportExternalDesignInput,
    ): IdkResult<IssuerDesignRecord, IdkError>

    suspend fun importVerifierDesign(
        tenantId: String,
        input: ImportExternalDesignInput,
    ): IdkResult<VerifierDesignRecord, IdkError>

    suspend fun refreshCredentialDesign(
        tenantId: String,
        designId: Uuid,
    ): IdkResult<CredentialDesignRecord, IdkError>

    suspend fun refreshIssuerDesign(
        tenantId: String,
        designId: Uuid,
    ): IdkResult<IssuerDesignRecord, IdkError>

    suspend fun refreshVerifierDesign(
        tenantId: String,
        designId: Uuid,
    ): IdkResult<VerifierDesignRecord, IdkError>

    suspend fun getSourceSnapshot(
        tenantId: String,
        snapshotId: Uuid,
    ): IdkResult<SourceSnapshotRecord, IdkError>

    suspend fun refreshSourceSnapshot(
        tenantId: String,
        snapshotId: Uuid,
    ): IdkResult<SourceSnapshotRecord, IdkError>

    // Resolution
    suspend fun resolveCredentialDesign(
        tenantId: String,
        input: ResolveCredentialDesignInput,
    ): IdkResult<ResolvedCredentialDesign, IdkError>

    suspend fun resolveIssuerDesign(
        tenantId: String,
        input: ResolveEntityDesignInput,
    ): IdkResult<ResolvedIssuerDesign, IdkError>

    suspend fun resolveVerifierDesign(
        tenantId: String,
        input: ResolveEntityDesignInput,
    ): IdkResult<ResolvedVerifierDesign, IdkError>

    // Assets
    suspend fun uploadDesignAsset(
        tenantId: String,
        input: UploadDesignAssetInput,
    ): IdkResult<AssetReference, IdkError>

    /**
     * Lists the caller-tenant's CONTENT-ADDRESSED, design-agnostic asset blobs (stored under
     * `vc-designs/{tenantId}/assets/by-hash/`), optionally narrowed by [AssetFilter].
     */
    suspend fun listDesignAssets(
        tenantId: String,
        filter: AssetFilter = AssetFilter(),
    ): IdkResult<List<AssetInfo>, IdkError>

    /**
     * DESIGN-AGNOSTIC, tenant-scoped asset upload. Content-addresses the bytes (SHA-256) and dedups
     * within the tenant, mirroring [uploadDesignAsset] but without a `designId`/`locale`.
     */
    suspend fun uploadTenantAsset(
        tenantId: String,
        input: UploadTenantAssetInput,
    ): IdkResult<AssetReference, IdkError>

    suspend fun getDesignAsset(
        tenantId: String,
        input: GetDesignAssetInput,
    ): IdkResult<ResolvedDesignAsset, IdkError>

    /**
     * Reads a CONTENT-ADDRESSED design asset by the lowercase-hex SHA-256 [hash] of its bytes.
     *
     * Backs the PUBLIC, unauthenticated hosting surface (`GET /public/assets/design/{hash}`).
     * Tenant-scoped: the same hash in different tenants resolves to that tenant's blob only.
     * Returns NOT_FOUND_ERROR when no asset with that hash exists for the tenant.
     */
    suspend fun getDesignAssetByHash(
        tenantId: String,
        hash: String,
    ): IdkResult<ResolvedDesignAsset, IdkError>
}

/**
 * Exposes [CredentialDesignService] as an optional graph accessor so that consumers declaring
 * `CredentialDesignService? = null` constructor parameters resolve cleanly under the Metro
 * `nullable type key`. Suppliers (the IDK [DefaultCredentialDesignService] and the EDK
 * [DefaultVersionedCredentialDesignService]) add a second
 * `@ContributesBinding(SessionScope::class, binding = binding<CredentialDesignService?>())` so the
 * default `null` body here is overridden whenever a real binding is present in the graph.
 */
@ContributesTo(SessionScope::class)
interface CredentialDesignServiceOptionalProvider {
    @OptionalBinding
    val optionalCredentialDesignService: CredentialDesignService? get() = null
}
