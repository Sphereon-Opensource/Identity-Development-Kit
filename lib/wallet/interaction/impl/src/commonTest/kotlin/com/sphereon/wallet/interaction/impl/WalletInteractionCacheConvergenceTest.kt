package com.sphereon.wallet.interaction.impl

import com.sphereon.core.api.Ok
import com.sphereon.wallet.interaction.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WalletInteractionCacheConvergenceTest {
    @Test fun persistedTerminalDecisionWinsOverCachedWaitingStateBeforeDispatch() = runTest {
        val sessions = InMemoryWalletInteractionSessionStore()
        val privateSessions = InMemoryWalletInteractionPrivateSessionStore()
        val engine = DefaultWalletInteractionEngine(
            adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
            sensitiveInputAuthority = StoreBackedWalletInteractionSensitiveInputAuthority(privateSessions),
            privateSessionStore = privateSessions, sessionStore = sessions,
            launchAuthorities = setOf(WalletInteractionLaunchAuthority { Ok(null) }),
        )
        val started = engine.start(WalletInteractionInput("wallet", WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x")))
        val observed = engine.observe(started.sessionId)
        val stored = assertNotNull(sessions.load(started.sessionId))
        val denied = stored.state.copy(status = WalletInteractionStatus.Failed, terminal = true, revision = stored.state.revision + 1)
        sessions.save(stored.copy(state = denied))

        engine.dispatch(started.sessionId, WalletInteractionAction.continueFlow())
        assertEquals(denied, engine.load(started.sessionId).state)
        assertEquals(denied, observed.value)
        assertEquals(denied, sessions.load(started.sessionId)?.state)
    }
}
