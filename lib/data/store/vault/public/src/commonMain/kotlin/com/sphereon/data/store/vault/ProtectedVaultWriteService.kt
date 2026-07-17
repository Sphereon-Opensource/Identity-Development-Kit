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
import kotlinx.serialization.Serializable

/** Explicit client-protected write surface; protected bytes are never treated as plaintext. */
interface ProtectedVaultWriteService {
    suspend fun putProtectedFile(
        context: VaultMutationContext,
        request: PutProtectedVaultFileRequest,
    ): IdkResult<VaultObjectRecord, VaultError>

    suspend fun createProtectedVersion(
        context: VaultMutationContext,
        request: CreateProtectedVaultVersionRequest,
    ): IdkResult<VaultObjectVersion, VaultError>
}

@Serializable
data class PutProtectedVaultFileRequest(
    val vaultId: VaultId,
    val path: VaultPath,
    val objectId: VaultObjectId,
    val versionId: VaultVersionId,
    val protectedBytes: ByteArray,
    val plaintextDigest: VaultDigest,
    val plaintextSizeBytes: Long,
    val protectedPackageRef: String,
    val associatedData: ByteArray,
    val protectedParameters: Map<String, String> = emptyMap(),
    val metadata: VaultMetadata = VaultMetadata(),
    val protection: VaultProtectionDescriptor,
    val condition: VaultMutationCondition = VaultMutationCondition.ANY,
) {
    init {
        require(!path.isRoot) { "A file cannot be written at the root path" }
        require(plaintextSizeBytes >= 0) { "plaintextSizeBytes must be non-negative" }
        require(protectedPackageRef.isNotBlank()) { "protectedPackageRef must not be blank" }
        require(associatedData.isNotEmpty()) { "associatedData must not be empty" }
        require(protection.profile == VaultProtectionProfile.OWNER_CONTROLLED_ZERO_ACCESS) {
            "Protected writes require OWNER_CONTROLLED_ZERO_ACCESS"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is PutProtectedVaultFileRequest &&
            vaultId == other.vaultId &&
            path == other.path &&
            objectId == other.objectId &&
            versionId == other.versionId &&
            protectedBytes.contentEquals(other.protectedBytes) &&
            plaintextDigest == other.plaintextDigest &&
            plaintextSizeBytes == other.plaintextSizeBytes &&
            protectedPackageRef == other.protectedPackageRef &&
            associatedData.contentEquals(other.associatedData) &&
            protectedParameters == other.protectedParameters &&
            metadata == other.metadata &&
            protection == other.protection &&
            condition == other.condition

    override fun hashCode(): Int = 31 * path.hashCode() + protectedBytes.contentHashCode()
}

@Serializable
data class CreateProtectedVaultVersionRequest(
    val selector: VaultObjectSelector,
    val objectId: VaultObjectId,
    val versionId: VaultVersionId,
    val protectedBytes: ByteArray,
    val plaintextDigest: VaultDigest,
    val plaintextSizeBytes: Long,
    val protectedPackageRef: String,
    val associatedData: ByteArray,
    val protectedParameters: Map<String, String> = emptyMap(),
    val metadata: VaultMetadata = VaultMetadata(),
    val protection: VaultProtectionDescriptor,
    val condition: VaultMutationCondition,
) {
    init {
        require(plaintextSizeBytes >= 0) { "plaintextSizeBytes must be non-negative" }
        require(protectedPackageRef.isNotBlank()) { "protectedPackageRef must not be blank" }
        require(associatedData.isNotEmpty()) { "associatedData must not be empty" }
        require(protection.profile == VaultProtectionProfile.OWNER_CONTROLLED_ZERO_ACCESS) {
            "Protected writes require OWNER_CONTROLLED_ZERO_ACCESS"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is CreateProtectedVaultVersionRequest &&
            selector == other.selector &&
            objectId == other.objectId &&
            versionId == other.versionId &&
            protectedBytes.contentEquals(other.protectedBytes) &&
            plaintextDigest == other.plaintextDigest &&
            plaintextSizeBytes == other.plaintextSizeBytes &&
            protectedPackageRef == other.protectedPackageRef &&
            associatedData.contentEquals(other.associatedData) &&
            protectedParameters == other.protectedParameters &&
            metadata == other.metadata &&
            protection == other.protection &&
            condition == other.condition

    override fun hashCode(): Int = 31 * selector.hashCode() + protectedBytes.contentHashCode()
}
