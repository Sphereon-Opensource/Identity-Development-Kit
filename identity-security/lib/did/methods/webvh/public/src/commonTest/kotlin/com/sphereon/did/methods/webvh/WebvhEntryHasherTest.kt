/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.methods.webvh

import com.sphereon.did.methods.webvh.scid.WebvhEntryHasher
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Spec §3.1 / §3.3: entryHash = base58btc(multihash(JCS(entryWithoutProof), SHA-256)).
 */
class WebvhEntryHasherTest {
    @Test
    fun computesStableHashForSameInput() {
        val entry =
            buildJsonObject {
                put("versionId", JsonPrimitive("Qm...SCID"))
                put("versionTime", JsonPrimitive("2026-04-01T00:00:00Z"))
            }
        val a = WebvhEntryHasher.computeEntryHash(entry)
        val b = WebvhEntryHasher.computeEntryHash(entry)
        assertEquals(a, b)
    }

    @Test
    fun isCanonicalAcrossKeyOrder() {
        val ordered =
            buildJsonObject {
                put("a", JsonPrimitive("1"))
                put("b", JsonPrimitive("2"))
            }
        val reordered =
            buildJsonObject {
                put("b", JsonPrimitive("2"))
                put("a", JsonPrimitive("1"))
            }
        assertEquals(WebvhEntryHasher.computeEntryHash(ordered), WebvhEntryHasher.computeEntryHash(reordered))
    }

    @Test
    fun differsForDifferentInput() {
        val a = WebvhEntryHasher.computeEntryHash(buildJsonObject { put("v", JsonPrimitive("1")) })
        val b = WebvhEntryHasher.computeEntryHash(buildJsonObject { put("v", JsonPrimitive("2")) })
        assertNotEquals(a, b)
    }

    @Test
    fun emitsBase58btcMultibase() {
        val hash = WebvhEntryHasher.computeEntryHash(buildJsonObject { put("v", JsonPrimitive("1")) })
        assertTrue(hash.startsWith("z"), "Expected multibase 'z' (base58btc) prefix, got: $hash")
    }
}
