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
import com.sphereon.credential.issuance.pipeline.command.GetSessionAttributesArgs
import com.sphereon.credential.issuance.pipeline.command.GetSessionAttributesCommand
import com.sphereon.credential.issuance.pipeline.command.GetSessionAttributesResult
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
import com.sphereon.openid.oid4vci.issuer.impl.http.testTenantIdProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class GetSessionAttributesEndpointCommandTest {
    private val execution = TestSessionExecution()
    private val fakeGetAttributes = FakeGetSessionAttributesCommand()
    private val command = GetSessionAttributesEndpointCommandImpl(execution, fakeGetAttributes)

    // ========================================================================
    // Descriptor
    // ========================================================================

    @Test
    fun endpointDescriptorHasCorrectMethodAndPath() {
        val endpoint = GetSessionAttributesEndpointCommand.ENDPOINT
        assertEquals(HttpMethod.GET, endpoint.method)
        assertEquals("/sessions/{correlationId}/attributes", endpoint.pathPattern)
    }

    @Test
    fun commandIdMatchesExpected() {
        assertEquals("oid4vci.protocol.get-session-attributes", GetSessionAttributesEndpointCommand.COMMAND_ID)
        assertEquals(GetSessionAttributesEndpointCommand.COMMAND_ID, command.id)
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
            fakeGetAttributes.result =
                Ok(
                    GetSessionAttributesResult(
                        attributes = mapOf("given_name" to JsonPrimitive("Jane")),
                        promotedLookupKeyNames = listOf("email"),
                    ),
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/sessions/corr-123/attributes",
                    headers = emptyMap(),
                    bodySupplier = { null },
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertNotNull(result.value.body)

            val capturedArgs = fakeGetAttributes.lastArgs
            assertNotNull(capturedArgs)
            assertEquals("corr-123", capturedArgs.correlationId)
        }

    @Test
    fun returns200OnSuccess() =
        runTest {
            fakeGetAttributes.result =
                Ok(
                    GetSessionAttributesResult(
                        attributes = emptyMap(),
                        promotedLookupKeyNames = emptyList(),
                    ),
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/sessions/corr-456/attributes",
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
            fakeGetAttributes.result =
                Err(IdkError.NOT_FOUND_ERROR(message = "Pipeline session not found"))

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/sessions/unknown/attributes",
                    bodySupplier = { null },
                )

            val result = command.execute(request)

            assertTrue(result.isErr)
            assertNotNull(result.error)
        }

    @Test
    fun returnsErrWhenNoPipelineIsBound() =
        runTest {
            val commandWithoutPipeline = GetSessionAttributesEndpointCommandImpl(execution, getSessionAttributesCommand = null)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/sessions/corr-123/attributes",
                    bodySupplier = { null },
                )

            val result = commandWithoutPipeline.execute(request)

            assertTrue(result.isErr)
            assertNotNull(result.error)
        }

    @Test
    fun returnsErrorWhenCorrelationIdAbsent() =
        runTest {
            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/",
                    bodySupplier = { null },
                )

            val result = command.execute(request)

            assertTrue(result.isErr)
            assertNull(fakeGetAttributes.lastArgs)
        }

    // ========================================================================
    // Adapter-level integration: route is reachable via the protocol adapter.
    // Uses the full /oid4vci/... path; the adapter strips the prefix before
    // routing to the endpoint command.
    // ========================================================================

    @Test
    fun protocolAdapterRoutesGetSessionAttributesToEndpointCommand() =
        runTest {
            fakeGetAttributes.result =
                Ok(
                    GetSessionAttributesResult(
                        attributes = mapOf("family_name" to JsonPrimitive("Doe")),
                        promotedLookupKeyNames = listOf("email"),
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
                    initSessionCommand,
                    evaluateCompletenessCommand,
                    command,
                    ContributeViaCallbackEndpointCommandImpl(execution, callbackTokenService = null, contributeAttributesCommand = null, callbackCoordinator = null),
                    FailPipelineSourceEndpointCommandImpl(execution, failPipelineSourceCommand = null),
                    ApprovePipelineSessionEndpointCommandImpl(execution, approvePipelineSessionCommand = null),
                    GetVctTypeMetadataEndpointCommandImpl(execution, NoOpVctTypeMetadataProvider),
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vci/sessions/integrated-corr/attributes",
                    headers = emptyMap(),
                    bodySupplier = { null },
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            val capturedArgs = fakeGetAttributes.lastArgs
            assertNotNull(capturedArgs)
            assertEquals("integrated-corr", capturedArgs.correlationId)
        }
}

internal class FakeGetSessionAttributesCommand : GetSessionAttributesCommand {
    var result: IdkResult<GetSessionAttributesResult, IdkError> =
        Err(IdkError.UNKNOWN_ERROR(message = "Not configured"))
    var lastArgs: GetSessionAttributesArgs? = null

    override val commandId: String get() = GetSessionAttributesCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean get() = true
    override val inputTypeToken: TypeToken<GetSessionAttributesArgs> = typeToken()
    override val outputTypeToken: TypeToken<GetSessionAttributesResult> = typeToken()

    override suspend fun execute(args: GetSessionAttributesArgs): IdkResult<GetSessionAttributesResult, IdkError> {
        lastArgs = args
        return result
    }
}
