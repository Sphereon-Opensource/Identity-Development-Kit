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

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.crypto.core.generic.Multibase
import com.sphereon.crypto.core.generic.MultibaseEncoding
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyManagerServiceGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.did.methods.webvh.command.CreateWebvhDidInput
import com.sphereon.did.methods.webvh.command.CreateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.ReplayWebvhLogInput
import com.sphereon.did.methods.webvh.command.ReplayWebvhLogServiceCommand
import com.sphereon.did.methods.webvh.provider.testutil.createWebvhProviderTestAppGraph
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for `did.webvh.replay-log`: the ServiceCommand wrapper around
 * [com.sphereon.did.methods.webvh.resolver.WebvhLogReplayer]. Verifies the
 * command surface (registry resolution, selector validation, output mapping)
 * works through the full DI graph.
 */
class WebvhReplayLogCommandTest {
    private lateinit var app: AppGraph
    private lateinit var registry: SessionScopedCommandRegistry
    private lateinit var keyManager: KeyManagerService

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
                "kms.providers.softwaretest.persistKeysDuringGeneration" to "true",
                "kms.providers.softwaretest.exposePrivateKeysDuringGeneration" to "true",
            ),
        )
        app = createWebvhProviderTestAppGraph(testInstance = this)
        app.userContextManager.destroyAll()
        val session =
            app.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId("webvh-replay-cmd", principalType = com.sphereon.di.context.PrincipalType.USER)
        val sessionGraph = session.graph
        registry = (sessionGraph as SessionScopedCommandRegistry.Graph).sessionScopedCommandRegistry
        keyManager = (sessionGraph as KeyManagerServiceGraph).keyManagerService
    }

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) {
            app.userContextManager.destroyAll()
        }
    }

    @Test
    fun replayCommandResolvedFromRegistry() =
        runTest {
            val cmd = registry.get(ReplayWebvhLogServiceCommand.COMMAND_ID)
            assertTrue(cmd is ReplayWebvhLogServiceCommand, "registry must return the replay-log command")
        }

    @Test
    fun replayLatestReturnsGenesisForSingleEntryLog() =
        runTest {
            val genesis = createGenesis()
            val replayCmd = registry.get(ReplayWebvhLogServiceCommand.COMMAND_ID) as ReplayWebvhLogServiceCommand
            val result =
                replayCmd.execute(
                    ReplayWebvhLogInput(
                        did = genesis.first,
                        entries = listOf(genesis.second),
                    ),
                )
            assertTrue(result.isOk, "replay must succeed; error: ${if (result.isErr) result.error.message else "<none>"}")
            assertEquals(genesis.second.versionId, result.value.selectedEntry.versionId)
            assertEquals(genesis.first, result.value.didDocument.id)
        }

    @Test
    fun replayByVersionNumberSelectsCorrectEntry() =
        runTest {
            val genesis = createGenesis()
            val replayCmd = registry.get(ReplayWebvhLogServiceCommand.COMMAND_ID) as ReplayWebvhLogServiceCommand
            val result =
                replayCmd.execute(
                    ReplayWebvhLogInput(
                        did = genesis.first,
                        entries = listOf(genesis.second),
                        versionNumber = 1,
                    ),
                )
            assertTrue(result.isOk)
            assertEquals(genesis.second.versionId, result.value.selectedEntry.versionId)
        }

    @Test
    fun replayRejectsAmbiguousSelector() =
        runTest {
            val genesis = createGenesis()
            val replayCmd = registry.get(ReplayWebvhLogServiceCommand.COMMAND_ID) as ReplayWebvhLogServiceCommand
            val result =
                replayCmd.execute(
                    ReplayWebvhLogInput(
                        did = genesis.first,
                        entries = listOf(genesis.second),
                        versionId = genesis.second.versionId,
                        versionNumber = 1,
                    ),
                )
            assertTrue(result.isErr, "ambiguous selector must be rejected")
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
        }

    @Test
    fun replayByMissingVersionIdReturnsNotFound() =
        runTest {
            val genesis = createGenesis()
            val replayCmd = registry.get(ReplayWebvhLogServiceCommand.COMMAND_ID) as ReplayWebvhLogServiceCommand
            val result =
                replayCmd.execute(
                    ReplayWebvhLogInput(
                        did = genesis.first,
                        entries = listOf(genesis.second),
                        versionId = "999-zNotARealEntry",
                    ),
                )
            assertTrue(result.isErr, "missing versionId selector must fail")
        }

    private suspend fun createGenesis(): Pair<String, com.sphereon.did.methods.webvh.model.WebvhLogEntry> {
        val alias = "replay-cmd-${kotlin.random.Random.nextLong()}"
        val keyPair =
            keyManager.generateKey(
                providerId = "softwaretest",
                alias = alias,
                alg = SignatureAlgorithm.ED25519,
            )
        val rawPublic =
            keyPair.jose.publicJwk.x!!
                .decodeFrom(Encoding.BASE64URL)
        val multikey =
            Multibase.encode(
                byteArrayOf(MULTICODEC_ED25519_BYTE_0, MULTICODEC_ED25519_BYTE_1) + rawPublic,
                MultibaseEncoding.BASE58BTC,
            )
        val createCmd = registry.get(CreateWebvhDidServiceCommand.COMMAND_ID) as CreateWebvhDidServiceCommand
        val out =
            createCmd
                .execute(
                    CreateWebvhDidInput(
                        domain = "example.com",
                        updateKeyRefs = listOf(alias),
                        updateMultikeys = listOf(multikey),
                    ),
                ).also { assertTrue(it.isOk) }
                .value
        return out.did to out.logEntry
    }

    private companion object {
        const val MULTICODEC_ED25519_BYTE_0: Byte = 0xED.toByte()
        const val MULTICODEC_ED25519_BYTE_1: Byte = 0x01.toByte()
    }
}
