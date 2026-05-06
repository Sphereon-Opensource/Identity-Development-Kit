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
import com.sphereon.did.methods.webvh.command.CreateWebvhDidInput
import com.sphereon.did.methods.webvh.command.CreateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.provider.testutil.createWebvhProviderTestAppGraph
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the validation surface of the `did.webvh.create` command via the
 * resolved registry instance (real DI graph). The command's `validate(input)`
 * step runs before any KMS interaction, so these tests focus narrowly on
 * input rejection and do not exercise signing. For the full sign + replay
 * happy path see `WebvhCreationE2ETest`.
 */
class WebvhCreateCommandValidationTest {
    private lateinit var app: AppGraph
    private lateinit var createCommand: CreateWebvhDidServiceCommand

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
        val session =
            app.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId("webvh-create-validation-test")
        val registry = (session.graph as SessionScopedCommandRegistry.Graph).sessionScopedCommandRegistry
        val command = registry.get(CreateWebvhDidServiceCommand.COMMAND_ID)
        assertNotNull(command, "did.webvh.create must be resolvable from session registry")
        @Suppress("UNCHECKED_CAST")
        createCommand = command as CreateWebvhDidServiceCommand
    }

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) {
            app.userContextManager.destroyAll()
        }
    }

    @Test
    fun rejectsBlankDomain() =
        runTest {
            val result =
                createCommand.execute(
                    CreateWebvhDidInput(
                        domain = "  ",
                        updateKeyRefs = listOf("key-1"),
                        updateMultikeys = listOf("z6MkExample"),
                    ),
                )
            assertTrue(result.isErr, "blank domain must fail validation")
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
        }

    @Test
    fun rejectsEmptyUpdateKeyRefs() =
        runTest {
            val result =
                createCommand.execute(
                    CreateWebvhDidInput(
                        domain = "example.com",
                        updateKeyRefs = emptyList(),
                        updateMultikeys = emptyList(),
                    ),
                )
            assertTrue(result.isErr, "empty updateKeyRefs must fail validation")
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
        }

    @Test
    fun rejectsMismatchedKeyRefAndMultikeyCounts() =
        runTest {
            val result =
                createCommand.execute(
                    CreateWebvhDidInput(
                        domain = "example.com",
                        updateKeyRefs = listOf("key-1", "key-2"),
                        updateMultikeys = listOf("z6Mk1"),
                    ),
                )
            assertTrue(result.isErr, "mismatched updateKeyRefs/updateMultikeys must fail validation")
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
        }
}
