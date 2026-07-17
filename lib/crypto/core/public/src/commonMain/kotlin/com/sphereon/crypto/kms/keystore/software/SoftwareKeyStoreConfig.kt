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

package com.sphereon.crypto.kms.keystore.software

import com.sphereon.core.api.Base64UrlSerializer
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.kms.AbstractKeyStoreConfig
import com.sphereon.crypto.core.kms.KeyStoreConfig
import com.sphereon.crypto.core.kms.KeyStoreConfigImpl
import com.sphereon.crypto.core.kms.model.KeyStoreAccessMode
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
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("SoftwareKeyStoreConfig", exact = true)
@JsExportCompat
interface SoftwareKeyStoreConfig : KeyStoreConfig {
    val accessMode: String
    val persist: Boolean
    val path: String?

    /**
     * Root directory under which per-tenant keystore files are derived.
     *
     * When [path] is explicitly configured, tenant-aware resolution still places that file or
     * subpath under `<keystoreRoot>/<tenantId>/...`. When [path] is null/blank, the effective file
     * resolves to `<keystoreRoot>/<tenantId>/<id>.<ext>` so each tenant gets its own keystore file
     * and each provider name (`id`) gets its own file within the tenant directory.
     *
     * Defaults to [TenantKeyStorePathResolver.DEFAULT_KEYSTORE_ROOT] when null/blank.
     */
    val keystoreRoot: String?

    @Serializable(with = Base64UrlSerializer::class)
    val bytes: ByteArray?
    val password: String?
}

object SoftwareKeyStoreConfigSerializer : JsonContentPolymorphicSerializer<KeyStoreConfig>(KeyStoreConfig::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<KeyStoreConfig> {
        val type = element.jsonObject["type"]?.jsonPrimitive?.content
        return when (type) {
            PredefinedKeyStoreTypes.FILE.keyStoreType -> EncryptedFileKeyStoreConfig.serializer()
            PredefinedKeyStoreTypes.PKCS12.keyStoreType -> Pkcs12KeyStoreConfig.serializer()
            PredefinedKeyStoreTypes.JKS.keyStoreType -> JksKeyStoreConfig.serializer()
            PredefinedKeyStoreTypes.APPLE.keyStoreType -> AppleKeyStoreConfig.serializer()
            else -> KeyStoreConfigImpl.serializer()
        }
    }
}

/** Encrypted JSON keystore used by JS/Node and WasmJS persistence backends. */
@JsExportCompat
@Serializable
@SerialName("file")
data class EncryptedFileKeyStoreConfig
    @JvmOverloads
    constructor(
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val id: String = "file",
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val enabled: Boolean = true,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val order: Int = Order.MEDIUM.orderValue,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        @SerialName("defaultConfigValues")
        override val defaultConfigValues: Map<String, String> = emptyMap(),
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val password: String,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val path: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val keystoreRoot: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val bytes: ByteArray? = null,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        @SerialName("accessMode")
        override val accessMode: String = KeyStoreAccessMode.READ_WRITE.accessMode,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        @SerialName("keyVisibility")
        override val keyVisibility: String = KeyVisibility.PRIVATE.keyVisibility,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val persist: Boolean = true,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        @SerialName("overwriteAlias")
        override val overwriteAlias: Boolean = false,
    ) : AbstractSoftwareKeyStoreConfig() {
        init {
            require(path.isNotBlank()) { "Encrypted file keystore path must not be blank" }
            require(password.isNotBlank()) { "Encrypted file keystore password must not be blank" }
        }

        @Transient
        override val keyStoreType: String = PredefinedKeyStoreTypes.FILE.keyStoreType
    }

