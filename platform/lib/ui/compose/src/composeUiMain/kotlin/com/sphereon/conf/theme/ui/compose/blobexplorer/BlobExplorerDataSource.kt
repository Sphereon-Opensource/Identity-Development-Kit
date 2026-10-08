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

/**
 * Descriptor for a stored blob, mirroring the IDK BlobDescriptor model.
 * Dates are ISO-8601 strings to avoid adding a kotlinx-datetime dependency to the UI module.
 */
data class BlobItem(
    val path: String,
    val filename: String? = null,
    val sizeBytes: Long = 0,
    val contentType: String? = null,
    val lastModified: String? = null,
    val createdAt: String? = null,
    val etag: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val isFolder: Boolean = false,
)

/**
 * Result of a paginated blob listing.
 */
data class BlobListResult(
    val items: List<BlobItem>,
    val commonPrefixes: List<String> = emptyList(),
    val nextPageToken: String? = null,
) {
    val hasMore: Boolean get() = nextPageToken != null
}

/**
 * Capabilities declared by the blob store backend.
 */
data class BlobExplorerCapabilities(
    val supportsCopy: Boolean = false,
    val supportsMove: Boolean = false,
    val supportsDelete: Boolean = true,
    val supportsUpload: Boolean = true,
    val supportsTempUrls: Boolean = false,
)

/**
 * Data source interface the host application implements to provide blob operations.
 * Decouples the UI from DI and transport.
 */
interface BlobExplorerDataSource {
    val capabilities: BlobExplorerCapabilities

    suspend fun listBlobs(
        prefix: String?,
        pageToken: String?,
    ): BlobListResult

    suspend fun deleteBlob(path: String): Boolean

    suspend fun copyBlob(
        source: String,
        destination: String,
    ): BlobItem

    suspend fun moveBlob(
        source: String,
        destination: String,
    ): BlobItem

    suspend fun uploadBlob(
        path: String,
        data: ByteArray,
        contentType: String?,
    ): BlobItem

    suspend fun createTempUrl(path: String): String
}
