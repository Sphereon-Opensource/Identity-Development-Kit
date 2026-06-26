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
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.EmptyResult
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ResolvedKeyInfo
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
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
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
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.holder.Oid4vpHolder
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.openid.oid4vp.holder.SubmissionResult
import com.sphereon.openid.oid4vp.holder.WalletConfig
import com.sphereon.openid.wallet.ObtainCredentialRequest
import com.sphereon.openid.wallet.impl.NoOpWalletIdentityResolver
import com.sphereon.openid.wallet.impl.store.BlobWalletDocumentStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Shared blob service factory (mirrors BlobWalletDocumentStoreTest)
// ---------------------------------------------------------------------------

private class WalletImplTestBlobStoreService(
    private val store: com.sphereon.data.store.blob.BlobStore,
) : BlobStoreService {
    override fun getStoreIds(): Array<String> = arrayOf("memory")

    override fun getStoreConfig(storeId: String) = InMemoryBlobStoreConfig(id = storeId)

    override fun getStore(storeId: String) = store
}

private class WalletImplTestKvStoreService(
    private val store: com.sphereon.data.store.kv.KvStore,
) : com.sphereon.data.store.kv.impl.KvStoreService {
    override fun getStoreIds(): Array<String> = arrayOf(KvBlobMetadataIndex.STORE_ID)

    override fun getStoreConfig(storeId: String) = InMemoryKvStoreConfig(id = storeId, scopeBinding = KvStoreScopeBinding.APP)

    override fun getStore(storeId: String) = store
}

private class WalletImplTestSessionExecution : com.sphereon.core.api.context.SessionExecution {
    override val sessionContext: com.sphereon.di.session.SessionContext = com.sphereon.di.context.NoOpSessionContext
    override val sessionContextManager: com.sphereon.di.session.SessionContextManager
        get() = throw NotImplementedError("Not needed for unit tests")
    override val log: com.sphereon.core.api.log.SessionLogService = WalletImplTestNoOpLogService(sessionContext)
    override val conf: com.sphereon.core.api.context.ContextConfig
        get() = throw NotImplementedError("Not needed for unit tests")
}

