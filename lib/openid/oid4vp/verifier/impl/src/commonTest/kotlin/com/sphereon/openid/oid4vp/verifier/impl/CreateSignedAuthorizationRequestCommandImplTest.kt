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
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.oauth2.client.command.CreateSignedJarArgs
import com.sphereon.oauth2.client.command.CreateSignedJarCommand
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.dcql.DcqlClaimQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.CreateSignedAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.impl.testutil.Oid4vpVerifierTestContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for CreateSignedAuthorizationRequestCommandImpl
 *
 * Tests the JAR (JWT-secured Authorization Request) creation flow for OpenID4VP RP.
 */
class CreateSignedAuthorizationRequestCommandImplTest {
    private val testContext = Oid4vpVerifierTestContext("create-signed-auth-req-test", this)

    // Mock signing key - uses KeyInfo with just a kid since mock command doesn't actually sign
    private val mockSigningKey = KeyInfo<Nothing>(kid = "test-key-id")

    /**
     * Mock CreateSignedJarCommand that returns a predictable JWT string
     */
    private class MockCreateSignedJarCommand(
        private val mockJar: String = "eyJhbGciOiJFUzI1NiJ9.mock-payload.mock-signature",
        private val shouldFail: Boolean = false,
        private val failureMessage: String = "Mock JAR creation failed",
    ) : CreateSignedJarCommand {
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<CreateSignedJarArgs> = typeToken<CreateSignedJarArgs>()
        override val outputTypeToken: TypeToken<StringResult> = typeToken<StringResult>()

        var lastArgs: CreateSignedJarArgs? = null
            private set

        override suspend fun execute(args: CreateSignedJarArgs): IdkResult<StringResult, IdkError> {
            lastArgs = args
            return if (shouldFail) {
                Err(IdkError.INVALID_STATE(message = failureMessage))
            } else {
                Ok(StringResult(mockJar))
            }
        }
    }

    private fun createTestDcqlQuery(): DcqlQuery =
        DcqlQuery(
            credentials =
                listOf(
                    DcqlCredentialQuery(
                        id = "identity_credential",
                        format = "dc+sd-jwt",
                        claims =
                            listOf(
                                DcqlClaimQuery(path = listOf("given_name")),
                                DcqlClaimQuery(path = listOf("family_name")),
                            ),
                    ),
                ),
        )

    private fun createCommand(mockJarCommand: MockCreateSignedJarCommand = MockCreateSignedJarCommand()): CreateSignedAuthorizationRequestCommandImpl {
        val createAuthRequestCommand =
            CreateAuthorizationRequestCommandImpl(
                execution = testContext.execution,
                authorizationSessionStore = TestAuthorizationSessionStore(),
                requestObjectSigningConfig =
                    com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
                        .disabled(),
            )
        return CreateSignedAuthorizationRequestCommandImpl(
            execution = testContext.execution,
            createAuthorizationRequestCommand = createAuthRequestCommand,
            createSignedJarCommand = mockJarCommand,
        )
    }

    @Test
    fun `test create signed authorization request success`() =
        runTest {
            val mockJarCommand =
                MockCreateSignedJarCommand(
                    mockJar = "eyJhbGciOiJFUzI1NiJ9.test-signed-request.signature",
                )
            val command = createCommand(mockJarCommand)

            val requestArgs =
                CreateAuthorizationRequestArgs(
                    instanceId = "verifier-instance-signed-request-success",
                    dcqlQuery = createTestDcqlQuery(),
                    clientId = "https://verifier.example.com",
                    responseUri = "https://verifier.example.com/response",
                    responseMode = ResponseMode.DIRECT_POST,
                    nonce = "nonce12345678",
                    state = "state123",
                )

            val args =
                CreateSignedAuthorizationRequestArgs(
                    requestArgs = requestArgs,
                    signingKey = mockSigningKey,
                    audience = "https://wallet.example.com",
                    expirationSeconds = 600,
                )

            val result = command.createSignedAuthorizationRequest(args)

            assertIs<Ok<*>>(result)
            val signedResult = result.value

            // Verify the result contains both request and signed JAR
            assertNotNull(signedResult.request)
            assertEquals("eyJhbGciOiJFUzI1NiJ9.test-signed-request.signature", signedResult.signedJar)
            assertNotNull(signedResult.sessionId)

            // Verify the request properties
            assertEquals("https://verifier.example.com", signedResult.request.clientId)
            assertEquals("vp_token", signedResult.request.responseType)

            // Verify JAR command was called with correct arguments
            val jarArgs = mockJarCommand.lastArgs
            assertNotNull(jarArgs)
            assertEquals("https://verifier.example.com", jarArgs.issuer)
            assertEquals("https://wallet.example.com", jarArgs.audience)
            assertEquals(600, jarArgs.expirationSeconds)
        }

