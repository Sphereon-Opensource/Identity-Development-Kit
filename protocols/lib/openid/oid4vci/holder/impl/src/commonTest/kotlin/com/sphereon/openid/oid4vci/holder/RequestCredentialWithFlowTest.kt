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

package com.sphereon.openid.oid4vci.holder

import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.holder.impl.flow.RequestCredentialWithFlowCommandImpl
import com.sphereon.wallet.unit.WalletAttestedKeyRef
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [RequestCredentialWithFlowArgs], [CredentialFlowResult], and
 * [RequestCredentialWithFlowCommand] contract.
 *
 * Tests cover:
 * - Args construction and defaults
 * - Nonce retry error code extraction contract
 * - All [CredentialFlowResult] subtypes, including the prepared-proof reactivation boundary
 * - Sealed class exhaustiveness
 */
class RequestCredentialWithFlowTest {
    @Test
    fun preparedProofModeIsExplicitAndReactivationResultIsTyped() {
        val prepared =
            PreparedCredentialRequestProofBatch(
                listOf(
                    PreparedCredentialRequestProof(
                        exactSigningInputBase64Url = "a.b",
                        protectedHeaderBase64Url = "a",
                        payloadBase64Url = "b",
                        keyRef = WalletAttestedKeyRef("key-1", "ES256"),
                        algorithm = "ES256",
                        keyInclusionMode = "KID",
                        walletUnitId = "unit-1",
                        walletAccountId = "account-1",
                        operationBinding = "binding-1",
                        operationType = "OID4VCI_CREDENTIAL_REQUEST",
                        digestBinding = "sha256:digest",
                        nonce = "nonce-1",
                        audience = "https://issuer.example.com",
                        issuerUrl = "https://issuer.example.com",
                        clientId = "client-1",
                        cNonce = "nonce-1",
                        iatEpochSeconds = 1_700_000_000L,
                        integrityBinding = "sha256:integrity",
                    ),
                ),
            )
        val args =
            RequestCredentialWithFlowArgs(
                sessionId = "session-1",
                walletUnitId = "unit-1",
                operationBinding = "binding-1",
                credentialEndpoint = "https://issuer.example.com/credential",
                accessToken = "token",
                issuerUrl = "https://issuer.example.com",
                signingKeyId = "key-1",
                proofMode = RequestCredentialWithFlowProofMode.Prepared(prepared),
            )

        val mode = assertIs<RequestCredentialWithFlowProofMode.Prepared>(args.proofMode)
        assertEquals(prepared, mode.proofBatch)
        val reactivation = CredentialFlowResult.ReactivationRequired(issuerNonce = "fresh-nonce", retryAfterSeconds = 5)
        assertEquals("fresh-nonce", reactivation.issuerNonce)
        assertEquals(5, reactivation.retryAfterSeconds)
    }

    // ============================================================================
    // Args defaults
    // ============================================================================

    @Test
    fun argsSigningAlgorithmDefaultsToEs256() {
        val args =
            RequestCredentialWithFlowArgs(
                sessionId = "sess-001",
                walletUnitId = "wallet-unit-1",
                operationBinding = "issuance-sess-001",
                credentialEndpoint = "https://issuer.example.com/credential",
                accessToken = "tok",
                issuerUrl = "https://issuer.example.com",
                signingKeyId = "key-1",
            )

        assertEquals("ES256", args.signingAlgorithm)
    }

    @Test
    fun argsOptionalFieldsDefaultToNull() {
        val args =
            RequestCredentialWithFlowArgs(
                sessionId = "sess-001",
                walletUnitId = "wallet-unit-1",
                operationBinding = "issuance-sess-001",
                credentialEndpoint = "https://issuer.example.com/credential",
                accessToken = "tok",
                issuerUrl = "https://issuer.example.com",
                signingKeyId = "key-1",
            )

        assertNull(args.credentialConfigurationId)
        assertNull(args.credentialIdentifier)
        assertNull(args.nonceEndpoint)
        assertNull(args.deferredCredentialEndpoint)
        assertNull(args.notificationEndpoint)
        assertNull(args.credentialResponseEncryption)
        assertNull(args.decryptionKey)
        assertEquals(RequestCredentialWithFlowProofMode.Unattended, args.proofMode)
    }