private class WalletImplTestNoOpLogService(
    override val sessionContext: com.sphereon.di.session.SessionContext,
) : com.sphereon.core.api.log.SessionLogService {
    override val id: String = "test-wallet-impl-log"
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

private class WalletImplTestEventService : com.sphereon.core.events.SessionEventService {
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

private fun createTestBlobService(): DefaultBlobService {
    val blobBackingStorage = InMemoryBlobBackingStorageImpl()
    val blobFactory = InMemoryBlobStoreFactoryImpl(blobBackingStorage)
    val blobConfig = InMemoryBlobStoreConfig(id = "memory")
    val memoryStore = blobFactory.create(blobConfig)

    val kvBackingStorage = InMemoryKvBackingStorageImpl()
    val kvFactory = InMemoryKvStoreFactoryImpl(kvBackingStorage)
    val kvConfig = InMemoryKvStoreConfig(id = KvBlobMetadataIndex.STORE_ID, scopeBinding = KvStoreScopeBinding.APP)
    val kvStore = kvFactory.create(kvConfig)

    return DefaultBlobService(
        blobStoreService = WalletImplTestBlobStoreService(blobFactory.create(blobConfig)),
        metadataIndex = KvBlobMetadataIndex(WalletImplTestKvStoreService(kvStore)),
        retentionPolicyService = DefaultRetentionPolicyService(),
        tempUrlPolicy = DefaultTempUrlPolicy(),
        eventService = WalletImplTestEventService(),
        execution = WalletImplTestSessionExecution(),
    )
}

// ---------------------------------------------------------------------------
// Constants shared across fakes and tests
// ---------------------------------------------------------------------------

private const val ISSUER_URL = "https://issuer.example.com"
private const val CONFIG_ID = "EmployeeCredential"
private const val TOKEN_ENDPOINT = "https://issuer.example.com/token"
private const val CREDENTIAL_ENDPOINT = "https://issuer.example.com/credential"
private const val NONCE_ENDPOINT = "https://issuer.example.com/nonce"
private const val FRESH_NONCE = "n-fresh"

private val fakeIssuerMetadata =
    CredentialIssuerMetadata(
        credentialIssuer = ISSUER_URL,
        credentialEndpoint = CREDENTIAL_ENDPOINT,
        nonceEndpoint = NONCE_ENDPOINT,
        credentialConfigurationsSupported =
            mapOf(
                CONFIG_ID to
                    CredentialConfigurationSupported(
                        format = "dc+sd-jwt",
                        display = listOf(DisplayProperties(name = "Employee Credential", locale = "en")),
                    ),
            ),
        display = listOf(DisplayProperties(name = "Acme", locale = "en")),
    )

private val fakeResolvedAs =
    ResolvedAuthorizationServer(
        authorizationServerUrl = ISSUER_URL,
        metadata =
            buildJsonObject {
                put("token_endpoint", TOKEN_ENDPOINT)
            },
    )

// ---------------------------------------------------------------------------
// Fake Oid4vciHolder
// ---------------------------------------------------------------------------

private class FakeOid4vciHolder : Oid4vciHolder {
    override val commands: Oid4vciHolder.Commands get() = throw NotImplementedError("commands not needed in fake")

    override suspend fun resolveIssuerMetadata(issuerUrl: String): IdkResult<CredentialIssuerMetadata, IdkError> = Ok(fakeIssuerMetadata)

    override suspend fun selectAuthorizationServer(
        issuerMetadata: CredentialIssuerMetadata,
        preferredAuthorizationServer: String?,
    ): IdkResult<ResolvedAuthorizationServer, IdkError> = Ok(fakeResolvedAs)

    override suspend fun exchangePreAuthorizedCode(
        tokenEndpoint: String,
        preAuthorizedCode: String,
        txCode: String?,
        clientId: String?,
        redirectUri: String?,
    ): IdkResult<TokenResponseWithContext, IdkError> = Ok(TokenResponseWithContext(accessToken = "at", tokenType = "Bearer", cNonce = "n1"))

    override suspend fun createCredentialRequestProof(
        issuerUrl: String,
        cNonce: String?,
        signingKeyId: String,
        signingAlgorithm: String,
        clientId: String?,
        count: Int,
        keyInclusionMode: com.sphereon.crypto.jose.jws.JwsIdentifierMode,
    ): IdkResult<CreatedProof, IdkError> = Ok(CreatedProof(proofs = CredentialRequestProofs.jwt((0 until count).map { "fake-jwt-proof-$it" })))

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
        decryptionKeyId: String?,
    ): IdkResult<CredentialResponse, IdkError> {
        val count = proofs?.proofValues?.size ?: 1
        return Ok(
            CredentialResponse(
                credentials =
                    (0 until count).map { i ->
                        CredentialResponseItem(credential = JsonPrimitive("vc-$i"))
                    }
            )
        )
    }

    override suspend fun parseCredentialOffer(rawOffer: String): IdkResult<CredentialOffer, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun resolveCredentialOffer(offer: CredentialOffer): IdkResult<ResolvedCredentialOffer, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun requestNonce(nonceEndpoint: String): IdkResult<NonceResponse, IdkError> = Ok(NonceResponse(cNonce = FRESH_NONCE))

    override suspend fun requestDeferredCredential(
        deferredCredentialEndpoint: String,
        accessToken: String,
        transactionId: String,
        credentialResponseEncryption: RequestedCredentialResponseEncryption?,
        requestEncryptionJwk: JsonObject?,
        requestEncryptionAlg: String?,
        requestEncryptionEnc: String?,
        decryptionKeyId: String?,
    ): IdkResult<CredentialResponse, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun sendNotification(
        notificationEndpoint: String,
        accessToken: String,
        notificationId: String,
        event: CredentialNotificationEvent,
        eventDescription: String?,
    ): IdkResult<Unit, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun followUpIae(args: FollowUpIaeArgs): IdkResult<IaeHolderResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun initiateIae(args: InitiateIaeArgs): IdkResult<IaeHolderResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

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
        locations: List<String>?,
    ): IdkResult<AuthorizationRequestResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun exchangeAuthorizationCode(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String,
        redirectUri: String,
        clientId: String?,
    ): IdkResult<TokenResponseWithContext, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))
}

