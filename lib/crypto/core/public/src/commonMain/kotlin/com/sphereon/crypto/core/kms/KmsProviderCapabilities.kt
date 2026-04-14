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

package com.sphereon.crypto.core.kms

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.generic.CryptoAlg
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.resolution.IIdentifierMethod
import kotlinx.serialization.Serializable
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Enumeration of all possible KMS provider operations.
 * Used for capability introspection to determine what operations a provider supports.
 */
@JsExportCompat
enum class KmsProviderOperation {
    // Key Management Operations
    IMPORT_KEY,
    EXPORT_KEY,
    GENERATE_KEY,
    DELETE_KEY,

    // Cryptographic Operations - Signature
    SIGN,
    VERIFY,

    // Cryptographic Operations - Encryption (for JWE, data encryption)
    ENCRYPT,
    DECRYPT,

    // Key Wrapping/Unwrapping (for JWE key encryption)
    WRAP_KEY,
    UNWRAP_KEY,

    // Key Agreement (for ECDH-ES)
    KEY_AGREEMENT,

    // Certificate Operations
    GENERATE_CERTIFICATE,
    IMPORT_CERTIFICATE,
    X509_CHAIN_VALIDATION,

    // MAC Operations (HMAC — AWS GenerateMac/VerifyMac pattern)
    GENERATE_MAC,
    VERIFY_MAC,

    // Advanced Capabilities
    HARDWARE_BACKED,
    ATTESTATION,
}

/**
 * Enumeration of key storage types supported by KMS providers.
 * Indicates where and how keys are stored.
 */
@JsExportCompat
enum class KeyStorageType {
    /** No storage - ephemeral keys only, lost after session */
    NONE,

    /** Session-scoped storage - keys persist for the session duration */
    EPHEMERAL,

    /** Persistent storage - keys saved to disk/database */
    PERSISTENT,

    /** Hardware-backed storage - keys stored in HSM, Secure Enclave, TPM, etc. */
    HARDWARE,
}

/**
 * Represents the capability of a KMS provider to perform a specific operation.
 *
 * @property operation The operation this capability represents
 * @property supported Whether the operation is supported by the provider
 * @property signatureAlgorithms Signature algorithms supported (for SIGN/VERIFY operations)
 * @property contentEncryptionAlgorithms Content encryption algorithms supported (for ENCRYPT/DECRYPT operations)
 * @property keyWrapAlgorithms Key wrap algorithms supported (for WRAP_KEY/UNWRAP_KEY operations)
 * @property keyAgreementAlgorithms Key agreement algorithms supported (for KEY_AGREEMENT operations)
 * @property notes Additional context or limitations for this capability
 */
@JsExportCompat
data class
OperationCapability
    @JvmOverloads
    constructor(
        val operation: KmsProviderOperation,
        val supported: Boolean,
        val signatureAlgorithms: Array<SignatureAlgorithm> = emptyArray(),
        val contentEncryptionAlgorithms: Array<ContentEncryptionAlgorithm> = emptyArray(),
        val keyWrapAlgorithms: Array<KeyWrapAlgorithm> = emptyArray(),
        val keyAgreementAlgorithms: Array<KeyAgreementAlgorithm> = emptyArray(),
        val notes: String? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as OperationCapability

            if (operation != other.operation) {
                return false
            }
            if (supported != other.supported) {
                return false
            }
            if (!signatureAlgorithms.contentEquals(other.signatureAlgorithms)) {
                return false
            }
            if (!contentEncryptionAlgorithms.contentEquals(other.contentEncryptionAlgorithms)) {
                return false
            }
            if (!keyWrapAlgorithms.contentEquals(other.keyWrapAlgorithms)) {
                return false
            }
            if (!keyAgreementAlgorithms.contentEquals(other.keyAgreementAlgorithms)) {
                return false
            }
            if (notes != other.notes) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = operation.hashCode()
            result = 31 * result + supported.hashCode()
            result = 31 * result + signatureAlgorithms.contentHashCode()
            result = 31 * result + contentEncryptionAlgorithms.contentHashCode()
            result = 31 * result + keyWrapAlgorithms.contentHashCode()
            result = 31 * result + keyAgreementAlgorithms.contentHashCode()
            result = 31 * result + (notes?.hashCode() ?: 0)
            return result
        }
    }

