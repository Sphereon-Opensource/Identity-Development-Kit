/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.kms.provider.software

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.interop.expectedOkpKeyByteLength
import com.sphereon.crypto.core.interop.okpRawToJwk
import com.sphereon.crypto.core.interop.toRawOkpPrivateKeyBytes
import com.sphereon.crypto.core.interop.toRawOkpPublicKeyBytes
import com.sphereon.crypto.core.jose.JwaKeyType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SoftwareOkpJwkRoundTripTest {
    private val curves =
        listOf(
            Curve.Ed25519,
            Curve.Ed448,
            Curve.X25519,
            Curve.X448,
        )

    private fun seed(
        curve: Curve,
        fillByte: Byte
    ): ByteArray = ByteArray(expectedOkpKeyByteLength(curve)) { fillByte }

    @Test
    fun rawToJwkAndBackPreservesPublicAndPrivateBytes() {
        for (curve in curves) {
            val pub = seed(curve, 0x42)
            val prv = seed(curve, 0x55)
            val jwk = okpRawToJwk(rawPublic = pub, rawPrivate = prv, curve = curve)

            assertEquals(JwaKeyType.OKP, jwk.kty, "kty for $curve")
            assertEquals(curve.jose, jwk.crv, "crv for $curve")
            assertContentEquals(pub, jwk.toRawOkpPublicKeyBytes(curve), "public roundtrip for $curve")
            assertContentEquals(prv, jwk.toRawOkpPrivateKeyBytes(curve), "private roundtrip for $curve")
        }
    }

    @Test
    fun publicOnlyJwkOmitsD() {
        for (curve in curves) {
            val pub = seed(curve, 0x11)
            val jwk = okpRawToJwk(rawPublic = pub, rawPrivate = null, curve = curve)
            assertEquals(null, jwk.d, "d must be null for public-only $curve JWK")
            assertContentEquals(pub, jwk.toRawOkpPublicKeyBytes(curve), "public roundtrip for $curve")
        }
    }

    @Test
    fun rejectsWrongPublicLength() {
        for (curve in curves) {
            val expected = expectedOkpKeyByteLength(curve)
            val wrongLength = ByteArray(expected + 1)
            assertFailsWith<IllegalArgumentException>("$curve must reject ${wrongLength.size}-byte public key (expected $expected)") {
                okpRawToJwk(rawPublic = wrongLength, rawPrivate = null, curve = curve)
            }
        }
    }

    @Test
    fun rejectsWrongPrivateLength() {
        for (curve in curves) {
            val expected = expectedOkpKeyByteLength(curve)
            val pub = seed(curve, 0x01)
            val wrongLength = ByteArray(expected - 1)
            assertFailsWith<IllegalArgumentException>("$curve must reject ${wrongLength.size}-byte private key (expected $expected)") {
                okpRawToJwk(rawPublic = pub, rawPrivate = wrongLength, curve = curve)
            }
        }
    }

    @Test
    fun toRawOkpPrivateKeyBytesFailsWhenJwkHasNoD() {
        for (curve in curves) {
            val jwk = okpRawToJwk(rawPublic = seed(curve, 0x33), rawPrivate = null, curve = curve)
            assertFailsWith<IllegalArgumentException>("$curve public-only JWK must reject toRawOkpPrivateKeyBytes") {
                jwk.toRawOkpPrivateKeyBytes(curve)
            }
        }
    }

    @Test
    fun publicJwkExposesXAsBase64UrlOfRawBytes() {
        for (curve in curves) {
            val pub = seed(curve, 0x77)
            val jwk = okpRawToJwk(rawPublic = pub, rawPrivate = null, curve = curve)
            val xValue = jwk.x ?: error("x must be present for $curve")
            val decoded = xValue.decodeFrom(Encoding.BASE64URL)
            assertContentEquals(pub, decoded, "x must round-trip as base64url(rawPublic) for $curve")
        }
    }
}
