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

package com.sphereon.crypto.kms.provider.aws

import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.model.AwsKmsClientConfig
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.kms.model.KeyProviderType


import com.sphereon.crypto.core.sign.model.SignInput
import com.sphereon.crypto.core.sign.model.SignOutput
import com.sphereon.crypto.core.sign.model.Signature
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.CryptoAlg
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.CertificateOptions
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.EncryptionResult
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KeyStoreService
import com.sphereon.crypto.core.kms.KeyStorageType
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import com.sphereon.crypto.core.kms.KmsProviderCapabilities
import com.sphereon.crypto.core.kms.KmsProviderOperation
import com.sphereon.crypto.core.kms.OperationCapability

abstract class BaseAwsKmsCryptoProvider(override val settings: KeyProviderSettings) : KmsProvider,
    KeyStoreService {

    init {
        check(settings.id.isNotBlank()) { "Missing ID in settings.id" }
        requireNotNull(settings.config.aws) { "Missing AWS KMS configuration in settings.config.aws" }
        check(settings.config.type == KeyProviderType.AWS_KMS) { "Invalid key provider type: ${settings.config.type}. Expected AWS_KMS" }
    }


    protected val awsConfig: AwsKmsClientConfig = settings.config.aws!!

    override val id: String = settings.id

    /**
     * Returns the full capabilities of this AWS KMS provider.
     */
    override fun getCapabilities(): KmsProviderCapabilities {
        return KmsProviderCapabilities(
            providerId = id,
            providerType = settings.config.type.name,

            // Storage capabilities
            storageTypes = arrayOf(KeyStorageType.HARDWARE),
            supportsKeyImport = true,
            supportsKeyExport = false,  // AWS KMS doesn't allow private key export
            exposePrivateKeys = false,

            // Operations
            operations = arrayOf(
                OperationCapability(
                    operation = KmsProviderOperation.GENERATE_KEY,
                    supported = true,
                    notes = "Supports EC (P-256, P-384, P-521) and RSA (2048, 3072, 4096) key generation"
                ),
                OperationCapability(
                    operation = KmsProviderOperation.IMPORT_KEY,
                    supported = true
                ),
                OperationCapability(
                    operation = KmsProviderOperation.EXPORT_KEY,
                    supported = false,
                    notes = "AWS KMS does not support private key export"
                ),
                OperationCapability(
                    operation = KmsProviderOperation.DELETE_KEY,
                    supported = true
                ),
                OperationCapability(
                    operation = KmsProviderOperation.SIGN,
                    supported = true,
                    signatureAlgorithms = arrayOf(
                        SignatureAlgorithm.ECDSA_SHA256,
                        SignatureAlgorithm.ECDSA_SHA384,
                        SignatureAlgorithm.ECDSA_SHA512,
                        SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
                        SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
                        SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
                        SignatureAlgorithm.RSA_SHA256,
                        SignatureAlgorithm.RSA_SHA384,
                        SignatureAlgorithm.RSA_SHA512
                    ),
                    notes = "Supports ECDSA and RSA (PKCS#1 v1.5 and PSS) signatures"
                ),
                OperationCapability(
                    operation = KmsProviderOperation.VERIFY,
                    supported = true,
                    signatureAlgorithms = arrayOf(
                        SignatureAlgorithm.ECDSA_SHA256,
                        SignatureAlgorithm.ECDSA_SHA384,
                        SignatureAlgorithm.ECDSA_SHA512,
                        SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
                        SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
                        SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
                        SignatureAlgorithm.RSA_SHA256,
                        SignatureAlgorithm.RSA_SHA384,
                        SignatureAlgorithm.RSA_SHA512
                    ),
                    notes = "Supports ECDSA and RSA (PKCS#1 v1.5 and PSS) signature verification"
                ),
                OperationCapability(
                    operation = KmsProviderOperation.ENCRYPT,
                    supported = true,
                    contentEncryptionAlgorithms = arrayOf(
                        ContentEncryptionAlgorithm.A128GCM,
                        ContentEncryptionAlgorithm.A192GCM,
                        ContentEncryptionAlgorithm.A256GCM,
                        ContentEncryptionAlgorithm.A128CBC_HS256,
                        ContentEncryptionAlgorithm.A192CBC_HS384,
                        ContentEncryptionAlgorithm.A256CBC_HS512
                    ),
                    notes = "AWS KMS supports AES encryption operations"
                ),
                OperationCapability(
                    operation = KmsProviderOperation.DECRYPT,
                    supported = true,
                    contentEncryptionAlgorithms = arrayOf(
                        ContentEncryptionAlgorithm.A128GCM,
                        ContentEncryptionAlgorithm.A192GCM,
                        ContentEncryptionAlgorithm.A256GCM,
                        ContentEncryptionAlgorithm.A128CBC_HS256,
                        ContentEncryptionAlgorithm.A192CBC_HS384,
                        ContentEncryptionAlgorithm.A256CBC_HS512
                    ),
                    notes = "AWS KMS supports AES decryption operations"
                ),
                OperationCapability(
                    operation = KmsProviderOperation.WRAP_KEY,
                    supported = true,
                    keyWrapAlgorithms = arrayOf(
                        KeyWrapAlgorithm.RSA_OAEP,
                        KeyWrapAlgorithm.RSA_OAEP_256
                    ),
                    notes = "AWS KMS supports RSA-OAEP key wrapping"
                ),
                OperationCapability(
                    operation = KmsProviderOperation.UNWRAP_KEY,
                    supported = true,
                    keyWrapAlgorithms = arrayOf(
                        KeyWrapAlgorithm.RSA_OAEP,
                        KeyWrapAlgorithm.RSA_OAEP_256
                    ),
                    notes = "AWS KMS supports RSA-OAEP key unwrapping"
                ),
                OperationCapability(
                    operation = KmsProviderOperation.KEY_AGREEMENT,
                    supported = true,
                    keyAgreementAlgorithms = arrayOf(KeyAgreementAlgorithm.ECDH_ES),
                    notes = "ECDH supported via DeriveSharedSecret API (since June 2024). Requires KMS keys with KEY_AGREEMENT usage (create via AWS Console/SDK)."
                ),
                OperationCapability(
                    operation = KmsProviderOperation.GENERATE_CERTIFICATE,
                    supported = false
                ),
                OperationCapability(
                    operation = KmsProviderOperation.IMPORT_CERTIFICATE,
                    supported = false
                ),
                OperationCapability(
                    operation = KmsProviderOperation.X509_CHAIN_VALIDATION,
                    supported = false
                ),
                OperationCapability(
                    operation = KmsProviderOperation.HARDWARE_BACKED,
                    supported = true,
                    notes = "AWS KMS uses HSM-backed keys"
                ),
                OperationCapability(
                    operation = KmsProviderOperation.ATTESTATION,
                    supported = false
                )
            ),

            // Key type support
            supportedKeyTypes = arrayOf(KeyTypeMapping.EC, KeyTypeMapping.RSA),
            supportedCurves = arrayOf(Curve.P_256, Curve.P_384, Curve.P_521),

            // Algorithm support
            supportedCryptoAlgorithms = arrayOf(CryptoAlg.ECDSA, CryptoAlg.RSA),
            supportedDigestAlgorithms = arrayOf(DigestAlg.SHA256, DigestAlg.SHA384, DigestAlg.SHA512),
            signatureAlgorithms = arrayOf(
                SignatureAlgorithm.ECDSA_SHA256,
                SignatureAlgorithm.ECDSA_SHA384,
                SignatureAlgorithm.ECDSA_SHA512,
                SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
                SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
                SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
                SignatureAlgorithm.RSA_SHA256,
                SignatureAlgorithm.RSA_SHA384,
                SignatureAlgorithm.RSA_SHA512
            ),
            contentEncryptionAlgorithms = arrayOf(
                ContentEncryptionAlgorithm.A128GCM,
                ContentEncryptionAlgorithm.A192GCM,
                ContentEncryptionAlgorithm.A256GCM,
                ContentEncryptionAlgorithm.A128CBC_HS256,
                ContentEncryptionAlgorithm.A192CBC_HS384,
                ContentEncryptionAlgorithm.A256CBC_HS512
            ),

            // Additional capabilities
            supportsX509 = false,
            supportsAttestation = false,
            supportsHardwareBacking = true,

            // Public key resolution
            supportsPublicKeyResolution = true,
            resolutionMethods = emptyArray()
        )
    }

    @Deprecated("Use getCapabilities().supportedCurves instead")
    override fun supportedCurves(): Array<Curve> = getCapabilities().supportedCurves

    override fun isSupportedCurve(curve: Curve): Boolean = getCapabilities().supportedCurves.contains(curve)

    @Deprecated("Use getCapabilities().supportedDigestAlgorithms instead")
    override fun supportedDigests(): Array<DigestAlg> = getCapabilities().supportedDigestAlgorithms

    @Deprecated("Use getCapabilities().supportedKeyTypes instead")
    override fun supportedKeyTypes(): Array<KeyTypeMapping> = getCapabilities().supportedKeyTypes

    @Deprecated("Use getCapabilities().signatureAlgorithms instead")
    override fun supportedSignatureAlgorithms(): Array<SignatureAlgorithm> = getCapabilities().signatureAlgorithms

    fun isSupportedSignatureAlgorithm(signatureAlgorithm: SignatureAlgorithm): Boolean {
        return supportedSignatureAlgorithms().contains(signatureAlgorithm)
    }

    override suspend fun generateKeyAsync(
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        certificateOptions: CertificateOptions?
    ): ManagedKeyPair {
        TODO("Implement in platform-specific code")
    }

    override suspend fun createRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean
    ): ByteArray {
        TODO("Implement in platform-specific code")
    }

    override suspend fun isValidRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray
    ): Boolean {
        TODO("Implement in platform-specific code")
    }

    override suspend fun createSignature(
        signInput: SignInput,
        keyInfo: KeyInfoType<*>?,
        signatureAlgorithm: SignatureAlgorithm?
    ): SignOutput {
        TODO("Implement in platform-specific code")
    }

    override suspend fun isValidSignature(signInput: SignInput, signature: Signature): Boolean {
        TODO("Implement in platform-specific code")
    }

    // Encryption operations - to be implemented using AWS KMS SDK
    override suspend fun encrypt(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?
    ): EncryptionResult {
        TODO("Implement AWS KMS encryption")
    }

    override suspend fun decrypt(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?
    ): ByteArray {
        TODO("Implement AWS KMS decryption")
    }

    override suspend fun wrapKey(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): ByteArray {
        TODO("Implement AWS KMS key wrapping")
    }

    override suspend fun unwrapKey(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): ByteArray {
        TODO("Implement AWS KMS key unwrapping")
    }

    override suspend fun performKeyAgreement(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int?
    ): ByteArray {
        throw UnsupportedOperationException("Key agreement not supported by AWS KMS provider")
    }

}
