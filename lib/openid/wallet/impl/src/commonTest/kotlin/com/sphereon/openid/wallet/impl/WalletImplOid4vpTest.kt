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
 */

package com.sphereon.openid.wallet.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.EmptyResult
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.EncryptionResult
import com.sphereon.crypto.core.kms.GetAllCapabilitiesResult
import com.sphereon.crypto.core.kms.HasKeyStoreService
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyResolverRegistry
import com.sphereon.crypto.core.kms.KeyResolverService
import com.sphereon.crypto.core.kms.KeyStoreService
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderQuery
import com.sphereon.crypto.core.kms.KmsProviderRegistry
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
import com.sphereon.crypto.core.kms.command.StoreKeyResult
import com.sphereon.crypto.core.kms.command.UnwrapKeyResult
import com.sphereon.crypto.core.kms.command.VerifyRawSignatureResult
import com.sphereon.crypto.core.kms.command.WrapKeyResult
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.toKeyReference
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.data.store.blob.DefaultTempUrlPolicy
import com.sphereon.data.store.blob.InMemoryBlobStoreConfig
import com.sphereon.data.store.blob.impl.BlobStoreService
import com.sphereon.data.store.blob.impl.DefaultBlobService
import com.sphereon.data.store.blob.impl.DefaultRetentionPolicyService
import com.sphereon.data.store.blob.impl.KvBlobMetadataIndex
import com.sphereon.data.store.blob.memory.InMemoryBlobBackingStorageImpl
import com.sphereon.data.store.blob.memory.InMemoryBlobStoreFactoryImpl
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.memory.InMemoryKvBackingStorageImpl
import com.sphereon.data.store.kv.memory.InMemoryKvStoreFactoryImpl
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.oauth2.client.client.AuthorizationResult
import com.sphereon.oauth2.client.client.OAuth2Client
import com.sphereon.oauth2.client.client.OidcLoginApi
import com.sphereon.oauth2.client.client.OidcLoginInitiation
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.FetchUserInfoResult
import com.sphereon.oauth2.client.command.OidcLoginResult
import com.sphereon.oauth2.client.command.VerifyPkceArgs
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.client.service.PkceService
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.IdTokenValidationOptions
import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.common.model.ValidatedIdToken
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import com.sphereon.openid.oid4vci.holder.AuthorizationRequestResult
import com.sphereon.openid.oid4vci.holder.CreatedProof
import com.sphereon.openid.oid4vci.holder.FollowUpIaeArgs
import com.sphereon.openid.oid4vci.holder.IaeHolderResult
import com.sphereon.openid.oid4vci.holder.InitiateIaeArgs
import com.sphereon.openid.oid4vci.holder.Oid4vciHolder
import com.sphereon.openid.oid4vci.holder.ResolvedAuthorizationServer
import com.sphereon.openid.oid4vci.holder.ResolvedCredentialOffer
import com.sphereon.openid.oid4vci.holder.TokenResponseWithContext
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.holder.Oid4vpHolder
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.openid.oid4vp.holder.SubmissionResult
import com.sphereon.openid.oid4vp.holder.VerifierInfo
import com.sphereon.openid.wallet.IdentifierRef
import com.sphereon.openid.wallet.WalletConfig
import com.sphereon.openid.wallet.WalletCredentialInstance
import com.sphereon.openid.wallet.WalletDocument
import com.sphereon.openid.wallet.impl.NoOpWalletIdentityResolver
import com.sphereon.openid.wallet.impl.store.BlobWalletDocumentStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.sphereon.openid.oid4vp.holder.WalletConfig as Oid4vpWalletConfig

// ---------------------------------------------------------------------------
// Blob/KV infrastructure helpers (same pattern as WalletImplOid4vciTest)
// ---------------------------------------------------------------------------

private class Oid4vpTestBlobStoreService(
    private val store: com.sphereon.data.store.blob.BlobStore,
) : BlobStoreService {
    override fun getStoreIds(): Array<String> = arrayOf("memory")

    override fun getStoreConfig(storeId: String) = InMemoryBlobStoreConfig(id = storeId)

    override fun getStore(storeId: String) = store
}

