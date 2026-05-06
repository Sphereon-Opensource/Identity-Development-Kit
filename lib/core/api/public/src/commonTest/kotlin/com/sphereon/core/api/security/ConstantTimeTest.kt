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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Functional contract tests for [ConstantTime]. Timing-channel verification belongs in a
 * statistical benchmark (out of scope for unit tests on a noisy CI runner — JIT warm-up,
 * GC pauses, and OS scheduling jitter swamp the per-byte signal we care about). These tests
 * pin the output behaviour: equal inputs return true, unequal inputs return false, and the
 * length-mismatch path matches the equal-length mismatch path.
 */
class ConstantTimeTest {
    @Test
    fun equalStringsReturnTrue() {
        assertTrue(ConstantTime.equalsCT("hello", "hello"))
        assertTrue(ConstantTime.equalsCT("", ""))
        assertTrue(ConstantTime.equalsCT("a", "a"))
    }

    @Test
    fun differentStringsReturnFalse() {
        assertFalse(ConstantTime.equalsCT("hello", "world"))
        assertFalse(ConstantTime.equalsCT("a", "b"))
    }

    @Test
    fun differentLengthsReturnFalse() {
        // Length mismatch is handled by walking max(a, b) length. The point is that we
        // return false WITHOUT short-circuiting on the length check itself.
        assertFalse(ConstantTime.equalsCT("hello", "hello!"))
        assertFalse(ConstantTime.equalsCT("hello!", "hello"))
        assertFalse(ConstantTime.equalsCT("", "x"))
        assertFalse(ConstantTime.equalsCT("x", ""))
    }

    @Test
    fun matchingPrefixThenDivergeReturnsFalse() {
        // The classic timing-attack target shape: identical prefix, single byte differs at
        // a later position. The function must return false regardless of where the divergence
        // occurs.
        assertFalse(ConstantTime.equalsCT("aaaaaaaaab", "aaaaaaaaaa"))
        assertFalse(ConstantTime.equalsCT("aaaaaaaaab", "aaaaaaaaac"))
    }

    @Test
    fun unicodeStringsCompareByUtf8Bytes() {
        // The implementation encodes both inputs to UTF-8 before comparing. Confirm
        // multi-byte code points still compare equal when they should.
        assertTrue(ConstantTime.equalsCT("café", "café"))
        assertFalse(ConstantTime.equalsCT("café", "cafe"))
    }

    @Test
    fun byteArrayEqualReturnTrue() {
        assertTrue(ConstantTime.equalsCT(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertTrue(ConstantTime.equalsCT(ByteArray(0), ByteArray(0)))
    }

    @Test
    fun byteArrayDifferentReturnFalse() {
        assertFalse(ConstantTime.equalsCT(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertFalse(ConstantTime.equalsCT(byteArrayOf(1, 2, 3), byteArrayOf(1, 2)))
        assertFalse(ConstantTime.equalsCT(byteArrayOf(0), ByteArray(0)))
    }
}
