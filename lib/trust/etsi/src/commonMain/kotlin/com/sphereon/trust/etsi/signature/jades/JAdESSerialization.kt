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

/** Byte-preserving JWS compact serialization helpers used by JAdES. */
object JAdESSerialization {
    /**
     * Returns the exact ASCII signing input represented by a compact JWS.
     * No trimming, JSON parsing, or re-serialization is performed.
     */
    fun compactSigningInput(
        serializedJws: ByteArray,
        detachedContent: ByteArray? = null,
    ): ByteArray? {
        val firstDot = serializedJws.indexOf(DOT)
        val secondDot = serializedJws.indexOf(DOT, firstDot + 1)
        if (firstDot <= 0 || secondDot <= firstDot || secondDot == serializedJws.lastIndex) {
            return null
        }
        if (serializedJws.indexOf(DOT, secondDot + 1) >= 0) {
            return null
        }

        val protectedSegment = serializedJws.copyOfRange(0, firstDot)
        val serializedPayload = serializedJws.copyOfRange(firstDot + 1, secondDot)
        val signatureSegment = serializedJws.copyOfRange(secondDot + 1, serializedJws.size)
        if (!isBase64UrlSegment(protectedSegment) ||
            (serializedPayload.isNotEmpty() && !isBase64UrlSegment(serializedPayload)) ||
            !isBase64UrlSegment(signatureSegment)
        ) {
            return null
        }
        val payloadSegment =
            if (serializedPayload.isEmpty()) {
                (detachedContent?.encodeTo(Encoding.BASE64URL) ?: return null).encodeToByteArray()
            } else {
                serializedPayload
            }

        return join(protectedSegment, DOT_BYTE, payloadSegment)
    }

    /** Creates compact serialization without changing any encoded segment. */
    fun compactSerialization(
        protectedHeader: ByteArray,
        payload: ByteArray,
        signature: ByteArray,
    ): ByteArray =
        join(
            protectedHeader,
            DOT_BYTE,
            payload,
            DOT_BYTE,
            signature,
        )

    private fun join(vararg parts: ByteArray): ByteArray {
        val result = ByteArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) {
            part.copyInto(result, offset)
            offset += part.size
        }
        return result
    }

    private fun ByteArray.indexOf(value: Byte, startIndex: Int = 0): Int {
        for (index in startIndex.coerceAtLeast(0)..lastIndex) {
            if (this[index] == value) return index
        }
        return -1
    }

    private fun isBase64UrlSegment(segment: ByteArray): Boolean =
        segment.isNotEmpty() && segment.all { value ->
            val character = value.toInt().toChar()
            character in 'A'..'Z' || character in 'a'..'z' || character in '0'..'9' || character == '-' || character == '_'
        }

    private const val DOT: Byte = 46
    private val DOT_BYTE = byteArrayOf(DOT)
}
