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
 */

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.data.store.kv.KvEntry
import com.sphereon.data.store.kv.KvEntryMetadata
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvNamespaceId
import com.sphereon.data.store.kv.KvPutResult
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfig
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.data.store.kv.impl.KvStoreService
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.crypto.jose.jwe.JweServiceImpl
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.common.responseUri
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriArgs
import com.sphereon.openid.oid4vp.verifier.HandleDirectPostResponseArgs
import com.sphereon.openid.oid4vp.verifier.RetrieveAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.Oid4vpVerifierService
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestUriSigningConfig
import com.sphereon.openid.oid4vp.verifier.store.ResponseCodeStore
import com.sphereon.oauth2.client.command.CreateSignedJarArgs
import com.sphereon.oauth2.client.command.CreateSignedJarCommand
import com.sphereon.oauth2.common.jarm.CreateJarmResponseArgs
import com.sphereon.oauth2.common.jarm.CreateJarmResponseCommandImpl
import com.sphereon.oauth2.common.jarm.JarmVerificationResult
import com.sphereon.oauth2.common.jarm.JarmConfig
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseArgs
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseCommand
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseCommandImpl
import com.sphereon.oauth2.common.model.ClientRegistration
import com.sphereon.oauth2.common.model.ClientType
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.ResponseType
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.openid.oid4vp.common.ClientMetadata
import com.sphereon.openid.oid4vp.verifier.impl.http.Oid4vpVerifierHttpAdapter
import com.sphereon.core.api.http.HttpAdapterDispatcher
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.ktor.http.client.FetchRequestUriCommandImpl
import com.sphereon.ktor.http.client.ParseUriQueryCommand
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.oauth2.client.JarService
import com.sphereon.crypto.resolution.extern.MultiExternalIdentifierService
import com.sphereon.crypto.resolution.extern.ExternalIdentifierService
import com.sphereon.crypto.resolution.extern.JwksUrlExternalIdentifierResolutionServiceImpl
import com.sphereon.crypto.resolution.extern.MultiExternalIdentifierResolutionServiceImpl
import com.sphereon.openid.oid4vp.holder.ResolveAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.holder.impl.ParseAuthorizationRequestCommandImpl
import com.sphereon.openid.oid4vp.holder.ParseAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.holder.JarmOptions
import com.sphereon.openid.oid4vp.holder.SubmissionResult
import com.sphereon.openid.oid4vp.holder.SubmitAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.holder.impl.SubmitAuthorizationResponseCommandImpl
import com.sphereon.openid.oid4vp.common.buildOid4vpAuthorizationResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.formUrlEncode
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import io.ktor.http.contentType
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import dev.zacsweers.metro.ContributesTo
import com.sphereon.openid.oid4vp.verifier.impl.createOid4vpRpJvmTestAppComponent
import com.sphereon.di.session.SessionScope
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * JVM flow test that exercises the core Universal OID4VP flow pieces, with selected components mocked/stubbed.
 *
 * - Create an authorization request + session (direct mode)
 * - Serve request_uri (request object by-reference) via [RequestUriHandlerImpl]
 * - Handle a direct_post response and create a response_code
 * - Retrieve the authorization response by response_code
 *
 * This test focuses on protocol behavior; platform HTTP routing is covered by the universal HTTP adapter tests.
 */
class Oid4vpUniversalFlowWithMocksTest {

    @Test
    fun `end-to-end flow - create session, serve request_uri, handle direct_post, retrieve by response_code`() = runTest {
        val execution: SessionExecution = TestExecutionContext.createExecution()

        // Stores
        val sessionStore = TestAuthorizationSessionStore()
        val responseCodeStore: ResponseCodeStore = KvResponseCodeStore(
            kvStoreManager = TestKvStoreManager(TestKvStore()),
            kvStoreService = NoConfigKvStoreService(),
            execution = execution
        )

        // Commands / handlers
        val createAuthorizationRequest = CreateAuthorizationRequestCommandImpl(
            execution = execution,
            authorizationSessionStore = sessionStore
        )

        val parseAuthorizationResponse = ParseAuthorizationResponseCommandImpl(
            execution = execution,
            verifyJarmCommand = MockVerifyJarmResponseCommand()
        )

        val handleDirectPost = HandleDirectPostResponseCommandImpl(
            execution = execution,
            parseAuthorizationResponseCommand = parseAuthorizationResponse,
            validateAuthorizationResponseCommand = MockValidateAuthorizationResponseCommand(),
            authorizationSessionStore = sessionStore,
            responseCodeStore = responseCodeStore
        )

        val retrieve = RetrieveAuthorizationResponseCommandImpl(
            execution = execution,
            responseCodeStore = responseCodeStore
        )

        val jarCommand = object : CreateSignedJarCommand {
            override val isEnabled: Boolean = true
            override val inputTypeToken: TypeToken<CreateSignedJarArgs> = typeToken<CreateSignedJarArgs>()
            override val outputTypeToken: TypeToken<StringResult> = typeToken<StringResult>()
            override suspend fun execute(args: CreateSignedJarArgs) = Ok(StringResult("signed.jwt.payload"))
        }
        val signingConfig = object : RequestUriSigningConfig {
            override val signingKey = com.sphereon.crypto.core.KeyInfo<Nothing>(kid = "test-key")
            override val audience: String = "https://wallet.example.com"
            override val expirationSeconds: Long = 60
        }
        val requestUriHandler = RequestUriHandlerImpl(
            authorizationSessionStore = sessionStore,
            createSignedJarCommand = jarCommand,
            signingConfig = signingConfig
        )

        // 1) Create authorization request + store session
        val created = createAuthorizationRequest.createAuthorizationRequest(
            CreateAuthorizationRequestArgs(
                dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt"))),
                clientId = "https://verifier.example.com",
                responseUri = "https://verifier.example.com/response",
                responseMode = ResponseMode.DIRECT_POST,
                nonce = "nonce12345678",
                state = "state-1234"
            )
        ).getOrThrow()

        assertNotNull(created.sessionId)
        assertEquals("https://verifier.example.com", created.request.clientId)

        // 2) request_uri fetch (JAR)
        val jarResponse = requestUriHandler.handleGet("/oid4vp/request-uri/${created.sessionId}").getOrThrow()
        assertEquals("application/oauth-authz-req+jwt", jarResponse.contentType)
        assertEquals("signed.jwt.payload", jarResponse.signedJar)

        // 3) direct_post response to backend -> response_code
        val vpToken = """{"cred":"eyJhbGciOiJFUzI1NiJ9.eyJpYXQiOjE3MDAwMDAwMDB9.sig~eyJhbGciOiJub25lIn0~"}"""
        val handled = handleDirectPost.handleDirectPostResponse(
            HandleDirectPostResponseArgs(
                responseParams = mapOf(
                    "vp_token" to vpToken,
                    "state" to "state-1234"
                ),
                originalRequest = created.request,
                dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt"))),
                redirectUri = "https://verifier.example.com/callback"
            )
        ).getOrThrow()

        assertNotNull(handled.responseCode)
        assertTrue(handled.redirectUri.contains("response_code="))

        // 4) retrieve by response_code
        val retrieved = retrieve.retrieveAuthorizationResponse(
            RetrieveAuthorizationResponseArgs(
                responseCode = handled.responseCode,
                markAsUsed = true
            )
        ).getOrThrow()

        assertEquals("state-1234", retrieved.state)
        assertEquals(1, retrieved.parsedResponse.vpToken.presentations.size)
    }