private class Oid4vpTestKvStoreService(
    private val store: com.sphereon.data.store.kv.KvStore,
) : com.sphereon.data.store.kv.impl.KvStoreService {
    override fun getStoreIds(): Array<String> = arrayOf(KvBlobMetadataIndex.STORE_ID)

    override fun getStoreConfig(storeId: String) = InMemoryKvStoreConfig(id = storeId, scopeBinding = KvStoreScopeBinding.APP)

    override fun getStore(storeId: String) = store
}

private class Oid4vpTestSessionExecution : com.sphereon.core.api.context.SessionExecution {
    override val sessionContext: com.sphereon.di.session.SessionContext = com.sphereon.di.context.NoOpSessionContext
    override val sessionContextManager: com.sphereon.di.session.SessionContextManager
        get() = throw NotImplementedError("Not needed for unit tests")
    override val log: com.sphereon.core.api.log.SessionLogService = Oid4vpTestNoOpLogService(sessionContext)
    override val conf: com.sphereon.core.api.context.ContextConfig
        get() = throw NotImplementedError("Not needed for unit tests")
}

private class Oid4vpTestNoOpLogService(
    override val sessionContext: com.sphereon.di.session.SessionContext,
) : com.sphereon.core.api.log.SessionLogService {
    override val id: String = "test-oid4vp-log"
    override val isEnabled: Boolean = false
    override val scope = com.sphereon.core.api.context.IdkScope.SESSION
    override val logManager: com.sphereon.core.api.log.SessionLogManager
        get() = throw NotImplementedError("Not needed for unit tests")

    override suspend fun setConfig(config: com.sphereon.core.api.log.LoggerConfig): com.sphereon.core.api.log.LogService = this

    override fun executeAsync(message: com.sphereon.core.api.log.LogMessage): com.sphereon.core.api.IdkResult<Unit, com.sphereon.core.api.error.IdkErrorType> =
        com.sphereon.core.api
            .Ok(Unit)

    override fun toAsync(): com.sphereon.core.api.log.AsyncLogService = throw NotImplementedError("Not needed for unit tests")
}

private class Oid4vpTestEventService : com.sphereon.core.events.SessionEventService {
    private val hub =
        com.sphereon.core.events.impl
            .EventHubImpl()
    override val scope: com.sphereon.core.api.context.IdkScope = com.sphereon.core.api.context.IdkScope.SESSION
    override val eventHub: com.sphereon.core.events.EventHub = hub
    override val parent: com.sphereon.core.events.UserEventService get() = throw NotImplementedError("Not needed for unit tests")
    override val sessionContext: com.sphereon.di.session.SessionContext = com.sphereon.di.context.NoOpSessionContext

    override suspend fun emit(event: com.sphereon.core.events.Event) {
        hub.publish(event)
    }

    override suspend fun emit(
        event: com.sphereon.core.events.Event,
        sign: Boolean,
        encrypt: Boolean,
        keyAlias: String?,
        encryptionKeyAlias: String?,
        encryptParts: Set<com.sphereon.core.events.EncryptedPart>,
    ) {
        hub.publish(event)
    }

    override fun eventBuilder(): com.sphereon.core.events.EventBuilder =
        com.sphereon.core.events.impl
            .DefaultEventBuilder(com.sphereon.core.api.context.IdkScope.SESSION)
}

