/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.memory.InMemoryKvBackingStorageImpl
import com.sphereon.data.store.kv.memory.InMemoryKvStoreFactoryImpl
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.wallet.interaction.GetWalletInteractionEventsArgs
import com.sphereon.wallet.interaction.BeginWalletAppOutcomeEvidenceArgs
import com.sphereon.wallet.interaction.RecordWalletAppOutcomeEvidenceArgs
import com.sphereon.wallet.interaction.RegisterWalletInteractionSensitiveInputArgs
import com.sphereon.wallet.interaction.VerifiedWalletAppOutcomeEvidence
import com.sphereon.wallet.interaction.WalletAppOutcomeEvidenceChallenge
import com.sphereon.wallet.interaction.WalletAppOutcomeEvidenceSummary
import com.sphereon.wallet.interaction.WalletAppOutcomeEvidenceVerifier
import com.sphereon.wallet.interaction.ListWalletInteractionActivityArgs
import com.sphereon.wallet.interaction.SubmitWalletInteractionActionArgs
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionFlowKind
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputPurpose
import com.sphereon.wallet.interaction.WalletSecurityAssurance
import com.sphereon.wallet.interaction.WalletSecurityGrant
import com.sphereon.wallet.interaction.WalletCounterpartyAssociationRequest
import com.sphereon.wallet.interaction.WalletCounterpartyEncounterRegistry
import com.sphereon.wallet.interaction.WalletCounterpartyEncounterRequest
import com.sphereon.wallet.interaction.WalletCounterpartyEncounterResult
import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletCounterpartySummary
import com.sphereon.wallet.interaction.WalletProtocol
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletInteractionServiceCommandImplsTest {
    @Test
    fun resolveCounterpartyEncounterCommandForwardsOnlyTheTypedCounterpartySummary() =
        runTest {
            val request =
                WalletCounterpartyEncounterRequest(
                    walletUnitId = WALLET_UNIT_ID,
                    protocol = WalletProtocol.OID4VCI,
                    counterparty =
                        WalletCounterpartySummary(
                            role = WalletCounterpartyRole.ISSUER,
                            identifier = "https://issuer.example",
                            displayName = "Example issuer",
                        ),
                )
            var invocations = 0
            var received: WalletCounterpartyEncounterRequest? = null
            val registry =
                object : WalletCounterpartyEncounterRegistry {
                    override suspend fun encounter(input: WalletCounterpartyEncounterRequest): WalletCounterpartyEncounterResult {
                        invocations += 1
                        received = input
                        return WalletCounterpartyEncounterResult(
                            counterparty = input.counterparty.copy(partyId = "party-issuer"),
                            resolved = true,
                            organizationCreated = false,
                            firstInteraction = true,
                        )
                    }

                    override suspend fun resolveAssociation(request: WalletCounterpartyAssociationRequest): WalletCounterpartyEncounterResult =
                        error("unused")
                }

            val result =
                ResolveWalletCounterpartyEncounterCommandImpl(TestSessionExecution(), registry)
                    .execute(request)
                    .getOrThrow()

            assertEquals(1, invocations)
            assertEquals(request, received)
            assertEquals("party-issuer", result.counterparty.partyId)
            assertEquals(true, result.resolved)
        }

    @Test
    fun registerSensitiveInputCommandPreservesSecurityGrantsAsTypedOneUseValues() =
        runTest {
            val authority = StoreBackedWalletInteractionSensitiveInputAuthority(InMemoryWalletInteractionPrivateSessionStore())
            val engine =
                testWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sensitiveInputAuthority = authority,
                    sessionStore = InMemoryWalletInteractionSessionStore(),
                )
            val started =
                engine.start(
                    WalletInteractionInput(
                        walletUnitId = WALLET_UNIT_ID,
                        entryPoint = WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x"),
                    ),
                )
            val grant =
                WalletSecurityGrant(
                    grantId = "security-challenge-a",
                    assurance = WalletSecurityAssurance.PASSKEY,
                    expiresAtEpochSeconds = 1_800_000_000,
                    evidence =
                        mapOf(
                            "challenge_id" to "security-challenge-a",
                            "wallet_unit_id" to WALLET_UNIT_ID,
                            "operation_binding" to "operation-binding-a",
                        ),
                )
            val result =
                RegisterWalletInteractionSensitiveInputCommandImpl(TestSessionExecution(), engine, authority)
                    .execute(
                        RegisterWalletInteractionSensitiveInputArgs(
                            walletUnitId = WALLET_UNIT_ID,
                            sessionId = started.sessionId,
                            purpose = WalletInteractionSensitiveInputPurpose.INTERACTION_SECURITY_GRANT,
                            value = Json.encodeToString(grant),
                        ),
                    ).getOrThrow()

            assertEquals(grant, authority.consumeSecurityGrant(started.sessionId, result.ref))
            assertEquals(null, authority.consumeSecurityGrant(started.sessionId, result.ref), "security grants must remain one-use")
        }

    @Test
    fun verifiedWalletAppOutcomeCommandsPersistOnlyTheMinimizedTerminalProjectionAndReplayIdempotently() =
        runTest {
            val execution = TestSessionExecution()
            val sessionStore = InMemoryWalletInteractionSessionStore()
            val engine = testWalletInteractionEngine(
                sensitiveInputAuthority = testSensitiveInputAuthority(),
                sessionStore = sessionStore,
            )
            val summary = WalletAppOutcomeEvidenceSummary(
                walletInstanceId = "wallet-instance-a",
                walletUnitId = WALLET_UNIT_ID,
                appRegistrationId = "app-registration-a",
                sessionId = WalletInteractionSessionId("wallet-app-outcome-a"),
                flowKind = WalletInteractionFlowKind.CredentialPresent,
                status = WalletInteractionStatus.Completed,
                policyRevision = 7,
                outcomeDigest = "sha256:terminal-outcome",
                recordedAtEpochSeconds = 1_800_000_000,
                idempotencyKey = "outcome-a",
            )
            var verificationCount = 0
            val verifier = object : WalletAppOutcomeEvidenceVerifier {
                override suspend fun begin(summary: WalletAppOutcomeEvidenceSummary): IdkResult<WalletAppOutcomeEvidenceChallenge, IdkError> =
                    Ok(
                        WalletAppOutcomeEvidenceChallenge(
                            challengeId = "challenge-a",
                            challenge = "opaque-challenge",
                            rpId = "wallet.example.test",
                            allowedOrigins = setOf("https://wallet.example.test"),
                            userVerification = "required",
                            expiresAtEpochSeconds = 1_800_000_300,
                            credentialIds = setOf("credential-a"),
                        ),
                    )

                override suspend fun verify(args: RecordWalletAppOutcomeEvidenceArgs): IdkResult<VerifiedWalletAppOutcomeEvidence, IdkError> {
                    verificationCount += 1
                    return Ok(
                        VerifiedWalletAppOutcomeEvidence(
                            args.summary,
                            "wallet-app-outcome-evidence-a",
                            "WEBAUTHN_ASSERTION",
                            1_800_000_001,
                        ),
                    )
                }
            }
            val challenge = BeginWalletAppOutcomeEvidenceCommandImpl(execution, verifier)
                .execute(BeginWalletAppOutcomeEvidenceArgs(summary)).getOrThrow()
            assertEquals(setOf("credential-a"), challenge.challenge.credentialIds)
            val recordArgs = RecordWalletAppOutcomeEvidenceArgs(
                summary = summary,
                challengeId = challenge.challenge.challengeId,
                credentialId = "credential-a",
                authenticatorData = "authenticator-data",
                clientDataJson = "client-data-json",
                signature = "signature",
                origin = "https://wallet.example.test",
                rpId = "wallet.example.test",
                userVerified = true,
            )
            val command = RecordWalletAppOutcomeEvidenceCommandImpl(execution, verifier, engine)
            val first = command.execute(recordArgs).getOrThrow()
            val replay = command.execute(recordArgs).getOrThrow()
            val activity = ListWalletInteractionActivityCommandImpl(execution, engine)
                .execute(ListWalletInteractionActivityArgs(WALLET_UNIT_ID)).getOrThrow().entries.single()

            assertEquals(first, replay)
            assertEquals(1, verificationCount)
            assertEquals(1_800_000_000L, activity.recordedAtEpochSeconds)
            assertEquals(WalletInteractionStatus.Completed, activity.status)
            assertEquals(WalletInteractionFlowKind.CredentialPresent, activity.flowKind)
            assertEquals(emptySet(), activity.credentialRecordIds)
            assertEquals(null, activity.counterparty)
        }

    @Test
    fun listActivityCommandDelegatesThroughTheSessionEngine() =
        runTest {
            val execution = TestSessionExecution()
            val engine =
                testWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sensitiveInputAuthority = testSensitiveInputAuthority(),
                    sessionStore = KvWalletInteractionSessionStore(createTestKvStore(InMemoryKvBackingStorageImpl())),
                )
            val started =
                StartWalletInteractionCommandImpl(execution, engine)
                    .execute(
                        WalletInteractionInput(
                            walletUnitId = WALLET_UNIT_ID,
                            entryPoint = WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x"),
                        ),
                    ).getOrThrow()
            SubmitWalletInteractionActionCommandImpl(execution, engine)
                .execute(
                    SubmitWalletInteractionActionArgs(
                        walletUnitId = WALLET_UNIT_ID,
                        sessionId = started.sessionId,
                        action = WalletInteractionAction.continueFlow(),
                    ),
                ).getOrThrow()

            val result =
                ListWalletInteractionActivityCommandImpl(execution, engine)
                    .execute(ListWalletInteractionActivityArgs(WALLET_UNIT_ID))
                    .getOrThrow()

            assertEquals(1, result.entries.size)
            assertEquals(WalletInteractionStatus.Completed, result.entries.single().status)
            assertEquals(1L, result.nextSequence)
        }

    @Test
    fun observeEventsCommandStaysConnectedForLaterMutationAcrossStoreInstances() =
        runTest {
            val backingStorage = InMemoryKvBackingStorageImpl()
            val liveEventBus = ProcessLocalWalletInteractionLiveEventBus()
            val execution = TestSessionExecution()

            fun engine() =
                testWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sensitiveInputAuthority = testSensitiveInputAuthority(),
                    sessionStore = KvWalletInteractionSessionStore(createTestKvStore(backingStorage), liveEventBus = liveEventBus),
                )
            val startCommand = StartWalletInteractionCommandImpl(execution, engine())
            val observeCommand = ObserveWalletInteractionEventsCommandImpl(execution, engine())
            val submitCommand = SubmitWalletInteractionActionCommandImpl(execution, engine())
            val started =
                startCommand
                    .execute(
                        WalletInteractionInput(
                            walletUnitId = WALLET_UNIT_ID,
                            entryPoint = WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x"),
                        ),
                    ).getOrThrow()

            val pendingLiveEvent =
                async {
                    observeCommand
                        .executeStream(
                            GetWalletInteractionEventsArgs(
                                walletUnitId = WALLET_UNIT_ID,
                                sessionId = started.sessionId,
                                afterRevision = started.state.revision,
                            ),
                        ).getOrThrow()
                        .take(1)
                        .toList()
                        .single()
                }
            yield()

            submitCommand
                .execute(
                    SubmitWalletInteractionActionArgs(
                        walletUnitId = WALLET_UNIT_ID,
                        sessionId = started.sessionId,
                        action = WalletInteractionAction.continueFlow(),
                    ),
                ).getOrThrow()

            val event = withTimeout(1_000) { pendingLiveEvent.await() }

            assertEquals(started.state.revision + 1, event.revision)
            assertEquals(WalletInteractionStatus.Completed, event.state.status)
        }

    private fun createTestKvStore(backingStorage: InMemoryKvBackingStorageImpl): KvStore =
        InMemoryKvStoreFactoryImpl(backingStorage).create(
            InMemoryKvStoreConfig(id = "wallet-interaction-command-test", scopeBinding = KvStoreScopeBinding.APP),
            execution = null,
        )

    private fun testSensitiveInputAuthority() =
        StoreBackedWalletInteractionSensitiveInputAuthority(InMemoryWalletInteractionPrivateSessionStore())

    private class TestSessionExecution(
        override val sessionContext: SessionContext = NoOpSessionContext,
    ) : SessionExecution {
        override val sessionContextManager: SessionContextManager
            get() = throw NotImplementedError("Not needed for test")
        override val log: SessionLogService = NoOpSessionLogService(sessionContext)
        override val conf: ContextConfig = NoOpContextConfig()
    }

    private class NoOpSessionLogService(
        override val sessionContext: SessionContext,
    ) : SessionLogService {
        override val id: String = "wallet-interaction-command-test-log"
        override val isEnabled: Boolean = false
        override val scope: IdkScope = IdkScope.SESSION
        override val logManager: SessionLogManager
            get() = throw NotImplementedError("Not needed for test")

        override suspend fun setConfig(config: LoggerConfig): LogService = this

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

        override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for test")
    }

    private class NoOpContextConfig : ContextConfig {
        override val app: AppConfigService
            get() = throw NotImplementedError("Not needed for test")
        override val tenant: TenantConfigService
            get() = throw NotImplementedError("Not needed for test")
        override val principal: PrincipalConfigService
            get() = throw NotImplementedError("Not needed for test")

        override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for test")
    }

    private companion object {
        const val WALLET_UNIT_ID = "wallet-command-stream"
    }
}
