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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.command.CreateRawSignatureResult
import com.sphereon.crypto.core.kms.command.DecryptResult
import com.sphereon.crypto.core.kms.command.DeleteKeyResult
import com.sphereon.crypto.core.kms.command.EncryptResult
import com.sphereon.crypto.core.kms.command.GenerateKeyResult
import com.sphereon.crypto.core.kms.command.GetKeyResult
import com.sphereon.crypto.core.kms.command.ListKeysResult
import com.sphereon.crypto.core.kms.command.PerformKeyAgreementResult
import com.sphereon.crypto.core.kms.command.ResolvePublicKeyResult
import com.sphereon.crypto.core.kms.command.StoreKeyResult
import com.sphereon.crypto.core.kms.command.UnwrapKeyResult
import com.sphereon.crypto.core.kms.command.VerifyRawSignatureResult
import com.sphereon.crypto.core.kms.command.WrapKeyResult
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import com.sphereon.crypto.core.sign.SimpleSignatureService
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.di.session.SessionGraph
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * KeyManagerService provides an interface for managing key management systems providers and key resolver services.
 * It extends the SimpleSignatureService, EncryptionService, and PublicKeyResolver interfaces.
 *
 * This interface also extends [KmsProviderRegistry] and [KeyResolverRegistry] to provide
 * a unified facade for provider and resolver management. Commands can choose to inject
 * either the full [KeyManagerService] or the narrower registry interfaces depending on their needs.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyManagerService", exact = true)
