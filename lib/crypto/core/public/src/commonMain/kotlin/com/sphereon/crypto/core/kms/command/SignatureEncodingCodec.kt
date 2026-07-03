/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.crypto.core.kms.command

import com.sphereon.crypto.core.generic.SignatureAlgorithm

object SignatureEncodingCodec {
    fun scalarLength(signatureAlgorithm: SignatureAlgorithm): Int =
        when (signatureAlgorithm) {
            SignatureAlgorithm.ECDSA_SHA256 -> 32
            SignatureAlgorithm.ECDSA_SHA384 -> 48
            SignatureAlgorithm.ECDSA_SHA512 -> 66
            else -> throw IllegalArgumentException("Unsupported ECDSA signature algorithm: $signatureAlgorithm")
        }

    fun normalize(
        signature: ByteArray,
        from: SignatureEncoding,
        to: SignatureEncoding,
        signatureAlgorithm: SignatureAlgorithm,
    ): ByteArray {
        if (from == to) return signature.copyOf()
        val scalarLength = scalarLength(signatureAlgorithm)
        return when (from) {
            SignatureEncoding.RAW -> rawToDer(signature, scalarLength)
            SignatureEncoding.DER -> derToRaw(signature, scalarLength)
        }
    }

    fun derToRaw(
        der: ByteArray,
        scalarLength: Int,
    ): ByteArray {
        require(der.size >= 8) { "Invalid DER ECDSA signature length" }
        var offset = 0
        require(der[offset++] == DER_SEQUENCE) { "DER ECDSA signature must start with SEQUENCE" }
        val sequenceLength = readDerLength(der, offset).also { offset = it.nextOffset }.length
        require(offset + sequenceLength == der.size) { "DER ECDSA sequence length mismatch" }
        val r = readDerInteger(der, offset).also { offset = it.nextOffset }.value
        val s = readDerInteger(der, offset).also { offset = it.nextOffset }.value
        require(offset == der.size) { "Trailing bytes in DER ECDSA signature" }
        return unsignedIntegerToFixed(r, scalarLength) + unsignedIntegerToFixed(s, scalarLength)
    }

    fun rawToDer(
        raw: ByteArray,
        scalarLength: Int,
    ): ByteArray {
        require(raw.size == scalarLength * 2) { "Raw ECDSA signature must be ${scalarLength * 2} bytes" }
        val r = derInteger(raw.copyOfRange(0, scalarLength))
        val s = derInteger(raw.copyOfRange(scalarLength, raw.size))
        val body = r + s
        return byteArrayOf(DER_SEQUENCE) + derLength(body.size) + body
    }

    fun isDer(signature: ByteArray): Boolean = signature.firstOrNull() == DER_SEQUENCE

    private fun readDerInteger(
        der: ByteArray,
        offset: Int,
    ): DerInteger {
        var current = offset
        require(current < der.size && der[current++] == DER_INTEGER) { "Expected DER INTEGER" }
        val length = readDerLength(der, current).also { current = it.nextOffset }.length
        require(length > 0) { "DER INTEGER must not be empty" }
        require(current + length <= der.size) { "DER INTEGER length exceeds signature" }
        val value = der.copyOfRange(current, current + length)
        current += length
        require(value.size == 1 || !(value[0] == 0.toByte() && (value[1].toInt() and 0x80) == 0)) {
            "DER INTEGER is not minimally encoded"
        }
        return DerInteger(stripPositiveSignByte(value), current)
    }

    private fun readDerLength(
        der: ByteArray,
        offset: Int,
    ): DerLength {
        require(offset < der.size) { "Missing DER length" }
        val first = der[offset].toInt() and 0xFF
        if ((first and 0x80) == 0) return DerLength(first, offset + 1)
        val count = first and 0x7F
        require(count in 1..2) { "Unsupported DER length width: $count" }
        require(offset + 1 + count <= der.size) { "Truncated DER length" }
        var length = 0
        repeat(count) { index ->
            length = (length shl 8) or (der[offset + 1 + index].toInt() and 0xFF)
        }
        require(length >= 128) { "DER length must use short form below 128" }
        return DerLength(length, offset + 1 + count)
    }

    private fun derInteger(unsigned: ByteArray): ByteArray {
        val withoutLeadingZeroes = unsigned.dropWhile { it == 0.toByte() }.toByteArray()
        val stripped = if (withoutLeadingZeroes.isEmpty()) byteArrayOf(0) else withoutLeadingZeroes
        val value = if ((stripped[0].toInt() and 0x80) != 0) byteArrayOf(0) + stripped else stripped
        return byteArrayOf(DER_INTEGER) + derLength(value.size) + value
    }

    private fun derLength(length: Int): ByteArray {
        require(length >= 0) { "DER length must be non-negative" }
        return when {
            length < 128 -> byteArrayOf(length.toByte())
            length <= 0xFF -> byteArrayOf(0x81.toByte(), length.toByte())
            length <= 0xFFFF -> byteArrayOf(0x82.toByte(), ((length ushr 8) and 0xFF).toByte(), (length and 0xFF).toByte())
            else -> throw IllegalArgumentException("DER length too large")
        }
    }

    private fun stripPositiveSignByte(value: ByteArray): ByteArray =
        if (value.size > 1 && value[0] == 0.toByte()) value.copyOfRange(1, value.size) else value

    private fun unsignedIntegerToFixed(
        value: ByteArray,
        length: Int,
    ): ByteArray {
        require(value.size <= length) { "DER ECDSA integer does not fit in $length bytes" }
        return ByteArray(length - value.size) + value
    }

    private data class DerLength(
        val length: Int,
        val nextOffset: Int,
    )

    private data class DerInteger(
        val value: ByteArray,
        val nextOffset: Int,
    )

    private const val DER_SEQUENCE: Byte = 48
    private const val DER_INTEGER: Byte = 2
}
