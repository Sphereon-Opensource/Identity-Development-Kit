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

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Blob store backend identifiers.
 */
object BlobStoreBackends {
    const val MEMORY: String = "memory"
    const val FILESYSTEM: String = "filesystem"
}

/**
 * Scope binding for blob store instances — determines how stores are partitioned.
 */
@Serializable
@JsExportCompat
enum class BlobStoreScopeBinding {
    APP,
    TENANT,
}

/**
 * Base interface for all blob store configurations.
 *
 * Each backend provides a typed subclass (e.g., [InMemoryBlobStoreConfig], [FileSystemBlobStoreConfig]).
 * The polymorphic config binder deserializes the correct type based on the `type` discriminator field.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobStoreConfigBase", exact = true)
@JsExportCompat
interface BlobStoreConfigBase {
    val backendId: String
    val id: String
    val enabled: Boolean
    val scopeBinding: BlobStoreScopeBinding
}

/**
 * Abstract serializable base for blob store configs.
 * Subclasses add backend-specific typed fields.
 */
@Serializable
@JsExportCompat
abstract class AbstractBlobStoreConfig {
    abstract val backendId: String
    abstract val id: String
    abstract val enabled: Boolean

    @SerialName("scopebinding")
    abstract val scopeBinding: BlobStoreScopeBinding
}

/**
 * Generic fallback config for unknown or EDK-provided backends.
 * Used when no specific typed config subclass matches the `type` discriminator.
 */
@Serializable
@SerialName("BlobStoreConfig")
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobStoreConfig", exact = true)
@JsExportCompat
data class BlobStoreConfig(
    override val id: String,
    @SerialName("scopebinding")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val scopeBinding: BlobStoreScopeBinding = BlobStoreScopeBinding.TENANT,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val backendId: String = BlobStoreBackends.MEMORY,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val enabled: Boolean = true,
) : AbstractBlobStoreConfig(),
    BlobStoreConfigBase

/**
 * In-memory blob store configuration.
 *
 * ```properties
 * blob.stores.cache.type=memory
 * blob.stores.cache.scopeBinding=APP
 * blob.stores.cache.maxEntries=10000
 * ```
 */
@Serializable
@SerialName("memory")
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryBlobStoreConfig", exact = true)
@JsExportCompat
data class InMemoryBlobStoreConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val id: String = "memory",
    @SerialName("scopebinding")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val scopeBinding: BlobStoreScopeBinding = BlobStoreScopeBinding.APP,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val enabled: Boolean = true,
    @SerialName("maxentries")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val maxEntries: Int = 0,
) : AbstractBlobStoreConfig(),
    BlobStoreConfigBase {
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val backendId: String = BlobStoreBackends.MEMORY
}

/**
 * Filesystem blob store configuration.
 *
 * ```properties
 * blob.stores.documents.type=filesystem
 * blob.stores.documents.scopeBinding=TENANT
 * blob.stores.documents.rootDir=/var/data/blobs
 * blob.stores.documents.autoCreateDirs=true
 * ```
 */
@Serializable
@SerialName("filesystem")
@OptIn(ExperimentalObjCName::class)
@ObjCName("FileSystemBlobStoreConfig", exact = true)
@JsExportCompat
data class FileSystemBlobStoreConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val id: String = "filesystem",
    @SerialName("scopebinding")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val scopeBinding: BlobStoreScopeBinding = BlobStoreScopeBinding.TENANT,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val enabled: Boolean = true,
    @SerialName("rootDir")
    val rootDir: String = "",
    @SerialName("autoCreateDirs")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val autoCreateDirs: Boolean = true,
) : AbstractBlobStoreConfig(),
    BlobStoreConfigBase {
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val backendId: String = BlobStoreBackends.FILESYSTEM
}
