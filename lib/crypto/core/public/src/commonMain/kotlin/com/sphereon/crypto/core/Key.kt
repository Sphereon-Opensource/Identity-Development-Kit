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

package com.sphereon.crypto.core

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.annotations.Beta
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkDTOType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.x509.Certificate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic
import kotlin.native.ObjCName

/**
 * Explicit identity for keys to use in caches and maps.
 *
 * Instead of relying on KeyInfo.equals()/hashCode() which may not include all relevant fields,
 * use KeyIdentity for explicit key identity comparison. This is particularly useful for:
 * - Cache keys where you want deterministic behavior
 * - Map keys where identity semantics matter
 * - Comparing keys across different representations (JWK, COSE, etc.)
 *
 * Usage:
 * ```kotlin
 * val cache = mutableMapOf<KeyIdentity, SomeValue>()
 * cache[keyInfo.identity()] = value
 * ```
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyIdentity", exact = true)
@Serializable
data class KeyIdentity(
    val kid: String? = null,
    val providerId: String? = null,
    val alias: String? = null,
    val keyType: KeyTypeMapping? = null
) {
    /**
     * Check if this identity has any identifying information.
     */
    fun hasIdentity(): Boolean = kid != null || alias != null

    companion object {
        /**
         * Create identity from a key info.
         */
        @JsStatic
        fun <KT : KeyType> fromKeyInfo(keyInfo: KeyInfoType<KT>): KeyIdentity {
            return KeyIdentity(
                kid = keyInfo.kid ?: keyInfo.key?.getKeyId(false),
                providerId = keyInfo.providerId,
                alias = keyInfo.alias,
                keyType = keyInfo.keyType ?: keyInfo.key?.getKeyType()
            )
        }
    }
}

/**
 * Represents an interface for a cryptographic key.
 */
@JsExportCompat
@Serializable(with = PolymorphicSerializer::class)
interface KeyDTOType {

    /**
     * Represents the key type for the implementation of the Key interface.
     *
     * This variable holds the type of the key (kty) as specified in COSE (CBOR Object Signing and Encryption)
     * and JWA (JSON Web Algorithms) standards.
     *
     * It is used to identify the key type for cryptographic operations, providing compatibility
     * between different security frameworks and ensuring that the key can be correctly interpreted
     * and utilized across various implementations.
     */
    val kty: Any

    /**
     * Unique identifier for the cryptographic key.
     *
     * This value is often used to differentiate between multiple keys
     * within a set, allowing for key management and retrieval based on this identifier.
     *
     * It can be null if the key identifier is not provided or not relevant
     * to the context in which the key is used.
     */
    val kid: Any?

    /**
     * Represents the algorithm associated with the key.
     *
     * Holds the algorithm identifier, which defines the cryptographic operations
     * that can be performed with the key. The value may be any type, including
     * but not limited to strings, cbor objects, depending on the
     * context in which it is used.
     */
    val alg: Any?

    /**
     * Represents the key operations applicable to the key.
     *
     * The content of this property describes what operations a key is capable of
     * performing, such as encryption, decryption, signing, and verification among others.
     * This information is used to enforce what actions can and cannot be performed
     * using the specified key, adhering to the security constraints and intended
     * usage of the key.
     */
    val key_ops: Any?

    /**
     * Represents the `crv` (Curve) parameter in a cryptographic key.
     *
     * This parameter is typically used in elliptic curve cryptography to define the specific curve
     * on which the cryptographic operations will be performed. It may be `null` if not applicable
     * or not specified.
     */
    val crv: Any?

    /**
     * Represents the 'x' coordinate parameter for an elliptic curve key or a similar cryptographic key component.
     *
     * This value is typically used in the context of keys that rely on elliptic curve algorithms,
     * and it is essential for cryptographic operations involving such keys.
     *
     * The representation is flexible and can accommodate various types of data required by different
     * cryptographic standards.
     */
    val x: Any?

    /**
     * Represents the y-coordinate of an elliptic curve point in cryptographic operations.
     *
     * Typically part of the public key for elliptic curve cryptography (ECC).
     *
     * The value can be null, indicating that the coordinate is not set or not applicable.
     */
    val y: Any?

