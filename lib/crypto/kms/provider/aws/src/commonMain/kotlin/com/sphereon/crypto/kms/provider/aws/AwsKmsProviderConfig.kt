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

package com.sphereon.crypto.kms.provider.aws

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.kms.AbstractKmsProviderConfig
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import com.sphereon.crypto.core.kms.PredefinedKmsProviderTypes
import com.sphereon.crypto.core.kms.model.CredentialOpts
import com.sphereon.crypto.core.kms.model.ExponentialBackoffRetryOpts
import com.sphereon.di.Order
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.serializer
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The polymorphic, config-driven view of an AWS KMS provider. Mirrors the `software`/`azure_keyvault`
 * providers: it is a [KmsProviderConfigBase] subclass selected by the `type` discriminator (`aws_kms`),
 * so the tenant-kms provider binder reconstructs it from `kms.providers.<id>.*` config and the
 * [AwsKmsProviderFactory] turns it into a running provider. The connection settings reuse the shared
 * [com.sphereon.crypto.core.kms.model.AwsKmsClientConfig] field shapes.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AwsKmsProviderConfigType", exact = true)
interface AwsKmsProviderConfigType : KmsProviderConfigBase {
    val applicationId: String
    val region: String
    val credentialOpts: CredentialOpts
    val exponentialBackoffRetryOpts: ExponentialBackoffRetryOpts?
}

@Serializable
@JsExportCompat
@SerialName("aws_kms") // mapped onto kmsProviderType
@OptIn(ExperimentalObjCName::class)
@ObjCName("AwsKmsProviderConfig", exact = true)
data class AwsKmsProviderConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val id: String = "aws_kms",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val enabled: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val order: Int = Order.HIGH.orderValue,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("exposePrivateKeysDuringGeneration")
    override val exposePrivateKeysDuringGeneration: Boolean = false,
    @SerialName("persistKeysDuringGeneration")
    override val persistKeysDuringGeneration: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("defaultConfigValues")
    override val defaultConfigValues: Map<String, String> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("applicationId")
    override val applicationId: String = "aws-kms",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("region")
    override val region: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("credentialOpts")
    override val credentialOpts: CredentialOpts,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("exponentialBackoffRetryOpts")
    override val exponentialBackoffRetryOpts: ExponentialBackoffRetryOpts? = null,
) : AbstractKmsProviderConfig(),
    AwsKmsProviderConfigType {
    @OptIn(InternalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @Transient
    override val kmsProviderType: String = PredefinedKmsProviderTypes.AWS_KMS.kmsProviderType

    //  get the discriminator as a field (the field name is unimportant); a delegated get()-only field has
    //  no backing field, so kotlinx serialization ignores it.
    @OptIn(InternalSerializationApi::class)
    val type: String
        get() = this::class.serializer().descriptor.serialName
}
