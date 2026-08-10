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

package com.sphereon.openid.oid4vci.issuer.impl.format

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ResolvedKeyInfo
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
import com.sphereon.crypto.core.kms.GetAllCapabilitiesResult
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyResolverService
import com.sphereon.crypto.core.kms.KeyStorageType
import com.sphereon.crypto.core.kms.KeyStoreService
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderCapabilities
import com.sphereon.crypto.core.kms.KmsProviderOperation
import com.sphereon.crypto.core.kms.KmsProviderQuery
import com.sphereon.crypto.core.kms.OperationCapability
import com.sphereon.crypto.core.kms.ProviderMatch
import com.sphereon.crypto.core.kms.QueryProviderResult
import com.sphereon.crypto.core.kms.QueryProvidersResult
import com.sphereon.crypto.core.kms.command.CreateRawSignatureResult
import com.sphereon.crypto.core.kms.command.DecryptResult
import com.sphereon.crypto.core.kms.command.DeleteKeyResult
import com.sphereon.crypto.core.kms.command.EncryptResult
import com.sphereon.crypto.core.kms.command.GenerateKeyResult
import com.sphereon.crypto.core.kms.command.GetKeyResult
import com.sphereon.crypto.core.kms.command.ListKeysResult
import com.sphereon.crypto.core.kms.command.PerformKeyAgreementResult
import com.sphereon.crypto.core.kms.command.ResolvePublicKeyResult
import com.sphereon.crypto.core.kms.command.SignDigestResult
import com.sphereon.crypto.core.kms.command.SignatureEncoding
import com.sphereon.crypto.core.kms.command.StoreKeyResult
import com.sphereon.crypto.core.kms.command.UnwrapKeyResult
import com.sphereon.crypto.core.kms.command.VerifyDigestResult
import com.sphereon.crypto.core.kms.command.VerifyRawSignatureResult
import com.sphereon.crypto.core.kms.command.WrapKeyResult
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.toKeyReference
import com.sphereon.crypto.core.x509.Certificate

/**
 * Test mock implementation of KeyManagerService for unit testing.
 * Copied from lib-crypto-core-impl commonTest (no shared test-fixtures module yet).
 *
 * Provides basic in-memory storage and mock responses for testing crypto operations.
 */
class TestKmsMock : KeyManagerService {
    private val providers = mutableMapOf<String, KmsProvider>()
    private val resolvers = mutableMapOf<String, KeyResolverService>()
    private val keys = mutableMapOf<String, ManagedKeyInfoType<*>>()
    private var defaultProviderId: String = "test-provider"
    private var defaultResolverId: String = "test-resolver"

    override fun defaultProviderId(): String = defaultProviderId

    override fun defaultResolverId(): String = defaultResolverId

    override fun registerProvider(
        provider: KmsProvider,
        makeDefaultKms: Boolean?,
    ) {
        providers[provider.id] = provider
        if (makeDefaultKms == true) {
            defaultProviderId = provider.id
        }
    }

    override fun getProviderIds(): Array<String> = providers.keys.toTypedArray()

    override suspend fun getProviderById(id: String): KmsProvider = providers[id] ?: throw IllegalArgumentException("Provider not found: $id")

    override suspend fun getKmsBySignatureAlgorithm(signatureAlgorithm: SignatureAlgorithm): KmsProvider =
        providers.values.firstOrNull {
            it.supportedSignatureAlgorithms().contains(signatureAlgorithm)
        } ?: throw IllegalArgumentException("No provider for algorithm: $signatureAlgorithm")

    override fun getResolverById(id: String): KeyResolverService = resolvers[id] ?: throw IllegalArgumentException("Resolver not found: $id")

    override fun getResolverByKeyTypeOrIdentifier(
        identifierMethod: IdentifierMethod?,
        keyType: KeyTypeMapping?,
        resolverId: String?,
    ): KeyResolverService {
        if (resolverId != null) return getResolverById(resolverId)
        return resolvers.values.firstOrNull() ?: throw IllegalArgumentException("No resolver available")
    }

