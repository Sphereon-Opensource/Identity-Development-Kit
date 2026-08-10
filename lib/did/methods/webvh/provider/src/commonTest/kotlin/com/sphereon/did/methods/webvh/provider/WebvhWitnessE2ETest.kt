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
import com.sphereon.did.methods.webvh.command.CreateWitnessProofInput
import com.sphereon.did.methods.webvh.command.CreateWitnessProofServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileInput
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileServiceCommand
import com.sphereon.did.methods.webvh.model.WebvhWitnessConfig
import com.sphereon.did.methods.webvh.model.WebvhWitnessRef
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
 * End-to-end test for the witness lifecycle (spec §3.4):
 *
 * 1. Controller side: create a webvh DID with a witness config (1-of-1
 *    threshold pointing at a single witness `did:key`).
 * 2. Witness side: sign the genesis versionId via
 *    `did.webvh.create-witness-proof` (the witness's KMS key is held in the
 *    same software KMS for test purposes — production deployments would have
 *    the witness KMS in an entirely separate process).
 * 3. Controller side: merge the incoming witness proof into the witness file
 *    via `did.webvh.update-witness-file`.
 * 4. Replay the log together with the merged witness file: the replayer must
 *    accept (witness threshold met for the latest versionId).
 * 5. Negative case: replay without the witness file when one is configured
 *    must fail.
 */
class WebvhWitnessE2ETest {
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
                .createOrGetFromId("webvh-witness-e2e", principalType = com.sphereon.di.context.PrincipalType.USER)
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
    fun controllerWitnessProofMergeAndReplay() =
        runTest {
            // 1. Generate controller key (signs the entry) and witness key (signs the versionId).
            val (controllerAlias, controllerMultikey) = generateEd25519AndMultikey("witness-e2e-controller")
            val (witnessAlias, witnessMultikey) = generateEd25519AndMultikey("witness-e2e-witness")
            val witnessDid = "did:key:$witnessMultikey"

            // 2. Controller creates webvh with witness config.
            val createCmd = registry.get(CreateWebvhDidServiceCommand.COMMAND_ID) as CreateWebvhDidServiceCommand
            val createResult =
                createCmd.execute(
                    CreateWebvhDidInput(
                        domain = "example.com",
                        updateKeyRefs = listOf(controllerAlias),
                        updateMultikeys = listOf(controllerMultikey),
                        witness = WebvhWitnessConfig(threshold = 1, witnesses = listOf(WebvhWitnessRef(id = witnessDid))),
                    ),
                )
            assertTrue(createResult.isOk, "create with witness must succeed; error: ${if (createResult.isErr) createResult.error.message else "<none>"}")
            val genesis = createResult.value
            assertEquals(
                witnessDid,
                genesis.logEntry.parameters.witness
                    ?.witnesses
                    ?.firstOrNull()
                    ?.id
            )

            // 3. Witness signs the genesis versionId.
            val createWitnessProofCmd = registry.get(CreateWitnessProofServiceCommand.COMMAND_ID) as CreateWitnessProofServiceCommand
            val witnessResult =
                createWitnessProofCmd.execute(
                    CreateWitnessProofInput(
                        did = genesis.did,
                        versionId = genesis.logEntry.versionId,
                        signingKeyRef = witnessAlias,
                        witnessDid = witnessDid,
                    ),
                )
            assertTrue(
                witnessResult.isOk,
                "witness sign must succeed; error: ${if (witnessResult.isErr) witnessResult.error.message else "<none>"}",
            )
            val witnessProof = witnessResult.value.witnessProof
            assertEquals(genesis.logEntry.versionId, witnessProof.versionId)
            assertNotNull(witnessProof.proof.proofValue, "witness proof must carry a proofValue")

            // 4. Controller merges the witness proof into a witness file.
            val updateWitnessFileCmd = registry.get(UpdateWitnessFileServiceCommand.COMMAND_ID) as UpdateWitnessFileServiceCommand
            val mergeResult =
                updateWitnessFileCmd.execute(
                    UpdateWitnessFileInput(
                        did = genesis.did,
                        existingWitnessFile = null,
                        incomingProofs = listOf(witnessProof),
                    ),
                )
            assertTrue(mergeResult.isOk, "update-witness-file must succeed; error: ${if (mergeResult.isErr) mergeResult.error.message else "<none>"}")
            val merged = mergeResult.value.witnessFile
            assertEquals(1, merged.proofs.size, "merged file must have one entry for the genesis versionId")
            assertEquals(genesis.logEntry.versionId, merged.proofs.first().versionId)

            // 5. Replay with the merged witness file: must succeed (1-of-1 threshold met).
            val replayResult =
                replayer.replay(
                    did = genesis.did,
                    entries = listOf(genesis.logEntry),
                    witnessFile = merged,
                )
            assertTrue(
                replayResult.isOk,
                "replay with witness file must succeed; error: ${if (replayResult.isErr) replayResult.error.message else "<none>"}",
            )
        }

