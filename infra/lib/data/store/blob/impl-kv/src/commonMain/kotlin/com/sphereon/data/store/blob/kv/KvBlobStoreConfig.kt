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

package com.sphereon.data.store.blob.kv

import com.sphereon.data.store.blob.AbstractBlobStoreConfig
import com.sphereon.data.store.blob.BlobStoreConfigBase
import com.sphereon.data.store.blob.BlobStoreScopeBinding
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Blob store configuration backed by KvStore.
 *
 * Stores blobs as key-value entries: key = blob path, value = serialized BlobEntry (data + metadata).
 * Uses an existing KvStore instance (resolved by [kvStoreId]) for persistence.
 *
 * This is useful for small blobs (config files, certificates, small documents) where a separate
 * filesystem or cloud backend is overkill. The backing store is resolved from the normal
 * `kv.stores.<id>` configuration, so deployments can use memory, embedded kottage, PostgreSQL,
 * or any other registered KvStore backend.
 *
 * ```properties
 * blob.stores.small-docs.type=kvstore
 * blob.stores.small-docs.scopeBinding=TENANT
 * blob.stores.small-docs.kvStoreId=blob-kv-store
 *
 * # The backing KvStore must also be configured:
 * kv.stores.blob-kv-store.type=database
 * kv.stores.blob-kv-store.scopeBinding=TENANT
 * ```
 */
@Serializable
@SerialName("kvstore")
@OptIn(ExperimentalObjCName::class)
@ObjCName("KvBlobStoreConfig", exact = true)
data class KvBlobStoreConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val id: String = "kvstore",
    @SerialName("scopebinding")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val scopeBinding: BlobStoreScopeBinding = BlobStoreScopeBinding.TENANT,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val enabled: Boolean = true,
    /** ID of the KvStore to use for blob storage (must be configured in kv.stores.*) */
    @SerialName("kvStoreId")
    val kvStoreId: String = "blob-kv-store",
    /** Maximum blob size in bytes (0 = unlimited). KvStore isn't designed for large blobs. */
    @SerialName("maxBlobSizeBytes")
    val maxBlobSizeBytes: Long = 10 * 1024 * 1024, // 10MB default
) : AbstractBlobStoreConfig(),
    BlobStoreConfigBase {
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val backendId: String = BACKEND_ID

    companion object {
        const val BACKEND_ID = "kvstore"
    }
}
