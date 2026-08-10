/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.iso18013

import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.cose.CoseKeyCborCodecImpl
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.wallet.wscd.testfixtures.TestWscdSupport
import com.sphereon.di.app.AppGraph
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.engagement.DeviceEngagementCborCodecImpl
import com.sphereon.mdoc.engagement.EngagementInstance
import com.sphereon.mdoc.engagement.MdocEngagementFactory
import com.sphereon.mdoc.engagement.MdocEngagementManager
import com.sphereon.mdoc.engagement.MdocEngagementManagerImpl
import com.sphereon.mdoc.engagement.MdocEngagementState
import com.sphereon.mdoc.transfer.DocumentProvider
import com.sphereon.mdoc.transfer.TransferManager
import com.sphereon.mdoc.transfer.reader.ReaderEngagementCborCodecImpl
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.ProtocolExecutionOwner
import com.sphereon.wallet.interaction.WalletInteractionFlowKind
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletSecurityGate
import com.sphereon.wallet.interaction.WalletProtocol
import com.sphereon.wallet.interaction.impl.DefaultWalletInteractionEngine
import com.sphereon.wallet.interaction.impl.InMemoryWalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.impl.InMemoryWalletInteractionSessionStore
import dev.whyoleg.cryptography.CryptographyProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import software.amazon.app.platform.scope.coroutine.CoroutineScopeScoped
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class Iso18013MdocEngagementManagerIntegrationTest {
    @Test
    fun startWithMdocWebsiteQrUsesRealMdocToAppPath() =
        runTest {
            val manager = createRealGraphMdocManager("iso18013-${Uuid.random()}")
            val adapter =
                Iso18013WalletInteractionProtocolAdapter(
                    disclosureExecutor = Iso18013DisclosureExecutor.notConfigured,
                    engagementManager = manager,
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("iso-real-path"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                )

            try {
                val session = adapter.start(context, WalletEntryPoint.rawQr(RDW_WEBSITE_READER_ENGAGEMENT))

                assertEquals(WalletInteractionStatus.DisclosureConsent, session.state.status, "error=${session.state.error?.code}")
                assertEquals(WalletProtocol.ISO18013, session.state.protocol)
                assertNotNull(manager.toAppEngagement.value, "real MdocEngagementManager.toApp should create a TO_APP engagement")
            } finally {
                manager.close()
            }
        }

    @Test
    fun neutralEngineUsesRealMdocDisclosureExecutorForDeviceResponse() =
        runTest {
            val logMessages = mutableListOf<String>()
            val transferCalls = mutableListOf<String>()
            val transfer = recordingTransferManager(transferCalls)
            val manager =
                createMdocManager(
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("iso18013-real-executor-test")),
                    logMessages = logMessages,
                    engagement = mockEngagement(transfer, transferCalls),
                )
            val engine =
                DefaultWalletInteractionEngine(
                    sensitiveInputAuthority = Iso18013TestSensitiveInputAuthority,
                    privateSessionStore = InMemoryWalletInteractionPrivateSessionStore(),
                    sessionStore = InMemoryWalletInteractionSessionStore(),
                    securityGate = WalletSecurityGate.allow,
                    adapters =
                        listOf(
                            Iso18013WalletInteractionProtocolAdapter(
                                engagementManager = manager,
                                disclosureExecutor = Iso18013MdocDisclosureExecutor(manager),
                            ),
                        ),
                )

            try {
                val session =
                    engine.start(
                        WalletInteractionInput(
                            walletUnitId = "wallet",
                            entryPoint = WalletEntryPoint.rawQr(RDW_WEBSITE_READER_ENGAGEMENT),
                        ),
                    )

                assertEquals(WalletProtocol.ISO18013, session.state.protocol)
                assertEquals(WalletInteractionStatus.DisclosureConsent, session.state.status, "error=${session.state.error?.code}, logs=$logMessages")

                engine.dispatch(session.sessionId, WalletInteractionAction.continueFlow())
                val completed = engine.observe(session.sessionId).value

                assertEquals(WalletInteractionStatus.Completed, completed.status, "error=${completed.error?.code}, logs=$logMessages")
                assertEquals(
                    listOf("start", "receiveDeviceRequest", "createResponse", "sendDeviceResponse"),
                    transferCalls,
                )
            } finally {
                manager.close()
            }
        }

    @Test
    fun disclosureExecutorRegistersResolvedDocumentProviderForDeviceResponse() =
        runTest {
            val logMessages = mutableListOf<String>()
            val transferCalls = mutableListOf<String>()
            val documentProvider =
                object : DocumentProvider {
                    override suspend fun getDocuments(selectorData: Any?) = emptySet<com.sphereon.mdoc.data.device.DocumentWithKeyAlias>()
                }
            val transfer = recordingTransferManager(transferCalls, expectedDocumentProvider = documentProvider)
            val manager =
                createMdocManager(
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("iso18013-provider-executor-test")),
                    logMessages = logMessages,
                    engagement = mockEngagement(transfer, transferCalls),
                )
            val executor =
                Iso18013MdocDisclosureExecutor(
                    engagementManager = manager,
                    documentProviderResolver = Iso18013DocumentProviderResolver { _, _ -> documentProvider },
                )
            val sessionId = WalletInteractionSessionId("iso-provider-executor")
            val privateSessionStore = InMemoryWalletInteractionPrivateSessionStore()
            privateSessionStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = Iso18013WalletInteractionProtocolAdapter.ADAPTER_ID,
                    values = mapOf("entry_point.raw" to RDW_WEBSITE_READER_ENGAGEMENT),
                ),
            )
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_BACKEND,
                    privateSessionStore = privateSessionStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = context.sessionId,
                    walletUnitId = context.walletUnitId,
                    status = WalletInteractionStatus.DisclosureConsent,
                    flowKind = WalletInteractionFlowKind.AttendedPresent,
                    protocol = WalletProtocol.ISO18013,
                    adapterId = Iso18013WalletInteractionProtocolAdapter.ADAPTER_ID,
                )

            try {
                val result = executor.sendDeviceResponse(context, state)

                assertEquals(Iso18013DisclosureExecutionResult.Sent(), result)
                assertEquals(
                    listOf("start", "receiveDeviceRequest", "registerCustomResponseSelectors", "createResponse:provider", "sendDeviceResponse"),
                    transferCalls,
                )
            } finally {
                manager.close()
            }
        }

    private fun createRealGraphMdocManager(sessionId: String): MdocEngagementManager {
        val app =
            createIso18013MdocIntegrationTestAppGraph(
                application = "Iso18013MdocEngagementManagerIntegrationTest",
                appId = "com.sphereon.wallet.interaction.iso18013-test",
                profile = "test",
                version = "0.1.0",
            )
        val session =
            app.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId(sessionId, principalType = com.sphereon.di.context.PrincipalType.USER)
        // Sanctioned test KMS wiring lives in the wscd test-fixtures module.
        val kms = TestWscdSupport.ensureSoftwareKmsProvider(app, session, "$sessionId-software-kms")
        return (session.graph as MdocEngagementManagerImpl.Graph).mdocEngagementManager
    }

    private fun createMdocManager(
        scope: CoroutineScope,
        logMessages: MutableList<String>,
        engagement: EngagementInstance = mockEngagement(),
    ): MdocEngagementManager {
        val holder = mockk<MdocEngagementFactory.Holder>()
        val factory = mockk<MdocEngagementFactory>(relaxed = true)
        coEvery { holder.createFromBuilder(any()) } returns engagement.asOkResult()
        every { factory.holder } returns holder

        val keyManager = createRealSoftwareKms("iso18013-${Uuid.random()}")

        val logService = mockLogService(logMessages)
        val scopeScoped =
            mockk<CoroutineScopeScoped>().also {
                every { it.coroutineContext } returns scope.coroutineContext + CoroutineName("iso18013-real-path-test")
                every { it.createChild() } returns scope
            }

        return MdocEngagementManagerImpl(
            factory = factory,
            kms = keyManager,
            logService = logService,
            coseKeyCborCodec = CoseKeyCborCodecImpl(),
            deviceEngagementCborCodec = DeviceEngagementCborCodecImpl(),
            readerEngagementCborCodec = ReaderEngagementCborCodecImpl(),
            scopeScoped = scopeScoped,
        )
    }

    private fun createRealSoftwareKms(sessionId: String): KeyManagerService {
        val app: AppGraph =
            createIso18013MdocIntegrationTestAppGraph(
                application = "Iso18013MdocEngagementManagerIntegrationTest",
                appId = "com.sphereon.wallet.interaction.iso18013-test",
                profile = "test",
                version = "0.1.0",
            )
        val session =
            app.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId(sessionId, principalType = com.sphereon.di.context.PrincipalType.USER)
        // Sanctioned test KMS wiring lives in the wscd test-fixtures module.
        val kms = TestWscdSupport.ensureSoftwareKmsProvider(app, session, "$sessionId-software-kms")
        return kms
    }

    private fun mockEngagement(
        transfer: TransferManager? = null,
        calls: MutableList<String> = mutableListOf(),
    ): EngagementInstance =
        mockk(relaxed = true) {
            every { id } returns Uuid.random()
            every { events } returns MutableSharedFlow()
            every { isActive } returns MutableStateFlow(true)
            every { isTransferInitialized() } returns false
            every { getCurrentState() } returns MdocEngagementState.INIT
            if (transfer != null) {
                coEvery { start() } coAnswers {
                    calls += "start"
                    transfer
                }
            }
        }

    private fun recordingTransferManager(
        calls: MutableList<String>,
        expectedDocumentProvider: DocumentProvider? = null,
    ): TransferManager {
        val request = DeviceRequest(original = null)
        val response = DeviceResponse(documents = null, original = null)
        return mockk(relaxed = true) {
            every { registerCustomResponseSelectors(any(), any(), any()) } answers {
                calls += "registerCustomResponseSelectors"
            }
            coEvery { receiveDeviceRequest() } coAnswers {
                calls += "receiveDeviceRequest"
                request
            }
            coEvery { createResponse(any(), any()) } coAnswers {
                val documentProvider = secondArg<DocumentProvider?>()
                calls += if (expectedDocumentProvider != null && documentProvider === expectedDocumentProvider) "createResponse:provider" else "createResponse"
                response
            }
            coEvery { sendDeviceResponse(any()) } coAnswers {
                calls += "sendDeviceResponse"
                7
            }
        }
    }

    private fun mockLogService(messages: MutableList<String>): SessionLogService {
        val log = TestLogService(messages)
        val logManager = TestSessionLogManager(log)
        return TestSessionLogService(logManager, messages)
    }

    private class TestSessionLogManager(
        private val log: LogService,
    ) : SessionLogManager {
        override suspend fun setGlobalConfig(config: LoggerConfig) = this

        override suspend fun getGlobalConfig() = LoggerConfig.Default

        override fun withTagAsync(
            tag: String,
            config: LoggerConfig?,
        ): AsyncLogService = log.toAsync()

        override fun withTag(
            tag: String,
            config: LoggerConfig?,
        ): LogService = log
    }

    private class TestSessionLogService(
        override val logManager: SessionLogManager,
        private val messages: MutableList<String>,
    ) : SessionLogService,
        LogService by TestLogService(messages) {
        override val id: String = "iso18013-real-path-test"
        override val scope: IdkScope = IdkScope.SESSION
    }

    private class TestLogService(
        private val messages: MutableList<String> = mutableListOf(),
    ) : LogService {
        override val sessionContext: SessionContext = NoOpSessionContext
        override val scope: IdkScope = IdkScope.SESSION
        override val id: String = "iso18013-real-path-test"
        override val isEnabled: Boolean = true

        override suspend fun setConfig(config: LoggerConfig): LogService = this

        override suspend fun getConfig(): LoggerConfig = LoggerConfig.Default

        override fun executeAsync(message: LogMessage) =
            Unit
                .also {
                    messages += "${message.level}: ${message.message}"
                }.asOkResult()

        override fun toAsync(): AsyncLogService = TestAsyncLogService(this)
    }

    private class TestAsyncLogService(
        private val syncLog: LogService = TestLogService(),
    ) : AsyncLogService {
        override val sessionContext: SessionContext = NoOpSessionContext
        override val scope: IdkScope = IdkScope.SESSION
        override val id: String = "iso18013-real-path-test"
        override val isEnabled: Boolean = true

        override suspend fun setConfig(config: LoggerConfig): AsyncLogService = this

        override suspend fun getConfig(): LoggerConfig = LoggerConfig.Default

        override suspend fun execute(args: LogMessage) = Unit.asOkResult()

        override fun toSync(): LogService = syncLog
    }

    private companion object {
        private const val RDW_WEBSITE_READER_ENGAGEMENT =
            "mdoc://owBjMS4xAYIB2BhYS6QBAiABIVggzzR7n13ilUKBaRhmnAApagKPiqn1qOfEkWQ_TKVq5UYiWCBc6ZWwMkjVLAlVIRLvAkslI04EjJVvUow8EHBY8bSggAKBgwQBoQB4Umh0dHBzOi8vcmVhZGVyLnJkdy5tZG9jLm9ubGluZS9hcGkvMi4wL0FubmV4QS8zNzhjM2U4Yi05ZmZjLTQyMDYtYWI3Yy1jMzY5N2FiOGVjYzU"
    }
}
