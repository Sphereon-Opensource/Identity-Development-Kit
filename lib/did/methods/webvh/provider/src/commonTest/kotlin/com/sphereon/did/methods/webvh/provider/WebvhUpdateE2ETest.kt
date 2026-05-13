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
 * End-to-end test for the `did.webvh.update` lifecycle command exercising
 * key rotation against the pre-rotation chain (spec §3.3.1):
 *
 * 1. Create a genesis entry signed by key A, announcing pre-rotation of key
 *    B via `nextKeyHashes = [hash(multikey(B))]`.
 * 2. Update: rotate updateKeys from [A] to [B], with key B signing the new
 *    entry. The replayer must verify the rotation against the genesis
 *    entry's `nextKeyHashes` and accept the new entry.
 * 3. Replay the full 2-entry log and assert version 2 is the head.
 *
 * Closes the lifecycle gap noted in the original webvh plan: rotation flow
 * was previously untested.
 */
class WebvhUpdateE2ETest {
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
                .createOrGetFromId("webvh-update-e2e")
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
    fun createThenRotateUpdateKeyValidatesOnReplay() =
        runTest {
            // 1. Generate two Ed25519 keys: A (active at genesis) and B (next key for pre-rotation).
            val (aliasA, multikeyA) = generateEd25519AndMultikey("update-e2e-A")
            val (aliasB, multikeyB) = generateEd25519AndMultikey("update-e2e-B")

            // 2. Create with A as active updateKeys and B announced via nextKeyMultikeys.
            val createCmd = registry.get(CreateWebvhDidServiceCommand.COMMAND_ID) as CreateWebvhDidServiceCommand
            val createResult =
                createCmd.execute(
                    CreateWebvhDidInput(
                        domain = "example.com",
                        updateKeyRefs = listOf(aliasA),
                        updateMultikeys = listOf(multikeyA),
                        nextKeyMultikeys = listOf(multikeyB),
                    ),
                )
            assertTrue(createResult.isOk, "create must succeed; error: ${if (createResult.isErr) createResult.error.message else "<none>"}")
            val genesis = createResult.value
            val genesisEntry = genesis.logEntry
            assertEquals(listOf(multikeyA), genesisEntry.parameters.updateKeys)
            val nextKeyHashes = genesisEntry.parameters.nextKeyHashes
            assertNotNull(nextKeyHashes, "genesis must announce nextKeyHashes when nextKeyMultikeys was supplied")
            assertEquals(1, nextKeyHashes.size)

            // 3. Build the new DID document with B as the only verification method.
            val updatedDocument = buildDidDocumentWithMultikey(genesis.did, multikeyB)

            // 4. Update: rotate updateKeys to [B], signed by B.
            val updateCmd = registry.get(UpdateWebvhDidServiceCommand.COMMAND_ID) as UpdateWebvhDidServiceCommand
            val updateResult =
                updateCmd.execute(
                    UpdateWebvhDidInput(
                        did = genesis.did,
                        existingLog = listOf(genesisEntry),
                        newDocument = updatedDocument,
                        parameterChanges =
                            WebvhParameters(
                                updateKeys = listOf(multikeyB),
                                // No further pre-rotation in this test; replayer still accepts.
                                nextKeyHashes = null,
                            ),
                        signingKeyRefs = listOf(aliasB),
                        // Force versionTime > genesis.
                        versionTime = futureIso(genesisEntry.versionTime),
                    ),
                )
            assertTrue(
                updateResult.isOk,
                "did.webvh.update must succeed; error: ${if (updateResult.isErr) updateResult.error.message else "<none>"}",
            )
            val newEntry = updateResult.value.newEntry
            assertTrue(newEntry.versionId.startsWith("2-"), "new entry versionId must start with '2-', was: ${newEntry.versionId}")
            assertEquals(listOf(multikeyB), newEntry.parameters.updateKeys, "new entry must declare updateKeys = [B]")

            // 5. Replay the full log: genesis (signed by A) + update (signed by B, validates against pre-rotation chain).
            val parsed = WebvhLogReader.read(updateResult.value.newLogJsonl)
            assertTrue(parsed.isOk)
            assertEquals(2, parsed.value.size)

            val replayResult = replayer.replay(did = genesis.did, entries = parsed.value)
            assertTrue(
                replayResult.isOk,
                "replay must accept the rotated chain; error: ${if (replayResult.isErr) replayResult.error.message else "<none>"}",
            )
            val replay = replayResult.value
            assertEquals(newEntry.versionId, replay.selectedEntry.versionId, "replay LATEST must select the version-2 entry")
            assertEquals(listOf(multikeyB), replay.activeParameters.updateKeys, "active updateKeys after replay must be [B]")

            // 6. did:web companion is populated by default on Update too,
            //    rewriting the rotated state document to did:web and binding
            //    back to the webvh DID via alsoKnownAs.
            assertNotNull(updateResult.value.didWebDocument, "update did:web companion must be populated by default")
            assertNotNull(updateResult.value.didWebJson, "update did:web companion JSON must be populated by default")
            assertEquals(
                "did:web:example.com",
                updateResult.value.didWebDocument!!.id,
                "update companion id must strip the SCID",
            )
            val updateAka = updateResult.value.didWebDocument!!.alsoKnownAs
            assertNotNull(updateAka, "update companion must declare alsoKnownAs")
            assertTrue(genesis.did in updateAka, "update companion must alsoKnownAs the webvh DID")
        }

