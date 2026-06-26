/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import com.sphereon.credential.issuance.pipeline.IssuancePipelineStatus
import com.sphereon.credential.issuance.pipeline.command.ApprovalDecision
import com.sphereon.credential.issuance.pipeline.command.ApprovePipelineSessionArgs
import com.sphereon.credential.issuance.pipeline.command.ApprovePipelineSessionCommand
import com.sphereon.credential.issuance.pipeline.command.ApprovePipelineSessionResult
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
import com.sphereon.openid.oid4vci.issuer.impl.http.testTenantIdProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ApprovePipelineSessionEndpointCommandTest {
    private val execution = TestSessionExecution()
    private val fakeApprove = FakeApprovePipelineSessionCommand()
    private val command = ApprovePipelineSessionEndpointCommandImpl(execution, fakeApprove)

    // ========================================================================
    // Descriptor
    // ========================================================================

    @Test
    fun endpointDescriptorHasCorrectMethodAndPath() {
        val endpoint = ApprovePipelineSessionEndpointCommand.ENDPOINT
        assertEquals(HttpMethod.POST, endpoint.method)
        assertEquals("/backend/sessions/{correlationId}/approve", endpoint.pathPattern)
    }

    @Test
    fun commandIdMatchesExpected() {
        assertEquals("oid4vci.protocol.approve-pipeline-session", ApprovePipelineSessionEndpointCommand.COMMAND_ID)
        assertEquals(ApprovePipelineSessionEndpointCommand.COMMAND_ID, command.id)
    }

    // ========================================================================
    // doExecute delegates to service command with path-extracted correlationId
    // and body-supplied decision / reason / evidence.
    //
    // Sanity: ApprovePipelineSessionArgs has no `approver` field; this is a
    // compile-level guarantee — if someone adds `approver` the test that checks
    // capturedArgs.decision would need updating but the field absence is implicit.
    // ========================================================================

    @Test
    fun delegatesWithCorrelationIdFromPathAndDecisionFromBody() =
        runTest {
            fakeApprove.result =
                Ok(
                    ApprovePipelineSessionResult(
                        correlationId = "corr-123",
                        status = IssuancePipelineStatus.READY,
                    ),
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/backend/sessions/corr-123/approve",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = { """{"decision":"APPROVE"}""" },
                )

            val result = command.execute(request)

            assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
            assertEquals(200, result.value.statusCode)

            val capturedArgs = fakeApprove.lastArgs
            assertNotNull(capturedArgs)
            assertEquals("corr-123", capturedArgs.correlationId)
            assertEquals(ApprovalDecision.APPROVE, capturedArgs.decision)
            assertNull(capturedArgs.reason)
            assertNull(capturedArgs.evidence)
        }

    @Test
    fun delegatesRejectWithReasonFromBody() =
        runTest {
            fakeApprove.result =
                Ok(
                    ApprovePipelineSessionResult(
                        correlationId = "corr-456",
                        status = IssuancePipelineStatus.FAILED,
                    ),
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/backend/sessions/corr-456/approve",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = { """{"decision":"REJECT","reason":"Document expired"}""" },
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            val capturedArgs = fakeApprove.lastArgs
            assertNotNull(capturedArgs)
            assertEquals("corr-456", capturedArgs.correlationId)
            assertEquals(ApprovalDecision.REJECT, capturedArgs.decision)
            assertEquals("Document expired", capturedArgs.reason)
            assertNull(capturedArgs.evidence)
        }

    @Test
    fun approverIsNotOnArgsDataClass() =
        runTest {
            // Compile-level check: ApprovePipelineSessionArgs fields are
            // correlationId, decision, reason, evidence — no `approver`.
            // Constructing the args below would fail to compile if `approver` existed
            // as a required field; its absence is therefore proven by a clean build.
            fakeApprove.result =
                Ok(
                    ApprovePipelineSessionResult(
                        correlationId = "corr-sanity",
                        status = IssuancePipelineStatus.READY,
                    ),
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/backend/sessions/corr-sanity/approve",
                    bodySupplier = { """{"decision":"APPROVE"}""" },
                )

            command.execute(request)

            val capturedArgs = fakeApprove.lastArgs
            assertNotNull(capturedArgs)
            // The only fields on ApprovePipelineSessionArgs are the four below.
            assertEquals("corr-sanity", capturedArgs.correlationId)
            assertFalse(capturedArgs::class.members.any { it.name == "approver" })
        }

    @Test
    fun returns200OnSuccess() =
        runTest {
            fakeApprove.result =
                Ok(
                    ApprovePipelineSessionResult(
                        correlationId = "corr-789",
                        status = IssuancePipelineStatus.READY,
                    ),
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/backend/sessions/corr-789/approve",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = { """{"decision":"APPROVE"}""" },
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
            fakeApprove.result = Err(IdkError.NOT_FOUND_ERROR(message = "Pipeline session not found"))

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/backend/sessions/unknown/approve",
                    bodySupplier = { """{"decision":"APPROVE"}""" },
                )

            val result = command.execute(request)

            assertTrue(result.isErr)
            assertNotNull(result.error)
        }

    @Test
    fun returnsErrWhenNoPipelineIsBound() =
        runTest {
            val commandNoPipeline = ApprovePipelineSessionEndpointCommandImpl(execution, approvePipelineSessionCommand = null)

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/backend/sessions/corr-123/approve",
                    bodySupplier = { """{"decision":"APPROVE"}""" },
                )

            val result = commandNoPipeline.execute(request)

            assertTrue(result.isErr)
            assertNotNull(result.error)
        }

    @Test
    fun returnsErrOnMalformedBody() =
        runTest {
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/backend/sessions/corr-bad/approve",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = { "not-valid-json{{" },
                )

            val result = command.execute(request)

            assertTrue(result.isErr)
            assertNull(fakeApprove.lastArgs)
        }

    @Test
    fun returnsErrWhenCorrelationIdAbsent() =
        runTest {
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/",
                    bodySupplier = { """{"decision":"APPROVE"}""" },
                )

            val result = command.execute(request)

            assertTrue(result.isErr)
            assertNull(fakeApprove.lastArgs)
        }

    // ========================================================================
    // Adapter-level integration: route is reachable via the protocol adapter.
    // Uses the full /oid4vci/... path; the adapter strips the prefix before
    // routing to the endpoint command.
    // ========================================================================

    @Test
    fun protocolAdapterRoutesPostSessionsApproveToEndpointCommand() =
        runTest {
            fakeApprove.result =
                Ok(
                    ApprovePipelineSessionResult(
                        correlationId = "integrated-corr",
                        status = IssuancePipelineStatus.READY,
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
                    com.sphereon.openid.oid4vci.issuer.impl.http.NoOpAppConfigService,
                    credentialOfferCommand,
                    nonceCommand,
                    credentialCommand,
                    deferredCommand,
                    notificationCommand,
                    ContributeAttributesEndpointCommandImpl(execution, contributeAttributesCommand = null),
                    InitPipelineSessionEndpointCommandImpl(execution, initPipelineSessionCommand = null),
                    EvaluateCompletenessEndpointCommandImpl(execution, evaluateAttributeCompletenessCommand = null),
                    GetSessionAttributesEndpointCommandImpl(execution, getSessionAttributesCommand = null),
                    ContributeViaCallbackEndpointCommandImpl(execution, callbackTokenService = null, contributeAttributesCommand = null, callbackCoordinator = null),
                    FailPipelineSourceEndpointCommandImpl(execution, failPipelineSourceCommand = null),
                    command,
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vci/backend/sessions/integrated-corr/approve",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = { """{"decision":"APPROVE"}""" },
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            val capturedArgs = fakeApprove.lastArgs
            assertNotNull(capturedArgs)
            assertEquals("integrated-corr", capturedArgs.correlationId)
            assertEquals(ApprovalDecision.APPROVE, capturedArgs.decision)
        }
}

internal class FakeApprovePipelineSessionCommand : ApprovePipelineSessionCommand {
    var result: IdkResult<ApprovePipelineSessionResult, IdkError> =
        Err(IdkError.UNKNOWN_ERROR(message = "Not configured"))
    var lastArgs: ApprovePipelineSessionArgs? = null

    override val commandId: String get() = ApprovePipelineSessionCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean get() = true
    override val inputTypeToken: TypeToken<ApprovePipelineSessionArgs> = typeToken()
    override val outputTypeToken: TypeToken<ApprovePipelineSessionResult> = typeToken()

    override suspend fun execute(args: ApprovePipelineSessionArgs): IdkResult<ApprovePipelineSessionResult, IdkError> {
        lastArgs = args
        return result
    }
}