private fun createOid4vpTestBlobService(): DefaultBlobService {
    val blobBackingStorage = InMemoryBlobBackingStorageImpl()
    val blobFactory = InMemoryBlobStoreFactoryImpl(blobBackingStorage)
    val blobConfig = InMemoryBlobStoreConfig(id = "memory")
    val memoryStore = blobFactory.create(blobConfig)

    val kvBackingStorage = InMemoryKvBackingStorageImpl()
    val kvFactory = InMemoryKvStoreFactoryImpl(kvBackingStorage)
    val kvConfig = InMemoryKvStoreConfig(id = KvBlobMetadataIndex.STORE_ID, scopeBinding = KvStoreScopeBinding.APP)
    val kvStore = kvFactory.create(kvConfig)

    return DefaultBlobService(
        blobStoreService = Oid4vpTestBlobStoreService(blobFactory.create(blobConfig)),
        metadataIndex = KvBlobMetadataIndex(Oid4vpTestKvStoreService(kvStore)),
        retentionPolicyService = DefaultRetentionPolicyService(),
        tempUrlPolicy = DefaultTempUrlPolicy(),
        eventService = Oid4vpTestEventService(),
        execution = Oid4vpTestSessionExecution(),
    )
}

// ---------------------------------------------------------------------------
// Fake Oid4vciHolder — all stubs; not exercised in OID4VP tests
// ---------------------------------------------------------------------------

private class Oid4vpTestFakeOid4vciHolder : Oid4vciHolder {
    override val commands: Oid4vciHolder.Commands get() = throw NotImplementedError("commands not needed in fake")
    private val err get() = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in oid4vp fake"))

    override suspend fun resolveIssuerMetadata(issuerUrl: String): IdkResult<CredentialIssuerMetadata, IdkError> = err

    override suspend fun selectAuthorizationServer(
        issuerMetadata: CredentialIssuerMetadata,
        preferredAuthorizationServer: String?
    ): IdkResult<ResolvedAuthorizationServer, IdkError> = err

    override suspend fun exchangePreAuthorizedCode(
        tokenEndpoint: String,
        preAuthorizedCode: String,
        txCode: String?,
        clientId: String?,
        redirectUri: String?
    ): IdkResult<TokenResponseWithContext, IdkError> = err

    override suspend fun createCredentialRequestProof(
        issuerUrl: String,
        cNonce: String?,
        signingKeyId: String,
        signingAlgorithm: String,
        clientId: String?,
        count: Int,
        keyInclusionMode: com.sphereon.crypto.jose.jws.JwsIdentifierMode
    ): IdkResult<CreatedProof, IdkError> = err

    override suspend fun requestCredential(
        credentialEndpoint: String,
        accessToken: String,
        credentialConfigurationId: String?,
        credentialIdentifier: String?,
        proofs: CredentialRequestProofs?,
        credentialResponseEncryption: RequestedCredentialResponseEncryption?,
        requestEncryptionJwk: JsonObject?,
        requestEncryptionAlg: String?,
        requestEncryptionEnc: String?,
        decryptionKeyId: String?
    ): IdkResult<CredentialResponse, IdkError> = err

    override suspend fun parseCredentialOffer(rawOffer: String): IdkResult<CredentialOffer, IdkError> = err

    override suspend fun resolveCredentialOffer(offer: CredentialOffer): IdkResult<ResolvedCredentialOffer, IdkError> = err

    override suspend fun requestNonce(nonceEndpoint: String): IdkResult<NonceResponse, IdkError> = err

    override suspend fun requestDeferredCredential(
        deferredCredentialEndpoint: String,
        accessToken: String,
        transactionId: String,
        credentialResponseEncryption: RequestedCredentialResponseEncryption?,
        requestEncryptionJwk: JsonObject?,
        requestEncryptionAlg: String?,
        requestEncryptionEnc: String?,
        decryptionKeyId: String?
    ): IdkResult<CredentialResponse, IdkError> = err

    override suspend fun sendNotification(
        notificationEndpoint: String,
        accessToken: String,
        notificationId: String,
        event: CredentialNotificationEvent,
        eventDescription: String?
    ): IdkResult<Unit, IdkError> = err

    override suspend fun followUpIae(args: FollowUpIaeArgs): IdkResult<IaeHolderResult, IdkError> = err

    override suspend fun initiateIae(args: InitiateIaeArgs): IdkResult<IaeHolderResult, IdkError> = err

