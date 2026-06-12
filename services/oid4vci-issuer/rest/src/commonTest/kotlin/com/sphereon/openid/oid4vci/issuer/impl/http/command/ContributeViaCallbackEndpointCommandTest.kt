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

import com.sphereon.attribute.pipeline.Oid4vciPipelinePhase
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.credential.issuance.pipeline.IssuancePipelineStatus
import com.sphereon.credential.issuance.pipeline.callback.CallbackCoordinator
import com.sphereon.credential.issuance.pipeline.callback.CallbackTokenClaims
import com.sphereon.credential.issuance.pipeline.callback.CallbackTokenService
import com.sphereon.credential.issuance.pipeline.command.ContributeAttributesResult
import com.sphereon.openid.oid4vci.issuer.impl.http.TestSessionExecution
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class ContributeViaCallbackEndpointCommandTest {
    private val execution = TestSessionExecution()
    private val fakeContribute = FakeContributeAttributesCommand()
    private val fakeTokenService = FakeCallbackTokenService()
    private val fakeCoordinator = RecordingCallbackCoordinator()
    private val command =
        ContributeViaCallbackEndpointCommandImpl(
            execution,
            callbackTokenService = fakeTokenService,
            contributeAttributesCommand = fakeContribute,
            callbackCoordinator = fakeCoordinator,
        )

    // ========================================================================
    // Descriptor
    // ========================================================================

    @Test
    fun endpointDescriptorHasCorrectMethodAndPath() {
        val endpoint = ContributeViaCallbackEndpointCommand.ENDPOINT
        assertEquals(HttpMethod.POST, endpoint.method)
        assertEquals("/backend/sessions/{correlationId}/callbacks/{callbackToken}", endpoint.pathPattern)
    }

    @Test
    fun commandIdMatchesExpected() {
        assertEquals("oid4vci.protocol.contribute-via-callback", ContributeViaCallbackEndpointCommand.COMMAND_ID)
        assertEquals(ContributeViaCallbackEndpointCommand.COMMAND_ID, command.id)
    }

    // ========================================================================
    // Happy path: token validates, claims match, body parsed, service called
    // with DEFERRED phase, coordinator notified.
    // ========================================================================

    @Test
    fun delegatesToContributeWithDeferredPhaseAndCorrelationIdFromPath() =
        runTest {
            fakeTokenService.validateResponse =
                Ok(CallbackTokenClaims(correlationId = "corr-123", sourceId = "src-A", exp = FAR_FUTURE_EXP))
            fakeContribute.result =
                Ok(
                    ContributeAttributesResult(
                        sessionId = "session-abc",
                        status = IssuancePipelineStatus.PHASE_COMPLETED,
                        completedPhases = setOf(Oid4vciPipelinePhase.DEFERRED),
                    ),
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/backend/sessions/corr-123/callbacks/token.value.here",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = { """{"attributes":[],"lookup_keys":[]}""" },
                )

            val result = command.execute(request)

            assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
            assertEquals(200, result.value.statusCode)
            assertEquals("token.value.here", fakeTokenService.lastToken)
            val capturedArgs = fakeContribute.lastArgs
            assertNotNull(capturedArgs)
            assertEquals("corr-123", capturedArgs.correlationId)
            assertEquals(Oid4vciPipelinePhase.DEFERRED, capturedArgs.phase)
            assertEquals("corr-123", fakeCoordinator.lastNotified?.first)
            assertEquals("src-A", fakeCoordinator.lastNotified?.second)
        }

    @Test
    fun coordinatorNotNotifiedWhenContributeFails() =
        runTest {
            fakeTokenService.validateResponse =
                Ok(CallbackTokenClaims(correlationId = "corr-X", sourceId = "src-A", exp = FAR_FUTURE_EXP))
            fakeContribute.result = Err(IdkError.NOT_FOUND_ERROR(message = "Session gone"))

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/backend/sessions/corr-X/callbacks/tok",
                    bodySupplier = { """{}""" },
                )

            val result = command.execute(request)

            assertTrue(result.isErr)
            assertNull(fakeCoordinator.lastNotified, "coordinator must not be notified when contribute errors")
        }

    // ========================================================================
    // Token validation failure / mismatch
    // ========================================================================

    @Test
    fun returnsErrWhenTokenValidationFails() =
        runTest {
            fakeTokenService.validateResponse = Err(IdkError.UNAUTHORIZED_ERROR(message = "Invalid callback token"))

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/backend/sessions/corr-1/callbacks/bad-token",
                    bodySupplier = { """{}""" },
                )

            val result = command.execute(request)

            assertTrue(result.isErr)
            assertEquals("UNAUTHORIZED", result.error.code)
            assertNull(fakeContribute.lastArgs, "contribute must not run when the token is invalid")
            assertNull(fakeCoordinator.lastNotified)
        }

    @Test
    fun returnsErrWhenClaimsCorrelationIdDiffersFromPath() =
        runTest {
            fakeTokenService.validateResponse =
                Ok(CallbackTokenClaims(correlationId = "corr-other", sourceId = "src-A", exp = FAR_FUTURE_EXP))

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/backend/sessions/corr-1/callbacks/tok",
                    bodySupplier = { """{}""" },
                )

            val result = command.execute(request)

            assertTrue(result.isErr)
            assertEquals("UNAUTHORIZED", result.error.code)
            assertNull(fakeContribute.lastArgs)
            assertNull(fakeCoordinator.lastNotified)
        }

    // ========================================================================
    // Null-guard / nullable-injection
    // ========================================================================

    @Test
    fun returnsErrWhenCallbackTokenServiceIsAbsent() =
        runTest {
            val commandNoTokenService =
                ContributeViaCallbackEndpointCommandImpl(
                    execution,
                    callbackTokenService = null,
                    contributeAttributesCommand = fakeContribute,
                    callbackCoordinator = null,
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/backend/sessions/corr-1/callbacks/tok",
                    bodySupplier = { """{}""" },
                )

            val result = commandNoTokenService.execute(request)
            assertTrue(result.isErr)
        }

    @Test
    fun returnsErrWhenContributeCommandIsAbsent() =
        runTest {
            val commandNoContribute =
                ContributeViaCallbackEndpointCommandImpl(
                    execution,
                    callbackTokenService = fakeTokenService,
                    contributeAttributesCommand = null,
                    callbackCoordinator = null,
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/backend/sessions/corr-1/callbacks/tok",
                    bodySupplier = { """{}""" },
                )

            val result = commandNoContribute.execute(request)
            assertTrue(result.isErr)
        }

    @Test
    fun coordinatorAbsenceIsTolerated() =
        runTest {
            val commandNoCoordinator =
                ContributeViaCallbackEndpointCommandImpl(
                    execution,
                    callbackTokenService = fakeTokenService,
                    contributeAttributesCommand = fakeContribute,
                    callbackCoordinator = null,
                )
            fakeTokenService.validateResponse =
                Ok(CallbackTokenClaims(correlationId = "corr-z", sourceId = "src-z", exp = FAR_FUTURE_EXP))
            fakeContribute.result =
                Ok(
                    ContributeAttributesResult(
                        sessionId = "session-z",
                        status = IssuancePipelineStatus.PHASE_COMPLETED,
                        completedPhases = setOf(Oid4vciPipelinePhase.DEFERRED),
                    ),
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/backend/sessions/corr-z/callbacks/tok",
                    bodySupplier = { """{}""" },
                )

            val result = commandNoCoordinator.execute(request)
            assertTrue(result.isOk)
        }

    // ========================================================================
    // Malformed body / missing path params
    // ========================================================================

    @Test
    fun returnsErrOnMalformedBody() =
        runTest {
            fakeTokenService.validateResponse =
                Ok(CallbackTokenClaims(correlationId = "corr-1", sourceId = "src-A", exp = FAR_FUTURE_EXP))

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/backend/sessions/corr-1/callbacks/tok",
                    bodySupplier = { "not-json{{" },
                )

            val result = command.execute(request)
            assertTrue(result.isErr)
            assertNull(fakeContribute.lastArgs)
        }

    @Test
    fun returnsErrWhenPathParamsAreMissing() =
        runTest {
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/",
                    bodySupplier = { """{}""" },
                )

            val result = command.execute(request)
            assertTrue(result.isErr)
            assertNull(fakeTokenService.lastToken)
            assertNull(fakeContribute.lastArgs)
        }

    private companion object {
        const val FAR_FUTURE_EXP: Long = 9_999_999_999L
    }
}

internal class FakeCallbackTokenService : CallbackTokenService {
    var validateResponse: IdkResult<CallbackTokenClaims, IdkError> =
        Err(IdkError.UNKNOWN_ERROR(message = "Not configured"))
    var lastToken: String? = null

    override suspend fun mint(
        correlationId: String,
        sourceId: String,
        ttl: Duration,
    ): IdkResult<com.sphereon.credential.issuance.pipeline.callback.CallbackToken, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "mint is not exercised by these tests"))

    override suspend fun validate(token: String): IdkResult<CallbackTokenClaims, IdkError> {
        lastToken = token
        return validateResponse
    }
}

internal class RecordingCallbackCoordinator : CallbackCoordinator {
    var lastNotified: Pair<String, String>? = null

    override suspend fun notifyContribution(
        correlationId: String,
        sourceId: String,
    ) {
        lastNotified = correlationId to sourceId
    }

    override suspend fun awaitContribution(
        correlationId: String,
        sourceId: String,
    ) {
        // The endpoint tests never call awaitContribution; in-process coordination has its own
        // dedicated test suite.
    }
}