    override fun registerResolver(
        resolver: KeyResolverService,
        makeDefaultResolver: Boolean?,
    ) {
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
        keyVisibility: KeyVisibility?,
    ): ManagedKeyPair {
        val provider =
            if (providerId != null) {
                getProviderById(providerId)
            } else {
                providers.values.firstOrNull()
                    ?: throw IllegalStateException("No provider registered")
            }
        return provider.generateKeyAsync(alias, use, keyOperations, alg)
    }

    @Deprecated("Use generateKey instead", ReplaceWith("generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)"))
    override suspend fun generateKeyAsync(
        providerId: String?,
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        keyVisibility: KeyVisibility?,
    ): ManagedKeyPair = generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)

    override fun getResolverIds(): Array<String> = resolvers.keys.toTypedArray()

    override suspend fun getProvider(
        providerId: String?,
        alg: SignatureAlgorithm?,
    ): KmsProvider {
        if (providerId != null) return getProviderById(providerId)
        if (alg != null) return getKmsBySignatureAlgorithm(alg)
        return providers.values.firstOrNull() ?: throw IllegalStateException("No provider registered")
    }

    override suspend fun createRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean,
    ): ByteArray = ByteArray(64) { it.toByte() }

    override suspend fun isValidRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray,
    ): Boolean = true

    override suspend fun signDigest(
        keyInfo: KeyInfoType<*>,
        digest: ByteArray,
        signatureAlgorithm: SignatureAlgorithm,
        signatureEncoding: SignatureEncoding,
        requireX5Chain: Boolean,
    ): ByteArray =
        getProvider(keyInfo.providerId, signatureAlgorithm)
            .signDigest(keyInfo, digest, signatureAlgorithm, signatureEncoding, requireX5Chain)

    override suspend fun verifyDigest(
        keyInfo: KeyInfoType<*>,
        digest: ByteArray,
        signature: ByteArray,
        signatureAlgorithm: SignatureAlgorithm,
        signatureEncoding: SignatureEncoding,
    ): Boolean =
        getProvider(keyInfo.providerId, signatureAlgorithm)
            .verifyDigest(keyInfo, digest, signature, signatureAlgorithm, signatureEncoding)

    @Suppress("UNCHECKED_CAST")
    override suspend fun <KT : KeyType> resolvePublicKey(
        keyInfo: KeyInfoType<KT>,
        identifierMethod: IdentifierMethod?,
        trustedCerts: Array<String>?,
        verifyX509CertificateChain: Boolean?,
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

    override suspend fun listKeys(): Array<ManagedKeyReference> = keys.values.map { it.toKeyReference() }.toTypedArray()

    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        val alias = keyInfo.alias ?: keyInfo.kid ?: throw IllegalArgumentException("Need alias or kid")
        return keys[alias] ?: keys.values.find { it.kid == alias }
            ?: throw IllegalArgumentException("Key not found: $alias")
    }

    override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?,
    ): ManagedKeyInfoType<*> {
        val managed =
            ManagedKeyInfo(
                alias = alias,
                providerId = providerId,
                resolvedKeyInfo = keyInfo,
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
            Ok(
                QueryProviderResult(
                    match =
                        ProviderMatch(
                            providerId = matching.id,
                            capabilities = caps,
                            matchScore = 100,
                        ),
                ),
            )
        } else {
            Err(IdkError.NOT_FOUND_ERROR(resource = "KmsProvider", message = "No provider matches query"))
        }
    }

    override suspend fun queryProviders(query: KmsProviderQuery): IdkResult<QueryProvidersResult, IdkError> {
        val matching = providers.values.filter { query.matches(it.getCapabilities()) }
        val matches =
            matching
                .map { provider ->
                    ProviderMatch(
                        providerId = provider.id,
                        capabilities = provider.getCapabilities(),
                        matchScore = 100,
                    )
                }.toTypedArray()
        return Ok(
            QueryProvidersResult(
                matches = matches,
                totalProviders = providers.size,
            ),
        )
    }

    override suspend fun getAllCapabilities(includeDisabled: Boolean): IdkResult<GetAllCapabilitiesResult, IdkError> =
        Ok(
            GetAllCapabilitiesResult(
                capabilities = providers.map { (id, provider) -> id to provider.getCapabilities() }.toMap(),
            ),
        )

    override suspend fun encrypt(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?,
    ): EncryptionResult =
        EncryptionResult(
            ciphertext = plaintext.reversedArray(),
            iv = ByteArray(12) { it.toByte() },
            authTag = ByteArray(16) { it.toByte() },
        )

    override suspend fun decrypt(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?,
    ): ByteArray = ciphertext.reversedArray()

    override suspend fun wrapKey(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): ByteArray = keyToWrap.reversedArray()

    override suspend fun unwrapKey(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): ByteArray = wrappedKey.reversedArray()

    override suspend fun performKeyAgreement(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int?,
    ): ByteArray = ByteArray(keyDataLen ?: 32) { it.toByte() }

    // IdkResult-returning methods
    override suspend fun createRawSignatureResult(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean,
    ): IdkResult<CreateRawSignatureResult, IdkError> = Ok(CreateRawSignatureResult(createRawSignature(keyInfo, input, requireX5Chain)))

    override suspend fun verifyRawSignatureResult(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray,
    ): IdkResult<VerifyRawSignatureResult, IdkError> = Ok(VerifyRawSignatureResult(isValidRawSignature(keyInfo, input, signature)))

    override suspend fun signDigestResult(
        keyInfo: KeyInfoType<*>,
        digest: ByteArray,
        signatureAlgorithm: SignatureAlgorithm,
        signatureEncoding: SignatureEncoding,
        requireX5Chain: Boolean,
    ): IdkResult<SignDigestResult, IdkError> = Ok(SignDigestResult(signDigest(keyInfo, digest, signatureAlgorithm, signatureEncoding, requireX5Chain)))

    override suspend fun verifyDigestResult(
        keyInfo: KeyInfoType<*>,
        digest: ByteArray,
        signature: ByteArray,
        signatureAlgorithm: SignatureAlgorithm,
        signatureEncoding: SignatureEncoding,
    ): IdkResult<VerifyDigestResult, IdkError> = Ok(VerifyDigestResult(verifyDigest(keyInfo, digest, signature, signatureAlgorithm, signatureEncoding)))

    override suspend fun encryptResult(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?,
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
        additionalAuthenticatedData: ByteArray?,
    ): IdkResult<DecryptResult, IdkError> = Ok(DecryptResult(decrypt(keyInfo, ciphertext, algorithm, iv, authTag, additionalAuthenticatedData)))

    override suspend fun wrapKeyResult(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): IdkResult<WrapKeyResult, IdkError> = Ok(WrapKeyResult(wrapKey(wrappingKeyInfo, keyToWrap, algorithm)))

    override suspend fun unwrapKeyResult(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): IdkResult<UnwrapKeyResult, IdkError> = Ok(UnwrapKeyResult(unwrapKey(unwrappingKeyInfo, wrappedKey, algorithm)))

    override suspend fun performKeyAgreementResult(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int?,
    ): IdkResult<PerformKeyAgreementResult, IdkError> = Ok(PerformKeyAgreementResult(performKeyAgreement(privateKeyInfo, publicKeyInfo, algorithm, keyDataLen)))

    override suspend fun generateKeyResult(
        providerId: String?,
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        keyVisibility: KeyVisibility?,
    ): IdkResult<GenerateKeyResult, IdkError> = Ok(GenerateKeyResult(generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)))

    override suspend fun listKeysResult(providerId: String?): IdkResult<ListKeysResult, IdkError> {
        val allKeys = listKeys()
        val filtered =
            if (providerId != null) {
                allKeys.filter { it.providerId == providerId }.toTypedArray()
            } else {
                allKeys
            }
        return Ok(ListKeysResult(filtered))
    }

    override suspend fun getKeyResult(keyInfo: KeyInfoType<*>): IdkResult<GetKeyResult, IdkError> =
        try {
            Ok(GetKeyResult(getKey(keyInfo)))
        } catch (expected: Exception) {
            Err(IdkError.NOT_FOUND_ERROR(resource = "Key", message = expected.message ?: "Key not found"))
        }

    override suspend fun storeKeyResult(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?,
    ): IdkResult<StoreKeyResult, IdkError> = Ok(StoreKeyResult(storeKey(keyInfo, providerId, alias, certChain)))

    override suspend fun deleteKeyResult(keyInfo: KeyInfoType<*>): IdkResult<DeleteKeyResult, IdkError> = Ok(DeleteKeyResult(deleteKey(keyInfo)))

    @Suppress("UNCHECKED_CAST")
    override suspend fun resolvePublicKeyResult(
        keyInfo: KeyInfoType<*>,
        identifierMethod: IdentifierMethod?,
        trustedCerts: Array<String>?,
        verifyX509CertificateChain: Boolean?,
    ): IdkResult<ResolvePublicKeyResult, IdkError> =
        try {
            Ok(ResolvePublicKeyResult(resolvePublicKey(keyInfo, identifierMethod, trustedCerts, verifyX509CertificateChain)))
        } catch (expected: Exception) {
            Err(IdkError.NOT_FOUND_ERROR(resource = "Key", message = expected.message ?: "Could not resolve key"))
        }
}

