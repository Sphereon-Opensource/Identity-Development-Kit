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
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidInput
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.log.WebvhLogReader
import com.sphereon.did.methods.webvh.provider.testutil.createWebvhProviderTestAppGraph
import com.sphereon.did.methods.webvh.resolver.WebvhLogReplayer
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E2E for the deactivation lifecycle command (spec §3.6):
 *
 * 1. Create genesis with key A (no pre-rotation).
 * 2. Deactivate with key A — appends a new entry with
 *    `parameters.deactivated = true` and `updateKeys = []`.
 * 3. Replay the 2-entry log — must succeed; final entry's effective
 *    parameters carry `deactivated = true` and empty `updateKeys`.
 *
 * Closes the third leg of the create+update+deactivate lifecycle trio.
 */
class WebvhDeactivateE2ETest {
    private lateinit var app: AppGraph
    private lateinit var registry: SessionScopedCommandRegistry
    private lateinit var keyManager: KeyManagerService
    private lateinit var replayer: WebvhLogReplayer

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
                .createOrGetFromId("webvh-deactivate-e2e")
        val sessionGraph = session.graph
        registry = (sessionGraph as SessionScopedCommandRegistry.Graph).sessionScopedCommandRegistry
        keyManager = (sessionGraph as KeyManagerServiceGraph).keyManagerService
        replayer = (sessionGraph as WebvhLogReplayerGraph).webvhLogReplayer
    }

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) {
            app.userContextManager.destroyAll()
        }
    }

    @Test
    fun createThenDeactivateValidatesOnReplay() =
        runTest {
            val (alias, multikey) = generateEd25519AndMultikey("deactivate-e2e")

            // Create genesis with key A.
            val createCmd = registry.get(CreateWebvhDidServiceCommand.COMMAND_ID) as CreateWebvhDidServiceCommand
            val genesis =
                createCmd
                    .execute(
                        CreateWebvhDidInput(
                            domain = "example.com",
                            updateKeyRefs = listOf(alias),
                            updateMultikeys = listOf(multikey),
                        ),
                    ).also { assertTrue(it.isOk, "create must succeed") }
                    .value

            // Deactivate with key A (no rotation; same key signs the deactivation entry).
            val deactivateCmd = registry.get(DeactivateWebvhDidServiceCommand.COMMAND_ID) as DeactivateWebvhDidServiceCommand
            val deactivateResult =
                deactivateCmd.execute(
                    DeactivateWebvhDidInput(
                        did = genesis.did,
                        existingLog = listOf(genesis.logEntry),
                        signingKeyRefs = listOf(alias),
                        versionTime = futureIso(genesis.logEntry.versionTime),
                    ),
                )
            assertTrue(
                deactivateResult.isOk,
                "deactivate must succeed; error: ${if (deactivateResult.isErr) deactivateResult.error.message else "<none>"}",
            )
            val deactivationEntry = deactivateResult.value.deactivationEntry
            assertTrue(deactivationEntry.versionId.startsWith("2-"), "deactivation entry versionId must start with '2-'")
            assertEquals(true, deactivationEntry.parameters.deactivated, "deactivation entry must set deactivated=true")
            assertEquals(emptyList(), deactivationEntry.parameters.updateKeys, "deactivation entry must clear updateKeys")

            // Replay full log.
            val parsed = WebvhLogReader.read(deactivateResult.value.newLogJsonl)
            assertTrue(parsed.isOk)
            assertEquals(2, parsed.value.size, "deactivated log must contain genesis + deactivation entry")

            val replayResult = replayer.replay(did = genesis.did, entries = parsed.value)
            assertTrue(
                replayResult.isOk,
                "replay must accept create+deactivate chain; error: ${if (replayResult.isErr) replayResult.error.message else "<none>"}",
            )
            val replay = replayResult.value
            assertEquals(deactivationEntry.versionId, replay.selectedEntry.versionId, "replay LATEST must select the deactivation entry")
            assertEquals(true, replay.activeParameters.deactivated, "active parameters after replay must show deactivated=true")
            assertEquals(emptyList(), replay.activeParameters.updateKeys, "active updateKeys after replay must be empty")

            // did:web companion is populated for the deactivated state too;
            // resolvers that only speak did:web see the final (deactivated)
            // document with alsoKnownAs back to the webvh DID.
            assertNotNull(deactivateResult.value.didWebDocument, "deactivate did:web companion must be populated by default")
            assertNotNull(deactivateResult.value.didWebJson, "deactivate did:web companion JSON must be populated by default")
            assertEquals(
                "did:web:example.com",
                deactivateResult.value.didWebDocument!!.id,
                "deactivate companion id must strip the SCID",
            )
            val deactivateAka = deactivateResult.value.didWebDocument!!.alsoKnownAs
            assertNotNull(deactivateAka, "deactivate companion must declare alsoKnownAs")
            assertTrue(genesis.did in deactivateAka, "deactivate companion must alsoKnownAs the webvh DID")
        }

    private suspend fun generateEd25519AndMultikey(prefix: String): Pair<String, String> {
        val alias = "$prefix-${kotlin.random.Random.nextLong()}"
        val keyPair =
            keyManager.generateKey(
                providerId = "softwaretest",
                alias = alias,
                alg = SignatureAlgorithm.ED25519,
            )
        val rawPublic =
            keyPair.jose.publicJwk.x!!
                .decodeFrom(Encoding.BASE64URL)
        val multicodecPrefixed = byteArrayOf(MULTICODEC_ED25519_BYTE_0, MULTICODEC_ED25519_BYTE_1) + rawPublic
        val multikey = Multibase.encode(multicodecPrefixed, MultibaseEncoding.BASE58BTC)
        return alias to multikey
    }

    private fun futureIso(prior: String): String {
        val parsed = kotlin.time.Instant.parse(prior)
        val bumped = parsed + kotlin.time.Duration.parse("1ms")
        val now =
            kotlin.time.Clock.System
                .now()
        return (if (bumped > now) now else bumped).toString()
    }

    private companion object {
        const val MULTICODEC_ED25519_BYTE_0: Byte = 0xED.toByte()
        const val MULTICODEC_ED25519_BYTE_1: Byte = 0x01.toByte()
    }
}
