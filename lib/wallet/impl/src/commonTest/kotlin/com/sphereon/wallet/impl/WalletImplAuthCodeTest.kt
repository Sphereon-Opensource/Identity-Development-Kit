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

package com.sphereon.wallet.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.EmptyResult
import com.sphereon.data.store.blob.DefaultTempUrlPolicy
import com.sphereon.data.store.blob.InMemoryBlobStoreConfig
import com.sphereon.data.store.blob.impl.DefaultBlobService
import com.sphereon.data.store.blob.impl.DefaultRetentionPolicyService
import com.sphereon.data.store.blob.impl.KvBlobMetadataIndex
import com.sphereon.data.store.blob.memory.InMemoryBlobBackingStorageImpl
import com.sphereon.data.store.blob.memory.InMemoryBlobStoreFactoryImpl
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.memory.InMemoryKvBackingStorageImpl
import com.sphereon.data.store.kv.memory.InMemoryKvStoreFactoryImpl
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
import com.sphereon.oauth2.common.model.PkceMethod
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
import com.sphereon.wallet.WalletConfig
import com.sphereon.wallet.credential.store.BlobWalletCredentialStore
import com.sphereon.wallet.credential.store.BlobWalletIssuanceSessionStore
import com.sphereon.wallet.credential.store.TestWalletCredentialBodyProtector
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.sphereon.openid.oid4vp.holder.WalletConfig as Oid4vpWalletConfig

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

private const val AC_ISSUER_URL = "https://issuer.example.com"
private const val AC_AUTH_ENDPOINT = "https://as.example.com/authorize"
private const val AC_TOKEN_ENDPOINT = "https://as.example.com/token"
private const val AC_CLIENT_ID = "client"
private const val AC_REDIRECT_URI = "http://localhost:8765/callback"
private const val AC_SCOPE = "openid"
private const val AC_WALLET_INSTANCE_ID = "wallet-auth-code"
private const val AC_CODE_VERIFIER = "ver"
private const val AC_CODE_CHALLENGE = "chal"
private const val AC_ACCESS_TOKEN = "at"
private const val AC_C_NONCE = "n1"

// ---------------------------------------------------------------------------
// Fake issuer metadata + resolved AS for auth-code flow
// ---------------------------------------------------------------------------

private val acFakeIssuerMetadata =
    CredentialIssuerMetadata(
        credentialIssuer = AC_ISSUER_URL,
        credentialEndpoint = "https://issuer.example.com/credential",
        credentialConfigurationsSupported =
            mapOf(
                "TestCredential" to
                    CredentialConfigurationSupported(
                        format = "dc+sd-jwt",
                        display = listOf(DisplayProperties(name = "Test Credential", locale = "en")),
                    ),
            ),
        display = listOf(DisplayProperties(name = "TestIssuer", locale = "en")),
    )

private val acFakeResolvedAs =
    ResolvedAuthorizationServer(
        authorizationServerUrl = AC_ISSUER_URL,
        metadata =
            buildJsonObject {
                put("authorization_endpoint", AC_AUTH_ENDPOINT)
                put("token_endpoint", AC_TOKEN_ENDPOINT)
            },
    )

// ---------------------------------------------------------------------------
// Fake Oid4vciHolder — authorization-code aware
// ---------------------------------------------------------------------------

private class AcFakeOid4vciHolder : Oid4vciHolder {
    override val commands: Oid4vciHolder.Commands get() = throw NotImplementedError("not needed in fake")

    override suspend fun resolveIssuerMetadata(issuerUrl: String): IdkResult<CredentialIssuerMetadata, IdkError> = Ok(acFakeIssuerMetadata)

    override suspend fun selectAuthorizationServer(
        issuerMetadata: CredentialIssuerMetadata,
        preferredAuthorizationServer: String?,
    ): IdkResult<ResolvedAuthorizationServer, IdkError> = Ok(acFakeResolvedAs)

    override suspend fun exchangeAuthorizationCode(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String,
        redirectUri: String,
        clientId: String?,
    ): IdkResult<TokenResponseWithContext, IdkError> = Ok(TokenResponseWithContext(accessToken = AC_ACCESS_TOKEN, tokenType = "Bearer", cNonce = AC_C_NONCE))

    // --- Not needed for auth-code flow tests ---

