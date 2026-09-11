package com.sphereon.crypto.core.kms.command

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SignatureEncodingCodecTest {
    @Test
    fun derToRawRejectsNegativeInteger() {
        val negativeInteger =
            byteArrayOf(
                0x30,
                0x06,
                0x02,
                0x01,
                0x80.toByte(),
                0x02,
                0x01,
                0x01,
            )

        assertFailsWith<IllegalArgumentException> {
            SignatureEncodingCodec.derToRaw(negativeInteger, scalarLength = 1)
        }
    }

    @Test
    fun derToRawRejectsUnnecessaryPositiveSignPadding() {
        val unnecessarySignPadding =
            byteArrayOf(
                0x30,
                0x07,
                0x02,
                0x02,
                0x00,
                0x7f,
                0x02,
                0x01,
                0x01,
            )

        assertFailsWith<IllegalArgumentException> {
            SignatureEncodingCodec.derToRaw(unnecessarySignPadding, scalarLength = 2)
        }
    }

    @Test
    fun derToRawRejectsNonMinimalTwoByteLength() {
        val nonMinimalLength =
            byteArrayOf(0x30, 0x81.toByte(), 0x87.toByte(), 0x02, 0x01, 0x01, 0x02, 0x82.toByte(), 0x00, 0x80.toByte()) +
                byteArrayOf(0x01) + ByteArray(127)

        val failure = assertFailsWith<IllegalArgumentException> {
            SignatureEncodingCodec.derToRaw(nonMinimalLength, scalarLength = 128)
        }
        assertTrue(failure.message.orEmpty().contains("leading zero"))
    }

    @Test
    fun derToRawRejectsTruncatedAndTrailingData() {
        val truncated = byteArrayOf(0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01)
        val valid = SignatureEncodingCodec.rawToDer(byteArrayOf(0x01, 0x01), scalarLength = 1)
        val trailingData = valid + byteArrayOf(0x00)

        assertFailsWith<IllegalArgumentException> {
            SignatureEncodingCodec.derToRaw(truncated, scalarLength = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            SignatureEncodingCodec.derToRaw(trailingData, scalarLength = 1)
        }
    }

    @Test
    fun validHighBitP256P384P521ValuesRoundTripWithExplicitWidths() {
        for (scalarLength in listOf(32, 48, 66)) {
            val raw = ByteArray(scalarLength * 2) { index -> (index * 37 + 11).toByte() }
            if (scalarLength == 66) {
                raw[0] = 0x01
                raw[1] = 0x80.toByte()
                raw[scalarLength] = 0x01
                raw[scalarLength + 1] = 0xa5.toByte()
            } else {
                raw[0] = 0x80.toByte()
                raw[scalarLength] = 0xa5.toByte()
            }

            val der = SignatureEncodingCodec.rawToDer(raw, scalarLength)

            assertContentEquals(raw, SignatureEncodingCodec.derToRaw(der, scalarLength))
        }
    }

    @Test
    fun canonicalPositiveGoldenDerRoundTripsAndNormalizesAcrossWidths() {
        val goldenDer = byteArrayOf(0x30, 0x08, 0x02, 0x02, 0x00, 0x80.toByte(), 0x02, 0x02, 0x00, 0xff.toByte())
        val algorithms = listOf(
            32 to SignatureAlgorithm.ECDSA_SHA256,
            48 to SignatureAlgorithm.ECDSA_SHA384,
            66 to SignatureAlgorithm.ECDSA_SHA512,
        )

        for ((scalarLength, algorithm) in algorithms) {
            val raw = ByteArray(scalarLength * 2)
            raw[scalarLength - 1] = 0x80.toByte()
            raw[raw.lastIndex] = 0xff.toByte()

            assertContentEquals(goldenDer, SignatureEncodingCodec.rawToDer(raw, scalarLength))
            assertContentEquals(raw, SignatureEncodingCodec.derToRaw(goldenDer, scalarLength))
            assertContentEquals(goldenDer, SignatureEncodingCodec.normalize(raw, SignatureEncoding.RAW, SignatureEncoding.DER, algorithm))
            assertContentEquals(raw, SignatureEncodingCodec.normalize(goldenDer, SignatureEncoding.DER, SignatureEncoding.RAW, algorithm))
        }
    }
}