    override suspend fun buildAuthorizationRequest(
        authorizationEndpoint: String,
        clientId: String,
        redirectUri: String,
        credentialConfigurationIds: List<String>,
        scope: String?,
        issuerState: String?,
        usePar: Boolean,
        parEndpoint: String?,
        credentialIdentifiers: Map<String, List<String>>?,
        locations: List<String>?
    ): IdkResult<AuthorizationRequestResult, IdkError> = err

    override suspend fun exchangeAuthorizationCode(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String,
        redirectUri: String,
        clientId: String?
    ): IdkResult<TokenResponseWithContext, IdkError> = err
}

// ---------------------------------------------------------------------------
// Fake Oid4vpHolder — records selectedCredentials passed to createAuthorizationResponse
// ---------------------------------------------------------------------------

private class RecordingOid4vpHolder(
    private val resolvedRequest: ResolvedOid4vpRequest,
) : Oid4vpHolder {
    override val commands: Oid4vpHolder.Commands get() = throw NotImplementedError("commands not needed in fake")

    var capturedSelectedCredentials: List<SelectedCredential>? = null
        private set

    override suspend fun parseAuthorizationRequest(
        requestUri: String,
        walletConfig: Oid4vpWalletConfig?,
    ): IdkResult<AuthorizationRequest, IdkError> = Ok(AuthorizationRequest(responseType = "vp_token", clientId = "verifier"))

    override suspend fun resolveAuthorizationRequest(request: AuthorizationRequest): IdkResult<ResolvedOid4vpRequest, IdkError> = Ok(resolvedRequest)

    override suspend fun createAuthorizationResponse(
        request: ResolvedOid4vpRequest,
        selectedCredentials: List<SelectedCredential>,
    ): IdkResult<AuthorizationResponse, IdkError> {
        capturedSelectedCredentials = selectedCredentials
        return Ok(AuthorizationResponse(code = "test-code", state = "test-state"))
    }

    override suspend fun submitAuthorizationResponse(
        resolvedRequest: ResolvedOid4vpRequest,
        response: AuthorizationResponse,
        responseMode: ResponseMode?,
    ): IdkResult<SubmissionResult, IdkError> = Ok(SubmissionResult.Success(redirectUri = null))
}

// ---------------------------------------------------------------------------
// Fake KeyManagerService — minimal stub (no keys needed for present() tests)
// ---------------------------------------------------------------------------

private class Oid4vpTestFakeKeyManagerService : KeyManagerService {
    private val storedKeys = mutableMapOf<String, ManagedKeyInfoType<*>>()

    override fun defaultProviderId(): String = "fake-provider"

    override fun getProviderIds(): Array<String> = arrayOf("fake-provider")

    override fun getProviderById(id: String): KmsProvider = throw UnsupportedOperationException("not needed in fake")

    override fun getKmsBySignatureAlgorithm(signatureAlgorithm: SignatureAlgorithm): KmsProvider = throw UnsupportedOperationException("not needed in fake")

    override fun getProvider(
        providerId: String?,
        alg: SignatureAlgorithm?
    ): KmsProvider = throw UnsupportedOperationException("not needed in fake")

    override fun registerProvider(
        provider: KmsProvider,
        makeDefaultKms: Boolean?
    ) {}

    override fun defaultResolverId(): String = "fake-resolver"

    override fun getResolverIds(): Array<String> = emptyArray()

    override fun getResolverById(id: String): KeyResolverService = throw UnsupportedOperationException("not needed in fake")

    override fun getResolverByKeyTypeOrIdentifier(
        identifierMethod: IdentifierMethod?,
        keyType: KeyTypeMapping?,
        resolverId: String?
    ): KeyResolverService = throw UnsupportedOperationException("not needed in fake")

    override fun registerResolver(
        resolver: KeyResolverService,
        makeDefaultResolver: Boolean?
    ) {}

    override val keyStore: KeyStoreService get() = throw UnsupportedOperationException("keyStore not needed in fake")
    override val settings: KeyProviderSettings? = null

