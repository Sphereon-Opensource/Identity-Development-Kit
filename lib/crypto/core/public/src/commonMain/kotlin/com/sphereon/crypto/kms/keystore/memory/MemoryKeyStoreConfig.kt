/*
 * © 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.crypto.kms.keystore.memory

import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.kms.AbstractKeyStoreConfig
import com.sphereon.crypto.core.kms.KeyStoreConfig
import com.sphereon.crypto.core.kms.KeyStoreConfigImpl
import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes
import com.sphereon.di.Order
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("MemoryKeyStoreConfigType", exact = true)
interface MemoryKeyStoreConfigType : KeyStoreConfig {}

object MemoryKeyStoreConfigSerializer : JsonContentPolymorphicSerializer<KeyStoreConfig>(KeyStoreConfig::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<KeyStoreConfig> = when {
        "type" in element.jsonObject && element.jsonObject["type"]?.jsonPrimitive?.content == PredefinedKeyStoreTypes.MEMORY.keyStoreType -> MemoryKeyStoreConfig.serializer()
        else -> KeyStoreConfigImpl.serializer()
    }
}

@Serializable
@JsExportCompat
@SerialName("memory")
@OptIn(ExperimentalObjCName::class)
@ObjCName("MemoryKeyStoreConfig", exact = true)
data class MemoryKeyStoreConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val id: String = "memory",

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val enabled: Boolean = true,

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val order: Int = Order.MEDIUM.orderValue,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("defaultConfigValues")
    override val defaultConfigValues: Map<String, String> = emptyMap(),

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("keyVisibility")
    override val keyVisibility: String = KeyVisibility.PUBLIC.keyVisibility,

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("overwriteAlias")
    override val overwriteAlias: Boolean = true,

    /**
     * Defines the scope binding level for this keystore instance.
     * - APP: Single storage shared across entire application
     * - TENANT: Storage partitioned by tenant (default and suitable for REST APIs)
     * - PRINCIPAL_TENANT: Storage partitioned by principal + tenant
     * - SESSION: Storage partitioned by session + principal + tenant
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("scopeBinding")
    val scopeBinding: String = MemoryKeyStoreScopeBinding.TENANT.value,

    ) : AbstractKeyStoreConfig(), MemoryKeyStoreConfigType {

    @OptIn(InternalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @Transient
    override val keyStoreType: String = PredefinedKeyStoreTypes.MEMORY.keyStoreType


    //  get the discriminator as a field (the field name, objectName, is unimportant)
    // this must be a delegated field so there's no backing field, so kxs ignores it
    @OptIn(InternalSerializationApi::class)
    val type: String
        get() = this::class.serializer().descriptor.serialName

}
