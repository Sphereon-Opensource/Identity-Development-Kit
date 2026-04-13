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

package com.sphereon.crypto.core.json

import com.sphereon.cbor.json.JsonView
import com.sphereon.core.api.json.JsonSupport
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyJsonDTOType
import com.sphereon.crypto.core.cose.CoseKeyJsonType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.kms.KeyStoreConfig
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName

@JsExportCompat
@JsName("cryptoJsonSerializer")
val cryptoJsonSerializer: Json
    get() = CryptoJsonSupport.serializer


/**
 * Crypto-specific JSON serialization support.
 *
 * This object delegates registration to [JsonSupport] for the core mechanism,
 * while adding crypto-specific polymorphic serializers (COSE, JWK, etc.).
 *
 * For backward compatibility, existing code can continue to use [CryptoJsonSupport.register]
 * and [CryptoJsonSupport.serializer]. The registrations are stored in [JsonSupport].
 */
@JsExportCompat
@JsName("CryptoJsonSupport")
object CryptoJsonSupport {
    /**
     * Register a serializer module with an optional unique ID.
     * If an ID is provided and already registered, the registration is skipped.
     *
     * Delegates to [JsonSupport.register] for actual storage.
     *
     * @param registrationId Optional unique identifier for this registration (e.g., "memory-keystore")
     * @param block The serializer module builder block
     */
    fun register(registrationId: String? = null, block: SerializersModuleBuilder.() -> Unit) {
        JsonSupport.register(registrationId, block)
    }

    val module: SerializersModule
        get() = SerializersModule {
            // Include all registrations from JsonSupport
            include(JsonSupport.module)

            // Crypto-specific polymorphic serializers
            // Ensures we can do polymorphic serialization of both the Key and Private Key entries using the KeyEntry interface
            // TODO: Cbor key json serialization
            polymorphic(CoseKeyJsonDTOType::class) {
                subclass(CoseKeyJson::class)
            }
            polymorphic(CoseKeyJsonType::class) {
                subclass(CoseKeyJson::class)
            }
            polymorphic(JwkType::class) {
                subclass(Jwk::class)
            }
            polymorphic(KeyType::class) {
                subclass(Jwk::class)
                subclass(CoseKeyJson::class)
            }

            // TODO: Why do we have this one?
            polymorphic(JsonView::class) {
                subclass(CoseKeyJson::class)
            }
        }

    val serializer: Json
        get() = Json { serializersModule = module }

    /**
     * Decode a KmsProviderConfigBase from JSON string.
     * This method automatically uses the polymorphic serializer, so it works correctly
     * on all platforms including Kotlin/Native.
     *
     * @param json The JSON string to decode
     * @return The decoded KmsProviderConfigBase instance
     */
    fun decodeKmsProviderConfig(json: String): KmsProviderConfigBase {
        return serializer.decodeFromString(
            PolymorphicSerializer(KmsProviderConfigBase::class),
            json
        )
    }

    /**
     * Encode a KmsProviderConfigBase to JSON string.
     * This method automatically uses the polymorphic serializer for consistency.
     *
     * @param config The KmsProviderConfigBase to encode
     * @return The JSON string
     */
    fun encodeKmsProviderConfig(config: KmsProviderConfigBase): String {
        return serializer.encodeToString(
            PolymorphicSerializer(KmsProviderConfigBase::class),
            config
        )
    }

     fun decodeKeyStoreConfig(json: String): KeyStoreConfig {
         return serializer.decodeFromString(
             PolymorphicSerializer(KeyStoreConfig::class),
             json
         )
    }
}
