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

import com.sphereon.core.api.json.JsonSupport
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Blob store JSON serialization support.
 *
 * Each blob store backend module registers its config type via [register].
 */
object BlobStoreJsonSupport : SynchronizedObject() {
    private val initialized = atomic(false)

    private val localRegistrations = mutableListOf<SerializersModuleBuilder.() -> Unit>()

    fun register(
        registrationId: String? = null,
        block: SerializersModuleBuilder.() -> Unit,
    ) {
        synchronized(this) {
            localRegistrations.add(block)
        }
    }

    fun ensureBaseTypesRegistered() {
        if (initialized.value) {
            return
        }
        synchronized(this) {
            if (initialized.value) {
                return
            }
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
        get() =
            Json {
                ignoreUnknownKeys = true
                classDiscriminator = "type"
                serializersModule = module
            }

    fun decodeBlobStoreConfig(jsonString: String): BlobStoreConfigBase = serializer.decodeFromString(PolymorphicSerializer(BlobStoreConfigBase::class), jsonString)
}
