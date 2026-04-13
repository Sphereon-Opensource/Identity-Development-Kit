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

import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * Declares the capabilities of a blob store backend.
 */
@Serializable
data class BlobStoreCapabilities(
    val supportsEtag: Boolean = false,
    val supportsCopy: Boolean = false,
    val supportsMove: Boolean = false,
    val supportsBulkDelete: Boolean = false,
    val supportsTempUrls: Boolean = false,
    val supportsListing: Boolean = true,
    val supportsMetadata: Boolean = true,
    val supportsFolders: Boolean = false,
    val maxBlobSizeBytes: Long = Long.MAX_VALUE,
    val maxTempUrlDuration: Duration? = null,
) {
    companion object {
        val MINIMAL = BlobStoreCapabilities()

        val SIMPLE =
            BlobStoreCapabilities(
                supportsCopy = true,
                supportsMove = true,
                supportsBulkDelete = true,
                supportsFolders = true,
            )

        val CLOUD_OBJECT_STORE =
            BlobStoreCapabilities(
                supportsEtag = true,
                supportsCopy = true,
                supportsMove = true,
                supportsBulkDelete = true,
                supportsTempUrls = true,
            )
    }
}
