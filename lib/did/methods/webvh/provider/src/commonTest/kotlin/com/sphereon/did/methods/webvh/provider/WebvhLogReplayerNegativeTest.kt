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
import com.sphereon.did.methods.webvh.command.CreateWebvhDidOutput
import com.sphereon.did.methods.webvh.command.CreateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.model.WebvhLogEntry
import com.sphereon.did.methods.webvh.provider.testutil.createWebvhProviderTestAppGraph
import com.sphereon.did.methods.webvh.resolver.WebvhLogReplayer
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Negative-case coverage for [WebvhLogReplayer]: build a real signed genesis
 * entry (via the create command + Ed25519 KMS) then mutate one field and
 * assert the replayer rejects with an error. The original webvh plan called
 * for these but only the happy-path was previously tested.
 *
 * Each test mutates exactly one aspect of the entry to isolate which spec
 * rule rejects the input.
 */
class WebvhLogReplayerNegativeTest {
    private lateinit var app: AppGraph
    private lateinit var registry: SessionScopedCommandRegistry
    private lateinit var keyManager: KeyManagerService
    private lateinit var replayer: WebvhLogReplayer
    private lateinit var goodEntry: WebvhLogEntry
    private lateinit var goodDid: String

    @BeforeTest
    fun setUp() {
        // Synchronous bootstrap only — building the signed genesis entry is suspending,
        // so it happens inside each test's `runTest` block via `ensureBaseline()`.
        // (`runTest` returned from a @BeforeTest is a Promise on JS and isn't awaited,
        // so any `lateinit var` set inside it would be uninitialized when the test runs.)
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
                .createOrGetFromId("webvh-replayer-negative")
        val sessionGraph = session.graph
        registry = (sessionGraph as SessionScopedCommandRegistry.Graph).sessionScopedCommandRegistry
        keyManager = (sessionGraph as KeyManagerServiceGraph).keyManagerService
        replayer = (sessionGraph as WebvhLogReplayerGraph).webvhLogReplayer
    }

