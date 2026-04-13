package com.sphereon.data.store.okd.server

import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.blob.BlobStoreSchemes
import com.sphereon.data.store.blob.okd.OkdBlobMapping
import com.sphereon.data.store.blob.okd.OkdBlobStoreConfig
import com.sphereon.data.store.okd.generated.models.DocumentMetadata
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OkdBlobMappingTest {

    @Test
    fun fromOkdMetadataBasicFields() {
        val okdMeta = DocumentMetadata(
            dmsDocumentId = "abc-123",
            title = "Test Document",
            format = "application/pdf",
            documentname = "test.pdf",
            documentsize = 12345L,
        )
        val descriptor = OkdBlobMapping.fromOkdMetadata(okdMeta)

        assertEquals("abc-123", descriptor.path)
        assertEquals(OkdBlobStoreConfig.BACKEND_ID, descriptor.storeId)
        assertEquals(12345L, descriptor.sizeBytes)
        assertEquals("application/pdf", descriptor.contentType)
        assertEquals("test.pdf", descriptor.filename)
        assertEquals("Test Document", descriptor.metadata.custom["title"])
    }

    @Test
    fun fromOkdMetadataMinimalFields() {
        val okdMeta = DocumentMetadata(dmsDocumentId = "min-id")
        val descriptor = OkdBlobMapping.fromOkdMetadata(okdMeta)

        assertEquals("min-id", descriptor.path)
        assertEquals(0L, descriptor.sizeBytes)
        assertNull(descriptor.contentType)
        assertNull(descriptor.filename)
    }

    @Test
    fun toOkdMetadataBasicFields() {
        val descriptor = BlobDescriptor(
            path = "doc-456",
            storeId = BlobStoreSchemes.FILESYSTEM,
            sizeBytes = 9999L,
            contentType = "image/png",
            filename = "photo.png",
            createdAt = Instant.fromEpochMilliseconds(1700000000000L),
            metadata = BlobMetadata(
                contentType = "image/png",
                custom = mapOf("title" to "My Photo"),
            ),
        )
        val okdMeta = OkdBlobMapping.toOkdMetadata(descriptor, tempDownloadUrl = "https://example.com/download/doc-456")

        assertEquals("doc-456", okdMeta.dmsDocumentId)
        assertEquals(9999L, okdMeta.documentsize)
        assertEquals("image/png", okdMeta.format)
        assertEquals("photo.png", okdMeta.documentname)
        assertEquals("My Photo", okdMeta.title)
        assertEquals("https://example.com/download/doc-456", okdMeta.documentTempDownloadUrl)
        assertNotNull(okdMeta.creationdate)
    }

    @Test
    fun toOkdMetadataWithoutTitle() {
        val descriptor = BlobDescriptor(
            path = "notitle",
            storeId = BlobStoreSchemes.MEMORY,
            sizeBytes = 100L,
            filename = "file.txt",
        )
        val okdMeta = OkdBlobMapping.toOkdMetadata(descriptor)

        assertEquals("file.txt", okdMeta.title, "title should fall back to filename")
        assertNull(okdMeta.documentTempDownloadUrl)
    }

    @Test
    fun roundtripPreservesFields() {
        val original = DocumentMetadata(
            dmsDocumentId = "roundtrip-id",
            title = "Roundtrip Doc",
            format = "text/plain",
            documentname = "roundtrip.txt",
            documentsize = 500L,
        )
        val descriptor = OkdBlobMapping.fromOkdMetadata(original)
        val reconverted = OkdBlobMapping.toOkdMetadata(descriptor)

        assertEquals(original.dmsDocumentId, reconverted.dmsDocumentId)
        assertEquals(original.title, reconverted.title)
        assertEquals(original.format, reconverted.format)
        assertEquals(original.documentname, reconverted.documentname)
        assertEquals(original.documentsize, reconverted.documentsize)
    }
}
