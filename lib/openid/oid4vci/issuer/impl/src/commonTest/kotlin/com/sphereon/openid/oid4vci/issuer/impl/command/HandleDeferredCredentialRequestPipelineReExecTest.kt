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

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.attribute.flow.PipelinePhase
import com.sphereon.attribute.pipeline.Oid4vciPipelinePhase
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventTypes
import com.sphereon.credential.issuance.pipeline.IssuancePipelineStatus
import com.sphereon.credential.issuance.pipeline.command.BindingCompletenessVerdict
import com.sphereon.credential.issuance.pipeline.command.ContributeAttributesArgs
import com.sphereon.credential.issuance.pipeline.command.ContributeAttributesCommand
import com.sphereon.credential.issuance.pipeline.command.ContributeAttributesResult
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessArgs
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessCommand
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessResult
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.common.model.DeferredCredentialRequest
import com.sphereon.openid.oid4vci.issuer.attribute.CredentialAttributeContribution
import com.sphereon.openid.oid4vci.issuer.attribute.CredentialAttributeContributor
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.format.CredentialEnvelope
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialEntry
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStatus
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Exercises Task 7.3 — the pipeline re-execution path on `/deferred_credential`. The PENDING
 * branch of [HandleDeferredCredentialRequestCommandImpl] now runs the DEFERRED pipeline phase,
 * re-evaluates completeness, and dispatches the format handler when every binding is complete
 * and not awaiting approval.
 */
class HandleDeferredCredentialRequestPipelineReExecTest {
    private val configId = "TestCredential"
    private val transactionId = "txn-123"
    private val sessionId = "session-1"
    private val correlationId = "corr-1"
    private val accessToken = "test-access-token"
    private val nowMillis = 1_700_000_000_000L

    // ------------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------------

    private class FakeAsBridge : Oid4vciAuthorizationServerBridge by NoOpAsBridge() {
        override suspend fun validateAccessToken(args: ValidateAccessTokenArgs): IdkResult<ValidatedTokenContext, IdkError> =
            Ok(
                ValidatedTokenContext(
                    subject = "did:example:holder",
                    clientId = "test-client",
                    scope = null,
                    credentialConfigurationIds = listOf("TestCredential"),
                ),
            )
    }

    private class CountingFormatHandler(
        private val envelopeFactory: () -> CredentialEnvelope = {
            CredentialEnvelope(
                credential = JsonPrimitive("issued-credential"),
                format = CredentialFormat.SD_JWT_DC.value,
            )
        },
    ) : CredentialFormatHandler {
        var issueCount = 0
        override val supportedFormat: String = CredentialFormat.SD_JWT_DC.value

        override suspend fun canHandle(
            request: CredentialRequest,
            configuration: CredentialConfigurationSupported,
        ): Boolean = true

        override suspend fun issueCredential(
            request: CredentialRequest,
            context: IssuanceContext,
        ): IdkResult<CredentialEnvelope, IdkError> {
            issueCount++
            return Ok(envelopeFactory())
        }
    }

    private class RecordingAttributeContributor(
        private val contributed: Map<String, JsonElement> = emptyMap(),
    ) : CredentialAttributeContributor {
        var calls = 0

        override suspend fun contribute(
            session: IssuanceSession,
            tokenContext: ValidatedTokenContext,
            credentialConfigurationId: String,
        ): IdkResult<CredentialAttributeContribution, IdkError> {
            calls++
            return Ok(CredentialAttributeContribution(attributes = contributed))
        }
    }

    private class FakeConfigProvider : Oid4vciIssuerConfigProvider {
        override val issuerIdentifier: String = "https://test.example/oid4vci"
        override val credentialConfigurations: Map<String, CredentialConfigurationSupported> =
            mapOf(
                "TestCredential" to
                    CredentialConfigurationSupported(
                        format = CredentialFormat.SD_JWT_DC.value,
                        vct = "https://test.example/vct",
                    ),
            )
        override val authorizationServers: List<String>? = null
        override val display: List<DisplayProperties>? = null
    }

