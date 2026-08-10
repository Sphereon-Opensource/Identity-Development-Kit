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
import com.sphereon.did.methods.webvh.command.UpdateWebvhDidInput
import com.sphereon.did.methods.webvh.command.UpdateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.log.WebvhLogReader
import com.sphereon.did.methods.webvh.model.WebvhParameters
import com.sphereon.did.methods.webvh.provider.testutil.createWebvhProviderTestAppGraph
import com.sphereon.did.methods.webvh.resolver.WebvhLogReplayer
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodOrReference
import com.sphereon.did.models.VerificationMethodType
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Multi-step pre-rotation chain test: rotates A → B → C across three log
 * entries, asserting the replayer accepts the chain at every step.
 *
 * - Genesis (entry 1): updateKeys=[A], nextKeyHashes=[hash(B)].
 * - Entry 2: updateKeys=[B], nextKeyHashes=[hash(C)] — verifies against
 *   genesis's nextKeyHashes (rotation event #1).
 * - Entry 3: updateKeys=[C], no further pre-rotation — verifies against
 *   entry 2's nextKeyHashes (rotation event #2).
 *
 * The earlier `WebvhUpdateE2ETest` only proves a single rotation event;
 * this one proves the chain holds across multiple events with the active
 * `nextKeyHashes` window sliding correctly.
 */
class WebvhMultiRotationChainE2ETest {
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
                .createOrGetFromId("webvh-multi-rotation-chain", principalType = com.sphereon.di.context.PrincipalType.USER)
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
    fun threeEntryChainRotatesAtoBtoCAcceptedByReplayer() =
        runTest {
            val (aliasA, mkA) = generateEd25519AndMultikey("multi-rot-A")
            val (aliasB, mkB) = generateEd25519AndMultikey("multi-rot-B")
            val (aliasC, mkC) = generateEd25519AndMultikey("multi-rot-C")

            // Entry 1: genesis with A active, B announced.
            val createCmd = registry.get(CreateWebvhDidServiceCommand.COMMAND_ID) as CreateWebvhDidServiceCommand
            val genesis =
                createCmd
                    .execute(
                        CreateWebvhDidInput(
                            domain = "example.com",
                            updateKeyRefs = listOf(aliasA),
                            updateMultikeys = listOf(mkA),
                            nextKeyMultikeys = listOf(mkB),
                        ),
                    ).also { assertTrue(it.isOk, "create must succeed") }
                    .value
            val nextKeyHashesAfterGenesis = genesis.logEntry.parameters.nextKeyHashes
            assertNotNull(nextKeyHashesAfterGenesis, "genesis must announce nextKeyHashes for B")

            // Entry 2: rotate to B as active, announce C as next.
            val updateCmd = registry.get(UpdateWebvhDidServiceCommand.COMMAND_ID) as UpdateWebvhDidServiceCommand
            val entry2Result =
                updateCmd.execute(
                    UpdateWebvhDidInput(
                        did = genesis.did,
                        existingLog = listOf(genesis.logEntry),
                        newDocument = buildDidDocumentWithMultikey(genesis.did, mkB),
                        parameterChanges =
                            WebvhParameters(
                                updateKeys = listOf(mkB),
                                nextKeyHashes =
                                    listOf(
                                        com.sphereon.did.methods.webvh.scid
                                            .WebvhPreRotationHasher
                                            .hashMultikey(mkC),
                                    ),
                            ),
                        signingKeyRefs = listOf(aliasB),
                        versionTime = futureIso(genesis.logEntry.versionTime),
                    ),
                )
            assertTrue(
                entry2Result.isOk,
                "entry 2 (A→B) update must succeed; error: ${if (entry2Result.isErr) entry2Result.error.message else "<none>"}",
            )
            val entry2 = entry2Result.value.newEntry
            assertEquals(listOf(mkB), entry2.parameters.updateKeys, "entry 2 must declare updateKeys = [B]")
            assertNotNull(entry2.parameters.nextKeyHashes, "entry 2 must announce nextKeyHashes for C")

            // Entry 3: rotate to C as active.
            val log2 = listOf(genesis.logEntry, entry2)
            val entry3Result =
                updateCmd.execute(
                    UpdateWebvhDidInput(
                        did = genesis.did,
                        existingLog = log2,
                        newDocument = buildDidDocumentWithMultikey(genesis.did, mkC),
                        parameterChanges =
                            WebvhParameters(
                                updateKeys = listOf(mkC),
                                nextKeyHashes = null,
                            ),
                        signingKeyRefs = listOf(aliasC),
                        versionTime = futureIso(entry2.versionTime),
                    ),
                )
            assertTrue(
                entry3Result.isOk,
                "entry 3 (B→C) update must succeed; error: ${if (entry3Result.isErr) entry3Result.error.message else "<none>"}",
            )
            val entry3 = entry3Result.value.newEntry
            assertEquals(listOf(mkC), entry3.parameters.updateKeys, "entry 3 must declare updateKeys = [C]")

            // Replay full 3-entry chain.
            val parsed = WebvhLogReader.read(entry3Result.value.newLogJsonl)
            assertTrue(parsed.isOk)
            assertEquals(3, parsed.value.size, "log must contain 3 entries")
            val replayResult = replayer.replay(did = genesis.did, entries = parsed.value)
            assertTrue(
                replayResult.isOk,
                "replay must accept the full A→B→C chain; error: ${if (replayResult.isErr) replayResult.error.message else "<none>"}",
            )
            val replay = replayResult.value
            assertEquals(entry3.versionId, replay.selectedEntry.versionId, "LATEST must select entry 3")
            assertEquals(listOf(mkC), replay.activeParameters.updateKeys, "active updateKeys after replay must be [C]")
            assertTrue(replay.selectedEntry.versionId.startsWith("3-"), "entry 3 versionId must start with '3-'")
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

    private fun buildDidDocumentWithMultikey(
        did: String,
        multikey: String,
    ): DidDocument {
        val vmId = "$did#key-1"
        val vm =
            VerificationMethod(
                id = vmId,
                type = VerificationMethodType.MULTIKEY.value,
                controller = did,
                publicKeyMultibase = multikey,
            )
        val ref = VerificationMethodOrReference.fromReference(vmId)
        return DidDocument(
            id = did,
            verificationMethod = listOf(vm),
            authentication = listOf(ref),
            assertionMethod = listOf(ref),
        )
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
