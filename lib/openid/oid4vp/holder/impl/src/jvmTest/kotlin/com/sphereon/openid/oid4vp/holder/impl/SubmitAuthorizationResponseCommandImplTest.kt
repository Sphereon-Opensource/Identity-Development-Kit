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

package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.MultiExternalIdentifierService
import com.sphereon.di.session.SessionContext
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.common.jarm.CreateJarmResponseArgs
import com.sphereon.oauth2.common.jarm.CreateJarmResponseCommand
import com.sphereon.oauth2.common.jarm.CreateJarmResponseResult
import com.sphereon.oauth2.common.jarm.JarmMode
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.common.buildOid4vpAuthorizationResponse
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SubmissionResult
import com.sphereon.openid.oid4vp.holder.SubmitAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.holder.VerifierInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for SubmitAuthorizationResponseCommandImpl.
 *
 * These tests verify the authorization response submission logic:
 * - Direct POST submission
 * - Fragment redirect mode
 * - Query redirect mode
 * - Error handling (missing URI, missing vp_token)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubmitAuthorizationResponseCommandImplTest {
    @Test
    fun `test dc_api returns authorization data without HTTP submission`() =
        runTest {
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = createMockHttpClientFactory(responseStatus = HttpStatusCode.InternalServerError),
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )
            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken("test_query", "eyJhbGciOiJFUzI1NiJ9.payload.signature")
                    state("dc-api-state")
                }

            val result =
                command.execute(
                    SubmitAuthorizationResponseArgs(
                        resolvedRequest = createResolvedRequestDirectPost(responseUri = ""),
                        response = response,
                        responseMode = ResponseMode.DC_API,
                    ),
                )

            val digitalCredential = assertIs<SubmissionResult.DigitalCredential>(result.value)
            assertEquals("dc-api-state", (digitalCredential.data["state"] as? JsonPrimitive)?.content)
            assertTrue(digitalCredential.data.containsKey("vp_token"))
        }

    /**
     * Create a resolved request for direct_post mode
     */
    private fun createResolvedRequestDirectPost(
        responseUri: String = "https://verifier.example.com/response",
        state: String? = "test-state",
    ): ResolvedOid4vpRequest {
        val authRequest =
            AuthorizationRequest(
                clientId = "test-client",
                redirectUri = "https://verifier.example.com/callback",
                responseType = "vp_token",
                state = state,
                responseMode = "direct_post",
                additionalParameters =
                    mapOf(
                        "nonce" to JsonPrimitive("test-nonce"),
                        "response_uri" to JsonPrimitive(responseUri),
                    ),
            )

        return ResolvedOid4vpRequest(
            request = authRequest,
            dcqlQuery = null,
            clientMetadata = null,
            verifierInfo =
                VerifierInfo(
                    clientId = "test-client",
                    clientIdScheme = ClientIdScheme.PRE_REGISTERED,
                    displayName = "Test Verifier",
                ),
        )
    }

    /**
     * Create a resolved request for fragment/query mode
     */
    private fun createResolvedRequestRedirect(
        redirectUri: String = "https://verifier.example.com/callback",
        state: String? = "test-state",
    ): ResolvedOid4vpRequest {
        val authRequest =
            AuthorizationRequest(
                clientId = "test-client",
                redirectUri = redirectUri,
                responseType = "vp_token",
                state = state,
                additionalParameters =
                    mapOf(
                        "nonce" to JsonPrimitive("test-nonce"),
                    ),
            )

        return ResolvedOid4vpRequest(
            request = authRequest,
            dcqlQuery = null,
            clientMetadata = null,
            verifierInfo =
                VerifierInfo(
                    clientId = "test-client",
                    clientIdScheme = ClientIdScheme.PRE_REGISTERED,
                    displayName = "Test Verifier",
                ),
        )
    }

    /**
     * Create a mock HttpClientFactory for testing
     */
    private fun createMockHttpClientFactory(
        responseStatus: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = """{"redirect_uri": "https://verifier.example.com/complete"}""",
    ): HttpClientFactory =
        object : HttpClientFactory {
            override fun createClient(options: HttpClientOptions): HttpClient =
                HttpClient(
                    MockEngine { request ->
                        respond(
                            content = responseBody,
                            status = responseStatus,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                ) {
                    install(ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                encodeDefaults = true
                            },
                        )
                    }
                }

            override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

            override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.CIO)

            override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
        }

    /**
     * Create a mock external identifier service for testing
     */
    private fun createMockExternalIdentifierService(): MultiExternalIdentifierService =
        object : MultiExternalIdentifierService {
            override val supportedIdentifierMethods: List<IIdentifierMethod> = emptyList()

            override suspend fun isSupportedIdentifier(identifier: Any): Boolean = false

            override suspend fun isSupportedIdentifierMethod(identifierMethod: IIdentifierMethod): Boolean = false

            override suspend fun isSupportedOpts(opts: ExternalIdentifierOptsOrResult): Boolean = false

            override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierOpts, IdkErrorType> =
                IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(message = "Not implemented in test").asErrorResult()

            override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult, IdkErrorType> =
                IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(message = "Not implemented in test").asErrorResult()
        }

    /**
     * Create a mock CreateJarmResponseCommand for testing
     */
    private fun createMockJarmCommand(): CreateJarmResponseCommand =
        object : CreateJarmResponseCommand {
            override val commandId: String = CreateJarmResponseCommand.COMMAND_ID
            override val isEnabled: Boolean = true
            override val inputTypeToken: TypeToken<CreateJarmResponseArgs> = typeToken<CreateJarmResponseArgs>()
            override val outputTypeToken: TypeToken<CreateJarmResponseResult> = typeToken<CreateJarmResponseResult>()

            override suspend fun execute(args: CreateJarmResponseArgs): IdkResult<CreateJarmResponseResult, IdkError> =
                Ok(
                    CreateJarmResponseResult(
                        jarmJwt = "mock.jarm.jwt",
                        mode = JarmMode.SIGNED,
                    ),
                )
        }

    // =============================
    // Fragment Mode Tests
    // =============================

    @Test
    fun `test fragment mode builds correct redirect URI`() =
        runTest {
            val mockFactory = createMockHttpClientFactory()
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = mockFactory,
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )

            val resolvedRequest = createResolvedRequestRedirect()
            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken("test_query", "eyJhbGciOiJFUzI1NiJ9.payload.signature")
                    state("test-state")
                }

            val args = SubmitAuthorizationResponseArgs(resolvedRequest, response, ResponseMode.FRAGMENT)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val submissionResult = result.value
            assertIs<SubmissionResult.Redirect>(submissionResult)

            // Verify redirect URI structure
            assertTrue(submissionResult.redirectUri.startsWith("https://verifier.example.com/callback#"))
            assertTrue(submissionResult.redirectUri.contains("vp_token="))
            assertTrue(submissionResult.redirectUri.contains("state=test-state"))
        }

    @Test
    fun `test fragment mode with multiple credentials`() =
        runTest {
            val mockFactory = createMockHttpClientFactory()
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = mockFactory,
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )

            val resolvedRequest = createResolvedRequestRedirect()
            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken(
                        mapOf(
                            "query1" to listOf("jwt1.payload.sig1"),
                            "query2" to listOf("jwt2.payload.sig2"),
                        ),
                    )
                    state("test-state")
                }

            val args = SubmitAuthorizationResponseArgs(resolvedRequest, response, ResponseMode.FRAGMENT)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val submissionResult = result.value
            assertIs<SubmissionResult.Redirect>(submissionResult)
            assertTrue(submissionResult.redirectUri.startsWith("https://verifier.example.com/callback#"))
            assertTrue(submissionResult.redirectUri.contains("vp_token="))
        }

    @Test
    fun `test fragment mode without state`() =
        runTest {
            val mockFactory = createMockHttpClientFactory()
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = mockFactory,
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )

            val resolvedRequest = createResolvedRequestRedirect(state = null)
            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken("test_query", "eyJhbGciOiJFUzI1NiJ9.payload.signature")
                }

            val args = SubmitAuthorizationResponseArgs(resolvedRequest, response, ResponseMode.FRAGMENT)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val submissionResult = result.value
            assertIs<SubmissionResult.Redirect>(submissionResult)

            // Should not contain state parameter
            assertTrue(submissionResult.redirectUri.startsWith("https://verifier.example.com/callback#vp_token="))
            assertTrue(!submissionResult.redirectUri.contains("state="))
        }

    // =============================
    // Query Mode Tests
    // =============================

    @Test
    fun `test query mode builds correct redirect URI`() =
        runTest {
            val mockFactory = createMockHttpClientFactory()
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = mockFactory,
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )

            val resolvedRequest = createResolvedRequestRedirect()
            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken("test_query", "eyJhbGciOiJFUzI1NiJ9.payload.signature")
                    state("test-state")
                }

            val args = SubmitAuthorizationResponseArgs(resolvedRequest, response, ResponseMode.QUERY)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val submissionResult = result.value
            assertIs<SubmissionResult.Redirect>(submissionResult)

            // Verify query string structure
            assertTrue(submissionResult.redirectUri.startsWith("https://verifier.example.com/callback?"))
            assertTrue(submissionResult.redirectUri.contains("vp_token="))
            assertTrue(submissionResult.redirectUri.contains("state=test-state"))
        }

    @Test
    fun `test query mode with existing query parameters`() =
        runTest {
            val mockFactory = createMockHttpClientFactory()
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = mockFactory,
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )

            // redirect_uri already has query params
            val resolvedRequest =
                createResolvedRequestRedirect(
                    redirectUri = "https://verifier.example.com/callback?session=abc123",
                )
            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken("test_query", "eyJhbGciOiJFUzI1NiJ9.payload.signature")
                    state("test-state")
                }

            val args = SubmitAuthorizationResponseArgs(resolvedRequest, response, ResponseMode.QUERY)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val submissionResult = result.value
            assertIs<SubmissionResult.Redirect>(submissionResult)

            // Should append with & since there's already a ?
            assertTrue(submissionResult.redirectUri.contains("session=abc123"))
            assertTrue(submissionResult.redirectUri.contains("vp_token="))
        }

    // =============================
    // Direct Post Mode Tests
    // =============================

    @Test
    fun `test direct_post success with redirect_uri in response`() =
        runTest {
            val mockFactory =
                createMockHttpClientFactory(
                    responseStatus = HttpStatusCode.OK,
                    responseBody = """{"redirect_uri": "https://verifier.example.com/complete?session=xyz"}""",
                )
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = mockFactory,
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )

            val resolvedRequest = createResolvedRequestDirectPost()
            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken("test_query", "eyJhbGciOiJFUzI1NiJ9.payload.signature")
                    state("test-state")
                }

            val args = SubmitAuthorizationResponseArgs(resolvedRequest, response, ResponseMode.DIRECT_POST)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val submissionResult = result.value
            assertIs<SubmissionResult.Success>(submissionResult)
            assertEquals("https://verifier.example.com/complete?session=xyz", submissionResult.redirectUri)
        }

    @Test
    fun `test direct_post success without redirect_uri`() =
        runTest {
            val mockFactory =
                createMockHttpClientFactory(
                    responseStatus = HttpStatusCode.OK,
                    responseBody = """{}""",
                )
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = mockFactory,
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )

            val resolvedRequest = createResolvedRequestDirectPost()
            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken("test_query", "eyJhbGciOiJFUzI1NiJ9.payload.signature")
                }

            val args = SubmitAuthorizationResponseArgs(resolvedRequest, response, ResponseMode.DIRECT_POST)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val submissionResult = result.value
            assertIs<SubmissionResult.Success>(submissionResult)
            assertEquals(null, submissionResult.redirectUri)
        }

    @Test
    fun `test direct_post error response`() =
        runTest {
            val mockFactory =
                createMockHttpClientFactory(
                    responseStatus = HttpStatusCode.BadRequest,
                    responseBody = """{"error": "invalid_request", "error_description": "Invalid VP token"}""",
                )
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = mockFactory,
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )

            val resolvedRequest = createResolvedRequestDirectPost()
            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken("test_query", "invalid-token")
                }

            val args = SubmitAuthorizationResponseArgs(resolvedRequest, response, ResponseMode.DIRECT_POST)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val submissionResult = result.value
            assertIs<SubmissionResult.Error>(submissionResult)
            assertEquals("invalid_request", submissionResult.error)
            assertEquals("Invalid VP token", submissionResult.errorDescription)
        }

    @Test
    fun `test direct_post missing response_uri fails`() =
        runTest {
            val mockFactory = createMockHttpClientFactory()
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = mockFactory,
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )

            // Create request without response_uri
            val authRequest =
                AuthorizationRequest(
                    clientId = "test-client",
                    redirectUri = "https://verifier.example.com/callback",
                    responseType = "vp_token",
                    responseMode = "direct_post",
                    additionalParameters =
                        mapOf(
                            "nonce" to JsonPrimitive("test-nonce"),
                            // No response_uri!
                        ),
                )
            val resolvedRequest =
                ResolvedOid4vpRequest(
                    request = authRequest,
                    dcqlQuery = null,
                    clientMetadata = null,
                    verifierInfo =
                        VerifierInfo(
                            clientId = "test-client",
                            clientIdScheme = ClientIdScheme.PRE_REGISTERED,
                        ),
                )
            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken("test_query", "eyJhbGciOiJFUzI1NiJ9.payload.signature")
                }

            val args = SubmitAuthorizationResponseArgs(resolvedRequest, response, ResponseMode.DIRECT_POST)
            val result = command.execute(args)

            assertIs<Err<*>>(result)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("response_uri", ignoreCase = true),
            )
        }

    // =============================
    // Validation Tests
    // =============================

    @Test
    fun `test missing vp_token fails`() =
        runTest {
            val mockFactory = createMockHttpClientFactory()
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = mockFactory,
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )

            val resolvedRequest = createResolvedRequestRedirect()
            // Response without vp_token
            val response =
                buildOid4vpAuthorizationResponse {
                    state("test-state")
                    // No vpToken!
                }

            val args = SubmitAuthorizationResponseArgs(resolvedRequest, response, ResponseMode.FRAGMENT)
            val result = command.execute(args)

            assertIs<Err<*>>(result)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("vp_token", ignoreCase = true),
            )
        }

    @Test
    fun `test http scheme rejected for non-localhost`() =
        runTest {
            val mockFactory = createMockHttpClientFactory()
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = mockFactory,
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )

            // Create request with HTTP (not HTTPS) URI
            val resolvedRequest =
                createResolvedRequestDirectPost(
                    responseUri = "http://verifier.example.com/response",
                )
            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken("test_query", "eyJhbGciOiJFUzI1NiJ9.payload.signature")
                }

            val args = SubmitAuthorizationResponseArgs(resolvedRequest, response, ResponseMode.DIRECT_POST)
            val result = command.execute(args)

            assertIs<Err<*>>(result)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("HTTPS", ignoreCase = true),
            )
        }

    @Test
    fun `test http scheme allowed for localhost`() =
        runTest {
            val mockFactory = createMockHttpClientFactory()
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = mockFactory,
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )

            // localhost is allowed for testing
            val resolvedRequest =
                createResolvedRequestDirectPost(
                    responseUri = "http://localhost:8080/response",
                )
            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken("test_query", "eyJhbGciOiJFUzI1NiJ9.payload.signature")
                }

            val args = SubmitAuthorizationResponseArgs(resolvedRequest, response, ResponseMode.DIRECT_POST)
            val result = command.execute(args)

            // Should succeed (validation should pass)
            assertIs<Ok<*>>(result)
        }

    @Test
    fun `test direct_post_jwt mode without jarmOptions returns error`() =
        runTest {
            val mockFactory = createMockHttpClientFactory()
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = mockFactory,
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )

            val resolvedRequest = createResolvedRequestDirectPost()
            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken("test_query", "eyJhbGciOiJFUzI1NiJ9.payload.signature")
                }

            val args = SubmitAuthorizationResponseArgs(resolvedRequest, response, ResponseMode.DIRECT_POST_JWT)
            val result = command.execute(args)

            // DIRECT_POST_JWT mode requires jarmOptions - without it, should return ILLEGAL_ARGUMENT error
            assertIs<Err<*>>(result)
            assertTrue(result.error.code.contains("ILLEGAL_ARGUMENT"))
        }

    // =============================
    // Response Mode Detection Tests
    // =============================

    @Test
    fun `test response mode defaults to request response_mode`() =
        runTest {
            val mockFactory = createMockHttpClientFactory()
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = mockFactory,
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )

            val resolvedRequest = createResolvedRequestDirectPost()
            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken("test_query", "eyJhbGciOiJFUzI1NiJ9.payload.signature")
                }

            // Don't specify responseMode - should use request's response_mode (direct_post)
            val args = SubmitAuthorizationResponseArgs(resolvedRequest, response)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val submissionResult = result.value
            assertIs<SubmissionResult.Success>(submissionResult) // direct_post returns Success
        }

    @Test
    fun `test response mode override works`() =
        runTest {
            val mockFactory = createMockHttpClientFactory()
            val command =
                SubmitAuthorizationResponseCommandImpl(
                    execution = TestExecutionContext.createExecution(),
                    httpClientFactory = mockFactory,
                    externalIdentifierService = createMockExternalIdentifierService(),
                    createJarmCommand = createMockJarmCommand(),
                )

            // Request says direct_post, but we override to fragment
            val resolvedRequest = createResolvedRequestDirectPost()
            val response =
                buildOid4vpAuthorizationResponse {
                    vpToken("test_query", "eyJhbGciOiJFUzI1NiJ9.payload.signature")
                }

            val args = SubmitAuthorizationResponseArgs(resolvedRequest, response, ResponseMode.FRAGMENT)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val submissionResult = result.value
            assertIs<SubmissionResult.Redirect>(submissionResult) // Fragment returns Redirect
        }
}