    override suspend fun exchangePreAuthorizedCode(
        tokenEndpoint: String,
        preAuthorizedCode: String,
        txCode: String?,
        clientId: String?,
        redirectUri: String?,
    ): IdkResult<TokenResponseWithContext, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

    override suspend fun createCredentialRequestProof(
        issuerUrl: String,
        cNonce: String?,
        signingKeyId: String,
        signingAlgorithm: String,
        clientId: String?,
        count: Int,
        keyInclusionMode: com.sphereon.crypto.jose.jws.JwsIdentifierMode,
    ): IdkResult<CreatedProof, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

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
    ): IdkResult<CredentialResponse, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

    override suspend fun parseCredentialOffer(rawOffer: String): IdkResult<CredentialOffer, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

    override suspend fun resolveCredentialOffer(offer: CredentialOffer): IdkResult<ResolvedCredentialOffer, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

    override suspend fun requestNonce(nonceEndpoint: String): IdkResult<NonceResponse, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

    override suspend fun requestDeferredCredential(
        deferredCredentialEndpoint: String,
        accessToken: String,
        transactionId: String,
        credentialResponseEncryption: RequestedCredentialResponseEncryption?,
        requestEncryptionJwk: JsonObject?,
        requestEncryptionAlg: String?,
        requestEncryptionEnc: String?,
        decryptionKeyId: String?,
    ): IdkResult<CredentialResponse, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

    override suspend fun sendNotification(
        notificationEndpoint: String,
        accessToken: String,
        notificationId: String,
        event: CredentialNotificationEvent,
        eventDescription: String?,
    ): IdkResult<Unit, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

    override suspend fun followUpIae(args: FollowUpIaeArgs): IdkResult<IaeHolderResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

    override suspend fun initiateIae(args: InitiateIaeArgs): IdkResult<IaeHolderResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

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
    ): IdkResult<AuthorizationRequestResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))
}

// ---------------------------------------------------------------------------
// Fake PkceService — returns fixed verifier/challenge for deterministic tests
// ---------------------------------------------------------------------------

private class AcFakePkceService : PkceService {
    override suspend fun createPkce(args: CreatePkceArgs): IdkResult<PkceData, IdkError> =
        Ok(PkceData(codeVerifier = AC_CODE_VERIFIER, codeChallenge = AC_CODE_CHALLENGE, codeChallengeMethod = PkceMethod.S256))

    override suspend fun verifyPkce(args: VerifyPkceArgs): IdkResult<EmptyResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

    override val commands: PkceService.Commands get() = throw NotImplementedError("not needed in fake")
}

// ---------------------------------------------------------------------------
// Fake Oid4vpHolder — not exercised in auth-code tests
// ---------------------------------------------------------------------------

private class AcFakeOid4vpHolder : Oid4vpHolder {
    override val commands: Oid4vpHolder.Commands get() = throw NotImplementedError("not needed in fake")

    override suspend fun parseAuthorizationRequest(
        requestUri: String,
        walletConfig: Oid4vpWalletConfig?,
    ): IdkResult<AuthorizationRequest, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

    override suspend fun resolveAuthorizationRequest(request: AuthorizationRequest): IdkResult<ResolvedOid4vpRequest, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

    override suspend fun createAuthorizationResponse(
        request: ResolvedOid4vpRequest,
        selectedCredentials: List<SelectedCredential>,
    ): IdkResult<AuthorizationResponse, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

    override suspend fun submitAuthorizationResponse(
        resolvedRequest: ResolvedOid4vpRequest,
        response: AuthorizationResponse,
        responseMode: ResponseMode?,
    ): IdkResult<SubmissionResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))
}

// ---------------------------------------------------------------------------
// Fake OAuth2Client — not exercised in auth-code tests
// ---------------------------------------------------------------------------