    @Test
    fun `end-to-end flow - direct_post_jwt verifies signed JARM and returns response_code`() = runTest {
        val testScope = TestScope()
        val app = createOid4vpRpJvmTestAppComponent(testScope, "test-verifier-app", "test", "1.0.0")

        // Configure a memory-backed software KMS provider via the standard property source mechanism.
        // This matches the pattern used in SoftwareKmsProviderConfigTest (no manual provider construction/registration).
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "test-verifier-app.test.kms.providers.test-software.type" to "software",
                "test-verifier-app.test.kms.providers.test-software.id" to "test-software",
                "test-verifier-app.test.kms.providers.test-software.keystore.type" to "memory",
                "test-verifier-app.test.kms.providers.test-software.keystore.id" to "test-memory-keystore",
                "test-verifier-app.test.kms.providers.test-software.keystore.keyVisibility" to "private",
                "test-verifier-app.test.kms.providers.test-software.keystore.overwriteAlias" to "true"
            )
        )

        // Ensure the new properties are applied when building scoped components.
        app.userContextManager.destroyAll()

        val context = app.userContextManager.getAnonymous()
        val session = context.sessionContextManager.createOrGetFromId("test")
        val sessionComponent = session.component
        val execution = session.asCoreApiServiceComponent().serviceExecution

        // Crypto services
        val jwtService = (sessionComponent as JwtServiceImpl.Component).jwtService
        val jweService = (sessionComponent as JweServiceImpl.Component).jweService
        val kms = sessionComponent.asKeyManagerServiceComponent().keyManagerService

        // JARM commands
        val createJarmCommand = CreateJarmResponseCommandImpl(
            execution = execution,
            jwtService = jwtService,
            jweService = jweService
        )
        val verifyJarmCommand = VerifyJarmResponseCommandImpl(
            execution = execution,
            jwtService = jwtService,
            jweService = jweService
        )

        val keyPair = kms.generateKeyAsync(
            providerId = "test-software",
            alias = "wallet-signing",
            use = JwkUse.sig,
            keyOperations = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
            alg = SignatureAlgorithm.ECDSA_SHA256
        )
        val walletKeyInfo = ManagedOptsKeyInfo(
            identifier = (ResolvedKeyInfo.fromKey(keyPair.jose.publicJwk) as ResolvedKeyInfo).copy(
                providerId = "test-software",
                alias = keyPair.alias,
                kid = keyPair.kid,
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
            )
        )

        // Stores
        val sessionStore = TestAuthorizationSessionStore()
        val responseCodeStore: ResponseCodeStore = KvResponseCodeStore(
            kvStoreManager = TestKvStoreManager(TestKvStore()),
            kvStoreService = NoConfigKvStoreService(),
            execution = execution
        )

        // Commands
        val createAuthorizationRequest = CreateAuthorizationRequestCommandImpl(
            execution = execution,
            authorizationSessionStore = sessionStore
        )
        val parseAuthorizationResponse = ParseAuthorizationResponseCommandImpl(
            execution = execution,
            verifyJarmCommand = verifyJarmCommand
        )
        val handleDirectPost = HandleDirectPostResponseCommandImpl(
            execution = execution,
            parseAuthorizationResponseCommand = parseAuthorizationResponse,
            validateAuthorizationResponseCommand = MockValidateAuthorizationResponseCommand(),
            authorizationSessionStore = sessionStore,
            responseCodeStore = responseCodeStore
        )

        // 1) Create authorization request with direct_post.jwt response mode
        val created = createAuthorizationRequest.createAuthorizationRequest(
            CreateAuthorizationRequestArgs(
                dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt"))),
                clientId = "https://verifier.example.com",
                responseUri = "https://verifier.example.com/response",
                responseMode = ResponseMode.DIRECT_POST_JWT,
                nonce = "nonce12345678",
                state = "state-1234"
            )
        ).getOrThrow()

        // 2) Wallet creates a signed JARM response containing vp_token + state
        val vpTokenJson = buildJsonObject {
            put("cred", JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.eyJpYXQiOjE3MDAwMDAwMDB9.sig~eyJhbGciOiJub25lIn0~"))
        }
        val responseParameters: JsonObject = buildJsonObject {
            put("vp_token", vpTokenJson)
        }
        val jarmJwt = createJarmCommand.execute(
            CreateJarmResponseArgs(
                responseParameters = responseParameters,
                state = "state-1234",
                issuer = "https://wallet.example.com",
                audience = "https://verifier.example.com",
                signingKey = walletKeyInfo,
                jarmConfig = JarmConfig.signed()
            )
        ).getOrThrow().jarmJwt

        // 3) RP backend handles direct_post.jwt (response=<JARM>)
        val handled = handleDirectPost.handleDirectPostResponse(
            HandleDirectPostResponseArgs(
                responseParams = mapOf(
                    "response" to jarmJwt
                ),
                originalRequest = created.request,
                dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt"))),
                redirectUri = "https://verifier.example.com/callback",
                jarmExpectedAudience = "https://verifier.example.com",
                jarmSignerIdentifier = walletKeyInfo
            )
        ).getOrThrow()

        assertNotNull(handled.responseCode)
        assertTrue(handled.redirectUri.contains("response_code="))
    }

    private class MockVerifyJarmResponseCommand : VerifyJarmResponseCommand {
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<VerifyJarmResponseArgs> = typeToken<VerifyJarmResponseArgs>()
        override val outputTypeToken: TypeToken<JarmVerificationResult> = typeToken<JarmVerificationResult>()

        override suspend fun execute(args: VerifyJarmResponseArgs): IdkResult<JarmVerificationResult, IdkError> {
            return Err(IdkError.fromString("Mock JARM verification not implemented"))
        }
    }

    private class MockValidateAuthorizationResponseCommand : ValidateAuthorizationResponseCommand {
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<ValidateAuthorizationResponseArgs> = typeToken<ValidateAuthorizationResponseArgs>()
        override val outputTypeToken: TypeToken<ValidationResult> = typeToken<ValidationResult>()

        override suspend fun execute(args: ValidateAuthorizationResponseArgs): IdkResult<ValidationResult, IdkError> {
            return Ok(ValidationResult(valid = true, matchedCredentials = emptyList(), errors = emptyList()))
        }
    }

    private class NoConfigKvStoreService : KvStoreService {
        override fun getStoreIds(): Array<String> = emptyArray()
        override fun getStoreConfig(storeId: String): KvStoreConfig = throw IllegalArgumentException("KV store '$storeId' not configured for test")
        override fun getStore(storeId: String): KvStore = throw IllegalArgumentException("KV store '$storeId' not configured for test")
    }

    private class TestKvStoreManager(
        private val store: KvStore
    ) : KvStoreManager {
        override fun createFromKvStoreConfig(config: KvStoreConfigBase): KvStore = store
        override fun createFromKvStoreConfig(config: KvStoreConfigBase, execution: SessionExecution?): KvStore = store
        override fun createFromProperties(configService: com.sphereon.core.api.conf.ConfigService, execution: SessionExecution?): Set<KvStore> = setOf(store)
    }

    private class TestKvStore : KvStore {
        override val config: KvStoreConfig = KvStoreConfig(id = "test", scopeBinding = KvStoreScopeBinding.TENANT)

        private data class Stored(
            val bytes: ByteArray,
            val createdAt: Long,
            val expiresAt: Long
        )

        private val entries = mutableMapOf<String, Stored>()

        private fun k(namespace: KvNamespaceId, key: String) = "${namespace.name}:$key"

        override suspend fun <V : Any> put(
            namespace: KvNamespace<V>,
            key: String,
            value: V,
            ttl: Duration
        ): IdkResult<KvPutResult, IdkError> {
            val now = Clock.System.now().toEpochMilliseconds()
            val expiresAt = if (ttl.isInfinite()) Long.MAX_VALUE else now + ttl.inWholeMilliseconds
            entries[k(namespace, key)] = Stored(bytes = namespace.codec.encode(value), createdAt = now, expiresAt = expiresAt)
            return Ok(KvPutResult(metadata = KvEntryMetadata(createdAtEpochMillis = now, expiresAtEpochMillis = expiresAt)))
        }

        override suspend fun <V : Any> get(namespace: KvNamespace<V>, key: String): IdkResult<V?, IdkError> {
            return getEntry(namespace, key).map { it?.value }
        }

        override suspend fun <V : Any> getEntry(namespace: KvNamespace<V>, key: String): IdkResult<KvEntry<V>?, IdkError> {
            val now = Clock.System.now().toEpochMilliseconds()
            val stored = entries[k(namespace, key)] ?: return Ok(null)
            if (stored.expiresAt <= now) {
                entries.remove(k(namespace, key))
                return Ok(null)
            }
            val value = namespace.codec.decode(stored.bytes)
            return Ok(
                KvEntry(
                    value = value,
                    metadata = KvEntryMetadata(createdAtEpochMillis = stored.createdAt, expiresAtEpochMillis = stored.expiresAt)
                )
            )
        }

        override suspend fun delete(namespace: KvNamespaceId, key: String): IdkResult<Boolean, IdkError> {
            return Ok(entries.remove(k(namespace, key)) != null)
        }

        override suspend fun exists(namespace: KvNamespaceId, key: String): IdkResult<Boolean, IdkError> {
            val now = Clock.System.now().toEpochMilliseconds()
            val stored = entries[k(namespace, key)] ?: return Ok(false)
            if (stored.expiresAt <= now) {
                entries.remove(k(namespace, key))
                return Ok(false)
            }
            return Ok(true)
        }

        override suspend fun touch(namespace: KvNamespaceId, key: String, ttl: Duration): IdkResult<Boolean, IdkError> {
            val now = Clock.System.now().toEpochMilliseconds()
            val stored = entries[k(namespace, key)] ?: return Ok(false)
            if (stored.expiresAt <= now) {
                entries.remove(k(namespace, key))
                return Ok(false)
            }
            val expiresAt = if (ttl.isInfinite()) Long.MAX_VALUE else now + ttl.inWholeMilliseconds
            entries[k(namespace, key)] = stored.copy(expiresAt = expiresAt)
            return Ok(true)
        }

        override suspend fun cleanupExpired(namespace: KvNamespaceId?): IdkResult<Int, IdkError> {
            val now = Clock.System.now().toEpochMilliseconds()
            val keys = entries.keys.toList()
            var removed = 0
            for (fullKey in keys) {
                if (namespace != null && !fullKey.startsWith("${namespace.name}:")) continue
                val stored = entries[fullKey] ?: continue
                if (stored.expiresAt <= now) {
                    entries.remove(fullKey)
                    removed++
                }
            }
            return Ok(removed)
        }
    }
}

