package com.sphereon.data.store.kv

import com.sphereon.core.api.json.JsonSupport
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.DeserializationStrategy

/**
 * KV store JSON serialization support.
 *
 * Delegates registration to [JsonSupport] for the core mechanism.
 * Each KV store backend registers its config type via [register].
 */
object KvStoreJsonSupport {
    private var initialized = false

    fun register(registrationId: String? = null, block: SerializersModuleBuilder.() -> Unit) {
        JsonSupport.register(registrationId, block)
    }

    fun ensureBaseTypesRegistered() {
        if (initialized) return
        initialized = true
        register("kv-store-base") {
            polymorphic(KvStoreConfigBase::class) {
                subclass(KvStoreConfig::class)
                subclass(InMemoryKvStoreConfig::class)
                subclass(KottageKvStoreConfig::class)
                default { KvStoreConfig.serializer() }
            }
        }
    }

    val module: SerializersModule
        get() {
            ensureBaseTypesRegistered()
            return SerializersModule { include(JsonSupport.module) }
        }

    val serializer: Json
        get() = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
            serializersModule = module
        }
}