@Serializable
@JsExportCompat
abstract class AbstractSoftwareKeyStoreConfig :
    AbstractKeyStoreConfig(),
    SoftwareKeyStoreConfig {
    abstract override val id: String
    abstract override val enabled: Boolean
    abstract override val order: Int

    @SerialName("defaultConfigValues")
    abstract override val defaultConfigValues: Map<String, String>
    abstract override val password: String?
    abstract override val path: String?
    abstract override val keystoreRoot: String?
    abstract override val bytes: ByteArray?

    @SerialName("accessMode")
    abstract override val accessMode: String

    @SerialName("keyVisibility")
    abstract override val keyVisibility: String
    abstract override val persist: Boolean

    abstract override val overwriteAlias: Boolean
    abstract override val keyStoreType: String
}

@JsExportCompat
@Serializable
@SerialName("pkcs12")
@OptIn(ExperimentalObjCName::class)
@ObjCName("Pkcs12KeyStoreConfig", exact = true)
data class
Pkcs12KeyStoreConfig
    @JvmOverloads
    constructor(
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val id: String = "pkcs12",
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val enabled: Boolean = true,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val order: Int = Order.MEDIUM.orderValue,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        @SerialName("defaultConfigValues")
        override val defaultConfigValues: Map<String, String> = emptyMap(),
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val password: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val path: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val keystoreRoot: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val bytes: ByteArray? = null,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        @SerialName("accessMode")
        override val accessMode: String = KeyStoreAccessMode.READ_WRITE.accessMode,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        @SerialName("keyVisibility")
        override val keyVisibility: String = KeyVisibility.PRIVATE.keyVisibility,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val persist: Boolean = true,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        @SerialName("overwriteAlias")
        override val overwriteAlias: Boolean = true,
    ) : AbstractSoftwareKeyStoreConfig() {
        @OptIn(InternalSerializationApi::class)
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        @Transient
        override val keyStoreType: String = PredefinedKeyStoreTypes.PKCS12.keyStoreType

        // get the discriminator as a field (the field name, objectName, is unimportant)
        // this must be a delegated field so there's no backing field, so kxs ignores it
        @OptIn(InternalSerializationApi::class)
        val type: String
            get() = this::class.serializer().descriptor.serialName

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as Pkcs12KeyStoreConfig

            if (enabled != other.enabled) {
                return false
            }
            if (order != other.order) {
                return false
            }
            if (persist != other.persist) {
                return false
            }
            if (id != other.id) {
                return false
            }
            if (defaultConfigValues != other.defaultConfigValues) {
                return false
            }
            if (password != other.password) {
                return false
            }
            if (path != other.path) {
                return false
            }
            if (keystoreRoot != other.keystoreRoot) {
                return false
            }
            if (!bytes.contentEquals(other.bytes)) {
                return false
            }
            if (accessMode != other.accessMode) {
                return false
            }
            if (keyVisibility != other.keyVisibility) {
                return false
            }
            if (keyStoreType != other.keyStoreType) {
                return false
            }
            if (overwriteAlias != other.overwriteAlias) {
                return false
            }
            if (type != other.type) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = enabled.hashCode()
            result = 31 * result + order
            result = 31 * result + persist.hashCode()
            result = 31 * result + id.hashCode()
            result = 31 * result + defaultConfigValues.hashCode()
            result = 31 * result + password.hashCode()
            result = 31 * result + (path?.hashCode() ?: 0)
            result = 31 * result + (keystoreRoot?.hashCode() ?: 0)
            result = 31 * result + (bytes?.contentHashCode() ?: 0)
            result = 31 * result + accessMode.hashCode()
            result = 31 * result + keyVisibility.hashCode()
            result = 31 * result + keyStoreType.hashCode()
            result = 31 * result + type.hashCode()
            result = 31 * result + overwriteAlias.hashCode()
            return result
        }
    }

