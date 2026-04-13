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

package com.sphereon.crypto.kms.provider.software

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.kms.AbstractKmsProviderConfig
import com.sphereon.crypto.core.kms.KeyStoreConfig
import com.sphereon.crypto.core.kms.KmsProviderConfig
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import com.sphereon.crypto.core.kms.PredefinedKmsProviderTypes
import com.sphereon.crypto.kms.keystore.memory.MemoryKeyStoreConfig
import com.sphereon.di.Order
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Polymorphic
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

@OptIn(ExperimentalObjCName::class)
@ObjCName("SoftwareKmsProviderConfigType", exact = true)
interface SoftwareKmsProviderConfigType : KmsProviderConfigBase {
    val cryptographyProvider: String?

    @Polymorphic
    val keyStore: KeyStoreConfig?

    val autoCreateCertificate: Boolean
//
//    @Transient
//    val privateKeyStore: KeyStoreService
}

object SoftwareKmsProviderConfigSerializer : JsonContentPolymorphicSerializer<KmsProviderConfigBase>(KmsProviderConfigBase::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<KmsProviderConfigBase> =
        when {
            "type" in element.jsonObject && element.jsonObject["type"]?.jsonPrimitive?.content == "software" -> SoftwareKmsProviderConfig.serializer()
            else -> KmsProviderConfig.serializer()
        }
}

@Serializable
@JsExportCompat
@SerialName("software") // mapped onto kmsProviderType
@OptIn(ExperimentalObjCName::class)
@ObjCName("SoftwareKmsProviderConfig", exact = true)
data class SoftwareKmsProviderConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val id: String = "software",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val enabled: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("exposePrivateKeysDuringGeneration")
    override val exposePrivateKeysDuringGeneration: Boolean = true,
    @SerialName("persistKeysDuringGeneration")
    override val persistKeysDuringGeneration: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val order: Int = Order.MEDIUM.orderValue,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("defaultConfigValues")
    override val defaultConfigValues: Map<String, String> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("cryptographyProvider")
    override val cryptographyProvider: String = CryptographyProvider.Default.name,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("autoCreateCertificate")
    override val autoCreateCertificate: Boolean = false,
    @Polymorphic
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("keyStore")
    override val keyStore: KeyStoreConfig = MemoryKeyStoreConfig(keyVisibility = KeyVisibility.PRIVATE.keyVisibility),
) : AbstractKmsProviderConfig(),
    SoftwareKmsProviderConfigType {
    @OptIn(InternalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @Transient
//    @SerialName("type")
    override val kmsProviderType: String = PredefinedKmsProviderTypes.SOFTWARE.kmsProviderType

    //  get the discriminator as a field (the field name, objectName, is unimportant)
    // this must be a delegated field so there's no backing field, so kxs ignores it
    @OptIn(InternalSerializationApi::class)
    val type: String
        get() = this::class.serializer().descriptor.serialName

    /*
    @Transient
    override var privateKeyStore: KeyStoreService =
        when (keyStore.keyStoreType) {
            PredefinedKeyStoreTypes.JKS.keyStoreType, -> SoftwareKeyStoreService(config = (keyStore as JksKeyStoreConfig))
            PredefinedKeyStoreTypes.PKCS12.keyStoreType -> SoftwareKeyStoreService(config = (keyStore as Pkcs12KeyStoreConfig))
            PredefinedKeyStoreTypes.MEMORY.keyStoreType -> MemoryKeyStoreService(config = (keyStore as MemoryKeyStoreConfig))
            else -> throw IllegalArgumentException("Unknown key store type ${keyStore.keyStoreType}")
        }

    fun setPrivateKeyStore(store: KeyStoreService) = apply { privateKeyStore = store }
     */
}