    override suspend fun listKeys(): Array<ManagedKeyReference> = emptyArray()

    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> = throw IllegalArgumentException("Key not found")

    override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?
    ): ManagedKeyInfoType<*> {
        val managed = ManagedKeyInfo(alias = alias, providerId = providerId, resolvedKeyInfo = keyInfo)
        storedKeys[alias] = managed
        return managed
    }

    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean = false

    override fun keyVisibility(): KeyVisibility = KeyVisibility.PUBLIC

    override suspend fun createRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean
    ): ByteArray = ByteArray(64)

    override suspend fun isValidRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray
    ): Boolean = true

    override suspend fun encrypt(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?
    ): EncryptionResult = EncryptionResult(ciphertext = plaintext, iv = ByteArray(12), authTag = ByteArray(16))

    override suspend fun decrypt(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?
    ): ByteArray = ciphertext

    override suspend fun wrapKey(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): ByteArray = keyToWrap

    override suspend fun unwrapKey(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): ByteArray = wrappedKey

    override suspend fun performKeyAgreement(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int?
    ): ByteArray = ByteArray(keyDataLen ?: 32)

    @Suppress("UNCHECKED_CAST")
    override suspend fun <KT : KeyType> resolvePublicKey(
        keyInfo: KeyInfoType<KT>,
        identifierMethod: IdentifierMethod?,
        trustedCerts: Array<String>?,
        verifyX509CertificateChain: Boolean?
    ): ResolvedKeyInfoType<KT> {
        if (keyInfo is ResolvedKeyInfoType<*>) return keyInfo as ResolvedKeyInfoType<KT>
        throw IllegalArgumentException("Cannot resolve key: ${keyInfo.alias ?: keyInfo.kid}")
    }

    override suspend fun generateKey(
        providerId: String?,
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        keyVisibility: KeyVisibility?
    ): ManagedKeyPair = throw UnsupportedOperationException("not needed in fake")

    @Deprecated("Use generateKey instead.", ReplaceWith("generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)"))
    override suspend fun generateKeyAsync(
        providerId: String?,
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        keyVisibility: KeyVisibility?
    ): ManagedKeyPair = generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)

    override suspend fun generateKeyResult(
        providerId: String?,
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        keyVisibility: KeyVisibility?
    ): IdkResult<GenerateKeyResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

    override suspend fun createRawSignatureResult(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean
    ): IdkResult<CreateRawSignatureResult, IdkError> = Ok(CreateRawSignatureResult(createRawSignature(keyInfo, input, requireX5Chain)))

    override suspend fun verifyRawSignatureResult(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray
    ): IdkResult<VerifyRawSignatureResult, IdkError> = Ok(VerifyRawSignatureResult(isValidRawSignature(keyInfo, input, signature)))

    override suspend fun encryptResult(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?
    ): IdkResult<EncryptResult, IdkError> {
        val r = encrypt(keyInfo, plaintext, algorithm, additionalAuthenticatedData)
        return Ok(EncryptResult(ciphertext = r.ciphertext, iv = r.iv, authTag = r.authTag))
    }

    override suspend fun decryptResult(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?
    ): IdkResult<DecryptResult, IdkError> = Ok(DecryptResult(decrypt(keyInfo, ciphertext, algorithm, iv, authTag, additionalAuthenticatedData)))

    override suspend fun wrapKeyResult(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): IdkResult<WrapKeyResult, IdkError> = Ok(WrapKeyResult(wrapKey(wrappingKeyInfo, keyToWrap, algorithm)))

    override suspend fun unwrapKeyResult(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm
    ): IdkResult<UnwrapKeyResult, IdkError> = Ok(UnwrapKeyResult(unwrapKey(unwrappingKeyInfo, wrappedKey, algorithm)))

    override suspend fun performKeyAgreementResult(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int?
    ): IdkResult<PerformKeyAgreementResult, IdkError> = Ok(PerformKeyAgreementResult(performKeyAgreement(privateKeyInfo, publicKeyInfo, algorithm, keyDataLen)))

    override suspend fun listKeysResult(providerId: String?): IdkResult<ListKeysResult, IdkError> = Ok(ListKeysResult(listKeys()))

    override suspend fun getKeyResult(keyInfo: KeyInfoType<*>): IdkResult<GetKeyResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "Key not found"))

    override suspend fun storeKeyResult(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?
    ): IdkResult<StoreKeyResult, IdkError> = Ok(StoreKeyResult(storeKey(keyInfo, providerId, alias, certChain)))

    override suspend fun deleteKeyResult(keyInfo: KeyInfoType<*>): IdkResult<DeleteKeyResult, IdkError> = Ok(DeleteKeyResult(deleteKey(keyInfo)))

    @Suppress("UNCHECKED_CAST")
    override suspend fun resolvePublicKeyResult(
        keyInfo: KeyInfoType<*>,
        identifierMethod: IdentifierMethod?,
        trustedCerts: Array<String>?,
        verifyX509CertificateChain: Boolean?
    ): IdkResult<ResolvePublicKeyResult, IdkError> =
        try {
            Ok(ResolvePublicKeyResult(resolvePublicKey(keyInfo, identifierMethod, trustedCerts, verifyX509CertificateChain)))
        } catch (
            e: Exception
        ) {
            Err(IdkError.UNKNOWN_ERROR(message = e.message ?: "Could not resolve key"))
        }

    override suspend fun queryProvider(query: KmsProviderQuery): IdkResult<QueryProviderResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "no providers in fake"))

    override suspend fun queryProviders(query: KmsProviderQuery): IdkResult<QueryProvidersResult, IdkError> = Ok(QueryProvidersResult(matches = emptyArray(), totalProviders = 0))

    override suspend fun getAllCapabilities(includeDisabled: Boolean): IdkResult<GetAllCapabilitiesResult, IdkError> = Ok(GetAllCapabilitiesResult(capabilities = emptyMap()))
}

