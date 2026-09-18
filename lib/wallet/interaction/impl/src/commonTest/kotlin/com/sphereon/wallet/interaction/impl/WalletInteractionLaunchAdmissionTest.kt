/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionLaunchAuthority
import com.sphereon.wallet.interaction.WalletInteractionProcessBinding
import com.sphereon.core.api.Ok
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/** Missing admission must not silently select ordinary-wallet compatibility. */
class WalletInteractionLaunchAdmissionTest {
    @Test
    fun admittedBindingRatherThanCallerClaimSurvivesEngineReload() = runTest {
        val sessions = InMemoryWalletInteractionSessionStore()
        val canonical = WalletInteractionProcessBinding(
            "tenant-b", "model-b", "representation", "case-b", "execution-original",
            "policy-b", 1, "present", "holder",
        )
        val engine = engine(sessions, setOf(WalletInteractionLaunchAuthority { Ok(canonical) }))
        val started = engine.start(WalletInteractionInput(
            "wallet-b", WalletEntryPoint.rawQr("unsupported"),
            processBinding = canonical.copy(executionId = "caller-substitution"),
        ))
        assertEquals(canonical, sessions.load(started.sessionId)?.input?.processBinding)
        assertEquals(started.state, engine(sessions).load(started.sessionId).state)
    }

    @Test
    fun ordinaryLaunchRequiresExplicitAuthorityPermission() = runTest {
        val sessions = InMemoryWalletInteractionSessionStore()
        val started = engine(sessions, setOf(WalletInteractionLaunchAuthority { Ok(null) }))
            .start(WalletInteractionInput("wallet-b", WalletEntryPoint.rawQr("unsupported")))
        assertEquals(started.sessionId, sessions.load(started.sessionId)?.sessionId)
        assertNull(sessions.load(started.sessionId)?.input?.processBinding)
    }

    private fun engine(
        sessions: WalletInteractionSessionStore,
        authorities: Set<WalletInteractionLaunchAuthority> = emptySet(),
    ): DefaultWalletInteractionEngine {
        val privateSessions = InMemoryWalletInteractionPrivateSessionStore()
        return DefaultWalletInteractionEngine(
            sensitiveInputAuthority = StoreBackedWalletInteractionSensitiveInputAuthority(privateSessions),
            privateSessionStore = privateSessions,
            sessionStore = sessions,
            launchAuthorities = authorities,
        )
    }

    @Test
    fun missingAuthorityCannotLaunchEvenWhenCallerOmitsBusinessContext() = runTest {
        assertUnadmittedLaunchIsNotPersisted(emptyMap())
    }

    @Test
    fun callerMetadataCannotAuthorizeBusinessLaunch() = runTest {
        assertUnadmittedLaunchIsNotPersisted(
            mapOf(
                "tenantId" to "tenant-b",
                "caseRef" to "case-b",
                "policyBindingRef" to "policy-b:1",
                "executionId" to "execution-b",
                "action" to "present",
                "authorized" to "true",
                "businessBindingRequired" to "false",
            ),
        )
    }

    private suspend fun assertUnadmittedLaunchIsNotPersisted(metadata: Map<String, String>) {
        val sessionId = WalletInteractionSessionId("unadmitted-launch")
        val sessions = InMemoryWalletInteractionSessionStore()
        val privateSessions = InMemoryWalletInteractionPrivateSessionStore()
        // Use the production constructor: test factories must not implicitly supply admission.
        val engine = DefaultWalletInteractionEngine(
            adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
            sessionIdGenerator = object : WalletInteractionSessionIdGenerator {
                override fun next() = sessionId
            },
            sensitiveInputAuthority = StoreBackedWalletInteractionSensitiveInputAuthority(privateSessions),
            privateSessionStore = privateSessions,
            sessionStore = sessions,
        )
        val result = runCatching {
            engine.start(
                WalletInteractionInput(
                    "wallet-b",
                    WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x"),
                    metadata = metadata,
                ),
            )
        }

        assertNull(sessions.load(sessionId), "Admission must precede persisted interaction state")
        assertTrue(sessions.events(sessionId).isEmpty(), "Unadmitted launches must not emit history")
        assertTrue(result.isFailure, "No contributed authority must not grant launch permission")
    }
}