/**
 * Complete capability report for a KMS provider.
 * Provides structured information about what the provider supports.
 *
 * @property providerId Unique identifier of the provider
 * @property providerType Type of the provider (software, mobile, azure, aws, etc.)
 * @property storageTypes Types of key storage supported by this provider
 * @property supportsKeyImport Whether the provider can import existing keys
 * @property supportsKeyExport Whether the provider can export keys
 * @property exposePrivateKeys Whether private keys can be accessed directly (true for software, false for HSM)
 * @property operations Detailed capability report for each operation
 * @property supportedKeyTypes Key types supported by this provider
 * @property supportedCurves Elliptic curves supported by this provider
 * @property supportedCryptoAlgorithms Crypto algorithms supported (ECDSA, RSA, ED25519, X25519, etc.)
 * @property supportedDigestAlgorithms Digest algorithms supported (SHA256, SHA384, SHA512, etc.)
 * @property signatureAlgorithms Complete signature algorithms supported (combines crypto + digest + curve)
 * @property contentEncryptionAlgorithms Content encryption algorithms supported (A128GCM, A256GCM, etc.)
 * @property supportsX509 Whether the provider supports X.509 certificates
 * @property supportsAttestation Whether the provider supports key attestation
 * @property supportsHardwareBacking Whether keys are backed by hardware security module
 * @property supportsPublicKeyResolution Whether the provider can resolve public keys
 * @property resolutionMethods Identifier resolution methods supported (JWK, X5C, DID, KID, etc.)
 */