/**
 * Test mock implementation of KmsProvider for unit testing.
 */
class TestKmsProviderMock : KmsProvider {
    private val keys = mutableMapOf<String, ManagedKeyInfoType<*>>()

    override val id: String = "test-kms-provider"

    override fun supportedKeyTypes(): Array<KeyTypeMapping> =
        arrayOf(
            KeyTypeMapping.EC,
            KeyTypeMapping.RSA,
            KeyTypeMapping.OKP,
        )

    override fun supportedSignatureAlgorithms(): Array<SignatureAlgorithm> =
        arrayOf(
            SignatureAlgorithm.ECDSA_SHA256,
            SignatureAlgorithm.ECDSA_SHA384,
            SignatureAlgorithm.ECDSA_SHA512,
        )

    override fun supportedDigests(): Array<DigestAlg> =
        arrayOf(
            DigestAlg.SHA256,
            DigestAlg.SHA384,
            DigestAlg.SHA512,
        )

    override fun supportedCurves(): Array<Curve> =
        arrayOf(
            Curve.P_256,
            Curve.P_384,
            Curve.P_521,
        )

    override fun isSupportedCurve(curve: Curve): Boolean = curve in supportedCurves()

    override suspend fun generateKeyAsync(
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        certificateOptions: CertificateOptions?,
    ): ManagedKeyPair = throw UnsupportedOperationException("Key generation not implemented in mock")