// ---------------------------------------------------------------------------
// Fake OAuth2Client and PkceService — not exercised in present() tests
// ---------------------------------------------------------------------------

private class Oid4vpTestFakeOAuth2Client : OAuth2Client {
    private val err get() = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in oid4vp fake"))

    override suspend fun fetchAuthorizationServerMetadata(issuer: String): IdkResult<AuthorizationServerMetadata, IdkError> = err

    override fun isDpopSupported(authorizationServerMetadata: AuthorizationServerMetadata): Boolean = false

    override val oidcLogin: OidcLoginApi get() = throw NotImplementedError("not needed in fake")

    override suspend fun initiateOidcLogin(
        issuer: String,
        clientId: String,
        redirectUri: String,
        scopes: Set<String>,
        responseMode: OAuth2ResponseMode,
        prompt: String?,
        loginHint: String?,
        tenantId: String?
    ): IdkResult<OidcLoginInitiation, IdkError> = err

    override suspend fun initiateOidcLogin(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientId: String,
        redirectUri: String,
        scopes: Set<String>,
        responseMode: OAuth2ResponseMode,
        prompt: String?,
        loginHint: String?,
        tenantId: String?
    ): IdkResult<OidcLoginInitiation, IdkError> = err

    override suspend fun initiateAuthorization(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientId: String,
        redirectUri: String,
        scope: String?,
        state: String?,
        resource: List<String>?,
        clientAuthentication: ClientAuthenticationConfig?,
        dpopContext: com.sphereon.oauth2.client.client.DpopContext?,
        additionalParameters: Map<String, String>
    ): IdkResult<AuthorizationResult, IdkError> = err

    override suspend fun parseAuthorizationResponse(redirectUrl: String): IdkResult<AuthorizationResponse, IdkError> = err

    override suspend fun exchangeAuthorizationCode(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        authorizationCode: String,
        redirectUri: String,
        pkceData: PkceData?,
        resource: List<String>?,
        dpopContext: com.sphereon.oauth2.client.client.DpopContext?
    ): IdkResult<TokenResponse, IdkError> = err

