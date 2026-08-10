/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.methods.webvh.resolver

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.did.methods.webvh.command.CreateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.CreateWitnessProofServiceCommand
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.FetchWebvhLogServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileServiceCommand
import com.sphereon.did.methods.webvh.command.ValidateWebvhTrustServiceCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Acceptance test for the read/write split: a verifier-only build that
 * includes ONLY `:lib:did:methods:webvh:public` + `:lib:did:methods:webvh:resolver`
 * + the two data-integrity-proof modules (and standard core/crypto/HTTP/DI
 * infrastructure) MUST compile and produce a working session graph.
 *
 * This test is in `:resolver`'s jvmTest, so its classpath does not include
 * `:provider` (lifecycle commands) or `:rest:server` (HTTP adapter that pulls
 * ktor-server). If a future change accidentally introduces a dependency from
 * `:resolver` onto `:provider` (or onto any ktor-server type), this test
 * will fail to compile.
 *
 * Behaviour asserted:
 * 1. The verifier-only Metro graph composes (proves the read-side module
 *    set is self-sufficient).
 * 2. `did.webvh.fetch-log` resolves through the [SessionScopedCommandRegistry]
 *    (the only webvh command available on the verifier classpath).
 * 3. None of the five lifecycle commands (`did.webvh.create`,
 *    `did.webvh.update`, `did.webvh.deactivate`,
 *    `did.webvh.create-witness-proof`, `did.webvh.update-witness-file`) are
 *    registered — confirming `:provider` really is absent from the verifier
 *    classpath.
 */
class WebvhVerifierBuildSmokeTest {
    private lateinit var app: AppGraph
    private lateinit var registry: SessionScopedCommandRegistry

    @BeforeTest
    fun setUp() {
        // No KMS configuration: verifiers don't need signing keys, and the verifier
        // classpath does not include any KMS provider module.
        app = createWebvhVerifierTestAppGraph()
        app.userContextManager.destroyAll()
        val session =
            app.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId("verifier-smoke", principalType = com.sphereon.di.context.PrincipalType.USER)
        registry = (session.graph as SessionScopedCommandRegistry.Graph).sessionScopedCommandRegistry
    }

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) {
            app.userContextManager.destroyAll()
        }
    }

    @Test
    fun fetchLogCommandIsAvailable() {
        assertTrue(registry.has(FetchWebvhLogServiceCommand.COMMAND_ID))
        assertNotNull(registry.get(FetchWebvhLogServiceCommand.COMMAND_ID))
    }

    @Test
    fun validateTrustCommandIsAvailable() {
        // Bridge to trust.did.validate; available on verifier classpath because
        // :resolver depends on :lib-trust-did, and trust validation is a
        // read-side concern (no signing needed).
        assertTrue(registry.has(ValidateWebvhTrustServiceCommand.COMMAND_ID))
        assertNotNull(registry.get(ValidateWebvhTrustServiceCommand.COMMAND_ID))
    }

    @Test
    fun lifecycleCommandsAreNotOnTheVerifierClasspath() {
        val lifecycleCommandIds =
            listOf(
                CreateWebvhDidServiceCommand.COMMAND_ID,
                UpdateWebvhDidServiceCommand.COMMAND_ID,
                DeactivateWebvhDidServiceCommand.COMMAND_ID,
                CreateWitnessProofServiceCommand.COMMAND_ID,
                UpdateWitnessFileServiceCommand.COMMAND_ID,
            )
        for (id in lifecycleCommandIds) {
            assertFalse(
                registry.has(id),
                "Lifecycle command '$id' must NOT be on a verifier classpath. " +
                    "Did :resolver pick up a transitive dep on :provider?",
            )
        }
    }
}

@DependencyGraph(AppScope::class)
internal abstract class WebvhVerifierTestAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): WebvhVerifierTestAppGraph
    }
}

internal fun createWebvhVerifierTestAppGraph(): WebvhVerifierTestAppGraph {
    val graph =
        createGraphFactory<WebvhVerifierTestAppGraph.Factory>().create(
            application = Any(),
            appId = "webvh-verifier-test",
            profile = "test",
            version = "0.13.0-test",
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