    override val kmsProviderType: String = "test-mock"

    override suspend fun createRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean,
    ): ByteArray = ByteArray(64) { it.toByte() }

    override suspend fun isValidRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray,
    ): Boolean = true

    override suspend fun createSignature(
        signInput: com.sphereon.crypto.core.sign.model.SignInput,
        keyInfo: KeyInfoType<*>?,
        signatureAlgorithm: SignatureAlgorithm?,
    ): com.sphereon.crypto.core.sign.model.SignOutput {
        val signatureBytes = createRawSignature(keyInfo!!, signInput.input, false)
        return com.sphereon.crypto.core.sign.model.SignOutputData(
            signedData = signatureBytes,
            signatureLevel = com.sphereon.crypto.core.sign.model.SignatureLevel.RAW,
            signingTime =
                kotlin.time.Clock.System
                    .now(),
            name = signInput.name,
            mimeType = null,
        )
    }

    override suspend fun isValidSignature(
        signInput: com.sphereon.crypto.core.sign.model.SignInput,
        signature: com.sphereon.crypto.core.sign.model.Signature,
    ): Boolean = isValidRawSignature(signature.keyInfo, signInput.input, signature.value)

    override val settings: KeyProviderSettings? = null

    override suspend fun listKeys(): Array<ManagedKeyReference> = keys.values.map { it.toKeyReference() }.toTypedArray()

    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        val alias = keyInfo.alias ?: keyInfo.kid ?: throw IllegalArgumentException("Need alias or kid")
        return keys[alias] ?: throw IllegalArgumentException("Key not found: $alias")
    }

    override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?,
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

    override fun getCapabilities(): KmsProviderCapabilities =
        KmsProviderCapabilities(
            providerId = id,
            providerType = kmsProviderType,
            storageTypes = arrayOf(KeyStorageType.EPHEMERAL),
            supportsKeyImport = false,
            supportsKeyExport = true,
            exposePrivateKeys = false,
            operations =
                arrayOf(
                    OperationCapability(KmsProviderOperation.SIGN, true),
                    OperationCapability(KmsProviderOperation.VERIFY, true),
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
            resolutionMethods = emptyArray(),
        )

    override suspend fun encrypt(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?,
    ): EncryptionResult =
        EncryptionResult(
            ciphertext = plaintext.reversedArray(),
            iv = ByteArray(12) { it.toByte() },
            authTag = ByteArray(16) { it.toByte() },
        )

    override suspend fun decrypt(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?,
    ): ByteArray = ciphertext.reversedArray()

    override suspend fun wrapKey(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): ByteArray = keyToWrap.reversedArray()

    override suspend fun unwrapKey(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): ByteArray = wrappedKey.reversedArray()

    override suspend fun performKeyAgreement(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int?,
    ): ByteArray = ByteArray(keyDataLen ?: 32) { it.toByte() }
}

