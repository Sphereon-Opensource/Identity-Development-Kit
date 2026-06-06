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
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreCapabilities
import com.sphereon.data.store.blob.BlobStoreError
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.ResolvedBlobInfo
import com.sphereon.data.store.blob.TempUrlOptions
import com.sphereon.data.store.blob.TempUrlResult
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Decorator that rejects all write operations, making a store effectively read-only.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ReadOnlyBlobStore", exact = true)
class ReadOnlyBlobStore(
    private val delegate: BlobStore,
) : BlobStore {
    override val schemeId: String get() = delegate.schemeId
    override val capabilities: BlobStoreCapabilities get() = delegate.capabilities

    private fun readOnlyError(): IdkError = BlobStoreError.PermissionDenied("Blob store is read-only").toIdkError()

    override suspend fun put(
        target: BlobInfo,
        data: ByteArray,
        options: PutOptions,
    ): IdkResult<BlobDescriptor, IdkError> = Err(readOnlyError())

    override suspend fun get(info: BlobInfo): IdkResult<ResolvedBlobInfo, IdkError> = delegate.get(info)

    override suspend fun delete(info: BlobInfo): IdkResult<Boolean, IdkError> = Err(readOnlyError())

    override suspend fun exists(info: BlobInfo): IdkResult<Boolean, IdkError> = delegate.exists(info)

    override suspend fun stat(info: BlobInfo): IdkResult<BlobDescriptor, IdkError> = delegate.stat(info)

    override suspend fun list(
        info: BlobInfo,
        options: ListOptions,
    ): IdkResult<ListResult, IdkError> = delegate.list(info, options)

    override suspend fun putIfAbsent(
        target: BlobInfo,
        data: ByteArray,
    ): IdkResult<BlobDescriptor, IdkError> = Err(readOnlyError())

    override suspend fun deletePrefix(info: BlobInfo): IdkResult<Int, IdkError> = Err(readOnlyError())

    override suspend fun copy(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> = Err(readOnlyError())

    override suspend fun move(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> = Err(readOnlyError())

    override suspend fun createTempUrl(
        info: BlobInfo,
        options: TempUrlOptions,
    ): IdkResult<TempUrlResult, IdkError> = delegate.createTempUrl(info, options)
}
