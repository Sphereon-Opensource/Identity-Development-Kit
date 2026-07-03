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


import kotlin.js.Promise
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.SignClientException
import com.sphereon.crypto.core.generic.CoseKeyPair
import com.sphereon.crypto.core.generic.CryptoAlg
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.JoseKeyPair
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.CertificateOptions
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.toKeyReference
import com.sphereon.crypto.core.sign.model.SignInput
import com.sphereon.crypto.core.sign.model.SignOutput
import com.sphereon.crypto.core.sign.model.SignOutputData
import com.sphereon.crypto.core.sign.model.Signature
import com.sphereon.crypto.core.sign.model.SignatureLevel
import com.sphereon.crypto.core.sign.model.SigningMode
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.kms.PredefinedKmsProviderTypes
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.EncryptionResult
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import com.sphereon.crypto.core.kms.command.SignatureEncoding
import com.sphereon.crypto.core.kms.command.SignatureEncodingCodec


/** Convert Kotlin ByteArray (Int8Array) to Uint8Array for Azure SDK interop */
private fun ByteArray.toUint8Array(): Uint8Array {
    val uint8 = Uint8Array(size)
    for (i in indices) {
        uint8.asDynamic()[i] = this[i].toInt() and 0xFF
    }
    return uint8
}

/** Convert Uint8Array from Azure SDK back to Kotlin ByteArray */
private fun Uint8Array.toByteArray(): ByteArray {
    val bytes = ByteArray(length)
    for (i in 0 until length) {
        bytes[i] = asDynamic()[i].unsafeCast<Int>().toByte()
    }
    return bytes
}

// Azure SDK external declarations are in separate files with @file:JsModule
// for proper ESM named import generation:
// - AzureIdentityExternals.kt: ClientSecretCredential from @azure/identity
// - AzureKeyvaultKeysExternals.kt: KeyClient, CryptographyClient, CreateEcKeyOptions, CreateRsaKeyOptions from @azure/keyvault-keys

external interface AzureKeyvaultEncryptResult {
    val result: Uint8Array
    val iv: Uint8Array?
    val authenticationTag: Uint8Array?
}

external interface AzureKeyvaultDecryptResult {
    val result: Uint8Array
}

external interface AzureKeyvaultWrapResult {
    val result: Uint8Array
}

external interface AzureKeyvaultUnwrapResult {
    val result: Uint8Array
}

external interface AsyncIterableIterator<T> {
}

external interface JsIteratorResult<T> {
    val value: T
    val done: Boolean?
}

/**
 * Collects all items from an async iterable iterator into a List.
 *
 * @return List containing all collected items
 */
suspend fun <T> Any.collectAsyncIterableToList(): List<T> {
    val items = mutableListOf<T>()
    val iterator = this.asDynamic()[js("Symbol.asyncIterator")]()

    while (true) {
        val iterationResult = js("iterator.next()")
            .unsafeCast<Promise<JsIteratorResult<T>>>()
            .await()

        if (iterationResult.done == true) {
            break
        }

        items.add(iterationResult.value)
    }

    return items
}


/**
 * JavaScript implementation of the Azure Key Vault crypto provider for Kotlin Multiplatform.
 * Provides cryptographic operations using Azure Key Vault keys through the Azure SDK for JavaScript.
 *
 * @param config Azure KMS provider configuration including credentials and vault URL
 * @param settings Key provider settings for managing key operations
 */
