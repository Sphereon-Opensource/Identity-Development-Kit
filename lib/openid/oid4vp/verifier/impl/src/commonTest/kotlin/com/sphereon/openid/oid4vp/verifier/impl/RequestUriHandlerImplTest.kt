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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class RequestUriHandlerImplTest {
    private val testContext = Oid4vpVerifierTestContext("request-uri-handler-test", this)

    @Test
    fun `did request object iss is the did and kid is its full assertionMethod`() {
        val did = "did:web:verifier.example"
        val binding = VerifierSignerBinding.Did(did = did, verificationMethodId = "$did#verifier-request-object-root")

        assertEquals(did, binding.requestObjectIssuer())
        assertEquals(binding.requestObjectIssuer(), binding.verificationMethodId.substringBefore('#'))
        assertFailsWith<IllegalArgumentException> {
            VerifierSignerBinding.Did(
                did = did,
                verificationMethodId = "did:web:other.example#verifier-request-object-root",
            ).requestObjectIssuer()
        }
        assertFailsWith<IllegalArgumentException> {
            VerifierSignerBinding.Did(did = did, verificationMethodId = did).requestObjectIssuer()
        }
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
                        clientId = "https://verifier.example.com",
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