// ---------------------------------------------------------------------------
// Helper: build a minimal SD-JWT whose issuer-JWT contains the given payload JSON.
// No real signature; CredentialSubjectExtractorImpl only reads the payload part.
// ---------------------------------------------------------------------------

private fun buildTestSdJwt(payloadJson: String): String {
    val header = """{"alg":"ES256","typ":"dc+sd-jwt"}""".encodeToByteArray().encodeToBase64Url()
    val payload = payloadJson.encodeToByteArray().encodeToBase64Url()
    return "$header.$payload.fakesig~"
}

// A variant of FakeOid4vciHolder that returns a single SD-JWT credential with a `sub` claim.
private class FakeOid4vciHolderWithSubject(
    private val subjectDid: String
) : Oid4vciHolder {
    override val commands: Oid4vciHolder.Commands get() = throw NotImplementedError("commands not needed in fake")

    override suspend fun resolveIssuerMetadata(issuerUrl: String): IdkResult<CredentialIssuerMetadata, IdkError> = Ok(fakeIssuerMetadata)

    override suspend fun selectAuthorizationServer(
        issuerMetadata: CredentialIssuerMetadata,
        preferredAuthorizationServer: String?,
    ): IdkResult<ResolvedAuthorizationServer, IdkError> = Ok(fakeResolvedAs)

    override suspend fun exchangePreAuthorizedCode(
        tokenEndpoint: String,
        preAuthorizedCode: String,
        txCode: String?,
        clientId: String?,
        redirectUri: String?,
    ): IdkResult<TokenResponseWithContext, IdkError> = Ok(TokenResponseWithContext(accessToken = "at", tokenType = "Bearer", cNonce = "n1"))

    override suspend fun createCredentialRequestProof(
        issuerUrl: String,
        cNonce: String?,
        signingKeyId: String,
        signingAlgorithm: String,
        clientId: String?,
        count: Int,
        keyInclusionMode: com.sphereon.crypto.jose.jws.JwsIdentifierMode,
    ): IdkResult<CreatedProof, IdkError> = Ok(CreatedProof(proofs = CredentialRequestProofs.jwt((0 until count).map { "fake-jwt-proof-$it" })))

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
        decryptionKeyId: String?,
    ): IdkResult<CredentialResponse, IdkError> {
        val raw = buildTestSdJwt("""{"iss":"$ISSUER_URL","sub":"$subjectDid","vct":"$CONFIG_ID"}""")
        return Ok(CredentialResponse(credentials = listOf(CredentialResponseItem(credential = JsonPrimitive(raw)))))
    }

    override suspend fun parseCredentialOffer(rawOffer: String): IdkResult<CredentialOffer, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun resolveCredentialOffer(offer: CredentialOffer): IdkResult<ResolvedCredentialOffer, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun requestNonce(nonceEndpoint: String): IdkResult<NonceResponse, IdkError> = Ok(NonceResponse(cNonce = FRESH_NONCE))

    override suspend fun requestDeferredCredential(
        deferredCredentialEndpoint: String,
        accessToken: String,
        transactionId: String,
        credentialResponseEncryption: RequestedCredentialResponseEncryption?,
        requestEncryptionJwk: JsonObject?,
        requestEncryptionAlg: String?,
        requestEncryptionEnc: String?,
        decryptionKeyId: String?,
    ): IdkResult<CredentialResponse, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun sendNotification(
        notificationEndpoint: String,
        accessToken: String,
        notificationId: String,
        event: CredentialNotificationEvent,
        eventDescription: String?,
    ): IdkResult<Unit, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun followUpIae(args: FollowUpIaeArgs): IdkResult<IaeHolderResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun initiateIae(args: InitiateIaeArgs): IdkResult<IaeHolderResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

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
        locations: List<String>?,
    ): IdkResult<AuthorizationRequestResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun exchangeAuthorizationCode(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String,
        redirectUri: String,
        clientId: String?,
    ): IdkResult<TokenResponseWithContext, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))
}