@JsExportCompat
data class
KmsProviderCapabilities
    @JvmOverloads
    constructor(
        val providerId: String,
        val providerType: String,
        // Storage capabilities
        val storageTypes: Array<KeyStorageType>,
        val supportsKeyImport: Boolean,
        val supportsKeyExport: Boolean,
        val exposePrivateKeys: Boolean,
        // Cryptographic capabilities
        val operations: Array<OperationCapability>,
        // Key type support
        val supportedKeyTypes: Array<KeyTypeMapping>,
        val supportedCurves: Array<Curve>,
        // Algorithm support - using generic crypto types (NOT JOSE-specific)
        val supportedCryptoAlgorithms: Array<CryptoAlg>,
        val supportedDigestAlgorithms: Array<DigestAlg>,
        val signatureAlgorithms: Array<SignatureAlgorithm>,
        val contentEncryptionAlgorithms: Array<ContentEncryptionAlgorithm> = emptyArray(),
        // Additional capabilities
        val supportsX509: Boolean,
        val supportsAttestation: Boolean,
        val supportsHardwareBacking: Boolean,
        // Public key resolution - using IIdentifierMethod interface
        val supportsPublicKeyResolution: Boolean,
        val resolutionMethods: Array<IIdentifierMethod>,
    ) {
        /**
         * Checks if the provider supports a specific operation.
         */
        fun supportsOperation(operation: KmsProviderOperation): Boolean = operations.any { it.operation == operation && it.supported }

        /**
         * Checks if the provider supports encryption operations.
         */
        fun supportsEncryption(): Boolean = supportsOperation(KmsProviderOperation.ENCRYPT)

        /**
         * Checks if the provider supports signature operations.
         */
        fun supportsSigning(): Boolean = supportsOperation(KmsProviderOperation.SIGN)

        /**
         * Checks if the provider supports key agreement (e.g., ECDH).
         */
        fun supportsKeyAgreement(): Boolean = supportsOperation(KmsProviderOperation.KEY_AGREEMENT)

        /**
         * Checks if the provider supports key wrapping operations.
         */
        fun supportsKeyWrap(): Boolean = supportsOperation(KmsProviderOperation.WRAP_KEY)

        /**
         * Gets the capability details for a specific operation.
         */
        fun getOperationCapability(operation: KmsProviderOperation): OperationCapability? = operations.firstOrNull { it.operation == operation }

        /**
         * Checks if a specific crypto algorithm is supported.
         */
        fun supportsCryptoAlgorithm(algorithm: CryptoAlg): Boolean = supportedCryptoAlgorithms.contains(algorithm)

        /**
         * Checks if a specific digest algorithm is supported.
         */
        fun supportsDigestAlgorithm(algorithm: DigestAlg): Boolean = supportedDigestAlgorithms.contains(algorithm)

        /**
         * Checks if a specific content encryption algorithm is supported.
         */
        fun supportsContentEncryption(algorithm: ContentEncryptionAlgorithm): Boolean = contentEncryptionAlgorithms.contains(algorithm)

        /**
         * Checks if a specific identifier resolution method is supported.
         */
        fun supportsIdentifierMethod(method: IIdentifierMethod): Boolean = resolutionMethods.any { it.methodName == method.methodName }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as KmsProviderCapabilities

            if (providerId != other.providerId) {
                return false
            }
            if (providerType != other.providerType) {
                return false
            }
            if (!storageTypes.contentEquals(other.storageTypes)) {
                return false
            }
            if (supportsKeyImport != other.supportsKeyImport) {
                return false
            }
            if (supportsKeyExport != other.supportsKeyExport) {
                return false
            }
            if (exposePrivateKeys != other.exposePrivateKeys) {
                return false
            }
            if (!operations.contentEquals(other.operations)) {
                return false
            }
            if (!supportedKeyTypes.contentEquals(other.supportedKeyTypes)) {
                return false
            }
            if (!supportedCurves.contentEquals(other.supportedCurves)) {
                return false
            }
            if (!supportedCryptoAlgorithms.contentEquals(other.supportedCryptoAlgorithms)) {
                return false
            }
            if (!supportedDigestAlgorithms.contentEquals(other.supportedDigestAlgorithms)) {
                return false
            }
            if (!signatureAlgorithms.contentEquals(other.signatureAlgorithms)) {
                return false
            }
            if (!contentEncryptionAlgorithms.contentEquals(other.contentEncryptionAlgorithms)) {
                return false
            }
            if (supportsX509 != other.supportsX509) {
                return false
            }
            if (supportsAttestation != other.supportsAttestation) {
                return false
            }
            if (supportsHardwareBacking != other.supportsHardwareBacking) {
                return false
            }
            if (supportsPublicKeyResolution != other.supportsPublicKeyResolution) {
                return false
            }
            if (!resolutionMethods.contentEquals(other.resolutionMethods)) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = providerId.hashCode()
            result = 31 * result + providerType.hashCode()
            result = 31 * result + storageTypes.contentHashCode()
            result = 31 * result + supportsKeyImport.hashCode()
            result = 31 * result + supportsKeyExport.hashCode()
            result = 31 * result + exposePrivateKeys.hashCode()
            result = 31 * result + operations.contentHashCode()
            result = 31 * result + supportedKeyTypes.contentHashCode()
            result = 31 * result + supportedCurves.contentHashCode()
            result = 31 * result + supportedCryptoAlgorithms.contentHashCode()
            result = 31 * result + supportedDigestAlgorithms.contentHashCode()
            result = 31 * result + signatureAlgorithms.contentHashCode()
            result = 31 * result + contentEncryptionAlgorithms.contentHashCode()
            result = 31 * result + supportsX509.hashCode()
            result = 31 * result + supportsAttestation.hashCode()
            result = 31 * result + supportsHardwareBacking.hashCode()
            result = 31 * result + supportsPublicKeyResolution.hashCode()
            result = 31 * result + resolutionMethods.contentHashCode()
            return result
        }
    }

/**
 * Content Encryption Algorithms for JWE (enc header parameter).
 * These algorithms encrypt the actual plaintext content.
 *
 * References:
 * - RFC 7518 Section 5.1 (JWE Encryption Algorithms)
 * - RFC 7516 (JSON Web Encryption)
 */
