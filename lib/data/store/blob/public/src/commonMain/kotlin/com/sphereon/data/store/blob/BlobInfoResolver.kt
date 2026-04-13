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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Resolves [BlobInfoType] references to actual content, and stores content at [BlobInfo] targets.
 *
 * Mirrors the KMS provider registry pattern: commands accept blob _references_,
 * and the resolver handles the actual I/O against the correct store backend.
 *
 * If the input is already a [ResolvedBlobInfo], [resolve] returns it directly (no re-fetch).
 * This enables the same pipeline optimization as [ResolvedKeyInfo] in KMS: resolve once,
 * pass through subsequent stages without redundant lookups.
 *
 * ## Example: eIDAS seal pipeline
 * ```kotlin
 * val source = BlobInfo(storeId = "sharepoint", path = "contracts/doc.pdf")
 * val target = BlobInfo(storeId = "azure", path = "sealed/doc.pdf")
 *
 * val resolved = resolver.resolve(source)           // Read from SharePoint
 * val sealed = sealService.seal(resolved.data, cert) // Apply eIDAS seal
 * resolver.store(target, sealed)                     // Write to Azure
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobInfoResolver", exact = true)
interface BlobInfoResolver {
    /**
     * Resolve a [BlobInfoType] to its content.
     *
     * If [info] is already a [ResolvedBlobInfo], returns it directly.
     * If [info] is a [BlobInfo], reads from the referenced store.
     *
     * @param info Reference identifying which store and path to read from.
     * @return The resolved blob with content, or an error.
     */
    suspend fun resolve(info: BlobInfoType): IdkResult<ResolvedBlobInfo, IdkError>

    /**
     * Store content at the location described by a [BlobInfoType].
     *
     * If [info] is a [BlobInfo] with null path, the store assigns a path (server-generated ID).
     * Content type and metadata are taken from the info.
     *
     * @param info Reference identifying which store and path to write to.
     * @param data The blob content to store.
     * @param options Put options (overwrite behavior, digest algorithm).
     * @return The descriptor of the stored blob, or an error.
     */
    suspend fun store(
        info: BlobInfoType,
        data: ByteArray,
        options: PutOptions = PutOptions.DEFAULT,
    ): IdkResult<BlobDescriptor, IdkError>
}
