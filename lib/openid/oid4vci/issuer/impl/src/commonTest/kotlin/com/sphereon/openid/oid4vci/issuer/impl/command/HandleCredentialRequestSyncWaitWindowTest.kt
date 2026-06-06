/*
 * © 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.attribute.flow.AttributeProvenanceRef
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.issuance.pipeline.callback.CallbackCoordinator
import com.sphereon.credential.issuance.pipeline.command.BindingCompletenessVerdict
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessArgs
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessCommand
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessResult
import com.sphereon.crypto.jose.jwe.CreateJweCompactArgs
import com.sphereon.crypto.jose.jwe.CreateJweJsonArgs
import com.sphereon.crypto.jose.jwe.CreateJweJsonGeneralArgs
import com.sphereon.crypto.jose.jwe.DecryptJweArgs
import com.sphereon.crypto.jose.jwe.JweCompact
import com.sphereon.crypto.jose.jwe.JweDecryptionResult
import com.sphereon.crypto.jose.jwe.JweJsonFlattened
import com.sphereon.crypto.jose.jwe.JweJsonGeneral
import com.sphereon.crypto.jose.jwe.JweService
import com.sphereon.crypto.jose.jwe.PrepareJweArgs
import com.sphereon.crypto.jose.jwe.PreparedJwe
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.issuer.attribute.CredentialAttributeContribution
import com.sphereon.openid.oid4vci.issuer.attribute.CredentialAttributeContributor
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.format.CredentialEnvelope
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.openid.oid4vci.issuer.impl.encryption.CredentialResponseEncryptor
import com.sphereon.openid.oid4vci.issuer.impl.nonce.NonceManager
import com.sphereon.openid.oid4vci.issuer.impl.proof.ProofVerifier
import com.sphereon.openid.oid4vci.issuer.store.CredentialNonceStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionStatus
import com.sphereon.openid.oid4vci.issuer.store.NonceEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Exercises the §6.5.7 sync-wait window inside [HandleCredentialRequestCommandImpl]: when the
 * attribute contributor reports a non-empty `pendingAsyncCallbackSources` and a
 * [CallbackCoordinator] is wired, the command must hold its response open up to the contributor's
 * `syncWaitWindow` for those sources to land via the callback endpoint. If the await resolves
 * in time, the contributor is re-run and issuance completes synchronously; if it times out the
 * deferral decision tree fires as if no fast-path existed.
 */
class HandleCredentialRequestSyncWaitWindowTest {
    private val configId = "TestCredential"
    private val pendingSource = AttributeProvenanceRef("source-a")

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

