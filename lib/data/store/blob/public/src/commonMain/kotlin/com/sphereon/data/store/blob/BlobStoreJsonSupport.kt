package com.sphereon.data.store.blob

import com.sphereon.core.api.json.JsonSupport
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Blob store JSON serialization support.
 *
 * Each blob store backend module registers its config type via [register].
 */
object BlobStoreJsonSupport : SynchronizedObject() {

    private val initialized = atomic(false)

    private val localRegistrations = mutableListOf<SerializersModuleBuilder.() -> Unit>()

    fun register(registrationId: String? = null, block: SerializersModuleBuilder.() -> Unit) {
        synchronized(this) {
            localRegistrations.add(block)
        }
    }

    fun ensureBaseTypesRegistered() {
        if (initialized.value) return
        synchronized(this) {
            if (initialized.value) return
            initialized.value = true
            localRegistrations.add {
                polymorphic(BlobStoreConfigBase::class) {
                    subclass(BlobStoreConfig::class)
                    subclass(InMemoryBlobStoreConfig::class)
                    subclass(FileSystemBlobStoreConfig::class)
                }
            }
        }
    }

    val module: SerializersModule
        get() {
            ensureBaseTypesRegistered()
            val snapshot = synchronized(this) { localRegistrations.toList() }
            return SerializersModule {
                for (registration in snapshot) {
                    registration()
                }
            }
        }

    val serializer: Json
        get() = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
            serializersModule = module
        }

    fun decodeBlobStoreConfig(jsonString: String): BlobStoreConfigBase {
        return serializer.decodeFromString(PolymorphicSerializer(BlobStoreConfigBase::class), jsonString)
    }
}
