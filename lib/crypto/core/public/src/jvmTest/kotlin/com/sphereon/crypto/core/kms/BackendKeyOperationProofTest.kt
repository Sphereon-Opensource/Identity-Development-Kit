/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.core.kms

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackendKeyOperationProofTest {
    @Test
    fun plaintextIsConsumedOnceAndZeroized() {
        val proved = BackendKeyProvedDecryption(byteArrayOf(1, 2, 3), DIGEST)
        lateinit var observed: ByteArray

        proved.usePlaintext { plaintext ->
            observed = plaintext
            assertContentEquals(byteArrayOf(1, 2, 3), plaintext)
        }

        assertTrue(observed.all { it == 0.toByte() })
        assertFailsWith<IllegalStateException> { proved.usePlaintext { } }
    }

    @Test
    fun closeDestroysUnconsumedPlaintextAndPreventsReuse() {
        val proved = BackendKeyProvedDecryption(byteArrayOf(1), DIGEST)

        proved.close()
        proved.close()

        assertFailsWith<IllegalStateException> { proved.usePlaintext { } }
    }

    private companion object {
        const val DIGEST = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