@JsExportCompat
@Serializable
@SerialName("jks")
@OptIn(ExperimentalObjCName::class)
@ObjCName("JksKeyStoreConfig", exact = true)
data class
JksKeyStoreConfig
    @JvmOverloads
    constructor(
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val id: String = "jks",
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val enabled: Boolean = true,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val order: Int = Order.MEDIUM.orderValue,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        @SerialName("defaultConfigValues")
        override val defaultConfigValues: Map<String, String> = emptyMap(),
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val password: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val path: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val keystoreRoot: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val bytes: ByteArray? = null,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        @SerialName("accessMode")
        override val accessMode: String = KeyStoreAccessMode.READ_WRITE.accessMode,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        @SerialName("keyVisibility")
        override val keyVisibility: String = KeyVisibility.PRIVATE.keyVisibility,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val persist: Boolean = true,
        @SerialName("overwriteAlias")
        override val overwriteAlias: Boolean = false,
    ) : AbstractSoftwareKeyStoreConfig() {
        @OptIn(InternalSerializationApi::class)
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        @Transient
        override val keyStoreType: String = PredefinedKeyStoreTypes.JKS.keyStoreType

        // get the discriminator as a field (the field name, objectName, is unimportant)
        // this must be a delegated field so there's no backing field, so kxs ignores it
        @OptIn(InternalSerializationApi::class)
        val type: String
            get() = this::class.serializer().descriptor.serialName

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as JksKeyStoreConfig

            if (enabled != other.enabled) {
                return false
            }
            if (order != other.order) {
                return false
            }
            if (persist != other.persist) {
                return false
            }
            if (id != other.id) {
                return false
            }
            if (defaultConfigValues != other.defaultConfigValues) {
                return false
            }
            if (password != other.password) {
                return false
            }
            if (path != other.path) {
                return false
            }
            if (keystoreRoot != other.keystoreRoot) {
                return false
            }
            if (!bytes.contentEquals(other.bytes)) {
                return false
            }
            if (accessMode != other.accessMode) {
                return false
            }
            if (keyVisibility != other.keyVisibility) {
                return false
            }
            if (keyStoreType != other.keyStoreType) {
                return false
            }
            if (overwriteAlias != other.overwriteAlias) {
                return false
            }
            if (type != other.type) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = enabled.hashCode()
            result = 31 * result + order
            result = 31 * result + persist.hashCode()
            result = 31 * result + id.hashCode()
            result = 31 * result + defaultConfigValues.hashCode()
            result = 31 * result + password.hashCode()
            result = 31 * result + (path?.hashCode() ?: 0)
            result = 31 * result + (keystoreRoot?.hashCode() ?: 0)
            result = 31 * result + (bytes?.contentHashCode() ?: 0)
            result = 31 * result + accessMode.hashCode()
            result = 31 * result + keyVisibility.hashCode()
            result = 31 * result + keyStoreType.hashCode()
            result = 31 * result + type.hashCode()
            result = 31 * result + overwriteAlias.hashCode()
            return result
        }
    }

@JsExportCompat
@Serializable
@SerialName("apple")
@OptIn(ExperimentalObjCName::class)
@ObjCName("AppleKeyStoreConfig", exact = true)
data class
AppleKeyStoreConfig
    @JvmOverloads
    constructor(
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val id: String = "apple",
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val enabled: Boolean = true,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val order: Int = Order.MEDIUM.orderValue,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        @SerialName("defaultConfigValues")
        override val defaultConfigValues: Map<String, String> = emptyMap(),
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val password: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val path: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val keystoreRoot: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val bytes: ByteArray? = null,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        @SerialName("accessMode")
        override val accessMode: String = KeyStoreAccessMode.READ_WRITE.accessMode,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        @SerialName("keyVisibility")
        override val keyVisibility: String = KeyVisibility.PUBLIC.keyVisibility,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val persist: Boolean = true,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        @SerialName("overwriteAlias")
        override val overwriteAlias: Boolean = true,
    ) : AbstractSoftwareKeyStoreConfig() {
        @OptIn(InternalSerializationApi::class)
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        @Transient
        override val keyStoreType: String = PredefinedKeyStoreTypes.APPLE.keyStoreType

        @OptIn(InternalSerializationApi::class)
        val type: String
            get() = this::class.serializer().descriptor.serialName
    }
