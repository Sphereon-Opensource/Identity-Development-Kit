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
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.oauth2.client.command.CreateSignedJarArgs
import com.sphereon.oauth2.client.command.CreateSignedJarCommand
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.sdJwtVcMeta
import com.sphereon.openid.oid4vp.verifier.impl.testutil.Oid4vpVerifierTestContext
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.requesturi.VerifierSignerBinding
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class RequestUriHandlerImplTest {
    private val testContext = Oid4vpVerifierTestContext("request-uri-handler-test", this)

    @Test
    fun `did binding client_id is decentralized_identifier prefixed and kid may be absolute or relative`() {
        val did = "did:web:verifier.example"
        val binding = VerifierSignerBinding.Did(did = did, verificationMethodId = "$did#verifier-request-object-root")

        assertEquals("decentralized_identifier:$did", binding.clientId)
        assertEquals(ClientIdScheme.DECENTRALIZED_IDENTIFIER, binding.scheme)
        assertEquals(did, binding.bareIdentifier)
        assertEquals(did, binding.requestObjectIssuer())
        assertEquals(binding.requestObjectIssuer(), binding.verificationMethodId.substringBefore('#'))
        assertEquals("$did#verifier-request-object-root", binding.absoluteVerificationMethodId)

        // Relative fragment is accepted and qualified against the client_id DID.
        val relative = VerifierSignerBinding.Did(did = did, verificationMethodId = "#verifier-request-object-root")
        assertEquals("#verifier-request-object-root", relative.verificationMethodId)
        assertEquals("$did#verifier-request-object-root", relative.absoluteVerificationMethodId)

        assertFailsWith<IllegalArgumentException> {
            VerifierSignerBinding.Did(
                did = did,
                verificationMethodId = "did:web:other.example#verifier-request-object-root",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            VerifierSignerBinding.Did(did = did, verificationMethodId = did)
        }
    }

    @Test
    fun `signed DID JAR qualifies relative kid to absolute against client_id DID`() =
        runTest {
            val did = "did:jwk:eyJhbGciOiJFUzI1NiJ9"
            val binding = VerifierSignerBinding.Did(did = did, verificationMethodId = "#0")
            val capture = CapturingSignedJarCommand(testContext)
            val store = TestAuthorizationSessionStore()
            val handler =
                RequestUriHandlerImpl(
                    authorizationSessionStore = store,
                    createSignedJarCommand = capture,
                    signingConfig = EnabledSigningConfig(binding = binding, includeIss = false),
                    clock = Clock.System,
                )
            val correlationId =
                preloadSession(
                    store,
                    sessionId = "sess-did-relative-kid",
                    clientId = binding.clientId,
                )

            val response = handler.handleGet("/oid4vp/request-uri/$correlationId")
            assertIs<Ok<*>>(response)
            val args = assertNotNull(capture.lastArgs)
            assertEquals("$did#0", args.kid)
            assertEquals(binding.clientId, args.authorizationRequest.clientId)
        }

    @Test
    fun `signed DID JAR uses prefixed client_id and absolute kid — never HTTPS client_id`() =
        runTest {
            val did = "did:jwk:eyJhbGciOiJFUzI1NiJ9"
            val vmId = "$did#0"
            val binding = VerifierSignerBinding.Did(did = did, verificationMethodId = vmId)
            val capture = CapturingSignedJarCommand(testContext)
            val store = TestAuthorizationSessionStore()
            val handler =
                RequestUriHandlerImpl(
                    authorizationSessionStore = store,
                    createSignedJarCommand = capture,
                    signingConfig = EnabledSigningConfig(binding = binding, includeIss = false),
                    clock = Clock.System,
                )
            val correlationId =
                preloadSession(
                    store,
                    sessionId = "sess-did-jar",
                    clientId = binding.clientId,
                )

            val response = handler.handleGet("/oid4vp/request-uri/$correlationId")
            assertIs<Ok<*>>(response)
            val args = assertNotNull(capture.lastArgs)
            assertEquals(binding.clientId, args.authorizationRequest.clientId)
            assertEquals(vmId, args.kid)
            assertNull(args.x5c)
            assertEquals(false, args.includeIss)
            assertEquals(did, args.issuer)
            assertTrue(args.authorizationRequest.clientId.startsWith("decentralized_identifier:"))
            assertTrue(!args.authorizationRequest.clientId.startsWith("https://"))
        }

    @Test
    fun `signed x509_san_dns JAR uses prefixed client_id and x5c without DID kid`() =
        runTest {
            val binding =
                VerifierSignerBinding.X509SanDns(
                    dnsName = "verifier.example.com",
                    certificateChain = listOf("MIIBdTCCARugAwIBAgIUTestLeaf"),
                )
            val capture = CapturingSignedJarCommand(testContext)
            val store = TestAuthorizationSessionStore()
            val handler =
                RequestUriHandlerImpl(
                    authorizationSessionStore = store,
                    createSignedJarCommand = capture,
                    signingConfig = EnabledSigningConfig(binding = binding, includeIss = false),
                    clock = Clock.System,
                )
            val correlationId =
                preloadSession(
                    store,
                    sessionId = "sess-x509-jar",
                    clientId = binding.clientId,
                )

            val response = handler.handleGet("/oid4vp/request-uri/$correlationId")
            assertIs<Ok<*>>(response)
            val args = assertNotNull(capture.lastArgs)
            assertEquals("x509_san_dns:verifier.example.com", args.authorizationRequest.clientId)
            assertNull(args.kid)
            assertEquals(listOf("MIIBdTCCARugAwIBAgIUTestLeaf"), args.x5c)
        }

    @Test
    fun `signed JAR rejects session whose client_id does not match binding prefix identity`() =
        runTest {
            val binding =
                VerifierSignerBinding.Did(
                    did = "did:jwk:eyJhbGciOiJFUzI1NiJ9",
                    verificationMethodId = "did:jwk:eyJhbGciOiJFUzI1NiJ9#0",
                )
            val capture = CapturingSignedJarCommand(testContext)
            val store = TestAuthorizationSessionStore()
            val handler =
                RequestUriHandlerImpl(
                    authorizationSessionStore = store,
                    createSignedJarCommand = capture,
                    signingConfig = EnabledSigningConfig(binding = binding),
                    clock = Clock.System,
                )
            // Stale HTTPS client_id must not be served under DID signing.
            val correlationId =
                preloadSession(
                    store,
                    sessionId = "sess-mismatch",
                    clientId = "https://verifier.example.com",
                )

            val response = handler.handleGet("/oid4vp/request-uri/$correlationId")
            assertIs<Err<*>>(response)
            assertTrue(
                response.error.message.defaultMessage.contains("does not match"),
                response.error.message.defaultMessage,
            )
            assertNull(capture.lastArgs)
        }

    @Test
    fun `handlePost echoes wallet_nonce as a JAR claim`() =
        runTest {
            val handler = createHandler()
            val correlationId = preloadSession(handler.store, sessionId = "sess-post-echo")

            val nonce = "wallet-nonce-12345"
            val response =
                handler.handler.handlePost(
                    requestUriPath = "/oid4vp/request-uri/$correlationId",
                    walletMetadata = null,
                    walletNonce = nonce,
                )

            assertIs<Ok<*>>(response)
            val claims = decodeUnsignedJarPayload(response.value.signedJar)
            assertEquals(nonce, claims["wallet_nonce"]?.jsonPrimitive?.content)
        }

    @Test
    fun `handleGet emits no wallet_nonce claim`() =
        runTest {
            val handler = createHandler()
            val correlationId = preloadSession(handler.store, sessionId = "sess-get-no-echo")

            val response = handler.handler.handleGet("/oid4vp/request-uri/$correlationId")

            assertIs<Ok<*>>(response)
            val claims = decodeUnsignedJarPayload(response.value.signedJar)
            assertNull(claims["wallet_nonce"])
        }

    @Test
    fun `handlePost rejects wallet_nonce shorter than the minimum length`() =
        runTest {
            val handler = createHandler()
            val correlationId = preloadSession(handler.store, sessionId = "sess-too-short")

            val response =
                handler.handler.handlePost(
                    requestUriPath = "/oid4vp/request-uri/$correlationId",
                    walletNonce = "abc",
                )

            assertIs<Err<*>>(response)
            assertTrue(
                response.error.message.defaultMessage
                    .contains("at least")
            )
        }

    @Test
    fun `handlePost rejects wallet_nonce with characters outside the unreserved set`() =
        runTest {
            val handler = createHandler()
            val correlationId = preloadSession(handler.store, sessionId = "sess-bad-chars")

            // Space is not in RFC 3986 unreserved (`A-Z a-z 0-9 -._~`).
            val response =
                handler.handler.handlePost(
                    requestUriPath = "/oid4vp/request-uri/$correlationId",
                    walletNonce = "valid-prefix bad-suffix",
                )

            assertIs<Err<*>>(response)
            assertTrue(
                response.error.message.defaultMessage
                    .contains("unreserved characters")
            )
        }

    private data class HandlerHarness(
        val handler: RequestUriHandlerImpl,
        val store: TestAuthorizationSessionStore,
    )

    private fun createHandler(): HandlerHarness {
        val store = TestAuthorizationSessionStore()
        val handler =
            RequestUriHandlerImpl(
                authorizationSessionStore = store,
                createSignedJarCommand = NeverCalledSignedJarCommand(testContext),
                signingConfig = RequestObjectSigningConfig.disabled(),
                clock = Clock.System,
            )
        return HandlerHarness(handler = handler, store = store)
    }

    private suspend fun preloadSession(
        store: TestAuthorizationSessionStore,
        sessionId: String,
        clientId: String = "https://verifier.example.com",
    ): String {
        val now = Clock.System.now().toEpochMilliseconds()
        val session =
            AuthorizationSession(
                instanceId = "verifier-instance-request-uri-handler",
                sessionId = sessionId,
                correlationId = sessionId,
                dcqlQuery =
                    DcqlQuery(
                        credentials =
                            listOf(
                                DcqlCredentialQuery(
                                    id = "credential",
                                    format = "dc+sd-jwt",
                                    meta = sdJwtVcMeta("urn:test:credential"),
                                ),
                            ),
                    ),
                authorizationRequest =
                    AuthorizationRequest(
                        clientId = clientId,
                        redirectUri = "https://verifier.example.com/callback",
                        state = "state-1",
                        nonce = "nonce-12345678",
                        responseMode = "direct_post",
                    ),
                status = AuthorizationSessionStatus.AUTHORIZATION_REQUEST_CREATED,
                createdAt = now,
                updatedAt = now,
                expiresAt = now + 600_000,
            )
        val put = store.put(sessionId, session, ttlSeconds = 600)
        assertIs<Ok<*>>(put)
        val persistedSession = store.get(sessionId)
        assertIs<Ok<*>>(persistedSession)
        assertEquals(session.instanceId, persistedSession.value?.instanceId)
        return sessionId
    }

    private fun decodeUnsignedJarPayload(jar: String): JsonObject {
        val parts = jar.split('.')
        assertTrue(parts.size >= 2, "Expected JWT-shaped JAR, got: $jar")
        val payload = parts[1].decodeFromBase64Url().decodeToString()
        return Json.parseToJsonElement(payload) as JsonObject
    }

    private class EnabledSigningConfig(
        private val binding: VerifierSignerBinding,
        override val includeIss: Boolean = false,
    ) : RequestObjectSigningConfig {
        override val enabled: Boolean = true
        override val audience: String = "https://wallet.example.com"
        override val expirationSeconds: Long = 60

        override suspend fun resolveSigningKey() =
            com.sphereon.crypto.core.KeyInfo<Nothing>(alias = "stub-jar-key")

        override suspend fun resolveSignerBinding(scheme: ClientIdScheme?) = binding
    }

    /**
     * Captures [CreateSignedJarArgs] so tests can assert OID4VP §5.9.3 client_id / kid / x5c
     * without needing a real KMS signature.
     */
    private class CapturingSignedJarCommand(
        testContext: Oid4vpVerifierTestContext,
    ) : TypedServiceCommandAdapter<CreateSignedJarArgs, StringResult, IdkError>(
            commandId = CreateSignedJarCommand.COMMAND_ID,
            execution = testContext.execution,
            inputTypeToken = typeToken<CreateSignedJarArgs>(),
            outputTypeToken = typeToken<StringResult>(),
        ),
        CreateSignedJarCommand {
        var lastArgs: CreateSignedJarArgs? = null
            private set

        override val commandId: String get() = CreateSignedJarCommand.COMMAND_ID

        override suspend fun supports(args: Any): Boolean = args is CreateSignedJarArgs

        override suspend fun doExecute(
            args: CreateSignedJarArgs,
            applyDuring: (CreateSignedJarArgs) -> CreateSignedJarArgs,
        ): IdkResult<StringResult, IdkError> {
            lastArgs = applyDuring(args)
            return Ok(StringResult("header.payload.signature"))
        }
    }

    /**
     * The unsigned-JAR fast path in [RequestUriHandlerImpl] never calls the signing command,
     * but the constructor requires one. Throw if anything ever does call us so a future
     * refactor can't silently route unsigned tests through the signing path.
     */
    private class NeverCalledSignedJarCommand(
        testContext: Oid4vpVerifierTestContext,
    ) : TypedServiceCommandAdapter<CreateSignedJarArgs, StringResult, IdkError>(
            commandId = CreateSignedJarCommand.COMMAND_ID,
            execution = testContext.execution,
            inputTypeToken = typeToken<CreateSignedJarArgs>(),
            outputTypeToken = typeToken<StringResult>(),
        ),
        CreateSignedJarCommand {
        override val commandId: String get() = CreateSignedJarCommand.COMMAND_ID

        override suspend fun supports(args: Any): Boolean = args is CreateSignedJarArgs

        override suspend fun doExecute(
            args: CreateSignedJarArgs,
            applyDuring: (CreateSignedJarArgs) -> CreateSignedJarArgs,
        ): IdkResult<StringResult, IdkError> = error("CreateSignedJarCommand should not be invoked under disabled signing config")
    }
}
