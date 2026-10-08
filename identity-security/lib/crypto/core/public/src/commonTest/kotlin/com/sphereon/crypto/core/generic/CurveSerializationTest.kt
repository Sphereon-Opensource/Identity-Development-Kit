/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.core.generic

import com.sphereon.crypto.core.json.CryptoJsonSupport
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks the JSON wire format of [Curve] subclasses.
 *
 * The IDK-21 review noted that marking `Curve.{P_256, P_384, P_521, Secp256k1, Ed25519, X25519}`
 * as `@Serializable` is a cross-cutting change to a public crypto type — round-trip coverage
 * here keeps the discriminator names stable and prevents an accidental rename from silently
 * breaking on-the-wire compatibility for any consumer that serializes a Curve.
 */
class CurveSerializationTest {
    private val json = Json { serializersModule = CryptoJsonSupport.module }

    @Test
    fun roundTripP256() = assertRoundTrip(Curve.P_256, """{"type":"P-256"}""")

    @Test
    fun roundTripP384() = assertRoundTrip(Curve.P_384, """{"type":"P-384"}""")

    @Test
    fun roundTripP521() = assertRoundTrip(Curve.P_521, """{"type":"P-521"}""")

    @Test
    fun roundTripSecp256k1() = assertRoundTrip(Curve.Secp256k1, """{"type":"secp256k1"}""")

    @Test
    fun roundTripEd25519() = assertRoundTrip(Curve.Ed25519, """{"type":"Ed25519"}""")

    @Test
    fun roundTripX25519() = assertRoundTrip(Curve.X25519, """{"type":"X25519"}""")

    private fun assertRoundTrip(
        curve: Curve,
        expectedWire: String,
    ) {
        val encoded = json.encodeToString(Curve.serializer(), curve)
        assertEquals(expectedWire, encoded, "Curve ${curve::class.simpleName} should encode to a stable discriminator")
        val decoded = json.decodeFromString(Curve.serializer(), encoded)
        assertEquals(curve, decoded, "Curve ${curve::class.simpleName} round-trip mismatch")
    }
}