/**
 * Test mock implementation of KeyResolverService for unit testing.
 */
class TestKeyResolverMock : KeyResolverService {
    override fun getId(): String = "test-resolver"

    override fun allSupportedIdentifierMethods(): Array<IdentifierMethod> =
        arrayOf(
            IdentifierMethod.jwk,
            IdentifierMethod.x5c,
        )

    override fun allSupportedKeyTypes(): Array<KeyTypeMapping> =
        arrayOf(
            KeyTypeMapping.EC,
            KeyTypeMapping.RSA,
            KeyTypeMapping.OKP,
        )

    override fun supportedKeyTypesAndIdentifierMethods(): Map<IdentifierMethod, Array<KeyTypeMapping>> = allSupportedIdentifierMethods().associateWith { allSupportedKeyTypes() }

    override fun getSupportedKeyTypes(identifierMethod: IdentifierMethod): Array<KeyTypeMapping> = allSupportedKeyTypes()

    override fun getSupportedIdentifierMethods(keyType: KeyTypeMapping): Array<IdentifierMethod> = allSupportedIdentifierMethods()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <KT : KeyType> resolvePublicKey(
        keyInfo: KeyInfoType<KT>,
        identifierMethod: IdentifierMethod?,
        trustedCerts: Array<String>?,
        verifyX509CertificateChain: Boolean?,
    ): ResolvedKeyInfoType<KT> {
        if (keyInfo is ResolvedKeyInfoType<*>) {
            return keyInfo as ResolvedKeyInfoType<KT>
        }
        throw IllegalArgumentException("Cannot resolve key in test mock: ${keyInfo.alias ?: keyInfo.kid}")
    }
}
