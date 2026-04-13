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

package com.sphereon.crypto.core.kms

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.CryptoAlg
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.x509.Certificate
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
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet


/**
 * Test mock implementation of KeyManagerService for unit testing.
 *
 * Provides basic in-memory storage and mock responses for testing crypto operations.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class)
class TestKmsMock : KeyManagerService {
    private val providers = mutableMapOf<String, KmsProvider>()
    private val resolvers = mutableMapOf<String, KeyResolverService>()
    private val keys = mutableMapOf<String, ManagedKeyInfoType<*>>()
    private var defaultProviderId: String = "test-provider"
    private var defaultResolverId: String = "test-resolver"

    override fun defaultProviderId(): String = defaultProviderId

    override fun defaultResolverId(): String = defaultResolverId

    override fun registerProvider(provider: KmsProvider, makeDefaultKms: Boolean?) {
        providers[provider.id] = provider
        if (makeDefaultKms == true) {
            defaultProviderId = provider.id
        }
    }

    override fun getProviderIds(): Array<String> = providers.keys.toTypedArray()

    override fun getProviderById(id: String): KmsProvider {
        return providers[id] ?: throw IllegalArgumentException("Provider not found: $id")
    }

    override fun getKmsBySignatureAlgorithm(signatureAlgorithm: SignatureAlgorithm): KmsProvider {
        return providers.values.firstOrNull {
            it.supportedSignatureAlgorithms().contains(signatureAlgorithm)
        } ?: throw IllegalArgumentException("No provider for algorithm: $signatureAlgorithm")
    }

    override fun getResolverById(id: String): KeyResolverService {
        return resolvers[id] ?: throw IllegalArgumentException("Resolver not found: $id")
    }

    override fun getResolverByKeyTypeOrIdentifier(
        identifierMethod: IdentifierMethod?,
        keyType: KeyTypeMapping?,
        resolverId: String?
    ): KeyResolverService {
        if (resolverId != null) return getResolverById(resolverId)
        return resolvers.values.firstOrNull() ?: throw IllegalArgumentException("No resolver available")
    }

    override fun registerResolver(resolver: KeyResolverService, makeDefaultResolver: Boolean?) {
        resolvers[resolver.getId()] = resolver
        if (makeDefaultResolver == true) {
            defaultResolverId = resolver.getId()
        }
    }
    override suspend fun generateKey(
        providerId: String?,
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        keyVisibility: KeyVisibility?
    ): ManagedKeyPair {
        val provider = if (providerId != null) getProviderById(providerId) else providers.values.firstOrNull()
            ?: throw IllegalStateException("No provider registered")
        return provider.generateKeyAsync(alias, use, keyOperations, alg)
    }

    @Deprecated("Use generateKey instead", ReplaceWith("generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)"))
    override suspend fun generateKeyAsync(
        providerId: String?,
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        keyVisibility: KeyVisibility?
    ): ManagedKeyPair = generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)

    override fun getResolverIds(): Array<String> = resolvers.keys.toTypedArray()

    override fun getProvider(providerId: String?, alg: SignatureAlgorithm?): KmsProvider {
        if (providerId != null) return getProviderById(providerId)
        if (alg != null) return getKmsBySignatureAlgorithm(alg)
        return providers.values.firstOrNull() ?: throw IllegalStateException("No provider registered")
    }

    override suspend fun createRawSignature(keyInfo: KeyInfoType<*>, input: ByteArray, requireX5Chain: Boolean): ByteArray {
        return ByteArray(64) { it.toByte() }
    }

    override suspend fun isValidRawSignature(keyInfo: KeyInfoType<*>, input: ByteArray, signature: ByteArray): Boolean {
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <KT : KeyType> resolvePublicKey(
        keyInfo: KeyInfoType<KT>,
        identifierMethod: IdentifierMethod?,
        trustedCerts: Array<String>?,
        verifyX509CertificateChain: Boolean?
    ): ResolvedKeyInfoType<KT> {
        if (keyInfo is ResolvedKeyInfoType<*>) {
            return keyInfo as ResolvedKeyInfoType<KT>
        }
        val stored = keyInfo.alias?.let { keys[it] } ?: keyInfo.kid?.let { kid -> keys.values.find { it.kid == kid } }
        if (stored != null) {
            return ResolvedKeyInfo.fromDTO(stored) as ResolvedKeyInfoType<KT>
        }
        throw IllegalArgumentException("Cannot resolve key: ${keyInfo.alias ?: keyInfo.kid}")
    }

    override val settings: KeyProviderSettings? = null

    override suspend fun listKeys(): Array<ManagedKeyInfoType<*>> = keys.values.toTypedArray()

    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        val alias = keyInfo.alias ?: keyInfo.kid ?: throw IllegalArgumentException("Need alias or kid")
        return keys[alias] ?: keys.values.find { it.kid == alias }
            ?: throw IllegalArgumentException("Key not found: $alias")
    }

    override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?
    ): ManagedKeyInfoType<*> {
        val managed = ManagedKeyInfo(
            alias = alias,
            providerId = providerId,
            resolvedKeyInfo = keyInfo
        )
        keys[alias] = managed
        return managed
    }

    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        val alias = keyInfo.alias ?: return false
        return keys.remove(alias) != null
    }

    override fun keyVisibility(): KeyVisibility = KeyVisibility.PUBLIC

    override val keyStore: KeyStoreService
        get() = throw UnsupportedOperationException("KeyStore not available in mock")

    override suspend fun queryProvider(query: KmsProviderQuery): IdkResult<QueryProviderResult, IdkError> {
        val matching = providers.values.firstOrNull { query.matches(it.getCapabilities()) }
        return if (matching != null) {
            val caps = matching.getCapabilities()
            Ok(QueryProviderResult(
                match = ProviderMatch(
                    providerId = matching.id,
                    capabilities = caps,
                    matchScore = 100
                )
            ))
        } else {
            Err(IdkError.NOT_FOUND_ERROR(resource = "KmsProvider", message = "No provider matches query"))
        }
    }

    override suspend fun queryProviders(query: KmsProviderQuery): IdkResult<QueryProvidersResult, IdkError> {
        val matching = providers.values.filter { query.matches(it.getCapabilities()) }
        val matches = matching.map { provider ->
            ProviderMatch(
                providerId = provider.id,
                capabilities = provider.getCapabilities(),
                matchScore = 100
            )
        }.toTypedArray()
        return Ok(QueryProvidersResult(
            matches = matches,
            totalProviders = providers.size
        ))
    }

    override suspend fun getAllCapabilities(includeDisabled: Boolean): IdkResult<GetAllCapabilitiesResult, IdkError> {
        return Ok(GetAllCapabilitiesResult(
            capabilities = providers.map { (id, provider) -> id to provider.getCapabilities() }.toMap()
        ))
    }

    override suspend fun encrypt(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?
    ): EncryptionResult {
        return EncryptionResult(
            ciphertext = plaintext.reversedArray(),
            iv = ByteArray(12) { it.toByte() },
            authTag = ByteArray(16) { it.toByte() }
        )
    }

    override suspend fun decrypt(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?
    ): ByteArray {
        return ciphertext.reversedArray()
    }

    override suspend fun wrapKey(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): ByteArray {
        return keyToWrap.reversedArray()
    }

    override suspend fun unwrapKey(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): ByteArray {
        return wrappedKey.reversedArray()
    }

    override suspend fun performKeyAgreement(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int?
    ): ByteArray {
        return ByteArray(keyDataLen ?: 32) { it.toByte() }
    }

    // IdkResult-returning methods
    override suspend fun createRawSignatureResult(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean
    ): IdkResult<CreateRawSignatureResult, IdkError> {
        return Ok(CreateRawSignatureResult(createRawSignature(keyInfo, input, requireX5Chain)))
    }

    override suspend fun verifyRawSignatureResult(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray
    ): IdkResult<VerifyRawSignatureResult, IdkError> {
        return Ok(VerifyRawSignatureResult(isValidRawSignature(keyInfo, input, signature)))
    }

    override suspend fun encryptResult(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?
    ): IdkResult<EncryptResult, IdkError> {
        val result = encrypt(keyInfo, plaintext, algorithm, additionalAuthenticatedData)
        return Ok(EncryptResult(ciphertext = result.ciphertext, iv = result.iv, authTag = result.authTag))
    }

    override suspend fun decryptResult(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?
    ): IdkResult<DecryptResult, IdkError> {
        return Ok(DecryptResult(decrypt(keyInfo, ciphertext, algorithm, iv, authTag, additionalAuthenticatedData)))
    }

    override suspend fun wrapKeyResult(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): IdkResult<WrapKeyResult, IdkError> {
        return Ok(WrapKeyResult(wrapKey(wrappingKeyInfo, keyToWrap, algorithm)))
    }

    override suspend fun unwrapKeyResult(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): IdkResult<UnwrapKeyResult, IdkError> {
        return Ok(UnwrapKeyResult(unwrapKey(unwrappingKeyInfo, wrappedKey, algorithm)))
    }

    override suspend fun performKeyAgreementResult(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int?
    ): IdkResult<PerformKeyAgreementResult, IdkError> {
        return Ok(PerformKeyAgreementResult(performKeyAgreement(privateKeyInfo, publicKeyInfo, algorithm, keyDataLen)))
    }

    override suspend fun generateKeyResult(
        providerId: String?,
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        keyVisibility: KeyVisibility?
    ): IdkResult<GenerateKeyResult, IdkError> {
        return Ok(GenerateKeyResult(generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)))
    }

    override suspend fun listKeysResult(providerId: String?): IdkResult<ListKeysResult, IdkError> {
        val allKeys = listKeys()
        val filtered = if (providerId != null) {
            allKeys.filter { it.providerId == providerId }.toTypedArray()
        } else {
            allKeys
        }
        return Ok(ListKeysResult(filtered))
    }

    override suspend fun getKeyResult(keyInfo: KeyInfoType<*>): IdkResult<GetKeyResult, IdkError> {
        return try {
            Ok(GetKeyResult(getKey(keyInfo)))
        } catch (e: Exception) {
            Err(IdkError.NOT_FOUND_ERROR(resource = "Key", message = e.message ?: "Key not found"))
        }
    }

    override suspend fun storeKeyResult(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?
    ): IdkResult<StoreKeyResult, IdkError> {
        return Ok(StoreKeyResult(storeKey(keyInfo, providerId, alias, certChain)))
    }

    override suspend fun deleteKeyResult(keyInfo: KeyInfoType<*>): IdkResult<DeleteKeyResult, IdkError> {
        return Ok(DeleteKeyResult(deleteKey(keyInfo)))
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun resolvePublicKeyResult(
        keyInfo: KeyInfoType<*>,
        identifierMethod: IdentifierMethod?,
        trustedCerts: Array<String>?,
        verifyX509CertificateChain: Boolean?
    ): IdkResult<ResolvePublicKeyResult, IdkError> {
        return try {
            Ok(ResolvePublicKeyResult(resolvePublicKey(keyInfo, identifierMethod, trustedCerts, verifyX509CertificateChain)))
        } catch (e: Exception) {
            Err(IdkError.NOT_FOUND_ERROR(resource = "Key", message = e.message ?: "Could not resolve key"))
        }
    }
}


/**
 * Test mock implementation of KmsProvider for unit testing.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class)
class TestKmsProviderMock : KmsProvider {
    private val keys = mutableMapOf<String, ManagedKeyInfoType<*>>()

    override val id: String = "test-kms-provider"

    override fun supportedKeyTypes(): Array<KeyTypeMapping> = arrayOf(
        KeyTypeMapping.EC,
        KeyTypeMapping.RSA,
        KeyTypeMapping.OKP
    )

    override fun supportedSignatureAlgorithms(): Array<SignatureAlgorithm> = arrayOf(
        SignatureAlgorithm.ECDSA_SHA256,
        SignatureAlgorithm.ECDSA_SHA384,
        SignatureAlgorithm.ECDSA_SHA512
    )

    override fun supportedDigests(): Array<DigestAlg> = arrayOf(
        DigestAlg.SHA256,
        DigestAlg.SHA384,
        DigestAlg.SHA512
    )

    override fun supportedCurves(): Array<Curve> = arrayOf(
        Curve.P_256,
        Curve.P_384,
        Curve.P_521
    )

    override fun isSupportedCurve(curve: Curve): Boolean = curve in supportedCurves()

    override suspend fun generateKeyAsync(
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        certificateOptions: CertificateOptions?
    ): ManagedKeyPair {
        throw UnsupportedOperationException("Key generation not implemented in mock")
    }

    override val kmsProviderType: String = "test-mock"

    override suspend fun createRawSignature(keyInfo: KeyInfoType<*>, input: ByteArray, requireX5Chain: Boolean): ByteArray {
        return ByteArray(64) { it.toByte() }
    }

    override suspend fun isValidRawSignature(keyInfo: KeyInfoType<*>, input: ByteArray, signature: ByteArray): Boolean {
        return true
    }

    override suspend fun createSignature(
        signInput: com.sphereon.crypto.core.sign.model.SignInput,
        keyInfo: KeyInfoType<*>?,
        signatureAlgorithm: SignatureAlgorithm?
    ): com.sphereon.crypto.core.sign.model.SignOutput {
        val signatureBytes = createRawSignature(keyInfo!!, signInput.input, false)
        return com.sphereon.crypto.core.sign.model.SignOutputData(
            signedData = signatureBytes,
            signatureLevel = com.sphereon.crypto.core.sign.model.SignatureLevel.RAW,
            signingTime = kotlinx.datetime.Clock.System.now(),
            name = signInput.name,
            mimeType = null
        )
    }

    override suspend fun isValidSignature(
        signInput: com.sphereon.crypto.core.sign.model.SignInput,
        signature: com.sphereon.crypto.core.sign.model.Signature
    ): Boolean {
        return isValidRawSignature(signature.keyInfo, signInput.input, signature.value)
    }

    override val settings: KeyProviderSettings? = null

    override suspend fun listKeys(): Array<ManagedKeyInfoType<*>> = keys.values.toTypedArray()

    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        val alias = keyInfo.alias ?: keyInfo.kid ?: throw IllegalArgumentException("Need alias or kid")
        return keys[alias] ?: throw IllegalArgumentException("Key not found: $alias")
    }

    override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?
    ): ManagedKeyInfoType<*> {
        val managed = ManagedKeyInfo(alias = alias, providerId = providerId, resolvedKeyInfo = keyInfo)
        keys[alias] = managed
        return managed
    }

    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        val alias = keyInfo.alias ?: return false
        return keys.remove(alias) != null
    }

    override fun keyVisibility(): KeyVisibility = KeyVisibility.PUBLIC

    override fun getCapabilities(): KmsProviderCapabilities {
        return KmsProviderCapabilities(
            providerId = id,
            providerType = kmsProviderType,
            storageTypes = arrayOf(KeyStorageType.EPHEMERAL),
            supportsKeyImport = false,
            supportsKeyExport = true,
            exposePrivateKeys = false,
            operations = arrayOf(
                OperationCapability(KmsProviderOperation.SIGN, true),
                OperationCapability(KmsProviderOperation.VERIFY, true)
            ),
            supportedKeyTypes = supportedKeyTypes(),
            supportedCurves = supportedCurves(),
            supportedCryptoAlgorithms = arrayOf(CryptoAlg.ECDSA),
            supportedDigestAlgorithms = supportedDigests(),
            signatureAlgorithms = supportedSignatureAlgorithms(),
            supportsX509 = false,
            supportsAttestation = false,
            supportsHardwareBacking = false,
            supportsPublicKeyResolution = false,
            resolutionMethods = emptyArray()
        )
    }

    override suspend fun encrypt(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?
    ): EncryptionResult {
        return EncryptionResult(
            ciphertext = plaintext.reversedArray(),
            iv = ByteArray(12) { it.toByte() },
            authTag = ByteArray(16) { it.toByte() }
        )
    }

    override suspend fun decrypt(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?
    ): ByteArray {
        return ciphertext.reversedArray()
    }

    override suspend fun wrapKey(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): ByteArray {
        return keyToWrap.reversedArray()
    }

    override suspend fun unwrapKey(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): ByteArray {
        return wrappedKey.reversedArray()
    }

    override suspend fun performKeyAgreement(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int?
    ): ByteArray {
        return ByteArray(keyDataLen ?: 32) { it.toByte() }
    }
}

/**
 * Test mock implementation of KeyResolverService for unit testing.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<KeyResolverService>())
class TestKeyResolver : KeyResolverService {
    override fun getId(): String = "test-resolver"

    override fun allSupportedIdentifierMethods(): Array<IdentifierMethod> = arrayOf(
        IdentifierMethod.jwk,
        IdentifierMethod.x5c
    )

    override fun allSupportedKeyTypes(): Array<KeyTypeMapping> = arrayOf(
        KeyTypeMapping.EC,
        KeyTypeMapping.RSA,
        KeyTypeMapping.OKP
    )

    override fun supportedKeyTypesAndIdentifierMethods(): Map<IdentifierMethod, Array<KeyTypeMapping>> {
        return allSupportedIdentifierMethods().associateWith { allSupportedKeyTypes() }
    }

    override fun getSupportedKeyTypes(identifierMethod: IdentifierMethod): Array<KeyTypeMapping> {
        return allSupportedKeyTypes()
    }

    override fun getSupportedIdentifierMethods(keyType: KeyTypeMapping): Array<IdentifierMethod> {
        return allSupportedIdentifierMethods()
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <KT : KeyType> resolvePublicKey(
        keyInfo: KeyInfoType<KT>,
        identifierMethod: IdentifierMethod?,
        trustedCerts: Array<String>?,
        verifyX509CertificateChain: Boolean?
    ): ResolvedKeyInfoType<KT> {
        if (keyInfo is ResolvedKeyInfoType<*>) {
            return keyInfo as ResolvedKeyInfoType<KT>
        }
        throw IllegalArgumentException("Cannot resolve key in test mock: ${keyInfo.alias ?: keyInfo.kid}")
    }
}
