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

import com.sphereon.attribute.pipeline.Oid4vciPipelinePhase
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.defaults.http.NoOpRoutableSlugLookup
import com.sphereon.credential.issuance.pipeline.IssuancePipelineStatus
import com.sphereon.credential.issuance.pipeline.command.ContributeAttributesArgs
import com.sphereon.credential.issuance.pipeline.command.ContributeAttributesCommand
import com.sphereon.credential.issuance.pipeline.command.ContributeAttributesResult
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

class ContributeAttributesEndpointCommandTest {
    private val execution = TestSessionExecution()
    private val fakeContribute = FakeContributeAttributesCommand()
    private val command = ContributeAttributesEndpointCommandImpl(execution, fakeContribute)

    // ========================================================================
    // Descriptor
    // ========================================================================

    @Test
    fun endpointDescriptorHasCorrectMethodAndPath() {
        val endpoint = ContributeAttributesEndpointCommand.ENDPOINT
        assertEquals(HttpMethod.POST, endpoint.method)
        assertEquals("/sessions/{correlationId}/attributes", endpoint.pathPattern)
    }

    @Test
    fun commandIdMatchesExpected() {
        assertEquals("oid4vci.protocol.contribute-attributes", ContributeAttributesEndpointCommand.COMMAND_ID)
        assertEquals(ContributeAttributesEndpointCommand.COMMAND_ID, command.id)
    }

    // ========================================================================
    // doExecute delegates to service command with path-extracted correlationId
    //
    // In a real request, the CommandBackedHttpAdapter strips the /oid4vci prefix
    // before routing. The direct command tests use the adapter-relative path
    // /sessions/{id}/attributes so withExtractedParams can extract the param.
    // ========================================================================

