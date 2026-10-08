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

package com.sphereon.data.store.vault

import com.sphereon.core.api.IdkResult

/**
 * Provider-neutral KMP contract for protected vaults, files, folders and immutable versions.
 *
 * The contract deliberately has no BlobStore, KMS, tenancy, Wallet Unit or WSCA/WSCD dependency.
 * Implementations may use those capabilities behind repository, crypto and authorization adapters.
 */
interface VaultService {
    // Vault lifecycle
    suspend fun createVault(
        context: VaultMutationContext,
        request: CreateVaultRequest,
    ): IdkResult<VaultDescriptor, VaultError>

    suspend fun getVault(
        context: VaultOperationContext,
        vaultId: VaultId,
    ): IdkResult<VaultDescriptor, VaultError>

    suspend fun updateVaultPolicy(
        context: VaultMutationContext,
        request: UpdateVaultPolicyRequest,
    ): IdkResult<VaultDescriptor, VaultError>

    suspend fun deleteVault(
        context: VaultMutationContext,
        request: DeleteVaultRequest,
    ): IdkResult<VaultDescriptor, VaultError>

    // File/folder lifecycle
    suspend fun createFolder(
        context: VaultMutationContext,
        request: CreateVaultFolderRequest,
    ): IdkResult<VaultFolder, VaultError>

    suspend fun putFile(
        context: VaultMutationContext,
        request: PutVaultFileRequest,
    ): IdkResult<VaultObjectRecord, VaultError>

    suspend fun createVersion(
        context: VaultMutationContext,
        request: CreateVaultVersionRequest,
    ): IdkResult<VaultObjectVersion, VaultError>

    suspend fun updateMetadata(
        context: VaultMutationContext,
        request: UpdateVaultMetadataRequest,
    ): IdkResult<VaultObjectRecord, VaultError>

    suspend fun getObject(
        context: VaultOperationContext,
        selector: VaultObjectSelector,
    ): IdkResult<VaultObjectRecord, VaultError>

    suspend fun readFile(
        context: VaultOperationContext,
        request: VaultReadRequest,
    ): IdkResult<VaultFileContent, VaultError>

    suspend fun list(
        context: VaultOperationContext,
        request: VaultListRequest,
    ): IdkResult<VaultPage<VaultPathEntry>, VaultError>

    suspend fun search(
        context: VaultOperationContext,
        query: VaultSearchQuery,
    ): IdkResult<VaultPage<VaultSearchHit>, VaultError>

    suspend fun listVersions(
        context: VaultOperationContext,
        request: VaultVersionListRequest,
    ): IdkResult<VaultPage<VaultObjectVersion>, VaultError>

    suspend fun move(
        context: VaultMutationContext,
        request: MoveVaultObjectRequest,
    ): IdkResult<VaultObjectRecord, VaultError>

    suspend fun copy(
        context: VaultMutationContext,
        request: CopyVaultObjectRequest,
    ): IdkResult<VaultObjectRecord, VaultError>

    suspend fun deleteObject(
        context: VaultMutationContext,
        request: DeleteVaultObjectRequest,
    ): IdkResult<VaultObjectRecord, VaultError>

    // Grants and policy-facing decisions
    suspend fun putGrant(
        context: VaultMutationContext,
        request: PutVaultGrantRequest,
    ): IdkResult<VaultGrant, VaultError>

    suspend fun revokeGrant(
        context: VaultMutationContext,
        request: RevokeVaultGrantRequest,
    ): IdkResult<VaultGrant, VaultError>

    suspend fun listGrants(
        context: VaultOperationContext,
        vaultId: VaultId,
        pageSize: Int = 100,
        pageToken: String? = null,
    ): IdkResult<VaultPage<VaultGrant>, VaultError>

    // Recipient-scoped portability
    suspend fun export(
        context: VaultMutationContext,
        request: VaultExportRequest,
    ): IdkResult<VaultExportResult, VaultError>

    suspend fun importVault(
        context: VaultMutationContext,
        request: VaultImportRequest,
    ): IdkResult<VaultImportManifest, VaultError>
}
