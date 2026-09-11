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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreCapabilities
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.ResolvedBlobInfo
import com.sphereon.data.store.blob.TempUrlOptions
import com.sphereon.data.store.blob.TempUrlResult

/**
 * Tenant-scoping [BlobStore] decorator.
 *
 * [DefaultBlobService] applies the `"<tenantId>/<path>"` scoping convention
 * (see [BlobTenantScope]) to every operation, but it is session-scoped. Callers
 * that hold a [BlobStore] directly WITHOUT a session — e.g. an app-scoped
 * listener that writes blobs another component later reads back through the
 * session-scoped service — would otherwise write to the unscoped path and the
 * scoped read would miss it (`BLOB_NOT_FOUND`).
 *
 * Wrapping the backing store in this decorator applies the SAME scope-on-write /
 * unscope-on-read transform, sourcing the tenant from each [BlobInfo], so both
 * sides agree on the physical layout while every caller keeps using logical
 * (unscoped) paths. Drop-in: it does not change any caller's view of paths.
 *
 * The scoping convention itself lives in [BlobTenantScope] — shared with
 * [DefaultBlobService] so there is a single owner.
 */
class TenantScopedBlobStore(
    private val delegate: BlobStore,
) : BlobStore {

    override val schemeId: String get() = delegate.schemeId
    override val capabilities: BlobStoreCapabilities get() = delegate.capabilities

    private fun scopeInfo(info: BlobInfo): BlobInfo {
        val tenantId = BlobTenantScope.tenantOf(info)
        return info.copy(path = info.path?.let { BlobTenantScope.scopePath(it, tenantId) })
    }

    private fun unscopeDescriptor(descriptor: BlobDescriptor, tenantId: String): BlobDescriptor =
        descriptor.copy(path = BlobTenantScope.unscopePath(descriptor.path, tenantId))

    private fun unscopeResolved(resolved: ResolvedBlobInfo, tenantId: String): ResolvedBlobInfo =
        resolved.copy(
            info = resolved.info.copy(path = resolved.info.path?.let { BlobTenantScope.unscopePath(it, tenantId) }),
            descriptor = unscopeDescriptor(resolved.descriptor, tenantId),
        )

    override suspend fun put(
        target: BlobInfo,
        data: ByteArray,
        options: PutOptions,
    ): IdkResult<BlobDescriptor, IdkError> {
        val tenantId = BlobTenantScope.tenantOf(target)
        val result = delegate.put(scopeInfo(target), data, options)
        return if (result is Ok) Ok(unscopeDescriptor(result.value, tenantId)) else result
    }

    override suspend fun get(info: BlobInfo): IdkResult<ResolvedBlobInfo, IdkError> {
        val tenantId = BlobTenantScope.tenantOf(info)
        val result = delegate.get(scopeInfo(info))
        return if (result is Ok) Ok(unscopeResolved(result.value, tenantId)) else result
    }

    override suspend fun delete(info: BlobInfo): IdkResult<Boolean, IdkError> =
        delegate.delete(scopeInfo(info))

    override suspend fun exists(info: BlobInfo): IdkResult<Boolean, IdkError> =
        delegate.exists(scopeInfo(info))

    override suspend fun stat(info: BlobInfo): IdkResult<BlobDescriptor, IdkError> {
        val tenantId = BlobTenantScope.tenantOf(info)
        val result = delegate.stat(scopeInfo(info))
        return if (result is Ok) Ok(unscopeDescriptor(result.value, tenantId)) else result
    }

    override suspend fun list(
        info: BlobInfo,
        options: ListOptions,
    ): IdkResult<ListResult, IdkError> {
        val tenantId = BlobTenantScope.tenantOf(info)
        val scopedOptions = options.copy(prefix = BlobTenantScope.scopePrefix(options.prefix, tenantId))
        val result = delegate.list(scopeInfo(info), scopedOptions)
        return if (result is Ok) {
            Ok(
                result.value.copy(
                    descriptors = result.value.descriptors.map { unscopeDescriptor(it, tenantId) },
                    commonPrefixes = result.value.commonPrefixes.map { BlobTenantScope.unscopePath(it, tenantId) },
                ),
            )
        } else {
            result
        }
    }

    override suspend fun putIfAbsent(
        target: BlobInfo,
        data: ByteArray,
    ): IdkResult<BlobDescriptor, IdkError> {
        val tenantId = BlobTenantScope.tenantOf(target)
        val result = delegate.putIfAbsent(scopeInfo(target), data)
        return if (result is Ok) Ok(unscopeDescriptor(result.value, tenantId)) else result
    }

    override suspend fun deletePrefix(info: BlobInfo): IdkResult<Int, IdkError> =
        delegate.deletePrefix(scopeInfo(info))

    override suspend fun copy(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> {
        val tenantId = BlobTenantScope.tenantOf(destination)
        val result = delegate.copy(scopeInfo(source), scopeInfo(destination))
        return if (result is Ok) Ok(unscopeDescriptor(result.value, tenantId)) else result
    }

    override suspend fun move(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> {
        val tenantId = BlobTenantScope.tenantOf(destination)
        val result = delegate.move(scopeInfo(source), scopeInfo(destination))
        return if (result is Ok) Ok(unscopeDescriptor(result.value, tenantId)) else result
    }

    override suspend fun createTempUrl(
        info: BlobInfo,
        options: TempUrlOptions,
    ): IdkResult<TempUrlResult, IdkError> =
        delegate.createTempUrl(scopeInfo(info), options)
}
