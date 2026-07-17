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
enum class VaultKind {
    GENERIC,
    WALLET,
}

@Serializable
enum class VaultAuthorityLocation {
    CLIENT_DEVICE,
    SERVICE,
    EXTERNAL_PROVIDER,
}

@Serializable
data class VaultAuthority(
    val location: VaultAuthorityLocation,
    val authorityRef: String,
) {
    init {
        require(authorityRef.isNotBlank()) { "authorityRef must not be blank" }
    }
}

@Serializable
enum class VaultProtectionProfile {
    OWNER_CONTROLLED_ZERO_ACCESS,
    WSCD_GATED,
    SERVICE_MANAGED,
}

@Serializable
data class VaultProtectionDescriptor(
    val profile: VaultProtectionProfile,
    val providerRef: String,
    /** Opaque provider reference only; this field must never contain key material. */
    val keyReference: String? = null,
    val parameters: Map<String, String> = emptyMap(),
) {
    init {
        require(providerRef.isNotBlank()) { "providerRef must not be blank" }
        require(keyReference == null || keyReference.isNotBlank()) { "keyReference must not be blank" }
    }
}

@Serializable
enum class VaultLifecycleState {
    ACTIVE,
    SUSPENDED,
    DELETING,
    DELETED,
}

@Serializable
data class VaultPolicyRef(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "VaultPolicyRef must not be blank" }
    }
}

@Serializable
data class VaultDescriptor(
    val vaultId: VaultId,
    val kind: VaultKind,
    val authority: VaultAuthority,
    val protection: VaultProtectionDescriptor,
    val repositoryRef: String,
    val providerRef: String,
    val policyRef: VaultPolicyRef? = null,
    val revision: VaultRevision,
    val lifecycleState: VaultLifecycleState,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(repositoryRef.isNotBlank()) { "repositoryRef must not be blank" }
        require(providerRef.isNotBlank()) { "providerRef must not be blank" }
        require(updatedAt >= createdAt) { "updatedAt must not precede createdAt" }
    }
}

@Serializable
enum class VaultObjectKind {
    FILE,
    FOLDER,
}

@Serializable
data class VaultDigest(
    val algorithm: String,
    val value: String,
) {
    init {
        require(algorithm.isNotBlank()) { "Digest algorithm must not be blank" }
        require(value.isNotBlank()) { "Digest value must not be blank" }
    }
}

@Serializable
data class VaultSidecarRef(
    val type: String,
    val schemaRef: String,
    val version: String,
    val digest: VaultDigest,
) {
    init {
        require(type.isNotBlank()) { "Sidecar type must not be blank" }
        require(schemaRef.isNotBlank()) { "Sidecar schemaRef must not be blank" }
        require(version.isNotBlank()) { "Sidecar version must not be blank" }
    }
}

@Serializable
data class VaultMetadata(
    val contentType: String? = null,
    val displayName: String? = null,
    val tags: Map<String, String> = emptyMap(),
    val attributes: Map<String, String> = emptyMap(),
    val sidecars: List<VaultSidecarRef> = emptyList(),
)

@Serializable
data class VaultObject(
    val vaultId: VaultId,
    val objectId: VaultObjectId,
    val kind: VaultObjectKind,
    val currentVersionId: VaultVersionId,
    val revision: VaultRevision,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(updatedAt >= createdAt) { "updatedAt must not precede createdAt" }
    }
}

@Serializable
data class VaultObjectVersion(
    val vaultId: VaultId,
    val objectId: VaultObjectId,
    val versionId: VaultVersionId,
    val contentRef: String,
    val contentDigest: VaultDigest,
    val protection: VaultProtectionDescriptor,
    val metadata: VaultMetadata,
    val sizeBytes: Long,
    val createdBy: String,
    val createdAt: Instant,
    val parentVersionId: VaultVersionId? = null,
    val revision: VaultRevision,
) {
    init {
        require(contentRef.isNotBlank()) { "contentRef must not be blank" }
        require(sizeBytes >= 0) { "sizeBytes must be non-negative" }
        require(createdBy.isNotBlank()) { "createdBy must not be blank" }
        require(parentVersionId != versionId) { "A version cannot be its own parent" }
    }
}

@Serializable
data class VaultPathEntry(
    val vaultId: VaultId,
    val path: VaultPath,
    val objectId: VaultObjectId,
    val kind: VaultObjectKind,
    val revision: VaultRevision,
)

@Serializable
data class VaultFolder(
    val entry: VaultPathEntry,
    val metadata: VaultMetadata = VaultMetadata(),
    val policyRef: VaultPolicyRef? = null,
)

@Serializable
data class VaultObjectRecord(
    val objectInfo: VaultObject,
    val pathEntry: VaultPathEntry,
    val currentVersion: VaultObjectVersion,
)

@Serializable
data class VaultFileContent(
    val record: VaultObjectRecord,
    /** Plaintext content. ZERO_ACCESS implementations return ClientUnwrapRequired instead. */
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is VaultFileContent && record == other.record && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * record.hashCode() + bytes.contentHashCode()
}

@Serializable
data class VaultPage<T>(
    val items: List<T>,
    val nextPageToken: String? = null,
)