    private class CountingFormatHandler : CredentialFormatHandler {
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
            return Ok(
                CredentialEnvelope(
                    credential = JsonPrimitive("issued-credential"),
                    format = supportedFormat,
                ),
            )
        }
    }

    private class FakeConfigProvider : Oid4vciIssuerConfigProvider {
        override val issuerIdentifier: String = "https://test.example/oid4vci"
        override val credentialConfigurations: Map<String, CredentialConfigurationSupported> = emptyMap()
        override val authorizationServers: List<String>? = null
        override val display: List<com.sphereon.openid.oid4vc.common.DisplayProperties>? = null
    }

    private class NoOpNonceStore : CredentialNonceStore {
        override suspend fun create(
            nonce: String,
            ttlSeconds: Long,
        ): IdkResult<NonceEntry, IdkError> = Ok(NonceEntry(nonce = nonce, createdAt = 0, expiresAt = ttlSeconds))

        override suspend fun consume(nonce: String): IdkResult<NonceEntry?, IdkError> = Ok(null)
    }

    private object ThrowingJweService : JweService {
        override val commands: JweService.Commands get() = throw UnsupportedOperationException("not used")

        override suspend fun prepareJwe(args: PrepareJweArgs): IdkResult<PreparedJwe, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun createJweCompact(args: CreateJweCompactArgs): IdkResult<JweCompact, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun createJweJsonFlattened(args: CreateJweJsonArgs): IdkResult<JweJsonFlattened, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun createJweJsonGeneral(args: CreateJweJsonGeneralArgs): IdkResult<JweJsonGeneral, IdkError> = throw UnsupportedOperationException("not used")

        override suspend fun decryptJwe(args: DecryptJweArgs): IdkResult<JweDecryptionResult, IdkError> = throw UnsupportedOperationException("not used")
    }

    /** Returns a fixed set of verdicts, mirroring the EDK completeness command. */
    private class FakeCompletenessCommand(
        private val verdicts: List<BindingCompletenessVerdict>,
    ) : EvaluateAttributeCompletenessCommand {
        override val inputTypeToken: TypeToken<EvaluateAttributeCompletenessArgs> = typeToken()
        override val outputTypeToken: TypeToken<EvaluateAttributeCompletenessResult> = typeToken()
        override val isEnabled: Boolean = true

        override suspend fun execute(args: EvaluateAttributeCompletenessArgs,): IdkResult<EvaluateAttributeCompletenessResult, IdkError> = Ok(EvaluateAttributeCompletenessResult(verdicts = verdicts))

        override suspend fun supports(args: Any): Boolean = args is EvaluateAttributeCompletenessArgs
    }

    /**
     * Returns [pendingOnFirstCall] from the first [contribute] call and [pendingOnSecondCall]
     * from every subsequent call — modelling "the callback has landed and the second pass sees no
     * more pending sources".
     */
    private class TwoPhaseContributor(
        private val pendingOnFirstCall: Set<AttributeProvenanceRef>,
        private val pendingOnSecondCall: Set<AttributeProvenanceRef> = emptySet(),
        private val syncWaitWindow: Duration = Duration.ZERO,
    ) : CredentialAttributeContributor {
        var calls = 0

        override suspend fun contribute(
            session: IssuanceSession,
            tokenContext: ValidatedTokenContext,
            credentialConfigurationId: String,
        ): IdkResult<CredentialAttributeContribution, IdkError> {
            calls++
            val pending = if (calls == 1) pendingOnFirstCall else pendingOnSecondCall
            return Ok(
                CredentialAttributeContribution(
                    attributes = emptyMap(),
                    pendingAsyncCallbackSources = pending,
                    syncWaitWindow = syncWaitWindow,
                ),
            )
        }
    }

    /** Resolves [awaitContribution] after [resolveAfter] of virtual delay. */
    private class FakeCoordinator(
        private val resolveAfter: Duration,
    ) : CallbackCoordinator {
        var awaitCalls = 0
        var notifyCalls = 0

        override suspend fun notifyContribution(
            correlationId: String,
            sourceId: String,
        ) {
            notifyCalls++
        }

        override suspend fun awaitContribution(
            correlationId: String,
            sourceId: String,
        ) {
            awaitCalls++
            delay(resolveAfter)
        }
    }

    private fun fixedClock(): Clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochSeconds(1_700_000_000)
        }

    private fun session(): IssuanceSession =
        IssuanceSession(
            sessionId = "session-1",
            issuerId = "https://test.example/oid4vci",
            credentialConfigurationIds = listOf(configId),
            status = IssuanceSessionStatus.CREDENTIAL_REQUESTED,
            pipelineCorrelationId = "corr-1",
            createdAt = 0,
            expiresAt = Long.MAX_VALUE,
        )

    private fun command(
        contributor: CredentialAttributeContributor,
        completenessCommand: EvaluateAttributeCompletenessCommand?,
        coordinator: CallbackCoordinator?,
        sessionStore: RecordingSessionStore,
        deferredStore: RecordingDeferredStore,
        formatHandler: CountingFormatHandler,
    ): HandleCredentialRequestCommandImpl {
        val configProvider = FakeConfigProvider()
        return HandleCredentialRequestCommandImpl(
            execution = TestSessionExecution(),
            asBridge = FakeAsBridge(),
            proofVerifiers = emptySet(),
            nonceManager = NonceManager(NoOpNonceStore()),
            formatHandlers = setOf(formatHandler),
            attributeContributor = contributor,
            sessionStore = sessionStore,
            deferredStore = deferredStore,
            encryptor = CredentialResponseEncryptor(ThrowingJweService, configProvider),
            issuerConfigProvider = configProvider,
            evaluateAttributeCompletenessCommand = completenessCommand,
            callbackCoordinator = coordinator,
            clock = fixedClock(),
        )
    }

    private fun request(): HandleCredentialRequestArgs =
        HandleCredentialRequestArgs(
            accessToken = "test-access-token",
            credentialRequest =
                CredentialRequest(
                    credentialConfigurationId = configId,
                    format = CredentialFormat.SD_JWT_DC.value,
                ),
            credentialConfigurations =
                mapOf(
                    configId to
                        CredentialConfigurationSupported(
                            format = CredentialFormat.SD_JWT_DC.value,
                            vct = "https://test.example/vct",
                        ),
                ),
        )

    // ------------------------------------------------------------------------
    // (a) callback lands inside the window → synchronous issuance
    // ------------------------------------------------------------------------

    @Test
    fun callbackLandingInsideWindowYieldsSynchronousIssuance() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session())
            val deferredStore = RecordingDeferredStore()
            val formatHandler = CountingFormatHandler()
            val contributor =
                TwoPhaseContributor(
                    pendingOnFirstCall = setOf(pendingSource),
                    pendingOnSecondCall = emptySet(),
                    syncWaitWindow = 2.seconds,
                )
            val coordinator = FakeCoordinator(resolveAfter = 100.milliseconds)
            val completeness =
                FakeCompletenessCommand(
                    listOf(BindingCompletenessVerdict(bindingId = configId, complete = true)),
                )

            val result =
                command(
                    contributor = contributor,
                    completenessCommand = completeness,
                    coordinator = coordinator,
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    formatHandler = formatHandler,
                ).execute(request())

            assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
            assertNotNull(result.value.credentials, "synchronous issuance returns credentials")
            assertNull(result.value.transactionId, "no deferral so no transaction_id")
            assertEquals(1, formatHandler.issueCount, "format handler must have issued the credential")
            assertTrue(deferredStore.created.isEmpty(), "no DeferredCredentialEntry must be created on the fast-path")
            assertEquals(1, coordinator.awaitCalls, "coordinator.awaitContribution must be called once per pending source")
            assertEquals(2, contributor.calls, "contributor re-runs after the wait resolves")
        }

    // ------------------------------------------------------------------------
    // (b) callback does NOT land within the window → fall-through to deferral
    // ------------------------------------------------------------------------

    @Test
    fun callbackMissingTheWindowFallsThroughToDeferral() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session())
            val deferredStore = RecordingDeferredStore()
            val formatHandler = CountingFormatHandler()
            val contributor =
                TwoPhaseContributor(
                    pendingOnFirstCall = setOf(pendingSource),
                    // If the await actually returned, the second pass would clear pending; we should
                    // never see that here because the await times out first.
                    pendingOnSecondCall = emptySet(),
                    syncWaitWindow = 100.milliseconds,
                )
            val coordinator = FakeCoordinator(resolveAfter = 5.seconds)
            // Mark the binding incomplete-but-deferrable so the §6.1 path fires when the wait times out.
            val completeness =
                FakeCompletenessCommand(
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
                    contributor = contributor,
                    completenessCommand = completeness,
                    coordinator = coordinator,
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    formatHandler = formatHandler,
                ).execute(request())

            assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
            assertNotNull(result.value.transactionId, "deferred response must carry a transaction_id")
            assertNotNull(result.value.interval, "deferred response must carry a poll interval")
            assertNull(result.value.credentials, "deferred response must not carry credentials")
            assertEquals(0, formatHandler.issueCount, "format handler must NOT be dispatched when deferring")
            assertEquals(1, deferredStore.created.size, "exactly one DeferredCredentialEntry must be created")
            assertEquals(
                IssuanceSessionStatus.DEFERRED,
                sessionStore.updated.last().status,
                "session status becomes DEFERRED on timeout fall-through",
            )
            assertEquals(1, contributor.calls, "on timeout the contributor is NOT re-run; the initial contribution is used")
        }

    // ------------------------------------------------------------------------
    // (c) zero wait window → degenerate no-op, straight to the §6.1 path
    // ------------------------------------------------------------------------

    @Test
    fun zeroSyncWaitWindowSkipsTheFastPath() =
        runTest {
            val sessionStore = RecordingSessionStore()
            sessionStore.create(session())
            val deferredStore = RecordingDeferredStore()
            val formatHandler = CountingFormatHandler()
            val contributor =
                TwoPhaseContributor(
                    pendingOnFirstCall = setOf(pendingSource),
                    syncWaitWindow = Duration.ZERO,
                )
            val coordinator = FakeCoordinator(resolveAfter = 100.milliseconds)
            val completeness =
                FakeCompletenessCommand(
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
                    contributor = contributor,
                    completenessCommand = completeness,
                    coordinator = coordinator,
                    sessionStore = sessionStore,
                    deferredStore = deferredStore,
                    formatHandler = formatHandler,
                ).execute(request())

            assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
            assertNotNull(result.value.transactionId, "ZERO window must defer immediately")
            assertEquals(0, coordinator.awaitCalls, "ZERO window must skip the coordinator entirely")
            assertEquals(1, contributor.calls, "no fast-path → contributor runs exactly once")
        }
}