    private class RecordingContributeAttributesCommand(
        private val onExecute: (ContributeAttributesArgs) -> IdkResult<ContributeAttributesResult, IdkError> = { args ->
            Ok(
                ContributeAttributesResult(
                    sessionId = "session-1",
                    status = IssuancePipelineStatus.PHASE_COMPLETED,
                    completedPhases = setOf(args.phase),
                ),
            )
        },
    ) : ContributeAttributesCommand {
        val calls = mutableListOf<ContributeAttributesArgs>()
        override val inputTypeToken: TypeToken<ContributeAttributesArgs> = typeToken()
        override val outputTypeToken: TypeToken<ContributeAttributesResult> = typeToken()
        override val isEnabled: Boolean = true

        override suspend fun execute(args: ContributeAttributesArgs,): IdkResult<ContributeAttributesResult, IdkError> {
            calls += args
            return onExecute(args)
        }

        override suspend fun supports(args: Any): Boolean = args is ContributeAttributesArgs
    }

    private class RecordingCompletenessCommand(
        private val verdicts: List<BindingCompletenessVerdict>,
    ) : EvaluateAttributeCompletenessCommand {
        var calls = 0
        override val inputTypeToken: TypeToken<EvaluateAttributeCompletenessArgs> = typeToken()
        override val outputTypeToken: TypeToken<EvaluateAttributeCompletenessResult> = typeToken()
        override val isEnabled: Boolean = true

        override suspend fun execute(args: EvaluateAttributeCompletenessArgs,): IdkResult<EvaluateAttributeCompletenessResult, IdkError> {
            calls++
            return Ok(EvaluateAttributeCompletenessResult(verdicts = verdicts))
        }

        override suspend fun supports(args: Any): Boolean = args is EvaluateAttributeCompletenessArgs
    }

