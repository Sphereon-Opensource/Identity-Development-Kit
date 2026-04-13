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

import aws.sdk.kotlin.services.kms.KmsClient
import aws.sdk.kotlin.services.kms.model.*
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.CoseKeyPair
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.JoseKeyPair
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.interop.toDerEcdsaPublicKeyBytes
import com.sphereon.crypto.core.kms.CertificateOptions
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.EncryptionResult
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import com.sphereon.crypto.core.kms.PredefinedKmsProviderTypes
import com.sphereon.crypto.core.x509.Certificate
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*

actual class AwsKmsCryptoProvider actual constructor(
    settings: KeyProviderSettings
) : BaseAwsKmsCryptoProvider(settings) {

    private suspend fun getAWSKmsClient(): KmsClient {
        return withContext(Dispatchers.IO) {
            KmsClient {
                region = awsConfig.region
            }
        }
    }

    override suspend fun generateKeyAsync(
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        certificateOptions: CertificateOptions?
    ): ManagedKeyPair {
        if (certificateOptions != null) throw IllegalArgumentException("Certificate options are not yet supported by AWS KMS")
        val signingAlgorithm = alg ?: SignatureAlgorithm.ECDSA_SHA256

        if (!isSupportedSignatureAlgorithm(signingAlgorithm)) {
            val algName = when (signingAlgorithm) {
                SignatureAlgorithm.ED25519 -> "Ed25519"
                else -> signingAlgorithm.toString()
            }
            throw IllegalArgumentException("Signature algorithm $algName is not supported by AWS KMS")
        }

        // Map signature algorithm to key spec
        val keySpec = when (signingAlgorithm) {
            SignatureAlgorithm.ECDSA_SHA256 -> KeySpec.EccNistP256
            SignatureAlgorithm.ECDSA_SHA384 -> KeySpec.EccNistP384
            SignatureAlgorithm.ECDSA_SHA512 -> KeySpec.EccNistP521
            // RSA PSS algorithms
            SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
            SignatureAlgorithm.RSA_SHA256 -> KeySpec.Rsa2048
            SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
            SignatureAlgorithm.RSA_SHA384 -> KeySpec.Rsa3072
            SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
            SignatureAlgorithm.RSA_SHA512 -> KeySpec.Rsa4096
            else -> throw IllegalArgumentException("Unsupported signature algorithm: $signingAlgorithm")
        }

        // Create key in AWS KMS
        val client = getAWSKmsClient()
        val createKeyResponse = client.createKey(CreateKeyRequest {
            this.keyUsage = KeyUsageType.SignVerify
            this.keySpec = keySpec
        })

        val kid = createKeyResponse.keyMetadata?.keyId
            ?: throw IllegalStateException("Failed to retrieve key ID after creation")

        if (alias != null && (createKeyResponse.keyMetadata?.arn != null || createKeyResponse.keyMetadata?.keyId != null)) {
            // We cannot set the alias right from the start, so we are doing it afterward
            client.createAlias(
                CreateAliasRequest {
                    aliasName = if (alias.startsWith("alias/")) alias else "alias/$alias"
                    targetKeyId = createKeyResponse.keyMetadata?.keyId ?: createKeyResponse.keyMetadata?.arn
                })
        }


        // Get public key in DER format
        val getPublicKeyResponse = client.getPublicKey(GetPublicKeyRequest {
            keyId = kid
        })
        val publicKeyDer: ByteArray = getPublicKeyResponse.publicKey
            ?: throw IllegalStateException("Public key not found")

        return toManagedKeyPair(publicKeyDer, kid, alias ?: kid, signingAlgorithm)
    }

    private fun toManagedKeyPair(publicKeyDer: ByteArray, kid: String, alias: String, signatureAlgorithm: SignatureAlgorithm? = null): ManagedKeyPair {
        // Create JWK from public key
        val jwk = createJwkFromPublicKey(publicKeyDer, kid, signatureAlgorithm)
        val joseKeyPair = JoseKeyPair(null, jwk)

        return ManagedKeyPair(
            providerId = id,
            alias = alias,
            kid = kid,
            jose = joseKeyPair,
            cose = CoseKeyPair(null, CoseJoseKeyMappingService.toCoseKey(jwk))
        )
    }

    override suspend fun createRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean
    ): ByteArray {
        var algorithm = keyInfo.signatureAlgorithm
        if (algorithm == null) {
            // No alg supplied. Although the AWS SDK lists the signature param as optional it really is not. So let's lookup the key in this case
            val key = getKey(keyInfo)
            algorithm = key.signatureAlgorithm
                ?: throw IllegalArgumentException("Key does not have a signature algorithm set")
        }

        val client = getAWSKmsClient()
        val signResponse = client.sign(SignRequest {
            this.keyId = determineAwsKeyId(keyInfo)
            message = input
            this.signingAlgorithm = algorithm.toSigningAlgorithmSpec()
        })

        return signResponse.signature!!
    }

    /**
     * Verifies a raw signature using a key stored in Azure Key Vault.
     */
    override suspend fun isValidRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray
    ): Boolean {
        val algorithm = keyInfo.signatureAlgorithm

        val client = getAWSKmsClient()
        try {
            val verifyResponse = client.verify(VerifyRequest {
                this.keyId = determineAwsKeyId(keyInfo)
                message = input
                this.signature = signature
                this.signingAlgorithm = algorithm?.toSigningAlgorithmSpec()
            })
            return verifyResponse.signatureValid
        } catch (e: KmsInvalidSignatureException) {
            // Signature validation failed
            return false
        }
    }

    private fun SignatureAlgorithm.toSigningAlgorithmSpec(): SigningAlgorithmSpec {
        return when (this) {
            SignatureAlgorithm.ECDSA_SHA256 -> SigningAlgorithmSpec.EcdsaSha256
            SignatureAlgorithm.ECDSA_SHA384 -> SigningAlgorithmSpec.EcdsaSha384
            SignatureAlgorithm.ECDSA_SHA512 -> SigningAlgorithmSpec.EcdsaSha512
            // RSA PSS (RSASSA-PSS)
            SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1 -> SigningAlgorithmSpec.RsassaPssSha256
            SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1 -> SigningAlgorithmSpec.RsassaPssSha384
            SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1 -> SigningAlgorithmSpec.RsassaPssSha512
            // RSA PKCS#1 v1.5 - use fromValue as enum names vary by SDK version
            SignatureAlgorithm.RSA_SHA256 -> SigningAlgorithmSpec.fromValue("RSASSA_PKCS1_V1_5_SHA_256")
            SignatureAlgorithm.RSA_SHA384 -> SigningAlgorithmSpec.fromValue("RSASSA_PKCS1_V1_5_SHA_384")
            SignatureAlgorithm.RSA_SHA512 -> SigningAlgorithmSpec.fromValue("RSASSA_PKCS1_V1_5_SHA_512")
            else -> throw IllegalArgumentException("Unsupported signature algorithm: $this")
        }
    }

    /**
     * Creates a JWK (JSON Web Key) representation from the DER-encoded public key.
     *
     * This implementation supports EC curves P-256, P-384, P-521 and RSA keys.
     *
     * @param publicKeyDer DER-encoded public key bytes.
     * @param keyId The key identifier to embed in the JWK.
     * @param signatureAlgorithm Optional signature algorithm hint for RSA keys.
     * @return A Jwk representing the public key.
     * @throws IllegalArgumentException if the key is not a valid EC or RSA key.
     */
    fun createJwkFromPublicKey(publicKeyDer: ByteArray, keyId: String, signatureAlgorithm: SignatureAlgorithm? = null): Jwk {
        val keySpec = X509EncodedKeySpec(publicKeyDer)
        val encoder = Base64.getUrlEncoder().withoutPadding()

        // Convert BigInteger to a fixed-length byte array (unsigned representation)
        fun bigIntToFixedLengthBytes(value: BigInteger, length: Int): ByteArray {
            val bytes = value.toByteArray()
            return when {
                bytes.size == length -> bytes
                bytes.size == length + 1 && bytes[0].toInt() == 0 -> bytes.copyOfRange(1, bytes.size)
                bytes.size < length -> {
                    // Left pad with zeros if necessary
                    ByteArray(length).apply {
                        System.arraycopy(bytes, 0, this, length - bytes.size, bytes.size)
                    }
                }
                else -> bytes.copyOfRange(bytes.size - length, bytes.size)
            }
        }

        // Try EC first, then RSA
        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            val publicKey = keyFactory.generatePublic(keySpec) as ECPublicKey

            // Extract the affine coordinates (x and y)
            val ecPoint = publicKey.w
            val params = publicKey.params
            val fieldSize = params.curve.field.fieldSize
            val coordinateLength = (fieldSize + 7) / 8

            val xBytes = bigIntToFixedLengthBytes(ecPoint.affineX, coordinateLength)
            val yBytes = bigIntToFixedLengthBytes(ecPoint.affineY, coordinateLength)

            val xEncoded = encoder.encodeToString(xBytes)
            val yEncoded = encoder.encodeToString(yBytes)

            // Map the field size to the corresponding JWK "crv" value
            val (crv, alg) = when (fieldSize) {
                256 -> JwaCurve.P_256 to JwaAlgorithm.ES256
                384 -> JwaCurve.P_384 to JwaAlgorithm.ES384
                521 -> JwaCurve.P_521 to JwaAlgorithm.ES512
                else -> throw IllegalArgumentException("Unsupported EC curve with field size $fieldSize")
            }

            Jwk.Builder()
                .withKid(keyId)
                .withKty(JwaKeyType.EC)
                .withAlg(alg)
                .withCrv(crv)
                .withX(xEncoded)
                .withY(yEncoded)
                .withKeyOps(arrayOf(JoseKeyOperations.SIGN, JoseKeyOperations.VERIFY))
                .build()
        } catch (e: Exception) {
            // Try RSA
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec) as java.security.interfaces.RSAPublicKey

            val modulusBytes = publicKey.modulus.toByteArray().let {
                if (it[0].toInt() == 0) it.copyOfRange(1, it.size) else it
            }
            val exponentBytes = publicKey.publicExponent.toByteArray()

            val nEncoded = encoder.encodeToString(modulusBytes)
            val eEncoded = encoder.encodeToString(exponentBytes)

            // Determine RSA algorithm based on signature algorithm hint or default to PS256
            val alg = when (signatureAlgorithm) {
                SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1 -> JwaAlgorithm.PS256
                SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1 -> JwaAlgorithm.PS384
                SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1 -> JwaAlgorithm.PS512
                SignatureAlgorithm.RSA_SHA256 -> JwaAlgorithm.RS256
                SignatureAlgorithm.RSA_SHA384 -> JwaAlgorithm.RS384
                SignatureAlgorithm.RSA_SHA512 -> JwaAlgorithm.RS512
                else -> JwaAlgorithm.PS256 // Default to PS256
            }

            Jwk.Builder()
                .withKid(keyId)
                .withKty(JwaKeyType.RSA)
                .withAlg(alg)
                .withN(nEncoded)
                .withE(eEncoded)
                .withKeyOps(arrayOf(JoseKeyOperations.SIGN, JoseKeyOperations.VERIFY))
                .build()
        }
    }


    override suspend fun listKeys(): Array<ManagedKeyInfoType<*>> {
        return run {
            val client = getAWSKmsClient()
            client.listKeys().keys?.map { entry: KeyListEntry ->
                getKey(KeyInfo<JwkType>(kid = entry.keyId!!))
            }
        }.orEmpty().toTypedArray()


    }

    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        return getAWSKmsClient().use { client ->

            client.getPublicKey(GetPublicKeyRequest { keyId = determineAwsKeyId(keyInfo) }).publicKey?.let {

                toManagedKeyPair(
                    it,
                    keyInfo.kid!!,
                    keyInfo.alias!!
                ).joseToManagedKeyInfo()
            }
                ?: throw IllegalArgumentException(
                    "Key with ID ${keyInfo.alias} not found in AWS KMS"
                )
        }
    }

    override suspend fun storeKey(keyInfo: ResolvedKeyInfoType<*>, providerId: String, alias: String, certChain: Array<Certificate>?): ManagedKeyInfoType<*> {
        throw UnsupportedOperationException("AWS KMS does not support storing keys, it generates them only")
    }

    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        return getAWSKmsClient().use { client ->
            val keyId = if (keyInfo.kid != null) keyInfo.kid else getKey(keyInfo).kid ?: determineAwsKeyId(keyInfo)  // We fetch the key first, since deletion can only happen via kid and not an alias!
            client.scheduleKeyDeletion(ScheduleKeyDeletionRequest {
                this.keyId = keyId
                pendingWindowInDays = 7
            }).keyState == KeyState.PendingDeletion
        }
    }

    override fun keyVisibility(): KeyVisibility {
        return KeyVisibility.PUBLIC
    }

    override val kmsProviderType: String
        get() = PredefinedKmsProviderTypes.AWS_KMS.kmsProviderType

    /**
     * Encrypts plaintext using the specified key and algorithm.
     *
     * AWS KMS supports symmetric encryption (AES-256-GCM) for symmetric keys
     * and asymmetric encryption (RSA-OAEP) for RSA keys.
     *
     * @param keyInfo Information about the key to use for encryption
     * @param plaintext The data to encrypt
     * @param algorithm The content encryption algorithm to use
     * @param additionalAuthenticatedData Optional AAD for authenticated encryption (not supported by AWS KMS directly)
     * @return EncryptionResult containing ciphertext, IV, and authentication tag
     */
    override suspend fun encrypt(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?
    ): EncryptionResult {
        val client = getAWSKmsClient()

        // AWS KMS encrypt operation - uses symmetric key encryption
        val encryptResponse = client.encrypt(EncryptRequest {
            this.keyId = determineAwsKeyId(keyInfo)
            this.plaintext = plaintext
            // AWS KMS uses symmetric encryption by default (AES-256-GCM)
            // The algorithm parameter maps to the key's encryption algorithm
            this.encryptionAlgorithm = algorithm.toAwsEncryptionAlgorithm()
        })

        return EncryptionResult(
            ciphertext = encryptResponse.ciphertextBlob ?: throw IllegalStateException("Encryption failed - no ciphertext returned"),
            iv = ByteArray(algorithm.ivLength), // AWS KMS handles IV internally
            authTag = ByteArray(algorithm.tagLength) // AWS KMS handles auth tag internally
        )
    }

    /**
     * Decrypts ciphertext using the specified key and algorithm.
     *
     * @param keyInfo Information about the key to use for decryption
     * @param ciphertext The encrypted data
     * @param algorithm The content encryption algorithm used for encryption
     * @param iv The initialization vector used during encryption (not used by AWS KMS - handled internally)
     * @param authTag The authentication tag for verification (not used by AWS KMS - handled internally)
     * @param additionalAuthenticatedData Optional AAD used during encryption
     * @return The decrypted plaintext
     */
    override suspend fun decrypt(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?
    ): ByteArray {
        val client = getAWSKmsClient()

        val decryptResponse = client.decrypt(DecryptRequest {
            this.keyId = determineAwsKeyId(keyInfo)
            this.ciphertextBlob = ciphertext
            this.encryptionAlgorithm = algorithm.toAwsEncryptionAlgorithm()
        })

        return decryptResponse.plaintext ?: throw IllegalStateException("Decryption failed - no plaintext returned")
    }

    /**
     * Wraps (encrypts) a key using the specified wrapping key and algorithm.
     *
     * AWS KMS uses the encrypt operation for key wrapping with RSA-OAEP algorithms.
     *
     * @param wrappingKeyInfo Information about the key to use for wrapping
     * @param keyToWrap The key material to wrap
     * @param algorithm The key wrap algorithm to use
     * @return The wrapped key bytes
     */
    override suspend fun wrapKey(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): ByteArray {
        val client = getAWSKmsClient()

        // AWS KMS uses encrypt operation for key wrapping
        val encryptResponse = client.encrypt(EncryptRequest {
            this.keyId = determineAwsKeyId(wrappingKeyInfo)
            this.plaintext = keyToWrap
            this.encryptionAlgorithm = algorithm.toAwsEncryptionAlgorithm()
        })

        return encryptResponse.ciphertextBlob ?: throw IllegalStateException("Key wrapping failed - no ciphertext returned")
    }

    /**
     * Unwraps (decrypts) a wrapped key using the specified unwrapping key and algorithm.
     *
     * @param unwrappingKeyInfo Information about the key to use for unwrapping
     * @param wrappedKey The wrapped key bytes
     * @param algorithm The key wrap algorithm used during wrapping
     * @return The unwrapped key material
     */
    override suspend fun unwrapKey(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): ByteArray {
        val client = getAWSKmsClient()

        val decryptResponse = client.decrypt(DecryptRequest {
            this.keyId = determineAwsKeyId(unwrappingKeyInfo)
            this.ciphertextBlob = wrappedKey
            this.encryptionAlgorithm = algorithm.toAwsEncryptionAlgorithm()
        })

        return decryptResponse.plaintext ?: throw IllegalStateException("Key unwrapping failed - no plaintext returned")
    }

    /**
     * Performs ECDH key agreement using AWS KMS DeriveSharedSecret API.
     *
     * AWS KMS supports ECDH key agreement (as of June 2024) via the DeriveSharedSecret API.
     * Note: This requires EC keys created with KeyUsage of KEY_AGREEMENT in AWS KMS.
     * Keys created by generateKeyAsync() are for signing (SIGN_VERIFY) and cannot be used here.
     * You need to create KEY_AGREEMENT keys via AWS Console or SDK directly.
     *
     * @param privateKeyInfo Information about the KMS key to use as the private key (must have KEY_AGREEMENT usage)
     * @param publicKeyInfo Information about the public key (must be a JWK with EC key type)
     * @param algorithm The key agreement algorithm to use (ECDH_ES)
     * @param keyDataLen Optional length for the derived key data (not used by AWS KMS raw derivation)
     * @return The derived shared secret bytes
     */
    override suspend fun performKeyAgreement(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int?
    ): ByteArray {
        if (algorithm != KeyAgreementAlgorithm.ECDH_ES) {
            throw UnsupportedOperationException("AWS KMS only supports ECDH key agreement, not $algorithm")
        }

        // Get the public key and convert to DER format
        val publicKey = publicKeyInfo.key
            ?: throw IllegalArgumentException("Public key is required for key agreement")

        // Convert JWK to DER-encoded public key bytes
        val publicKeyDer = when (publicKey) {
            is JwkType -> toDerEcdsaPublicKeyBytes(publicKey)
            else -> throw IllegalArgumentException("Public key must be a JWK for AWS KMS key agreement")
        }

        val client = getAWSKmsClient()

        val deriveResponse = client.deriveSharedSecret(DeriveSharedSecretRequest {
            this.keyId = determineAwsKeyId(privateKeyInfo)
            this.publicKey = publicKeyDer
            this.keyAgreementAlgorithm = KeyAgreementAlgorithmSpec.fromValue("ECDH")
        })

        return deriveResponse.sharedSecret
            ?: throw IllegalStateException("Key agreement failed - no shared secret returned")
    }

    /**
     * Converts ContentEncryptionAlgorithm to AWS KMS EncryptionAlgorithmSpec.
     * AWS KMS symmetric keys use SYMMETRIC_DEFAULT (AES-256-GCM).
     */
    private fun ContentEncryptionAlgorithm.toAwsEncryptionAlgorithm(): EncryptionAlgorithmSpec = when (this) {
        ContentEncryptionAlgorithm.A128GCM,
        ContentEncryptionAlgorithm.A192GCM,
        ContentEncryptionAlgorithm.A256GCM,
        ContentEncryptionAlgorithm.A128CBC_HS256,
        ContentEncryptionAlgorithm.A192CBC_HS384,
        ContentEncryptionAlgorithm.A256CBC_HS512 -> EncryptionAlgorithmSpec.SymmetricDefault
    }

    /**
     * Converts KeyWrapAlgorithm to AWS KMS EncryptionAlgorithmSpec.
     */
    private fun KeyWrapAlgorithm.toAwsEncryptionAlgorithm(): EncryptionAlgorithmSpec = when (this) {
        KeyWrapAlgorithm.RSA_OAEP -> EncryptionAlgorithmSpec.RsaesOaepSha1
        KeyWrapAlgorithm.RSA_OAEP_256 -> EncryptionAlgorithmSpec.RsaesOaepSha256
        KeyWrapAlgorithm.RSA1_5 -> throw UnsupportedOperationException("RSA1_5 not supported by AWS KMS - use RSA_OAEP instead")
        KeyWrapAlgorithm.RSA_OAEP_384,
        KeyWrapAlgorithm.RSA_OAEP_512 -> throw UnsupportedOperationException("RSA-OAEP-384/512 not supported by AWS KMS")
        KeyWrapAlgorithm.A128KW,
        KeyWrapAlgorithm.A192KW,
        KeyWrapAlgorithm.A256KW -> EncryptionAlgorithmSpec.SymmetricDefault
        KeyWrapAlgorithm.A128GCMKW,
        KeyWrapAlgorithm.A192GCMKW,
        KeyWrapAlgorithm.A256GCMKW -> throw UnsupportedOperationException("AES-GCM key wrap not supported by AWS KMS")
        KeyWrapAlgorithm.DIR -> throw UnsupportedOperationException("Direct key agreement not supported for key wrapping")
    }
}

fun determineAwsKeyId(keyInfo: KeyInfoType<*>): String {
    val keyIdArg = keyInfo.alias ?: keyInfo.kid ?: throw IllegalArgumentException("KMS key reference is required")
    return if (keyInfo.alias == keyInfo.kid || keyInfo.alias == null || keyIdArg.startsWith("alias/")) keyIdArg else "alias/$keyIdArg"
}

