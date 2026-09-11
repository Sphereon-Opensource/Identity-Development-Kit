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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.dispatch.HttpAdapterDispatcher
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteSelection
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteSelector
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.jose.jwe.JweServiceImpl
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.extern.ExternalIdentifierService
import com.sphereon.crypto.resolution.extern.JwksUrlExternalIdentifierResolutionServiceImpl
import com.sphereon.crypto.resolution.extern.MultiExternalIdentifierResolutionServiceImpl
import com.sphereon.crypto.resolution.extern.MultiExternalIdentifierService
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
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
import com.sphereon.di.session.SessionScope
import com.sphereon.did.resolver.impl.DidExternalIdentifierResolutionServiceImpl
import com.sphereon.ktor.http.client.FetchRequestUriCommandImpl
import com.sphereon.ktor.http.client.ParseUriQueryCommand
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.client.JarService
import com.sphereon.oauth2.client.command.CreateSignedJarArgs
import com.sphereon.oauth2.client.command.CreateSignedJarCommand
import com.sphereon.oauth2.common.jarm.CreateJarmResponseArgs
import com.sphereon.oauth2.common.jarm.CreateJarmResponseCommandImpl
import com.sphereon.oauth2.common.jarm.JarmVerificationResult
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseArgs
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseCommand
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseCommandImpl
import com.sphereon.openid.oid4vp.common.ClientMetadata
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.common.buildOid4vpAuthorizationResponse
import com.sphereon.openid.oid4vp.common.responseUri
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.sdJwtVcMeta
import com.sphereon.openid.oid4vp.holder.JarmOptions
import com.sphereon.openid.oid4vp.holder.ParseAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.holder.ResolveAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.holder.SubmissionResult
import com.sphereon.openid.oid4vp.holder.SubmitAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.common.impl.UnavailableOid4vpRequestTrustMaterialProvider
import com.sphereon.openid.oid4vp.holder.impl.ParseAuthorizationRequestCommandImpl
import com.sphereon.openid.oid4vp.holder.impl.SubmitAuthorizationResponseCommandImpl
import com.sphereon.openid.oid4vp.universal.impl.createUniversalOid4vpTestAppGraph
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriArgs
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.HandleDirectPostResponseArgs
import com.sphereon.openid.oid4vp.verifier.Oid4vpVerifierService
import com.sphereon.openid.oid4vp.verifier.RetrieveAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import com.sphereon.openid.oid4vp.verifier.config.DEFAULT_OID4VP_VERIFIER_INSTANCE_ID
import com.sphereon.openid.oid4vp.verifier.impl.http.Oid4vpVerifierHttpAdapter
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.store.ResponseCodeStore
import com.sphereon.sdjwt.IssueSdJwtArgs
import com.sphereon.sdjwt.PresentSdJwtArgs
import com.sphereon.sdjwt.SdField
import com.sphereon.sdjwt.SdJwtServiceImpl
import com.sphereon.sdjwt.SdMap
import com.sphereon.sdjwt.dsl.sdJwtPayload
import dev.zacsweers.metro.ContributesTo
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
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration

