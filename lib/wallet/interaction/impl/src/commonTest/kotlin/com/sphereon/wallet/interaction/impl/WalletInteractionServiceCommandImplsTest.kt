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
import com.sphereon.wallet.interaction.SubmitWalletInteractionActionArgs
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletInteractionServiceCommandImplsTest {
    @Test
    fun observeEventsCommandStaysConnectedForLaterMutationAcrossStoreInstances() =
        runTest {
            val backingStorage = InMemoryKvBackingStorageImpl()
            val liveEventBus = ProcessLocalWalletInteractionLiveEventBus()
            val execution = TestSessionExecution()

            fun engine() =
                DefaultWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sessionStore = KvWalletInteractionSessionStore(createTestKvStore(backingStorage), liveEventBus = liveEventBus),
                )
            val startCommand = StartWalletInteractionCommandImpl(execution, engine())
            val observeCommand = ObserveWalletInteractionEventsCommandImpl(execution, engine())
            val submitCommand = SubmitWalletInteractionActionCommandImpl(execution, engine())
            val started =
                startCommand
                    .execute(
                        WalletInteractionInput(
                            walletInstanceId = WALLET_INSTANCE_ID,
                            entryPoint = WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x"),
                        ),
                    ).getOrThrow()

            val pendingLiveEvent =
                async {
                    observeCommand
                        .executeStream(
                            GetWalletInteractionEventsArgs(
                                walletInstanceId = WALLET_INSTANCE_ID,
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
                        walletInstanceId = WALLET_INSTANCE_ID,
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
        const val WALLET_INSTANCE_ID = "wallet-command-stream"
    }
}
