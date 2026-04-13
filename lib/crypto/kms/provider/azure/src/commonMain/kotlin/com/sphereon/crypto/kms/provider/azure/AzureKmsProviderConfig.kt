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

package com.sphereon.crypto.kms.provider.azure

import com.sphereon.core.compat.JsExportCompat
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

private const val SECOND = 1000L
private const val ONE = 1
private const val FIFTEEN = 15

/**
 * Available credential modes for Azure authentication.
 * Each mode corresponds to a specific authentication method and credential type.
 *
 * @param credentialType The type of credential (SERVICE or USER)
 */
@Serializable
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CredentialMode", exact = true)
enum class CredentialMode(
    val credentialType: CredentialType,
) {
    SERVICE_CLIENT_SECRET(CredentialType.SERVICE),
    SERVICE_CLIENT_CERTIFICATE(CredentialType.SERVICE),
    USER_INTERACTIVE_BROWSER(CredentialType.USER),
    USER_USERNAME_PASSWORD(CredentialType.USER),
}

/**
 * Container for credential configuration options.
 * Only one credential option should be provided based on the selected credential mode.
 *
 * @param credentialMode The authentication mode to use
 * @param secretCredentialOpts Client secret authentication options
 * @param certificateCredentialOpts Client certificate authentication options
 * @param interactiveBrowserCredentialOpts Interactive browser authentication options
 * @param usernamePasswordCredentialOpts Username/password authentication options
 */
@Serializable
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CredentialOpts", exact = true)
data class CredentialOpts(
    val credentialMode: CredentialMode,
    val secretCredentialOpts: SecretCredentialOpts? = null,
    val certificateCredentialOpts: CertificateCredentialOpts? = null,
    val interactiveBrowserCredentialOpts: InteractiveBrowserCredentialOpts? = null,
    val usernamePasswordCredentialOpts: UsernamePasswordCredentialOpts? = null,
)

/**
 * Types of credentials available for Azure authentication.
 */
@Serializable
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CredentialType", exact = true)
enum class CredentialType {
    SERVICE,
    USER,
}

/**
 * Types of Azure Key Vault HSM implementations.
 */
@Serializable
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("HSMType", exact = true)
enum class HSMType {
    KEYVAULT,
    MANAGED_HSM,
}

/**
 * HTTP header configuration for Azure Key Vault requests.
 *
 * @param name Header name
 * @param values List of header values
 */
@Serializable
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Header", exact = true)
data class Header(
    val name: String,
    val values: List<String>? = mutableListOf(),
)

/**
 * Configuration for exponential backoff retry strategy.
 *
 * @param maxRetries Maximum number of retry attempts
 * @param baseDelayInMS Base delay in milliseconds before first retry
 * @param maxDelayInMS Maximum delay in milliseconds between retries
 */
@Serializable
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ExponentialBackoffRetryOpts", exact = true)
data class ExponentialBackoffRetryOpts(
    val maxRetries: Int? = 10,
    val baseDelayInMS: Long? = ONE * SECOND,
    val maxDelayInMS: Long? = FIFTEEN * SECOND,
)

/**
 *  Authenticate with client secret.
 *
 * @param clientId Azure application client ID
 * @param clientSecret Client secret for authentication
 */
@Serializable
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("SecretCredentialOpts", exact = true)
data class SecretCredentialOpts(
    val clientId: String,
    val clientSecret: String,
)

/**
 *  Authenticate with a client certificate.
 *
 * @param clientId Azure application client ID
 * @param pemCertificatePath Path to PEM certificate file
 */
@Serializable
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CertificateCredentialOpts", exact = true)
data class CertificateCredentialOpts(
    val clientId: String,
    val pemCertificatePath: String,
)

/**
 * Authenticate interactively in the browser.
 *
 * @param clientId Azure application client ID
 * @param redirectUrl Redirect URL after authentication
 */
@Serializable
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("InteractiveBrowserCredentialOpts", exact = true)
data class InteractiveBrowserCredentialOpts(
    val clientId: String,
    val redirectUrl: String,
)

/**
 * Authenticate with username, password.
 *
 * @param clientId Azure application client ID
 * @param userName Username for authentication
 * @param password Password for authentication
 */
@Serializable
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("UsernamePasswordCredentialOpts", exact = true)
data class UsernamePasswordCredentialOpts(
    val clientId: String,
    val userName: String,
    val password: String,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("AzureKmsProviderConfigType", exact = true)
interface AzureKmsProviderConfigType : KmsProviderConfigBase {
    val applicationId: String
    val keyvaultUrl: String
    val tenantId: String
    val credentialOpts: CredentialOpts
    val hsmType: HSMType
    val headers: List<Header>?
    val exponentialBackoffRetryOpts: ExponentialBackoffRetryOpts?
}

object AzureKmsProviderConfigSerializer : JsonContentPolymorphicSerializer<KmsProviderConfigBase>(KmsProviderConfigBase::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<KmsProviderConfigBase> =
        when {
            "type" in element.jsonObject && element.jsonObject["type"]?.jsonPrimitive?.content == "azure_keyvault" -> AzureKmsProviderConfig.serializer()
            else -> KmsProviderConfig.serializer()
        }
}

@Serializable
@JsExportCompat
@SerialName("azure_keyvault") // mapped onto kmsProviderType
@OptIn(ExperimentalObjCName::class)
@ObjCName("AzureKmsProviderConfig", exact = true)
data class AzureKmsProviderConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val id: String = "azure_keyvault",
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
    override val applicationId: String = "azure-keyvault",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("keyvaultUrl")
    override val keyvaultUrl: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("tenantId")
    override val tenantId: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("credentialOpts")
    override val credentialOpts: CredentialOpts,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("hsmType")
    override val hsmType: HSMType,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    override val headers: List<Header>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("exponentialBackoffRetryOpts")
    override val exponentialBackoffRetryOpts: ExponentialBackoffRetryOpts? = null,
) : AbstractKmsProviderConfig(),
    AzureKmsProviderConfigType {
    @OptIn(InternalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @Transient
//    @SerialName("type")
    override val kmsProviderType: String = PredefinedKmsProviderTypes.AZURE_KEYVAULT.kmsProviderType

    //  get the discriminator as a field (the field name, objectName, is unimportant)
    // this must be a delegated field so there's no backing field, so kxs ignores it
    @OptIn(InternalSerializationApi::class)
    val type: String
        get() = this::class.serializer().descriptor.serialName
}
