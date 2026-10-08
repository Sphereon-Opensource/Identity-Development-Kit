/*
 * Copyright 2023-2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.conf.theme.ui.compose.blobexplorer

import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.ListOptions

/** Blob explorer adapter backed by a BlobService. Kept outside the reusable visual module. */
class BlobServiceDataSource(
    private val blobService: BlobService,
    private val tenantId: String,
    private val storeId: String? = null,
) : BlobExplorerDataSource {
    override val capabilities =
        BlobExplorerCapabilities(
            supportsCopy = true,
            supportsMove = true,
            supportsDelete = true,
            supportsUpload = true,
            supportsTempUrls = true,
        )

    override suspend fun listBlobs(
        prefix: String?,
        pageToken: String?,
    ): BlobListResult {
        val result =
            blobService.listBlobs(
                info = BlobInfo(tenantId = tenantId, storeId = storeId),
                options = ListOptions(prefix = prefix, pageToken = pageToken),
            )
        check(result.isOk) { result.error.message?.toString() ?: "Failed to list blobs" }
        return BlobListResult(
            items = result.value.descriptors.map { it.toBlobItem() },
            commonPrefixes = result.value.commonPrefixes,
            nextPageToken = result.value.nextPageToken,
        )
    }

    override suspend fun deleteBlob(path: String): Boolean {
        val result = blobService.deleteBlob(BlobInfo(tenantId = tenantId, path = path, storeId = storeId))
        check(result.isOk) { result.error.message?.toString() ?: "Failed to delete blob" }
        return result.value
    }

    override suspend fun copyBlob(
        source: String,
        destination: String,
    ): BlobItem {
        val result =
            blobService.copyBlob(
                source = BlobInfo(tenantId = tenantId, path = source, storeId = storeId),
                destination = BlobInfo(tenantId = tenantId, path = destination, storeId = storeId),
            )
        check(result.isOk) { result.error.message?.toString() ?: "Failed to copy blob" }
        return result.value.toBlobItem()
    }

    override suspend fun moveBlob(
        source: String,
        destination: String,
    ): BlobItem {
        val result =
            blobService.moveBlob(
                source = BlobInfo(tenantId = tenantId, path = source, storeId = storeId),
                destination = BlobInfo(tenantId = tenantId, path = destination, storeId = storeId),
            )
        check(result.isOk) { result.error.message?.toString() ?: "Failed to move blob" }
        return result.value.toBlobItem()
    }

    override suspend fun uploadBlob(
        path: String,
        data: ByteArray,
        contentType: String?,
    ): BlobItem {
        val result =
            blobService.storeBlob(
                target = BlobInfo(tenantId = tenantId, path = path, storeId = storeId, contentType = contentType),
                data = data,
            )
        check(result.isOk) { result.error.message?.toString() ?: "Failed to upload blob" }
        return result.value.toBlobItem()
    }

    override suspend fun createTempUrl(path: String): String {
        val result = blobService.createTempUrl(BlobInfo(tenantId = tenantId, path = path, storeId = storeId))
        check(result.isOk) { result.error.message?.toString() ?: "Failed to create temp URL" }
        return result.value.url
    }
}

private fun BlobDescriptor.toBlobItem() =
    BlobItem(
        path = path,
        filename = filename ?: path.substringAfterLast('/'),
        sizeBytes = sizeBytes,
        contentType = contentType,
        lastModified = lastModified?.toString(),
        createdAt = createdAt?.toString(),
        etag = etag,
        metadata = metadata.custom,
    )
