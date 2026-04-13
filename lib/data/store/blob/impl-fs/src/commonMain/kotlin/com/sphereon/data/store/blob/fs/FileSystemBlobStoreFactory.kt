package com.sphereon.data.store.blob.fs

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreBackends
import com.sphereon.data.store.blob.BlobStoreConfigBase
import com.sphereon.data.store.blob.BlobStoreFactory
import com.sphereon.data.store.blob.FileSystemBlobStoreConfig
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<BlobStoreFactory>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("FileSystemBlobStoreFactoryImpl", exact = true)
class FileSystemBlobStoreFactoryImpl : BlobStoreFactory {
    override val backendId: String = BlobStoreBackends.FILESYSTEM
    override fun create(config: BlobStoreConfigBase, execution: SessionExecution?): BlobStore {
        require(config.backendId.equals(backendId, ignoreCase = true)) {
            "FileSystemBlobStoreFactory cannot create backend '${config.backendId}'. Supported: '$backendId'"
        }
        val typedConfig = config as? FileSystemBlobStoreConfig
            ?: throw IllegalArgumentException(
                "FileSystem blob store requires FileSystemBlobStoreConfig, got ${config::class.simpleName}. " +
                "Ensure 'blob.stores.${config.id}.type=filesystem' is set in config."
            )
        require(typedConfig.rootDir.isNotBlank()) {
            "Filesystem blob store config '${config.id}' must have a non-blank 'rootDir'"
        }
        return FileSystemBlobStore(
            rootDir = typedConfig.rootDir,
            autoCreateDirs = typedConfig.autoCreateDirs,
        )
    }
}
