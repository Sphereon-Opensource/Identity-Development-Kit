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
enum class VaultExportScope {
    OBJECT,
    FOLDER,
    VAULT,
}

@Serializable
enum class VaultExportFormat {
    TDF,
    BAGIT_TDF,
}

@Serializable
data class VaultExportRecipient(
    val recipientRef: String,
    val wrappingKeyRef: String,
) {
    init {
        require(recipientRef.isNotBlank()) { "recipientRef must not be blank" }
        require(wrappingKeyRef.isNotBlank()) { "wrappingKeyRef must not be blank" }
    }
}

@Serializable
data class VaultExportRequest(
    val vaultId: VaultId,
    val scope: VaultExportScope,
    val objectId: VaultObjectId? = null,
    val path: VaultPath? = null,
    val recipient: VaultExportRecipient,
    val includeVersionHistory: Boolean = true,
    val includeProvenance: Boolean = true,
    val includeDpvJsonLd: Boolean = false,
    val condition: VaultMutationCondition,
) {
    init {
        when (scope) {
            VaultExportScope.OBJECT -> require((objectId == null) != (path == null)) {
                "An object export requires exactly one objectId or path"
            }
            VaultExportScope.FOLDER -> require(path != null && objectId == null) {
                "A folder export requires path and no objectId"
            }
            VaultExportScope.VAULT -> require(path == null && objectId == null) {
                "A vault export must not select an object or path"
            }
        }
    }
}

@Serializable
data class VaultExportManifest(
    val exportId: String,
    val vaultId: VaultId,
    val scope: VaultExportScope,
    val format: VaultExportFormat,
    val createdAt: Instant,
    val recipientRef: String,
    val objectCount: Long,
    val contentDigest: VaultDigest,
    val manifestDigest: VaultDigest,
) {
    init {
        require(exportId.isNotBlank()) { "exportId must not be blank" }
        require(recipientRef.isNotBlank()) { "recipientRef must not be blank" }
        require(objectCount >= 0) { "objectCount must be non-negative" }
        require((scope == VaultExportScope.OBJECT) == (format == VaultExportFormat.TDF)) {
            "Object exports use TDF; folder and vault exports use BagIt-in-TDF"
        }
    }
}

@Serializable
data class VaultExportResult(
    val manifest: VaultExportManifest,
    val packageBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is VaultExportResult && manifest == other.manifest && packageBytes.contentEquals(other.packageBytes)

    override fun hashCode(): Int = 31 * manifest.hashCode() + packageBytes.contentHashCode()
}

@Serializable
enum class VaultImportKeyDisposition {
    AVAILABLE,
    REISSUANCE_REQUIRED,
}

@Serializable
data class VaultImportRequest(
    val targetVaultId: VaultId,
    val destination: VaultPath = VaultPath.ROOT,
    val packageBytes: ByteArray,
    val condition: VaultMutationCondition,
) {
    init {
        require(packageBytes.isNotEmpty()) { "Import package must not be empty" }
    }

    override fun equals(other: Any?): Boolean =
        other is VaultImportRequest &&
            targetVaultId == other.targetVaultId &&
            destination == other.destination &&
            packageBytes.contentEquals(other.packageBytes) &&
            condition == other.condition

    override fun hashCode(): Int = 31 * targetVaultId.hashCode() + packageBytes.contentHashCode()
}

@Serializable
data class VaultImportedObject(
    val objectId: VaultObjectId,
    val path: VaultPath,
    val keyDisposition: VaultImportKeyDisposition,
)

@Serializable
data class VaultImportManifest(
    val importId: String,
    val targetVaultId: VaultId,
    val importedAt: Instant,
    val sourceDigest: VaultDigest,
    val importedObjects: List<VaultImportedObject>,
) {
    init {
        require(importId.isNotBlank()) { "importId must not be blank" }
    }
}