private class AcFakeOAuth2Client : OAuth2Client {
    private val err get() = Err(IdkError.UNKNOWN_ERROR(message = "not needed in fake"))

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
// Blob service factory (same pattern as WalletImplOid4vciTest)
// ---------------------------------------------------------------------------

private class AcTestBlobStoreService(
    private val store: com.sphereon.data.store.blob.BlobStore,
) : com.sphereon.data.store.blob.impl.BlobStoreService {
    override fun getStoreIds(): Array<String> = arrayOf("memory")

    override fun getStoreConfig(storeId: String) = InMemoryBlobStoreConfig(id = storeId)

    override fun getStore(storeId: String) = store
}

private class AcTestKvStoreService(
    private val store: com.sphereon.data.store.kv.KvStore,
) : com.sphereon.data.store.kv.impl.KvStoreService {
    override fun getStoreIds(): Array<String> = arrayOf(KvBlobMetadataIndex.STORE_ID)

    override fun getStoreConfig(storeId: String) = InMemoryKvStoreConfig(id = storeId, scopeBinding = KvStoreScopeBinding.APP)

    override fun getStore(storeId: String) = store
}

private class AcTestSessionExecution : com.sphereon.core.api.context.SessionExecution {
    override val sessionContext: com.sphereon.di.session.SessionContext = com.sphereon.di.context.NoOpSessionContext
    override val sessionContextManager: com.sphereon.di.session.SessionContextManager
        get() = throw NotImplementedError("not needed in test")
    override val log: com.sphereon.core.api.log.SessionLogService = AcTestNoOpLogService(sessionContext)
    override val conf: com.sphereon.core.api.context.ContextConfig
        get() = throw NotImplementedError("not needed in test")
}

private class AcTestNoOpLogService(
    override val sessionContext: com.sphereon.di.session.SessionContext,
) : com.sphereon.core.api.log.SessionLogService {
    override val id: String = "test-authcode-log"
    override val isEnabled: Boolean = false
    override val scope = com.sphereon.core.api.context.IdkScope.SESSION
    override val logManager: com.sphereon.core.api.log.SessionLogManager
        get() = throw NotImplementedError("not needed in test")

    override suspend fun setConfig(config: com.sphereon.core.api.log.LoggerConfig): com.sphereon.core.api.log.LogService = this

    override fun executeAsync(message: com.sphereon.core.api.log.LogMessage): com.sphereon.core.api.IdkResult<Unit, com.sphereon.core.api.error.IdkErrorType> =
        com.sphereon.core.api
            .Ok(Unit)

    override fun toAsync(): com.sphereon.core.api.log.AsyncLogService = throw NotImplementedError("not needed in test")
}

private class AcTestEventService : com.sphereon.core.events.SessionEventService {
    private val hub =
        com.sphereon.core.events.impl
            .EventHubImpl()
    override val scope: com.sphereon.core.api.context.IdkScope = com.sphereon.core.api.context.IdkScope.SESSION
    override val eventHub: com.sphereon.core.events.EventHub = hub
    override val parent: com.sphereon.core.events.UserEventService get() = throw NotImplementedError("not needed in test")
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

private fun createAcTestBlobService(): DefaultBlobService {
    val blobBackingStorage = InMemoryBlobBackingStorageImpl()
    val blobFactory = InMemoryBlobStoreFactoryImpl(blobBackingStorage)
    val blobConfig = InMemoryBlobStoreConfig(id = "memory")

    val kvBackingStorage = InMemoryKvBackingStorageImpl()
    val kvFactory = InMemoryKvStoreFactoryImpl(kvBackingStorage)
    val kvConfig = InMemoryKvStoreConfig(id = KvBlobMetadataIndex.STORE_ID, scopeBinding = KvStoreScopeBinding.APP)
    val kvStore = kvFactory.create(kvConfig)

    return DefaultBlobService(
        blobStoreService = AcTestBlobStoreService(blobFactory.create(blobConfig)),
        metadataIndex = KvBlobMetadataIndex(AcTestKvStoreService(kvStore)),
        retentionPolicyService = DefaultRetentionPolicyService(),
        tempUrlPolicy = DefaultTempUrlPolicy(),
        eventService = AcTestEventService(),
        execution = AcTestSessionExecution(),
    )
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class WalletImplAuthCodeTest {
    private fun buildWallet(): WalletImpl {
        val blobService = createAcTestBlobService()
        return WalletImpl(
            credentials = BlobWalletCredentialStore(blobService, TestWalletCredentialBodyProtector),
            issuanceSessions = BlobWalletIssuanceSessionStore(blobService),
            oid4vciHolder = AcFakeOid4vciHolder(),
            oid4vpHolder = AcFakeOid4vpHolder(),
            oauth2Client = AcFakeOAuth2Client(),
            pkceService = AcFakePkceService(),
            keyManagerService = createAcFakeKeyManagerService(),
            identityResolver = NoOpWalletIdentityResolver(),
            subjectExtractor = CredentialSubjectExtractorImpl(),
            verifySdJwtVcCommand = FakeVerifySdJwtVcCommand(accept = true),
        )
    }

    @Test
    fun startAuthorizationCodeFlowReturnsValidAuthCodeStart() =
        runTest {
            val wallet = buildWallet()
            val result =
                wallet.startAuthorizationCodeFlow(
                    credentialIssuer = AC_ISSUER_URL,
                    config = WalletConfig(walletInstanceId = AC_WALLET_INSTANCE_ID, clientId = AC_CLIENT_ID, redirectUri = AC_REDIRECT_URI),
                    scope = AC_SCOPE,
                )

            assertTrue(result.isOk, "startAuthorizationCodeFlow should succeed: ${if (result.isErr) result.error else ""}")
            val start = result.value

            assertTrue(start.authorizationUrl.contains("response_type=code"), "URL should contain response_type=code")
            assertTrue(start.authorizationUrl.contains("code_challenge=$AC_CODE_CHALLENGE"), "URL should contain code_challenge")
            assertTrue(start.authorizationUrl.contains("code_challenge_method=S256"), "URL should contain code_challenge_method=S256")
            assertTrue(start.authorizationUrl.contains("client_id=$AC_CLIENT_ID"), "URL should contain client_id")
            assertTrue(start.authorizationUrl.contains("redirect_uri="), "URL should contain redirect_uri")
            assertTrue(start.state.isNotBlank(), "state should be non-blank")
            assertEquals(AC_CODE_VERIFIER, start.codeVerifier)
            assertEquals(AC_TOKEN_ENDPOINT, start.tokenEndpoint)
            assertEquals(AC_CLIENT_ID, start.clientId)
        }

    @Test
    fun completeAuthorizationCodeFlowReturnsTokenSet() =
        runTest {
            val wallet = buildWallet()

            val startResult =
                wallet.startAuthorizationCodeFlow(
                    credentialIssuer = AC_ISSUER_URL,
                    config = WalletConfig(walletInstanceId = AC_WALLET_INSTANCE_ID, clientId = AC_CLIENT_ID, redirectUri = AC_REDIRECT_URI),
                    scope = AC_SCOPE,
                )
            assertTrue(startResult.isOk)

            val completeResult =
                wallet.completeAuthorizationCodeFlow(
                    start = startResult.value,
                    code = "the-code",
                )

            assertTrue(completeResult.isOk, "completeAuthorizationCodeFlow should succeed: ${if (completeResult.isErr) completeResult.error else ""}")
            val tokenSet = completeResult.value
            assertEquals(AC_ACCESS_TOKEN, tokenSet.accessToken)
            assertEquals(AC_C_NONCE, tokenSet.cNonce)
        }
}

// ---------------------------------------------------------------------------
// Minimal KeyManagerService fake for auth-code tests (not exercised, just needed for construction)
// ---------------------------------------------------------------------------

private fun createAcFakeKeyManagerService(): com.sphereon.crypto.core.kms.KeyManagerService {
    // Reuse a simple anonymous implementation — mirrors the structure in WalletImplOid4vciTest
    return object :
        com.sphereon.crypto.core.kms.KeyManagerService,
        com.sphereon.crypto.core.kms.KmsProviderRegistry,
        com.sphereon.crypto.core.kms.KeyResolverRegistry,
        com.sphereon.crypto.core.kms.HasKeyStoreService,
        com.sphereon.crypto.core.kms.KeyStoreService {
        override fun defaultProviderId(): String = "fake"

        override fun getProviderIds(): Array<String> = arrayOf("fake")

        override fun getProviderById(id: String): com.sphereon.crypto.core.kms.KmsProvider = throw UnsupportedOperationException()

        override fun getKmsBySignatureAlgorithm(alg: com.sphereon.crypto.core.generic.SignatureAlgorithm): com.sphereon.crypto.core.kms.KmsProvider = throw UnsupportedOperationException()

        override fun getProvider(
            providerId: String?,
            alg: com.sphereon.crypto.core.generic.SignatureAlgorithm?
        ): com.sphereon.crypto.core.kms.KmsProvider = throw UnsupportedOperationException()

        override fun registerProvider(
            provider: com.sphereon.crypto.core.kms.KmsProvider,
            makeDefaultKms: Boolean?
        ) {}

        override fun defaultResolverId(): String = "fake"

        override fun getResolverIds(): Array<String> = emptyArray()

        override fun getResolverById(id: String): com.sphereon.crypto.core.kms.KeyResolverService = throw UnsupportedOperationException()

        override fun getResolverByKeyTypeOrIdentifier(
            identifierMethod: com.sphereon.crypto.core.kms.model.IdentifierMethod?,
            keyType: com.sphereon.crypto.core.generic.KeyTypeMapping?,
            resolverId: String?
        ): com.sphereon.crypto.core.kms.KeyResolverService = throw UnsupportedOperationException()

        override fun registerResolver(
            resolver: com.sphereon.crypto.core.kms.KeyResolverService,
            makeDefaultResolver: Boolean?
        ) {}

        override val keyStore: com.sphereon.crypto.core.kms.KeyStoreService get() = this
        override val settings: com.sphereon.crypto.core.kms.model.KeyProviderSettings? = null

        override suspend fun listKeys(): Array<com.sphereon.crypto.core.ManagedKeyReference> = emptyArray()

        override suspend fun getKey(keyInfo: com.sphereon.crypto.core.KeyInfoType<*>): com.sphereon.crypto.core.ManagedKeyInfoType<*> = throw IllegalArgumentException("not needed")

        override suspend fun storeKey(
            keyInfo: com.sphereon.crypto.core.ResolvedKeyInfoType<*>,
            providerId: String,
            alias: String,
            certChain: Array<com.sphereon.crypto.core.x509.Certificate>?
        ): com.sphereon.crypto.core.ManagedKeyInfoType<*> = throw UnsupportedOperationException()

        override suspend fun deleteKey(keyInfo: com.sphereon.crypto.core.KeyInfoType<*>): Boolean = false

        override fun keyVisibility(): com.sphereon.crypto.core.KeyVisibility = com.sphereon.crypto.core.KeyVisibility.PUBLIC

        override suspend fun createRawSignature(
            keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            input: ByteArray,
            requireX5Chain: Boolean
        ): ByteArray = ByteArray(64)

        override suspend fun isValidRawSignature(
            keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            input: ByteArray,
            signature: ByteArray
        ): Boolean = true

        override suspend fun encrypt(
            keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            plaintext: ByteArray,
            algorithm: com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm,
            additionalAuthenticatedData: ByteArray?
        ): com.sphereon.crypto.core.kms.EncryptionResult =
            com.sphereon.crypto.core.kms
                .EncryptionResult(plaintext, ByteArray(12), ByteArray(16))

        override suspend fun decrypt(
            keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            ciphertext: ByteArray,
            algorithm: com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm,
            iv: ByteArray,
            authTag: ByteArray,
            additionalAuthenticatedData: ByteArray?
        ): ByteArray = ciphertext

        override suspend fun wrapKey(
            wrappingKeyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            keyToWrap: ByteArray,
            algorithm: com.sphereon.crypto.core.kms.KeyWrapAlgorithm
        ): ByteArray = keyToWrap

        override suspend fun unwrapKey(
            unwrappingKeyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            wrappedKey: ByteArray,
            algorithm: com.sphereon.crypto.core.kms.KeyWrapAlgorithm
        ): ByteArray = wrappedKey

        override suspend fun performKeyAgreement(
            privateKeyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            publicKeyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            algorithm: com.sphereon.crypto.core.kms.KeyAgreementAlgorithm,
            keyDataLen: Int?
        ): ByteArray = ByteArray(keyDataLen ?: 32)

        @Suppress("UNCHECKED_CAST")
        override suspend fun <KT : com.sphereon.crypto.core.KeyType> resolvePublicKey(
            keyInfo: com.sphereon.crypto.core.KeyInfoType<KT>,
            identifierMethod: com.sphereon.crypto.core.kms.model.IdentifierMethod?,
            trustedCerts: Array<String>?,
            verifyX509CertificateChain: Boolean?
        ): com.sphereon.crypto.core.ResolvedKeyInfoType<KT> {
            if (keyInfo is com.sphereon.crypto.core.ResolvedKeyInfoType<*>) return keyInfo as com.sphereon.crypto.core.ResolvedKeyInfoType<KT>
            throw IllegalArgumentException("Cannot resolve key")
        }

        override suspend fun generateKey(
            providerId: String?,
            alias: String?,
            use: com.sphereon.crypto.core.jose.JwkUse?,
            keyOperations: Array<out com.sphereon.crypto.core.generic.KeyOperations>?,
            alg: com.sphereon.crypto.core.generic.SignatureAlgorithm?,
            keyVisibility: com.sphereon.crypto.core.KeyVisibility?
        ): com.sphereon.crypto.core.generic.ManagedKeyPair = throw UnsupportedOperationException()

        @Deprecated("Use generateKey instead.", ReplaceWith("generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)"))
        override suspend fun generateKeyAsync(
            providerId: String?,
            alias: String?,
            use: com.sphereon.crypto.core.jose.JwkUse?,
            keyOperations: Array<out com.sphereon.crypto.core.generic.KeyOperations>?,
            alg: com.sphereon.crypto.core.generic.SignatureAlgorithm?,
            keyVisibility: com.sphereon.crypto.core.KeyVisibility?
        ): com.sphereon.crypto.core.generic.ManagedKeyPair = throw UnsupportedOperationException()

        override suspend fun generateKeyResult(
            providerId: String?,
            alias: String?,
            use: com.sphereon.crypto.core.jose.JwkUse?,
            keyOperations: Array<out com.sphereon.crypto.core.generic.KeyOperations>?,
            alg: com.sphereon.crypto.core.generic.SignatureAlgorithm?,
            keyVisibility: com.sphereon.crypto.core.KeyVisibility?
        ): IdkResult<com.sphereon.crypto.core.kms.command.GenerateKeyResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed"))

        override suspend fun createRawSignatureResult(
            keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            input: ByteArray,
            requireX5Chain: Boolean
        ): IdkResult<com.sphereon.crypto.core.kms.command.CreateRawSignatureResult, IdkError> =
            Ok(
                com.sphereon.crypto.core.kms.command
                    .CreateRawSignatureResult(ByteArray(64))
            )

        override suspend fun verifyRawSignatureResult(
            keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            input: ByteArray,
            signature: ByteArray
        ): IdkResult<com.sphereon.crypto.core.kms.command.VerifyRawSignatureResult, IdkError> =
            Ok(
                com.sphereon.crypto.core.kms.command
                    .VerifyRawSignatureResult(true)
            )

        override suspend fun signDigestResult(
            keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            digest: ByteArray,
            signatureAlgorithm: com.sphereon.crypto.core.generic.SignatureAlgorithm,
            signatureEncoding: com.sphereon.crypto.core.kms.command.SignatureEncoding,
            requireX5Chain: Boolean
        ): IdkResult<com.sphereon.crypto.core.kms.command.SignDigestResult, IdkError> =
            Ok(
                com.sphereon.crypto.core.kms.command
                    .SignDigestResult(ByteArray(64))
            )

        override suspend fun verifyDigestResult(
            keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            digest: ByteArray,
            signature: ByteArray,
            signatureAlgorithm: com.sphereon.crypto.core.generic.SignatureAlgorithm,
            signatureEncoding: com.sphereon.crypto.core.kms.command.SignatureEncoding
        ): IdkResult<com.sphereon.crypto.core.kms.command.VerifyDigestResult, IdkError> =
            Ok(
                com.sphereon.crypto.core.kms.command
                    .VerifyDigestResult(true)
            )

        override suspend fun signDigest(
            keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            digest: ByteArray,
            signatureAlgorithm: com.sphereon.crypto.core.generic.SignatureAlgorithm,
            signatureEncoding: com.sphereon.crypto.core.kms.command.SignatureEncoding,
            requireX5Chain: Boolean
        ): ByteArray = ByteArray(64)

        override suspend fun verifyDigest(
            keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            digest: ByteArray,
            signature: ByteArray,
            signatureAlgorithm: com.sphereon.crypto.core.generic.SignatureAlgorithm,
            signatureEncoding: com.sphereon.crypto.core.kms.command.SignatureEncoding
        ): Boolean = true

        override suspend fun encryptResult(
            keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            plaintext: ByteArray,
            algorithm: com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm,
            additionalAuthenticatedData: ByteArray?
        ): IdkResult<com.sphereon.crypto.core.kms.command.EncryptResult, IdkError> =
            Ok(
                com.sphereon.crypto.core.kms.command
                    .EncryptResult(plaintext, ByteArray(12), ByteArray(16))
            )

        override suspend fun decryptResult(
            keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            ciphertext: ByteArray,
            algorithm: com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm,
            iv: ByteArray,
            authTag: ByteArray,
            additionalAuthenticatedData: ByteArray?
        ): IdkResult<com.sphereon.crypto.core.kms.command.DecryptResult, IdkError> =
            Ok(
                com.sphereon.crypto.core.kms.command
                    .DecryptResult(ciphertext)
            )

        override suspend fun wrapKeyResult(
            wrappingKeyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            keyToWrap: ByteArray,
            algorithm: com.sphereon.crypto.core.kms.KeyWrapAlgorithm
        ): IdkResult<com.sphereon.crypto.core.kms.command.WrapKeyResult, IdkError> =
            Ok(
                com.sphereon.crypto.core.kms.command
                    .WrapKeyResult(keyToWrap)
            )

        override suspend fun unwrapKeyResult(
            unwrappingKeyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            wrappedKey: ByteArray,
            algorithm: com.sphereon.crypto.core.kms.KeyWrapAlgorithm
        ): IdkResult<com.sphereon.crypto.core.kms.command.UnwrapKeyResult, IdkError> =
            Ok(
                com.sphereon.crypto.core.kms.command
                    .UnwrapKeyResult(wrappedKey)
            )

        override suspend fun performKeyAgreementResult(
            privateKeyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            publicKeyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            algorithm: com.sphereon.crypto.core.kms.KeyAgreementAlgorithm,
            keyDataLen: Int?
        ): IdkResult<com.sphereon.crypto.core.kms.command.PerformKeyAgreementResult, IdkError> =
            Ok(
                com.sphereon.crypto.core.kms.command.PerformKeyAgreementResult(
                    ByteArray(keyDataLen ?: 32)
                )
            )

        override suspend fun listKeysResult(providerId: String?): IdkResult<com.sphereon.crypto.core.kms.command.ListKeysResult, IdkError> =
            Ok(
                com.sphereon.crypto.core.kms.command
                    .ListKeysResult(emptyArray())
            )

        override suspend fun getKeyResult(keyInfo: com.sphereon.crypto.core.KeyInfoType<*>): IdkResult<com.sphereon.crypto.core.kms.command.GetKeyResult, IdkError> =
            Err(IdkError.UNKNOWN_ERROR(message = "not needed"))

        override suspend fun storeKeyResult(
            keyInfo: com.sphereon.crypto.core.ResolvedKeyInfoType<*>,
            providerId: String,
            alias: String,
            certChain: Array<com.sphereon.crypto.core.x509.Certificate>?
        ): IdkResult<com.sphereon.crypto.core.kms.command.StoreKeyResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed"))

        override suspend fun deleteKeyResult(keyInfo: com.sphereon.crypto.core.KeyInfoType<*>): IdkResult<com.sphereon.crypto.core.kms.command.DeleteKeyResult, IdkError> =
            Ok(
                com.sphereon.crypto.core.kms.command
                    .DeleteKeyResult(false)
            )

        override suspend fun resolvePublicKeyResult(
            keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
            identifierMethod: com.sphereon.crypto.core.kms.model.IdentifierMethod?,
            trustedCerts: Array<String>?,
            verifyX509CertificateChain: Boolean?
        ): IdkResult<com.sphereon.crypto.core.kms.command.ResolvePublicKeyResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "not needed"))

        override suspend fun queryProvider(query: com.sphereon.crypto.core.kms.KmsProviderQuery): IdkResult<com.sphereon.crypto.core.kms.QueryProviderResult, IdkError> =
            Err(IdkError.UNKNOWN_ERROR(message = "not needed"))

        override suspend fun queryProviders(query: com.sphereon.crypto.core.kms.KmsProviderQuery): IdkResult<com.sphereon.crypto.core.kms.QueryProvidersResult, IdkError> =
            Ok(
                com.sphereon.crypto.core.kms
                    .QueryProvidersResult(matches = emptyArray(), totalProviders = 0)
            )

        override suspend fun getAllCapabilities(includeDisabled: Boolean): IdkResult<com.sphereon.crypto.core.kms.GetAllCapabilitiesResult, IdkError> =
            Ok(
                com.sphereon.crypto.core.kms
                    .GetAllCapabilitiesResult(capabilities = emptyMap())
            )
    }
}
