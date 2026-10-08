/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.security

/**
 * Constant-time equality helpers for comparing values that an attacker could choose. Naïve
 * equality (`==` on `String`, `equals` on `ByteArray`) leaks length and prefix-match
 * information through timing side channels: the comparison short-circuits on the first byte
 * that differs, so an attacker who can measure response time across many requests can
 * recover the secret one byte at a time.
 *
 * The implementations below run in time proportional to the longer input, never to the
 * matching prefix. A length mismatch flips the diff bit but the loop still walks every
 * position so the timing distinguisher disappears at the cost of a few microseconds.
 *
 * Use these comparators for any value where a timing oracle would be useful to an attacker:
 * - PKCE `code_challenge` ↔ `code_verifier` (RFC 7636 §4.6 — leaks knowledge of valid codes)
 * - Client secrets / client assertions
 * - DPoP `ath` claim (token-binding hash; timing leak narrows access-token guesses)
 * - Refresh token comparisons against stored fingerprints
 * - HMAC tags, OTP / TOTP codes, action-token replay-prevention IDs
 *
 * Don't use for genuinely public values (URLs, audience strings, scope sets) where timing
 * leakage carries no information; CT compares are slower and noisier in profiling.
 *
 * Why not `String.equals`? On the JVM, `String.equals` short-circuits on the first
 * differing code point. Same on Kotlin/JS (`==` falls through to native `==` which
 * short-circuits) and Kotlin/Native. So the issue exists on every KMP target, not just JVM,
 * which is why this lives in `commonMain` of `lib-core-api-public` (broadly reachable).
 */
object ConstantTime {
    /**
     * Constant-time equality of two strings. Encodes both to UTF-8 first because
     * `String.equals` short-circuits on the first differing code point. The UTF-8 byte
     * comparison guarantees the constant-time property regardless of platform.
     *
     * `a` and `b` may be different lengths; the comparator returns false in constant time
     * (proportional to `max(a.length, b.length)`) without revealing which is longer.
     */
    fun equalsCT(
        a: String,
        b: String,
    ): Boolean = equalsCT(a.encodeToByteArray(), b.encodeToByteArray())

    /**
     * Constant-time equality of two byte arrays. Loop walks every position regardless of
     * whether a mismatch was found earlier; XORs accumulate into a single int that is zero
     * iff the arrays are identical. Length mismatch sets the accumulator to non-zero on the
     * first iteration but the full loop still runs, preserving the constant-time property.
     */
    fun equalsCT(
        a: ByteArray,
        b: ByteArray,
    ): Boolean {
        // Walk the longer input so an attacker probing length cannot distinguish "rejected
        // because lengths differ" (cheap) from "rejected because bytes differ" (expensive)
        // by request latency alone.
        val n = maxOf(a.size, b.size)
        var diff = a.size xor b.size
        for (i in 0 until n) {
            // Out-of-bounds reads on the shorter array are replaced with 0 so the XOR still
            // contributes to `diff`. The bounds check is a constant-time branch (predictable
            // by the CPU after warm-up) so it does not reintroduce a timing channel.
            val ai = if (i < a.size) a[i].toInt() else 0
            val bi = if (i < b.size) b[i].toInt() else 0
            diff = diff or (ai xor bi)
        }
        return diff == 0
    }
}
