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
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KvEntry
import com.sphereon.data.store.kv.KvEntryMetadata
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvNamespaceId
import com.sphereon.data.store.kv.KvPutResult
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.data.store.kv.impl.KvStoreService
import com.sphereon.oauth2.common.jarm.JarmVerificationResult
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseArgs
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseCommand
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolutionPolicy
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.sdJwtVcMeta
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.sphereon.openid.oid4vp.verifier.HandleDirectPostResponseArgs
import com.sphereon.openid.oid4vp.verifier.RetrieveAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.TrustedAuthenticationResolution
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import com.sphereon.openid.oid4vp.verifier.impl.testutil.Oid4vpVerifierTestContext
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import com.sphereon.openid.oid4vp.verifier.store.ResponseCodeError
import com.sphereon.openid.oid4vp.verifier.store.ResponseCodeStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Unit tests for Response Code Protection mechanism per OpenID4VP 1.0 Section 14.3.3.
 *
 * Tests cover:
 * - HandleDirectPostResponseCommand: Generate response_code and redirect_uri
 * - RetrieveAuthorizationResponseCommand: Retrieve response by response_code
 * - ResponseCodeStore (KV-backed): Storage operations (store, retrieve, expire, single-use)
 */
class ResponseCodeProtectionTest {
    private data class TestFixture(
        val store: ResponseCodeStore,
        val parseCommand: ParseAuthorizationResponseCommandImpl,
        val handleCommand: HandleDirectPostResponseCommandImpl,
        val retrieveCommand: RetrieveAuthorizationResponseCommandImpl,
        val authorizationSessionStore: TestAuthorizationSessionStore,
        val validateCommand: MockValidateAuthorizationResponseCommand,
        val clock: MutableClock,
    )

    private fun createFixture(): TestFixture {
        val testContext = Oid4vpVerifierTestContext("response-code-test", this)
        val execution = testContext.execution
        val clock = MutableClock(startEpochMillis = 1_000_000L)
        val kvStoreManager = TestKvStoreManager(TestKvStore(clock = clock))
        val store =
            KvResponseCodeStore(
                kvStoreManager = kvStoreManager,
                kvStoreService = NoConfigKvStoreService(),
                execution = execution,
                clock = clock,
            )
        val parseCommand =
            ParseAuthorizationResponseCommandImpl(
                execution = execution,
                verifyJarmCommand = MockVerifyJarmResponseCommand(),
            )
        val authorizationSessionStore = TestAuthorizationSessionStore()
        val validateCommand = MockValidateAuthorizationResponseCommand()
        val handleCommand =
            HandleDirectPostResponseCommandImpl(
                execution = execution,
                parseAuthorizationResponseCommand = parseCommand,
                validateAuthorizationResponseCommand = validateCommand,
                authorizationSessionStore = authorizationSessionStore,
                responseCodeStore = store,
            )
        val retrieveCommand =
            RetrieveAuthorizationResponseCommandImpl(
                execution = execution,
                responseCodeStore = store,
            )
        return TestFixture(store, parseCommand, handleCommand, retrieveCommand, authorizationSessionStore, validateCommand, clock)
    }

    private suspend fun TestFixture.persistAuthorizationSession(
        args: HandleDirectPostResponseArgs,
        instanceId: String,
    ) {
        val correlationState = requireNotNull(args.originalRequest.state) { "Test authorization request must have correlation state" }
        val now = clock.now().toEpochMilliseconds()
        val session =
            AuthorizationSession(
                instanceId = instanceId,
                sessionId = "response-code-session-$correlationState",
                correlationId = correlationState,
                dcqlQuery = args.dcqlQuery,
                authorizationRequest = args.originalRequest,
                status = AuthorizationSessionStatus.AUTHORIZATION_REQUEST_CREATED,
                createdAt = now,
                updatedAt = now,
                expiresAt = now + 600_000,
            )

        assertIs<Ok<*>>(authorizationSessionStore.put(correlationState, session, ttlSeconds = 600))
        val persistedSession = authorizationSessionStore.get(correlationState)
        assertIs<Ok<*>>(persistedSession)
        assertEquals(instanceId, persistedSession.value?.instanceId)
    }

    // =========================================================================
    // HandleDirectPostResponseCommand Tests
    // =========================================================================