/*
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
    fun `end-to-end flow - create session, serve request_uri, handle direct_post, retrieve by response_code`() =
        runTest {
            val testScope = TestScope()
            val app = createUniversalOid4vpTestAppGraph(testScope, appId = "test-verifier-app", profile = "test", version = "1.0.0")
            val context = app.userContextManager.getAnonymous()
            val session = context.sessionContextManager.createOrGetFromId("test", principalType = com.sphereon.di.context.PrincipalType.USER)
            val execution: SessionExecution = session.sessionExecution

            // Stores
            val sessionStore = TestAuthorizationSessionStore()
            val responseCodeStore: ResponseCodeStore =
                KvResponseCodeStore(
                    kvStoreManager = TestKvStoreManager(TestKvStore()),
                    kvStoreService = NoConfigKvStoreService(),
                    execution = execution,
                    clock = Clock.System,
                )

            // Fixed did:jwk identity for this test (any well-formed did:jwk; no KMS lookup needed
            // because the JAR signer command below is mocked and we only care about the binding plumbing).
            val testDid = "did:jwk:eyJrdHkiOiJPS1AiLCJjcnYiOiJFZDI1NTE5IiwieCI6IjExcVlBWUttNGwtVEFrS1NLZ3Bkb1BkaUJPLWRvNWZWVEw0aFB0N0VocWcifQ"
            val sharedSigningConfig =
                object : RequestObjectSigningConfig {
                    override val enabled: Boolean = true
                    override val audience: String = "https://wallet.example.com"
                    override val expirationSeconds: Long = 60

                    override suspend fun resolveSigningKey() =
                        com.sphereon.crypto.core
                            .KeyInfo<Nothing>(kid = "test-key")

                    override suspend fun resolveSignerBinding(scheme: com.sphereon.openid.oid4vp.common.ClientIdScheme?) =
                        com.sphereon.openid.oid4vp.verifier.requesturi.VerifierSignerBinding.Did(
                            did = testDid,
                            verificationMethodId = "$testDid#0",
                        )
                }

            // Commands / handlers
            val createAuthorizationRequest =
                CreateAuthorizationRequestCommandImpl(
                    execution = execution,
                    authorizationSessionStore = sessionStore,
                    requestObjectSigningConfig = sharedSigningConfig,
                )

            val parseAuthorizationResponse =
                ParseAuthorizationResponseCommandImpl(
                    execution = execution,
                    verifyJarmCommand = MockVerifyJarmResponseCommand(),
                )

            val handleDirectPost =
                HandleDirectPostResponseCommandImpl(
                    execution = execution,
                    parseAuthorizationResponseCommand = parseAuthorizationResponse,
                    validateAuthorizationResponseCommand = MockValidateAuthorizationResponseCommand(),
                    authorizationSessionStore = sessionStore,
                    responseCodeStore = responseCodeStore,
                )

            val retrieve =
                RetrieveAuthorizationResponseCommandImpl(
                    execution = execution,
                    responseCodeStore = responseCodeStore,
                )

            val jarCommand =
                object : CreateSignedJarCommand {
                    override val isEnabled: Boolean = true
                    override val inputTypeToken: TypeToken<CreateSignedJarArgs> = typeToken<CreateSignedJarArgs>()
                    override val outputTypeToken: TypeToken<StringResult> = typeToken<StringResult>()

                    override suspend fun execute(args: CreateSignedJarArgs) = Ok(StringResult("signed.jwt.payload"))
                }
            val verifierGraph = session.graph
            val kms = (verifierGraph as com.sphereon.crypto.core.kms.KeyManagerServiceGraph).keyManagerService
            val requestUriHandler =
                RequestUriHandlerImpl(
                    authorizationSessionStore = sessionStore,
                    createSignedJarCommand = jarCommand,
                    signingConfig = sharedSigningConfig,
                    clock = Clock.System,
                )

            // 1) Create authorization request + store session
            val created =
                createAuthorizationRequest
                    .createAuthorizationRequest(
                        CreateAuthorizationRequestArgs(
                            instanceId = DEFAULT_OID4VP_VERIFIER_INSTANCE_ID,
                            dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:credential")))),
                            clientId = "https://verifier.example.com",
                            responseUri = "https://verifier.example.com/response",
                            responseMode = ResponseMode.DIRECT_POST,
                            nonce = "nonce12345678",
                            state = "state-1234",
                        ),
                    ).getOrThrow()

            assertNotNull(created.sessionId)
            // Signing is enabled with a did:jwk binding, so the HTTPS client_id is substituted
            // with the §5.9.3 decentralized_identifier-prefixed form at request-creation time.
            assertEquals("decentralized_identifier:$testDid", created.request.clientId)

            // 2) request_uri fetch (JAR)
            val jarResponse = requestUriHandler.handleGet("/oid4vp/request-uri/${created.sessionId}").getOrThrow()
            assertEquals("application/oauth-authz-req+jwt", jarResponse.contentType)
            assertEquals("signed.jwt.payload", jarResponse.signedJar)

            // 3) direct_post response to backend -> response_code
            val vpToken = """{"cred":["eyJhbGciOiJFUzI1NiJ9.eyJpYXQiOjE3MDAwMDAwMDB9.sig~eyJhbGciOiJub25lIn0~"]}"""
            val handled =
                handleDirectPost
                    .handleDirectPostResponse(
                        HandleDirectPostResponseArgs(
                            responseParams =
                                mapOf(
                                    "vp_token" to vpToken,
                                    "state" to "state-1234",
                                ),
                            originalRequest = created.request,
                            dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:credential")))),
                            redirectUri = "https://verifier.example.com/callback",
                        ),
                    ).getOrThrow()

            assertNotNull(handled.responseCode)
            assertTrue(handled.redirectUri.contains("response_code="))

            // 4) retrieve by response_code
            val retrieved =
                retrieve
                    .retrieveAuthorizationResponse(
                        RetrieveAuthorizationResponseArgs(
                            responseCode = handled.responseCode,
                            markAsUsed = true,
                        ),
                    ).getOrThrow()

            assertEquals("state-1234", retrieved.state)
            assertEquals(1, retrieved.parsedResponse.vpToken.presentations.size)
        }

    // The previous `direct_post_jwt verifies signed JARM and returns response_code` test was
    // removed. OID4VP 1.0 final §8.3 mandates that `direct_post.jwt` responses MUST be
    // unsigned-encrypted JWTs; the spec-compliant verifier
    // (ParseAuthorizationResponseCommandImpl) now explicitly rejects SIGNED and
    // SIGNED_ENCRYPTED JARM responses for this response mode, so a test asserting the
    // verifier ACCEPTS a signed JARM exercises non-conformant behavior. Generic JARM signed
    // round-trip is still covered by the JARM lib's own command tests; the OID4VP-specific
    // encrypted path is exercised by `direct_post_jwt (encrypted) returns response_code` in
    // [UniversalOid4vpE2ETest].

    private class MockVerifyJarmResponseCommand : VerifyJarmResponseCommand {
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<VerifyJarmResponseArgs> = typeToken<VerifyJarmResponseArgs>()
        override val outputTypeToken: TypeToken<JarmVerificationResult> = typeToken<JarmVerificationResult>()

        override suspend fun execute(args: VerifyJarmResponseArgs): IdkResult<JarmVerificationResult, IdkError> = Err(IdkError.fromString("Mock JARM verification not implemented"))
    }

    private class MockValidateAuthorizationResponseCommand : ValidateAuthorizationResponseCommand {
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<ValidateAuthorizationResponseArgs> = typeToken<ValidateAuthorizationResponseArgs>()
        override val outputTypeToken: TypeToken<ValidationResult> = typeToken<ValidationResult>()

        override suspend fun execute(args: ValidateAuthorizationResponseArgs): IdkResult<ValidationResult, IdkError> =
            Ok(ValidationResult(valid = true, matchedCredentials = emptyList(), errors = emptyList()))
    }

    private class NoConfigKvStoreService : KvStoreService {
        override fun getStoreIds(): Array<String> = emptyArray()

        override fun getStoreConfig(storeId: String): KvStoreConfig = throw IllegalArgumentException("KV store '$storeId' not configured for test")

        override fun getStore(storeId: String): KvStore = throw IllegalArgumentException("KV store '$storeId' not configured for test")
    }

    private class TestKvStoreManager(
        private val store: KvStore,
    ) : KvStoreManager {
        override fun createFromKvStoreConfig(config: KvStoreConfigBase): KvStore = store

        override fun createFromKvStoreConfig(
            config: KvStoreConfigBase,
            execution: SessionExecution?,
        ): KvStore = store

        override fun createFromProperties(
            configService: com.sphereon.core.api.conf.ConfigService,
            execution: SessionExecution?,
        ): Set<KvStore> = setOf(store)
    }

    private class TestKvStore : KvStore {
        override val config: KvStoreConfig = KvStoreConfig(id = "test", scopeBinding = KvStoreScopeBinding.TENANT)

        private data class Stored(
            val bytes: ByteArray,
            val createdAt: Long,
            val expiresAt: Long,
        )

        private val entries = mutableMapOf<String, Stored>()

        private fun k(
            namespace: KvNamespaceId,
            key: String,
        ) = "${namespace.name}:$key"

        override suspend fun <V : Any> put(
            namespace: KvNamespace<V>,
            key: String,
            value: V,
            ttl: Duration,
        ): IdkResult<KvPutResult, IdkError> {
            val now = Clock.System.now().toEpochMilliseconds()
            val expiresAt =
                if (ttl.isInfinite()) {
                    Long.MAX_VALUE
                } else {
                    now + ttl.inWholeMilliseconds
                }
            entries[k(namespace, key)] = Stored(bytes = namespace.codec.encode(value), createdAt = now, expiresAt = expiresAt)
            return Ok(KvPutResult(metadata = KvEntryMetadata(createdAtEpochMillis = now, expiresAtEpochMillis = expiresAt)))
        }

        override suspend fun <V : Any> get(
            namespace: KvNamespace<V>,
            key: String,
        ): IdkResult<V?, IdkError> = getEntry(namespace, key).map { it?.value }

        override suspend fun <V : Any> getEntry(
            namespace: KvNamespace<V>,
            key: String,
        ): IdkResult<KvEntry<V>?, IdkError> {
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
                    metadata = KvEntryMetadata(createdAtEpochMillis = stored.createdAt, expiresAtEpochMillis = stored.expiresAt),
                ),
            )
        }

        override suspend fun delete(
            namespace: KvNamespaceId,
            key: String,
        ): IdkResult<Boolean, IdkError> = Ok(entries.remove(k(namespace, key)) != null)

        override suspend fun exists(
            namespace: KvNamespaceId,
            key: String,
        ): IdkResult<Boolean, IdkError> {
            val now = Clock.System.now().toEpochMilliseconds()
            val stored = entries[k(namespace, key)] ?: return Ok(false)
            if (stored.expiresAt <= now) {
                entries.remove(k(namespace, key))
                return Ok(false)
            }
            return Ok(true)
        }

        override suspend fun touch(
            namespace: KvNamespaceId,
            key: String,
            ttl: Duration,
        ): IdkResult<Boolean, IdkError> {
            val now = Clock.System.now().toEpochMilliseconds()
            val stored = entries[k(namespace, key)] ?: return Ok(false)
            if (stored.expiresAt <= now) {
                entries.remove(k(namespace, key))
                return Ok(false)
            }
            val expiresAt =
                if (ttl.isInfinite()) {
                    Long.MAX_VALUE
                } else {
                    now + ttl.inWholeMilliseconds
                }
            entries[k(namespace, key)] = stored.copy(expiresAt = expiresAt)
            return Ok(true)
        }

        override suspend fun cleanupExpired(namespace: KvNamespaceId?): IdkResult<Int, IdkError> {
            val now = Clock.System.now().toEpochMilliseconds()
            val keys = entries.keys.toList()
            var removed = 0
            for (fullKey in keys) {
                if (namespace != null && !fullKey.startsWith("${namespace.name}:")) {
                    continue
                }
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
interface RpServiceGraph {
    val oid4vpVerifierService: Oid4vpVerifierService
}

@ContributesTo(SessionScope::class)
interface Oauth2JarGraph {
    val createSignedJarCommand: CreateSignedJarCommand
    val jarService: JarService
}

@ContributesTo(SessionScope::class)
interface HolderDepsGraph {
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
    fun `universal oid4vp e2e - request_uri fetch, holder parses and resolves, direct_post returns response_code`() =
        runTest {
            TestRequestObjectSigningConfig.enableDidJwkSigning()
            val testScope = TestScope()
            val app = createUniversalOid4vpTestAppGraph(testScope, appId = "test-verifier-app", profile = "test", version = "1.0.0")

            // Configure a memory-backed software KMS provider via the standard property source mechanism.
            DefaultPrincipalMapPropertySource.addProperties(
                mapOf(
                    "kms.providers.test-software.type" to "software",
                    "kms.providers.test-software.id" to "test-software",
                    "kms.providers.test-software.keystore.type" to "memory",
                    "kms.providers.test-software.keystore.id" to "test-memory-keystore",
                    "kms.providers.test-software.keystore.keyVisibility" to "private",
                    "kms.providers.test-software.keystore.overwriteAlias" to "true",
                ),
            )
            app.userContextManager.destroyAll()

            // Verifier (RP) session
            val verifierContext = app.userContextManager.getAnonymous()
            val verifierSession = verifierContext.sessionContextManager.createOrGetFromId("verifier", principalType = com.sphereon.di.context.PrincipalType.USER)
            val verifierGraph = verifierSession.graph

            val rpService = (verifierGraph as RpServiceGraph).oid4vpVerifierService
            val kms = verifierGraph.asKeyManagerServiceGraph().keyManagerService
            val createSignedJarCommand = (verifierGraph as Oauth2JarGraph).createSignedJarCommand

            // Generate a real signing key (software KMS) for signing request objects (JAR).
            // The alias must match TestRequestObjectSigningConfig.signingKey.kid
            val jarKeyPair =
                kms.generateKeyAsync(
                    providerId = "test-software",
                    alias = "test-request-uri-signing-key",
                    use = JwkUse.sig,
                    keyOperations = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PRIVATE,
                )
            val jarSigningKeyInfo = jarKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            // Build verifier client metadata with embedded JWKS so the holder can verify the signed
            // request object. OID4VP 1.0 final §11.1 narrowed `client_metadata` to the wallet-facing
            // verifier parameters (`jwks`, `jwks_uri`, `vp_formats_supported`,
            // `encrypted_response_enc_values_supported`); OAuth2 RFC 7591 client-registration
            // fields are emitted separately if needed and don't belong inside `client_metadata`.
            val clientMetadata =
                ClientMetadata(
                    jwks = JwkSet(keys = arrayOf(jarKeyPair.jose.publicJwk)),
                )

            // 1) RP creates an authorization request + stores the authorization session (KV-backed).
            val created =
                rpService
                    .createAuthorizationRequest(
                        CreateAuthorizationRequestArgs(
                            instanceId = DEFAULT_OID4VP_VERIFIER_INSTANCE_ID,
                            dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:credential")))),
                            clientId = "https://verifier.example.com",
                            responseUri = "https://verifier.example.com/response",
                            redirectUri = "https://frontend.example.com/callback",
                            responseMode = ResponseMode.DIRECT_POST,
                            nonce = "nonce12345678",
                            state = "state-1234",
                            clientMetadata = clientMetadata,
                        ),
                    ).getOrThrow()
            assertEquals("state-1234", created.request.state)

            val correlationId = created.sessionId ?: error("Expected RP to return a sessionId")
            val requestUri = "https://verifier.example.com${Oid4vpVerifierHttpAdapter.REQUEST_URI_PREFIX}$correlationId"

            // Build the actual OID4VP request URI that the wallet receives (deep link / QR content).
            val requestUriLink =
                rpService
                    .buildAuthorizationRequestUri(
                        BuildAuthorizationRequestUriArgs(
                            request = created.request,
                            useRequestUri = true,
                            requestUri = requestUri,
                        ),
                    ).getOrThrow()
                    .value
            // In request_uri mode, the outer URI intentionally omits client_metadata — wallets
            // fetch it from the JAR per OID4VP §5.10. What MUST be present is client_id + request_uri.
            assertTrue("client_id" in Url(requestUriLink).parameters.names())
            assertTrue("request_uri" in Url(requestUriLink).parameters.names())

            val routeSelector = (app as HttpAdapterRouteSelector.Graph).httpAdapterRouteSelector
            val httpDispatcher = (verifierGraph as HttpAdapterDispatcher.Graph).httpAdapterDispatcher

            val httpClientFactory =
                object : HttpClientFactory {
                    override fun createClient(options: HttpClientOptions): HttpClient {
                        val engine =
                            MockEngine { request ->
                                val url: Url = request.url
                                val path: String = url.encodedPath

                                // 1) request_uri fetch: wallet -> RP
                                if (request.method == HttpMethod.Get && path.startsWith(Oid4vpVerifierHttpAdapter.REQUEST_URI_PREFIX)) {
                                    val genericRequest =
                                        GenericHttpRequest(
                                            method = "GET",
                                            path = path,
                                            headers = request.headers.entries().associate { (k, v) -> k to v.joinToString(",") },
                                        )
                                    val selection = routeSelector.select(genericRequest.method, genericRequest.path)
                                    val route = (selection as? HttpAdapterRouteSelection.Selected)?.match
                                        ?: error("Expected request-uri route, got $selection")
                                    val resp = httpDispatcher.dispatch(genericRequest, route)
                                    return@MockEngine respond(
                                        content = resp.body ?: "",
                                        status = HttpStatusCode.fromValue(resp.statusCode),
                                        headers = headersOf(*resp.headers.map { (k, v) -> k to listOf(v) }.toTypedArray()),
                                    )
                                }

                                // 2) direct_post: wallet -> RP response_uri (simulate the RP HTTP endpoint wiring)
                                if (request.method == HttpMethod.Post && url.toString() == "https://verifier.example.com/response") {
                                    val bodyText =
                                        when (val body = request.body) {
                                            is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
                                            is OutgoingContent.NoContent -> ""
                                            else -> ""
                                        }
                                    val params = parseQueryString(bodyText)
                                    val responseParams: Map<String, String> =
                                        params.names().associateWith { name ->
                                            params.getAll(name)?.firstOrNull().orEmpty()
                                        }

                                    val handled =
                                        rpService
                                            .handleDirectPostResponse(
                                                HandleDirectPostResponseArgs(
                                                    responseParams = responseParams,
                                                    originalRequest = created.request,
                                                    dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:credential")))),
                                                    redirectUri = "https://frontend.example.com/callback",
                                                ),
                                            ).getOrThrow()

                                    return@MockEngine respond(
                                        content = handled.redirectUri,
                                        status = HttpStatusCode.OK,
                                        headers = headersOf("Content-Type" to listOf(ContentType.Text.Plain.toString())),
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
            val holderSession = holderContext.sessionContextManager.createOrGetFromId("holder", principalType = com.sphereon.di.context.PrincipalType.USER)
            val holderExecution = holderSession.asCoreApiServiceGraph().serviceExecution
            val holderGraph = holderSession.graph
            val parseUriQueryCommand = (holderGraph as HolderDepsGraph).parseUriQueryCommand
            val externalIdentifierService = (holderGraph as HolderDepsGraph).externalIdentifierService
            val jarService = (holderGraph as Oauth2JarGraph).jarService
            val resolveAuthorizationRequestCommand = (holderGraph as HolderDepsGraph).resolveAuthorizationRequestCommand

            // Construct holder-side parsing command with an HTTP factory that is intercepted in-process.
            val fetchRequestUriCommand =
                FetchRequestUriCommandImpl(
                    execution = holderExecution,
                    httpClientFactory = httpClientFactory,
                )
            val parseAuthorizationRequestCommand =
                ParseAuthorizationRequestCommandImpl(
                    execution = holderExecution,
                    parseUriQueryCommand = parseUriQueryCommand,
                    fetchRequestUriCommand = fetchRequestUriCommand,
                    jarService = jarService,
                    httpClientFactory = httpClientFactory,
                    externalIdentifierService = externalIdentifierService,
                    jwtService = (holderGraph as JwtServiceImpl.Graph).jwtService,
                    requestTrustMaterialProvider = UnavailableOid4vpRequestTrustMaterialProvider(),
                )

            // 2) Holder fetches request_uri, verifies JAR signature, and parses the Authorization Request.
            val parsedRequest =
                parseAuthorizationRequestCommand.execute(ParseAuthorizationRequestArgs(requestUri = requestUriLink, walletConfig = null)).getOrThrow()

            // 3) Holder resolves the request (DCQL parse + client_id validation etc).
            val resolved = resolveAuthorizationRequestCommand.execute(parsedRequest).getOrThrow()
            assertNotNull(resolved.dcqlQuery)
            // Signing is enabled → CreateAuthorizationRequestCommandImpl substitutes the HTTPS
            // client_id with the §5.9.3 decentralized_identifier-prefixed DID binding.
            assertTrue(resolved.request.clientId.startsWith("decentralized_identifier:did:jwk:"))

            // 4) Holder issues a real SD-JWT VC and creates a presentation with a KB-JWT bound
            // to the auth-request nonce. The verifier's ValidateAuthorizationResponseCommand /
            // VerifyHolderBindingCommand actually parse + verify the SD-JWT now (no laxer
            // legacy fallback), so a real signed VC is required here. The issuer's public key
            // is registered with the shared in-app KMS so the verifier resolves it through the
            // managed-identifier resolver chain.
            val holderKmsForVc = holderGraph.asKeyManagerServiceGraph().keyManagerService
            val sdJwtService = (holderGraph as SdJwtServiceImpl.Graph).sdJwtService
            val issuerKeyPair =
                holderKmsForVc.generateKeyAsync(
                    providerId = "test-software",
                    alias = "issuer-signing-key",
                    use = JwkUse.sig,
                    keyOperations = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PRIVATE,
                )
            val issuerOpts =
                ManagedOptsKeyInfo(
                    identifier = issuerKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE) as ManagedKeyInfoType<*>,
                    context =
                        IdentifierContext(
                            clientId = "test-pid-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://issuer.example.com",
                        ),
                )
            val holderBindingKeyPair =
                holderKmsForVc.generateKeyAsync(
                    providerId = "test-software",
                    alias = "holder-binding-key",
                    use = JwkUse.sig,
                    keyOperations = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            val holderBindingOpts =
                ManagedOptsKeyInfo(
                    identifier = holderBindingKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE) as ManagedKeyInfoType<*>,
                    context =
                        IdentifierContext(
                            clientId = "test-holder",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://holder.example.com",
                        ),
                )
            val cnfValue =
                kotlinx.serialization.json
                    .buildJsonObject {
                        put(
                            "jwk",
                            holderBindingKeyPair.jose.publicJwk
                                .toMinimalJwk()
                                .toJsonObject()
                        )
                    }
            val sdJwtVc =
                sdJwtService
                    .issueSdJwt(
                        IssueSdJwtArgs(
                            issuer = issuerOpts,
                            payload =
                                sdJwtPayload {
                                    iss("https://issuer.example.com")
                                    claim("vct", "https://example.com/PersonIdentificationData")
                                    claimSd("given_name", "Alice")
                                    claim("cnf", cnfValue)
                                },
                        ),
                    ).getOrThrow()
                    .sdJwt
            val vpPresentation =
                sdJwtService
                    .presentSdJwt(
                        PresentSdJwtArgs(
                            sdJwt = sdJwtVc,
                            disclosureSelection = SdMap(mapOf("given_name" to SdField(sd = true))),
                            holderKey = holderBindingOpts,
                            audience = resolved.request.clientId,
                            nonce = created.request.nonce ?: error("auth request must have a nonce"),
                        ),
                    ).getOrThrow()
                    .presentation

            val vpToken = """{"cred":["$vpPresentation"]}"""
            val responseUri = parsedRequest.responseUri ?: error("Expected response_uri for direct_post")

            val walletClient = httpClientFactory.createClient(HttpClientOptions.createDefault().copy(enableLogging = false))
            val redirectUri =
                walletClient
                    .post(responseUri) {
                        contentType(ContentType.Application.FormUrlEncoded)
                        setBody(
                            Parameters
                                .build {
                                    append("vp_token", vpToken)
                                    append("state", "state-1234")
                                }.formUrlEncode(),
                        )
                    }.bodyAsText()
            walletClient.close()

            val responseCode = Url(redirectUri).parameters["response_code"]
            assertNotNull(responseCode)

            // 5) RP retrieves the response by response_code (KV-backed, single-use).
            val retrieved =
                rpService
                    .retrieveAuthorizationResponse(
                        RetrieveAuthorizationResponseArgs(
                            responseCode = responseCode,
                            markAsUsed = true,
                        ),
                    ).getOrThrow()

            assertEquals("state-1234", retrieved.state)
            assertEquals(1, retrieved.parsedResponse.vpToken.presentations.size)

            // Optional: verify the RP session status has advanced as expected.
            val sessionAfter = rpService.authorizationSessionStore.getByCorrelationId(correlationId).getOrThrow()
            assertNotNull(sessionAfter)
            assertTrue(
                sessionAfter.status.name == com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_RECEIVED.name ||
                    sessionAfter.status.name == com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_VERIFIED.name,
            )
        }

    @Test
    fun `universal oid4vp e2e - request_uri fetch, holder resolves, direct_post_jwt (encrypted) returns response_code`() =
        runTest {
            TestRequestObjectSigningConfig.enableDidJwkSigning()
            val testScope = TestScope()
            val app = createUniversalOid4vpTestAppGraph(testScope, appId = "test-verifier-app", profile = "test", version = "1.0.0")

            DefaultPrincipalMapPropertySource.addProperties(
                mapOf(
                    "kms.providers.test-software.type" to "software",
                    "kms.providers.test-software.id" to "test-software",
                    "kms.providers.test-software.keystore.type" to "memory",
                    "kms.providers.test-software.keystore.id" to "test-memory-keystore",
                    "kms.providers.test-software.keystore.keyVisibility" to "private",
                    "kms.providers.test-software.keystore.overwriteAlias" to "true",
                ),
            )
            app.userContextManager.destroyAll()

            // Verifier (RP) session
            val verifierContext = app.userContextManager.getAnonymous()
            val verifierSession = verifierContext.sessionContextManager.createOrGetFromId("verifier-jarm", principalType = com.sphereon.di.context.PrincipalType.USER)
            val verifierGraph = verifierSession.graph

            val rpService = (verifierGraph as RpServiceGraph).oid4vpVerifierService
            val kms = verifierGraph.asKeyManagerServiceGraph().keyManagerService
            val createSignedJarCommand = (verifierGraph as Oauth2JarGraph).createSignedJarCommand

            // Signing key for request objects (JAR)
            // The alias must match TestRequestObjectSigningConfig.signingKey.kid
            val jarKeyPair =
                kms.generateKeyAsync(
                    providerId = "test-software",
                    alias = "test-request-uri-signing-key",
                    use = JwkUse.sig,
                    keyOperations = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PRIVATE,
                )
            val jarSigningKeyInfo = jarKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            // Encryption key for JARM responses (wallet encrypts to verifier).
            val jarmEncKeyPair =
                kms.generateKeyAsync(
                    providerId = "test-software",
                    alias = "verifier-jarm-enc",
                    use = JwkUse.enc,
                    keyOperations = arrayOf(KeyOperations.ENCRYPT, KeyOperations.DECRYPT),
                    alg = SignatureAlgorithm.RSA_SHA256,
                    keyVisibility = KeyVisibility.PRIVATE,
                )
            val jarmDecryptionKey = ManagedOptsKeyInfo(identifier = jarmEncKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE))
            val jwksUri = "https://verifier.example.com/jwks"

            // Client metadata for OID4VP 1.0 final §8.3 encrypted authorization response.
            // This test specifically exercises the `jwks_uri` resolution path: `jwks` carries
            // ONLY the JAR sig key (so the wallet can verify the signed request object) while
            // the JARM encryption key is served via `jwks_uri` and the wallet's
            // resolveEncryptionRecipient must fetch it over HTTP. The `jwksFetchCount`
            // assertion below pins that behavior. The JWE `alg` is taken from the chosen JWK's
            // `alg` field per OID4VP §8.3 — there is no top-level
            // `authorization_encrypted_response_alg` in the spec.
            val clientMetadata =
                ClientMetadata(
                    jwks = JwkSet(keys = arrayOf(jarKeyPair.jose.publicJwk)),
                    jwksUri = jwksUri,
                    encryptedResponseEncValuesSupported = listOf("A256GCM"),
                )

            val created =
                rpService
                    .createAuthorizationRequest(
                        CreateAuthorizationRequestArgs(
                            instanceId = DEFAULT_OID4VP_VERIFIER_INSTANCE_ID,
                            dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:credential")))),
                            clientId = "https://verifier.example.com",
                            responseUri = "https://verifier.example.com/response",
                            redirectUri = "https://frontend.example.com/callback",
                            responseMode = ResponseMode.DIRECT_POST_JWT,
                            nonce = "nonce12345678",
                            state = "state-1234",
                            clientMetadata = clientMetadata,
                        ),
                    ).getOrThrow()
            val correlationId = created.sessionId ?: error("Expected RP to return a sessionId")

            val requestUri = "https://verifier.example.com${Oid4vpVerifierHttpAdapter.REQUEST_URI_PREFIX}$correlationId"

            val requestUriLink =
                rpService
                    .buildAuthorizationRequestUri(
                        BuildAuthorizationRequestUriArgs(
                            request = created.request,
                            useRequestUri = true,
                            requestUri = requestUri,
                        ),
                    ).getOrThrow()
                    .value

            val routeSelector = (app as HttpAdapterRouteSelector.Graph).httpAdapterRouteSelector
            val httpDispatcher = (verifierGraph as HttpAdapterDispatcher.Graph).httpAdapterDispatcher

            // The JWK published at jwks_uri carries `alg = RSA-OAEP` so the wallet's
            // deriveJarmConfigFromClientMetadata picks the JWE key encryption algorithm from
            // the JWK itself, per OID4VP §8.3 (no top-level `authorization_encrypted_response_alg`
            // field in the spec). The KMS-generated public JWK carries the signing-side `alg`
            // (`RS256`) by default, so override it for the wire form.
            val jwksJson =
                Json.encodeToString(
                    JwkSet.serializer(),
                    JwkSet(keys = arrayOf(jarmEncKeyPair.jose.publicJwk.copy(alg = JwaAlgorithm.RSA_OAEP))),
                )
            var jwksFetchCount = 0

            // Intercept HTTP calls in-process, while still exercising the RP command pipeline.
            val httpClientFactory =
                object : HttpClientFactory {
                    override fun createClient(options: HttpClientOptions): HttpClient {
                        val engine =
                            MockEngine { request ->
                                val url: Url = request.url
                                val path: String = url.encodedPath

                                if (request.method == HttpMethod.Get && url.toString() == jwksUri) {
                                    jwksFetchCount++
                                    return@MockEngine respond(
                                        content = jwksJson,
                                        status = HttpStatusCode.OK,
                                        headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString())),
                                    )
                                }

                                // request_uri fetch: wallet -> RP
                                if (request.method == HttpMethod.Get && path.startsWith(Oid4vpVerifierHttpAdapter.REQUEST_URI_PREFIX)) {
                                    val genericRequest =
                                        GenericHttpRequest(
                                            method = "GET",
                                            path = path,
                                            headers = request.headers.entries().associate { (k, v) -> k to v.joinToString(",") },
                                        )
                                    val selection = routeSelector.select(genericRequest.method, genericRequest.path)
                                    val route = (selection as? HttpAdapterRouteSelection.Selected)?.match
                                        ?: error("Expected request-uri route, got $selection")
                                    val resp = httpDispatcher.dispatch(genericRequest, route)
                                    return@MockEngine respond(
                                        content = resp.body ?: "",
                                        status = HttpStatusCode.fromValue(resp.statusCode),
                                        headers = headersOf(*resp.headers.map { (k, v) -> k to listOf(v) }.toTypedArray()),
                                    )
                                }

                                // direct_post(.jwt): wallet -> RP response_uri
                                if (request.method == HttpMethod.Post && url.toString() == "https://verifier.example.com/response") {
                                    val bodyText =
                                        when (val body = request.body) {
                                            is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
                                            is OutgoingContent.NoContent -> ""
                                            else -> ""
                                        }
                                    val params = parseQueryString(bodyText)
                                    val responseParams: Map<String, String> =
                                        params.names().associateWith { name ->
                                            params.getAll(name)?.firstOrNull().orEmpty()
                                        }

                                    val handled =
                                        rpService
                                            .handleDirectPostResponse(
                                                HandleDirectPostResponseArgs(
                                                    responseParams = responseParams,
                                                    originalRequest = created.request,
                                                    dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:credential")))),
                                                    redirectUri = "https://frontend.example.com/callback",
                                                    jarmExpectedAudience = created.request.clientId,
                                                    jarmDecryptionKey = jarmDecryptionKey,
                                                ),
                                            ).getOrThrow()

                                    val responseBody = """{"redirect_uri":"${handled.redirectUri}","response_code":"${handled.responseCode}"}"""
                                    return@MockEngine respond(
                                        content = responseBody,
                                        status = HttpStatusCode.OK,
                                        headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString())),
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
            val holderSession = holderContext.sessionContextManager.createOrGetFromId("holder-jarm", principalType = com.sphereon.di.context.PrincipalType.USER)
            val holderExecution = holderSession.asCoreApiServiceGraph().serviceExecution
            val holderGraph = holderSession.graph

            val parseUriQueryCommand = (holderGraph as HolderDepsGraph).parseUriQueryCommand
            // IMPORTANT: For this E2E, we intercept HTTP with a Ktor MockEngine. The DI-provided
            // external identifier resolution service uses the platform HttpClientFactory (OKHTTP on JVM)
            // and would attempt a real network call. We explicitly wire a JWKS URL resolver that uses the
            // intercepted HttpClientFactory instead, to keep the flow end-to-end while staying in-process.
            val didExternalIdentifierService =
                (holderGraph as DidExternalIdentifierResolutionServiceImpl.Graph).didExternalIdentifierResolutionService
            val externalIdentifierService: MultiExternalIdentifierService =
                MultiExternalIdentifierResolutionServiceImpl(
                    execution = holderExecution,
                    external =
                        setOf<ExternalIdentifierService>(
                            JwksUrlExternalIdentifierResolutionServiceImpl(
                                execution = holderExecution,
                                httpClientFactory = httpClientFactory,
                            ),
                            didExternalIdentifierService,
                        ),
                )
            val jarService = (holderGraph as Oauth2JarGraph).jarService
            val resolveAuthorizationRequestCommand = (holderGraph as HolderDepsGraph).resolveAuthorizationRequestCommand

            val fetchRequestUriCommand =
                FetchRequestUriCommandImpl(
                    execution = holderExecution,
                    httpClientFactory = httpClientFactory,
                )
            val parseAuthorizationRequestCommand =
                ParseAuthorizationRequestCommandImpl(
                    execution = holderExecution,
                    parseUriQueryCommand = parseUriQueryCommand,
                    fetchRequestUriCommand = fetchRequestUriCommand,
                    jarService = jarService,
                    httpClientFactory = httpClientFactory,
                    externalIdentifierService = externalIdentifierService,
                    jwtService = (holderGraph as JwtServiceImpl.Graph).jwtService,
                    requestTrustMaterialProvider = UnavailableOid4vpRequestTrustMaterialProvider(),
                )

            val parsedRequest =
                parseAuthorizationRequestCommand.execute(ParseAuthorizationRequestArgs(requestUri = requestUriLink, walletConfig = null)).getOrThrow()
            val resolved = resolveAuthorizationRequestCommand.execute(parsedRequest).getOrThrow()

            val submitAuthorizationResponseCommand =
                SubmitAuthorizationResponseCommandImpl(
                    execution = holderExecution,
                    httpClientFactory = httpClientFactory,
                    externalIdentifierService = externalIdentifierService,
                    createJarmCommand =
                        CreateJarmResponseCommandImpl(
                            execution = holderExecution,
                            jwtService = (holderGraph as JwtServiceImpl.Graph).jwtService,
                            jweService = (holderGraph as JweServiceImpl.Graph).jweService,
                        ),
                )

            // Issue a real SD-JWT VC + KB-JWT presentation. The verifier now actually
            // parses + verifies the SD-JWT (no laxer legacy fallback) so a hand-rolled
            // synthetic token would be rejected at the parse step.
            val holderKmsForVc = holderGraph.asKeyManagerServiceGraph().keyManagerService
            val sdJwtService = (holderGraph as SdJwtServiceImpl.Graph).sdJwtService
            val vcIssuerKeyPair =
                holderKmsForVc.generateKeyAsync(
                    providerId = "test-software",
                    alias = "vc-issuer-signing-key",
                    use = JwkUse.sig,
                    keyOperations = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PRIVATE,
                )
            val vcIssuerOpts =
                ManagedOptsKeyInfo(
                    identifier = vcIssuerKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE) as ManagedKeyInfoType<*>,
                    context =
                        IdentifierContext(
                            clientId = "test-pid-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://issuer.example.com",
                        ),
                )
            val holderBindingKp =
                holderKmsForVc.generateKeyAsync(
                    providerId = "test-software",
                    alias = "vc-holder-binding-key",
                    use = JwkUse.sig,
                    keyOperations = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            val holderBindingOpts =
                ManagedOptsKeyInfo(
                    identifier = holderBindingKp.joseToManagedKeyInfo(KeyVisibility.PRIVATE) as ManagedKeyInfoType<*>,
                    context =
                        IdentifierContext(
                            clientId = "test-holder",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://holder.example.com",
                        ),
                )
            val cnfValue =
                kotlinx.serialization.json
                    .buildJsonObject {
                        put(
                            "jwk",
                            holderBindingKp.jose.publicJwk
                                .toMinimalJwk()
                                .toJsonObject()
                        )
                    }
            val sdJwtVc =
                sdJwtService
                    .issueSdJwt(
                        IssueSdJwtArgs(
                            issuer = vcIssuerOpts,
                            payload =
                                sdJwtPayload {
                                    iss("https://issuer.example.com")
                                    claim("vct", "https://example.com/PersonIdentificationData")
                                    claimSd("given_name", "Alice")
                                    claim("cnf", cnfValue)
                                },
                        ),
                    ).getOrThrow()
                    .sdJwt
            val vpPresentation =
                sdJwtService
                    .presentSdJwt(
                        PresentSdJwtArgs(
                            sdJwt = sdJwtVc,
                            disclosureSelection = SdMap(mapOf("given_name" to SdField(sd = true))),
                            holderKey = holderBindingOpts,
                            audience = resolved.request.clientId,
                            nonce = created.request.nonce ?: error("auth request must have a nonce"),
                        ),
                    ).getOrThrow()
                    .presentation

            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken("cred", vpPresentation)
                    state("state-1234")
                }

            val submission =
                submitAuthorizationResponseCommand
                    .execute(
                        SubmitAuthorizationResponseArgs(
                            resolvedRequest = resolved,
                            response = response,
                            responseMode = ResponseMode.DIRECT_POST_JWT,
                            // Wallet's deriveJarmConfigFromClientMetadata only inspects the
                            // embedded `jwks` for an enc key — it does not pre-fetch jwks_uri.
                            // This test deliberately ships the enc key only via `jwks_uri`
                            // (to exercise the resolver fetch path), so we pass an explicit
                            // JarmConfig here. Recipient resolution still runs and pulls the
                            // RSA-OAEP key from `jwks_uri`, which `jwksFetchCount = 1` pins.
                            jarmOptions =
                                JarmOptions(
                                    issuer = "https://wallet.example.com",
                                    jarmConfig =
                                        com.sphereon.oauth2.common.jarm.JarmConfig
                                            .encrypted(
                                                keyEncryptionAlg = "RSA-OAEP",
                                                contentEncryptionAlg = "A256GCM",
                                            ),
                                ),
                        ),
                    ).getOrThrow()

            val redirectUri =
                (submission as SubmissionResult.Success).redirectUri
                    ?: error("Expected redirectUri from RP backend")
            val responseCode = Url(redirectUri).parameters["response_code"]
            assertNotNull(responseCode)
            assertEquals(1, jwksFetchCount)

            val retrieved =
                rpService
                    .retrieveAuthorizationResponse(
                        RetrieveAuthorizationResponseArgs(
                            responseCode = responseCode,
                            markAsUsed = true,
                        ),
                    ).getOrThrow()
            assertEquals("state-1234", retrieved.state)
            assertEquals(1, retrieved.parsedResponse.vpToken.presentations.size)
        }

    // The previous `direct_post_jwt (signed) returns response_code` E2E test was removed.
    // OID4VP 1.0 final §8.3 mandates that `direct_post.jwt` responses MUST be unsigned-encrypted
    // JWTs; the verifier (ParseAuthorizationResponseCommandImpl) now rejects SIGNED JARM
    // responses for this response mode. The signed JARM round-trip is still covered by the
    // generic JARM lib tests; the OID4VP-specific encrypted path is the test above.
}
