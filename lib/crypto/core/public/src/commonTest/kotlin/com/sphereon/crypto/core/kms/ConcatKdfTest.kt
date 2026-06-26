/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.core.kms

import kotlin.test.Test
import kotlin.test.assertEquals

class ConcatKdfTest {
    @Test
    fun wrappedEcdhUsesKeyWrapAlgorithmId() {
        assertEquals("A128KW", ConcatKdf.getAlgorithmId(KeyAgreementAlgorithm.ECDH_ES_A128KW, "A256GCM"))
        assertEquals("A192KW", ConcatKdf.getAlgorithmId(KeyAgreementAlgorithm.ECDH_ES_A192KW, "A256GCM"))
        assertEquals("A256KW", ConcatKdf.getAlgorithmId(KeyAgreementAlgorithm.ECDH_ES_A256KW, "A256GCM"))
    }

    @Test
    fun directEcdhUsesContentEncryptionAlgorithmId() {
        assertEquals("A256GCM", ConcatKdf.getAlgorithmId(KeyAgreementAlgorithm.ECDH_ES, "A256GCM"))
    }
}
