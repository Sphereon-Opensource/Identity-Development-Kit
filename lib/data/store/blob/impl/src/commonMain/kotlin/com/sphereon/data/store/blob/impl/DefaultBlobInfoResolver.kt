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

package com.sphereon.data.store.blob.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobInfoResolver
import com.sphereon.data.store.blob.BlobInfoType
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.ResolvedBlobInfo

/**
 * Default [BlobInfoResolver] that delegates to [BlobService].
 *
 * If the input is already [ResolvedBlobInfo], returns it directly (no re-fetch).
 * Resolves tenant/store from the [BlobInfoType], falling back to configured defaults.
 */
class DefaultBlobInfoResolver(
    private val blobService: BlobService,
    private val defaultTenantId: String = "default",
) : BlobInfoResolver {
    override suspend fun resolve(info: BlobInfoType): IdkResult<ResolvedBlobInfo, IdkError> {
        if (info is ResolvedBlobInfo) {
            return Ok(info)
        }

        val path =
            info.path
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for resolve"))

        return blobService.getBlob(info)
    }

    override suspend fun store(
        info: BlobInfoType,
        data: ByteArray,
        options: PutOptions,
    ): IdkResult<BlobDescriptor, IdkError> {
        val blobInfo = info.toBlobInfo()

        return blobService.storeBlob(
            target = blobInfo,
            data = data,
            options = options,
        )
    }
}