@JsExportCompat
@Serializable
enum class ContentEncryptionAlgorithm(
    val identifier: String,
    val description: String,
    val keySize: Int, // Content encryption key size in bits
    val ivLength: Int, // Initialization vector length in bytes
    val tagLength: Int, // Authentication tag length in bytes (for AEAD)
    val cryptoAlg: CryptoAlg?, // Underlying crypto algorithm if applicable
) {
    // AES-GCM (Authenticated Encryption with Associated Data) - Recommended
    A128GCM("A128GCM", "AES-128 GCM", keySize = 128, ivLength = 12, tagLength = 16, cryptoAlg = null),
    A192GCM("A192GCM", "AES-192 GCM", keySize = 192, ivLength = 12, tagLength = 16, cryptoAlg = null),
    A256GCM("A256GCM", "AES-256 GCM", keySize = 256, ivLength = 12, tagLength = 16, cryptoAlg = null),

    // AES-CBC with HMAC-SHA2 (Composite Authenticated Encryption)
    A128CBC_HS256("A128CBC-HS256", "AES-128 CBC with HMAC-SHA-256", keySize = 256, ivLength = 16, tagLength = 16, cryptoAlg = null),
    A192CBC_HS384("A192CBC-HS384", "AES-192 CBC with HMAC-SHA-384", keySize = 384, ivLength = 16, tagLength = 24, cryptoAlg = null),
    A256CBC_HS512("A256CBC-HS512", "AES-256 CBC with HMAC-SHA-512", keySize = 512, ivLength = 16, tagLength = 32, cryptoAlg = null),
    ;

    override fun toString() = identifier

    companion object {
        @JsStatic
        @JvmStatic
        fun fromIdentifier(identifier: String): ContentEncryptionAlgorithm? = entries.firstOrNull { it.identifier == identifier }
    }
}

/**
 * Query criteria for selecting a KMS provider based on capabilities.
 * Used by KeyManagerService to find a suitable provider.
 *
 * Use the DSL function `kmsQuery { }` for easier construction.
 *
 * @property operation Required operation (optional - can query by algorithm alone)
 * @property cryptoAlgorithm Required crypto algorithm (ECDSA, RSA, ED25519, etc.)
 * @property digestAlgorithm Required digest algorithm (SHA256, SHA384, etc.)
 * @property signatureAlgorithm Required complete signature algorithm
 * @property contentEncryptionAlgorithm Required content encryption algorithm
 * @property curve Required elliptic curve
 * @property keyType Required key type
 * @property storageType Required storage type
 * @property requiresHardwareBacking Whether hardware backing is required
 * @property requiresAttestation Whether attestation is required
 * @property requiresKeyExport Whether key export is required
 * @property requiresKeyImport Whether key import is required
 * @property identifierMethod Required identifier resolution method
 * @property providerType Specific provider type filter (software, azure, aws, etc.)
 */