    override suspend fun exchangePreAuthorizedCode(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        preAuthorizedCode: String,
        txCode: String?,
        resource: List<String>?,
        dpopContext: com.sphereon.oauth2.client.client.DpopContext?
    ): IdkResult<TokenResponse, IdkError> = err

    override suspend fun refreshAccessToken(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        refreshToken: String,
        scope: String?,
        resource: List<String>?,
        dpopContext: com.sphereon.oauth2.client.client.DpopContext?
    ): IdkResult<TokenResponse, IdkError> = err

    override suspend fun introspectToken(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        token: String,
        tokenTypeHint: String?
    ): IdkResult<TokenIntrospectionResponse, IdkError> = err

    override suspend fun validateIdToken(
        idToken: String,
        options: IdTokenValidationOptions
    ): IdkResult<ValidatedIdToken, IdkError> = err

    override suspend fun fetchUserInfo(
        accessToken: String,
        metadata: AuthorizationServerMetadata
    ): IdkResult<FetchUserInfoResult, IdkError> = err
}

private class Oid4vpTestFakePkceService : PkceService {
    private val err get() = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in oid4vp fake"))

    override suspend fun createPkce(args: CreatePkceArgs): IdkResult<PkceData, IdkError> = err

    override suspend fun verifyPkce(args: VerifyPkceArgs): IdkResult<EmptyResult, IdkError> = err

    override val commands: PkceService.Commands get() = throw NotImplementedError("not needed in fake")
}

// ---------------------------------------------------------------------------
// Test fixture helpers
// ---------------------------------------------------------------------------

private const val VCT_EMP = "vct:emp"
private const val QUERY_ID = "q1"
private const val REQUEST_URI = "openid4vp://?client_id=verifier&request_uri=https://verifier.example.com/req"

private fun buildResolvedRequest(): ResolvedOid4vpRequest {
    val metaJson =
        JsonObject(
            mapOf("vct_values" to JsonArray(listOf(JsonPrimitive(VCT_EMP))))
        )
    val query =
        DcqlCredentialQuery(
            id = QUERY_ID,
            format = "dc+sd-jwt",
            meta = metaJson,
        )
    return ResolvedOid4vpRequest(
        request = AuthorizationRequest(responseType = "vp_token", clientId = "verifier"),
        dcqlQuery = DcqlQuery(credentials = listOf(query)),
        verifierInfo =
            VerifierInfo(
                clientId = "verifier",
                clientIdScheme = ClientIdScheme.PRE_REGISTERED,
            ),
    )
}

