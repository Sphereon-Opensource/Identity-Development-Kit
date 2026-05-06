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

import com.sphereon.did.methods.webvh.scid.WebvhPreRotationHasher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Spec §3.2.1: `nextKeyHash = base58btc(multihash(utf8(multikey-string), SHA-256))`.
 *
 * Hashes the multikey string itself (e.g. `z6Mk...`), NOT the raw public key bytes.
 */
class WebvhPreRotationHasherTest {
    @Test
    fun stableForSameMultikey() {
        val mk = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        assertEquals(WebvhPreRotationHasher.hashMultikey(mk), WebvhPreRotationHasher.hashMultikey(mk))
    }

    @Test
    fun differsAcrossMultikeys() {
        val a = WebvhPreRotationHasher.hashMultikey("z6Mka")
        val b = WebvhPreRotationHasher.hashMultikey("z6Mkb")
        assertNotEquals(a, b)
    }

    @Test
    fun emitsBase58btcMultibase() {
        val hash = WebvhPreRotationHasher.hashMultikey("z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        assertTrue(hash.startsWith("z"), "Expected multibase 'z' (base58btc) prefix, got: $hash")
    }
}