@JsExportCompat
data class
KmsProviderQuery
    @JvmOverloads
    constructor(
        val operation: KmsProviderOperation? = null,
        val cryptoAlgorithm: CryptoAlg? = null,
        val digestAlgorithm: DigestAlg? = null,
        val signatureAlgorithm: SignatureAlgorithm? = null,
        val contentEncryptionAlgorithm: ContentEncryptionAlgorithm? = null,
        val curve: Curve? = null,
        val keyType: KeyTypeMapping? = null,
        val storageType: KeyStorageType? = null,
        val requiresHardwareBacking: Boolean = false,
        val requiresAttestation: Boolean = false,
        val requiresKeyExport: Boolean = false,
        val requiresKeyImport: Boolean = false,
        val identifierMethod: IIdentifierMethod? = null,
        val providerType: String? = null,
    ) {
        /**
         * Checks if a provider's capabilities match this query.
         */
        fun matches(capabilities: KmsProviderCapabilities): Boolean {
            // Check provider type filter
            if (providerType != null && capabilities.providerType != providerType) {
                return false
            }

            // Check operation support
            if (operation != null && !capabilities.supportsOperation(operation)) {
                return false
            }

            // Check crypto algorithm
            if (cryptoAlgorithm != null && !capabilities.supportedCryptoAlgorithms.contains(cryptoAlgorithm)) {
                return false
            }

            // Check digest algorithm
            if (digestAlgorithm != null && !capabilities.supportedDigestAlgorithms.contains(digestAlgorithm)) {
                return false
            }

            // Check signature algorithm (higher level - combines crypto + digest + curve)
            if (signatureAlgorithm != null && !capabilities.signatureAlgorithms.contains(signatureAlgorithm)) {
                return false
            }

            // Check content encryption algorithm
            if (contentEncryptionAlgorithm != null && !capabilities.contentEncryptionAlgorithms.contains(contentEncryptionAlgorithm)) {
                return false
            }

            // Check curve
            if (curve != null && !capabilities.supportedCurves.contains(curve)) {
                return false
            }

            // Check key type
            if (keyType != null && !capabilities.supportedKeyTypes.contains(keyType)) {
                return false
            }

            // Check storage type
            if (storageType != null && !capabilities.storageTypes.contains(storageType)) {
                return false
            }

            // Check hardware backing
            if (requiresHardwareBacking && !capabilities.supportsHardwareBacking) {
                return false
            }

            // Check attestation
            if (requiresAttestation && !capabilities.supportsAttestation) {
                return false
            }

            // Check key export
            if (requiresKeyExport && !capabilities.supportsKeyExport) {
                return false
            }

            // Check key import
            if (requiresKeyImport && !capabilities.supportsKeyImport) {
                return false
            }

            // Check identifier method
            if (identifierMethod != null && !capabilities.supportsIdentifierMethod(identifierMethod)) {
                return false
            }

            return true
        }
    }

/**
 * DSL builder for KmsProviderQuery.
 * Provides a fluent API for constructing queries.
 */
@JsExportCompat
class KmsProviderQueryBuilder {
    var operation: KmsProviderOperation? = null
    var cryptoAlgorithm: CryptoAlg? = null
    var digestAlgorithm: DigestAlg? = null
    var signatureAlgorithm: SignatureAlgorithm? = null
    var contentEncryptionAlgorithm: ContentEncryptionAlgorithm? = null
    var curve: Curve? = null
    var keyType: KeyTypeMapping? = null
    var storageType: KeyStorageType? = null
    var requiresHardwareBacking: Boolean = false
    var requiresAttestation: Boolean = false
    var requiresKeyExport: Boolean = false
    var requiresKeyImport: Boolean = false
    var identifierMethod: IIdentifierMethod? = null
    var providerType: String? = null

    fun build(): KmsProviderQuery =
        KmsProviderQuery(
            operation = operation,
            cryptoAlgorithm = cryptoAlgorithm,
            digestAlgorithm = digestAlgorithm,
            signatureAlgorithm = signatureAlgorithm,
            contentEncryptionAlgorithm = contentEncryptionAlgorithm,
            curve = curve,
            keyType = keyType,
            storageType = storageType,
            requiresHardwareBacking = requiresHardwareBacking,
            requiresAttestation = requiresAttestation,
            requiresKeyExport = requiresKeyExport,
            requiresKeyImport = requiresKeyImport,
            identifierMethod = identifierMethod,
            providerType = providerType,
        )
}

/**
 * DSL function for creating a KmsProviderQuery.
 *
 * Example usage:
 * ```kotlin
 * // Find provider for ephemeral ES256 key without hardware backing
 * val query = kmsQuery {
 *     curve = Curve.P_256
 *     signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
 *     storageType = KeyStorageType.EPHEMERAL
 *     requiresHardwareBacking = false
 * }
 *
 * // Find Azure provider that supports key import
 * val azureQuery = kmsQuery {
 *     providerType = "azure_keyvault"
 *     requiresKeyImport = true
 * }
 *
 * // Find provider for X.509 signature verification
 * val verifyQuery = kmsQuery {
 *     operation = KmsProviderOperation.VERIFY
 *     identifierMethod = IdentifierMethodDefaults.X5C
 * }
 * ```
 */
fun kmsQuery(block: KmsProviderQueryBuilder.() -> Unit): KmsProviderQuery = KmsProviderQueryBuilder().apply(block).build()