interface KeyManagerService :
    SimpleSignatureService,
    EncryptionService,
    PublicKeyResolver,
    ManagedKeyStoreService,
    HasKeyStoreService,
    KmsProviderRegistry,
    KeyResolverRegistry {
    /**
     * Modern async query for a single KMS provider using Command pattern with IdkResult.
     *
     * This method uses the QueryProviderCommand internally and returns an IdkResult,
     * allowing for proper error handling without exceptions.
     *
     * Example usage:
     * ```kotlin
     * val result = keyManagerService.queryProvider(kmsQuery {
     *     signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
     *     storageType = KeyStorageType.EPHEMERAL
     * })
     *
     * when {
     *     result.isOk -> {
     *         val provider = result.value.provider
     *         // Use provider...
     *     }
     *     result.isErr -> {
     *         log.error("Query failed: ${result.error.message}")
     *     }
     * }
     * ```
     *
     * @param query The capability criteria to match against
     * @return IdkResult containing QueryProviderResult with the matching provider and its capabilities
     */

    suspend fun queryProvider(query: KmsProviderQuery): IdkResult<QueryProviderResult, IdkError>

    /**
     * Modern async query for multiple KMS providers using Command pattern with IdkResult.
     *
     * This method uses the QueryProvidersCommand internally and returns an IdkResult,
     * allowing for proper error handling without exceptions.
     *
     * Example usage:
     * ```kotlin
     * val result = keyManagerService.queryProviders(kmsQuery {
     *     operation = KmsProviderOperation.ENCRYPT
     *     contentEncryptionAlgorithm = ContentEncryptionAlgorithm.A256GCM
     * })
     *
     * if (result.isOk) {
     *     println("Found ${result.value.matchCount} providers")
     *     result.value.providers.forEach { provider ->
     *         println("- ${provider.id}")
     *     }
     * }
     * ```
     *
     * @param query The capability criteria to match against
     * @return IdkResult containing QueryProvidersResult with all matching providers
     */

    suspend fun queryProviders(query: KmsProviderQuery): IdkResult<QueryProvidersResult, IdkError>

    /**
     * Gets all registered providers' capabilities.
     * Useful for introspection, diagnostics, and UI/admin tools.
     *
     * Example usage:
     * ```kotlin
     * val result = keyManagerService.getAllCapabilities(includeDisabled = false)
     * if (result.isOk) {
     *     result.value.capabilities.forEach { (providerId, caps) ->
     *         println("Provider $providerId: ${caps.providerType}")
     *         caps.operations.filter { it.supported }.forEach {
     *             println("  - ${it.operation}")
     *         }
     *     }
     * }
     * ```
     *
     * @param includeDisabled Whether to include disabled providers in the result
     * @return IdkResult containing GetAllCapabilitiesResult with all provider capabilities
     */

    suspend fun getAllCapabilities(includeDisabled: Boolean = false): IdkResult<GetAllCapabilitiesResult, IdkError>

    // Provider and resolver management methods are inherited from KmsProviderRegistry and KeyResolverRegistry

    /**
     * Asynchronously generates a cryptographic key pair based on the specified elliptic curve mapping.
     *
     * @param curve The elliptic curve mapping used to generate the key pair. This parameter defines the types of curves supported
     *              for cryptographic operations and includes mappings for both COSE and JOSE curves.
     * @return A `CryptoProviderKeyPair` containing both COSE and JOSE key pairs.
     */
    @Deprecated(
        message = "Use generateKey instead. This method will be removed in a future release.",
        replaceWith = ReplaceWith("generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)"),
        level = DeprecationLevel.WARNING,
    )
    suspend fun generateKeyAsync(
        providerId: String? = null, // Either we look for the signature algo supported or use the default KMS
        alias: String? = null,
        use: JwkUse? = null,
        keyOperations: Array<out KeyOperations>? = null,
        alg: SignatureAlgorithm? = null,
        keyVisibility: KeyVisibility? = KeyVisibility.PUBLIC,
    ): ManagedKeyPair

    /**
     * Asynchronously generates a cryptographic key pair based on the specified elliptic curve mapping.
     *
     * @param curve The elliptic curve mapping used to generate the key pair. This parameter defines the types of curves supported
     *              for cryptographic operations and includes mappings for both COSE and JOSE curves.
     * @return A `CryptoProviderKeyPair` containing both COSE and JOSE key pairs.
     */
    suspend fun generateKey(
        providerId: String? = null, // Either we look for the signature algo supported or use the default KMS
        alias: String? = null,
        use: JwkUse? = null,
        keyOperations: Array<out KeyOperations>? = null,
        alg: SignatureAlgorithm? = null,
        keyVisibility: KeyVisibility? = KeyVisibility.PUBLIC,
    ): ManagedKeyPair

    // ========================================================================
    // Command-based API returning IdkResult (recommended for new code)
    // ========================================================================

    /**
     * Creates a raw signature using the command pattern with IdkResult return type.
     *
     * @param keyInfo The key info used for signing
     * @param input The data to be signed
     * @param requireX5Chain Whether the X5 chain should be included in the signature
     * @return IdkResult containing CreateRawSignatureResult or IdkError
     */

    suspend fun createRawSignatureResult(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean = false,
    ): IdkResult<CreateRawSignatureResult, IdkError>

    /**
     * Verifies a raw signature using the command pattern with IdkResult return type.
     *
     * @param keyInfo Key information that includes the public key
     * @param input The original data which the signature is supposed to represent
     * @param signature The signature that needs to be verified
     * @return IdkResult containing VerifyRawSignatureResult or IdkError
     */

    suspend fun verifyRawSignatureResult(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray,
    ): IdkResult<VerifyRawSignatureResult, IdkError>

    /**
     * Encrypts plaintext using the command pattern with IdkResult return type.
     *
     * @param keyInfo The key info containing the encryption key
     * @param plaintext The data to encrypt
     * @param algorithm The content encryption algorithm to use
     * @param additionalAuthenticatedData Optional AAD
     * @return IdkResult containing EncryptResult or IdkError
     */

    suspend fun encryptResult(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray? = null,
    ): IdkResult<EncryptResult, IdkError>

    /**
     * Decrypts ciphertext using the command pattern with IdkResult return type.
     *
     * @param keyInfo The key info containing the decryption key
     * @param ciphertext The encrypted data
     * @param algorithm The content encryption algorithm used during encryption
     * @param iv The initialization vector used during encryption
     * @param authTag The authentication tag generated during encryption
     * @param additionalAuthenticatedData Optional AAD
     * @return IdkResult containing DecryptResult or IdkError
     */

    suspend fun decryptResult(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray? = null,
    ): IdkResult<DecryptResult, IdkError>

    /**
     * Wraps a key using the command pattern with IdkResult return type.
     *
     * @param wrappingKeyInfo The key info containing the key-encryption key
     * @param keyToWrap The raw bytes of the key to be wrapped
     * @param algorithm The key wrapping algorithm to use
     * @return IdkResult containing WrapKeyResult or IdkError
     */

    suspend fun wrapKeyResult(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): IdkResult<WrapKeyResult, IdkError>

    /**
     * Unwraps a key using the command pattern with IdkResult return type.
     *
     * @param unwrappingKeyInfo The key info containing the key-decryption key
     * @param wrappedKey The wrapped key bytes
     * @param algorithm The key wrapping algorithm that was used
     * @return IdkResult containing UnwrapKeyResult or IdkError
     */

    suspend fun unwrapKeyResult(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): IdkResult<UnwrapKeyResult, IdkError>

    /**
     * Performs key agreement using the command pattern with IdkResult return type.
     *
     * @param privateKeyInfo The local party's private key info
     * @param publicKeyInfo The remote party's public key info
     * @param algorithm The key agreement algorithm
     * @param keyDataLen The desired length of derived key material in bits
     * @return IdkResult containing PerformKeyAgreementResult or IdkError
     */

    suspend fun performKeyAgreementResult(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int? = null,
    ): IdkResult<PerformKeyAgreementResult, IdkError>

    /**
     * Generates a key using the command pattern with IdkResult return type.
     *
     * @param providerId The KMS provider ID to use
     * @param alias Optional alias for the key
     * @param use The intended use of the key
     * @param keyOperations The allowed operations for this key
     * @param alg The signature algorithm to use
     * @param keyVisibility The visibility of the key
     * @return IdkResult containing GenerateKeyResult or IdkError
     */

    suspend fun generateKeyResult(
        providerId: String? = null,
        alias: String? = null,
        use: JwkUse? = null,
        keyOperations: Array<out KeyOperations>? = null,
        alg: SignatureAlgorithm? = null,
        keyVisibility: KeyVisibility? = KeyVisibility.PUBLIC,
    ): IdkResult<GenerateKeyResult, IdkError>

    /**
     * Lists all keys using the command pattern with IdkResult return type.
     *
     * @param providerId Optional provider ID filter
     * @return IdkResult containing ListKeysResult or IdkError
     */

    suspend fun listKeysResult(providerId: String? = null): IdkResult<ListKeysResult, IdkError>

    /**
     * Gets a key using the command pattern with IdkResult return type.
     *
     * @param keyInfo The key info identifying the key to retrieve
     * @return IdkResult containing GetKeyResult or IdkError
     */

    suspend fun getKeyResult(keyInfo: KeyInfoType<*>): IdkResult<GetKeyResult, IdkError>

    /**
     * Stores a key using the command pattern with IdkResult return type.
     *
     * @param keyInfo The resolved key info to store
     * @param providerId The KMS provider ID where the key will be stored
     * @param alias The alias for the key
     * @param certChain Optional certificate chain
     * @return IdkResult containing StoreKeyResult or IdkError
     */

    suspend fun storeKeyResult(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>? = null,
    ): IdkResult<StoreKeyResult, IdkError>

    /**
     * Deletes a key using the command pattern with IdkResult return type.
     *
     * @param keyInfo The key info identifying the key to delete
     * @return IdkResult containing DeleteKeyResult or IdkError
     */

    suspend fun deleteKeyResult(keyInfo: KeyInfoType<*>): IdkResult<DeleteKeyResult, IdkError>

    /**
     * Resolves a public key using the command pattern with IdkResult return type.
     *
     * @param keyInfo The key information to be resolved
     * @param identifierMethod Optional method used to identify the key
     * @param trustedCerts Optional array of trusted certificates
     * @param verifyX509CertificateChain Optional boolean for X.509 chain verification
     * @return IdkResult containing ResolvePublicKeyResult or IdkError
     */

    suspend fun resolvePublicKeyResult(
        keyInfo: KeyInfoType<*>,
        identifierMethod: IdentifierMethod? = null,
        trustedCerts: Array<String>? = null,
        verifyX509CertificateChain: Boolean? = null,
    ): IdkResult<ResolvePublicKeyResult, IdkError>

    /**
     * Graph interface for DI graph integration.
     * Allows other components to access the KeyManagerService.
     * @deprecated Use top-level [KeyManagerServiceGraph] instead for KSP compatibility
     */

    interface KmsGraph : KeyManagerServiceGraph

    @ContributesTo(AppScope::class)
    interface KmsProviderFactoriesGraph {
        val kmsProviderFactories: Set<KmsProviderFactory>
    }
}

/**
 * Graph interface for DI graph integration.
 * Allows other components to access the KeyManagerService.
 * This is a top-level interface (required for KSP/Anvil processing).
 */
@ContributesTo(scope = SessionScope::class)
interface KeyManagerServiceGraph {
    val keyManagerService: KeyManagerService
}

/**
 * Extension function to get the KeyManagerServiceGraph from a SessionGraph.
 * Use this instead of casting to `KeyManagerService.KmsGraph`.
 */
fun SessionGraph.asKeyManagerServiceGraph(): KeyManagerServiceGraph = this as KeyManagerServiceGraph

@OptIn(ExperimentalObjCName::class)
@ObjCName("ManagedKeyStoreService", exact = true)
interface ManagedKeyStoreService : KeyStoreService
