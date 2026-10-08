/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.methods.webvh.provider

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.app.AppGraph
import com.sphereon.did.methods.webvh.command.CreateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.CreateWitnessProofServiceCommand
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.FetchWebvhLogServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileServiceCommand
import com.sphereon.did.methods.webvh.provider.testutil.createWebvhProviderTestAppGraph
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test that composes the real Metro DI graph for the `did:webvh`
 * provider stack and asserts every lifecycle command is resolvable via the
 * [SessionScopedCommandRegistry].
 *
 * This is the "DI wiring is correct" acceptance check — if a
 * `@ContributesBinding` is missing, a scope mismatch is introduced, or a
 * `RegistrableServiceCommandDescriptor` is dropped from
 * `WebvhProviderCommandDescriptors` / `WebvhResolverCommandDescriptors`,
 * this test will fail at graph creation or command lookup.
 *
 * For the full real-cryptography sign + replay round-trip (now possible
 * since the OSS software KMS supports Ed25519 / Ed448), see
 * `WebvhCreationE2ETest`.
 */
class WebvhDiGraphCompositionTest {
    private lateinit var app: AppGraph
    private lateinit var registry: SessionScopedCommandRegistry

    @BeforeTest
    fun setUp() {
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.softwaretest.type" to "software",
                "kms.providers.softwaretest.id" to "softwaretest",
                "kms.providers.softwaretest.keystore.type" to "memory",
                "kms.providers.softwaretest.keystore.id" to "test-memory-keystore",
                "kms.providers.softwaretest.keystore.keyVisibility" to "private",
                "kms.providers.softwaretest.keystore.overwriteAlias" to "true",
            ),
        )

        app = createWebvhProviderTestAppGraph(testInstance = this)
        app.userContextManager.destroyAll()

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("webvh-di-composition-test", principalType = com.sphereon.di.context.PrincipalType.USER)
        registry = (session.graph as SessionScopedCommandRegistry.Graph).sessionScopedCommandRegistry
    }

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) {
            app.userContextManager.destroyAll()
        }
    }

    @Test
    fun graphResolvesAllSixWebvhCommandIds() {
        val expected =
            listOf(
                CreateWebvhDidServiceCommand.COMMAND_ID,
                UpdateWebvhDidServiceCommand.COMMAND_ID,
                DeactivateWebvhDidServiceCommand.COMMAND_ID,
                CreateWitnessProofServiceCommand.COMMAND_ID,
                UpdateWitnessFileServiceCommand.COMMAND_ID,
                FetchWebvhLogServiceCommand.COMMAND_ID,
            )
        for (id in expected) {
            assertTrue(registry.has(id), "Command not registered: $id (registered: ${registry.listCommandIds()})")
            assertNotNull(registry.get(id), "Command resolved to null: $id")
        }
    }
}
