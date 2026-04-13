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

package com.sphereon.crypto.kms.provider.mobile

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.Digest
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.RSAPadding
import at.asitplus.signum.supreme.dsl.PREFERRED
import at.asitplus.signum.supreme.os.JKSProvider
import at.asitplus.signum.supreme.os.SigningProvider
import at.asitplus.signum.supreme.sign.SignatureInput
import at.asitplus.signum.supreme.sign.Signer
import at.asitplus.signum.supreme.sign.verifierFor
import at.asitplus.signum.supreme.signature
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Inject
import com.sphereon.cbor.toCborByteString
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.Encoding
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.CoseKeyPair
import com.sphereon.crypto.core.generic.CryptoAlg
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.JoseKeyPair
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.interop.DerKmpKeyInfoContext
import com.sphereon.crypto.core.interop.checkSupportedEcdsaCurve
import com.sphereon.crypto.core.interop.keyInfoToEcdsaDerKmpContext
import com.sphereon.crypto.core.interop.keyInfoToRSADerKmpContext
import com.sphereon.crypto.core.interop.resolveEcdsaSignumCurve
import com.sphereon.crypto.core.interop.toJwk
import com.sphereon.crypto.core.interop.toSignum
import com.sphereon.crypto.core.interop.toSignumAlgorithm
import com.sphereon.crypto.core.interop.toSignumPublicKey
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.jose.generateJwkThumbprint
import com.sphereon.crypto.core.kms.CertificateOptions
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.EncryptionResult
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KeyStorageType
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderCapabilities
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import com.sphereon.crypto.core.kms.KmsProviderOperation
import com.sphereon.crypto.core.kms.OperationCapability


import com.sphereon.crypto.core.sign.model.SignInput
import com.sphereon.crypto.core.sign.model.SignOutput
import com.sphereon.crypto.core.sign.model.Signature
import com.sphereon.crypto.core.kms.KeyStoreService
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.kms.keystore.memory.MemoryKeyStoreConfig
import com.sphereon.crypto.kms.keystore.memory.MemoryKeyStoreService
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("MobileKmsProvider", exact = true)
interface MobileKmsProvider : KmsProvider

/**
 * Non-Hardware-based CryptoProvider provides Elliptic Curve and RSA cryptographic operations delegating to well-known platform implementations like OpenSSL3, WebCrypto, Apple, Jdk,
 *
 * Warning: To be used for testing purposes. Use hardware-based crypto providers for production!
 * Exception to the warning: If you need ephemeral keys then you can use this provider together with the default in memory private key store.
 *
 * @param provider An instance of CryptographyProvider to use for cryptographic operations. Default is CryptographyProvider.Default.
 */
@dev.zacsweers.metro.AssistedFactory
fun interface MobileKmsProviderImplFactory {
    fun create(@Assisted config: KmsProviderConfigBase, @Assisted execution: SessionExecution): MobileKmsProviderImpl
}

