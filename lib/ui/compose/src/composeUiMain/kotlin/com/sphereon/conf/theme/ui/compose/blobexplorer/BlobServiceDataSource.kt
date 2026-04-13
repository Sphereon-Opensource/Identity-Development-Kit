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

package com.sphereon.conf.theme.ui.compose.blobexplorer

import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.ListOptions

/**
 * [BlobExplorerDataSource] implementation backed by any [BlobService] (including [HttpBlobServiceClient]).
 *
 * Bridges the UI-layer data source abstraction to the IDK BlobService interface.
 * Host apps can use this adapter directly or continue implementing [BlobExplorerDataSource] manually.
 *
 * Usage:
 * ```kotlin
 * val dataSource = BlobServiceDataSource(
 *     blobService = httpBlobServiceClient,
 *     tenantId = "my-tenant",
 *     storeId = "documents",
 * )
 * val state = rememberBlobExplorerState(dataSource)
 * BlobExplorer(state = state, ...)
 * ```
 */
class BlobServiceDataSource(
    private val blobService: BlobService,
    private val tenantId: String,
    private val storeId: String? = null,
) : BlobExplorerDataSource {
    override val capabilities: BlobExplorerCapabilities =
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
                options =
                    ListOptions(
                        prefix = prefix,
                        pageToken = pageToken,
                    ),
            )
        check(result.isOk) { result.error.message?.toString() ?: "Failed to list blobs" }
        val listResult = result.value
        return BlobListResult(
            items = listResult.descriptors.map { it.toBlobItem() },
            commonPrefixes = listResult.commonPrefixes,
            nextPageToken = listResult.nextPageToken,
        )
    }

    override suspend fun deleteBlob(path: String): Boolean {
        val result = blobService.deleteBlob(info = BlobInfo(tenantId = tenantId, path = path, storeId = storeId))
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
                target =
                    BlobInfo(
                        tenantId = tenantId,
                        path = path,
                        storeId = storeId,
                        contentType = contentType,
                    ),
                data = data,
            )
        check(result.isOk) { result.error.message?.toString() ?: "Failed to upload blob" }
        return result.value.toBlobItem()
    }

    override suspend fun createTempUrl(path: String): String {
        val result = blobService.createTempUrl(info = BlobInfo(tenantId = tenantId, path = path, storeId = storeId))
        check(result.isOk) { result.error.message?.toString() ?: "Failed to create temp URL" }
        return result.value.url
    }
}

private fun BlobDescriptor.toBlobItem(): BlobItem =
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
