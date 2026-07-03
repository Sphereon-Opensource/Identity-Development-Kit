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

import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.CryptoAlg
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.CertificateOptions
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.EncryptionResult
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KeyStorageType
import com.sphereon.crypto.core.kms.KeyStoreService
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderCapabilities
import com.sphereon.crypto.core.kms.KmsProviderOperation
import com.sphereon.crypto.core.kms.OperationCapability
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.sign.model.SignInput
import com.sphereon.crypto.core.sign.model.SignOutput
import com.sphereon.crypto.core.sign.model.Signature
import com.sphereon.crypto.core.x509.Certificate

/**
 * Base abstract class for Azure Key Vault crypto providers across different platforms.
 * Provides common functionality and defines the contract for platform-specific implementations.
 * Implements multiple interfaces for key management, signature services, and key storage.
 *
 * @param config Configuration containing Azure Key Vault settings and credentials
 * @param settings Key provider settings for managing key operations
 */
abstract class BaseAzureKeyvaultCryptoProvider(
    val config: AzureKmsProviderConfig,
//    override val settings: KeyProviderSettings
) : KmsProvider,
    KeyStoreService {
    override val id: String = config.applicationId

    override val settings: KeyProviderSettings?
        get() = throw UnsupportedOperationException("Azure Key Vault settings requires a platform-specific implementation")

    /**
     * Returns the full capabilities of this Azure Key Vault KMS provider.
     */
    override fun getCapabilities(): KmsProviderCapabilities =
        KmsProviderCapabilities(
            providerId = id,
            providerType = kmsProviderType,
            // Storage capabilities
            storageTypes = arrayOf(KeyStorageType.HARDWARE),
            supportsKeyImport = true,
            supportsKeyExport = false, // Azure Key Vault doesn't allow private key export
            exposePrivateKeys = false,
            // Operations
            operations =
                arrayOf(
                    OperationCapability(
                        operation = KmsProviderOperation.GENERATE_KEY,
                        supported = true,
                        notes = "Supports EC key generation (P-256, P-384, P-521, secp256k1) and RSA key generation (2048-bit)",
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.IMPORT_KEY,
                        supported = true,
                        notes = "Supports importing EC and RSA keys",
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.EXPORT_KEY,
                        supported = false,
                        notes = "Azure Key Vault does not support private key export",
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.DELETE_KEY,
                        supported = true,
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.SIGN,
                        supported = true,
                        signatureAlgorithms =
                            arrayOf(
                                SignatureAlgorithm.ECDSA_SHA256,
                                SignatureAlgorithm.ECDSA_SHA384,
                                SignatureAlgorithm.ECDSA_SHA512,
                                SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
                                SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
                                SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
                                SignatureAlgorithm.RSA_SHA256,
                                SignatureAlgorithm.RSA_SHA384,
                                SignatureAlgorithm.RSA_SHA512,
                            ),
                        notes = "Supports ECDSA and RSA (PKCS#1 v1.5 and PSS) signatures",
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.SIGN_DIGEST,
                        supported = true,
                        signatureAlgorithms =
                            arrayOf(
                                SignatureAlgorithm.ECDSA_SHA256,
                                SignatureAlgorithm.ECDSA_SHA384,
                                SignatureAlgorithm.ECDSA_SHA512,
                                SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
                                SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
                                SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
                                SignatureAlgorithm.RSA_SHA256,
                                SignatureAlgorithm.RSA_SHA384,
                                SignatureAlgorithm.RSA_SHA512,
                            ),
                        notes = "Azure Key Vault sign operation signs caller-provided digests; ECDSA DER/RAW normalization is handled at the provider boundary",
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.VERIFY,
                        supported = true,
                        signatureAlgorithms =
                            arrayOf(
                                SignatureAlgorithm.ECDSA_SHA256,
                                SignatureAlgorithm.ECDSA_SHA384,
                                SignatureAlgorithm.ECDSA_SHA512,
                                SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
                                SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
                                SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
                                SignatureAlgorithm.RSA_SHA256,
                                SignatureAlgorithm.RSA_SHA384,
                                SignatureAlgorithm.RSA_SHA512,
                            ),
                        notes = "Supports ECDSA and RSA (PKCS#1 v1.5 and PSS) signature verification",
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.VERIFY_DIGEST,
                        supported = true,
                        signatureAlgorithms =
                            arrayOf(
                                SignatureAlgorithm.ECDSA_SHA256,
                                SignatureAlgorithm.ECDSA_SHA384,
                                SignatureAlgorithm.ECDSA_SHA512,
                                SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
                                SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
                                SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
                                SignatureAlgorithm.RSA_SHA256,
                                SignatureAlgorithm.RSA_SHA384,
                                SignatureAlgorithm.RSA_SHA512,
                            ),
                        notes = "Azure Key Vault verify operation verifies caller-provided digests; ECDSA DER/RAW normalization is handled at the provider boundary",
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.ENCRYPT,
                        supported = true,
                        contentEncryptionAlgorithms =
                            arrayOf(
                                ContentEncryptionAlgorithm.A128GCM,
                                ContentEncryptionAlgorithm.A192GCM,
                                ContentEncryptionAlgorithm.A256GCM,
                                ContentEncryptionAlgorithm.A128CBC_HS256,
                                ContentEncryptionAlgorithm.A192CBC_HS384,
                                ContentEncryptionAlgorithm.A256CBC_HS512,
                            ),
                        notes = "Supports AES-GCM and AES-CBC with HMAC-SHA2 encryption",
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.DECRYPT,
                        supported = true,
                        contentEncryptionAlgorithms =
                            arrayOf(
                                ContentEncryptionAlgorithm.A128GCM,
                                ContentEncryptionAlgorithm.A192GCM,
                                ContentEncryptionAlgorithm.A256GCM,
                                ContentEncryptionAlgorithm.A128CBC_HS256,
                                ContentEncryptionAlgorithm.A192CBC_HS384,
                                ContentEncryptionAlgorithm.A256CBC_HS512,
                            ),
                        notes = "Supports AES-GCM and AES-CBC with HMAC-SHA2 decryption",
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.WRAP_KEY,
                        supported = true,
                        keyWrapAlgorithms =
                            arrayOf(
                                KeyWrapAlgorithm.RSA1_5,
                                KeyWrapAlgorithm.RSA_OAEP,
                                KeyWrapAlgorithm.RSA_OAEP_256,
                                KeyWrapAlgorithm.A128KW,
                                KeyWrapAlgorithm.A192KW,
                                KeyWrapAlgorithm.A256KW,
                            ),
                        notes = "Supports RSA (PKCS#1 v1.5, OAEP, OAEP-256) and AES key wrapping",
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.UNWRAP_KEY,
                        supported = true,
                        keyWrapAlgorithms =
                            arrayOf(
                                KeyWrapAlgorithm.RSA1_5,
                                KeyWrapAlgorithm.RSA_OAEP,
                                KeyWrapAlgorithm.RSA_OAEP_256,
                                KeyWrapAlgorithm.A128KW,
                                KeyWrapAlgorithm.A192KW,
                                KeyWrapAlgorithm.A256KW,
                            ),
                        notes = "Supports RSA (PKCS#1 v1.5, OAEP, OAEP-256) and AES key unwrapping",
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.KEY_AGREEMENT,
                        supported = false,
                        notes = "ECDH key agreement not supported by Azure Key Vault",
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.GENERATE_CERTIFICATE,
                        supported = false,
                        notes = "Certificate generation not yet implemented",
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.IMPORT_CERTIFICATE,
                        supported = false,
                        notes = "Certificate import not yet implemented",
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.X509_CHAIN_VALIDATION,
                        supported = false,
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.HARDWARE_BACKED,
                        supported = true,
                        notes = "Azure Key Vault provides HSM-backed key storage",
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.ATTESTATION,
                        supported = false,
                    ),
                ),
            // Key type support - EC for signing, RSA for key wrapping/encryption
            supportedKeyTypes = arrayOf(KeyTypeMapping.EC, KeyTypeMapping.RSA),
            supportedCurves = arrayOf(Curve.P_256, Curve.Secp256k1, Curve.P_384, Curve.P_521),
            // Algorithm support
            supportedCryptoAlgorithms = arrayOf(CryptoAlg.ECDSA, CryptoAlg.RSA),
            supportedDigestAlgorithms = arrayOf(DigestAlg.SHA256, DigestAlg.SHA384, DigestAlg.SHA512),
            signatureAlgorithms =
                arrayOf(
                    SignatureAlgorithm.ECDSA_SHA256,
                    SignatureAlgorithm.ECDSA_SHA384,
                    SignatureAlgorithm.ECDSA_SHA512,
                    SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
                    SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
                    SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
                    SignatureAlgorithm.RSA_SHA256,
                    SignatureAlgorithm.RSA_SHA384,
                    SignatureAlgorithm.RSA_SHA512,
                ),
            contentEncryptionAlgorithms =
                arrayOf(
                    ContentEncryptionAlgorithm.A128GCM,
                    ContentEncryptionAlgorithm.A192GCM,
                    ContentEncryptionAlgorithm.A256GCM,
                    ContentEncryptionAlgorithm.A128CBC_HS256,
                    ContentEncryptionAlgorithm.A192CBC_HS384,
                    ContentEncryptionAlgorithm.A256CBC_HS512,
                ),
            // Additional capabilities
            supportsX509 = false,
            supportsAttestation = false,
            supportsHardwareBacking = true,
            // Public key resolution
            supportsPublicKeyResolution = true,
            resolutionMethods = emptyArray(),
        )

    /**
     * Returns the array of cryptographic curves supported by Azure Key Vault.
     *
     * @return Array of supported Curve values
     */
    @Deprecated("Use getCapabilities().supportedCurves instead")
    override fun supportedCurves(): Array<Curve> = getCapabilities().supportedCurves

    /**
     * Checks if a specific cryptographic curve is supported by this provider.
     *
     * @param curve The curve to check for support
     * @return True if the curve is supported, false otherwise
     */
    override fun isSupportedCurve(curve: Curve): Boolean = getCapabilities().supportedCurves.contains(curve)

    /**
     * Returns the array of digest algorithms supported by this provider.
     * Extracts digest algorithms from supported signature algorithms.
     *
     * @return Array of supported DigestAlg values
     */
    @Deprecated("Use getCapabilities().supportedDigestAlgorithms instead")
    override fun supportedDigests(): Array<DigestAlg> = getCapabilities().supportedDigestAlgorithms

    /**
     * Returns the array of key types supported by Azure Key Vault.
     * Currently only supports Elliptic Curve keys.
     *
     * @return Array of supported KeyType values
     */
    @Deprecated("Use getCapabilities().supportedKeyTypes instead")
    override fun supportedKeyTypes(): Array<KeyTypeMapping> = getCapabilities().supportedKeyTypes

    /**
     * Returns the array of signature algorithms supported by Azure Key Vault.
     *
     * @return Array of supported SignatureAlgorithm values
     */
    @Deprecated("Use getCapabilities().signatureAlgorithms instead")
    override fun supportedSignatureAlgorithms(): Array<SignatureAlgorithm> = getCapabilities().signatureAlgorithms

    /**
     * Checks if a specific signature algorithm is supported by this provider.
     *
     * @param signatureAlgorithm The signature algorithm to check
     * @return True if the algorithm is supported, false otherwise
     */
    fun isSupportedSignatureAlgorithm(signatureAlgorithm: SignatureAlgorithm): Boolean = supportedSignatureAlgorithms().contains(signatureAlgorithm)

    /**
     * Generates a new key in Azure Key Vault with the specified parameters.
     * Must be implemented by platform-specific subclasses.
     *
     * @param alias Optional key reference/name
     * @param use JWK use parameter for the key
     * @param keyOperations Array of allowed key operations
     * @param alg Signature algorithm for the key
     * @return ManagedKeyPair containing the generated key information
     */
    override suspend fun generateKeyAsync(
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        certificateOptions: CertificateOptions?,
    ): ManagedKeyPair = throw UnsupportedOperationException("Azure Key Vault key generation requires a platform-specific implementation")

    /**
     * Creates a raw digital signature using an Azure Key Vault key.
     * Must be implemented by platform-specific subclasses.
     *
     * @param keyInfo Key information containing the key reference
     * @param input Data to be signed
     * @param requireX5Chain Whether X.509 certificate chain is required
     * @return Raw signature bytes
     */
    override suspend fun createRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean,
    ): ByteArray = throw UnsupportedOperationException("Azure Key Vault raw signature creation requires a platform-specific implementation")

    /**
     * Verifies a raw signature using a key stored in Azure Key Vault.
     * Must be implemented by platform-specific subclasses.
     *
     * @param keyInfo Key information containing the key reference
     * @param input Original data that was signed
     * @param signature Signature bytes to verify
     * @return True if signature is valid, false otherwise
     */
    override suspend fun isValidRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray,
    ): Boolean = throw UnsupportedOperationException("Azure Key Vault raw signature verification requires a platform-specific implementation")

    /**
     * Creates a complete signature structure including metadata and validation.
     * Must be implemented by platform-specific subclasses.
     *
     * @param signInput Input data and parameters for signing
     * @param keyInfo Key information for signing
     * @param signatureAlgorithm Algorithm to use for signing
     * @return SignOutput containing the signature and metadata
     */
    override suspend fun createSignature(
        signInput: SignInput,
        keyInfo: KeyInfoType<*>?,
        signatureAlgorithm: SignatureAlgorithm?,
    ): SignOutput = throw UnsupportedOperationException("Azure Key Vault signature creation requires a platform-specific implementation")

    /**
     * Validates a signature against the original input data.
     * Must be implemented by platform-specific subclasses.
     *
     * @param signInput Original input that was signed
     * @param signature Signature to validate
     * @return True if signature is valid, false otherwise
     */
    override suspend fun isValidSignature(
        signInput: SignInput,
        signature: Signature,
    ): Boolean = throw UnsupportedOperationException("Azure Key Vault signature verification requires a platform-specific implementation")

    /**
     * Lists all available keys from the Azure Key Vault.
     * Must be implemented by platform-specific subclasses.
     *
     * @return Array of managed key information for all available keys
     */
    override suspend fun listKeys(): Array<ManagedKeyReference> = throw UnsupportedOperationException("Azure Key Vault key listing requires a platform-specific implementation")

    /**
     * Retrieves a specific key by its key information.
     * Must be implemented by platform-specific subclasses.
     *
     * @param keyInfo Key information containing either key reference or kid
     * @return Managed key information for the requested key
     */
    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> = throw UnsupportedOperationException("Azure Key Vault key retrieval requires a platform-specific implementation")

    /**
     * Imports an existing key into Azure Key Vault.
     * Must be implemented by platform-specific subclasses.
     *
     * @param keyInfo Resolved key information containing the key to import
     * @param providerId KMS identifier
     * @param alias Key reference for the imported key
     * @return Managed key information for the imported key
     */
    override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?,
    ): ManagedKeyInfoType<*> = throw UnsupportedOperationException("Azure Key Vault key storage requires a platform-specific implementation")

    /**
     * Deletes a key from Azure Key Vault.
     * Must be implemented by platform-specific subclasses.
     *
     * @param keyInfo Key information containing the key reference to delete
     * @return True if deletion was successful, false otherwise
     */
    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean = throw UnsupportedOperationException("Azure Key Vault key deletion requires a platform-specific implementation")

    /**
     * Returns the key visibility level for this provider.
     * Must be implemented by platform-specific subclasses.
     *
     * @return KeyVisibility indicating the visibility level of keys
     */
    override fun keyVisibility(): KeyVisibility = throw UnsupportedOperationException("Azure Key Vault key visibility requires a platform-specific implementation")

    /**
     * Encrypts plaintext using the specified key and algorithm.
     * Must be implemented by platform-specific subclasses.
     *
     * @param keyInfo Key information containing the key reference
     * @param plaintext Data to encrypt
     * @param algorithm Content encryption algorithm to use
     * @param additionalAuthenticatedData Optional AAD for authenticated encryption
     * @return EncryptionResult containing ciphertext, IV, and authentication tag
     */
    override suspend fun encrypt(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?,
    ): EncryptionResult = throw UnsupportedOperationException("Azure Key Vault encryption requires a platform-specific implementation")

    /**
     * Decrypts ciphertext using the specified key and algorithm.
     * Must be implemented by platform-specific subclasses.
     *
     * @param keyInfo Key information containing the key reference
     * @param ciphertext Encrypted data
     * @param algorithm Content encryption algorithm used for encryption
     * @param iv Initialization vector used during encryption
     * @param authTag Authentication tag for verification
     * @param additionalAuthenticatedData Optional AAD used during encryption
     * @return Decrypted plaintext
     */
    override suspend fun decrypt(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?,
    ): ByteArray = throw UnsupportedOperationException("Azure Key Vault decryption requires a platform-specific implementation")

    /**
     * Wraps (encrypts) a key using the specified wrapping key and algorithm.
     * Must be implemented by platform-specific subclasses.
     *
     * @param wrappingKeyInfo Key information for the wrapping key
     * @param keyToWrap Key material to wrap
     * @param algorithm Key wrap algorithm to use
     * @return Wrapped key bytes
     */
    override suspend fun wrapKey(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): ByteArray = throw UnsupportedOperationException("Azure Key Vault key wrapping requires a platform-specific implementation")

    /**
     * Unwraps (decrypts) a wrapped key using the specified unwrapping key and algorithm.
     * Must be implemented by platform-specific subclasses.
     *
     * @param unwrappingKeyInfo Key information for the unwrapping key
     * @param wrappedKey Wrapped key bytes
     * @param algorithm Key wrap algorithm used during wrapping
     * @return Unwrapped key material
     */
    override suspend fun unwrapKey(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): ByteArray = throw UnsupportedOperationException("Azure Key Vault key unwrapping requires a platform-specific implementation")

    /**
     * Performs key agreement using ECDH or similar algorithms.
     * Not supported by Azure Key Vault provider.
     *
     * @throws UnsupportedOperationException Always, as Azure Key Vault doesn't support key agreement
     */
    override suspend fun performKeyAgreement(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int?,
    ): ByteArray = throw UnsupportedOperationException("Key agreement not supported by Azure Key Vault provider")
}