@AssistedInject
@OptIn(ExperimentalObjCName::class)
@ObjCName("MobileKmsProviderImpl", exact = true)
class MobileKmsProviderImpl(
    @Assisted private val config: KmsProviderConfigBase,
    // Assisted because this comes from a factory in app scope
    @Assisted private val execution: SessionExecution,
) : MobileKmsProvider {
    override val kmsProviderType = config.kmsProviderType
    override val id = config.id
    override val order = config.order
    private val lookupStore: KeyStoreService = MemoryKeyStoreService(MemoryKeyStoreConfig())//keyVisibility = KeyVisibility.PUBLIC)
    private val provider: SigningProvider = JKSProvider.Ephemeral().getOrThrow()
    private val exposePrivateKeysDuringGeneration: Boolean = config.exposePrivateKeysDuringGeneration

    /**
     * Returns the full capabilities of this KMS provider.
     */
    override fun getCapabilities(): KmsProviderCapabilities {
        return KmsProviderCapabilities(
            providerId = id,
            providerType = kmsProviderType,

            // Storage capabilities
            storageTypes = arrayOf(KeyStorageType.HARDWARE, KeyStorageType.EPHEMERAL),
            supportsKeyImport = false,
            supportsKeyExport = false,
            exposePrivateKeys = exposePrivateKeysDuringGeneration,

            // Operations
            operations = arrayOf(
                OperationCapability(
                    operation = KmsProviderOperation.GENERATE_KEY,
                    supported = true
                ),
                OperationCapability(
                    operation = KmsProviderOperation.IMPORT_KEY,
                    supported = false
                ),
                OperationCapability(
                    operation = KmsProviderOperation.EXPORT_KEY,
                    supported = false
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
                        SignatureAlgorithm.RSA_RAW,
                        SignatureAlgorithm.RSA_SHA256,
                        SignatureAlgorithm.RSA_SHA384,
                        SignatureAlgorithm.RSA_SHA512,
                        SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
                        SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
                        SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
                        SignatureAlgorithm.RSA_SSA_PSS_RAW_MGF1
                    )
                ),
                OperationCapability(
                    operation = KmsProviderOperation.VERIFY,
                    supported = true,
                    signatureAlgorithms = arrayOf(
                        SignatureAlgorithm.ECDSA_SHA256,
                        SignatureAlgorithm.ECDSA_SHA384,
                        SignatureAlgorithm.ECDSA_SHA512,
                        SignatureAlgorithm.RSA_RAW,
                        SignatureAlgorithm.RSA_SHA256,
                        SignatureAlgorithm.RSA_SHA384,
                        SignatureAlgorithm.RSA_SHA512,
                        SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
                        SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
                        SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
                        SignatureAlgorithm.RSA_SSA_PSS_RAW_MGF1
                    )
                ),
                OperationCapability(
                    operation = KmsProviderOperation.ENCRYPT,
                    supported = false,
                    notes = "Not supported by mobile KMS provider"
                ),
                OperationCapability(
                    operation = KmsProviderOperation.DECRYPT,
                    supported = false,
                    notes = "Not supported by mobile KMS provider"
                ),
                OperationCapability(
                    operation = KmsProviderOperation.WRAP_KEY,
                    supported = false,
                    notes = "Not supported by mobile KMS provider"
                ),
                OperationCapability(
                    operation = KmsProviderOperation.UNWRAP_KEY,
                    supported = false,
                    notes = "Not supported by mobile KMS provider"
                ),
                OperationCapability(
                    operation = KmsProviderOperation.KEY_AGREEMENT,
                    supported = false,
                    notes = "Not supported by mobile KMS provider"
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
                    notes = "Prefers hardware-backed storage when available"
                ),
                OperationCapability(
                    operation = KmsProviderOperation.ATTESTATION,
                    supported = false
                )
            ),

            // Key type support
            supportedKeyTypes = arrayOf(KeyTypeMapping.EC, KeyTypeMapping.RSA),
            supportedCurves = arrayOf(Curve.P_256, Curve.P_384, Curve.P_521),

            // Algorithm support - using generic crypto types
            supportedCryptoAlgorithms = arrayOf(CryptoAlg.ECDSA, CryptoAlg.RSA),
            supportedDigestAlgorithms = arrayOf(DigestAlg.SHA256, DigestAlg.SHA384, DigestAlg.SHA512),
            signatureAlgorithms = arrayOf(
                SignatureAlgorithm.ECDSA_SHA256,
                SignatureAlgorithm.ECDSA_SHA384,
                SignatureAlgorithm.ECDSA_SHA512,
                SignatureAlgorithm.RSA_RAW,
                SignatureAlgorithm.RSA_SHA256,
                SignatureAlgorithm.RSA_SHA384,
                SignatureAlgorithm.RSA_SHA512,
                SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
                SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
                SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
                SignatureAlgorithm.RSA_SSA_PSS_RAW_MGF1
            ),
            contentEncryptionAlgorithms = emptyArray(),

            // Additional capabilities
            supportsX509 = false,
            supportsAttestation = false,
            supportsHardwareBacking = true,

            // Public key resolution
            supportsPublicKeyResolution = true,
            resolutionMethods = emptyArray()
        )
    }

    /**
     * Returns an array of supported elliptic curves for cryptographic operations.
     *
     * @return An array of CurveMapping objects representing the supported elliptic curves.
     */
    @Deprecated("Use getCapabilities().supportedCurves instead")
    override fun supportedCurves(): Array<Curve> = getCapabilities().supportedCurves

    /**
     * Checks if the provided elliptic curve is supported by the SoftwareCryptoProvider.
     *
     * @param curve The elliptic curve to be checked.
     * @return True if the curve is supported, false otherwise.
     */
    override fun isSupportedCurve(curve: Curve): Boolean = getCapabilities().supportedCurves.contains(curve)

    /**
     * Returns an array of supported hash algorithms.
     *
     * @return An array containing the supported HashAlgorithm values: SHA256, SHA384, and SHA512.
     */
    @Deprecated("Use getCapabilities().supportedDigestAlgorithms instead")
    override fun supportedDigests(): Array<DigestAlg> = getCapabilities().supportedDigestAlgorithms

    /**
     * Generates a cryptographic key pair based on the provided elliptic curve.
     *
     * @param curve The elliptic curve mapping used to generate the key pair.
     * @return A `CryptoProviderKeyPair` object containing the generated key pair
     *         with their respective JWK and COSE representations.
     */


    override suspend fun generateKeyAsync(
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        certificateOptions: CertificateOptions?

    ): ManagedKeyPair {
        if (certificateOptions != null) throw IllegalArgumentException("Certificate options are not yet supported by MobileKmsProviderImpl")
        val alias = alias ?: Uuid.v4String()
        val keyUse = use ?: JwkUse.sig
        val algMapping = alg ?: SignatureAlgorithm.ECDSA_SHA256
        val curve = algMapping.curve
        // Only two keytypes for now
        val keyType = if (algMapping.cryptoAlgorithm == CryptoAlg.RSA) KeyTypeMapping.RSA else KeyTypeMapping.EC


        val keyOpsMapping = keyOperations ?: arrayOf(KeyOperations.SIGN)
        val privateJwk: Jwk
        val publicJwk: Jwk
        val signer: Signer.WithAlias
        if (keyType === KeyTypeMapping.EC) {
            require(curve !== null) { "Curve must be provided for EC key type" }
            // Only EcDSA curves for now.
            checkSupportedEcdsaCurve(curve)
            val curveImpl = resolveEcdsaSignumCurve(curve)
            signer = provider.createSigningKey(alias = alias) {
                ec {
                    this.curve = curveImpl
                    digests = setOf(algMapping.digestAlgorithm?.toSignumAlgorithm() ?: Digest.SHA256)
                }
                hardware {
                    backing = PREFERRED
                }
            }.getOrThrow()

        } else {
            val (bits, digest) = when (algMapping) {
                SignatureAlgorithm.RSA_SHA384, SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1 -> 3072 to Digest.SHA384
                SignatureAlgorithm.RSA_SHA512, SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1 -> 4096 to Digest.SHA512
                else -> 2048 to Digest.SHA256
            }
            signer = provider.createSigningKey(alias = alias) {
                rsa {
                    this.bits = bits
                    digests = setOf(digest)
                }
                hardware {
                    backing = PREFERRED
                }
            }.getOrThrow()
        }

        // TODO: Probably best to remove private key exposure from this KMS
        privateJwk = signer.publicKey.toJwk()
        publicJwk = signer.publicKey.toJwk()
        val kid = publicJwk.kid ?: generateJwkThumbprint(publicJwk)
        val privateCoseKey = CoseJoseKeyMappingService.toCoseKey(privateJwk)
        val publicCoseKey = CoseJoseKeyMappingService.toCoseKey(publicJwk)

        val managedKeyPair = ManagedKeyPair(
            providerId = id,
            kid = kid,
            alias = alias,
            jose = JoseKeyPair(if (exposePrivateKeysDuringGeneration) privateJwk.copy(kid = kid) else null, publicJwk.copy(kid = kid)),
            cose = CoseKeyPair(
                if (exposePrivateKeysDuringGeneration) privateCoseKey.copy(kid = kid.toCborByteString(Encoding.UTF8)) else null,
                publicCoseKey.copy(kid = kid.toCborByteString(Encoding.UTF8))
            )
        )
        val keyInfo: KeyInfoType<Jwk> = KeyInfo(
            key = publicJwk,
            keyVisibility = KeyVisibility.PUBLIC,
            keyType = keyType,
            alias = managedKeyPair.alias,
            providerId = managedKeyPair.providerId,
            kid = kid,
            x5c = publicJwk.x5c,
            signatureAlgorithm = publicJwk.getSignatureAlgorithm() ?: algMapping
        )
        if (config.persistKeysDuringGeneration) {
            lookupStore?.storeKey(
                ResolvedKeyInfo.fromKeyInfo(keyInfo, publicJwk), alias = managedKeyPair.alias, providerId = managedKeyPair.providerId
            )
        }

        return managedKeyPair
    }

    private suspend fun keyInfoToBytesWithKeystoreLookup(keyInfo: KeyInfoType<*>): DerKmpKeyInfoContext {
        val managedKeyInfo = lookupStore?.getKey(keyInfo)
        requireNotNull(managedKeyInfo) { "Key ${keyInfo.alias} not found in keystore" }
        return when (managedKeyInfo.keyType) {
            KeyTypeMapping.EC -> {
                keyInfoToEcdsaDerKmpContext(keyInfo, managedKeyInfo)
            }

            KeyTypeMapping.RSA -> {
                keyInfoToRSADerKmpContext(keyInfo, resolver = { managedKeyInfo })
            }

            else -> throw IllegalArgumentException("Key type ${managedKeyInfo.keyType} not supported")

        }

    }

    /**
     * Generates a signature for the given input data using the provided key information.
     *
     * @param keyInfo Information about the signing key.
     * @param input The data to be signed.
     * @return The generated signature as a byte array.
     * @throws IllegalArgumentException If the private key is not provided or not supported.
     */
    override suspend fun createRawSignature(keyInfo: KeyInfoType<*>, input: ByteArray, requireX5Chain: Boolean): ByteArray {
        val managedKeyInfo = keyInfo as? ManagedKeyInfoType ?: lookupStore?.getKey(keyInfo)
        val signatureAlgorithm = managedKeyInfo?.signatureAlgorithm ?: keyInfo.signatureAlgorithm
        ?: throw IllegalArgumentException("No signature algorithm found or supplied for $keyInfo")
        val digest = signatureAlgorithm.digestAlgorithm?.toSignumAlgorithm() ?: Digest.SHA256
        val alias = managedKeyInfo?.alias ?: keyInfo.alias ?: throw IllegalArgumentException("No key found for $keyInfo")
        val signer = provider.getSignerForKey(alias = alias, {
            if (signatureAlgorithm.cryptoAlgorithm == CryptoAlg.RSA) {
                rsa {
                    this.digest = digest
                    this.padding = if (signatureAlgorithm.maskGenFunction != null) RSAPadding.PSS else RSAPadding.PKCS1
                }
            } else {
                ec {
                    this.digest = digest
                }
            }
        }).getOrThrow()
        val result = signer.sign(data = input).signature.rawByteArray
        return result
    }


    /**
     * Verifies the signature of the input data using the provided key information.
     *
     * @param keyInfo Key information that includes the public key and other details.
     * @param input The original data which the signature is supposed to represent.
     * @param signature The signature that needs to be verified.
     * @return true if the signature is valid, false otherwise.
     * @throws IllegalArgumentException if a private key is used to verify the signature.
     */
    override suspend fun isValidRawSignature(keyInfo: KeyInfoType<*>, input: ByteArray, signature: ByteArray): Boolean {
        val keyInfo = keyInfo as? ManagedKeyInfoType ?: lookupStore?.getKey(keyInfo)
        val key = keyInfo?.key ?: throw IllegalArgumentException("No key found for $keyInfo")
        val jwk = CoseJoseKeyMappingService.toJoseJwk(key)
        val publicKey: CryptoPublicKey = jwk.toSignumPublicKey()

        val signatureAlgorithm = keyInfo.signatureAlgorithm ?: key.getSignatureAlgorithm() ?: SignatureAlgorithm.ECDSA_SHA256
        val digest = signatureAlgorithm.digestAlgorithm?.toSignumAlgorithm() ?: Digest.SHA256
        val verifier = signatureAlgorithm.toSignumAlgorithm().verifierFor(publicKey).getOrThrow()
        val signatureInput = SignatureInput(input)
        val signature = if (signatureAlgorithm.cryptoAlgorithm == CryptoAlg.ECDSA) CryptoSignature.EC.fromRawBytes(
            curve = signatureAlgorithm.curve?.jose?.toSignum() ?: ECCurve.SECP_256_R_1, input = signature
        ) else CryptoSignature.RSA(signature)
        val result = verifier.verify(data = signatureInput, sig = signature)
        return result.isSuccess
    }

    override suspend fun createSignature(signInput: SignInput, keyInfo: KeyInfoType<*>?, signatureAlgorithm: SignatureAlgorithm?): SignOutput {
        TODO("Not yet implemented")
    }


    override suspend fun isValidSignature(signInput: SignInput, signature: Signature): Boolean {
        TODO("Not yet implemented")
    }

    override fun supportedKeyTypes(): Array<KeyTypeMapping> = arrayOf(KeyTypeMapping.EC, KeyTypeMapping.RSA)

    /**
     * Returns an array of supported EcDSA and RSA algorithm mappings.
     *
     * @return An array containing AlgorithmMappings
     */
    override fun supportedSignatureAlgorithms(): Array<SignatureAlgorithm> = arrayOf(
        SignatureAlgorithm.ECDSA_SHA256,
        SignatureAlgorithm.ECDSA_SHA384,
        SignatureAlgorithm.ECDSA_SHA512,
        SignatureAlgorithm.RSA_RAW,
        SignatureAlgorithm.RSA_SHA256,
        SignatureAlgorithm.RSA_SHA384,
        SignatureAlgorithm.RSA_SHA512,
        SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
        SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
        SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
        SignatureAlgorithm.RSA_SSA_PSS_RAW_MGF1
    )

    override val settings: KeyProviderSettings? = null

    override suspend fun listKeys(): Array<ManagedKeyInfoType<*>> {
        TODO("Not implemented for mobile KMS")
    }

    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        val alias = alias(keyInfo)
        val signingKeyResult = provider.getSignerForKey(alias = alias)
        if (signingKeyResult.isFailure) throw IllegalArgumentException("No key found for $keyInfo")
        val signingKey = signingKeyResult.getOrThrow()
        val jwk = signingKey.publicKey.toJwk()
        return ManagedKeyInfo<JwkType>(alias = alias, providerId = id, resolvedKeyInfo = ResolvedKeyInfo.fromKeyInfo(keyInfo, jwk))
    }

    override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?
    ): ManagedKeyInfoType<*> {
        TODO("Not implemented, given the mobile KMS stores during generation")
    }

    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        val alias = alias(keyInfo)
        try {
            provider.deleteSigningKey(alias)
        } catch (e: Exception) {
            return false
        }
        return true
    }

    override fun keyVisibility(): KeyVisibility = KeyVisibility.PUBLIC

    private fun alias(keyInfo: KeyInfoType<*>): String =
        keyInfo.alias ?: keyInfo.kid ?: keyInfo.key?.getKeyId(true) ?: throw IllegalArgumentException("No key found for $keyInfo")

    // Encryption operations are not supported by mobile KMS provider
    override suspend fun encrypt(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?
    ): EncryptionResult {
        throw UnsupportedOperationException("Encryption operations are not supported by mobile KMS provider")
    }

    override suspend fun decrypt(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?
    ): ByteArray {
        throw UnsupportedOperationException("Decryption operations are not supported by mobile KMS provider")
    }

    override suspend fun wrapKey(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): ByteArray {
        throw UnsupportedOperationException("Key wrapping operations are not supported by mobile KMS provider")
    }

    override suspend fun unwrapKey(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): ByteArray {
        throw UnsupportedOperationException("Key unwrapping operations are not supported by mobile KMS provider")
    }

    override suspend fun performKeyAgreement(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int?
    ): ByteArray {
        throw UnsupportedOperationException("Key agreement operations are not supported by mobile KMS provider")
    }
}
