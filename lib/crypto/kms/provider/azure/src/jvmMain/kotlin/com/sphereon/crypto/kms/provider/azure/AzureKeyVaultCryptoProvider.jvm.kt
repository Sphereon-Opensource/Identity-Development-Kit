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

/**
 * Azure Key Vault implementation of the CryptoProvider interface for JVM platforms.
 *
 * This class provides integration with Azure Key Vault services for cryptographic operations
 * including key generation, storage, retrieval, signing, and signature verification.
 * It supports both regular Azure Key Vault and Azure Managed HSM instances.
 *
 * The provider supports the following operations:
 * - Key generation (EC keys with various curves)
 * - Raw signature creation and verification
 * - Structured signature creation and verification
 * - Key listing, retrieval, storage, and deletion
 * - Certificate-based operations when using standard Key Vault
 *
 * The implementation uses Azure SDK for Java to communicate with Azure Key Vault
 * and supports various authentication methods including client credentials,
 * certificate-based authentication, interactive browser authentication,
 * and username/password authentication.
 *
 * @property config Configuration options for connecting to Azure Key Vault
 * @property settings Provider-specific settings
 *
 */

import com.azure.core.exception.ResourceNotFoundException
import com.azure.core.http.policy.HttpLogDetailLevel
import com.azure.core.http.policy.HttpLogOptions
import com.azure.core.http.policy.HttpLoggingPolicy
import com.azure.core.http.policy.RetryOptions
import com.azure.core.http.policy.RetryPolicy
import com.azure.security.keyvault.certificates.CertificateClientBuilder
import com.azure.security.keyvault.certificates.CertificateServiceVersion
import com.azure.security.keyvault.keys.KeyAsyncClient
import com.azure.security.keyvault.keys.KeyClientBuilder
import com.azure.security.keyvault.keys.KeyServiceVersion
import com.azure.security.keyvault.keys.models.CreateEcKeyOptions
import com.azure.security.keyvault.keys.models.CreateRsaKeyOptions
import com.azure.security.keyvault.keys.models.ImportKeyOptions
import com.azure.security.keyvault.keys.models.JsonWebKey
import com.azure.security.keyvault.keys.models.KeyCurveName
import com.azure.security.keyvault.keys.models.KeyOperation
import com.azure.security.keyvault.keys.models.KeyProperties
import com.azure.security.keyvault.keys.models.KeyType
import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm as AzureSignatureAlgorithm
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm as AzureKeyWrapAlgorithm
import com.azure.security.keyvault.keys.cryptography.models.EncryptParameters
import com.azure.security.keyvault.keys.cryptography.models.DecryptParameters
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.toKeyReference
import com.sphereon.crypto.core.kms.EncryptionResult
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import java.security.SecureRandom
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import com.sphereon.core.compat.Uuid
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.SignClientException
import com.sphereon.crypto.core.generic.CoseKeyPair
import com.sphereon.crypto.core.generic.CryptoAlg
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.generic.JoseKeyPair
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.jose.generateJwkThumbprint
import com.sphereon.crypto.core.kms.CertificateOptions
import com.sphereon.crypto.core.kms.PredefinedKmsProviderTypes
import com.sphereon.crypto.core.kms.command.SignatureEncoding
import com.sphereon.crypto.core.kms.command.SignatureEncodingCodec
import com.sphereon.crypto.core.sign.model.SignInput
import com.sphereon.crypto.core.sign.model.SignOutput
import com.sphereon.crypto.core.sign.model.SignOutputData
import com.sphereon.crypto.core.sign.model.Signature
import com.sphereon.crypto.core.sign.model.SignatureLevel
import com.sphereon.crypto.core.sign.model.SigningMode
import com.sphereon.crypto.core.x509.Certificate

/**
 * Implementation of the Azure Key Vault Crypto Provider for JVM environments.
 * Handles key creation, signature operations, key listing, and importing via the Azure SDK for JS.
 */
