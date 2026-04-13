package com.sphereon.data.store.blob.okd

import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.okd.generated.models.DocumentMetadata
import kotlinx.datetime.Instant

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
    fun fromOkdMetadata(metadata: DocumentMetadata, storeId: String = OkdBlobStoreConfig.BACKEND_ID): BlobDescriptor {
        val documentId = metadata.dmsDocumentId
        return BlobDescriptor(
            path = documentId,
            storeId = storeId,
            sizeBytes = metadata.documentsize ?: 0L,
            contentType = metadata.format,
            filename = metadata.documentname,
            createdAt = metadata.creationdate?.let { convertInstant(it) },
            metadata = BlobMetadata(
                contentType = metadata.format,
                custom = buildMap {
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
    fun toOkdMetadata(descriptor: BlobDescriptor, tempDownloadUrl: String? = null): DocumentMetadata {
        return DocumentMetadata(
            dmsDocumentId = descriptor.path,
            title = descriptor.metadata.custom["title"] ?: descriptor.filename,
            documentTempDownloadUrl = tempDownloadUrl,
            creationdate = descriptor.createdAt?.let { convertToKotlinTimeInstant(it) },
            format = descriptor.contentType,
            documentname = descriptor.filename,
            documentsize = descriptor.sizeBytes,
        )
    }

    /**
     * Convert kotlin.time.Instant (from generated OKD models) -> kotlinx.datetime.Instant (IDK types).
     */
    private fun convertInstant(ktimeInstant: kotlin.time.Instant): Instant {
        return Instant.fromEpochMilliseconds(ktimeInstant.toEpochMilliseconds())
    }

    /**
     * Convert kotlinx.datetime.Instant (IDK) -> kotlin.time.Instant (OKD generated models).
     */
    private fun convertToKotlinTimeInstant(instant: Instant): kotlin.time.Instant {
        return kotlin.time.Instant.fromEpochMilliseconds(instant.toEpochMilliseconds())
    }
}