    @Test
    fun argsWithAllOptionalEndpoints() {
        val args =
            RequestCredentialWithFlowArgs(
                sessionId = "sess-002",
                walletUnitId = "wallet-unit-2",
                operationBinding = "issuance-sess-002",
                credentialEndpoint = "https://issuer.example.com/credential",
                accessToken = "Bearer eyJ.tok.sig",
                issuerUrl = "https://issuer.example.com",
                signingKeyId = "key-wallet-1",
                signingAlgorithm = "EdDSA",
                credentialConfigurationId = "UniversityDegreeCredential",
                nonceEndpoint = "https://issuer.example.com/nonce",
                deferredCredentialEndpoint = "https://issuer.example.com/deferred",
                notificationEndpoint = "https://issuer.example.com/notification",
            )

        assertEquals("sess-002", args.sessionId)
        assertEquals("EdDSA", args.signingAlgorithm)
        assertEquals("UniversityDegreeCredential", args.credentialConfigurationId)
        assertNull(args.credentialIdentifier)
        assertEquals("https://issuer.example.com/nonce", args.nonceEndpoint)
        assertEquals("https://issuer.example.com/deferred", args.deferredCredentialEndpoint)
        assertEquals("https://issuer.example.com/notification", args.notificationEndpoint)
    }

    @Test
    fun argsWithCredentialIdentifierInsteadOfConfigurationId() {
        val args =
            RequestCredentialWithFlowArgs(
                sessionId = "sess-003",
                walletUnitId = "wallet-unit-3",
                operationBinding = "issuance-sess-003",
                credentialEndpoint = "https://issuer.example.com/credential",
                accessToken = "tok",
                issuerUrl = "https://issuer.example.com",
                signingKeyId = "key-1",
                credentialIdentifier = "CivilEngineeringDegree-2024",
            )

        assertNull(args.credentialConfigurationId)
        assertEquals("CivilEngineeringDegree-2024", args.credentialIdentifier)
    }

    // ============================================================================
    // Nonce retry — error code extraction contract
    // ============================================================================

    @Test
    fun invalidNonceFreshNonceAvailableCodeContainsNonce() {
        // The RequestCredentialCommandImpl embeds the fresh nonce as:
        // INVALID_NONCE_FRESH_NONCE_AVAILABLE:<nonce>
        val errorCode = "INVALID_NONCE_FRESH_NONCE_AVAILABLE:tZignsnFbp"
        val prefix = "INVALID_NONCE_FRESH_NONCE_AVAILABLE:"

        assertTrue(errorCode.startsWith(prefix))
        val extracted = errorCode.removePrefix(prefix)
        assertEquals("tZignsnFbp", extracted)
    }

    @Test
    fun invalidNonceCodeWithEmptyNonceResultsInBlank() {
        val errorCode = "INVALID_NONCE_FRESH_NONCE_AVAILABLE:"
        val prefix = "INVALID_NONCE_FRESH_NONCE_AVAILABLE:"
        val extracted = errorCode.removePrefix(prefix)
        assertTrue(extracted.isBlank())
    }

    @Test
    fun unrelatedErrorCodeDoesNotMatchNoncePrefix() {
        val errorCode = "CREDENTIAL_REQUEST_FAILED"
        val prefix = "INVALID_NONCE_FRESH_NONCE_AVAILABLE:"
        assertTrue(!errorCode.startsWith(prefix))
    }

    // ============================================================================
    // CredentialFlowResult — Immediate
    // ============================================================================