    /**
     * Represents the private or secret part of a key in cryptographic operations.
     *
     * This property allows the inclusion of private key material in asymmetric keys,
     * which is crucial for decryption, signing, and other cryptographic operations that
     * require the private or secret part of the key.
     */
    val d: Any?

    /*  val e: Any?

      val p: Any?

      val q: Any?

      val dP: Any?
      val dQ: Any?
      val qInv: Any?*/

    /**
     * Represents additional information or data associated with the key.
     * This property can hold various types of supplementary data that may be needed for
     * certain cryptographic operations or key management tasks.
     *
     * Note: This uses `Any?` for flexibility in storing platform-specific data.
     * Consider using a more specific type or sealed class hierarchy in future versions.
     */
    @Transient
    val additional: Any?
}

/**
 * Represents an interface for a cryptographic key.
 */
@JsExportCompat
@Serializable(with = PolymorphicSerializer::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyType", exact = true)
interface KeyType : KeyDTOType {

    /**
     * Maps key types to their appropriate values for COSE/JWA implementations.
     *
     * @return A KeyTypeMapping object containing the mappings for key types.
     */
    fun getKeyType(): KeyTypeMapping

    /**
     * Retrieves the algorithm mapping for the key.
     *
     * @return the corresponding AlgorithmMapping instance, or null if not available
     */
    fun getSignatureAlgorithm(): SignatureAlgorithm?

    /**
     * Retrieves an array of key operations mappings for the current key.
     *
     * @return An array of KeyOperationsMapping objects representing the key operations,
     *         or null if there are no operations available.
     */
    fun getKeyOperations(): Array<KeyOperations>?

    /**
     * Retrieves the X.509 certificate chain (x5c) from the key.
     *
     * @return An array of strings representing the x5c certificate chain, or null if not available.
     */
    fun getX509CertificateChain(): Array<String>?

    /**
     * Retrieves the leaf X.509 certificate as DTO This function assumes the leaf certificate is the first one in the chain (which is common practice.)
     *
     * @return  the leaf X.509 certificate as DTO, or null if the certificate is not present.
     */
    fun getX509Certificate(): Certificate?

    fun getX509CertificatePem(): String?

    fun getKeyId(generate: Boolean = false): String?

    fun getXAsString(): String?
    fun getYAsString(): String?
    fun getDAsString(): String?

    fun toPublicKey(): KeyType

    fun publicKeyPem(): String
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("IdentifierAliasLookup", exact = true)
@Serializable
data class IdentifierAliasLookup(
    override val alias: String,
    override val providerId: String? = null,
    override val noCache: Boolean = false
) : IdentifierLookupType {
    @Transient
    override val kid: String? = null

    @Transient
    override val opts: Map<String, String>? = null
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("IdentifierKidLookup", exact = true)
@Serializable
data class IdentifierKidLookup(
    override val kid: String,
    override val providerId: String? = null,
    override val noCache: Boolean = false
) : IdentifierLookupType {
    @Transient
    override val alias: String? = null

    @Transient
    override val opts: Map<String, String>? = null
}


@OptIn(ExperimentalObjCName::class)
@ObjCName("IdentifierLookupType", exact = true)
interface IdentifierLookupType {
    val noCache: Boolean

    /**
     * A nullable String variable representing the name or identifier of a kid.
     *
     * Can be `null` when the kid's name or identifier is not provided.
     */
    val kid: String?

    /**
     * A map containing configuration options.
     *
     * The `opts` variable is a nullable map where both the keys and values can be of any type.
     * This map is used to store various configuration parameters that can be accessed and utilized
     * throughout the application. A null value indicates that there are no configuration options specified.
     */
    val opts: Map<String, String>?

    /**
     * Represents the Key Management System (KMS) provider or resolver identifier associated with the key.
     * This property might be used to specify which KMS should be utilized for operations involving the key.
     */
    val providerId: String?

    /**
     * A reference to a Key Management Service (KMS) key.
     *
     * This variable holds an optional string that serves as an identifier or
     * link to a key stored in a Key Management Service. It is used to refer
     * to a specific key within the KMS without directly storing the key's value
     * in the system.
     */
    val alias: String?
}

/**
 * Represents the interface for key information.
 *
 * Provides a structure to hold key-related metadata and configuration details
 * necessary for cryptographic operations.
 *
 * @param KT The specific type of key implementing the Key interface.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyInfoType", exact = true)
sealed interface KeyInfoType<out KT : KeyType> : IdentifierLookupType {

    /**
     * Represents a cryptographic key that can be used for various security operations such as encryption, decryption, signing, and verification.
     *
     * This key may be optional and can be null. The actual implementation of the key is determined by the KeyType.
     */
    /*val jwk: JWK,*/
    val key: KT?

    /**
     * Indicates the visibility status of a cryptographic key.
     *
     * This property can take a value from the `KeyVisibility` enum, representing
     * whether the key is public or private. It is used to determine the access level
     * and usability of the key within cryptographic operations.
     *
     * The possible values are:
     * - `PUBLIC`: Indicates that the key is publicly accessible.
     * - `PRIVATE`: Indicates that the key is privately held and should be restricted in its usage.
     *
     * Can be `null` if the visibility status is not specified. Public will be assumed then
     */
    val keyVisibility: KeyVisibility?

    /**
     * Represents the type of the cryptographic key.
     *
     * It associates the key with a specific cryptographic algorithm used for operations
     * such as signing and encryption. The `KeyType` can define key types like RSA, EC (Elliptic Curve),
     * and OKP (Octet Key Pair) which are used to specify the algorithmic properties and
     * ensure interoperability between different cryptographic standards.
     *
     * This variable determines how the key can be used and identifies the mapping
     * between COSE (CBOR Object Signing and Encryption) key types and JWA (JSON Web Algorithms)
     * key types.
     */
    val keyType: KeyTypeMapping?

    val keyEncoding: KeyEncoding?

    /**
     * Converts and returns the current key information to a public key information structure.
     *
     * @return An instance of KeyInfo containing the public key information derived from the current key.
     */
    fun toPublicKeyInfo(): KeyInfoType<KT>

    /**
     * Get the explicit identity of this key for use in caches and maps.
     *
     * Use this instead of relying on equals()/hashCode() for deterministic identity comparison.
     *
     * @return KeyIdentity containing the identifying information for this key
     */
    fun identity(): KeyIdentity = KeyIdentity.fromKeyInfo(this)

    /**
     * Represents the algorithm used for generating and verifying digital signatures.
     * This variable may hold a specific algorithm or be null if an algorithm is not set.
     * Common algorithms include RSA, DSA, and ECDSA.
     */
    val signatureAlgorithm: SignatureAlgorithm?

    val x5c: Array<String>?
}

/**
 * Represents a resolved cryptographic key information interface.
 *
 * This interface guarantees that the key is present and resolved, providing concrete access to the key.
 *
 * @param KT Generic type that extends Key, representing the cryptographic key type.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolvedKeyInfoType", exact = true)
sealed interface ResolvedKeyInfoType<out KT : KeyType> : KeyInfoType<KT> {
    /**
     * A unique identifier that is guaranteed to be present (resolved) for the object.
     */
// Same as the above, but now wit a key guaranteed to be present (resolved)
    override val key: KT

//    val x509VerificationResult: X509VerificationResult<KT>?

    fun toResolvedPublicKeyInfo(): ResolvedKeyInfoType<KT>
}

/**
 * Serializer for [ResolvedKeyInfoType] that delegates to the concrete [ResolvedKeyInfo] class
 * with polymorphic key serialization.
 */
object ResolvedKeyInfoSerializer : KSerializer<ResolvedKeyInfoType<*>> {
    private val delegate: KSerializer<ResolvedKeyInfo<KeyType>> =
        ResolvedKeyInfo.serializer(PolymorphicSerializer(KeyType::class))

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: ResolvedKeyInfoType<*>) {
        // Convert to concrete ResolvedKeyInfo if needed
        val concreteValue = when (value) {
            is ResolvedKeyInfo<*> -> value
            else -> ResolvedKeyInfo.fromDTO(value)
        }
        @Suppress("UNCHECKED_CAST")
        encoder.encodeSerializableValue(delegate, concreteValue as ResolvedKeyInfo<KeyType>)
    }

    override fun deserialize(decoder: Decoder): ResolvedKeyInfoType<*> =
        decoder.decodeSerializableValue(delegate)
}