private fun buildWalletWithHolder(holder: Oid4vpHolder): Pair<WalletImpl, BlobWalletDocumentStore> {
    val store = BlobWalletDocumentStore(createOid4vpTestBlobService())
    val wallet =
        WalletImpl(
            documents = store,
            oid4vciHolder = Oid4vpTestFakeOid4vciHolder(),
            oid4vpHolder = holder,
            oauth2Client = Oid4vpTestFakeOAuth2Client(),
            pkceService = Oid4vpTestFakePkceService(),
            keyManagerService = Oid4vpTestFakeKeyManagerService(),
            identityResolver = NoOpWalletIdentityResolver(),
            subjectExtractor = CredentialSubjectExtractorImpl(),
            verifySdJwtVcCommand = FakeVerifySdJwtVcCommand(accept = true),
        )
    return wallet to store
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class WalletImplOid4vpTest {
    @Test
    fun presentSucceedsAndRecordsSelectedCredential() =
        runTest {
            val resolvedRequest = buildResolvedRequest()
            val holder = RecordingOid4vpHolder(resolvedRequest)
            val (wallet, store) = buildWalletWithHolder(holder)

            val instance0 =
                WalletCredentialInstance(
                    credentialId = "vc-0",
                    format = "dc+sd-jwt",
                    raw = "raw-vc-0",
                    holderKeyAlias = "holder-key-1",
                )
            val instance1 =
                WalletCredentialInstance(
                    credentialId = "vc-1",
                    format = "dc+sd-jwt",
                    raw = "raw-vc-1",
                    holderKeyAlias = "holder-key-1",
                )
            val doc =
                WalletDocument(
                    id = "doc-1",
                    issuer = IdentifierRef(type = IdentifierType("https"), value = "https://issuer.example.com"),
                    credentialTypeId = VCT_EMP,
                    credentials = listOf(instance0, instance1),
                )
            val upsertResult = store.upsert(doc)
            assertTrue(upsertResult.isOk, "upsert should succeed")

            val result = wallet.present(REQUEST_URI, WalletConfig("client", "http://localhost/cb"))

            assertTrue(result.isOk, "present() should succeed: ${if (result.isErr) result.error else ""}")
            assertTrue(result.value.submitted, "PresentationResult.submitted should be true")

            val captured = holder.capturedSelectedCredentials
            assertNotNull(captured, "createAuthorizationResponse must have been called")
            assertEquals(1, captured.size, "exactly one SelectedCredential expected")

            val sel = captured.first()
            assertEquals(QUERY_ID, sel.credentialQueryId, "credentialQueryId must match DCQL query id")
            assertFalse(sel.presentation.isBlank(), "presentation must be non-blank")
        }

    @Test
    fun presentReturnsNotFoundWhenNoMatchingCredential() =
        runTest {
            val resolvedRequest = buildResolvedRequest()
            val holder = RecordingOid4vpHolder(resolvedRequest)
            val (wallet, _) = buildWalletWithHolder(holder)

            val result = wallet.present(REQUEST_URI, WalletConfig("client", "http://localhost/cb"))

            assertTrue(result.isErr, "present() should fail when no credential matches")
        }

    @Test
    fun presentSetsBoundToOnInstanceWithNoOpResolver() =
        runTest {
            val resolvedRequest = buildResolvedRequest()
            val holder = RecordingOid4vpHolder(resolvedRequest)
            val (wallet, store) = buildWalletWithHolder(holder)

            val instance =
                WalletCredentialInstance(
                    credentialId = "vc-bound-test",
                    format = "dc+sd-jwt",
                    raw = "raw-bound",
                    holderKeyAlias = "holder-key-1",
                )
            val doc =
                WalletDocument(
                    id = "doc-bound",
                    issuer = IdentifierRef(type = IdentifierType("https"), value = "https://issuer.example.com"),
                    credentialTypeId = VCT_EMP,
                    credentials = listOf(instance),
                )
            assertTrue(store.upsert(doc).isOk, "upsert should succeed")

            val result = wallet.present(REQUEST_URI, WalletConfig("client", "http://localhost/cb"))
            assertTrue(result.isOk, "present() should succeed: ${if (result.isErr) result.error else ""}")

            // Retrieve the updated document and verify boundTo was set.
            val updatedDocResult = store.get("doc-bound")
            assertTrue(updatedDocResult.isOk)
            val updatedDoc = updatedDocResult.value
            assertNotNull(updatedDoc, "document should still exist after present()")

            val boundInstance = updatedDoc.credentials.firstOrNull { it.credentialId == "vc-bound-test" }
            assertNotNull(boundInstance, "instance should still be in the document")
            assertNotNull(boundInstance.boundTo, "boundTo should be set after presentation")
            assertEquals("verifier", boundInstance.boundTo!!.value, "boundTo.value should be the verifier client_id")
            // NoOp resolver returns ref unchanged — no correlationId enrichment.
            assertEquals(null, boundInstance.boundTo!!.correlationId, "no-op resolver leaves correlationId null")

            val now = kotlin.time.Instant.fromEpochSeconds(1_800_000_000)
            val metadata = updatedDoc.metadata(now)
            assertEquals(1, metadata.boundInstanceCount, "boundInstanceCount should be 1 after one presentation")
        }
}