    @Test
    fun rotationToUnauthorizedKeyIsRejected() =
        runTest {
            // Sanity: rotating to a key that was NOT pre-announced via nextKeyHashes must fail
            // at replay time.
            val (aliasA, multikeyA) = generateEd25519AndMultikey("update-e2e-bad-A")
            val (aliasB, multikeyB) = generateEd25519AndMultikey("update-e2e-bad-B")
            val (aliasC, multikeyC) = generateEd25519AndMultikey("update-e2e-bad-C")

            val createCmd = registry.get(CreateWebvhDidServiceCommand.COMMAND_ID) as CreateWebvhDidServiceCommand
            val genesis =
                createCmd
                    .execute(
                        CreateWebvhDidInput(
                            domain = "example.com",
                            updateKeyRefs = listOf(aliasA),
                            updateMultikeys = listOf(multikeyA),
                            // Pre-rotate to B, not C.
                            nextKeyMultikeys = listOf(multikeyB),
                        ),
                    ).also { assertTrue(it.isOk) }
                    .value
            // Sanity-use aliasA so the static-analysis tooling doesn't flag it.
            assertNotNull(aliasA)
            assertNotNull(aliasB)

            val updateCmd = registry.get(UpdateWebvhDidServiceCommand.COMMAND_ID) as UpdateWebvhDidServiceCommand
            val updateResult =
                updateCmd.execute(
                    UpdateWebvhDidInput(
                        did = genesis.did,
                        existingLog = listOf(genesis.logEntry),
                        newDocument = buildDidDocumentWithMultikey(genesis.did, multikeyC),
                        parameterChanges =
                            WebvhParameters(
                                // Rotate to C — was NEVER announced.
                                updateKeys = listOf(multikeyC),
                            ),
                        signingKeyRefs = listOf(aliasC),
                        versionTime = futureIso(genesis.logEntry.versionTime),
                    ),
                )
            // Update may succeed at the command level (the command itself doesn't validate
            // the pre-rotation chain — replay does). The contract we care about is that
            // replay rejects this log.
            assertTrue(updateResult.isOk, "update command itself succeeds; replay catches the violation")

            val parsed = WebvhLogReader.read(updateResult.value.newLogJsonl)
            assertTrue(parsed.isOk)
            val replayResult = replayer.replay(did = genesis.did, entries = parsed.value)
            assertTrue(
                replayResult.isErr,
                "replay must reject rotation to a key not in the prior nextKeyHashes; instead got Ok",
            )
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

    /**
     * Returns an ISO-8601 UTC timestamp strictly after [prior] but ≤ now,
     * so the replayer (which checks `versionTime <= now`) accepts it.
     */
    private fun futureIso(prior: String): String {
        val parsed = kotlin.time.Instant.parse(prior)
        val bumped = parsed + kotlin.time.Duration.parse("1ms")
        // If bumped happens to land in the future (shouldn't, but defensive), clamp to now.
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