    @Test
    fun tamperedWitnessSignatureIsRejectedAtReplay() =
        runTest {
            // Spec §3.4: a witness proof MUST carry a valid eddsa-jcs-2022 signature.
            // Without proper verification, a malicious controller could forge a "witness
            // proof" and the threshold count alone would accept it. This test ensures
            // signature verification actually rejects a tampered witness signature.
            val (controllerAlias, controllerMultikey) = generateEd25519AndMultikey("witness-e2e-tamper-controller")
            val (witnessAlias, witnessMultikey) = generateEd25519AndMultikey("witness-e2e-tamper-witness")
            val witnessDid = "did:key:$witnessMultikey"

            val createCmd = registry.get(CreateWebvhDidServiceCommand.COMMAND_ID) as CreateWebvhDidServiceCommand
            val genesis =
                createCmd
                    .execute(
                        CreateWebvhDidInput(
                            domain = "example.com",
                            updateKeyRefs = listOf(controllerAlias),
                            updateMultikeys = listOf(controllerMultikey),
                            witness = WebvhWitnessConfig(threshold = 1, witnesses = listOf(WebvhWitnessRef(id = witnessDid))),
                        ),
                    ).also { assertTrue(it.isOk) }
                    .value

            val createWitnessProofCmd = registry.get(CreateWitnessProofServiceCommand.COMMAND_ID) as CreateWitnessProofServiceCommand
            val witnessResult =
                createWitnessProofCmd.execute(
                    CreateWitnessProofInput(
                        did = genesis.did,
                        versionId = genesis.logEntry.versionId,
                        signingKeyRef = witnessAlias,
                        witnessDid = witnessDid,
                    ),
                )
            assertTrue(witnessResult.isOk)
            val witnessProof = witnessResult.value.witnessProof

            // Flip one byte of the witness signature; multibase-encode back so it stays
            // a syntactically valid 64-byte base58btc proofValue.
            val original =
                com.sphereon.crypto.core.generic.Multibase
                    .decode(witnessProof.proof.proofValue)
            assertEquals(64, original.size, "Ed25519 witness signature must be 64 bytes")
            val flipped = original.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
            val tamperedProofValue =
                com.sphereon.crypto.core.generic.Multibase.encode(
                    flipped,
                    com.sphereon.crypto.core.generic.MultibaseEncoding.BASE58BTC,
                )
            val tamperedWitnessProof =
                com.sphereon.did.methods.webvh.model.WebvhWitnessProof(
                    versionId = witnessProof.versionId,
                    proof = witnessProof.proof.copy(proofValue = tamperedProofValue),
                )

            val updateWitnessFileCmd = registry.get(UpdateWitnessFileServiceCommand.COMMAND_ID) as UpdateWitnessFileServiceCommand
            val mergeResult =
                updateWitnessFileCmd.execute(
                    UpdateWitnessFileInput(
                        did = genesis.did,
                        existingWitnessFile = null,
                        incomingProofs = listOf(tamperedWitnessProof),
                    ),
                )
            assertTrue(mergeResult.isOk, "merging a tampered witness proof at the file level must succeed; replay catches it")
            val mergedFile = mergeResult.value.witnessFile

            val replayResult =
                replayer.replay(
                    did = genesis.did,
                    entries = listOf(genesis.logEntry),
                    witnessFile = mergedFile,
                )
            assertTrue(
                replayResult.isErr,
                "replay must reject a witness file whose only proof has a forged signature; got Ok",
            )
        }

