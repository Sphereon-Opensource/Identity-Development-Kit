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
import com.sphereon.credential.issuance.pipeline.command.FailPipelineSourceArgs
import com.sphereon.credential.issuance.pipeline.command.FailPipelineSourceCommand
import com.sphereon.credential.issuance.pipeline.command.FailPipelineSourceResult
import com.sphereon.openid.oid4vci.issuer.impl.http.TestSessionExecution
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FailPipelineSourceEndpointCommandTest {
    private val execution = TestSessionExecution()
    private val fakeFailSource = FakeFailPipelineSourceCommand()
    private val command = FailPipelineSourceEndpointCommandImpl(execution, fakeFailSource)

    // ========================================================================
    // Descriptor
    // ========================================================================

    @Test
    fun endpointDescriptorHasCorrectMethodAndPath() {
        val endpoint = FailPipelineSourceEndpointCommand.ENDPOINT
        assertEquals(HttpMethod.POST, endpoint.method)
        assertEquals("/sessions/{correlationId}/fail", endpoint.pathPattern)
    }

    @Test
    fun commandIdMatchesExpected() {
        assertEquals("oid4vci.protocol.fail-pipeline-source", FailPipelineSourceEndpointCommand.COMMAND_ID)
        assertEquals(FailPipelineSourceEndpointCommand.COMMAND_ID, command.id)
    }

    // ========================================================================
    // doExecute delegates to service command with path-extracted correlationId
    // and body-supplied sourceId / reason.
    // ========================================================================

    @Test
    fun delegatesWithCorrelationIdFromPathAndSourceIdFromBody() =
        runTest {
            fakeFailSource.result = Ok(FailPipelineSourceResult(correlationId = "corr-7", sourceId = "src-A"))

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions/corr-7/fail",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = { """{"source_id":"src-A","reason":"Upstream 502"}""" },
                )

            val result = command.execute(request)

            assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
            assertEquals(200, result.value.statusCode)
            val capturedArgs = fakeFailSource.lastArgs
            assertNotNull(capturedArgs)
            assertEquals("corr-7", capturedArgs.correlationId)
            assertEquals("src-A", capturedArgs.sourceId)
            assertEquals("Upstream 502", capturedArgs.reason)
        }

    @Test
    fun reasonIsOptional() =
        runTest {
            fakeFailSource.result = Ok(FailPipelineSourceResult(correlationId = "corr-8", sourceId = "src-B"))

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions/corr-8/fail",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = { """{"source_id":"src-B"}""" },
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            val capturedArgs = fakeFailSource.lastArgs
            assertNotNull(capturedArgs)
            assertNull(capturedArgs.reason)
        }

    // ========================================================================
    // Error paths
    // ========================================================================

    @Test
    fun propagatesServiceCommandError() =
        runTest {
            fakeFailSource.result = Err(IdkError.NOT_FOUND_ERROR(message = "Pipeline session not found"))

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions/unknown/fail",
                    bodySupplier = { """{"source_id":"src-A"}""" },
                )

            val result = command.execute(request)

            assertTrue(result.isErr)
        }

    @Test
    fun returnsErrWhenNoPipelineIsBound() =
        runTest {
            val commandNoPipeline = FailPipelineSourceEndpointCommandImpl(execution, failPipelineSourceCommand = null)

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions/corr-1/fail",
                    bodySupplier = { """{"source_id":"src-A"}""" },
                )

            val result = commandNoPipeline.execute(request)
            assertTrue(result.isErr)
        }

    @Test
    fun returnsErrOnMalformedBody() =
        runTest {
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions/corr-bad/fail",
                    bodySupplier = { "not-json{{" },
                )

            val result = command.execute(request)
            assertTrue(result.isErr)
            assertNull(fakeFailSource.lastArgs)
        }

    @Test
    fun returnsErrWhenSourceIdMissingFromBody() =
        runTest {
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions/corr-1/fail",
                    bodySupplier = { """{}""" },
                )

            val result = command.execute(request)
            // Missing required `source_id` causes the body decoder to fail; the service command
            // must not be invoked when the request cannot be parsed.
            assertTrue(result.isErr)
            assertNull(fakeFailSource.lastArgs)
        }

    @Test
    fun returnsErrWhenCorrelationIdAbsent() =
        runTest {
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/",
                    bodySupplier = { """{"source_id":"src-A"}""" },
                )

            val result = command.execute(request)
            assertTrue(result.isErr)
            assertNull(fakeFailSource.lastArgs)
        }
}

internal class FakeFailPipelineSourceCommand : FailPipelineSourceCommand {
    var result: IdkResult<FailPipelineSourceResult, IdkError> =
        Err(IdkError.UNKNOWN_ERROR(message = "Not configured"))
    var lastArgs: FailPipelineSourceArgs? = null

    override val commandId: String get() = FailPipelineSourceCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean get() = true
    override val inputTypeToken: TypeToken<FailPipelineSourceArgs> = typeToken()
    override val outputTypeToken: TypeToken<FailPipelineSourceResult> = typeToken()

    override suspend fun execute(args: FailPipelineSourceArgs): IdkResult<FailPipelineSourceResult, IdkError> {
        lastArgs = args
        return result
    }
}