    private fun fixedClock(epochMillis: Long = nowMillis): Clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(epochMillis)
        }

    private fun session(pipelineCorrelationId: String?): IssuanceSession =
        IssuanceSession(
            sessionId = sessionId,
            issuerId = "https://test.example/oid4vci",
            credentialConfigurationIds = listOf(configId),
            status = IssuanceSessionStatus.DEFERRED,
            pipelineCorrelationId = pipelineCorrelationId,
            createdAt = 0,
            expiresAt = Long.MAX_VALUE,
        )

    private fun pendingEntry(expiresAt: Long = nowMillis + 60_000): DeferredCredentialEntry =
        DeferredCredentialEntry(
            transactionId = transactionId,
            issuanceSessionId = sessionId,
            credentialConfigurationId = configId,
            status = DeferredCredentialStatus.PENDING,
            retryAfterSeconds = 5,
            createdAt = nowMillis - 1_000,
            expiresAt = expiresAt,
        )

    private fun args(): HandleDeferredCredentialRequestArgs =
        HandleDeferredCredentialRequestArgs(
            accessToken = accessToken,
            deferredRequest = DeferredCredentialRequest(transactionId = transactionId),
        )

    private fun configProvider(): Oid4vciIssuerConfigProvider = FakeConfigProvider()

    /**
     * The deferred command takes a long parameter list; the helper centralises the boilerplate
     * so each test only specifies the dependencies under test. Required dependencies
     * (`sessionStore`, `attributeContributor`, `issuerConfigProvider`) default to working fakes
     * so individual tests stay focused on the pipeline behaviour under exercise.
     */
    @Suppress("LongParameterList")
    private fun command(
        sessionStore: RecordingSessionStore,
        deferredStore: RecordingDeferredStore,
        formatHandlers: Set<CredentialFormatHandler> = emptySet(),
        attributeContributor: CredentialAttributeContributor = RecordingAttributeContributor(),
        contributeAttributesCommand: ContributeAttributesCommand? = null,
        evaluateAttributeCompletenessCommand: EvaluateAttributeCompletenessCommand? = null,
        issuerConfigProvider: Oid4vciIssuerConfigProvider = configProvider(),
        clockEpochMillis: Long = nowMillis,
        eventService: RecordingSessionEventService? = null,
    ): HandleDeferredCredentialRequestCommandImpl =
        HandleDeferredCredentialRequestCommandImpl(
            execution = TestSessionExecution(),
            asBridge = FakeAsBridge(),
            deferredStore = deferredStore,
            clock = fixedClock(clockEpochMillis),
            eventService = eventService,
            reExecutorFactory =
                DeferredPipelineReExecutorFactory(
                    deferredStore = deferredStore,
                    sessionStore = sessionStore,
                    attributeContributor = attributeContributor,
                    issuerConfigProvider = issuerConfigProvider,
                    formatDispatch =
                        DeferredPipelineReExecutorFactory.FormatDispatch(
                            formatHandlers = formatHandlers,
                        ),
                    pipelineCommands =
                        DeferredPipelineReExecutorFactory.PipelineCommands(
                            contributeAttributesCommand = contributeAttributesCommand,
                            evaluateAttributeCompletenessCommand = evaluateAttributeCompletenessCommand,
                        ),
                ),
        )

    // ------------------------------------------------------------------------
    // (a) PENDING, pipeline NOW complete after re-exec, format handler issues
    // ------------------------------------------------------------------------

    @Test
    fun reExecutionCompletesAndDeliversCredential() =
        runTest {
            val sessionStore = RecordingSessionStore().apply { create(session(correlationId)) }
            val deferredStore = RecordingDeferredStore().apply { create(pendingEntry()) }
            val formatHandler = CountingFormatHandler()
            val contribute = RecordingContributeAttributesCommand()
            val completeness =
                RecordingCompletenessCommand(
                    listOf(BindingCompletenessVerdict(bindingId = configId, complete = true)),
                )

            val result =
                command(
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    formatHandlers = setOf(formatHandler),
                    attributeContributor = RecordingAttributeContributor(),
                    contributeAttributesCommand = contribute,
                    evaluateAttributeCompletenessCommand = completeness,
                    issuerConfigProvider = configProvider(),
                ).execute(args())

            assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
            val credentials = result.value.credentials
            assertNotNull(credentials, "re-exec success must return credentials, not transactionId+interval")
            assertEquals(1, credentials.size)
            assertNull(result.value.transactionId, "re-exec success must not return transactionId+interval")
            assertEquals(1, formatHandler.issueCount, "format handler must dispatch once")
            assertEquals(1, contribute.calls.size, "DEFERRED phase must run exactly once")
            assertEquals(Oid4vciPipelinePhase.DEFERRED, contribute.calls.single().phase)
            assertEquals(
                DeferredCredentialStatus.READY,
                deferredStore.updated.single().status,
                "entry must be marked READY when the credential is produced",
            )
            assertNotNull(
                deferredStore.updated.single().credentialResponse,
                "entry must carry the produced credential body",
            )
        }

    // ------------------------------------------------------------------------
    // (b) PENDING, still incomplete after re-exec → 202 + transactionId + interval
    // ------------------------------------------------------------------------

    @Test
    fun reExecutionStillIncompleteReturns202() =
        runTest {
            val sessionStore = RecordingSessionStore().apply { create(session(correlationId)) }
            val deferredStore = RecordingDeferredStore().apply { create(pendingEntry()) }
            val formatHandler = CountingFormatHandler()
            val contribute = RecordingContributeAttributesCommand()
            val completeness =
                RecordingCompletenessCommand(
                    listOf(
                        BindingCompletenessVerdict(
                            bindingId = configId,
                            complete = false,
                            deferralRecommended = true,
                        ),
                    ),
                )

            val result =
                command(
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    formatHandlers = setOf(formatHandler),
                    attributeContributor = RecordingAttributeContributor(),
                    contributeAttributesCommand = contribute,
                    evaluateAttributeCompletenessCommand = completeness,
                    issuerConfigProvider = configProvider(),
                ).execute(args())

            assertTrue(result.isOk, "expected Ok with 202 shape, got Err: ${result.errorOrNull()}")
            assertEquals(transactionId, result.value.transactionId)
            assertEquals(5, result.value.interval)
            assertNull(result.value.credentials, "202 shape must not carry credentials")
            assertEquals(0, formatHandler.issueCount, "format handler must NOT be dispatched")
            assertTrue(
                deferredStore.updated.none { it.status == DeferredCredentialStatus.READY },
                "entry must stay PENDING when re-exec leaves bindings incomplete",
            )
        }

    @Test
    fun reExecutionAwaitingApprovalReturns202() =
        runTest {
            val sessionStore = RecordingSessionStore().apply { create(session(correlationId)) }
            val deferredStore = RecordingDeferredStore().apply { create(pendingEntry()) }
            val formatHandler = CountingFormatHandler()
            val contribute = RecordingContributeAttributesCommand()
            val completeness =
                RecordingCompletenessCommand(
                    listOf(
                        BindingCompletenessVerdict(
                            bindingId = configId,
                            complete = true,
                            awaitingApproval = true,
                        ),
                    ),
                )

            val result =
                command(
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    formatHandlers = setOf(formatHandler),
                    attributeContributor = RecordingAttributeContributor(),
                    contributeAttributesCommand = contribute,
                    evaluateAttributeCompletenessCommand = completeness,
                    issuerConfigProvider = configProvider(),
                ).execute(args())

            assertTrue(result.isOk)
            assertEquals(transactionId, result.value.transactionId)
            assertEquals(0, formatHandler.issueCount, "approval-gated bindings must NOT dispatch")
        }

    // ------------------------------------------------------------------------
    // (c) PENDING, past expiresAt → entry -> EXPIRED, Err
    // ------------------------------------------------------------------------

    @Test
    fun expiredPendingEntryFlipsToExpiredAndErrors() =
        runTest {
            val sessionStore = RecordingSessionStore().apply { create(session(correlationId)) }
            val deferredStore =
                RecordingDeferredStore().apply { create(pendingEntry(expiresAt = nowMillis - 1)) }
            val formatHandler = CountingFormatHandler()
            val contribute = RecordingContributeAttributesCommand()
            val completeness =
                RecordingCompletenessCommand(
                    listOf(BindingCompletenessVerdict(bindingId = configId, complete = true)),
                )

            val result =
                command(
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    formatHandlers = setOf(formatHandler),
                    attributeContributor = RecordingAttributeContributor(),
                    contributeAttributesCommand = contribute,
                    evaluateAttributeCompletenessCommand = completeness,
                    issuerConfigProvider = configProvider(),
                ).execute(args())

            assertTrue(result.isErr, "expected Err when entry has expired")
            assertEquals(
                DeferredCredentialStatus.EXPIRED,
                deferredStore.updated.single().status,
                "entry must be marked EXPIRED",
            )
            assertEquals(0, contribute.calls.size, "re-exec must NOT run after expiry")
            assertEquals(0, formatHandler.issueCount)
        }

    // ------------------------------------------------------------------------
    // (d) pure-IDK (no pipelineCorrelationId or no pipeline commands) → unchanged
    // ------------------------------------------------------------------------

    @Test
    fun pureIdkWithoutPipelineDepsFallsThroughTo202() =
        runTest {
            val sessionStore = RecordingSessionStore().apply { create(session(pipelineCorrelationId = null)) }
            val deferredStore = RecordingDeferredStore().apply { create(pendingEntry()) }
            val formatHandler = CountingFormatHandler()

            val result =
                command(
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    // No pipeline commands wired (pure-IDK) so re-execution is skipped entirely.
                    formatHandlers = setOf(formatHandler),
                ).execute(args())

            assertTrue(result.isOk)
            assertEquals(transactionId, result.value.transactionId)
            assertEquals(5, result.value.interval)
            assertNull(result.value.credentials)
            assertEquals(0, formatHandler.issueCount, "format handler must NOT be invoked on pure-IDK path")
            assertTrue(
                deferredStore.updated.isEmpty(),
                "no re-exec, no expiry flip, so the entry must not be updated",
            )
        }

    // ------------------------------------------------------------------------
    // (e) re-exec contribute errors → fall through to 202
    // ------------------------------------------------------------------------

    @Test
    fun contributeAttributesErrorFallsThroughTo202() =
        runTest {
            val sessionStore = RecordingSessionStore().apply { create(session(correlationId)) }
            val deferredStore = RecordingDeferredStore().apply { create(pendingEntry()) }
            val formatHandler = CountingFormatHandler()
            val contribute =
                RecordingContributeAttributesCommand(onExecute = {
                    Err(IdkError.fromString(code = "source_unavailable", message = "boom"))
                })
            val completeness =
                RecordingCompletenessCommand(
                    listOf(BindingCompletenessVerdict(bindingId = configId, complete = true)),
                )

            val result =
                command(
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    formatHandlers = setOf(formatHandler),
                    attributeContributor = RecordingAttributeContributor(),
                    contributeAttributesCommand = contribute,
                    evaluateAttributeCompletenessCommand = completeness,
                    issuerConfigProvider = configProvider(),
                ).execute(args())

            assertTrue(result.isOk, "ContributeAttributes errors must NOT bubble up to the wallet")
            assertEquals(transactionId, result.value.transactionId)
            assertEquals(0, completeness.calls, "completeness must not run when contribute errored")
            assertEquals(0, formatHandler.issueCount)
            assertTrue(
                deferredStore.updated.isEmpty(),
                "no re-exec progress means the entry stays PENDING",
            )
        }

    // ------------------------------------------------------------------------
    // (f) all pipeline deps wired but pipelineCorrelationId = null → 202, no side effects
    // ------------------------------------------------------------------------

    @Test
    fun allPipelineDepsWiredButNullCorrelationIdReturns202() =
        runTest {
            val sessionStore = RecordingSessionStore().apply { create(session(pipelineCorrelationId = null)) }
            val deferredStore = RecordingDeferredStore().apply { create(pendingEntry()) }
            val formatHandler = CountingFormatHandler()
            val contribute = RecordingContributeAttributesCommand()
            val completeness =
                RecordingCompletenessCommand(
                    listOf(BindingCompletenessVerdict(bindingId = configId, complete = true)),
                )
            val contributor = RecordingAttributeContributor()

            val result =
                command(
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    formatHandlers = setOf(formatHandler),
                    attributeContributor = contributor,
                    contributeAttributesCommand = contribute,
                    evaluateAttributeCompletenessCommand = completeness,
                    issuerConfigProvider = configProvider(),
                ).execute(args())

            assertTrue(result.isOk, "expected Ok with 202 shape, got Err: ${result.errorOrNull()}")
            assertEquals(transactionId, result.value.transactionId, "202 shape must carry transactionId")
            assertEquals(5, result.value.interval)
            assertNull(result.value.credentials, "202 shape must not carry credentials")
            assertEquals(0, contribute.calls.size, "contribute must NOT run when correlationId is null")
            assertEquals(0, contributor.calls, "attribute contributor must NOT be called")
            assertEquals(0, formatHandler.issueCount, "format handler must NOT be dispatched")
            assertTrue(
                deferredStore.updated.isEmpty(),
                "entry must not be updated when re-exec is skipped due to null correlationId",
            )
        }

    // ------------------------------------------------------------------------
    // (g) event emission: OID4VCI_CREDENTIAL_DEFERRED_ISSUED fires only on delivery; 202 emits nothing
    // ------------------------------------------------------------------------

    @Test
    fun eventFiredExactlyOnceOnCredentialDelivery() =
        runTest {
            val sessionStore = RecordingSessionStore().apply { create(session(correlationId)) }
            val deferredStore = RecordingDeferredStore().apply { create(pendingEntry()) }
            val formatHandler = CountingFormatHandler()
            val contribute = RecordingContributeAttributesCommand()
            val completeness =
                RecordingCompletenessCommand(
                    listOf(BindingCompletenessVerdict(bindingId = configId, complete = true)),
                )
            val recordingEventService = RecordingSessionEventService()

            command(
                sessionStore = sessionStore,
                deferredStore = deferredStore,
                formatHandlers = setOf(formatHandler),
                attributeContributor = RecordingAttributeContributor(),
                contributeAttributesCommand = contribute,
                evaluateAttributeCompletenessCommand = completeness,
                issuerConfigProvider = configProvider(),
                eventService = recordingEventService,
            ).execute(args())

            assertEquals(1, recordingEventService.emitted.size, "exactly one event must be emitted on credential delivery")
            assertEquals(
                EventTypes.OID4VCI_CREDENTIAL_DEFERRED_ISSUED,
                recordingEventService.emitted.single().type,
                "event type must be OID4VCI_CREDENTIAL_DEFERRED_ISSUED",
            )
        }

    @Test
    fun noEventFiredOnStillPending202() =
        runTest {
            val sessionStore = RecordingSessionStore().apply { create(session(correlationId)) }
            val deferredStore = RecordingDeferredStore().apply { create(pendingEntry()) }
            val formatHandler = CountingFormatHandler()
            val contribute = RecordingContributeAttributesCommand()
            val completeness =
                RecordingCompletenessCommand(
                    listOf(
                        BindingCompletenessVerdict(
                            bindingId = configId,
                            complete = false,
                            deferralRecommended = true,
                        ),
                    ),
                )
            val recordingEventService = RecordingSessionEventService()

            command(
                sessionStore = sessionStore,
                deferredStore = deferredStore,
                formatHandlers = setOf(formatHandler),
                attributeContributor = RecordingAttributeContributor(),
                contributeAttributesCommand = contribute,
                evaluateAttributeCompletenessCommand = completeness,
                issuerConfigProvider = configProvider(),
                eventService = recordingEventService,
            ).execute(args())

            assertEquals(
                0,
                recordingEventService.emitted.size,
                "no event must be emitted when the response is still-pending 202",
            )
        }

    // ------------------------------------------------------------------------
    // PipelinePhase smoke — verify DEFERRED phase exists on PipelinePhase
    // ------------------------------------------------------------------------

    @Test
    fun deferredPipelinePhaseIsRecognisable() {
        val phase: PipelinePhase = Oid4vciPipelinePhase.DEFERRED
        assertEquals("oid4vci_deferred", phase.value)
    }
}