// ---------------------------------------------------------------------------
// Fake Oid4vpHolder
// ---------------------------------------------------------------------------

private class FakeOid4vpHolder : Oid4vpHolder {
    override val commands: Oid4vpHolder.Commands get() = throw NotImplementedError("commands not needed in fake")

    override suspend fun parseAuthorizationRequest(
        requestUri: String,
        walletConfig: WalletConfig?,
    ): IdkResult<AuthorizationRequest, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun resolveAuthorizationRequest(request: AuthorizationRequest): IdkResult<ResolvedOid4vpRequest, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun createAuthorizationResponse(
        request: ResolvedOid4vpRequest,
        selectedCredentials: List<SelectedCredential>,
    ): IdkResult<AuthorizationResponse, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun submitAuthorizationResponse(
        resolvedRequest: ResolvedOid4vpRequest,
        response: AuthorizationResponse,
        responseMode: ResponseMode?,
    ): IdkResult<SubmissionResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))
}

// ---------------------------------------------------------------------------
// Fake KeyManagerService — minimal, based on TestKmsMock pattern
// ---------------------------------------------------------------------------

private class FakeKeyManagerService : KeyManagerService {
    private val storedKeys = mutableMapOf<String, ManagedKeyInfoType<*>>()

    // --- KmsProviderRegistry ---
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

    // --- KeyResolverRegistry ---
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

    // --- HasKeyStoreService ---
    override val keyStore: KeyStoreService get() = throw UnsupportedOperationException("keyStore not needed in fake")

    // --- KeyStoreService (ManagedKeyStoreService) ---
    override val settings: KeyProviderSettings? = null

    override suspend fun listKeys(): Array<ManagedKeyReference> = storedKeys.values.map { it.toKeyReference() }.toTypedArray()

    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        val alias = keyInfo.alias ?: keyInfo.kid ?: throw IllegalArgumentException("Need alias or kid")
        return storedKeys[alias] ?: throw IllegalArgumentException("Key not found: $alias")
    }

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

    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        val alias = keyInfo.alias ?: return false
        return storedKeys.remove(alias) != null
    }

    override fun keyVisibility(): KeyVisibility = KeyVisibility.PUBLIC

    // --- SimpleSignatureService ---
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

    // --- EncryptionService ---
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

    // --- PublicKeyResolver ---
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

    // --- Command-style IdkResult methods ---
    override suspend fun generateKey(
        providerId: String?,
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        keyVisibility: KeyVisibility?
    ): ManagedKeyPair = createFakeKeyPair(alias ?: "holder-key-1")

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
    ): IdkResult<GenerateKeyResult, IdkError> = Ok(GenerateKeyResult(keyPair = generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)))

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

    override suspend fun getKeyResult(keyInfo: KeyInfoType<*>): IdkResult<GetKeyResult, IdkError> =
        try {
            Ok(GetKeyResult(getKey(keyInfo)))
        } catch (e: Exception) {
            Err(IdkError.UNKNOWN_ERROR(message = e.message ?: "Key not found"))
        }

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

private fun createFakeKeyPair(alias: String): ManagedKeyPair {
    val fakeJwk =
        com.sphereon.crypto.core.jose.Jwk(
            kty = com.sphereon.crypto.core.jose.JwaKeyType.EC,
            crv = com.sphereon.crypto.core.jose.JwaCurve.P_256,
            x = "fake-x",
            y = "fake-y",
            kid = alias,
        )
    val josePair =
        com.sphereon.crypto.core.generic
            .JoseKeyPair(privateJwk = null, publicJwk = fakeJwk)
    val fakeCoseKey =
        com.sphereon.crypto.core.cose.CoseKey(
            kty =
                com.sphereon.cbor.CborUInt(
                    com.sphereon.crypto.core.cose.CoseKeyTypeEnum.EC2.value
                        .toLong()
                ),
        )
    val cosePair =
        com.sphereon.crypto.core.generic.CoseKeyPair(
            publicCoseKey = fakeCoseKey,
            privateCoseKey = null,
        )
    return ManagedKeyPair(kid = alias, providerId = "fake-provider", alias = alias, cose = cosePair, jose = josePair)
}

