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

package com.sphereon.data.store.blob.okd

import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.okd.generated.models.DocumentMetadata
import kotlin.time.Instant

/**
 * Bidirectional mapping between IDK blob store types and OKD API types.
 *
 * Used by both directions:
 * - Direction A (OkdBlobStore): converts OKD responses -> BlobDescriptor
 * - Direction B (OkdHttpAdapter): converts BlobDescriptor -> OKD responses
 */
object OkdBlobMapping {
    /**
     * Convert OKD DocumentMetadata -> BlobDescriptor.
     * Used by OkdBlobStore.stat() when reading from an external OKD DMS.
     */
    fun fromOkdMetadata(
        metadata: DocumentMetadata,
        storeId: String = OkdBlobStoreConfig.BACKEND_ID,
    ): BlobDescriptor {
        val documentId = metadata.dmsDocumentId
        return BlobDescriptor(
            path = documentId,
            storeId = storeId,
            sizeBytes = metadata.documentsize ?: 0L,
            contentType = metadata.format,
            filename = metadata.documentname,
            createdAt = metadata.creationdate?.let { convertInstant(it) },
            metadata =
                BlobMetadata(
                    contentType = metadata.format,
                    custom =
                        buildMap {
                            metadata.title?.let { put("title", it) }
                            metadata.documentname?.let { put("documentname", it) }
                            metadata.documentTempDownloadUrl?.let { put("documentTempDownloadUrl", it) }
                        },
                ),
        )
    }

    /**
     * Convert BlobDescriptor -> OKD DocumentMetadata.
     * Used by the OKD HTTP adapter when serving blob store content as OKD API responses.
     */
    fun toOkdMetadata(
        descriptor: BlobDescriptor,
        tempDownloadUrl: String? = null,
    ): DocumentMetadata =
        DocumentMetadata(
            dmsDocumentId = descriptor.path,
            title = descriptor.metadata.custom["title"] ?: descriptor.filename,
            documentTempDownloadUrl = tempDownloadUrl,
            creationdate = descriptor.createdAt?.let { convertToKotlinTimeInstant(it) },
            format = descriptor.contentType,
            documentname = descriptor.filename,
            documentsize = descriptor.sizeBytes,
        )

    /**
     * Convert kotlin.time.Instant (from generated OKD models) -> kotlin.time.Instant (IDK types).
     */
    private fun convertInstant(ktimeInstant: kotlin.time.Instant): Instant = Instant.fromEpochMilliseconds(ktimeInstant.toEpochMilliseconds())

    /**
     * Convert kotlin.time.Instant (IDK) -> kotlin.time.Instant (OKD generated models).
     */
    private fun convertToKotlinTimeInstant(instant: Instant): kotlin.time.Instant = kotlin.time.Instant.fromEpochMilliseconds(instant.toEpochMilliseconds())
}
