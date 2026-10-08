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

package com.sphereon.crypto.kms.provider.rest

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.kms.AbstractKmsProviderConfig
import com.sphereon.crypto.core.kms.KmsProviderConfig
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import com.sphereon.crypto.core.kms.PredefinedKmsProviderTypes
import com.sphereon.di.Order
import com.sphereon.ktor.http.client.provider.SimpleRestClientOptions
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

@OptIn(ExperimentalObjCName::class)
@ObjCName("RestClientKmsProviderConfigType", exact = true)
interface RestClientKmsProviderConfigType : KmsProviderConfigBase {
    val restKmsUrl: String
    val httpClientOptions: SimpleRestClientOptions
    val restProviderId: String?

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("autoCreateCertificate")
    val autoCreateCertificate: Boolean

    val authConfig: RestClientAuthConfig
}

@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("RestClientAuthConfig", exact = true)
data class RestClientAuthConfig(
    /**
     * JWT presented to the remote KMS as an OAuth 2.0 bearer token.
     *
     * Tenant, principal, and workload identity are carried only by validated
     * claims in this token. They are never configured as separate HTTP headers.
     */
    val bearerJwt: String? = null,
)

object RestClientKmsProviderConfigSerializer : JsonContentPolymorphicSerializer<KmsProviderConfigBase>(KmsProviderConfigBase::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<KmsProviderConfigBase> =
        when {
            "type" in element.jsonObject && element.jsonObject["type"]?.jsonPrimitive?.content == "rest" -> RestClientKmsProviderConfig.serializer()
            else -> KmsProviderConfig.serializer()
        }
}

@Serializable
@JsExportCompat
@SerialName("rest") // mapped onto kmsProviderType
@OptIn(ExperimentalObjCName::class)
@ObjCName("RestClientKmsProviderConfig", exact = true)
data class RestClientKmsProviderConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val id: String = "rest",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val restProviderId: String? = id,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("url")
    override val restKmsUrl: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val enabled: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val order: Int = Order.HIGH.orderValue,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val httpClientOptions: SimpleRestClientOptions = SimpleRestClientOptions.DEFAULT_OPTIONS,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val authConfig: RestClientAuthConfig = RestClientAuthConfig(),
) : AbstractKmsProviderConfig(),
    RestClientKmsProviderConfigType {
    @OptIn(InternalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @Transient
//    @SerialName("type")
    override val kmsProviderType: String = PredefinedKmsProviderTypes.REST.kmsProviderType

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("autoCreateCertificate")
    override val autoCreateCertificate: Boolean = false

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("exposePrivateKeysDuringGeneration")
    override val exposePrivateKeysDuringGeneration: Boolean = false

    @SerialName("persistKeysDuringGeneration")
    override val persistKeysDuringGeneration: Boolean = false

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("defaultConfigValues")
    override val defaultConfigValues: Map<String, String> = emptyMap()

    //  get the discriminator as a field (the field name, objectName, is unimportant)
    // this must be a delegated field so there's no backing field, so kxs ignores it
    @OptIn(InternalSerializationApi::class)
    val type: String
        get() = this::class.serializer().descriptor.serialName
}
