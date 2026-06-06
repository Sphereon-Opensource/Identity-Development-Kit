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

package com.sphereon.openid.oid4vci.issuer.impl.http.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.defaults.http.NoOpRoutableSlugLookup
import com.sphereon.credential.issuance.pipeline.PipelineConfiguration
import com.sphereon.credential.issuance.pipeline.command.InitPipelineSessionArgs
import com.sphereon.credential.issuance.pipeline.command.InitPipelineSessionCommand
import com.sphereon.credential.issuance.pipeline.command.InitPipelineSessionResult
import com.sphereon.openid.oid4vci.issuer.config.NoOpVctTypeMetadataProvider
import com.sphereon.openid.oid4vci.issuer.impl.command.InMemoryOfferRateLimiter
import com.sphereon.openid.oid4vci.issuer.impl.encryption.CredentialResponseEncryptor
import com.sphereon.openid.oid4vci.issuer.impl.http.FakeCreateCredentialOfferCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.FakeCredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.impl.http.FakeCredentialOfferSessionStore
import com.sphereon.openid.oid4vci.issuer.impl.http.FakeCredentialOfferStore
import com.sphereon.openid.oid4vci.issuer.impl.http.FakeDecryptJweCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.FakeHandleCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.FakeHandleDeferredCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.FakeHandleNotificationCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.FakeIssueNonceCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.FakeOid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.impl.http.Oid4vciIssuerProtocolHttpAdapter
import com.sphereon.openid.oid4vci.issuer.impl.http.TestSessionExecution
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetSessionAttributesEndpointCommandImpl
import com.sphereon.openid.oid4vci.issuer.impl.http.testTenantIdProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class InitPipelineSessionEndpointCommandTest {
    private val execution = TestSessionExecution()
    private val fakeInit = FakeInitPipelineSessionCommand()
    private val command = InitPipelineSessionEndpointCommandImpl(execution, fakeInit)

    // ========================================================================
    // Descriptor
    // ========================================================================

    @Test
    fun endpointDescriptorHasCorrectMethodAndPath() {
        val endpoint = InitPipelineSessionEndpointCommand.ENDPOINT
        assertEquals(HttpMethod.POST, endpoint.method)
        assertEquals("/sessions", endpoint.pathPattern)
    }

    @Test
    fun commandIdMatchesExpected() {
        assertEquals("oid4vci.protocol.init-pipeline-session", InitPipelineSessionEndpointCommand.COMMAND_ID)
        assertEquals(InitPipelineSessionEndpointCommand.COMMAND_ID, command.id)
    }

    // ========================================================================
    // doExecute delegates to service command with body-provided args
    // ========================================================================

    @Test
    fun delegatesToServiceCommandWithBodyFields() =
        runTest {
            fakeInit.result =
                Ok(
                    InitPipelineSessionResult(
                        sessionId = "session-abc",
                        correlationId = "corr-123",
                    ),
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = {
                        """{"pipeline_configuration":{"pipelineId":"pipe-1"},"correlation_id":"corr-123","ttl_seconds":3600}"""
                    },
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertNotNull(result.value.body)

            val capturedArgs = fakeInit.lastArgs
            assertNotNull(capturedArgs)
            assertEquals("pipe-1", capturedArgs.pipelineConfiguration.pipelineId)
            assertEquals("corr-123", capturedArgs.correlationId)
            assertEquals(3600L, capturedArgs.ttlSeconds)
            assertTrue(capturedArgs.initialAttributes.isEmpty())
            assertTrue(capturedArgs.initialLookupKeys.isEmpty())
        }

    @Test
    fun returns200OnSuccess() =
        runTest {
            fakeInit.result =
                Ok(
                    InitPipelineSessionResult(
                        sessionId = "session-xyz",
                        correlationId = "generated-corr",
                    ),
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = {
                        """{"pipeline_configuration":{"pipelineId":"pipe-2"}}"""
                    },
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertEquals("application/json", result.value.headers["Content-Type"])
            assertNotNull(result.value.body)
        }

    @Test
    fun correlationIdIsNullWhenAbsentFromBody() =
        runTest {
            fakeInit.result =
                Ok(
                    InitPipelineSessionResult(
                        sessionId = "session-no-corr",
                        correlationId = "auto-generated",
                    ),
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions",
                    bodySupplier = {
                        """{"pipeline_configuration":{"pipelineId":"pipe-3"}}"""
                    },
                )

            command.execute(request)

            val capturedArgs = fakeInit.lastArgs
            assertNotNull(capturedArgs)
            assertNull(capturedArgs.correlationId)
        }

    // ========================================================================
    // Error paths
    // ========================================================================

    @Test
    fun propagatesServiceCommandError() =
        runTest {
            fakeInit.result =
                Err(IdkError.NOT_FOUND_ERROR(message = "Pipeline configuration not found"))

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions",
                    bodySupplier = {
                        """{"pipeline_configuration":{"pipelineId":"unknown-pipe"}}"""
                    },
                )

            val result = command.execute(request)

            assertTrue(result.isErr)
            assertNotNull(result.error)
        }

    @Test
    fun returnsErrorOnMalformedBody() =
        runTest {
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = { "not-valid-json{{" },
                )

            val result = command.execute(request)

            assertTrue(result.isErr)
            assertNull(fakeInit.lastArgs)
        }

    @Test
    fun returnsErrWhenNoPipelineIsBound() =
        runTest {
            val commandWithoutPipeline = InitPipelineSessionEndpointCommandImpl(execution, initPipelineSessionCommand = null)

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions",
                    bodySupplier = {
                        """{"pipeline_configuration":{"pipelineId":"pipe-x"}}"""
                    },
                )

            val result = commandWithoutPipeline.execute(request)

            assertTrue(result.isErr)
            assertNotNull(result.error)
        }

    // ========================================================================
    // Adapter-level integration: route is reachable via the protocol adapter.
    // Uses the full /oid4vci/... path; the adapter strips the prefix before
    // routing to the endpoint command.
    // ========================================================================

    @Test
    fun protocolAdapterRoutesPostSessionsToEndpointCommand() =
        runTest {
            fakeInit.result =
                Ok(
                    InitPipelineSessionResult(
                        sessionId = "integrated-session",
                        correlationId = "integrated-corr",
                    ),
                )

            val fakeConfigProvider = FakeOid4vciIssuerConfigProvider()
            val fakeOfferStore = FakeCredentialOfferStore()
            val fakeOfferSessionStore = FakeCredentialOfferSessionStore()
            val rateLimiterClock =
                object : Clock {
                    override fun now(): Instant = Instant.parse("2026-05-15T12:00:00Z")
                }
            val offerRateLimiter = InMemoryOfferRateLimiter(rateLimiterClock)
            val credentialResponseEncryptor =
                CredentialResponseEncryptor(
                    jweService = ThrowingJweService,
                    configProvider = fakeConfigProvider,
                )
            val credentialOfferCommand =
                GetCredentialOfferEndpointCommandImpl(
                    execution,
                    fakeOfferStore,
                    FakeCredentialIssuanceSessionStore(),
                    fakeOfferSessionStore,
                    offerRateLimiter,
                    FakeCreateCredentialOfferCommand(),
                )
            val nonceCommand = IssueNonceEndpointCommandImpl(execution, FakeIssueNonceCommand())
            val credentialCommand =
                HandleCredentialEndpointCommandImpl(
                    execution,
                    FakeHandleCredentialRequestCommand(),
                    FakeDecryptJweCommand(),
                    fakeConfigProvider,
                    credentialResponseEncryptor,
                )
            val deferredCommand =
                HandleDeferredCredentialEndpointCommandImpl(
                    execution,
                    FakeHandleDeferredCredentialRequestCommand(),
                    FakeDecryptJweCommand(),
                    credentialResponseEncryptor,
                    fakeConfigProvider,
                )
            val notificationCommand = HandleNotificationEndpointCommandImpl(execution, FakeHandleNotificationCommand(), fakeConfigProvider)
            val contributeAttributesCommand = ContributeAttributesEndpointCommandImpl(execution, contributeAttributesCommand = null)
            val evaluateCompletenessCommand = EvaluateCompletenessEndpointCommandImpl(execution, evaluateAttributeCompletenessCommand = null)

            val protocolAdapter =
                Oid4vciIssuerProtocolHttpAdapter(
                    execution,
                    NoOpRoutableSlugLookup(),
                    testTenantIdProvider(),
                    credentialOfferCommand,
                    nonceCommand,
                    credentialCommand,
                    deferredCommand,
                    notificationCommand,
                    contributeAttributesCommand,
                    command,
                    evaluateCompletenessCommand,
                    GetSessionAttributesEndpointCommandImpl(execution, getSessionAttributesCommand = null),
                    ContributeViaCallbackEndpointCommandImpl(execution, callbackTokenService = null, contributeAttributesCommand = null, callbackCoordinator = null),
                    FailPipelineSourceEndpointCommandImpl(execution, failPipelineSourceCommand = null),
                    ApprovePipelineSessionEndpointCommandImpl(execution, approvePipelineSessionCommand = null),
                    GetVctTypeMetadataEndpointCommandImpl(execution, NoOpVctTypeMetadataProvider),
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vci/sessions",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = {
                        """{"pipeline_configuration":{"pipelineId":"pipe-integrated"}}"""
                    },
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            val capturedArgs = fakeInit.lastArgs
            assertNotNull(capturedArgs)
            assertEquals("pipe-integrated", capturedArgs.pipelineConfiguration.pipelineId)
        }
}

internal class FakeInitPipelineSessionCommand : InitPipelineSessionCommand {
    var result: IdkResult<InitPipelineSessionResult, IdkError> =
        Err(IdkError.UNKNOWN_ERROR(message = "Not configured"))
    var lastArgs: InitPipelineSessionArgs? = null

    override val commandId: String get() = InitPipelineSessionCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean get() = true
    override val inputTypeToken: TypeToken<InitPipelineSessionArgs> = typeToken()
    override val outputTypeToken: TypeToken<InitPipelineSessionResult> = typeToken()

    override suspend fun execute(args: InitPipelineSessionArgs): IdkResult<InitPipelineSessionResult, IdkError> {
        lastArgs = args
        return result
    }
}
