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
import com.sphereon.credential.issuance.pipeline.command.BindingCompletenessVerdict
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessArgs
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessCommand
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessResult
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

class EvaluateCompletenessEndpointCommandTest {
    private val execution = TestSessionExecution()
    private val fakeEvaluate = FakeEvaluateAttributeCompletenessCommand()
    private val command = EvaluateCompletenessEndpointCommandImpl(execution, fakeEvaluate)

    // ========================================================================
    // Descriptor
    // ========================================================================

    @Test
    fun endpointDescriptorHasCorrectMethodAndPath() {
        val endpoint = EvaluateCompletenessEndpointCommand.ENDPOINT
        assertEquals(HttpMethod.GET, endpoint.method)
        assertEquals("/backend/sessions/{correlationId}/completeness", endpoint.pathPattern)
    }

    @Test
    fun commandIdMatchesExpected() {
        assertEquals("oid4vci.protocol.evaluate-completeness", EvaluateCompletenessEndpointCommand.COMMAND_ID)
        assertEquals(EvaluateCompletenessEndpointCommand.COMMAND_ID, command.id)
    }

    // ========================================================================
    // doExecute delegates to service command with path-extracted correlationId
    //
    // In a real request, the CommandBackedHttpAdapter strips the /oid4vci prefix
    // before routing. The direct command tests use the adapter-relative path
    // /sessions/{id}/completeness so withExtractedParams can extract the param.
    // ========================================================================

    @Test
    fun delegatesToServiceCommandWithCorrelationIdFromPath() =
        runTest {
            fakeEvaluate.result =
                Ok(
                    EvaluateAttributeCompletenessResult(
                        verdicts =
                            listOf(
                                BindingCompletenessVerdict(bindingId = "binding-1", complete = true),
                            ),
                    ),
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/backend/sessions/corr-123/completeness",
                    headers = emptyMap(),
                    bodySupplier = { null },
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertNotNull(result.value.body)

            val capturedArgs = fakeEvaluate.lastArgs
            assertNotNull(capturedArgs)
            assertEquals("corr-123", capturedArgs.correlationId)
        }

    @Test
    fun returns200OnSuccess() =
        runTest {
            fakeEvaluate.result =
                Ok(
                    EvaluateAttributeCompletenessResult(
                        verdicts = emptyList(),
                    ),
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/backend/sessions/corr-456/completeness",
                    headers = emptyMap(),
                    bodySupplier = { null },
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertEquals("application/json", result.value.headers["Content-Type"])
            assertNotNull(result.value.body)
        }

    // ========================================================================
    // Error paths
    // ========================================================================

    @Test
    fun propagatesServiceCommandError() =
        runTest {
            fakeEvaluate.result =
                Err(IdkError.NOT_FOUND_ERROR(message = "Pipeline session not found"))

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/backend/sessions/unknown/completeness",
                    bodySupplier = { null },
                )

            val result = command.execute(request)

            assertTrue(result.isErr)
            assertNotNull(result.error)
        }

    @Test
    fun returnsErrWhenNoPipelineIsBound() =
        runTest {
            val commandWithoutPipeline = EvaluateCompletenessEndpointCommandImpl(execution, evaluateAttributeCompletenessCommand = null)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/backend/sessions/corr-123/completeness",
                    bodySupplier = { null },
                )

            val result = commandWithoutPipeline.execute(request)

            assertTrue(result.isErr)
            assertNotNull(result.error)
        }

    @Test
    fun returnsErrorWhenCorrelationIdAbsent() =
        runTest {
            // An empty path produces no extractable params, so requirePathParam
            // returns an error and the service command is never called.
            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/",
                    bodySupplier = { null },
                )

            val result = command.execute(request)

            assertTrue(result.isErr)
            assertNull(fakeEvaluate.lastArgs)
        }

    // ========================================================================
    // Adapter-level integration: route is reachable via the protocol adapter.
    // Uses the full /oid4vci/... path; the adapter strips the prefix before
    // routing to the endpoint command.
    // ========================================================================

    @Test
    fun protocolAdapterRoutesGetSessionsCompletenessToEndpointCommand() =
        runTest {
            fakeEvaluate.result =
                Ok(
                    EvaluateAttributeCompletenessResult(
                        verdicts =
                            listOf(
                                BindingCompletenessVerdict(bindingId = "binding-integrated", complete = true),
                            ),
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
            val initSessionCommand = InitPipelineSessionEndpointCommandImpl(execution, initPipelineSessionCommand = null)

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
                    initSessionCommand,
                    command,
                    GetSessionAttributesEndpointCommandImpl(execution, getSessionAttributesCommand = null),
                    ContributeViaCallbackEndpointCommandImpl(execution, callbackTokenService = null, contributeAttributesCommand = null, callbackCoordinator = null),
                    FailPipelineSourceEndpointCommandImpl(execution, failPipelineSourceCommand = null),
                    ApprovePipelineSessionEndpointCommandImpl(execution, approvePipelineSessionCommand = null),
                    GetVctTypeMetadataEndpointCommandImpl(execution, NoOpVctTypeMetadataProvider),
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vci/backend/sessions/integrated-corr/completeness",
                    headers = emptyMap(),
                    bodySupplier = { null },
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            val capturedArgs = fakeEvaluate.lastArgs
            assertNotNull(capturedArgs)
            assertEquals("integrated-corr", capturedArgs.correlationId)
        }
}

internal class FakeEvaluateAttributeCompletenessCommand : EvaluateAttributeCompletenessCommand {
    var result: IdkResult<EvaluateAttributeCompletenessResult, IdkError> =
        Err(IdkError.UNKNOWN_ERROR(message = "Not configured"))
    var lastArgs: EvaluateAttributeCompletenessArgs? = null

    override val commandId: String get() = EvaluateAttributeCompletenessCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean get() = true
    override val inputTypeToken: TypeToken<EvaluateAttributeCompletenessArgs> = typeToken()
    override val outputTypeToken: TypeToken<EvaluateAttributeCompletenessResult> = typeToken()

    override suspend fun execute(args: EvaluateAttributeCompletenessArgs): IdkResult<EvaluateAttributeCompletenessResult, IdkError> {
        lastArgs = args
        return result
    }
}