    @Test
    fun forgedWitnessFromUnauthorizedDidIsRejectedAtReplay() =
        runTest {
            // The witness produced a real signature, but the witness DID is NOT in
            // the configured witness set. Per spec §3.4 only proofs by configured
            // witnesses count toward the threshold.
            val (controllerAlias, controllerMultikey) = generateEd25519AndMultikey("witness-e2e-unauth-controller")
            val (authorizedAlias, authorizedMultikey) = generateEd25519AndMultikey("witness-e2e-unauth-authorized")
            val (impostorAlias, impostorMultikey) = generateEd25519AndMultikey("witness-e2e-unauth-impostor")
            val authorizedDid = "did:key:$authorizedMultikey"
            val impostorDid = "did:key:$impostorMultikey"
            assertNotNull(authorizedAlias) // sanity (key was generated)

            val createCmd = registry.get(CreateWebvhDidServiceCommand.COMMAND_ID) as CreateWebvhDidServiceCommand
            val genesis =
                createCmd
                    .execute(
                        CreateWebvhDidInput(
                            domain = "example.com",
                            updateKeyRefs = listOf(controllerAlias),
                            updateMultikeys = listOf(controllerMultikey),
                            // Only `authorizedDid` is configured.
                            witness = WebvhWitnessConfig(threshold = 1, witnesses = listOf(WebvhWitnessRef(id = authorizedDid))),
                        ),
                    ).also { assertTrue(it.isOk) }
                    .value

            // Impostor produces a real signature using their own key — but their DID is not authorized.
            val createWitnessProofCmd = registry.get(CreateWitnessProofServiceCommand.COMMAND_ID) as CreateWitnessProofServiceCommand
            val impostorProof =
                createWitnessProofCmd
                    .execute(
                        CreateWitnessProofInput(
                            did = genesis.did,
                            versionId = genesis.logEntry.versionId,
                            signingKeyRef = impostorAlias,
                            witnessDid = impostorDid,
                        ),
                    ).also { assertTrue(it.isOk) }
                    .value.witnessProof

            val updateWitnessFileCmd = registry.get(UpdateWitnessFileServiceCommand.COMMAND_ID) as UpdateWitnessFileServiceCommand
            val mergedFile =
                updateWitnessFileCmd
                    .execute(
                        UpdateWitnessFileInput(
                            did = genesis.did,
                            existingWitnessFile = null,
                            incomingProofs = listOf(impostorProof),
                        ),
                    ).also { assertTrue(it.isOk) }
                    .value.witnessFile

            val replayResult =
                replayer.replay(
                    did = genesis.did,
                    entries = listOf(genesis.logEntry),
                    witnessFile = mergedFile,
                )
            assertTrue(
                replayResult.isErr,
                "replay must reject a witness file whose proofs come from non-configured DIDs; got Ok",
            )
        }

    @Test
    fun replayWithoutWitnessFileFailsWhenWitnessConfigured() =
        runTest {
            val (controllerAlias, controllerMultikey) = generateEd25519AndMultikey("witness-e2e-no-file-controller")
            val (_, witnessMultikey) = generateEd25519AndMultikey("witness-e2e-no-file-witness")
            val witnessDid = "did:key:$witnessMultikey"

            val createCmd = registry.get(CreateWebvhDidServiceCommand.COMMAND_ID) as CreateWebvhDidServiceCommand
            val genesis =
                createCmd
                    .execute(
                        CreateWebvhDidInput(
                            domain = "example.com",
                            updateKeyRefs = listOf(controllerAlias),
                            updateMultikeys = listOf(controllerMultikey),
                            witness = WebvhWitnessConfig(threshold = 1, witnesses = listOf(WebvhWitnessRef(id = witnessDid))),
                        ),
                    ).also { assertTrue(it.isOk) }
                    .value

            // Replay WITHOUT witness file: must fail because witness threshold can't be evaluated.
            val replayResult =
                replayer.replay(
                    did = genesis.did,
                    entries = listOf(genesis.logEntry),
                    witnessFile = null,
                )
            assertTrue(
                replayResult.isErr,
                "replay must fail when witness config requires a file but none is provided; got Ok",
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

    private companion object {
        const val MULTICODEC_ED25519_BYTE_0: Byte = 0xED.toByte()
        const val MULTICODEC_ED25519_BYTE_1: Byte = 0x01.toByte()
    }
}
