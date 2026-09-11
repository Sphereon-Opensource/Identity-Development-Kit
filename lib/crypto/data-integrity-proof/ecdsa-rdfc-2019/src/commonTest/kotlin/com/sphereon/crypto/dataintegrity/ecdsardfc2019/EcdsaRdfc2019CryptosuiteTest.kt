/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.crypto.dataintegrity.ecdsardfc2019

import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.jose.JwaCurve
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EcdsaRdfc2019CryptosuiteTest {
    @Test
    fun curveProfilesUseTheNormativeDigestAndSignatureSizes() {
        assertEquals(DigestAlg.SHA256, EcdsaRdfc2019Cryptosuite.digestForCurve(JwaCurve.P_256))
        assertEquals(DigestAlg.SHA384, EcdsaRdfc2019Cryptosuite.digestForCurve(JwaCurve.P_384))
        assertEquals(32, EcdsaRdfc2019Cryptosuite.signatureScalarBytes(JwaCurve.P_256))
        assertEquals(48, EcdsaRdfc2019Cryptosuite.signatureScalarBytes(JwaCurve.P_384))
    }

    @Test
    fun proofValueUsesBase58BtcAndExactP1363Length() {
        val raw = ByteArray(64) { it.toByte() }
        val encoded = EcdsaRdfc2019Cryptosuite.encodeProofValue(raw, JwaCurve.P_256)
        assertEquals(raw.toList(), EcdsaRdfc2019Cryptosuite.decodeProofValue(encoded, JwaCurve.P_256).toList())
        assertFailsWith<IllegalArgumentException> {
            EcdsaRdfc2019Cryptosuite.encodeProofValue(ByteArray(63), JwaCurve.P_256)
        }
        assertFailsWith<IllegalArgumentException> {
            EcdsaRdfc2019Cryptosuite.decodeProofValue("u${encoded.substring(1)}", JwaCurve.P_256)
        }
        assertFailsWith<IllegalArgumentException> {
            EcdsaRdfc2019Cryptosuite.decodeProofValue(encoded, JwaCurve.P_384)
        }
    }
}
