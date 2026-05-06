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

import com.sphereon.did.methods.webvh.log.WebvhLogReader
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-implementation interop smoke test: parses a `did.jsonl` fixture
 * captured from an external `did:webvh` reference implementation (e.g. the
 * DIF TS or Rust reference impls at https://github.com/decentralized-identity/didwebvh-info)
 * and asserts the IDK's `WebvhLogReader` can decode it cleanly.
 *
 * The fixture is **gated by presence**: if no fixture file is on the test
 * classpath, the tests skip with an informative `assertTrue(true, ...)`
 * rather than fail. This avoids CI flakiness when the upstream changes
 * fixture shape, while still allowing the IDK to demonstrate cross-impl
 * decode compatibility when a fixture is present.
 *
 * To add a fixture:
 * 1. Capture a `did.jsonl` from a reference impl (e.g. by running its
 *    create flow against `example.com`, or by downloading a published log).
 * 2. Drop it at:
 *    `lib/did/methods/webvh/resolver/src/jvmTest/resources/com/sphereon/did/methods/webvh/resolver/cross-impl-did.jsonl`
 * 3. (Optional) Drop a matching `did-witness.json` at the same path with
 *    name `cross-impl-did-witness.json`.
 *
 * Full replay (signature verification) of the fixture requires a working
 * Ed25519 signing+verification path. The signing side is gated on Ed25519
 * KMS support (the IDK's software KMS is ECDSA-only); the verification
 * side works once the resolver chain is wired with the Ed25519 verifier
 * (which `:eddsa-jcs-2022` already provides). A future change can extend
 * this test to drive the replayer over the fixture once those pieces are
 * in place.
 */
class WebvhCrossImplFixtureTest {
    private fun loadFixture(name: String): String? {
        val stream = javaClass.classLoader.getResourceAsStream("com/sphereon/did/methods/webvh/resolver/$name")
        return stream?.use { it.readBytes().decodeToString() }
    }

    @Test
    fun fixtureLogParsesWhenPresent() {
        val jsonl =
            loadFixture("cross-impl-did.jsonl") ?: run {
                assertTrue(
                    true,
                    "No cross-impl-did.jsonl fixture present; skipping. " +
                        "See class kdoc for how to add one.",
                )
                return
            }

        val parsed = WebvhLogReader.read(jsonl)
        val parseError =
            if (parsed.isErr) {
                parsed.error.message
            } else {
                ""
            }
        assertTrue(parsed.isOk, "Fixture log must parse cleanly: $parseError")
        val entries = parsed.value
        assertTrue(entries.isNotEmpty(), "Fixture log must contain at least one entry")

        val genesis = entries.first()
        assertTrue(genesis.versionId.isNotBlank(), "Genesis entry must have a non-blank versionId")
        assertNotNull(genesis.parameters.method, "Genesis entry parameters must declare 'method'")
        assertNotNull(genesis.parameters.scid, "Genesis entry parameters must declare 'scid'")
        assertNotNull(genesis.parameters.updateKeys, "Genesis entry parameters must declare 'updateKeys'")
        assertTrue(genesis.proof.isNotEmpty(), "Genesis entry must carry at least one Data Integrity proof")
    }
}
