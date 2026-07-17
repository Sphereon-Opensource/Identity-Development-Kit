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

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class VaultExistenceCondition {
    ANY,
    MUST_EXIST,
    MUST_NOT_EXIST,
}

/** Compare-and-set conditions for one logical vault mutation. */
@Serializable
data class VaultMutationCondition(
    val existence: VaultExistenceCondition = VaultExistenceCondition.ANY,
    val expectedVaultRevision: VaultRevision? = null,
    val expectedObjectRevision: VaultRevision? = null,
    val expectedPathRevision: VaultRevision? = null,
    val expectedVersionRevision: VaultRevision? = null,
    val expectedCurrentVersionId: VaultVersionId? = null,
) {
    companion object {
        val ANY = VaultMutationCondition()
        val CREATE_ONLY = VaultMutationCondition(existence = VaultExistenceCondition.MUST_NOT_EXIST)
    }
}

@Serializable
data class VaultObjectSelector(
    val vaultId: VaultId,
    val objectId: VaultObjectId? = null,
    val path: VaultPath? = null,
) {
    init {
        require((objectId == null) != (path == null)) { "Exactly one of objectId or path must be provided" }
    }
}

@Serializable
data class CreateVaultRequest(
    val vaultId: VaultId,
    val kind: VaultKind,
    val authority: VaultAuthority,
    val protection: VaultProtectionDescriptor,
    val repositoryRef: String,
    val providerRef: String,
    val policyRef: VaultPolicyRef? = null,
    val condition: VaultMutationCondition = VaultMutationCondition.CREATE_ONLY,
) {
    init {
        require(repositoryRef.isNotBlank()) { "repositoryRef must not be blank" }
        require(providerRef.isNotBlank()) { "providerRef must not be blank" }
    }
}

@Serializable
data class UpdateVaultPolicyRequest(
    val vaultId: VaultId,
    val policyRef: VaultPolicyRef,
    val condition: VaultMutationCondition,
)

@Serializable
data class DeleteVaultRequest(
    val vaultId: VaultId,
    val condition: VaultMutationCondition,
)

@Serializable
data class CreateVaultFolderRequest(
    val vaultId: VaultId,
    val path: VaultPath,
    val metadata: VaultMetadata = VaultMetadata(),
    val policyRef: VaultPolicyRef? = null,
    val condition: VaultMutationCondition = VaultMutationCondition.CREATE_ONLY,
) {
    init {
        require(!path.isRoot) { "The root folder is created with the vault" }
    }
}

@Serializable
data class PutVaultFileRequest(
    val vaultId: VaultId,
    val path: VaultPath,
    val bytes: ByteArray,
    val metadata: VaultMetadata = VaultMetadata(),
    val protection: VaultProtectionDescriptor? = null,
    val condition: VaultMutationCondition = VaultMutationCondition.ANY,
) {
    init {
        require(!path.isRoot) { "A file cannot be written at the root path" }
    }

    override fun equals(other: Any?): Boolean =
        other is PutVaultFileRequest &&
            vaultId == other.vaultId &&
            path == other.path &&
            bytes.contentEquals(other.bytes) &&
            metadata == other.metadata &&
            protection == other.protection &&
            condition == other.condition

    override fun hashCode(): Int = 31 * path.hashCode() + bytes.contentHashCode()
}

@Serializable
data class CreateVaultVersionRequest(
    val selector: VaultObjectSelector,
    val bytes: ByteArray,
    val metadata: VaultMetadata,
    val protection: VaultProtectionDescriptor? = null,
    val condition: VaultMutationCondition,
) {
    override fun equals(other: Any?): Boolean =
        other is CreateVaultVersionRequest &&
            selector == other.selector &&
            bytes.contentEquals(other.bytes) &&
            metadata == other.metadata &&
            protection == other.protection &&
            condition == other.condition

    override fun hashCode(): Int = 31 * selector.hashCode() + bytes.contentHashCode()
}

@Serializable
data class UpdateVaultMetadataRequest(
    val selector: VaultObjectSelector,
    val metadata: VaultMetadata,
    val condition: VaultMutationCondition,
)

@Serializable
data class MoveVaultObjectRequest(
    val selector: VaultObjectSelector,
    val destination: VaultPath,
    val condition: VaultMutationCondition,
) {
    init {
        require(!destination.isRoot) { "An object cannot replace the root path" }
    }
}

@Serializable
data class CopyVaultObjectRequest(
    val selector: VaultObjectSelector,
    val destination: VaultPath,
    val recursive: Boolean = false,
    val condition: VaultMutationCondition,
) {
    init {
        require(!destination.isRoot) { "An object cannot replace the root path" }
    }
}

@Serializable
data class DeleteVaultObjectRequest(
    val selector: VaultObjectSelector,
    val recursive: Boolean = false,
    val condition: VaultMutationCondition,
)

@Serializable
data class VaultReadRequest(
    val selector: VaultObjectSelector,
    val versionId: VaultVersionId? = null,
)

@Serializable
data class VaultListRequest(
    val vaultId: VaultId,
    val folder: VaultPath = VaultPath.ROOT,
    val recursive: Boolean = false,
    val pageSize: Int = 100,
    val pageToken: String? = null,
) {
    init {
        require(pageSize in 1..1000) { "pageSize must be between 1 and 1000" }
    }
}

@Serializable
data class VaultVersionListRequest(
    val selector: VaultObjectSelector,
    val pageSize: Int = 100,
    val pageToken: String? = null,
) {
    init {
        require(pageSize in 1..1000) { "pageSize must be between 1 and 1000" }
    }
}

@Serializable
data class VaultSearchQuery(
    val vaultId: VaultId,
    val pathPrefix: VaultPath? = null,
    val text: String? = null,
    val objectKinds: Set<VaultObjectKind> = emptySet(),
    val contentTypes: Set<String> = emptySet(),
    val tags: Map<String, String> = emptyMap(),
    val createdAfter: Instant? = null,
    val createdBefore: Instant? = null,
    val pageSize: Int = 100,
    val pageToken: String? = null,
) {
    init {
        require(text == null || text.isNotBlank()) { "text must not be blank" }
        require(createdAfter == null || createdBefore == null || createdBefore > createdAfter) {
            "createdBefore must be after createdAfter"
        }
        require(pageSize in 1..1000) { "pageSize must be between 1 and 1000" }
    }
}

@Serializable
data class VaultSearchHit(
    val objectInfo: VaultObject,
    val pathEntry: VaultPathEntry,
    val metadata: VaultMetadata,
    val score: Double? = null,
)

@Serializable
data class PutVaultGrantRequest(
    val grant: VaultGrant,
    val condition: VaultMutationCondition,
)

@Serializable
data class RevokeVaultGrantRequest(
    val vaultId: VaultId,
    val grantId: String,
    val condition: VaultMutationCondition,
) {
    init {
        require(grantId.isNotBlank()) { "grantId must not be blank" }
    }
}