    @Test
    fun immediateResultHasCredentialAndNotificationSent() {
        val credential =
            CredentialResponse(
                credentials = listOf(CredentialResponseItem(JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.payload.sig"))),
                notificationId = "notif-001",
            )

        val result =
            CredentialFlowResult.Immediate(
                credential = credential,
                notificationSent = true,
            )

        assertIs<CredentialFlowResult.Immediate>(result)
        assertEquals("notif-001", result.credential.notificationId)
        assertTrue(result.notificationSent)
    }

    @Test
    fun immediateResultNotificationSentCanBeFalse() {
        val credential = CredentialResponse(credentials = listOf(CredentialResponseItem(JsonPrimitive("eyJ.x.y"))))

        val result = CredentialFlowResult.Immediate(credential = credential, notificationSent = false)

        assertTrue(!result.notificationSent)
    }

    // ============================================================================
    // CredentialFlowResult — DeferredCompleted
    // ============================================================================

    @Test
    fun deferredCompletedResultHasAllFields() {
        val credential =
            CredentialResponse(
                credentials = listOf(CredentialResponseItem(JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.deferred.sig"))),
                notificationId = "notif-deferred",
            )

        val result =
            CredentialFlowResult.DeferredCompleted(
                credential = credential,
                pollAttempts = 7,
                notificationSent = true,
            )

        assertIs<CredentialFlowResult.DeferredCompleted>(result)
        assertEquals(7, result.pollAttempts)
        assertTrue(result.notificationSent)
        assertEquals("notif-deferred", result.credential.notificationId)
    }

    // ============================================================================
    // CredentialFlowResult — DeferredExhausted
    // ============================================================================

    @Test
    fun deferredExhaustedResultPreservesAllState() {
        val result =
            CredentialFlowResult.DeferredExhausted(
                transactionId = "txn-server-final",
                attemptsMade = 60,
                lastInterval = 10,
            )

        assertIs<CredentialFlowResult.DeferredExhausted>(result)
        assertEquals("txn-server-final", result.transactionId)
        assertEquals(60, result.attemptsMade)
        assertEquals(10, result.lastInterval)
    }

    // ============================================================================
    // Sealed class exhaustiveness
    // ============================================================================

    @Test
    fun whenExpressionCoversAllSubtypes() {
        val credential = CredentialResponse(credentials = listOf(CredentialResponseItem(JsonPrimitive("eyJ.x.y"))))
        val results: List<CredentialFlowResult> =
            listOf(
                CredentialFlowResult.Immediate(credential, false),
                CredentialFlowResult.DeferredCompleted(credential, 3, true),
                CredentialFlowResult.DeferredExhausted("txn", 60, 5),
                CredentialFlowResult.ReactivationRequired("fresh-nonce", 5),
            )

        var immediateCount = 0
        var deferredCompletedCount = 0
        var deferredExhaustedCount = 0
        var reactivationRequiredCount = 0

        for (result in results) {
            when (result) {
                is CredentialFlowResult.Immediate -> immediateCount++
                is CredentialFlowResult.DeferredCompleted -> deferredCompletedCount++
                is CredentialFlowResult.DeferredExhausted -> deferredExhaustedCount++
                is CredentialFlowResult.ReactivationRequired -> reactivationRequiredCount++
            }
        }

        assertEquals(1, immediateCount)
        assertEquals(1, deferredCompletedCount)
        assertEquals(1, deferredExhaustedCount)
        assertEquals(1, reactivationRequiredCount)
    }

    // ============================================================================
    // Command ID
    // ============================================================================

    @Test
    fun commandIdIsCorrect() {
        assertEquals("oid4vci.holder.credential-flow", RequestCredentialWithFlowCommand.COMMAND_ID)
    }

    @Test
    fun preparedModeFinalizesOnceWithoutNonceProofRebuildOrRetry() = runTest {
        val finalizer = FlowRecordingProofPreparation()
        val nonce = FlowRecordingNonceCommand()
        val creator = FlowRecordingCreateProofCommand()
        val credential = FlowRecordingCredentialCommand()
        val sessionStore = FlowRecordingSessionStore()
        val command =
            RequestCredentialWithFlowCommandImpl(
                execution = FlowTestSessionExecution(),
                requestNonceCommand = nonce,
                createCredentialRequestProofCommand = creator,
                requestCredentialCommand = credential,
                pollDeferredCredentialCommand = FlowNoopPollCommand(),
                sendNotificationWithRetryCommand = FlowNoopNotificationCommand(),
                proofPreparation = finalizer,
                sessionStore = sessionStore,
                config = FlowTestConfig,
            )

        val result = command.execute(flowArgs())

        assertTrue(result.isOk)
        val reactivation = assertIs<CredentialFlowResult.ReactivationRequired>(result.value)
        assertEquals("issuer-fresh", reactivation.issuerNonce)
        assertEquals(7, reactivation.retryAfterSeconds)
        assertEquals(1, finalizer.finalizeCalls)
        assertEquals(0, nonce.calls)
        assertEquals(0, creator.calls)
        assertEquals(1, credential.calls)
        val submittedProof = credential.lastArgs!!.proofs!!.proofValues.single() as JsonPrimitive
        assertEquals("prepared-proof", submittedProof.content)
        assertNull(credential.lastArgs?.nonceEndpoint)
        assertEquals(Oid4vciHolderSessionStatus.ACTIVATION_REQUIRED, sessionStore.updates.last().status)
    }

    @Test
    fun preparedModeRejectsMultiProofBatchBeforeFinalization() = runTest {
        val finalizer = FlowRecordingProofPreparation()
        val nonce = FlowRecordingNonceCommand()
        val creator = FlowRecordingCreateProofCommand()
        val credential = FlowRecordingCredentialCommand()
        val oneProof = assertIs<RequestCredentialWithFlowProofMode.Prepared>(flowArgs().proofMode).proofBatch.proofs.single()

        val result =
            flowCommand(finalizer, nonce, creator, credential).execute(
                flowArgs().copy(
                    proofMode = RequestCredentialWithFlowProofMode.Prepared(
                        PreparedCredentialRequestProofBatch(listOf(oneProof, oneProof)),
                    ),
                ),
            )

        assertTrue(result.isErr)
        assertEquals("PREPARED_PROOF_BATCH_SIZE_MISMATCH", result.error!!.code)
        assertEquals(0, finalizer.finalizeCalls)
        assertEquals(0, nonce.calls)
        assertEquals(0, creator.calls)
        assertEquals(0, credential.calls)
    }

    @Test
    fun preparedModeSuccessfulIssuanceReturnsImmediateWithoutRebuildingProof() = runTest {
        val finalizer = FlowRecordingProofPreparation()
        val nonce = FlowRecordingNonceCommand()
        val creator = FlowRecordingCreateProofCommand()
        val credential = FlowRecordingCredentialCommand().apply { rejectWithInvalidNonce = false }

        val result =
            flowCommand(finalizer, nonce, creator, credential).execute(flowArgs())

        assertTrue(result.isOk)
        val immediate = assertIs<CredentialFlowResult.Immediate>(result.value)
        assertEquals(1, finalizer.finalizeCalls)
        assertEquals(0, nonce.calls)
        assertEquals(0, creator.calls)
        assertEquals(1, credential.calls)
        assertEquals("issued-credential", (immediate.credential.credentials!!.single().credential as JsonPrimitive).content)
        val submittedProof = credential.lastArgs!!.proofs!!.proofValues.single() as JsonPrimitive
        assertEquals("prepared-proof", submittedProof.content)
        assertNull(credential.lastArgs?.nonceEndpoint)
    }

    @Test
    fun preparedModeRejectsSnapshotForAnotherOperationBeforeFinalization() = runTest {
        val finalizer = FlowRecordingProofPreparation()
        val nonce = FlowRecordingNonceCommand()
        val creator = FlowRecordingCreateProofCommand()
        val credential = FlowRecordingCredentialCommand()

        val result =
            flowCommand(finalizer, nonce, creator, credential).execute(
                flowArgs().copy(operationBinding = "different-binding"),
            )

        assertTrue(result.isErr)
        assertEquals("PREPARED_PROOF_CONTEXT_MISMATCH", result.error!!.code)
        assertEquals(0, finalizer.finalizeCalls)
        assertEquals(0, nonce.calls)
        assertEquals(0, creator.calls)
        assertEquals(0, credential.calls)
    }

    private fun flowCommand(
        finalizer: FlowRecordingProofPreparation,
        nonce: FlowRecordingNonceCommand,
        creator: FlowRecordingCreateProofCommand,
        credential: FlowRecordingCredentialCommand,
    ): RequestCredentialWithFlowCommandImpl =
        RequestCredentialWithFlowCommandImpl(
            execution = FlowTestSessionExecution(),
            requestNonceCommand = nonce,
            createCredentialRequestProofCommand = creator,
            requestCredentialCommand = credential,
            pollDeferredCredentialCommand = FlowNoopPollCommand(),
            sendNotificationWithRetryCommand = FlowNoopNotificationCommand(),
            proofPreparation = finalizer,
            sessionStore = FlowRecordingSessionStore(),
            config = FlowTestConfig,
        )

    private fun flowArgs(): RequestCredentialWithFlowArgs =
        RequestCredentialWithFlowArgs(
            sessionId = "flow-session",
            walletUnitId = "wallet-unit",
            operationBinding = "binding",
            credentialEndpoint = "https://issuer.example/credential",
            accessToken = "token",
            issuerUrl = "https://issuer.example",
            signingKeyId = "key",
            nonceEndpoint = "https://issuer.example/nonce",
            proofMode = RequestCredentialWithFlowProofMode.Prepared(
                PreparedCredentialRequestProofBatch(
                    listOf(
                        PreparedCredentialRequestProof(
                            exactSigningInputBase64Url = "a.b",
                            protectedHeaderBase64Url = "a",
                            payloadBase64Url = "b",
                            keyRef = WalletAttestedKeyRef("key", "ES256"),
                            algorithm = "ES256",
                            keyInclusionMode = "KID",
                            walletUnitId = "wallet-unit",
                            walletAccountId = null,
                            operationBinding = "binding",
                            operationType = "operation",
                            digestBinding = "digest",
                            nonce = "nonce",
                            audience = "https://issuer.example",
                            issuerUrl = "https://issuer.example",
                            clientId = null,
                            cNonce = null,
                            iatEpochSeconds = 1,
                            integrityBinding = "integrity",
                        ),
                    ),
                ),
            ),
        )
}

private abstract class FlowFakeCommand<I : Any, O : Any>(
    private val commandIdentifier: String,
    override val inputTypeToken: TypeToken<I>,
    override val outputTypeToken: TypeToken<O>,
) : ServiceCommand<I, O, IdkError> {
    open override val commandId: String get() = commandIdentifier
    override val id: String get() = commandIdentifier
    override val isEnabled: Boolean get() = true
    override suspend fun supports(args: Any): Boolean = true
}

private class FlowRecordingProofPreparation : CredentialRequestProofPreparation {
    var finalizeCalls = 0
    override suspend fun prepare(request: CredentialRequestProofPreparationRequest): IdkResult<PreparedCredentialRequestProofBatch, IdkError> =
        error("unattended path not expected")
    override suspend fun finalize(prepared: PreparedCredentialRequestProofBatch): IdkResult<CreatedProof, IdkError> {
        finalizeCalls++
        return Ok(CreatedProof(CredentialRequestProofs.jwt(listOf("prepared-proof"))))
    }
}

private class FlowRecordingNonceCommand : FlowFakeCommand<RequestNonceArgs, NonceResponse>(
    RequestNonceCommand.COMMAND_ID,
    typeToken(),
    typeToken(),
), RequestNonceCommand {
    override val commandId: String = RequestNonceCommand.COMMAND_ID
    var calls = 0
    override suspend fun execute(args: RequestNonceArgs): IdkResult<NonceResponse, IdkError> {
        calls++
        return Ok(NonceResponse("unexpected"))
    }
}

private class FlowRecordingCreateProofCommand : FlowFakeCommand<CreateCredentialRequestProofArgs, CreatedProof>(
    CreateCredentialRequestProofCommand.COMMAND_ID,
    typeToken(),
    typeToken(),
), CreateCredentialRequestProofCommand {
    override val commandId: String = CreateCredentialRequestProofCommand.COMMAND_ID
    var calls = 0
    override suspend fun execute(args: CreateCredentialRequestProofArgs): IdkResult<CreatedProof, IdkError> {
        calls++
        return Err(IdkError.fromString("proof rebuild was not expected", "UNEXPECTED_PROOF_REBUILD"))
    }
}

private class FlowRecordingCredentialCommand : FlowFakeCommand<RequestCredentialArgs, CredentialResponse>(
    RequestCredentialCommand.COMMAND_ID,
    typeToken(),
    typeToken(),
), RequestCredentialCommand {
    override val commandId: String = RequestCredentialCommand.COMMAND_ID
    var calls = 0
    var rejectWithInvalidNonce = true
    var lastArgs: RequestCredentialArgs? = null
    override suspend fun execute(args: RequestCredentialArgs): IdkResult<CredentialResponse, IdkError> {
        calls++
        lastArgs = args
        if (!rejectWithInvalidNonce) {
            return Ok(CredentialResponse(credentials = listOf(CredentialResponseItem(JsonPrimitive("issued-credential")))))
        }
        return Err(
            IdkError.fromString(
                "issuer rejected the prepared nonce",
                "INVALID_NONCE_FRESH_NONCE_AVAILABLE:issuer-fresh",
            ).let { error -> IdkError(error.code, error.message, meta = mapOf("retry_after" to 7)) },
        )
    }
}

private class FlowNoopPollCommand : FlowFakeCommand<PollDeferredCredentialArgs, PollDeferredCredentialResult>(
    PollDeferredCredentialCommand.COMMAND_ID,
    typeToken(),
    typeToken(),
), PollDeferredCredentialCommand {
    override val commandId: String = PollDeferredCredentialCommand.COMMAND_ID
    override suspend fun execute(args: PollDeferredCredentialArgs): IdkResult<PollDeferredCredentialResult, IdkError> =
        error("deferred polling was not expected")
}

private class FlowNoopNotificationCommand : FlowFakeCommand<SendNotificationWithRetryArgs, Unit>(
    SendNotificationWithRetryCommand.COMMAND_ID,
    typeToken(),
    typeToken(),
), SendNotificationWithRetryCommand {
    override val commandId: String = SendNotificationWithRetryCommand.COMMAND_ID
    override suspend fun execute(args: SendNotificationWithRetryArgs): IdkResult<Unit, IdkError> =
        error("notification was not expected")
}

private class FlowRecordingSessionStore : Oid4vciHolderSessionStore {
    val updates = mutableListOf<Oid4vciHolderSession>()
    private val session = Oid4vciHolderSession(
        sessionId = "flow-session",
        issuerUrl = "https://issuer.example",
        credentialConfigurationIds = emptyList(),
        status = Oid4vciHolderSessionStatus.CREATED,
        createdAt = 1,
    )
    override suspend fun create(session: Oid4vciHolderSession) = Ok(session)
    override suspend fun get(sessionId: String) = Ok(session.takeIf { it.sessionId == sessionId })
    override suspend fun update(session: Oid4vciHolderSession) = Ok(session).also { updates += session }
}

private object FlowTestConfig : Oid4vciHolderConfig {
    override val clientId: String? = null
    override val preferredFormat: String? = null
    override val autoRequestNonce: Boolean = true
}

private class FlowTestSessionExecution(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionExecution {
    override val sessionContextManager: SessionContextManager get() = error("not needed")
    override val log: SessionLogService = FlowNoopLogService(sessionContext)
    override val conf: ContextConfig = FlowNoopContextConfig()
}

private class FlowNoopLogService(override val sessionContext: SessionContext) : SessionLogService {
    override val id = "flow-test-log"
    override val isEnabled = false
    override val scope = IdkScope.SESSION
    override val logManager: SessionLogManager
        get() = throw NotImplementedError("not needed")
    override suspend fun setConfig(config: LoggerConfig) = this
    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)
    override fun toAsync(): AsyncLogService = throw NotImplementedError("not needed")
}

private class FlowNoopContextConfig : ContextConfig {
    override val app: AppConfigService get() = error("not needed")
    override val tenant: TenantConfigService get() = error("not needed")
    override val principal: PrincipalConfigService get() = error("not needed")
    override fun conf(level: ConfigLevel): ConfigService = error("not needed")
}