/**
 * Represents a managed cryptographic key information interface.
 *
 * This interface guarantees that the key is present and resolved, part of a KMS providing concrete access to the key.
 *
 * @param KT Generic type that extends Key, representing the cryptographic key type.
 */
@JsExportCompat
@Serializable(with = ManagedKeyInfoSerializer::class)
@SerialName("ManagedKeyInfoType")
@OptIn(ExperimentalObjCName::class)
@ObjCName("ManagedKeyInfoType", exact = true)
interface ManagedKeyInfoType<out KT : KeyType> : ResolvedKeyInfoType<KT> {
    override val alias: String
    override val providerId: String
    fun toManagedPublicKeyInfo(): ManagedKeyInfoType<KT>
}

/**
 * Wrapper for SerialDescriptor that allows overriding the serial name.
 * Used to avoid polymorphic naming conflicts in sealed hierarchies.
 */
@OptIn(ExperimentalSerializationApi::class, SealedSerializationApi::class)
private class RenamedSerialDescriptor(
    private val original: SerialDescriptor,
    override val serialName: String
) : SerialDescriptor {
    override val elementsCount: Int get() = original.elementsCount
    override val kind get() = original.kind
    override val annotations: List<Annotation> get() = original.annotations
    override val isNullable: Boolean get() = original.isNullable
    override fun getElementName(index: Int): String = original.getElementName(index)
    override fun getElementIndex(name: String): Int = original.getElementIndex(name)
    override fun getElementAnnotations(index: Int): List<Annotation> = original.getElementAnnotations(index)
    override fun getElementDescriptor(index: Int): SerialDescriptor = original.getElementDescriptor(index)
    override fun isElementOptional(index: Int): Boolean = original.isElementOptional(index)
}