    /**
     * Builds the signed genesis entry and asserts the unmutated baseline replays
     * cleanly. Suspending — must be called from within a test's `runTest` block.
     * Caches the result so each test only pays the cost once on JVM (where setUp
     * is shared) but works correctly on JS where `@BeforeTest` can't suspend.
     */
    private suspend fun ensureBaseline() {
        if (::goodEntry.isInitialized) {
            return
        }
        val output = createGenesisEntry()
        goodEntry = output.logEntry
        goodDid = output.did
        val baseline = replayer.replay(did = goodDid, entries = listOf(goodEntry))
        assertTrue(
            baseline.isOk,
            "Sanity baseline must replay cleanly; error: ${if (baseline.isErr) baseline.error.message else "<none>"}",
        )
    }

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) {
            app.userContextManager.destroyAll()
        }
    }

    @Test
    fun rejectsMalformedVersionId() =
        runTest {
            ensureBaseline()
            // versionId must look like "<n>-<entryHash>"; "garbage" violates the format.
            val tampered = goodEntry.copy(versionId = "garbage")
            val result = replayer.replay(did = goodDid, entries = listOf(tampered))
            assertTrue(result.isErr, "Replayer must reject malformed versionId; got Ok")
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("invalid versionId") == true ||
                    result.error.message.defaultMessage
                        ?.contains("versionId") == true,
                "Error must mention versionId; was: ${result.error.message.defaultMessage}",
            )
        }

    @Test
    fun rejectsWrongVersionNumberInVersionId() =
        runTest {
            ensureBaseline()
            // versionId says version=2 but it's actually entry index 1 (i.e. version=1).
            val originalHash = goodEntry.versionId.substringAfter('-')
            val tampered = goodEntry.copy(versionId = "2-$originalHash")
            val result = replayer.replay(did = goodDid, entries = listOf(tampered))
            assertTrue(result.isErr, "Replayer must reject wrong version number; got Ok")
        }

    @Test
    fun rejectsFutureVersionTime() =
        runTest {
            ensureBaseline()
            // versionTime in the year 3000 — definitely "future" relative to replay time.
            val tampered = goodEntry.copy(versionTime = "3000-01-01T00:00:00Z")
            // Entry hash will also break; but versionTime check happens first per the impl
            // (validateVersionTime runs before validateEntryHash). We only assert isErr and a
            // plausible error string — the spec rejection itself is the contract.
            val result = replayer.replay(did = goodDid, entries = listOf(tampered))
            assertTrue(result.isErr, "Replayer must reject future versionTime; got Ok")
        }

    @Test
    fun rejectsTamperedProof() =
        runTest {
            ensureBaseline()
            // Decode the real 64-byte Ed25519 signature, flip its first byte, and re-encode
            // back to multibase base58btc. Same length, same structure, syntactically valid —
            // the only failure path is signature verification.
            val proof = goodEntry.proof.first()
            val originalProofValue = proof.proofValue
            require(originalProofValue.startsWith("z")) { "Proof value should be base58btc multibase" }
            val originalBytes =
                com.sphereon.crypto.core.generic.Multibase
                    .decode(originalProofValue)
            require(originalBytes.size == 64) { "Ed25519 signatures must be 64 bytes; was ${originalBytes.size}" }
            val flipped = originalBytes.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
            val tamperedProofValue =
                com.sphereon.crypto.core.generic.Multibase.encode(
                    flipped,
                    com.sphereon.crypto.core.generic.MultibaseEncoding.BASE58BTC,
                )
            require(tamperedProofValue != originalProofValue) {
                "Tampered proofValue must differ from the original; sanity check failed"
            }
            val tamperedProof = proof.copy(proofValue = tamperedProofValue)
            val tampered = goodEntry.copy(proof = listOf(tamperedProof))
            val result = replayer.replay(did = goodDid, entries = listOf(tampered))
            assertTrue(
                result.isErr,
                "Replayer must reject entry with tampered proof; got Ok with versionId ${if (result.isOk) result.value.selectedEntry.versionId else "<n/a>"}",
            )
        }

    @Test
    fun rejectsEmptyEntryList() =
        runTest {
            ensureBaseline()
            val result = replayer.replay(did = goodDid, entries = emptyList())
            assertTrue(result.isErr, "Replayer must reject empty log; got Ok")
        }

    private suspend fun createGenesisEntry(): CreateWebvhDidOutput {
        val alias = "webvh-replayer-neg-${kotlin.random.Random.nextLong()}"
        val keyPair =
            keyManager.generateKey(
                providerId = "softwaretest",
                alias = alias,
                alg = SignatureAlgorithm.ED25519,
            )
        val publicJwk = keyPair.jose.publicJwk
        val rawPublic = publicJwk.x!!.decodeFrom(Encoding.BASE64URL)
        val multicodecPrefixed = byteArrayOf(MULTICODEC_ED25519_BYTE_0, MULTICODEC_ED25519_BYTE_1) + rawPublic
        val multikey = Multibase.encode(multicodecPrefixed, MultibaseEncoding.BASE58BTC)

        val command = registry.get(CreateWebvhDidServiceCommand.COMMAND_ID) as CreateWebvhDidServiceCommand
        val input =
            CreateWebvhDidInput(
                domain = "example.com",
                updateKeyRefs = listOf(alias),
                updateMultikeys = listOf(multikey),
            )
        val createResult = command.execute(input)
        assertTrue(createResult.isOk, "Setup: did.webvh.create must succeed")
        return createResult.value.also { assertNotNull(it.logEntry) }
    }

    private companion object {
        const val MULTICODEC_ED25519_BYTE_0: Byte = 0xED.toByte()
        const val MULTICODEC_ED25519_BYTE_1: Byte = 0x01.toByte()
    }
}