@ContributesTo(SessionScope::class)
interface RpServiceComponent {
    val oid4vpVerifierService: Oid4vpVerifierService
}

@ContributesTo(SessionScope::class)
interface Oauth2JarComponent {
    val createSignedJarCommand: CreateSignedJarCommand
    val jarService: JarService
}

@ContributesTo(SessionScope::class)
interface HolderDepsComponent {
    val parseUriQueryCommand: ParseUriQueryCommand
    val externalIdentifierService: MultiExternalIdentifierService
    val resolveAuthorizationRequestCommand: ResolveAuthorizationRequestCommand
}

/**
 * Universal OpenID4VP E2E test using real crypto/KMS and real KV-backed stores.
 *
 * Only the HTTP layer is intercepted using a Ktor MockEngine that routes:
 * - `GET /oid4vp/request-uri/{correlationId}` to the real RP HTTP adapter + request_uri handler
 * - `POST /response` (direct_post) to the real RP command pipeline (parse + response_code protection)
 */
class UniversalOid4vpE2ETest {

    @Test
    fun `universal oid4vp e2e - request_uri fetch, holder parses and resolves, direct_post returns response_code`() = runTest {
        val testScope = TestScope()
        val app = createOid4vpRpJvmTestAppComponent(testScope, "test-verifier-app", "test", "1.0.0")

        // Configure a memory-backed software KMS provider via the standard property source mechanism.
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "test-verifier-app.test.kms.providers.test-software.type" to "software",
                "test-verifier-app.test.kms.providers.test-software.id" to "test-software",
                "test-verifier-app.test.kms.providers.test-software.keystore.type" to "memory",
                "test-verifier-app.test.kms.providers.test-software.keystore.id" to "test-memory-keystore",
                "test-verifier-app.test.kms.providers.test-software.keystore.keyVisibility" to "private",
                "test-verifier-app.test.kms.providers.test-software.keystore.overwriteAlias" to "true"
            )
        )
        app.userContextManager.destroyAll()

        // Verifier (RP) session
        val verifierContext = app.userContextManager.getAnonymous()
        val verifierSession = verifierContext.sessionContextManager.createOrGetFromId("verifier")
        val verifierComponent = verifierSession.component

        val rpService = (verifierComponent as RpServiceComponent).oid4vpVerifierService
        val kms = verifierComponent.asKeyManagerServiceComponent().keyManagerService
        val createSignedJarCommand = (verifierComponent as Oauth2JarComponent).createSignedJarCommand

        // Generate a real signing key (software KMS) for signing request objects (JAR).
        // The alias must match TestRequestUriSigningConfig.signingKey.kid
        val jarKeyPair = kms.generateKeyAsync(
            providerId = "test-software",
            alias = "test-request-uri-signing-key",
            use = JwkUse.sig,
            keyOperations = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
            alg = SignatureAlgorithm.ECDSA_SHA256,
            keyVisibility = KeyVisibility.PRIVATE
        )
        val jarSigningKeyInfo = jarKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        // Build verifier client metadata with embedded JWKS so the holder can verify the signed request object.
        val clientMetadata = ClientMetadata(
            baseMetadata = ClientRegistration(
                clientId = "https://verifier.example.com",
                clientName = "Test Verifier",
                clientType = ClientType.CONFIDENTIAL,
                grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
                responseTypes = listOf(ResponseType.CODE),
                redirectUris = listOf("https://frontend.example.com/callback"),
                jwks = JwkSet(keys = arrayOf(jarKeyPair.jose.publicJwk))
            )
        )

        // 1) RP creates an authorization request + stores the authorization session (KV-backed).
        val created = rpService.createAuthorizationRequest(
            CreateAuthorizationRequestArgs(
                dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt"))),
                clientId = "https://verifier.example.com",
                responseUri = "https://verifier.example.com/response",
                redirectUri = "https://frontend.example.com/callback",
                responseMode = ResponseMode.DIRECT_POST,
                nonce = "nonce12345678",
                state = "state-1234",
                clientMetadata = clientMetadata
            )
        ).getOrThrow()
        assertEquals("state-1234", created.request.state)

        val correlationId = created.sessionId ?: error("Expected RP to return a sessionId")
        val requestUri = "https://verifier.example.com${Oid4vpVerifierHttpAdapter.REQUEST_URI_PREFIX}$correlationId"

        // Build the actual OID4VP request URI that the wallet receives (deep link / QR content).
        val requestUriLink = rpService.buildAuthorizationRequestUri(
            BuildAuthorizationRequestUriArgs(
                request = created.request,
                useRequestUri = true,
                requestUri = requestUri
            )
        ).getOrThrow().value
        assertTrue("client_metadata" in Url(requestUriLink).parameters.names())

        // Get the HTTP adapter from DI - it's injected with all required commands
        val rpAdapter: HttpAdapter = (verifierComponent as Oid4vpVerifierHttpAdapter.Component).oid4VpVerifierHttpAdapter
        val dispatcher = HttpAdapterDispatcher(setOf(rpAdapter))

        val httpClientFactory = object : HttpClientFactory {
            override fun createClient(options: HttpClientOptions): HttpClient {
                val engine = MockEngine { request ->
                    val url: Url = request.url
                    val path: String = url.encodedPath

                    // 1) request_uri fetch: wallet -> RP
                    if (request.method == HttpMethod.Get && path.startsWith(Oid4vpVerifierHttpAdapter.REQUEST_URI_PREFIX)) {
                        val resp = dispatcher.dispatch(
                            GenericHttpRequest(
                                method = "GET",
                                path = path,
                                headers = request.headers.entries().associate { (k, v) -> k to v.joinToString(",") }
                            )
                        )
                        return@MockEngine respond(
                            content = resp.body ?: "",
                            status = HttpStatusCode.fromValue(resp.statusCode),
                            headers = headersOf(*resp.headers.map { (k, v) -> k to listOf(v) }.toTypedArray())
                        )
                    }

                    // 2) direct_post: wallet -> RP response_uri (simulate the RP HTTP endpoint wiring)
                    if (request.method == HttpMethod.Post && url.toString() == "https://verifier.example.com/response") {
                        val bodyText = when (val body = request.body) {
                            is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
                            is OutgoingContent.NoContent -> ""
                            else -> ""
                        }
                        val params = parseQueryString(bodyText)
                        val responseParams: Map<String, String> = params.names().associateWith { name ->
                            params.getAll(name)?.firstOrNull().orEmpty()
                        }

                        val handled = rpService.handleDirectPostResponse(
                            HandleDirectPostResponseArgs(
                                responseParams = responseParams,
                                originalRequest = created.request,
                                dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt"))),
                                redirectUri = "https://frontend.example.com/callback"
                            )
                        ).getOrThrow()

                        return@MockEngine respond(
                            content = handled.redirectUri,
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type" to listOf(ContentType.Text.Plain.toString()))
                        )
                    }

                    respond("Not found", HttpStatusCode.NotFound)
                }

                return HttpClient(engine)
            }

            override fun isSupportedOptions(options: HttpClientOptions): Boolean = true
            override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.CIO, HttpClientEngineType.OKHTTP, HttpClientEngineType.DARWIN)
            override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
        }

        // Holder (Wallet) session
        val holderContext = app.userContextManager.getAnonymous()
        val holderSession = holderContext.sessionContextManager.createOrGetFromId("holder")
        val holderExecution = holderSession.asCoreApiServiceComponent().serviceExecution
        val holderComponent = holderSession.component
        val parseUriQueryCommand = (holderComponent as HolderDepsComponent).parseUriQueryCommand
        val externalIdentifierService = (holderComponent as HolderDepsComponent).externalIdentifierService
        val jarService = (holderComponent as Oauth2JarComponent).jarService
        val resolveAuthorizationRequestCommand = (holderComponent as HolderDepsComponent).resolveAuthorizationRequestCommand

        // Construct holder-side parsing command with an HTTP factory that is intercepted in-process.
        val fetchRequestUriCommand = FetchRequestUriCommandImpl(
            execution = holderExecution,
            httpClientFactory = httpClientFactory
        )
        val parseAuthorizationRequestCommand = ParseAuthorizationRequestCommandImpl(
            execution = holderExecution,
            parseUriQueryCommand = parseUriQueryCommand,
            fetchRequestUriCommand = fetchRequestUriCommand,
            jarService = jarService,
            httpClientFactory = httpClientFactory,
            externalIdentifierService = externalIdentifierService
        )

        // 2) Holder fetches request_uri, verifies JAR signature, and parses the Authorization Request.
        val parsedRequest = parseAuthorizationRequestCommand.execute(
            ParseAuthorizationRequestArgs(requestUri = requestUriLink, walletConfig = null)).getOrThrow()

        // 3) Holder resolves the request (DCQL parse + client_id validation etc).
        val resolved = resolveAuthorizationRequestCommand.execute(parsedRequest).getOrThrow()
        assertNotNull(resolved.dcqlQuery)
        assertEquals("https://verifier.example.com", resolved.request.clientId)

        // 4) Holder sends direct_post response to response_uri (HTTP intercepted).
        val vpToken = """{"cred":"eyJhbGciOiJFUzI1NiJ9.eyJpYXQiOjE3MDAwMDAwMDB9.sig~eyJhbGciOiJub25lIn0~"}"""
        val responseUri = parsedRequest.responseUri ?: error("Expected response_uri for direct_post")

        val walletClient = httpClientFactory.createClient(HttpClientOptions.createDefault().copy(enableLogging = false))
        val redirectUri = walletClient.post(responseUri) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                Parameters.build {
                    append("vp_token", vpToken)
                    append("state", "state-1234")
                }.formUrlEncode()
            )
        }.bodyAsText()
        walletClient.close()

        val responseCode = Url(redirectUri).parameters["response_code"]
        assertNotNull(responseCode)

        // 5) RP retrieves the response by response_code (KV-backed, single-use).
        val retrieved = rpService.retrieveAuthorizationResponse(
            RetrieveAuthorizationResponseArgs(
                responseCode = responseCode,
                markAsUsed = true
            )
        ).getOrThrow()

        assertEquals("state-1234", retrieved.state)
        assertEquals(1, retrieved.parsedResponse.vpToken.presentations.size)

        // Optional: verify the RP session status has advanced as expected.
        val sessionAfter = rpService.authorizationSessionStore.getByCorrelationId(correlationId).getOrThrow()
        assertNotNull(sessionAfter)
        assertTrue(
            sessionAfter.status.name == com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_RECEIVED.name ||
                sessionAfter.status.name == com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_VERIFIED.name
        )
    }

    @Test
    fun `universal oid4vp e2e - request_uri fetch, holder resolves, direct_post_jwt (encrypted) returns response_code`() = runTest {
        val testScope = TestScope()
        val app = createOid4vpRpJvmTestAppComponent(testScope, "test-verifier-app", "test", "1.0.0")

        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "test-verifier-app.test.kms.providers.test-software.type" to "software",
                "test-verifier-app.test.kms.providers.test-software.id" to "test-software",
                "test-verifier-app.test.kms.providers.test-software.keystore.type" to "memory",
                "test-verifier-app.test.kms.providers.test-software.keystore.id" to "test-memory-keystore",
                "test-verifier-app.test.kms.providers.test-software.keystore.keyVisibility" to "private",
                "test-verifier-app.test.kms.providers.test-software.keystore.overwriteAlias" to "true"
            )
        )
        app.userContextManager.destroyAll()

        // Verifier (RP) session
        val verifierContext = app.userContextManager.getAnonymous()
        val verifierSession = verifierContext.sessionContextManager.createOrGetFromId("verifier-jarm")
        val verifierComponent = verifierSession.component

        val rpService = (verifierComponent as RpServiceComponent).oid4vpVerifierService
        val kms = verifierComponent.asKeyManagerServiceComponent().keyManagerService
        val createSignedJarCommand = (verifierComponent as Oauth2JarComponent).createSignedJarCommand

        // Signing key for request objects (JAR)
        // The alias must match TestRequestUriSigningConfig.signingKey.kid
        val jarKeyPair = kms.generateKeyAsync(
            providerId = "test-software",
            alias = "test-request-uri-signing-key",
            use = JwkUse.sig,
            keyOperations = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
            alg = SignatureAlgorithm.ECDSA_SHA256,
            keyVisibility = KeyVisibility.PRIVATE
        )
        val jarSigningKeyInfo = jarKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        // Encryption key for JARM responses (wallet encrypts to verifier).
        val jarmEncKeyPair = kms.generateKeyAsync(
            providerId = "test-software",
            alias = "verifier-jarm-enc",
            use = JwkUse.enc,
            keyOperations = arrayOf(KeyOperations.ENCRYPT, KeyOperations.DECRYPT),
            alg = SignatureAlgorithm.RSA_SHA256,
            keyVisibility = KeyVisibility.PRIVATE
        )
        val jarmDecryptionKey = ManagedOptsKeyInfo(identifier = jarmEncKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE))
        val jwksUri = "https://verifier.example.com/jwks"

        // Client metadata:
        // - embedded JWKS for JAR signature verification (sig key only)
        // - jwks_uri for JARM encryption recipient resolution (enc key served over HTTP)
        val clientMetadata = ClientMetadata(
            baseMetadata = ClientRegistration(
                clientId = "https://verifier.example.com",
                clientName = "Test Verifier",
                clientType = ClientType.CONFIDENTIAL,
                grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
                responseTypes = listOf(ResponseType.CODE),
                redirectUris = listOf("https://frontend.example.com/callback"),
                jwks = JwkSet(keys = arrayOf(jarKeyPair.jose.publicJwk)),
                jwksUri = jwksUri
            ),
            authorizationEncryptedResponseAlg = "RSA-OAEP",
            authorizationEncryptedResponseEnc = "A256GCM"
        )

        val created = rpService.createAuthorizationRequest(
            CreateAuthorizationRequestArgs(
                dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt"))),
                clientId = "https://verifier.example.com",
                responseUri = "https://verifier.example.com/response",
                redirectUri = "https://frontend.example.com/callback",
                responseMode = ResponseMode.DIRECT_POST_JWT,
                nonce = "nonce12345678",
                state = "state-1234",
                clientMetadata = clientMetadata
            )
        ).getOrThrow()
        val correlationId = created.sessionId ?: error("Expected RP to return a sessionId")

        val requestUri = "https://verifier.example.com${Oid4vpVerifierHttpAdapter.REQUEST_URI_PREFIX}$correlationId"

        val requestUriLink = rpService.buildAuthorizationRequestUri(
            BuildAuthorizationRequestUriArgs(
                request = created.request,
                useRequestUri = true,
                requestUri = requestUri
            )
        ).getOrThrow().value

        // Get the HTTP adapter from DI - it's injected with all required commands
        val rpAdapter: HttpAdapter = (verifierComponent as Oid4vpVerifierHttpAdapter.Component).oid4VpVerifierHttpAdapter
        val dispatcher = HttpAdapterDispatcher(setOf(rpAdapter))

        val jwksJson = Json.encodeToString(
            JwkSet.serializer(),
            JwkSet(keys = arrayOf(jarmEncKeyPair.jose.publicJwk))
        )
        var jwksFetchCount = 0

        // Intercept HTTP calls in-process, while still exercising the RP command pipeline.
        val httpClientFactory = object : HttpClientFactory {
            override fun createClient(options: HttpClientOptions): HttpClient {
                val engine = MockEngine { request ->
                    val url: Url = request.url
                    val path: String = url.encodedPath

                    if (request.method == HttpMethod.Get && url.toString() == jwksUri) {
                        jwksFetchCount++
                        return@MockEngine respond(
                            content = jwksJson,
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
                        )
                    }

                    // request_uri fetch: wallet -> RP
                    if (request.method == HttpMethod.Get && path.startsWith(Oid4vpVerifierHttpAdapter.REQUEST_URI_PREFIX)) {
                        val resp = dispatcher.dispatch(
                            GenericHttpRequest(
                                method = "GET",
                                path = path,
                                headers = request.headers.entries().associate { (k, v) -> k to v.joinToString(",") }
                            )
                        )
                        return@MockEngine respond(
                            content = resp.body ?: "",
                            status = HttpStatusCode.fromValue(resp.statusCode),
                            headers = headersOf(*resp.headers.map { (k, v) -> k to listOf(v) }.toTypedArray())
                        )
                    }

                    // direct_post(.jwt): wallet -> RP response_uri
                    if (request.method == HttpMethod.Post && url.toString() == "https://verifier.example.com/response") {
                        val bodyText = when (val body = request.body) {
                            is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
                            is OutgoingContent.NoContent -> ""
                            else -> ""
                        }
                        val params = parseQueryString(bodyText)
                        val responseParams: Map<String, String> = params.names().associateWith { name ->
                            params.getAll(name)?.firstOrNull().orEmpty()
                        }

                        val handled = rpService.handleDirectPostResponse(
                            HandleDirectPostResponseArgs(
                                responseParams = responseParams,
                                originalRequest = created.request,
                                dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt"))),
                                redirectUri = "https://frontend.example.com/callback",
                                jarmExpectedAudience = "https://verifier.example.com",
                                jarmDecryptionKey = jarmDecryptionKey
                            )
                        ).getOrThrow()

                        val responseBody = """{"redirect_uri":"${handled.redirectUri}","response_code":"${handled.responseCode}"}"""
                        return@MockEngine respond(
                            content = responseBody,
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
                        )
                    }

                    respond("Not found", HttpStatusCode.NotFound)
                }

                return HttpClient(engine)
            }

            override fun isSupportedOptions(options: HttpClientOptions): Boolean = true
            override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.CIO, HttpClientEngineType.OKHTTP, HttpClientEngineType.DARWIN)
            override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
        }

        // Holder (Wallet) session
        val holderContext = app.userContextManager.getAnonymous()
        val holderSession = holderContext.sessionContextManager.createOrGetFromId("holder-jarm")
        val holderExecution = holderSession.asCoreApiServiceComponent().serviceExecution
        val holderComponent = holderSession.component

        val parseUriQueryCommand = (holderComponent as HolderDepsComponent).parseUriQueryCommand
        // IMPORTANT: For this E2E, we intercept HTTP with a Ktor MockEngine. The DI-provided
        // external identifier resolution service uses the platform HttpClientFactory (OKHTTP on JVM)
        // and would attempt a real network call. We explicitly wire a JWKS URL resolver that uses the
        // intercepted HttpClientFactory instead, to keep the flow end-to-end while staying in-process.
        val externalIdentifierService: MultiExternalIdentifierService = MultiExternalIdentifierResolutionServiceImpl(
            execution = holderExecution,
            external = setOf<ExternalIdentifierService>(
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = holderExecution,
                    httpClientFactory = httpClientFactory
                )
            )
        )
        val jarService = (holderComponent as Oauth2JarComponent).jarService
        val resolveAuthorizationRequestCommand = (holderComponent as HolderDepsComponent).resolveAuthorizationRequestCommand

        val fetchRequestUriCommand = FetchRequestUriCommandImpl(
            execution = holderExecution,
            httpClientFactory = httpClientFactory
        )
        val parseAuthorizationRequestCommand = ParseAuthorizationRequestCommandImpl(
            execution = holderExecution,
            parseUriQueryCommand = parseUriQueryCommand,
            fetchRequestUriCommand = fetchRequestUriCommand,
            jarService = jarService,
            httpClientFactory = httpClientFactory,
            externalIdentifierService = externalIdentifierService
        )

        val parsedRequest = parseAuthorizationRequestCommand.execute(
            ParseAuthorizationRequestArgs(requestUri = requestUriLink, walletConfig = null)).getOrThrow()
        val resolved = resolveAuthorizationRequestCommand.execute(parsedRequest).getOrThrow()

        val submitAuthorizationResponseCommand = SubmitAuthorizationResponseCommandImpl(
            execution = holderExecution,
            httpClientFactory = httpClientFactory,
            externalIdentifierService = externalIdentifierService,
            createJarmCommand = CreateJarmResponseCommandImpl(
                execution = holderExecution,
                jwtService = (holderComponent as JwtServiceImpl.Component).jwtService,
                jweService = (holderComponent as JweServiceImpl.Component).jweService
            )
        )

        val response = buildOid4vpAuthorizationResponse {
            vpToken("cred", "eyJhbGciOiJFUzI1NiJ9.eyJpYXQiOjE3MDAwMDAwMDB9.sig~eyJhbGciOiJub25lIn0~")
            state("state-1234")
        }

        val submission = submitAuthorizationResponseCommand.execute(
            SubmitAuthorizationResponseArgs(
                resolvedRequest = resolved,
                response = response,
                responseMode = ResponseMode.DIRECT_POST_JWT,
                jarmOptions = JarmOptions(issuer = "https://wallet.example.com")
            )).getOrThrow()

        val redirectUri = (submission as SubmissionResult.Success).redirectUri
            ?: error("Expected redirectUri from RP backend")
        val responseCode = Url(redirectUri).parameters["response_code"]
        assertNotNull(responseCode)
        assertEquals(1, jwksFetchCount)

        val retrieved = rpService.retrieveAuthorizationResponse(
            RetrieveAuthorizationResponseArgs(
                responseCode = responseCode,
                markAsUsed = true
            )
        ).getOrThrow()
        assertEquals("state-1234", retrieved.state)
        assertEquals(1, retrieved.parsedResponse.vpToken.presentations.size)
    }

    @Test
    fun `universal oid4vp e2e - request_uri fetch, holder resolves, direct_post_jwt (signed) returns response_code`() = runTest {
        val testScope = TestScope()
        val app = createOid4vpRpJvmTestAppComponent(testScope, "test-verifier-app", "test", "1.0.0")

        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "test-verifier-app.test.kms.providers.test-software.type" to "software",
                "test-verifier-app.test.kms.providers.test-software.id" to "test-software",
                "test-verifier-app.test.kms.providers.test-software.keystore.type" to "memory",
                "test-verifier-app.test.kms.providers.test-software.keystore.id" to "test-memory-keystore",
                "test-verifier-app.test.kms.providers.test-software.keystore.keyVisibility" to "private",
                "test-verifier-app.test.kms.providers.test-software.keystore.overwriteAlias" to "true"
            )
        )
        app.userContextManager.destroyAll()

        // Verifier (RP) session
        val verifierContext = app.userContextManager.getAnonymous()
        val verifierSession = verifierContext.sessionContextManager.createOrGetFromId("verifier-jarm-signed")
        val verifierComponent = verifierSession.component

        val rpService = (verifierComponent as RpServiceComponent).oid4vpVerifierService
        val verifierKms = verifierComponent.asKeyManagerServiceComponent().keyManagerService
        val createSignedJarCommand = (verifierComponent as Oauth2JarComponent).createSignedJarCommand

        // Signing key for request objects (JAR)
        // The alias must match TestRequestUriSigningConfig.signingKey.kid
        val jarKeyPair = verifierKms.generateKeyAsync(
            providerId = "test-software",
            alias = "test-request-uri-signing-key",
            use = JwkUse.sig,
            keyOperations = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
            alg = SignatureAlgorithm.ECDSA_SHA256,
            keyVisibility = KeyVisibility.PRIVATE
        )
        val jarSigningKeyInfo = jarKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        // Client metadata:
        // - embedded JWKS for JAR signature verification
        // - JARM signing algorithm (wallet will sign the response)
        val clientMetadata = ClientMetadata(
            baseMetadata = ClientRegistration(
                clientId = "https://verifier.example.com",
                clientName = "Test Verifier",
                clientType = ClientType.CONFIDENTIAL,
                grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
                responseTypes = listOf(ResponseType.CODE),
                redirectUris = listOf("https://frontend.example.com/callback"),
                jwks = JwkSet(keys = arrayOf(jarKeyPair.jose.publicJwk))
            ),
            authorizationSignedResponseAlg = "ES256"
        )

        val created = rpService.createAuthorizationRequest(
            CreateAuthorizationRequestArgs(
                dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt"))),
                clientId = "https://verifier.example.com",
                responseUri = "https://verifier.example.com/response",
                redirectUri = "https://frontend.example.com/callback",
                responseMode = ResponseMode.DIRECT_POST_JWT,
                nonce = "nonce12345678",
                state = "state-1234",
                clientMetadata = clientMetadata
            )
        ).getOrThrow()
        val correlationId = created.sessionId ?: error("Expected RP to return a sessionId")

        val requestUri = "https://verifier.example.com${Oid4vpVerifierHttpAdapter.REQUEST_URI_PREFIX}$correlationId"

        val requestUriLink = rpService.buildAuthorizationRequestUri(
            BuildAuthorizationRequestUriArgs(
                request = created.request,
                useRequestUri = true,
                requestUri = requestUri
            )
        ).getOrThrow().value

        // Get the HTTP adapter from DI - it's injected with all required commands
        val rpAdapter: HttpAdapter = (verifierComponent as Oid4vpVerifierHttpAdapter.Component).oid4VpVerifierHttpAdapter
        val dispatcher = HttpAdapterDispatcher(setOf(rpAdapter))

        // Holder (Wallet) session
        val holderContext = app.userContextManager.getAnonymous()
        val holderSession = holderContext.sessionContextManager.createOrGetFromId("holder-jarm-signed")
        val holderExecution = holderSession.asCoreApiServiceComponent().serviceExecution
        val holderComponent = holderSession.component

        val holderKms = holderComponent.asKeyManagerServiceComponent ().keyManagerService
        val walletSigningKeyPair = holderKms.generateKeyAsync(
            providerId = "test-software",
            alias = "wallet-jarm-signing",
            use = JwkUse.sig,
            keyOperations = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
            alg = SignatureAlgorithm.ECDSA_SHA256
        )
        val walletSigningKey = ManagedOptsKeyInfo(
            identifier = (ResolvedKeyInfo.fromKey(walletSigningKeyPair.jose.publicJwk) as ResolvedKeyInfo).copy(
                providerId = "test-software",
                alias = walletSigningKeyPair.alias,
                kid = walletSigningKeyPair.kid,
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
            )
        )
        val walletSignerIdentifier = ManagedOptsKeyInfo(identifier = ResolvedKeyInfo.fromKey(walletSigningKeyPair.jose.publicJwk))

        // Intercept HTTP calls in-process, while still exercising the RP command pipeline.
        val httpClientFactory = object : HttpClientFactory {
            override fun createClient(options: HttpClientOptions): HttpClient {
                val engine = MockEngine { request ->
                    val url: Url = request.url
                    val path: String = url.encodedPath

                    // request_uri fetch: wallet -> RP
                    if (request.method == HttpMethod.Get && path.startsWith(Oid4vpVerifierHttpAdapter.REQUEST_URI_PREFIX)) {
                        val resp = dispatcher.dispatch(
                            GenericHttpRequest(
                                method = "GET",
                                path = path,
                                headers = request.headers.entries().associate { (k, v) -> k to v.joinToString(",") }
                            )
                        )
                        return@MockEngine respond(
                            content = resp.body ?: "",
                            status = HttpStatusCode.fromValue(resp.statusCode),
                            headers = headersOf(*resp.headers.map { (k, v) -> k to listOf(v) }.toTypedArray())
                        )
                    }

                    // direct_post.jwt: wallet -> RP response_uri
                    if (request.method == HttpMethod.Post && url.toString() == "https://verifier.example.com/response") {
                        val bodyText = when (val body = request.body) {
                            is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
                            is OutgoingContent.NoContent -> ""
                            else -> ""
                        }
                        val params = parseQueryString(bodyText)
                        val responseParams: Map<String, String> = params.names().associateWith { name ->
                            params.getAll(name)?.firstOrNull().orEmpty()
                        }

                        val handled = rpService.handleDirectPostResponse(
                            HandleDirectPostResponseArgs(
                                responseParams = responseParams,
                                originalRequest = created.request,
                                dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt"))),
                                redirectUri = "https://frontend.example.com/callback",
                                jarmExpectedAudience = "https://verifier.example.com",
                                jarmSignerIdentifier = walletSignerIdentifier
                            )
                        ).getOrThrow()

                        val responseBody = """{"redirect_uri":"${handled.redirectUri}","response_code":"${handled.responseCode}"}"""
                        return@MockEngine respond(
                            content = responseBody,
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
                        )
                    }

                    respond("Not found", HttpStatusCode.NotFound)
                }

                return HttpClient(engine)
            }

            override fun isSupportedOptions(options: HttpClientOptions): Boolean = true
            override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.CIO, HttpClientEngineType.OKHTTP, HttpClientEngineType.DARWIN)
            override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
        }

        val parseUriQueryCommand = (holderComponent as HolderDepsComponent).parseUriQueryCommand
        val externalIdentifierService: MultiExternalIdentifierService = MultiExternalIdentifierResolutionServiceImpl(
            execution = holderExecution,
            external = setOf<ExternalIdentifierService>(
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = holderExecution,
                    httpClientFactory = httpClientFactory
                )
            )
        )
        val jarService = (holderComponent as Oauth2JarComponent).jarService
        val resolveAuthorizationRequestCommand = (holderComponent as HolderDepsComponent).resolveAuthorizationRequestCommand

        val fetchRequestUriCommand = FetchRequestUriCommandImpl(
            execution = holderExecution,
            httpClientFactory = httpClientFactory
        )
        val parseAuthorizationRequestCommand = ParseAuthorizationRequestCommandImpl(
            execution = holderExecution,
            parseUriQueryCommand = parseUriQueryCommand,
            fetchRequestUriCommand = fetchRequestUriCommand,
            jarService = jarService,
            httpClientFactory = httpClientFactory,
            externalIdentifierService = externalIdentifierService
        )

        val parsedRequest = parseAuthorizationRequestCommand.execute(
            ParseAuthorizationRequestArgs(requestUri = requestUriLink, walletConfig = null)).getOrThrow()
        val resolved = resolveAuthorizationRequestCommand.execute(parsedRequest).getOrThrow()

        val submitAuthorizationResponseCommand = SubmitAuthorizationResponseCommandImpl(
            execution = holderExecution,
            httpClientFactory = httpClientFactory,
            externalIdentifierService = externalIdentifierService,
            createJarmCommand = CreateJarmResponseCommandImpl(
                execution = holderExecution,
                jwtService = (holderComponent as JwtServiceImpl.Component).jwtService,
                jweService = (holderComponent as JweServiceImpl.Component).jweService
            )
        )

        val response = buildOid4vpAuthorizationResponse {
            vpToken("cred", "eyJhbGciOiJFUzI1NiJ9.eyJpYXQiOjE3MDAwMDAwMDB9.sig~eyJhbGciOiJub25lIn0~")
            state("state-1234")
        }

        val submission = submitAuthorizationResponseCommand.execute(
            SubmitAuthorizationResponseArgs(
                resolvedRequest = resolved,
                response = response,
                responseMode = ResponseMode.DIRECT_POST_JWT,
                jarmOptions = JarmOptions(
                    signingKey = walletSigningKey,
                    issuer = "https://wallet.example.com"
                )
            )).getOrThrow()

        val redirectUri = (submission as SubmissionResult.Success).redirectUri
            ?: error("Expected redirectUri from RP backend")
        val responseCode = Url(redirectUri).parameters["response_code"]
        assertNotNull(responseCode)

        val retrieved = rpService.retrieveAuthorizationResponse(
            RetrieveAuthorizationResponseArgs(
                responseCode = responseCode,
                markAsUsed = true
            )
        ).getOrThrow()
        assertEquals("state-1234", retrieved.state)
        assertEquals(1, retrieved.parsedResponse.vpToken.presentations.size)
    }

}