    @Test
    fun `test signed request includes DCQL query`() =
        runTest {
            val mockJarCommand = MockCreateSignedJarCommand()
            val command = createCommand(mockJarCommand)

            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(
                                id = "mdoc_credential",
                                format = "mso_mdoc",
                                claims =
                                    listOf(
                                        DcqlClaimQuery(path = listOf("org.iso.18013.5.1", "given_name")),
                                    ),
                            ),
                        ),
                )

            val requestArgs =
                CreateAuthorizationRequestArgs(
                    instanceId = "verifier-instance-signed-dcql-request",
                    dcqlQuery = dcqlQuery,
                    clientId = "https://verifier.example.com",
                    responseUri = "https://verifier.example.com/callback",
                    responseMode = ResponseMode.DIRECT_POST,
                    nonce = "test-nonce-1234",
                )

            val args =
                CreateSignedAuthorizationRequestArgs(
                    requestArgs = requestArgs,
                    signingKey = mockSigningKey,
                    audience = "https://wallet.example.com",
                )

            val result = command.createSignedAuthorizationRequest(args)

            assertIs<Ok<*>>(result)
            val signedResult = result.value

            // Verify DCQL query is in the authorization request that was signed
            val jarArgs = mockJarCommand.lastArgs
            assertNotNull(jarArgs)
            assertNotNull(jarArgs.authorizationRequest.additionalParameters["dcql_query"])
        }

    @Test
    fun `test JAR signing failure propagates error`() =
        runTest {
            val mockJarCommand =
                MockCreateSignedJarCommand(
                    shouldFail = true,
                    failureMessage = "Key not found",
                )
            val command = createCommand(mockJarCommand)

            val requestArgs =
                CreateAuthorizationRequestArgs(
                    instanceId = "verifier-instance-signing-failure",
                    dcqlQuery = createTestDcqlQuery(),
                    clientId = "https://verifier.example.com",
                    responseUri = "https://verifier.example.com/response",
                    responseMode = ResponseMode.DIRECT_POST,
                    nonce = "nonce12345678",
                )

            val args =
                CreateSignedAuthorizationRequestArgs(
                    requestArgs = requestArgs,
                    signingKey = mockSigningKey,
                    audience = "https://wallet.example.com",
                )

            val result = command.createSignedAuthorizationRequest(args)

            assertIs<Err<*>>(result)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("JAR"),
            )
        }

    @Test
    fun `test invalid request args propagates validation error`() =
        runTest {
            val mockJarCommand = MockCreateSignedJarCommand()
            val command = createCommand(mockJarCommand)

            // Missing response_uri for direct_post mode
            val requestArgs =
                CreateAuthorizationRequestArgs(
                    instanceId = "verifier-instance-signed-request-validation",
                    dcqlQuery = createTestDcqlQuery(),
                    clientId = "https://verifier.example.com",
                    responseUri = null, // Missing!
                    responseMode = ResponseMode.DIRECT_POST,
                    nonce = "nonce12345678",
                )

            val args =
                CreateSignedAuthorizationRequestArgs(
                    requestArgs = requestArgs,
                    signingKey = mockSigningKey,
                    audience = "https://wallet.example.com",
                )

            val result = command.createSignedAuthorizationRequest(args)

            assertIs<Err<*>>(result)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("response_uri"),
            )
        }

    @Test
    fun `test custom expiration time is passed to JAR`() =
        runTest {
            val mockJarCommand = MockCreateSignedJarCommand()
            val command = createCommand(mockJarCommand)

            val requestArgs =
                CreateAuthorizationRequestArgs(
                    instanceId = "verifier-instance-custom-jar-expiration",
                    dcqlQuery = createTestDcqlQuery(),
                    clientId = "https://verifier.example.com",
                    responseUri = "https://verifier.example.com/response",
                    responseMode = ResponseMode.DIRECT_POST,
                    nonce = "nonce12345678",
                )

            val args =
                CreateSignedAuthorizationRequestArgs(
                    requestArgs = requestArgs,
                    signingKey = mockSigningKey,
                    audience = "https://wallet.example.com",
                    expirationSeconds = 3600, // 1 hour
                )

            val result = command.createSignedAuthorizationRequest(args)

            assertIs<Ok<*>>(result)

            // Verify custom expiration was passed
            val jarArgs = mockJarCommand.lastArgs
            assertNotNull(jarArgs)
            assertEquals(3600, jarArgs.expirationSeconds)
        }

    @Test
    fun `test default expiration time is 300 seconds`() =
        runTest {
            val mockJarCommand = MockCreateSignedJarCommand()
            val command = createCommand(mockJarCommand)

            val requestArgs =
                CreateAuthorizationRequestArgs(
                    instanceId = "verifier-instance-default-jar-expiration",
                    dcqlQuery = createTestDcqlQuery(),
                    clientId = "https://verifier.example.com",
                    responseUri = "https://verifier.example.com/response",
                    responseMode = ResponseMode.DIRECT_POST,
                    nonce = "nonce12345678",
                )

            val args =
                CreateSignedAuthorizationRequestArgs(
                    requestArgs = requestArgs,
                    signingKey = mockSigningKey,
                    audience = "https://wallet.example.com",
                    // No expirationSeconds - should use default
                )

            val result = command.createSignedAuthorizationRequest(args)

            assertIs<Ok<*>>(result)

            // Verify default expiration (300 seconds)
            val jarArgs = mockJarCommand.lastArgs
            assertNotNull(jarArgs)
            assertEquals(300, jarArgs.expirationSeconds)
        }

    @Test
    fun `test client_id is used as JWT issuer`() =
        runTest {
            val mockJarCommand = MockCreateSignedJarCommand()
            val command = createCommand(mockJarCommand)

            val requestArgs =
                CreateAuthorizationRequestArgs(
                    instanceId = "verifier-instance-jar-issuer",
                    dcqlQuery = createTestDcqlQuery(),
                    clientId = "did:example:verifier123",
                    responseUri = "https://verifier.example.com/response",
                    responseMode = ResponseMode.DIRECT_POST,
                    nonce = "nonce12345678",
                )

            val args =
                CreateSignedAuthorizationRequestArgs(
                    requestArgs = requestArgs,
                    signingKey = mockSigningKey,
                    audience = "https://wallet.example.com",
                )

            val result = command.createSignedAuthorizationRequest(args)

            assertIs<Ok<*>>(result)

            // Verify client_id is the JWT issuer
            val jarArgs = mockJarCommand.lastArgs
            assertNotNull(jarArgs)
            assertEquals("did:example:verifier123", jarArgs.issuer)
        }

    @Test
    fun `test fragment response mode with signed request`() =
        runTest {
            val mockJarCommand = MockCreateSignedJarCommand()
            val command = createCommand(mockJarCommand)

            val requestArgs =
                CreateAuthorizationRequestArgs(
                    instanceId = "verifier-instance-signed-fragment-response",
                    dcqlQuery = createTestDcqlQuery(),
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                    responseMode = ResponseMode.FRAGMENT,
                    nonce = "nonce12345678",
                )

            val args =
                CreateSignedAuthorizationRequestArgs(
                    requestArgs = requestArgs,
                    signingKey = mockSigningKey,
                    audience = "https://wallet.example.com",
                )

            val result = command.createSignedAuthorizationRequest(args)

            assertIs<Ok<*>>(result)
            val signedResult = result.value

            // Verify response mode is preserved
            assertEquals("fragment", signedResult.request.responseMode)
        }
}