    @Test
    fun delegatesToServiceCommandWithCorrelationIdFromPath() =
        runTest {
            fakeContribute.result =
                Ok(
                    ContributeAttributesResult(
                        sessionId = "session-abc",
                        status = IssuancePipelineStatus.PHASE_COMPLETED,
                        completedPhases = setOf(Oid4vciPipelinePhase.CREDENTIAL_REQUEST),
                    ),
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions/corr-123/attributes",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = { """{"attributes":[],"lookup_keys":[]}""" },
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertNotNull(result.value.body)

            val capturedArgs = fakeContribute.lastArgs
            assertNotNull(capturedArgs)
            assertEquals("corr-123", capturedArgs.correlationId)
            assertEquals(Oid4vciPipelinePhase.CREDENTIAL_REQUEST, capturedArgs.phase)
            assertTrue(capturedArgs.attributes.isEmpty())
            assertTrue(capturedArgs.lookupKeys.isEmpty())
        }

    @Test
    fun returns200OnSuccess() =
        runTest {
            fakeContribute.result =
                Ok(
                    ContributeAttributesResult(
                        sessionId = "session-xyz",
                        status = IssuancePipelineStatus.READY,
                        completedPhases = setOf(Oid4vciPipelinePhase.CREDENTIAL_REQUEST),
                    ),
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions/corr-456/attributes",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = { """{}""" },
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertEquals("application/json", result.value.headers["Content-Type"])
            assertNotNull(result.value.body)
        }

    @Test
    fun phaseRemainsCredentialRequestWithNonEmptyBody() =
        runTest {
            fakeContribute.result =
                Ok(
                    ContributeAttributesResult(
                        sessionId = "session-phase",
                        status = IssuancePipelineStatus.PHASE_COMPLETED,
                        completedPhases = setOf(Oid4vciPipelinePhase.CREDENTIAL_REQUEST),
                    ),
                )

            // Body carries non-empty attributes and lookup_keys lists;
            // phase on the ContributeAttributesArgs must always be CREDENTIAL_REQUEST
            // regardless of what the body contains.
            val attributeJson =
                """{"path":"given_name","value":{"type":"data","value":"Alice"},""" +
                    """"sourceId":"test-source","phase":{"value":"session_init"},""" +
                    """"timestamp":"2026-05-15T12:00:00Z"}"""
            val lookupKeyJson =
                """{"name":"email","value":"alice@example.com","producedBy":"test-source",""" +
                    """"phase":{"value":"session_init"},"timestamp":"2026-05-15T12:00:00Z"}"""
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions/session-phase/attributes",
                    bodySupplier = {
                        """{"attributes":[$attributeJson],"lookup_keys":[$lookupKeyJson]}"""
                    },
                )

            command.execute(request)

            val capturedArgs = fakeContribute.lastArgs
            assertNotNull(capturedArgs)
            assertEquals(Oid4vciPipelinePhase.CREDENTIAL_REQUEST, capturedArgs.phase)
            assertTrue(capturedArgs.attributes.isNotEmpty())
            assertTrue(capturedArgs.lookupKeys.isNotEmpty())
        }

    // ========================================================================
    // Error paths
    // ========================================================================

    @Test
    fun propagatesServiceCommandError() =
        runTest {
            fakeContribute.result =
                Err(IdkError.NOT_FOUND_ERROR(message = "Pipeline session not found"))

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions/unknown/attributes",
                    bodySupplier = { """{}""" },
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
                    path = "/sessions/corr-bad/attributes",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = { "not-valid-json{{" },
                )

            val result = command.execute(request)

            assertTrue(result.isErr)
            assertNull(fakeContribute.lastArgs)
        }

    @Test
    fun returnsErrWhenNoPipelineIsBound() =
        runTest {
            val commandWithoutPipeline = ContributeAttributesEndpointCommandImpl(execution, contributeAttributesCommand = null)

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions/corr-123/attributes",
                    bodySupplier = { """{}""" },
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
                    method = "POST",
                    path = "/",
                    bodySupplier = { """{}""" },
                )

            val result = command.execute(request)

            assertTrue(result.isErr)
            assertNull(fakeContribute.lastArgs)
        }

    // ========================================================================
    // Adapter-level integration: route is reachable via the protocol adapter.
    // Uses the full /oid4vci/... path; the adapter strips the prefix before
    // routing to the endpoint command.
    // ========================================================================

    @Test
    fun protocolAdapterRoutesPostSessionsAttributesToEndpointCommand() =
        runTest {
            fakeContribute.result =
                Ok(
                    ContributeAttributesResult(
                        sessionId = "integrated-session",
                        status = IssuancePipelineStatus.PHASE_COMPLETED,
                        completedPhases = setOf(Oid4vciPipelinePhase.CREDENTIAL_REQUEST),
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
                    command,
                    InitPipelineSessionEndpointCommandImpl(execution, initPipelineSessionCommand = null),
                    EvaluateCompletenessEndpointCommandImpl(execution, evaluateAttributeCompletenessCommand = null),
                    GetSessionAttributesEndpointCommandImpl(execution, getSessionAttributesCommand = null),
                    ContributeViaCallbackEndpointCommandImpl(execution, callbackTokenService = null, contributeAttributesCommand = null, callbackCoordinator = null),
                    FailPipelineSourceEndpointCommandImpl(execution, failPipelineSourceCommand = null),
                    ApprovePipelineSessionEndpointCommandImpl(execution, approvePipelineSessionCommand = null),
                    GetVctTypeMetadataEndpointCommandImpl(execution, NoOpVctTypeMetadataProvider),
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vci/sessions/integrated-corr/attributes",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = { """{"attributes":[],"lookup_keys":[]}""" },
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            val capturedArgs = fakeContribute.lastArgs
            assertNotNull(capturedArgs)
            assertEquals("integrated-corr", capturedArgs.correlationId)
        }
}

internal class FakeContributeAttributesCommand : ContributeAttributesCommand {
    var result: IdkResult<ContributeAttributesResult, IdkError> =
        Err(IdkError.UNKNOWN_ERROR(message = "Not configured"))
    var lastArgs: ContributeAttributesArgs? = null

    override val commandId: String get() = ContributeAttributesCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean get() = true
    override val inputTypeToken: TypeToken<ContributeAttributesArgs> = typeToken()
    override val outputTypeToken: TypeToken<ContributeAttributesResult> = typeToken()

    override suspend fun execute(args: ContributeAttributesArgs): IdkResult<ContributeAttributesResult, IdkError> {
        lastArgs = args
        return result
    }
}