    @Test
    fun `test handle direct_post generates response_code and redirect_uri`() =
        runTest {
            val f = createFixture()
            // Given: A valid direct_post response
            val vpToken = """{"driver_license":["eyJhbGciOiJFUzI1NiJ9.payload.sig"]}"""
            val originalRequest =
                AuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                    state = "test-state",
                )
            val testDcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:credential"))))
            val args =
                HandleDirectPostResponseArgs(
                    responseParams =
                        mapOf(
                            "vp_token" to vpToken,
                            "state" to "test-state",
                        ),
                    originalRequest = originalRequest,
                    dcqlQuery = testDcqlQuery,
                    redirectUri = "https://verifier.example.com/callback",
                )
            f.persistAuthorizationSession(args, instanceId = "verifier-instance-response-code-generation")

            // When: Handling the direct_post response
            val result = f.handleCommand.handleDirectPostResponse(args)

            // Then: Should generate response_code and return redirect_uri
            val response = result.getOrThrow()

            assertNotNull(response.responseCode)
            assertTrue(response.responseCode.isNotEmpty())
            assertTrue(response.redirectUri.contains("response_code="))
            assertTrue(
                response.redirectUri.startsWith("https://verifier.example.com/callback?response_code="),
                "Expected query param response_code per OID4VP spec, got: ${response.redirectUri}",
            )
            assertTrue(response.expiresAt > f.clock.now().toEpochMilliseconds())
        }

    @Test
    fun `direct_post forwards verifier admitted authentication and Data Integrity policy`() =
        runTest {
            val f = createFixture()
            val originalRequest =
                AuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                    state = "trusted-resolution-state",
                )
            val trustedAuthentications =
                listOf(
                    TrustedAuthenticationResolution(
                        controller = "https://holder.example.com",
                        trustedJwks =
                            JsonObject(
                                mapOf(
                                    "keys" to
                                        JsonArray(
                                            listOf(
                                                JsonObject(
                                                    mapOf(
                                                        "kty" to JsonPrimitive("EC"),
                                                        "crv" to JsonPrimitive("P-256"),
                                                        "x" to JsonPrimitive("WbbFpp0eS8_rJlvpuX_qEyU1J2PNmXYnqPCBJTqqiBA"),
                                                        "y" to JsonPrimitive("F8kbfVPRQc5M9kJA1fy3c_0Q6vCqHy1X7CZQC6XQy9I"),
                                                        "kid" to JsonPrimitive("holder-key"),
                                                    ),
                                                ),
                                            ),
                                        ),
                                ),
                            ),
                    ),
                )
            val resolutionPolicy = VerificationMethodResolutionPolicy.empty()
            val args =
                HandleDirectPostResponseArgs(
                    responseParams =
                        mapOf(
                            "vp_token" to """{"query":["eyJhbGciOiJFUzI1NiJ9.payload.sig"]}""",
                            "state" to "trusted-resolution-state",
                        ),
                    originalRequest = originalRequest,
                    dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:credential")))),
                    redirectUri = "https://verifier.example.com/callback",
                    trustedAuthentications = trustedAuthentications,
                    verificationMethodResolutionPolicy = resolutionPolicy,
                )
            f.persistAuthorizationSession(args, instanceId = "verifier-instance-trusted-resolution")

            assertIs<Ok<*>>(f.handleCommand.handleDirectPostResponse(args))

            val captured = assertNotNull(f.validateCommand.lastArgs)
            assertSame(trustedAuthentications, captured.trustedAuthentications)
            assertSame(resolutionPolicy, captured.verificationMethodResolutionPolicy)
        }

    @Test
    fun `test handle direct_post appends response_code to existing query params`() =
        runTest {
            val f = createFixture()
            val vpToken = """{"query":["eyJhbGciOiJFUzI1NiJ9.payload.sig"]}"""
            val originalRequest =
                AuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback?existing=param",
                    state = "response-code-existing-query-state",
                )
            val args =
                HandleDirectPostResponseArgs(
                    responseParams = mapOf("vp_token" to vpToken, "state" to "response-code-existing-query-state"),
                    originalRequest = originalRequest,
                    dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:credential")))),
                    redirectUri = "https://verifier.example.com/callback?existing=param",
                )
            f.persistAuthorizationSession(args, instanceId = "verifier-instance-response-code-existing-query")

            val result = f.handleCommand.handleDirectPostResponse(args)

            val response = result.getOrThrow()
            assertTrue(response.redirectUri.contains("existing=param"))
            assertTrue(
                response.redirectUri.contains("&response_code="),
                "Expected query param response_code appended to existing params, got: ${response.redirectUri}",
            )
        }

    @Test
    fun `test handle direct_post fails with invalid vp_token`() =
        runTest {
            val f = createFixture()
            val args =
                HandleDirectPostResponseArgs(
                    responseParams = mapOf("state" to "response-code-invalid-vp-token-state"), // Missing vp_token
                    originalRequest =
                        AuthorizationRequest(
                            clientId = "https://verifier.example.com",
                            redirectUri = "https://verifier.example.com/callback",
                            state = "response-code-invalid-vp-token-state",
                        ),
                    dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:credential")))),
                    redirectUri = "https://verifier.example.com/callback",
                )
            f.persistAuthorizationSession(args, instanceId = "verifier-instance-response-code-invalid-vp-token")

            val result = f.handleCommand.handleDirectPostResponse(args)

            assertIs<Err<*>>(result)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("vp_token"),
            )
        }

    // =========================================================================
    // RetrieveAuthorizationResponseCommand Tests
    // =========================================================================

    @Test
    fun `test retrieve response by response_code`() =
        runTest {
            val f = createFixture()
            // Given: A stored response
            val vpToken = """{"license":["eyJhbGciOiJFUzI1NiJ9.payload.sig"]}"""
            val handleArgs =
                HandleDirectPostResponseArgs(
                    responseParams =
                        mapOf(
                            "vp_token" to vpToken,
                            "state" to "retrieve-test",
                        ),
                    originalRequest =
                        AuthorizationRequest(
                            clientId = "https://verifier.example.com",
                            redirectUri = "https://verifier.example.com/callback",
                            state = "retrieve-test",
                        ),
                    dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:credential")))),
                    redirectUri = "https://verifier.example.com/callback",
                )
            f.persistAuthorizationSession(handleArgs, instanceId = "verifier-instance-response-code-retrieval")
            val handleResult = f.handleCommand.handleDirectPostResponse(handleArgs)
            val responseCode = handleResult.getOrThrow().responseCode

            // When: Retrieving by response_code
            val retrieveArgs =
                RetrieveAuthorizationResponseArgs(
                    responseCode = responseCode,
                    markAsUsed = true,
                )
            val retrieveResult = f.retrieveCommand.retrieveAuthorizationResponse(retrieveArgs)

            // Then: Should return the stored response
            val retrieved = retrieveResult.getOrThrow()

            assertEquals("retrieve-test", retrieved.state)
            assertNotNull(retrieved.parsedResponse)
            assertEquals(1, retrieved.parsedResponse.vpToken.presentations.size)
        }

    @Test
    fun `test response_code is single-use`() =
        runTest {
            val f = createFixture()
            // Given: A stored response
            val vpToken = """{"query":["eyJhbGciOiJFUzI1NiJ9.payload.sig"]}"""
            val handleArgs =
                HandleDirectPostResponseArgs(
                    responseParams = mapOf("vp_token" to vpToken, "state" to "response-code-single-use-state"),
                    originalRequest =
                        AuthorizationRequest(
                            clientId = "https://verifier.example.com",
                            redirectUri = "https://verifier.example.com/callback",
                            state = "response-code-single-use-state",
                        ),
                    dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:credential")))),
                    redirectUri = "https://verifier.example.com/callback",
                )
            f.persistAuthorizationSession(handleArgs, instanceId = "verifier-instance-response-code-single-use")
            val handleResult = f.handleCommand.handleDirectPostResponse(handleArgs)
            val responseCode = handleResult.getOrThrow().responseCode

            // When: First retrieval (marks as used)
            val firstResult =
                f.retrieveCommand.retrieveAuthorizationResponse(
                    RetrieveAuthorizationResponseArgs(responseCode = responseCode, markAsUsed = true),
                )
            firstResult.getOrThrow()

            // Then: Second retrieval should fail
            val secondResult =
                f.retrieveCommand.retrieveAuthorizationResponse(
                    RetrieveAuthorizationResponseArgs(responseCode = responseCode),
                )
            assertIs<Err<*>>(secondResult)
            assertEquals(ResponseCodeError.USED_RESPONSE_CODE, secondResult.error.code)
        }

    @Test
    fun `test retrieve without marking as used allows multiple retrievals`() =
        runTest {
            val f = createFixture()
            // Given: A stored response
            val vpToken = """{"query":["eyJhbGciOiJFUzI1NiJ9.payload.sig"]}"""
            val handleArgs =
                HandleDirectPostResponseArgs(
                    responseParams = mapOf("vp_token" to vpToken, "state" to "response-code-reusable-state"),
                    originalRequest =
                        AuthorizationRequest(
                            clientId = "https://verifier.example.com",
                            redirectUri = "https://verifier.example.com/callback",
                            state = "response-code-reusable-state",
                        ),
                    dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "cred", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:credential")))),
                    redirectUri = "https://verifier.example.com/callback",
                )
            f.persistAuthorizationSession(handleArgs, instanceId = "verifier-instance-response-code-reusable")
            val handleResult = f.handleCommand.handleDirectPostResponse(handleArgs)
            val responseCode = handleResult.getOrThrow().responseCode

            // When: Retrieving without marking as used
            val firstResult =
                f.retrieveCommand.retrieveAuthorizationResponse(
                    RetrieveAuthorizationResponseArgs(responseCode = responseCode, markAsUsed = false),
                )
            firstResult.getOrThrow()

            // Then: Second retrieval should also succeed
            val secondResult =
                f.retrieveCommand.retrieveAuthorizationResponse(
                    RetrieveAuthorizationResponseArgs(responseCode = responseCode, markAsUsed = false),
                )
            secondResult.getOrThrow()
        }

    @Test
    fun `test retrieve with invalid response_code fails`() =
        runTest {
            val f = createFixture()
            val result =
                f.retrieveCommand.retrieveAuthorizationResponse(
                    RetrieveAuthorizationResponseArgs(responseCode = "invalid-code"),
                )

            assertIs<Err<*>>(result)
            assertEquals(ResponseCodeError.INVALID_RESPONSE_CODE, result.error.code)
        }

    // =========================================================================
    // ResponseCodeStore Tests
    // =========================================================================

    @Test
    fun `test store generates unique response codes`() =
        runTest {
            val f = createFixture()
            val vpToken = """{"query":["eyJhbGciOiJFUzI1NiJ9.payload.sig"]}"""
            val parsedResponse =
                f.parseCommand
                    .parseAuthorizationResponse(
                        com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseArgs(
                            responseParams = mapOf("vp_token" to vpToken),
                        ),
                    ).getOrThrow()

            // Store multiple responses
            val codes =
                (1..10).map {
                    f.store
                        .createResponseCode(parsedResponse)
                        .getOrThrow()
                        .responseCode
                }

            // All codes should be unique
            assertEquals(10, codes.toSet().size)
        }

    @Test
    fun `test store respects TTL`() =
        runTest {
            val f = createFixture()
            val vpToken = """{"query":["eyJhbGciOiJFUzI1NiJ9.payload.sig"]}"""
            val parsedResponse =
                f.parseCommand
                    .parseAuthorizationResponse(
                        com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseArgs(
                            responseParams = mapOf("vp_token" to vpToken),
                        ),
                    ).getOrThrow()

            // Store with 1 second TTL
            val result = f.store.createResponseCode(parsedResponse, ttlSeconds = 1)
            val responseCode = result.getOrThrow().responseCode

            // Should be valid initially
            assertTrue(f.store.isValid(responseCode).getOrThrow())

            // Advance the injected clock past the TTL (no real delays).
            f.clock.advance(millis = 1100)

            // Should be invalid after expiration
            assertFalse(f.store.isValid(responseCode).getOrThrow())

            // Retrieve should fail
            val retrieveResult = f.store.getAndConsume(responseCode)
            assertIs<Err<*>>(retrieveResult)
            // Depending on the KV backend behavior, expired entries may be removed on read.
            // In that case, the store cannot distinguish "expired" from "not found" and returns INVALID.
            assertTrue(
                retrieveResult.error.code == ResponseCodeError.EXPIRED_RESPONSE_CODE ||
                    retrieveResult.error.code == ResponseCodeError.INVALID_RESPONSE_CODE,
            )
        }

    @Test
    fun `test cleanup removes expired entries`() =
        runTest {
            val f = createFixture()
            val vpToken = """{"query":["eyJhbGciOiJFUzI1NiJ9.payload.sig"]}"""
            val parsedResponse =
                f.parseCommand
                    .parseAuthorizationResponse(
                        com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseArgs(
                            responseParams = mapOf("vp_token" to vpToken),
                        ),
                    ).getOrThrow()

            // Store with very short TTL
            f.store.createResponseCode(parsedResponse, ttlSeconds = 1)
            f.store.createResponseCode(parsedResponse, ttlSeconds = 1)
            f.store.createResponseCode(parsedResponse, ttlSeconds = 1)

            // Advance the injected clock past the TTL (no real delays).
            f.clock.advance(millis = 1100)

            // Cleanup should remove expired entries
            val removed = f.store.cleanupExpired().getOrThrow()
            assertEquals(3, removed)
        }

    @Test
    fun `test delete removes entry`() =
        runTest {
            val f = createFixture()
            val vpToken = """{"query":["eyJhbGciOiJFUzI1NiJ9.payload.sig"]}"""
            val parsedResponse =
                f.parseCommand
                    .parseAuthorizationResponse(
                        com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseArgs(
                            responseParams = mapOf("vp_token" to vpToken),
                        ),
                    ).getOrThrow()

            val result = f.store.createResponseCode(parsedResponse)
            val responseCode = result.getOrThrow().responseCode

            // Should exist
            assertTrue(f.store.isValid(responseCode).getOrThrow())

            // Delete
            assertTrue(f.store.delete(responseCode).getOrThrow())

            // Should not exist
            assertFalse(f.store.isValid(responseCode).getOrThrow())
            assertFalse(f.store.delete(responseCode).getOrThrow())
        }

    /**
     * Mock validate authorization response command for testing.
     */
    private class MockValidateAuthorizationResponseCommand : ValidateAuthorizationResponseCommand {
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<ValidateAuthorizationResponseArgs> = typeToken<ValidateAuthorizationResponseArgs>()
        override val outputTypeToken: TypeToken<ValidationResult> = typeToken<ValidationResult>()

        var lastArgs: ValidateAuthorizationResponseArgs? = null

        override suspend fun execute(args: ValidateAuthorizationResponseArgs): IdkResult<ValidationResult, IdkError> {
            lastArgs = args
            return Ok(ValidationResult(valid = true, matchedCredentials = emptyList(), errors = emptyList()))
        }
    }

    /**
     * Mock JARM verification command for testing.
     */
    private class MockVerifyJarmResponseCommand : VerifyJarmResponseCommand {
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<VerifyJarmResponseArgs> = typeToken<VerifyJarmResponseArgs>()
        override val outputTypeToken: TypeToken<JarmVerificationResult> = typeToken<JarmVerificationResult>()

        override suspend fun execute(args: VerifyJarmResponseArgs): IdkResult<JarmVerificationResult, IdkError> = Err(IdkError.fromString("Mock JARM verification not implemented"))
    }

    private class MutableClock(
        startEpochMillis: Long,
    ) : Clock {
        private var nowEpochMillis: Long = startEpochMillis

        fun advance(millis: Long) {
            nowEpochMillis += millis
        }

        override fun now(): Instant = Instant.fromEpochMilliseconds(nowEpochMillis)
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

    /**
     * Test-only [KvStoreService] that simulates "no explicit KV config present".
     *
     * The production stores fall back to their internal defaults when configuration lookup fails.
     */
    private class NoConfigKvStoreService : KvStoreService {
        override fun getStoreIds(): Array<String> = emptyArray()

        override fun getStoreConfig(storeId: String): KvStoreConfigBase = throw IllegalArgumentException("KV store '$storeId' not configured for test")

        override fun getStore(storeId: String): KvStore = throw IllegalArgumentException("KV store '$storeId' not configured for test")
    }

    private class TestKvStore(
        private val clock: Clock,
    ) : KvStore {
        override val config: KvStoreConfigBase = InMemoryKvStoreConfig(id = "test", scopeBinding = KvStoreScopeBinding.TENANT)

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
            val now = clock.now().toEpochMilliseconds()
            val expiresAt = if (ttl.isInfinite()) Long.MAX_VALUE else now + ttl.inWholeMilliseconds
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
            val now = clock.now().toEpochMilliseconds()
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
            val now = clock.now().toEpochMilliseconds()
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
            val now = clock.now().toEpochMilliseconds()
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
            val now = clock.now().toEpochMilliseconds()
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
