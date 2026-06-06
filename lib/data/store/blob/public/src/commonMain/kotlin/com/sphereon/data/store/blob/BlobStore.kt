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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Core blob store interface. All blob operations are suspend functions returning [IdkResult].
 *
 * Backends implement the required operations; optional operations have default implementations
 * that return UNSUPPORTED errors. Check [capabilities] to determine what the backend supports.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobStore", exact = true)
interface BlobStore {
    /**
     * The BACKEND SCHEME this store implements (e.g. `"memory"`, `"filesystem"`, `"kv"`, `"okd"`),
     * a constant baked into each implementation.
     *
     * This is NOT the configured registry id. Callers resolve a store by its CONFIGURED id
     * (e.g. `"default"` from `blob.stores.default.*`) via [BlobStoreService.getStore], and that
     * configured id is what travels in [BlobInfo.storeId] and is stamped onto every caller-facing
     * [BlobDescriptor.storeId] by [BlobService]. The scheme id here only identifies which backend
     * implementation a store is; never round-trip it back through the service as a store id.
     */
    val schemeId: String

    val capabilities: BlobStoreCapabilities

    suspend fun put(
        target: BlobInfo,
        data: ByteArray,
        options: PutOptions = PutOptions.DEFAULT,
    ): IdkResult<BlobDescriptor, IdkError>

    suspend fun get(info: BlobInfo): IdkResult<ResolvedBlobInfo, IdkError>

    suspend fun delete(info: BlobInfo): IdkResult<Boolean, IdkError>

    suspend fun exists(info: BlobInfo): IdkResult<Boolean, IdkError> {
        val statResult = stat(info)
        if (statResult.isErr) {
            if (statResult.error.code == "BLOB_NOT_FOUND") {
                return Ok(false)
            }
            return Err(statResult.error)
        }
        return Ok(true)
    }

    suspend fun stat(info: BlobInfo): IdkResult<BlobDescriptor, IdkError>

    suspend fun list(
        info: BlobInfo,
        options: ListOptions = ListOptions.DEFAULT,
    ): IdkResult<ListResult, IdkError>

    suspend fun putIfAbsent(
        target: BlobInfo,
        data: ByteArray,
    ): IdkResult<BlobDescriptor, IdkError> {
        val existsResult = exists(target)
        if (existsResult.isErr) {
            return Err(existsResult.error)
        }
        if (existsResult.value) {
            return Err(BlobStoreError.AlreadyExists(target.path ?: "").toIdkError())
        }
        return put(target, data, PutOptions.NO_OVERWRITE)
    }

    suspend fun deletePrefix(info: BlobInfo): IdkResult<Int, IdkError> = Err(BlobStoreError.Unsupported("deletePrefix").toIdkError())

    suspend fun copy(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> {
        if (!capabilities.supportsCopy) {
            return Err(BlobStoreError.Unsupported("copy").toIdkError())
        }
        return Err(BlobStoreError.Unsupported("copy").toIdkError())
    }

    suspend fun move(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> {
        if (!capabilities.supportsMove) {
            return Err(BlobStoreError.Unsupported("move").toIdkError())
        }
        return Err(BlobStoreError.Unsupported("move").toIdkError())
    }

    suspend fun createTempUrl(
        info: BlobInfo,
        options: TempUrlOptions = TempUrlOptions.DEFAULT,
    ): IdkResult<TempUrlResult, IdkError> = Err(BlobStoreError.Unsupported("createTempUrl").toIdkError())
}
