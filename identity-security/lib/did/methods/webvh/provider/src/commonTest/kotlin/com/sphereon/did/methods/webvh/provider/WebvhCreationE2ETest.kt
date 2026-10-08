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
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.Multibase
import com.sphereon.crypto.core.generic.MultibaseEncoding
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerServiceGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.webvh.command.CreateWebvhDidInput
import com.sphereon.did.methods.webvh.command.CreateWebvhDidOutput
import com.sphereon.did.methods.webvh.command.CreateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.log.WebvhLogReader
import com.sphereon.did.methods.webvh.provider.testutil.createWebvhProviderTestAppGraph
import com.sphereon.did.methods.webvh.resolver.ReplaySelector
import com.sphereon.did.methods.webvh.resolver.WebvhLogReplayer
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real-cryptography end-to-end test for the `did:webvh` create + replay
 * loop.
 *
 * This test fully closes the loop the original webvh plan deferred:
 * 1. Compose the real Metro DI graph for `:provider` + `:resolver` +
 *    `:eddsa-jcs-2022` + the software KMS provider.
 * 2. Generate an Ed25519 key pair via the software KMS (now possible since
 *    the OSS KMS supports `SignatureAlgorithm.ED25519`).
 * 3. Derive the matching `z6Mk…` multikey from the public key bytes per the
 *    Multikey spec (multicodec `0xED 0x01` || raw public bytes, then
 *    base58btc multibase).
 * 4. Invoke `did.webvh.create` with the real key reference and multikey;
 *    the command computes the SCID, builds the genesis log entry, and
 *    signs it with `eddsa-jcs-2022` via [SoftwareKmsProvider].
 * 5. Replay the resulting JSONL through [WebvhLogReplayer], which verifies
 *    the proof against the active updateKeys.
 * 6. Assert the replayed DID document matches what the create command
 *    returned, and the replayed entry's versionId matches the genesis
 *    entry's versionId.
 */
class WebvhCreationE2ETest {
    private lateinit var app: AppGraph
    private lateinit var registry: SessionScopedCommandRegistry
    private lateinit var keyManager: com.sphereon.crypto.core.kms.KeyManagerService
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
                .createOrGetFromId("webvh-creation-e2e", principalType = com.sphereon.di.context.PrincipalType.USER)
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
    fun createWebvhDidWithEd25519KmsKeyAndReplayVerifies() =
        runTest {
            // 1. Generate a real Ed25519 key in the software KMS.
            val alias = "webvh-e2e-ed25519-${kotlin.random.Random.nextLong()}"
            val keyPair =
                keyManager.generateKey(
                    providerId = "softwaretest",
                    alias = alias,
                    alg = SignatureAlgorithm.ED25519,
                )
            val publicJwk = keyPair.jose.publicJwk
            assertNotNull(publicJwk.x, "Ed25519 public JWK must carry x")

            // 2. Derive the multikey form (multicodec 0xED 0x01 || raw public, base58btc).
            val rawPublic = publicJwk.x!!.decodeFrom(Encoding.BASE64URL)
            assertEquals(ED25519_KEY_BYTES, rawPublic.size, "Ed25519 raw public key must be 32 bytes")
            val multicodecPrefixed = byteArrayOf(MULTICODEC_ED25519_BYTE_0, MULTICODEC_ED25519_BYTE_1) + rawPublic
            val multikey = Multibase.encode(multicodecPrefixed, MultibaseEncoding.BASE58BTC)
            assertTrue(multikey.startsWith("z6Mk"), "Ed25519 multikey must start with z6Mk, was: $multikey")

            // 3. Invoke did.webvh.create through the real DI graph.
            val command = registry.get(CreateWebvhDidServiceCommand.COMMAND_ID) as CreateWebvhDidServiceCommand
            val input =
                CreateWebvhDidInput(
                    domain = "example.com",
                    updateKeyRefs = listOf(alias),
                    updateMultikeys = listOf(multikey),
                )
            val createResult = command.execute(input)
            assertTrue(
                createResult.isOk,
                "did.webvh.create must succeed; error: ${if (createResult.isErr) createResult.error.message else "<none>"}",
            )
            val output: CreateWebvhDidOutput = createResult.value

            // 4. Sanity-check the output shape.
            assertTrue(output.did.startsWith("did:webvh:"), "DID must start with did:webvh:")
            assertTrue(output.did.endsWith("example.com"), "DID must end with the supplied host")
            assertEquals(1, output.logEntry.proof.size, "Genesis entry must carry exactly one proof")
            assertEquals(
                "eddsa-jcs-2022",
                output.logEntry.proof
                    .first()
                    .cryptosuite
            )
            assertEquals(output.did, output.didDocument.id, "DID document id must match the minted DID")

            // 5. Round-trip the JSONL through the reader.
            val parsed = WebvhLogReader.read(output.logJsonl)
            assertTrue(parsed.isOk, "log JSONL must parse cleanly")
            assertEquals(1, parsed.value.size, "Genesis log must contain exactly one entry")
            assertEquals(output.logEntry.versionId, parsed.value.first().versionId)

            // 6. Replay the log through the verifier — this exercises the FULL
            //    eddsa-jcs-2022 signature verification chain. If the genesis
            //    proof is invalid, replay() returns an Err.
            val replayResult = replayer.replay(did = output.did, entries = parsed.value)
            assertTrue(
                replayResult.isOk,
                "replay must succeed (proof verifies, SCID matches, versionId chain valid); " +
                    "error: ${if (replayResult.isErr) replayResult.error.message else "<none>"}",
            )
            val replay = replayResult.value

            // 7. Assert the replayed state matches what create returned.
            assertEquals(output.did, replay.didDocument.id, "Replayed DID must match")
            assertEquals(output.logEntry.versionId, replay.selectedEntry.versionId, "Replayed versionId must match")
            assertEquals(
                listOf(multikey),
                replay.activeParameters.updateKeys,
                "Replayed updateKeys must match the supplied multikey",
            )

            // 8. did:web companion document is populated by default and
            //    correctly rewritten to point at the did:web identifier
            //    while binding back to the webvh DID via alsoKnownAs.
            assertNotNull(output.didWebDocument, "did:web companion must be populated by default")
            assertNotNull(output.didWebJson, "did:web companion JSON must be populated by default")
            assertEquals("did:web:example.com", output.didWebDocument!!.id, "companion id must strip the SCID")
            val aka = output.didWebDocument!!.alsoKnownAs
            assertNotNull(aka)
            assertTrue(output.did in aka, "companion must alsoKnownAs the webvh DID")
            assertTrue(
                output.didWebJson!!.contains("\"id\":\"did:web:example.com\""),
                "companion JSON must serialise the rewritten id",
            )
        }

    private companion object {
        const val ED25519_KEY_BYTES: Int = 32
        const val MULTICODEC_ED25519_BYTE_0: Byte = 0xED.toByte()
        const val MULTICODEC_ED25519_BYTE_1: Byte = 0x01.toByte()
    }
}

/**
 * Test-only @ContributesTo Graph interface so the JVM E2E test can pull the
 * session-scoped [WebvhLogReplayer] off the merged session graph. Lives in
 * jvmTest (not commonMain) to avoid forcing every consumer of `:resolver`
 * to re-link their session graph against an additional contribution and to
 * keep the contribution off non-JVM target classpaths.
 */
@ContributesTo(SessionScope::class)
interface WebvhLogReplayerGraph {
    val webvhLogReplayer: WebvhLogReplayer
}