@JsExport.Ignore
actual class AzureKeyVaultCryptoProvider actual constructor(
    config: AzureKmsProviderConfig,
//    settings: KeyProviderSettings
) : BaseAzureKeyvaultCryptoProvider(config, /*settings*/) {
    private val keyClient: KeyClient
    private val clientSecretCredential: ClientSecretCredential

    init {
        require(config.credentialOpts.secretCredentialOpts != null) { "Azure Key Vault requires a secret credential" }
        clientSecretCredential =
            ClientSecretCredential(
                config.tenantId,
                config.credentialOpts.secretCredentialOpts.clientId,
                config.credentialOpts.secretCredentialOpts.clientSecret
            )
        keyClient = KeyClient(config.keyvaultUrl, clientSecretCredential)
    }

    /**
     * Generates a new key in Azure Key Vault with the specified parameters.
     * Supports both EC and RSA key types.
     *
     * @param alias Optional key reference/name, generates UUID if null
     * @param use JWK use parameter for the key
     * @param keyOperations Array of allowed key operations
     * @param alg Signature algorithm, defaults to ECDSA_SHA256
     * @return ManagedKeyPair containing the generated key information
     */
    override suspend fun generateKeyAsync(
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        certificateOptions: CertificateOptions?
    ): ManagedKeyPair {
        val signatureAlgorithm = alg ?: SignatureAlgorithm.ECDSA_SHA256
        require(certificateOptions == null) { "Certificate options are not yet supported by Azure Key Vault" }
        require(isSupportedSignatureAlgorithm(signatureAlgorithm)) {
            "Signature algorithm ${signatureAlgorithm.cryptoAlgorithm.name} is not supported by Azure Key Vault"
        }
        val keyName = alias ?: "key-${Uuid.v4String()}"

        val keyVaultKey = when (signatureAlgorithm.cryptoAlgorithm) {
            CryptoAlg.RSA -> {
                // Create RSA key
                val rsaOptions: CreateRsaKeyOptions = js("{}").unsafeCast<CreateRsaKeyOptions>().apply {
                    keySize = 2048 // Default RSA key size
                }
                try {
                    keyClient.createRsaKey(keyName, rsaOptions).await()
                } catch (expected: Exception) {
                    throw SignClientException("Failed to create RSA key in Azure Key Vault: ${expected.message}")
                }
            }
            else -> {
                // Create EC key (ECDSA)
                val ecOptions: CreateEcKeyOptions = js("{}").unsafeCast<CreateEcKeyOptions>().apply {
                    curve = signatureAlgorithm.curve?.jose?.value
                }
                try {
                    keyClient.createEcKey(keyName, ecOptions).await()
                } catch (expected: Exception) {
                    throw SignClientException("Failed to create EC key in Azure Key Vault: ${expected.message}")
                }
            }
        }

        val keyVaultJwk = keyVaultKey.toJwk()
        val publicCoseKey = CoseJoseKeyMappingService.toCoseKey(keyVaultJwk)
        val kid = keyVaultKey.key.kid
        return ManagedKeyPair(
            providerId = id,
            alias = keyVaultKey.name,
            kid = kid,
            jose = JoseKeyPair(null, keyVaultJwk),
            cose = CoseKeyPair(null, publicCoseKey)
        )
    }

    /**
     * Creates a raw digital signature using an Azure Key Vault key.
     *
     * @param keyInfo Key information containing the key reference
     * @param input Data to be signed
     * @param requireX5Chain Whether X.509 certificate chain is required
     * @return Raw signature bytes
     */
    override suspend fun createRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean
    ): ByteArray {
        require(keyInfo.alias !== null) { "Key reference is required" }

        return try {
            val azureKey = keyClient.getKey(keyInfo.alias.toString()).await()
            val cryptographyClient = CryptographyClient(azureKey, clientSecretCredential)
            val signature = cryptographyClient.signData(azureKey.key.getSignatureAlgorithmName(), input.toUint8Array()).await()
            signature.result.toByteArray()
        } catch (expected: Exception) {
            throw SignClientException("Failed to create signature: ${expected.message}")
        }
    }

    /**
     * Signs a pre-computed digest using Azure's sign() (no internal hashing).
     */
    private suspend fun createRawSignatureFromDigest(
        keyInfo: KeyInfoType<*>,
        digest: ByteArray
    ): ByteArray {
        require(keyInfo.alias !== null) { "Key reference is required" }
        return try {
            val azureKey = keyClient.getKey(keyInfo.alias.toString()).await()
            val cryptographyClient = CryptographyClient(azureKey, clientSecretCredential)
            val signature = cryptographyClient.sign(azureKey.key.getSignatureAlgorithmName(), digest.toUint8Array()).await()
            signature.result.toByteArray()
        } catch (expected: Exception) {
            throw SignClientException("Failed to create signature from digest: ${expected.message}")
        }
    }

    override suspend fun signDigest(
        keyInfo: KeyInfoType<*>,
        digest: ByteArray,
        signatureAlgorithm: SignatureAlgorithm,
        signatureEncoding: SignatureEncoding,
        requireX5Chain: Boolean,
    ): ByteArray {
        require(keyInfo.alias !== null) { "Key reference is required" }
        requireDigestLength(signatureAlgorithm, digest)
        return try {
            val azureKey = keyClient.getKey(keyInfo.alias.toString()).await()
            val cryptographyClient = CryptographyClient(azureKey, clientSecretCredential)
            val signature = cryptographyClient.sign(signatureAlgorithm.toAzureSignatureAlgorithmName(), digest.toUint8Array()).await()
            normalizeAzureSignatureOutput(signature.result.toByteArray(), signatureEncoding, signatureAlgorithm)
        } catch (expected: Exception) {
            throw SignClientException("Failed to create digest signature: ${expected.message}", expected)
        }
    }

    /**
     * Verifies a raw digital signature against input data using an Azure Key Vault key.
     *
     * @param keyInfo Key information containing the key reference
     * @param input Original data that was signed
     * @param signature Signature bytes to verify
     * @return True if signature is valid, false otherwise
     */
    override suspend fun isValidRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray
    ): Boolean {
        require(keyInfo.alias !== null) { "Key reference is required" }

        return try {
            val azureKey = keyClient.getKey(keyInfo.alias.toString()).await()
            val cryptographyClient = CryptographyClient(azureKey, clientSecretCredential)
            val verifyResultRaw =
                cryptographyClient.verifyData(azureKey.key.getSignatureAlgorithmName(), input.toUint8Array(), signature.toUint8Array()).await()
            // Access result dynamically in case the property name differs in the Azure JS SDK
            val result = verifyResultRaw.asDynamic().result
            if (result == null || result == undefined) {
                throw SignClientException("verifyData returned null/undefined result. Keys: ${js("Object.keys(verifyResultRaw)").unsafeCast<Array<String>>().joinToString()}")
            }
            result.unsafeCast<Boolean>()
        } catch (expected: Exception) {
            throw SignClientException("Failed to verify signature: ${expected.message}", expected)
        }
    }

    /**
     * Fetches key information from Azure Key Vault by key reference.
     *
     * @param keyRef The key reference/name to fetch
     * @return ManagedKeyInfo containing the fetched key details
     */
    suspend fun fetchKeyAsync(keyRef: String): ManagedKeyInfoType<Jwk> {
        return try {
            val keyVaultKey = keyClient.getKey(keyRef).await()
            val keyVaultJwk = keyVaultKey.toJwk()
            ManagedKeyInfo(
                providerId = id,
                alias = keyVaultKey.name,
                resolvedKeyInfo = ResolvedKeyInfo.fromKey(keyVaultJwk),
            )
        } catch (expected: Exception) {
            throw SignClientException("Failed to fetch key '$keyRef': ${expected.message}")
        }
    }

    /**
     * Creates a complete signature structure including metadata and validation.
     *
     * @param signInput Input data and parameters for signing
     * @param keyInfo Key information for signing
     * @param signatureAlgorithm Algorithm to use for signing
     * @return SignOutput containing the signature and metadata
     */
    override suspend fun createSignature(
        signInput: SignInput,
        keyInfo: KeyInfoType<*>?,
        signatureAlgorithm: SignatureAlgorithm?
    ): SignOutput {
        val actualKeyInfo = keyInfo ?: throw IllegalArgumentException("Key info must be provided")
        val actualAlg = signatureAlgorithm ?: actualKeyInfo.signatureAlgorithm
            ?: throw IllegalArgumentException("signatureAlgorithm must be provided or derivable from keyInfo")

        // Azure has two signing methods:
        // - signData(): hashes the input internally (for DOCUMENT mode)
        // - sign(): takes a pre-computed digest (for DIGEST mode)
        val signatureValue = when (signInput.signMode) {
            SigningMode.DIGEST -> {
                // Input is already a digest — use sign() which does NOT hash
                createRawSignatureFromDigest(actualKeyInfo, signInput.input)
            }
            else -> {
                // Input is a full document — use signData() which hashes internally
                createRawSignature(actualKeyInfo, signInput.input, requireX5Chain = false)
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
     * Validates a signature against the original input data.
     *
     * @param signInput Original input that was signed
     * @param signature Signature to validate
     * @return True if signature is valid, false otherwise
     */
    override suspend fun isValidSignature(signInput: SignInput, signature: Signature): Boolean {
        // Azure's verifyData() hashes internally for DOCUMENT mode.
        // For DIGEST mode, use verify() which takes a pre-computed digest.
        return when (signature.signMode) {
            SigningMode.DIGEST -> {
                // Input is already a digest — use verify() (no internal hashing)
                isValidDigestSignature(signature.keyInfo, signInput.input, signature.value)
            }
            else -> {
                // Input is raw document — use verifyData() (hashes internally)
                isValidRawSignature(signature.keyInfo, signInput.input, signature.value)
            }
        }
    }

    /**
     * Verifies a signature against a pre-computed digest using Azure's verify() (no internal hashing).
     */
    private suspend fun isValidDigestSignature(
        keyInfo: KeyInfoType<*>,
        digest: ByteArray,
        signature: ByteArray
    ): Boolean {
        require(keyInfo.alias !== null) { "Key reference is required" }
        return try {
            val azureKey = keyClient.getKey(keyInfo.alias.toString()).await()
            val cryptographyClient = CryptographyClient(azureKey, clientSecretCredential)
            val verifyResult =
                cryptographyClient.verify(azureKey.key.getSignatureAlgorithmName(), digest.toUint8Array(), signature.toUint8Array()).await()
            verifyResult.result
        } catch (expected: Exception) {
            throw SignClientException("Failed to verify digest signature: ${expected.message}", expected)
        }
    }

    override suspend fun verifyDigest(
        keyInfo: KeyInfoType<*>,
        digest: ByteArray,
        signature: ByteArray,
        signatureAlgorithm: SignatureAlgorithm,
        signatureEncoding: SignatureEncoding,
    ): Boolean {
        require(keyInfo.alias !== null) { "Key reference is required" }
        requireDigestLength(signatureAlgorithm, digest)
        val nativeSignature = normalizeAzureSignatureInput(signature, signatureEncoding, signatureAlgorithm)
        return try {
            val azureKey = keyClient.getKey(keyInfo.alias.toString()).await()
            val cryptographyClient = CryptographyClient(azureKey, clientSecretCredential)
            val verifyResult =
                cryptographyClient.verify(signatureAlgorithm.toAzureSignatureAlgorithmName(), digest.toUint8Array(), nativeSignature.toUint8Array()).await()
            verifyResult.result
        } catch (expected: Exception) {
            throw SignClientException("Failed to verify digest signature: ${expected.message}", expected)
        }
    }

    /**
     * Lists all available keys from the Azure Key Vault.
     *
     * @return Array of managed key information for all available keys
     */
    override suspend fun listKeys(): Array<ManagedKeyReference> {
        try {
            val keyPropertiesList = keyClient.listPropertiesOfKeys()
                .collectAsyncIterableToList<KeyProperties>()

            return coroutineScope {
                val deferredKeys = keyPropertiesList
                    .filter { it.enabled == true }
                    .map { property ->
                        async {
                            getKey(KeyInfo<JwkType>(kid = property.toKid()))
                        }
                    }

                deferredKeys.awaitAll().map { it.toKeyReference() }.toTypedArray()
            }
        } catch (expected: Exception) {
            throw SignClientException("Failed to list keys: ${expected.message}")
        }
    }

    /**
     * Retrieves a specific key by its key information.
     *
     * @param keyInfo Key information containing either key reference or kid
     * @return Managed key information for the requested key
     */
    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        if (keyInfo.alias === null && keyInfo.kid === null) {
            throw IllegalArgumentException("Either key reference or kid must be provided")
        }

        val keyRef = keyInfo.alias ?: keyInfo.kid
        val (keyName, _) = kidToKVKeyName(keyRef.toString())

        return try {
            fetchKeyAsync(keyName)
        } catch (expected: Exception) {
            throw SignClientException("Failed to get key: ${expected.message}")
        }
    }

    /**
     * Imports an existing key into Azure Key Vault.
     *
     * @param keyInfo Resolved key information containing the key to import
     * @param providerId KMS identifier
     * @param alias Key reference for the imported key
     * @return Managed key information for the imported key
     */
    override suspend fun storeKey(keyInfo: ResolvedKeyInfoType<*>, providerId: String, alias: String, certChain: Array<Certificate>?): ManagedKeyInfoType<*> {
        try {
            val jwk: Jwk = keyInfo.key as? Jwk ?: throw IllegalArgumentException("Expected a JWK key for import")
            val jsonWebKey = jwk.toJSType()
            val keyVaultKey = keyClient.importKey(alias, jsonWebKey).await()
            return ManagedKeyInfo(
                providerId = id,
                alias = keyVaultKey.name,
                resolvedKeyInfo = ResolvedKeyInfo.fromKey(keyVaultKey.toJwk()),
            )
        } catch (expected: Exception) {
            throw SignClientException("Failed to import key: ${expected.message}")
        }
    }

    /**
     * Deletes a key from Azure Key Vault.
     *
     * @param keyInfo Key information containing the key reference to delete
     * @return True if deletion was successful, false otherwise
     */
    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        require(keyInfo.alias !== null) { "Key reference is required for deletion" }

        try {
            keyClient.beginDeleteKey(keyInfo.alias.toString()).await()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Returns the key visibility level for this provider.
     *
     * @return KeyVisibility.PUBLIC as Azure Key Vault keys are public keys
     */
    override fun keyVisibility(): KeyVisibility {
        return KeyVisibility.PUBLIC
    }

    actual override val kmsProviderType: String = PredefinedKmsProviderTypes.AZURE_KEYVAULT.kmsProviderType

    private fun SignatureAlgorithm.toAzureSignatureAlgorithmName(): String =
        when (this) {
            SignatureAlgorithm.ECDSA_SHA256 -> "ES256"
            SignatureAlgorithm.ECDSA_SHA384 -> "ES384"
            SignatureAlgorithm.ECDSA_SHA512 -> "ES512"
            SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1 -> "PS256"
            SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1 -> "PS384"
            SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1 -> "PS512"
            SignatureAlgorithm.RSA_SHA256 -> "RS256"
            SignatureAlgorithm.RSA_SHA384 -> "RS384"
            SignatureAlgorithm.RSA_SHA512 -> "RS512"
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
     * Encrypts plaintext using the specified key and algorithm.
     *
     * Azure Key Vault supports AES-GCM and AES-CBC encryption algorithms.
     *
     * @param keyInfo Information about the key to use for encryption
     * @param plaintext The data to encrypt
     * @param algorithm The content encryption algorithm to use
     * @param additionalAuthenticatedData Optional AAD for authenticated encryption (not supported in JS SDK)
     * @return EncryptionResult containing ciphertext, IV, and authentication tag
     * @throws SignClientException if the key cannot be found or encryption fails
     */
    override suspend fun encrypt(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?
    ): EncryptionResult {
        require(keyInfo.alias !== null) { "Key reference is required" }

        return try {
            val azureKey = keyClient.getKey(keyInfo.alias.toString()).await()
            val cryptographyClient = CryptographyClient(azureKey, clientSecretCredential)
            val azureAlgorithm = algorithm.toAzureAlgorithmName()

            val encryptResult = cryptographyClient.encrypt(azureAlgorithm, plaintext.toUint8Array()).await()

            EncryptionResult(
                ciphertext = encryptResult.result.toByteArray(),
                iv = encryptResult.iv?.toByteArray() ?: ByteArray(algorithm.ivLength),
                authTag = encryptResult.authenticationTag?.toByteArray() ?: ByteArray(algorithm.tagLength)
            )
        } catch (expected: Exception) {
            throw SignClientException("Failed to encrypt data: ${expected.message}")
        }
    }

    /**
     * Decrypts ciphertext using the specified key and algorithm.
     *
     * @param keyInfo Information about the key to use for decryption
     * @param ciphertext The encrypted data
     * @param algorithm The content encryption algorithm used for encryption
     * @param iv The initialization vector used during encryption
     * @param authTag The authentication tag for verification
     * @param additionalAuthenticatedData Optional AAD used during encryption (not supported in JS SDK)
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
        require(keyInfo.alias !== null) { "Key reference is required" }

        return try {
            val azureKey = keyClient.getKey(keyInfo.alias.toString()).await()
            val cryptographyClient = CryptographyClient(azureKey, clientSecretCredential)
            val azureAlgorithm = algorithm.toAzureAlgorithmName()

            val decryptResult = cryptographyClient.decrypt(azureAlgorithm, ciphertext.toUint8Array()).await()
            decryptResult.result.toByteArray()
        } catch (expected: Exception) {
            throw SignClientException("Failed to decrypt data: ${expected.message}")
        }
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
        require(wrappingKeyInfo.alias !== null) { "Key reference is required" }

        return try {
            val azureKey = keyClient.getKey(wrappingKeyInfo.alias.toString()).await()
            val cryptographyClient = CryptographyClient(azureKey, clientSecretCredential)
            val azureAlgorithm = algorithm.toAzureAlgorithmName()

            val wrapResult = cryptographyClient.wrapKey(azureAlgorithm, keyToWrap.toUint8Array()).await()
            wrapResult.result.toByteArray()
        } catch (expected: Exception) {
            throw SignClientException("Failed to wrap key: ${expected.message}")
        }
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
        require(unwrappingKeyInfo.alias !== null) { "Key reference is required" }

        return try {
            val azureKey = keyClient.getKey(unwrappingKeyInfo.alias.toString()).await()
            val cryptographyClient = CryptographyClient(azureKey, clientSecretCredential)
            val azureAlgorithm = algorithm.toAzureAlgorithmName()

            val unwrapResult = cryptographyClient.unwrapKey(azureAlgorithm, wrappedKey.toUint8Array()).await()
            unwrapResult.result.toByteArray()
        } catch (expected: Exception) {
            throw SignClientException("Failed to unwrap key: ${expected.message}")
        }
    }

    /**
     * Converts our ContentEncryptionAlgorithm to Azure's algorithm name string.
     */
    private fun ContentEncryptionAlgorithm.toAzureAlgorithmName(): String = when (this) {
        ContentEncryptionAlgorithm.A128GCM -> "A128GCM"
        ContentEncryptionAlgorithm.A192GCM -> "A192GCM"
        ContentEncryptionAlgorithm.A256GCM -> "A256GCM"
        ContentEncryptionAlgorithm.A128CBC_HS256 -> "A128CBC-HS256"
        ContentEncryptionAlgorithm.A192CBC_HS384 -> "A192CBC-HS384"
        ContentEncryptionAlgorithm.A256CBC_HS512 -> "A256CBC-HS512"
    }

    /**
     * Converts our KeyWrapAlgorithm to Azure's algorithm name string.
     */
    private fun KeyWrapAlgorithm.toAzureAlgorithmName(): String = when (this) {
        KeyWrapAlgorithm.RSA1_5 -> "RSA1_5"
        KeyWrapAlgorithm.RSA_OAEP -> "RSA-OAEP"
        KeyWrapAlgorithm.RSA_OAEP_256 -> "RSA-OAEP-256"
        KeyWrapAlgorithm.RSA_OAEP_384 -> throw UnsupportedOperationException("RSA-OAEP-384 not supported by Azure Key Vault")
        KeyWrapAlgorithm.RSA_OAEP_512 -> throw UnsupportedOperationException("RSA-OAEP-512 not supported by Azure Key Vault")
        KeyWrapAlgorithm.A128KW -> "A128KW"
        KeyWrapAlgorithm.A192KW -> "A192KW"
        KeyWrapAlgorithm.A256KW -> "A256KW"
        KeyWrapAlgorithm.A128GCMKW -> throw UnsupportedOperationException("A128GCMKW not supported by Azure Key Vault")
        KeyWrapAlgorithm.A192GCMKW -> throw UnsupportedOperationException("A192GCMKW not supported by Azure Key Vault")
        KeyWrapAlgorithm.A256GCMKW -> throw UnsupportedOperationException("A256GCMKW not supported by Azure Key Vault")
        KeyWrapAlgorithm.DIR -> throw UnsupportedOperationException("Direct key agreement not supported for key wrapping")
    }
}