/**
 * A little serializer that simply delegates
 * to the ManagedKeyInfoImpl.serializer() generated for your data class.
 *
 * Uses a wrapper descriptor with serial name "ManagedKeyInfoType" to avoid
 * polymorphic naming conflicts with ManagedKeyInfo in the ResolvedKeyInfoType sealed hierarchy.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class ManagedKeyInfoSerializer<KT : KeyType>(
    private val keySerializer: KSerializer<KT>
) : KSerializer<ManagedKeyInfoType<KT>> {
    private val delegate: KSerializer<ManagedKeyInfo<KT>> =
        ManagedKeyInfo.serializer(keySerializer)

    // Wrap the descriptor with "ManagedKeyInfoType" serial name to avoid conflict with ManagedKeyInfo
    override val descriptor: SerialDescriptor = RenamedSerialDescriptor(delegate.descriptor, "ManagedKeyInfoType")

    override fun serialize(encoder: Encoder, value: ManagedKeyInfoType<KT>) {
        // We know at runtime it is a ManagedKeyInfoImpl
        @Suppress("UNCHECKED_CAST")
        encoder.encodeSerializableValue(delegate, value as ManagedKeyInfo<KT>)
    }

    override fun deserialize(decoder: Decoder): ManagedKeyInfoType<KT> =
        decoder.decodeSerializableValue(delegate)
}

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyInfo", exact = true)
data class KeyInfo<out KT : KeyType>(
    override val kid: String? = null, /*val jwk: JWK,*/
    override val key: KT? = null,
    @Transient // Runtime configuration options, not serialized
    override val opts: Map<String, String>? = null,
    override val keyVisibility: KeyVisibility? = KeyVisibility.PUBLIC,
    override val signatureAlgorithm: SignatureAlgorithm? = null,
    override val x5c: Array<String>? = key?.getX509CertificateChain(),
    override val alias: String? = null,
    override val providerId: String? = null,
    override val keyType: KeyTypeMapping? = null,
    override val keyEncoding: KeyEncoding? = null,
    override val noCache: Boolean = false,
) : KeyInfoType<KT> {

    override fun hashCode(): Int {
        var result = kid?.hashCode() ?: 0
        result = 31 * result + (key?.hashCode() ?: 0)
        result = 31 * result + (opts?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "KeyInfo(alias=$alias, kid=$kid, key=$key, opts=$opts)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyInfo<*>) return false

        if (kid != other.kid) return false
        if (key != other.key) return false
        if (opts != other.opts) return false

        return true
    }

    fun resolve(resolver: ((keyInfo: KeyInfoType<KT>) -> ResolvedKeyInfoType<@UnsafeVariance KT>)) = resolver(this)

    @Suppress("UNCHECKED_CAST")
    override fun toPublicKeyInfo(): KeyInfoType<KT> = this.copy(key = key?.toPublicKey() as KT?, keyVisibility = KeyVisibility.PUBLIC)

    companion object {
        @JsStatic
        fun <KT : KeyType> fromDTO(dto: KeyInfoType<KT>) =
            with(dto) {
                KeyInfo(
                    kid = key?.getKeyId(false) ?: kid,
                    key = key,
                    opts = opts,
                    x5c = key?.getX509CertificateChain() ?: x5c,
                    providerId = providerId,
                    alias = alias,
                    keyVisibility = keyVisibility ?: KeyVisibility.PUBLIC,
                    signatureAlgorithm = signatureAlgorithm ?: key?.getSignatureAlgorithm(),
                    keyType = key?.getKeyType() ?: dto.keyType,
                )
            }
    }
}

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyEncoding", exact = true)
enum class KeyEncoding {
    COSE,
    JOSE,
    PLATFORM
}

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolvedKeyInfo", exact = true)
data class ResolvedKeyInfo<KT : KeyType>(
    override val kid: String? = null, /*val jwk: JWK,*/
    override val key: KT,
    @Transient // Runtime configuration options, not serialized
    override val opts: Map<String, String>? = null,
    override val keyVisibility: KeyVisibility? = KeyVisibility.PUBLIC,
    override val signatureAlgorithm: SignatureAlgorithm? = null,
    override val alias: String? = null,
    override val x5c: Array<String>? = null,
//    override val x509VerificationResult: X509VerificationResult<KT>? = null,
    override val providerId: String? = null,
    override val keyType: KeyTypeMapping? = null,
    override val keyEncoding: KeyEncoding? = null,
    override val noCache: Boolean = false,
) : ResolvedKeyInfoType<KT> {

    fun toKeyInfo() =
        KeyInfo(
            kid = kid ?: key.getKeyId(false),
            key = key,
            opts = opts,
            x5c = x5c ?: key.getX509CertificateChain(),
            providerId = providerId,
            alias = alias,
            keyVisibility = keyVisibility ?: if (key.d !== null) KeyVisibility.PRIVATE else KeyVisibility.PUBLIC,
            signatureAlgorithm = signatureAlgorithm ?: key.getSignatureAlgorithm(),
            keyType = keyType ?: key.getKeyType(),
            keyEncoding = keyEncoding ?: if (key is JwkDTOType || key is JwkType) KeyEncoding.JOSE else if (key is CoseKeyType) KeyEncoding.COSE else null
        )

    @Suppress("UNCHECKED_CAST")
    override fun toResolvedPublicKeyInfo(): ResolvedKeyInfo<KT> = this.copy(key = key.toPublicKey() as KT, keyVisibility = KeyVisibility.PUBLIC)

    override fun toPublicKeyInfo() = toKeyInfo().toPublicKeyInfo()

    companion object {
        @JsStatic
        fun <KT : KeyType> fromDTO(dto: ResolvedKeyInfoType<KT>) =
            with(dto) {
                ResolvedKeyInfo(
                    kid = key.getKeyId(false) ?: kid,
                    key = key,
                    opts = opts,
                    x5c = key.getX509CertificateChain() ?: x5c,
                    providerId = providerId,
                    alias = alias,
                    keyVisibility = keyVisibility ?: KeyVisibility.PUBLIC,
                    signatureAlgorithm = signatureAlgorithm ?: key.getSignatureAlgorithm(),
                    keyType = keyType,
                    keyEncoding = keyEncoding ?: if (key is JwkDTOType || key is JwkType) KeyEncoding.JOSE else if (key is CoseKeyType) KeyEncoding.COSE else null
                )
            }

        /**
         * Safely creates a ResolvedKeyInfo from a KeyInfoType.
         * @return IdkResult containing the resolved key info, or an error if no key is available.
         */
        @JsStatic
        @Beta(message = "Safe conversion API - may have minor changes in future versions")
        fun <KT : KeyType> tryFromKeyInfo(dto: KeyInfoType<*>, key: KT? = null): IdkResult<ResolvedKeyInfo<KT>, IdkError> =
            with(dto) {
                @Suppress("UNCHECKED_CAST")
                val resolvedKey = key ?: dto.key?.let { it as KT }
                    ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "No key passed in and key info also had no key"))
                Ok(ResolvedKeyInfo(
                    kid = kid ?: resolvedKey.getKeyId(false),
                    key = resolvedKey,
                    opts = opts,
                    x5c = x5c ?: resolvedKey.getX509CertificateChain(),
                    providerId = providerId,
                    alias = alias,
                    keyVisibility = keyVisibility ?: if (resolvedKey.d !== null) KeyVisibility.PRIVATE else KeyVisibility.PUBLIC,
                    signatureAlgorithm = signatureAlgorithm ?: resolvedKey.getSignatureAlgorithm(),
                    keyType = dto.keyType ?: resolvedKey.getKeyType(),
                    keyEncoding = keyEncoding ?: if (resolvedKey is JwkDTOType || resolvedKey is JwkType) KeyEncoding.JOSE else if (resolvedKey is CoseKeyType) KeyEncoding.COSE else null
                ))
            }

        /**
         * Creates a ResolvedKeyInfo from a KeyInfoType.
         * @throws IllegalArgumentException if no key is available.
         */
        @JsStatic
        fun <KT : KeyType> fromKeyInfo(dto: KeyInfoType<*>, key: KT? = null): ResolvedKeyInfo<KT> =
            tryFromKeyInfo(dto, key).getOrElse {
                throw IllegalArgumentException(it.message.defaultMessage)
            }

        @JsStatic
        fun <KT : KeyType> fromKey(key: KT): ResolvedKeyInfoType<KT> {
            return ResolvedKeyInfo(
                kid = key.getKeyId(false),
                key = key,
                x5c = key.getX509CertificateChain(),
                keyVisibility = if (key.d !== null) KeyVisibility.PRIVATE else KeyVisibility.PUBLIC,
                signatureAlgorithm = key.getSignatureAlgorithm(),
                keyType = key.getKeyType(),
                keyEncoding = if (key is JwkDTOType || key is JwkType) KeyEncoding.JOSE else if (key is CoseKeyType) KeyEncoding.COSE else null
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ResolvedKeyInfoType<*>

        if (kid != other.kid) return false
        if (key != other.key) return false
        if (opts != other.opts) return false
        if (keyVisibility != other.keyVisibility) return false
        if (signatureAlgorithm != other.signatureAlgorithm) return false
        if (alias != other.alias) return false
        if (x5c != null) {
            if (other.x5c == null || !x5c.contentEquals(other.x5c)) return false
        } else if (other.x5c != null) return false
        if (providerId != other.providerId) return false
        if (keyType != other.keyType) return false
        if (keyEncoding != other.keyEncoding) return false

        return true
    }

    override fun hashCode(): Int {
        var result = kid?.hashCode() ?: 0
        result = 31 * result + key.hashCode()
        result = 31 * result + (opts?.hashCode() ?: 0)
        result = 31 * result + (keyVisibility?.hashCode() ?: 0)
        result = 31 * result + (signatureAlgorithm?.hashCode() ?: 0)
        result = 31 * result + (alias?.hashCode() ?: 0)
        result = 31 * result + (x5c?.contentHashCode() ?: 0)
        result = 31 * result + (providerId?.hashCode() ?: 0)
        result = 31 * result + (keyType?.hashCode() ?: 0)
        result = 31 * result + (keyEncoding?.hashCode() ?: 0)
        return result
    }
}

/**
 * Represents a key that is managed by a KMS (Key Management System) provider.
 *
 * ManagedKeyInfo wraps a [ResolvedKeyInfoType] with additional provider management metadata,
 * specifically the [alias] and [providerId] that identify where the key is stored.
 *
 * This class uses delegation to [ResolvedKeyInfoType] for all key-related properties,
 * meaning it provides all the guarantees of a resolved key (non-null key) plus
 * the management metadata required for KMS operations.
 *
 * Example usage:
 * ```kotlin
 * // Create from existing key info
 * val managed = ManagedKeyInfo.fromKeyInfo(keyInfo)
 *
 * // Or build with explicit provider info
 * val managed = ManagedKeyInfo.build(
 *     keyInfo = resolvedKey,
 *     alias = "my-signing-key",
 *     providerId = "aws-kms"
 * )
 *
 * // Access key properties (delegated to resolvedKeyInfo)
 * val key = managed.key
 * val algorithm = managed.signatureAlgorithm
 * ```
 *
 * @param KT The type of key, must extend [KeyType]
 * @property alias The unique alias identifying this key within the provider
 * @property providerId The identifier of the KMS provider managing this key
 * @property resolvedKeyInfo The underlying resolved key information (delegated)
 */
@Serializable
@SerialName("ManagedKeyInfo")
@OptIn(ExperimentalObjCName::class)
@ObjCName("ManagedKeyInfo", exact = true)
data class ManagedKeyInfo<out KT : KeyType>(
    override val alias: String,
    override val providerId: String,
    private val resolvedKeyInfo: ResolvedKeyInfoType<KT>
) : ManagedKeyInfoType<KT>, ResolvedKeyInfoType<KT> by resolvedKeyInfo {
    override val key = resolvedKeyInfo.key
    override fun toResolvedPublicKeyInfo() = resolvedKeyInfo.toResolvedPublicKeyInfo()
    override fun toManagedPublicKeyInfo(): ManagedKeyInfoType<KT> =
        this.copy(resolvedKeyInfo = resolvedKeyInfo.toResolvedPublicKeyInfo(), alias = alias, providerId = providerId)

    override fun toPublicKeyInfo() = resolvedKeyInfo.toPublicKeyInfo()

    companion object {
        /**
         * Creates a ManagedKeyInfo from an existing KeyInfoType.
         *
         * Requires that the keyInfo has a non-null key, alias, and providerId.
         *
         * @param T The key type
         * @param keyInfo The source key information with all required fields populated
         * @return A new ManagedKeyInfo wrapping the key
         * @throws IllegalArgumentException if key, alias, or providerId is null
         */
        @JsStatic
        fun <T : KeyType> fromKeyInfo(keyInfo: KeyInfoType<T>): ManagedKeyInfo<T> {
            require(keyInfo.key != null) { "KeyInfo to ManagedKeyInfo must have a key" }
            require(keyInfo.alias != null) { "KeyInfo to ManagedKeyInfo must have an alias" }
            require(keyInfo.providerId != null) { "KeyInfo to managedKeyInfo must have a providerId" }
            val resolvedKeyInfo = ResolvedKeyInfo.fromKeyInfo<T>(keyInfo)
            return ManagedKeyInfo(
                alias = keyInfo.alias ?: resolvedKeyInfo.alias!!,
                providerId = keyInfo.providerId ?: resolvedKeyInfo.providerId!!,
                resolvedKeyInfo = resolvedKeyInfo
            )

        }

        /**
         * Builds a ManagedKeyInfo with explicit alias and providerId, allowing overrides.
         *
         * Use this when you need to assign a key to a specific provider/alias
         * combination that differs from the source keyInfo.
         *
         * @param T The key type
         * @param keyInfo The source key information (must have a key)
         * @param alias The alias to use (defaults to keyInfo.alias if not provided)
         * @param providerId The provider ID to use (defaults to keyInfo.providerId if not provided)
         * @return A new ManagedKeyInfo with the specified alias and provider
         * @throws IllegalArgumentException if alias or providerId is null
         */
        @JsStatic
        fun <T : KeyType> build(keyInfo: KeyInfoType<T>, alias: String? = keyInfo.alias, providerId: String? = keyInfo.providerId): ManagedKeyInfo<T> {
            requireNotNull(alias) { "Alias must be provided" }
            requireNotNull(providerId) { "ProviderId must be provided" }
            return fromKeyInfo(KeyInfo.fromDTO(keyInfo).copy(alias = alias, providerId = providerId))
        }
    }
}
