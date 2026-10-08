/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.trust.etsi.signature.jades

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class JAdESSerializationTest {
    @Test
    fun compactSigningInputPreservesSerializedSegmentsExactly() {
        val protectedSegment = "eyJhbGciOiJSUzI1NiJ9"
        val payloadSegment = "eyJ2YWx1ZSI6Ingi fQ".replace(" ", "")
        val serialized = "$protectedSegment.$payloadSegment.signature".encodeToByteArray()

        assertContentEquals(
            "$protectedSegment.$payloadSegment".encodeToByteArray(),
            JAdESSerialization.compactSigningInput(serialized),
        )
    }

    @Test
    fun compactSerializationRejectsWhitespaceThatWouldChangeTheSignedBytes() {
        val serialized = " eyJhbGciOiJSUzI1NiJ9.eA.signature".encodeToByteArray()

        assertNull(JAdESSerialization.compactSigningInput(serialized))
    }

    @Test
    fun detachedSigningInputUsesExactDetachedBytes() {
        val protectedSegment = "eyJhbGciOiJSUzI1NiJ9"
        val detached = byteArrayOf(0, 1, 2, 255.toByte())
        val payloadSegment = detached.encodeTo(Encoding.BASE64URL)
        val serialized = "$protectedSegment..signature".encodeToByteArray()

        assertContentEquals(
            "$protectedSegment.$payloadSegment".encodeToByteArray(),
            JAdESSerialization.compactSigningInput(serialized, detached),
        )
    }
}