// ---------------------------------------------------------------------------
// Fake OAuth2Client — stubs all abstract methods; WalletImpl B3 does not call it
// ---------------------------------------------------------------------------

private class FakeOAuth2Client : OAuth2Client {
    private val err get() = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

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

// ---------------------------------------------------------------------------
// Fake PkceService — stubs all abstract methods; not called in B3
// ---------------------------------------------------------------------------

private class FakePkceService : PkceService {
    private val err get() = Err(IdkError.UNKNOWN_ERROR(message = "not implemented in fake"))

    override suspend fun createPkce(args: CreatePkceArgs): IdkResult<PkceData, IdkError> = err

    override suspend fun verifyPkce(args: VerifyPkceArgs): IdkResult<EmptyResult, IdkError> = err

    override val commands: PkceService.Commands get() = throw NotImplementedError("not needed in fake")
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class WalletImplOid4vciTest {
    private fun buildWallet(): WalletImpl {
        val store = BlobWalletDocumentStore(createTestBlobService())
        return WalletImpl(
            documents = store,
            oid4vciHolder = FakeOid4vciHolder(),
            oid4vpHolder = FakeOid4vpHolder(),
            oauth2Client = FakeOAuth2Client(),
            pkceService = FakePkceService(),
            keyManagerService = FakeKeyManagerService(),
            identityResolver = NoOpWalletIdentityResolver(),
            subjectExtractor = CredentialSubjectExtractorImpl(),
            verifySdJwtVcCommand = FakeVerifySdJwtVcCommand(accept = true),
        )
    }

    @Test
    fun createHolderKeyReturnsKeyAlias() =
        runTest {
            val wallet = buildWallet()
            val result = wallet.createHolderKey()
            assertTrue(result.isOk, "createHolderKey should succeed: ${if (result.isErr) result.error else ""}")
            assertEquals("holder-key-1", result.value)
        }

    @Test
    fun exchangePreAuthorizedCodeReturnsTokenSet() =
        runTest {
            val wallet = buildWallet()
            val result =
                wallet.exchangePreAuthorizedCode(
                    credentialIssuer = ISSUER_URL,
                    preAuthorizedCode = "preauth",
                )
            assertTrue(result.isOk, "exchangePreAuthorizedCode should succeed: ${if (result.isErr) result.error else ""}")
            assertEquals("at", result.value.accessToken)
            assertEquals("n1", result.value.cNonce)
        }

    @Test
    fun obtainCredentialStoresDocumentWithTwoInstances() =
        runTest {
            val wallet = buildWallet()
            val request =
                ObtainCredentialRequest(
                    credentialIssuer = ISSUER_URL,
                    credentialConfigurationId = CONFIG_ID,
                    accessToken = "at",
                    cNonce = "n1",
                    holderKeyAlias = "holder-key-1",
                    count = 2,
                )
            val result = wallet.obtainCredential(request)
            assertTrue(result.isOk, "obtainCredential should succeed: ${if (result.isErr) result.error else ""}")

            val doc = result.value
            assertEquals(2, doc.credentials.size, "expected 2 credential instances")
            assertEquals("vc-0", doc.credentials[0].raw)
            assertEquals("vc-1", doc.credentials[1].raw)
            assertEquals("dc+sd-jwt", doc.credentials[0].format)

            val displayName = doc.credentialDisplay.firstOrNull { it.locale == "en" }?.name
            assertEquals("Employee Credential", displayName)

            val getResult = wallet.documents.get(doc.id)
            assertTrue(getResult.isOk)
            assertNotNull(getResult.value)
            assertEquals(doc.id, getResult.value!!.id)

            val findResult = wallet.documents.findMetadataByCredentialType(CONFIG_ID)
            assertTrue(findResult.isOk)
            assertEquals(1, findResult.value.size)
            assertEquals(CONFIG_ID, findResult.value.first().credentialType)
        }

    @Test
    fun obtainCredentialRejectsCredentialWhenIssuerVerificationFails() =
        runTest {
            // The wallet verifies the issued SD-JWT VC on receipt; when issuer-signature
            // verification fails it must NOT store the credential and must surface an error.
            val store = BlobWalletDocumentStore(createTestBlobService())
            val wallet =
                WalletImpl(
                    documents = store,
                    oid4vciHolder = FakeOid4vciHolder(),
                    oid4vpHolder = FakeOid4vpHolder(),
                    oauth2Client = FakeOAuth2Client(),
                    pkceService = FakePkceService(),
                    keyManagerService = FakeKeyManagerService(),
                    identityResolver = NoOpWalletIdentityResolver(),
                    subjectExtractor = CredentialSubjectExtractorImpl(),
                    verifySdJwtVcCommand = FakeVerifySdJwtVcCommand(accept = false),
                )
            val request =
                ObtainCredentialRequest(
                    credentialIssuer = ISSUER_URL,
                    credentialConfigurationId = CONFIG_ID,
                    accessToken = "at",
                    cNonce = "n1",
                    holderKeyAlias = "holder-key-1",
                    count = 1,
                )

            val result = wallet.obtainCredential(request)

            assertTrue(result.isErr, "obtainCredential must fail when issuer verification fails")
            val findResult = wallet.documents.findMetadataByCredentialType(CONFIG_ID)
            assertTrue(findResult.isOk)
            assertEquals(0, findResult.value.size, "no document should have been stored")
        }

    @Test
    fun obtainCredentialAppendsInstancesToExistingDoc() =
        runTest {
            val wallet = buildWallet()
            val request =
                ObtainCredentialRequest(
                    credentialIssuer = ISSUER_URL,
                    credentialConfigurationId = CONFIG_ID,
                    accessToken = "at",
                    cNonce = "n1",
                    holderKeyAlias = "holder-key-1",
                    count = 1,
                )
            val first = wallet.obtainCredential(request)
            assertTrue(first.isOk)
            assertEquals(1, first.value.credentials.size)

            val second = wallet.obtainCredential(request)
            assertTrue(second.isOk)
            assertEquals(2, second.value.credentials.size)
        }

    @Test
    fun obtainCredentialPopulatesSubjectsFromCredential() =
        runTest {
            val subjectDid = "did:example:holder-subject-42"
            val store = BlobWalletDocumentStore(createTestBlobService())
            val wallet =
                WalletImpl(
                    documents = store,
                    oid4vciHolder = FakeOid4vciHolderWithSubject(subjectDid),
                    oid4vpHolder = FakeOid4vpHolder(),
                    oauth2Client = FakeOAuth2Client(),
                    pkceService = FakePkceService(),
                    keyManagerService = FakeKeyManagerService(),
                    identityResolver = NoOpWalletIdentityResolver(),
                    subjectExtractor = CredentialSubjectExtractorImpl(),
                    verifySdJwtVcCommand = FakeVerifySdJwtVcCommand(accept = true),
                )

            val request =
                ObtainCredentialRequest(
                    credentialIssuer = ISSUER_URL,
                    credentialConfigurationId = CONFIG_ID,
                    accessToken = "at",
                    cNonce = "n1",
                    holderKeyAlias = "holder-key-1",
                    count = 1,
                )
            val result = wallet.obtainCredential(request)
            assertTrue(result.isOk, "obtainCredential should succeed: ${if (result.isErr) result.error else ""}")

            val doc = result.value
            assertEquals(1, doc.subjects.size, "expected one subject extracted from credential")
            assertEquals(IdentifierType.DID, doc.subjects[0].type)
            assertEquals(subjectDid, doc.subjects[0].value)
            // NoOpWalletIdentityResolver returns the ref unchanged — identityIdentifierId stays null
            assertEquals(null, doc.subjects[0].identityIdentifierId)
        }
}
