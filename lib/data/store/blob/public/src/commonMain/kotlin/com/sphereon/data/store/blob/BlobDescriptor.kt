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

package com.sphereon.data.store.blob

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Descriptor for a stored blob, returned by put/stat/list operations.
 *
 * Classification hint fields mirror [BlobMetadata] so callers reading a descriptor
 * can surface the classification without reaching into [metadata]. Authoritative
 * values live on the consumer's own row (for example, vault_document); these
 * are hints only.
 */
@Serializable
@JsExportCompat
data class BlobDescriptor(
    val path: String,
    /**
     * The CONFIGURED registry id of the store this blob lives in (e.g. `"default"` from
     * `blob.stores.default.*`), NOT the backend scheme id ([BlobStore.schemeId], e.g. `"memory"`
     * or `"filesystem"`).
     *
     * Backend [BlobStore] implementations stamp their own scheme id here, but [BlobService]
     * normalises it to the configured id on every caller-facing descriptor before it leaves the
     * service. Callers may therefore round-trip this value straight back into [BlobInfo.storeId]
     * for a subsequent operation. Descriptors observed directly from a [BlobStore] (below the
     * service boundary) still carry the scheme id.
     */
    val storeId: String,
    val sizeBytes: Long,
    val contentType: String? = null,
    val filename: String? = null,
    val etag: String? = null,
    val createdAt: Instant? = null,
    val lastModified: Instant? = null,
    val metadata: BlobMetadata = BlobMetadata.EMPTY,
    val contentHash: String? = null,
    /** Consumer-supplied sensitivity classification hint mirrored from [BlobMetadata.classification]. */
    val classification: String? = null,
    /** Consumer-supplied legal basis hint mirrored from [BlobMetadata.legalBasis]. */
    val legalBasis: String? = null,
    /** Consumer-supplied retention-days hint mirrored from [BlobMetadata.retentionDays]. */
    val retentionDays: Int? = null,
    /** Consumer-supplied processing-purpose hint mirrored from [BlobMetadata.processingPurpose]. */
    val processingPurpose: String? = null,
    /** Consumer-supplied jurisdiction hint mirrored from [BlobMetadata.jurisdiction]. */
    val jurisdiction: String? = null,
)