actual class AzureKeyVaultCryptoProvider actual constructor(
    config: AzureKmsProviderConfig,
//    settings: KeyProviderSettings
) : BaseAzureKeyvaultCryptoProvider(config/*, settings*/) {

    private val keyClient: KeyAsyncClient = KeyClientBuilder()
        .serviceVersion(KeyServiceVersion.V7_3)
        .vaultUrl(config.keyvaultUrl)
        .clientOptions(config.toClientOptions())
        .addPolicy(HttpLoggingPolicy(HttpLogOptions().setLogLevel(HttpLogDetailLevel.BODY_AND_HEADERS)))
        .retryPolicy(
            if (config.exponentialBackoffRetryOpts == null) null
            else RetryPolicy(RetryOptions(config.exponentialBackoffRetryOpts.toExponentialBackoffOptions()))
        )
        .credential(config.credentialOpts.toTokenCredential(config.tenantId))
        .buildAsyncClient()


    private val hasCertsApi = config.hsmType == HSMType.KEYVAULT

    private val certClient = if (hasCertsApi) {
        with(config) {
            CertificateClientBuilder()
                .serviceVersion(CertificateServiceVersion.V7_3)
                .vaultUrl(keyvaultUrl)
                .clientOptions(toClientOptions())
                .retryPolicy(
                    if (exponentialBackoffRetryOpts == null) null
                    else RetryPolicy(RetryOptions(exponentialBackoffRetryOpts.toExponentialBackoffOptions()))
                )
                .credential(credentialOpts.toTokenCredential(tenantId))
                .buildAsyncClient()
        }
    } else null

    /**
     * Generates a new key pair in Azure Key Vault.
     *
     * @param alias The key reference to use for the key pair. If null, a new key reference will be generated.
     * @param keyOperations The key operations that the key pair should support.
     * @param alg The signature algorithm to use for the key pair.
     * @return A ManagedKeyPair object containing the generated key pair.
     * @throws IllegalArgumentException if the curve is not supported by Azure Key Vault.
     * @throws SignClientException if the key pair could not be generated.
     */
    override suspend fun generateKeyAsync(
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        certificateOptions: CertificateOptions?
    ): ManagedKeyPair {
        require(certificateOptions == null) { "Certificate options are not yet supported for Azure Keyvault" }
        val signatureAlgorithm = alg ?: SignatureAlgorithm.ECDSA_SHA256

        require(isSupportedSignatureAlgorithm(signatureAlgorithm)) {
            "Signature algorithm ${signatureAlgorithm.cryptoAlgorithm.name} is not supported by Azure Key Vault"
        }

        val keyName = alias ?: "key-${Uuid.v4String()}"
        val operations: Array<KeyOperation> = (keyOperations ?: arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY))
            .map {
                it.toAzureKeyOperation()
            }
            .toTypedArray()

        val keyVaultKey = when (signatureAlgorithm.cryptoAlgorithm) {
            CryptoAlg.RSA -> {
                // Create RSA key
                val createRsaKeyOptions = CreateRsaKeyOptions(keyName)
                    .setKeySize(2048) // Default RSA key size
                    .setKeyOperations(*operations)

                keyClient.createRsaKey(createRsaKeyOptions)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorMap(TimeoutException::class.java) {
                        RuntimeException("Azure operation timed out, check your Azure env vars")
                    }
                    .awaitSingle()
            }
            else -> {
                // Create EC key (ECDSA)
                val curve = signatureAlgorithm.curve ?: Curve.P_256

                val createEcKeyOptions = CreateEcKeyOptions(keyName)
                    .setCurveName(curve.toAzureKeyCurveName())
                    .setKeyOperations(*operations)

                keyClient.createEcKey(createEcKeyOptions)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorMap(TimeoutException::class.java) {
                        RuntimeException("Azure operation timed out, check your Azure env vars")
                    }
                    .awaitSingle()
            }
        }

        if (keyVaultKey == null) {
            throw SignClientException("Failed to create key in Azure Key Vault for reference $keyName in vault ${keyClient.vaultUrl}")
        }

        val keyVaultJwk = keyVaultKey.toJwk()
        val kid = keyVaultJwk.kid ?: generateJwkThumbprint(keyVaultJwk)
        val jwk = keyVaultJwk.copy(kid = kid)
        val publicCoseKey = CoseJoseKeyMappingService.toCoseKey(jwk)

        return ManagedKeyPair(
            providerId = id,
            alias = keyVaultKey.name,
            kid = kid,
            jose = JoseKeyPair(null, jwk),
            cose = CoseKeyPair(null, publicCoseKey)
        )

    }

    /**
     * Creates a raw digital signature using the specified key and input data.
     *
     * @param keyInfo Information about the key to use for signing
     * @param input The data to be signed
     * @param requireX5Chain Whether to require an X.509 certificate chain
     * @return The raw signature bytes
     * @throws SignClientException if the key cannot be found or signature creation fails
     */
    override suspend fun createRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean
    ): ByteArray {
        val keyVaultKey = keyClient.getKey(keyInfo.alias).awaitSingleOrNull()
            ?: throw SignClientException("Key not found in Azure Key Vault for reference: ${keyInfo.alias}")

        val cryptoClient = keyClient.getCryptographyAsyncClient(keyInfo.alias)
        val signResult = cryptoClient.sign(keyVaultKey.toSignatureAlgorithm(), hash(keyInfo, input)).awaitSingleOrNull()
            ?: throw SignClientException("Failed to create raw signature for key: ${keyInfo.alias}")

        return signResult.signature
    }

    private fun hash(keyInfo: KeyInfoType<*>, input: ByteArray): ByteArray {
        val digestAlg = DigestAlg.fromValue(
            keyInfo.signatureAlgorithm?.digestAlgorithm?.name
                ?: throw IllegalArgumentException("Digest algorithm is required")
        )
        return hash(digestAlg, input)
    }

    private fun hash(digestAlg: com.sphereon.crypto.core.generic.DigestAlg, input: ByteArray): ByteArray {
        return hash(input, digestAlg)
    }

    /**
     * Verifies a raw signature against the provided input data using the specified key.
     *
     * @param keyInfo Information about the key to use for verification
     * @param input The original data that was signed
     * @param signature The signature bytes to verify
     * @return True if the signature is valid, false otherwise
     * @throws SignClientException if the key cannot be found or verification fails
     */
    override suspend fun isValidRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray
    ): Boolean {
        val keyVaultKey = keyClient.getKey(keyInfo.alias).awaitSingleOrNull()
            ?: throw SignClientException("Key not found in Azure Key Vault for reference: ${keyInfo.alias}")

        val cryptoClient = keyClient.getCryptographyAsyncClient(keyInfo.alias)

        val algorithm = keyVaultKey.toSignatureAlgorithm()
        val verifyResult = cryptoClient.verify(algorithm, hash(keyInfo, input), signature).awaitSingleOrNull()
            ?: throw SignClientException("Failed to verify signature for key: ${keyInfo.alias}")

        return verifyResult.isValid
    }

    override suspend fun signDigest(
        keyInfo: KeyInfoType<*>,
        digest: ByteArray,
        signatureAlgorithm: SignatureAlgorithm,
        signatureEncoding: SignatureEncoding,
        requireX5Chain: Boolean,
    ): ByteArray {
        keyClient.getKey(keyInfo.alias).awaitSingleOrNull()
            ?: throw SignClientException("Key not found in Azure Key Vault for reference: ${keyInfo.alias}")
        requireDigestLength(signatureAlgorithm, digest)
        val cryptoClient = keyClient.getCryptographyAsyncClient(keyInfo.alias)
        val signResult = cryptoClient.sign(signatureAlgorithm.toAzureSignatureAlgorithm(), digest).awaitSingleOrNull()
            ?: throw SignClientException("Failed to create digest signature for key: ${keyInfo.alias}")
        return normalizeAzureSignatureOutput(signResult.signature, signatureEncoding, signatureAlgorithm)
    }

    override suspend fun verifyDigest(
        keyInfo: KeyInfoType<*>,
        digest: ByteArray,
        signature: ByteArray,
        signatureAlgorithm: SignatureAlgorithm,
        signatureEncoding: SignatureEncoding,
    ): Boolean {
        keyClient.getKey(keyInfo.alias).awaitSingleOrNull()
            ?: throw SignClientException("Key not found in Azure Key Vault for reference: ${keyInfo.alias}")
        requireDigestLength(signatureAlgorithm, digest)
        val cryptoClient = keyClient.getCryptographyAsyncClient(keyInfo.alias)
        val nativeSignature = normalizeAzureSignatureInput(signature, signatureEncoding, signatureAlgorithm)
        val verifyResult = cryptoClient.verify(signatureAlgorithm.toAzureSignatureAlgorithm(), digest, nativeSignature).awaitSingleOrNull()
            ?: throw SignClientException("Failed to verify digest signature for key: ${keyInfo.alias}")
        return verifyResult.isValid
    }

    /**
     * Creates a structured signature for the provided input using the specified key and algorithm.
     *
     * @param signInput The input data and parameters for the signing operation
     * @param keyInfo Information about the key to use for signing (required)
     * @param signatureAlgorithm The algorithm to use for signing (defaults to signInput.algorithm if null)
     * @return A SignOutput containing the signature and related metadata
     * @throws SignClientException if the key info is not provided or signing fails
     */
    override suspend fun createSignature(
        signInput: SignInput,
        keyInfo: KeyInfoType<*>?,
        signatureAlgorithm: SignatureAlgorithm?
    ): SignOutput {
        val actualKeyInfo = keyInfo ?: throw SignClientException("Key info must be provided for signature creation")
        signatureAlgorithm ?: actualKeyInfo.signatureAlgorithm
            ?: throw SignClientException("signatureAlgorithm must be provided or derivable from keyInfo")

        val signatureValue = when (signInput.signMode) {
            SigningMode.DOCUMENT -> {
                // Raw document data — createRawSignature handles hashing internally
                createRawSignature(actualKeyInfo, signInput.input, false)
            }
            SigningMode.DIGEST -> {
                // Input is already a digest — sign directly without additional hashing
                val cryptoClient = keyClient.getCryptographyAsyncClient(actualKeyInfo.alias)
                val keyVaultKey = keyClient.getKey(actualKeyInfo.alias).awaitSingleOrNull()
                    ?: throw SignClientException("Key not found in Azure Key Vault for reference: ${actualKeyInfo.alias}")
                val signResult = cryptoClient.sign(keyVaultKey.toSignatureAlgorithm(), signInput.input).awaitSingleOrNull()
                    ?: throw SignClientException("Failed to create signature for key: ${actualKeyInfo.alias}")
                signResult.signature
            }
        }

        return SignOutputData(
            signedData = signatureValue,
            signatureLevel = SignatureLevel.RAW,
            signingTime = kotlin.time.Clock.System.now(),
            name = signInput.name,
        )
    }

    /**
     * Verifies a structured signature against the provided input data.
     *
     * @param signInput The input data to verify against the signature
     * @param signature The signature object containing the signature value and metadata
     * @return True if the signature is valid, false otherwise
     * @throws SignClientException if verification fails or the digest algorithm is missing for DOCUMENT mode
     */
    override suspend fun isValidSignature(signInput: SignInput, signature: Signature): Boolean {
        return when (signature.signMode) {
            SigningMode.DOCUMENT -> {
                // Raw document data — isValidRawSignature handles hashing internally
                isValidRawSignature(signature.keyInfo, signInput.input, signature.value)
            }
            SigningMode.DIGEST -> {
                // Input is already a digest — verify directly without additional hashing
                val cryptoClient = keyClient.getCryptographyAsyncClient(signature.keyInfo.alias)
                val keyVaultKey = keyClient.getKey(signature.keyInfo.alias).awaitSingleOrNull()
                    ?: throw SignClientException("Key not found in Azure Key Vault for reference: ${signature.keyInfo.alias}")
                val verifyResult = cryptoClient.verify(keyVaultKey.toSignatureAlgorithm(), signInput.input, signature.value).awaitSingleOrNull()
                    ?: throw SignClientException("Failed to verify signature for key: ${signature.keyInfo.alias}")
                verifyResult.isValid
            }
        }
    }

    /**
     * Lists all available keys in the Azure Key Vault.
     *
     * @return An array of managed key information objects
     */
    override suspend fun listKeys(): Array<ManagedKeyReference> {
        val keyProperties = keyClient.listPropertiesOfKeys()
            .filter { it.isEnabled }
            .collectList()
            .awaitSingle()

        return coroutineScope {
            keyProperties.map { property: KeyProperties ->
                async {
                    getKey(KeyInfo<JwkType>(kid = property.toKid()))
                }
            }.awaitAll().map { it.toKeyReference() }.toTypedArray()
        }
    }

    /**
     * Retrieves key information from Azure Key Vault.
     * First attempts to get the key as a certificate if the certificate API is available,
     * then falls back to retrieving it as a key if necessary.
     *
     * @param keyInfo Information about the key to retrieve
     * @return Managed key information including the full key details
     * @throws SignClientException if the key cannot be found in Azure Key Vault
     */
    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        val kvNames = kidToKVKeyName(keyInfo.kid!!)
        // Try the certificate first if available
        val keyEntry = if (hasCertsApi && certClient != null) {
            try {
                certClient.getCertificateVersion(kvNames.first, kvNames.second)
                    .awaitSingleOrNull()?.toManagedCertInfo()
            } catch (_: ResourceNotFoundException) {
                null
            }
        } else null

        // Fall back to key if certificate not found
        try {
            return keyEntry ?: keyClient.getKey(kvNames.first, kvNames.second)
                .awaitSingleOrNull()?.toManagedKeyInfo()
            ?: throw SignClientException("Key not found in Azure Key Vault for reference: ${keyInfo.kid}")
        } catch (expected: Exception) {
            throw SignClientException("keyClient.getKey failed for ${kvNames.first}", expected)
        }
    }

    /**
     * Stores a key in Azure Key Vault.
     *
     * @param keyInfo The resolved key information to store
     * @param providerId The key management system identifier
     * @param alias The reference to use for the key in the KMS
     * @return Managed key information for the stored key
     * @throws SignClientException if the key format is unsupported or storage fails
     */
    override suspend fun storeKey(keyInfo: ResolvedKeyInfoType<*>, providerId: String, alias: String, certChain: Array<Certificate>?): ManagedKeyInfoType<*> {
        val key = keyInfo.key

        // Handle Jwk keys
        val jsonWebKey = when (key) {
            is Jwk -> {
                with(key) {
                    val azureKey = JsonWebKey()
                        .setKeyType(KeyType.fromString(kty.value))

                    when (kty) {
                        JwaKeyType.EC -> {
                            azureKey.setCurveName(KeyCurveName.fromString(crv?.value ?: "P-256"))
                                .setX(x?.decodeFromBase64(true))
                                .setY(y?.decodeFromBase64(true))
                                .setD(d?.decodeFromBase64(true))
                        }

                        JwaKeyType.RSA -> {
                            azureKey.setN(n?.decodeFromBase64(true))
                                .setE(e?.decodeFromBase64(true))
                                .setD(d?.decodeFromBase64(true))
                        }

                        else -> throw SignClientException("Unsupported key type: ${kty.value}")
                    }

                    key_ops?.let { ops ->
                        azureKey.setKeyOps(ops.map {
                            KeyOperations.fromJose(it).toAzureKeyOperation()
                        }.toList())
                    }
                    azureKey
                }
            }

            else -> throw SignClientException("Unsupported key format: ${key::class.simpleName}")
        }

        val (name, version) = kidToKVKeyName(alias)
        val importedKey = keyClient.importKey(
            ImportKeyOptions(name, jsonWebKey)
                .setHardwareProtected(config.hsmType == HSMType.MANAGED_HSM)
        ).awaitSingle()


        return importedKey.toManagedKeyInfo()
    }

    /**
     * Deletes a key from Azure Key Vault.
     * Note: This only marks the key for deletion. Purging after the deletion period would require additional steps.
     *
     * @param keyInfo Information about the key to delete
     * @return True if deletion was successful, false otherwise
     * @throws SignClientException if the key ID is missing
     */
    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        if (keyInfo.kid == null) {
            throw SignClientException("Key ID is required to delete a key")
        }
        val (name, version) = kidToKVKeyName(keyInfo.kid!!)

        try {
            keyClient.beginDeleteKey(name).last().awaitSingle()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Gets the visibility level of keys managed by this provider.
     *
     * @return KeyVisibility.PUBLIC indicating that keys are publicly accessible
     */
    override fun keyVisibility(): KeyVisibility = KeyVisibility.PUBLIC
    actual override val kmsProviderType: String = PredefinedKmsProviderTypes.AZURE_KEYVAULT.kmsProviderType

    /**
     * Encrypts plaintext using the specified key and algorithm.
     *
     * Azure Key Vault supports AES-GCM and AES-CBC encryption algorithms.
     * The IV is automatically generated for GCM algorithms.
     *
     * @param keyInfo Information about the key to use for encryption
     * @param plaintext The data to encrypt
     * @param algorithm The content encryption algorithm to use
     * @param additionalAuthenticatedData Optional AAD for authenticated encryption
     * @return EncryptionResult containing ciphertext, IV, and authentication tag
     * @throws SignClientException if the key cannot be found or encryption fails
     */
    override suspend fun encrypt(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?
    ): EncryptionResult {
        val cryptoClient = keyClient.getCryptographyAsyncClient(keyInfo.alias)

        // Generate IV based on algorithm requirements
        val iv = ByteArray(algorithm.ivLength)
        SecureRandom().nextBytes(iv)

        val encryptParams = when (algorithm) {
            ContentEncryptionAlgorithm.A128GCM -> {
                if (additionalAuthenticatedData != null) {
                    EncryptParameters.createA128GcmParameters(plaintext, additionalAuthenticatedData)
                } else {
                    EncryptParameters.createA128GcmParameters(plaintext)
                }
            }
            ContentEncryptionAlgorithm.A192GCM -> {
                if (additionalAuthenticatedData != null) {
                    EncryptParameters.createA192GcmParameters(plaintext, additionalAuthenticatedData)
                } else {
                    EncryptParameters.createA192GcmParameters(plaintext)
                }
            }
            ContentEncryptionAlgorithm.A256GCM -> {
                if (additionalAuthenticatedData != null) {
                    EncryptParameters.createA256GcmParameters(plaintext, additionalAuthenticatedData)
                } else {
                    EncryptParameters.createA256GcmParameters(plaintext)
                }
            }
            ContentEncryptionAlgorithm.A128CBC_HS256 -> EncryptParameters.createA128CbcParameters(plaintext, iv)
            ContentEncryptionAlgorithm.A192CBC_HS384 -> EncryptParameters.createA192CbcParameters(plaintext, iv)
            ContentEncryptionAlgorithm.A256CBC_HS512 -> EncryptParameters.createA256CbcParameters(plaintext, iv)
        }

        val encryptResult = cryptoClient.encrypt(encryptParams).awaitSingleOrNull()
            ?: throw SignClientException("Failed to encrypt data with key: ${keyInfo.alias}")

        return EncryptionResult(
            ciphertext = encryptResult.cipherText,
            iv = encryptResult.iv ?: iv,
            authTag = encryptResult.authenticationTag ?: ByteArray(algorithm.tagLength)
        )
    }

    /**
     * Decrypts ciphertext using the specified key and algorithm.
     *
     * @param keyInfo Information about the key to use for decryption
     * @param ciphertext The encrypted data
     * @param algorithm The content encryption algorithm used for encryption
     * @param iv The initialization vector used during encryption
     * @param authTag The authentication tag for verification
     * @param additionalAuthenticatedData Optional AAD used during encryption
     * @return The decrypted plaintext
     * @throws SignClientException if the key cannot be found or decryption fails
     */
    override suspend fun decrypt(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?
    ): ByteArray {
        val cryptoClient = keyClient.getCryptographyAsyncClient(keyInfo.alias)

        val decryptParams = when (algorithm) {
            ContentEncryptionAlgorithm.A128GCM -> {
                if (additionalAuthenticatedData != null) {
                    DecryptParameters.createA128GcmParameters(ciphertext, iv, authTag, additionalAuthenticatedData)
                } else {
                    DecryptParameters.createA128GcmParameters(ciphertext, iv, authTag)
                }
            }
            ContentEncryptionAlgorithm.A192GCM -> {
                if (additionalAuthenticatedData != null) {
                    DecryptParameters.createA192GcmParameters(ciphertext, iv, authTag, additionalAuthenticatedData)
                } else {
                    DecryptParameters.createA192GcmParameters(ciphertext, iv, authTag)
                }
            }
            ContentEncryptionAlgorithm.A256GCM -> {
                if (additionalAuthenticatedData != null) {
                    DecryptParameters.createA256GcmParameters(ciphertext, iv, authTag, additionalAuthenticatedData)
                } else {
                    DecryptParameters.createA256GcmParameters(ciphertext, iv, authTag)
                }
            }
            ContentEncryptionAlgorithm.A128CBC_HS256 -> DecryptParameters.createA128CbcParameters(ciphertext, iv)
            ContentEncryptionAlgorithm.A192CBC_HS384 -> DecryptParameters.createA192CbcParameters(ciphertext, iv)
            ContentEncryptionAlgorithm.A256CBC_HS512 -> DecryptParameters.createA256CbcParameters(ciphertext, iv)
        }

        val decryptResult = cryptoClient.decrypt(decryptParams).awaitSingleOrNull()
            ?: throw SignClientException("Failed to decrypt data with key: ${keyInfo.alias}")

        return decryptResult.plainText
    }

    /**
     * Wraps (encrypts) a key using the specified wrapping key and algorithm.
     *
     * Supports RSA-OAEP and AES key wrap algorithms.
     *
     * @param wrappingKeyInfo Information about the key to use for wrapping
     * @param keyToWrap The key material to wrap
     * @param algorithm The key wrap algorithm to use
     * @return The wrapped key bytes
     * @throws SignClientException if wrapping fails
     */
    override suspend fun wrapKey(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): ByteArray {
        val cryptoClient = keyClient.getCryptographyAsyncClient(wrappingKeyInfo.alias)
        val azureAlgorithm = algorithm.toAzureKeyWrapAlgorithm()

        val wrapResult = cryptoClient.wrapKey(azureAlgorithm, keyToWrap).awaitSingleOrNull()
            ?: throw SignClientException("Failed to wrap key with key: ${wrappingKeyInfo.alias}")

        return wrapResult.encryptedKey
    }

    /**
     * Unwraps (decrypts) a wrapped key using the specified unwrapping key and algorithm.
     *
     * @param unwrappingKeyInfo Information about the key to use for unwrapping
     * @param wrappedKey The wrapped key bytes
     * @param algorithm The key wrap algorithm used during wrapping
     * @return The unwrapped key material
     * @throws SignClientException if unwrapping fails
     */
    override suspend fun unwrapKey(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): ByteArray {
        val cryptoClient = keyClient.getCryptographyAsyncClient(unwrappingKeyInfo.alias)
        val azureAlgorithm = algorithm.toAzureKeyWrapAlgorithm()

        val unwrapResult = cryptoClient.unwrapKey(azureAlgorithm, wrappedKey).awaitSingleOrNull()
            ?: throw SignClientException("Failed to unwrap key with key: ${unwrappingKeyInfo.alias}")

        return unwrapResult.key
    }

    private fun SignatureAlgorithm.toAzureSignatureAlgorithm(): AzureSignatureAlgorithm =
        when (this) {
            SignatureAlgorithm.ECDSA_SHA256 -> AzureSignatureAlgorithm.ES256
            SignatureAlgorithm.ECDSA_SHA384 -> AzureSignatureAlgorithm.ES384
            SignatureAlgorithm.ECDSA_SHA512 -> AzureSignatureAlgorithm.ES512
            SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1 -> AzureSignatureAlgorithm.PS256
            SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1 -> AzureSignatureAlgorithm.PS384
            SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1 -> AzureSignatureAlgorithm.PS512
            SignatureAlgorithm.RSA_SHA256 -> AzureSignatureAlgorithm.RS256
            SignatureAlgorithm.RSA_SHA384 -> AzureSignatureAlgorithm.RS384
            SignatureAlgorithm.RSA_SHA512 -> AzureSignatureAlgorithm.RS512
            else -> throw SignClientException("Digest signing is not supported for $this")
        }

    private fun requireDigestLength(
        algorithm: SignatureAlgorithm,
        digest: ByteArray,
    ) {
        val expected =
            when (algorithm) {
                SignatureAlgorithm.ECDSA_SHA256,
                SignatureAlgorithm.RSA_SHA256,
                SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1 -> 32
                SignatureAlgorithm.ECDSA_SHA384,
                SignatureAlgorithm.RSA_SHA384,
                SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1 -> 48
                SignatureAlgorithm.ECDSA_SHA512,
                SignatureAlgorithm.RSA_SHA512,
                SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1 -> 64
                else -> throw SignClientException("Digest signing is not supported for $algorithm")
            }
        require(digest.size == expected) { "Digest for $algorithm must be $expected bytes, got ${digest.size}" }
    }

    private fun normalizeAzureSignatureOutput(
        signature: ByteArray,
        signatureEncoding: SignatureEncoding,
        algorithm: SignatureAlgorithm,
    ): ByteArray =
        when {
            algorithm.cryptoAlgorithm == CryptoAlg.ECDSA && signatureEncoding == SignatureEncoding.RAW ->
                if (SignatureEncodingCodec.isDer(signature)) {
                    SignatureEncodingCodec.derToRaw(signature, SignatureEncodingCodec.scalarLength(algorithm))
                } else {
                    signature
                }

            algorithm.cryptoAlgorithm == CryptoAlg.ECDSA && signatureEncoding == SignatureEncoding.DER ->
                if (SignatureEncodingCodec.isDer(signature)) {
                    signature
                } else {
                    SignatureEncodingCodec.rawToDer(signature, SignatureEncodingCodec.scalarLength(algorithm))
                }

            signatureEncoding == SignatureEncoding.RAW -> signature
            else -> throw SignClientException("DER signature encoding is only supported for ECDSA algorithms")
        }

    private fun normalizeAzureSignatureInput(
        signature: ByteArray,
        signatureEncoding: SignatureEncoding,
        algorithm: SignatureAlgorithm,
    ): ByteArray =
        when {
            algorithm.cryptoAlgorithm == CryptoAlg.ECDSA && signatureEncoding == SignatureEncoding.RAW -> signature
            algorithm.cryptoAlgorithm == CryptoAlg.ECDSA && signatureEncoding == SignatureEncoding.DER ->
                SignatureEncodingCodec.derToRaw(signature, SignatureEncodingCodec.scalarLength(algorithm))
            signatureEncoding == SignatureEncoding.RAW -> signature
            else -> throw SignClientException("DER signature encoding is only supported for ECDSA algorithms")
        }

    /**
     * Converts our KeyWrapAlgorithm to Azure's KeyWrapAlgorithm.
     */
    private fun KeyWrapAlgorithm.toAzureKeyWrapAlgorithm(): AzureKeyWrapAlgorithm = when (this) {
        KeyWrapAlgorithm.RSA1_5 -> AzureKeyWrapAlgorithm.RSA1_5
        KeyWrapAlgorithm.RSA_OAEP -> AzureKeyWrapAlgorithm.RSA_OAEP
        KeyWrapAlgorithm.RSA_OAEP_256 -> AzureKeyWrapAlgorithm.RSA_OAEP_256
        KeyWrapAlgorithm.RSA_OAEP_384 -> throw UnsupportedOperationException("RSA-OAEP-384 not supported by Azure Key Vault")
        KeyWrapAlgorithm.RSA_OAEP_512 -> throw UnsupportedOperationException("RSA-OAEP-512 not supported by Azure Key Vault")
        KeyWrapAlgorithm.A128KW -> AzureKeyWrapAlgorithm.A128KW
        KeyWrapAlgorithm.A192KW -> AzureKeyWrapAlgorithm.A192KW
        KeyWrapAlgorithm.A256KW -> AzureKeyWrapAlgorithm.A256KW
        KeyWrapAlgorithm.A128GCMKW -> throw UnsupportedOperationException("A128GCMKW not supported by Azure Key Vault")
        KeyWrapAlgorithm.A192GCMKW -> throw UnsupportedOperationException("A192GCMKW not supported by Azure Key Vault")
        KeyWrapAlgorithm.A256GCMKW -> throw UnsupportedOperationException("A256GCMKW not supported by Azure Key Vault")
        KeyWrapAlgorithm.DIR -> throw UnsupportedOperationException("Direct key agreement not supported for key wrapping")
    }

}
