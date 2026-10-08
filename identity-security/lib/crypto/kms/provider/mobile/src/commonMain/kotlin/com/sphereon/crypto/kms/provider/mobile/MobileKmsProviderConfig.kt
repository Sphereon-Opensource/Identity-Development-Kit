/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.crypto.kms.provider.mobile

import com.sphereon.crypto.core.kms.AbstractKmsProviderConfig
import com.sphereon.crypto.core.kms.KmsProviderConfig
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import com.sphereon.crypto.core.kms.PredefinedKmsProviderTypes
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
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * [KmsProviderConfigBase.defaultConfigValues] key requesting a specific hardware-backing preference
 * for key generation (wallet-v4 P2 Task 5, `LocalNativeWscd`). Value is one of
 * [MOBILE_KMS_HARDWARE_BACKING_REQUIRED] or [MOBILE_KMS_HARDWARE_BACKING_PREFERRED]; absent or any
 * other value falls back to the provider's original, unconditional behavior (preferred).
 */
const val MOBILE_KMS_HARDWARE_BACKING_KEY: String = "hardware.backing"

/**
 * Requests signum-supreme's `REQUIRED` hardware-backing feature preference: the platform actual then
 * refuses to create the key at all when hardware-backed storage cannot be provided, instead of
 * silently falling back to software storage. See `MobileKmsProviderImpl.hardwareBackingPreference`.
 */
const val MOBILE_KMS_HARDWARE_BACKING_REQUIRED: String = "required"

/**
 * Requests signum-supreme's `PREFERRED` hardware-backing feature preference (default when
 * [MOBILE_KMS_HARDWARE_BACKING_KEY] is absent): hardware is used when available, with a silent
 * software fallback otherwise.
 */
const val MOBILE_KMS_HARDWARE_BACKING_PREFERRED: String = "preferred"

@OptIn(ExperimentalObjCName::class)
@ObjCName("MobileKmsProviderConfigType", exact = true)
interface MobileKmsProviderConfigType : KmsProviderConfigBase

object MobileKmsProviderConfigSerializer : JsonContentPolymorphicSerializer<KmsProviderConfigBase>(KmsProviderConfigBase::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<KmsProviderConfigBase> =
        when {
            "type" in element.jsonObject && element.jsonObject["type"]?.jsonPrimitive?.content == "mobile" -> MobileKmsProviderConfig.serializer()
            else -> KmsProviderConfig.serializer()
        }
}

@Serializable
@SerialName("mobile") // mapped onto kmsProviderType
@OptIn(ExperimentalObjCName::class)
@ObjCName("MobileKmsProviderConfig", exact = true)
data class MobileKmsProviderConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val id: String = "mobile",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val enabled: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val order: Int = Order.MEDIUM.orderValue,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("exposePrivateKeysDuringGeneration")
    override val exposePrivateKeysDuringGeneration: Boolean = false,
    @SerialName("persistKeysDuringGeneration")
    override val persistKeysDuringGeneration: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("defaultConfigValues")
    override val defaultConfigValues: Map<String, String> = emptyMap(),
) : AbstractKmsProviderConfig(),
    MobileKmsProviderConfigType {
    @OptIn(InternalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @Transient
//    @SerialName("type")
    override val kmsProviderType: String = PredefinedKmsProviderTypes.MOBILE.kmsProviderType

    //  get the discriminator as a field (the field name, objectName, is unimportant)
    // this must be a delegated field so there's no backing field, so kxs ignores it
    @OptIn(InternalSerializationApi::class)
    val type: String
        get() = this::class.serializer().descriptor.serialName
}
