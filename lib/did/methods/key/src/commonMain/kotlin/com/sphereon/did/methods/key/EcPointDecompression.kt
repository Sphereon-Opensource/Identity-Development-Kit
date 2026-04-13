/*
 * (c) 2026 Sphereon International B.V.
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
 *
 */

package com.sphereon.did.methods.key

import com.sphereon.crypto.core.generic.Curve

/**
 * EC point decompression for curves used in did:key.
 *
 * Supports P-256, P-384, and Secp256k1. All three primes satisfy p ≡ 3 (mod 4),
 * so square roots can be computed as y = rhs^((p+1)/4) mod p.
 *
 * Field elements are represented as big-endian ByteArray. Arithmetic is performed
 * using [BigUIntOps] which operates on unsigned integer arrays in little-endian
 * UInt-limb form internally.
 */
internal object EcPointDecompression {
    private const val BYTE_MASK = 0xFF
    private const val EC_EVEN_PREFIX = 0x02
    private const val EC_ODD_PREFIX = 0x03
    private const val LSB_MASK = 0x01
    private const val P256_COORD_SIZE = 32
    private const val P384_COORD_SIZE = 48
    private const val SECP256K1_COORD_SIZE = 32
    private const val SECP256K1_B_VALUE: Byte = 7
    private const val SECP256K1_B_INDEX = 31
    private const val HEX_PAIR_SIZE = 2
    private const val HEX_RADIX = 16
    private const val HEX_HIGH_NIBBLE_SHIFT = 4

    /**
     * Decompresses a compressed EC point for the given curve.
     *
     * @param curve The elliptic curve
     * @param compressed 02/03 prefix + x coordinate bytes
     * @return Pair(x, y) as big-endian byte arrays
     */
    fun decompress(
        curve: Curve,
        compressed: ByteArray,
    ): Pair<ByteArray, ByteArray> {
        val params = curveParams(curve)
        val prefix = compressed[0].toInt() and BYTE_MASK
        require(prefix == EC_EVEN_PREFIX || prefix == EC_ODD_PREFIX) {
            "Invalid compressed point prefix: $prefix"
        }
        val xBytes = compressed.sliceArray(1 until compressed.size)
        require(xBytes.size == params.coordSize) {
            "Invalid x coordinate size for $curve: expected ${params.coordSize}, got ${xBytes.size}"
        }

        val ops = BigUIntOps(params.p)
        val x = ops.fromBytes(xBytes)
        val a = ops.fromBytes(params.a)
        val b = ops.fromBytes(params.b)

        // rhs = x^3 + a*x + b  (mod p)
        val x2 = ops.mul(x, x)
        val x3 = ops.mul(x2, x)
        val ax = ops.mul(a, x)
        val rhs = ops.add(ops.add(x3, ax), b)

        // y = rhs^((p+1)/4) mod p
        val exp = ops.fromBytes(params.pPlus1Div4)
        val y = ops.pow(rhs, exp)

        // Verify y^2 == rhs
        val ySquared = ops.mul(y, y)
        require(ops.eq(ySquared, rhs)) {
            "Point is not on curve $curve"
        }

        // Select parity: 0x02 = even y, 0x03 = odd y
        val yBytes = ops.toBytes(y)
        val yIsEven = (yBytes[params.coordSize - 1].toInt() and LSB_MASK) == 0
        val wantEven = prefix == EC_EVEN_PREFIX

        val finalY =
            if (yIsEven == wantEven) {
                yBytes
            } else {
                ops.toBytes(ops.sub(ops.fromBytes(params.p), y))
            }

        return Pair(xBytes, finalY)
    }

    private fun curveParams(curve: Curve): CurveParams =
        when (curve) {
            Curve.P_256 -> P256_PARAMS
            Curve.P_384 -> P384_PARAMS
            Curve.Secp256k1 -> SECP256K1_PARAMS
            else -> throw IllegalArgumentException("Unsupported curve: $curve")
        }

    private class CurveParams(
        val coordSize: Int,
        val p: ByteArray,
        val a: ByteArray,
        val b: ByteArray,
        val pPlus1Div4: ByteArray,
    )

    // P-256: p = 2^256 - 2^224 + 2^192 + 2^96 - 1, a = -3 (i.e. p-3), b = ...
    private val P256_PARAMS =
        CurveParams(
            coordSize = P256_COORD_SIZE,
            p = hexToBytes("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF"),
            a = hexToBytes("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC"), // p - 3
            b = hexToBytes("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B"),
            // (p+1)/4 = (FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF + 1) / 4
            // = 3FFFFFFFC0000000400000000000000000000000400000000000000000000000
            pPlus1Div4 = hexToBytes("3FFFFFFFC0000000400000000000000000000000400000000000000000000000"),
        )

    // P-384: p = 2^384 - 2^128 - 2^96 + 2^32 - 1, a = -3 (p-3), b = ...
    private val P384_PARAMS =
        CurveParams(
            coordSize = P384_COORD_SIZE,
            p = hexToBytes("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFF0000000000000000FFFFFFFF"),
            a = hexToBytes("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFF0000000000000000FFFFFFFC"), // p - 3
            b = hexToBytes("B3312FA7E23EE7E4988E056BE3F82D19181D9C6EFE8141120314088F5013875AC656398D8A2ED19D2A85C8EDD3EC2AEF"),
            // (p+1)/4
            pPlus1Div4 = hexToBytes("3FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF BFFFFFFFC0000000000000003FFFFFFF".replace(" ", "")),
        )

    // Secp256k1: p = 2^256 - 2^32 - 977, a = 0, b = 7
    private val SECP256K1_PARAMS =
        CurveParams(
            coordSize = SECP256K1_COORD_SIZE,
            p = hexToBytes("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F"),
            a = ByteArray(SECP256K1_COORD_SIZE), // a = 0
            b = ByteArray(SECP256K1_COORD_SIZE).also { it[SECP256K1_B_INDEX] = SECP256K1_B_VALUE }, // b = 7
            pPlus1Div4 = hexToBytes("3FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFBFFFFF0C"),
        )

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % HEX_PAIR_SIZE == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / HEX_PAIR_SIZE) { i ->
            ((hex[i * HEX_PAIR_SIZE].digitToInt(HEX_RADIX) shl HEX_HIGH_NIBBLE_SHIFT) or hex[i * HEX_PAIR_SIZE + 1].digitToInt(HEX_RADIX)).toByte()
        }
    }
}

/**
 * Modular arithmetic on big unsigned integers represented as little-endian UInt limb arrays.
 *
 * The modulus p is set at construction and all operations return results in [0, p).
 */
internal class BigUIntOps(
    pBytes: ByteArray,
) {
    private val limbCount: Int = (pBytes.size + LIMB_PADDING) / BYTES_PER_LIMB // number of 32-bit limbs
    private val byteSize: Int = pBytes.size
    private val p: UIntArray = bytesToLimbs(pBytes)

    fun fromBytes(bytes: ByteArray): UIntArray {
        val padded =
            if (bytes.size < byteSize) {
                ByteArray(byteSize - bytes.size) + bytes
            } else {
                bytes
            }
        return bytesToLimbs(padded)
    }

    fun toBytes(limbs: UIntArray): ByteArray {
        val bytes = ByteArray(byteSize)
        for (i in 0 until limbCount) {
            val offset = byteSize - BYTES_PER_LIMB - i * BYTES_PER_LIMB
            if (offset >= 0) {
                bytes[offset] = (limbs[i] shr SHIFT_BYTE3).toByte()
                bytes[offset + 1] = (limbs[i] shr SHIFT_BYTE2).toByte()
                bytes[offset + 2] = (limbs[i] shr SHIFT_BYTE1).toByte()
                bytes[offset + LIMB_PADDING] = limbs[i].toByte()
            } else {
                // Partial last limb (when byteSize is not a multiple of 4)
                val partialOffset = 0
                val shift = (-offset) * BITS_PER_BYTE
                for (b in 0 until (BYTES_PER_LIMB + offset)) {
                    bytes[partialOffset + b] = (limbs[i] shr (SHIFT_BYTE3 - shift - b * BITS_PER_BYTE)).toByte()
                }
            }
        }
        return bytes
    }

    fun eq(
        a: UIntArray,
        b: UIntArray,
    ): Boolean {
        for (i in 0 until limbCount) {
            if (a[i] != b[i]) {
                return false
            }
        }
        return true
    }

    fun add(
        a: UIntArray,
        b: UIntArray,
    ): UIntArray {
        val r = UIntArray(limbCount)
        var carry = 0UL
        for (i in 0 until limbCount) {
            carry += a[i].toULong() + b[i].toULong()
            r[i] = carry.toUInt()
            carry = carry shr LIMB_BITS_COUNT
        }
        return if (carry > 0UL || cmp(r, p) >= 0) {
            subUnchecked(r, p)
        } else {
            r
        }
    }

    fun sub(
        a: UIntArray,
        b: UIntArray,
    ): UIntArray =
        if (cmp(a, b) >= 0) {
            subUnchecked(a, b)
        } else {
            // a - b + p
            val tmp = UIntArray(limbCount)
            var carry = 0UL
            for (i in 0 until limbCount) {
                carry += a[i].toULong() + p[i].toULong()
                tmp[i] = carry.toUInt()
                carry = carry shr LIMB_BITS_COUNT
            }
            subUnchecked(tmp, b)
        }

    fun mul(
        a: UIntArray,
        b: UIntArray,
    ): UIntArray {
        // Schoolbook multiply into double-width result
        val wide = UIntArray(limbCount * 2)
        for (i in 0 until limbCount) {
            var carry = 0UL
            for (j in 0 until limbCount) {
                val prod = a[i].toULong() * b[j].toULong() + wide[i + j].toULong() + carry
                wide[i + j] = prod.toUInt()
                carry = prod shr LIMB_BITS_COUNT
            }
            wide[i + limbCount] = carry.toUInt()
        }
        return modReduceWide(wide)
    }

    fun pow(
        base: UIntArray,
        exp: UIntArray,
    ): UIntArray {
        var result = UIntArray(limbCount).also { it[0] = 1U }
        var seenOne = false

        for (i in limbCount - 1 downTo 0) {
            for (bit in LIMB_TOP_BIT downTo 0) {
                if (seenOne) {
                    result = mul(result, result)
                }
                if ((exp[i] shr bit) and 1U == 1U) {
                    seenOne = true
                    result = mul(result, base)
                }
            }
        }
        return result
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private fun cmp(
        a: UIntArray,
        b: UIntArray,
    ): Int {
        for (i in limbCount - 1 downTo 0) {
            if (a[i] != b[i]) {
                return if (a[i] > b[i]) {
                    1
                } else {
                    -1
                }
            }
        }
        return 0
    }

    private fun cmpWide(
        a: UIntArray,
        b: UIntArray,
        bShift: Int,
    ): Int {
        val aLen = a.size
        for (i in aLen - 1 downTo 0) {
            val bIdx = i - bShift
            val bVal =
                if (bIdx in b.indices) {
                    b[bIdx]
                } else {
                    0U
                }
            if (a[i] != bVal) {
                return if (a[i] > bVal) {
                    1
                } else {
                    -1
                }
            }
        }
        return 0
    }

    private fun subUnchecked(
        a: UIntArray,
        b: UIntArray,
    ): UIntArray {
        val r = UIntArray(a.size)
        var borrow = 0L
        for (i in a.indices) {
            val bVal =
                if (i < b.size) {
                    b[i].toLong()
                } else {
                    0L
                }
            val diff = a[i].toLong() - bVal - borrow
            if (diff < 0) {
                r[i] = (diff + UINT_OVERFLOW).toUInt()
                borrow = 1
            } else {
                r[i] = diff.toUInt()
                borrow = 0
            }
        }
        return r
    }

    /**
     * Subtracts b shifted left by `shift` limb positions from a (in place).
     */
    private fun subShiftedInPlace(
        a: UIntArray,
        b: UIntArray,
        shift: Int,
    ) {
        var borrow = 0L
        for (i in b.indices) {
            val idx = i + shift
            if (idx >= a.size) {
                break
            }
            val diff = a[idx].toLong() - b[i].toLong() - borrow
            if (diff < 0) {
                a[idx] = (diff + UINT_OVERFLOW).toUInt()
                borrow = 1
            } else {
                a[idx] = diff.toUInt()
                borrow = 0
            }
        }
        // propagate borrow
        var idx = b.size + shift
        while (borrow > 0 && idx < a.size) {
            val diff = a[idx].toLong() - borrow
            if (diff < 0) {
                a[idx] = (diff + UINT_OVERFLOW).toUInt()
                borrow = 1
            } else {
                a[idx] = diff.toUInt()
                borrow = 0
            }
            idx++
        }
    }

    /**
     * Reduces a double-width product modulo p using long division.
     *
     * For each limb position from the top down, estimates the quotient digit
     * using a two-limb dividend / single-limb divisor, then subtracts the
     * corresponding multiple of p. This is O(n^2) in limb count, not O(2^32).
     */
    private fun modReduceWide(wide: UIntArray): UIntArray {
        val w = wide.copyOf()
        val n = limbCount
        val pHi = p[n - 1].toULong()

        // Long-division style reduction: process from the highest extra limb down
        for (j in w.size - 1 downTo n) {
            if (w[j] == 0U) {
                continue
            }

            // Estimate q = (w[j] * 2^32 + w[j-1]) / (pHi + 1)
            // Under-estimates to avoid over-subtraction
            val dividend = (w[j].toULong() shl LIMB_BITS_COUNT) or w[j - 1].toULong()
            var q = dividend / (pHi + 1UL)
            if (q == 0UL) {
                q = 1UL
            } // always make progress

            // Subtract q * p shifted by (j - n) limb positions
            val shift = j - n
            // Could do q > UInt.MAX: split into iterations, but product of two n-bit
            // numbers has at most 2n bits, so q fits in a UInt for n-limb primes close to 2^(32n)
            val qU = q.toUInt()
            var borrow = 0UL
            for (i in 0 until n) {
                val idx = i + shift
                val prod = p[i].toULong() * qU.toULong() + borrow
                val lo = prod.toUInt()
                borrow = prod shr LIMB_BITS_COUNT
                val diff = w[idx].toLong() - lo.toLong()
                if (diff < 0) {
                    w[idx] = (diff + UINT_OVERFLOW).toUInt()
                    borrow++
                } else {
                    w[idx] = diff.toUInt()
                }
            }
            // Apply remaining borrow
            var bidx = n + shift
            while (borrow > 0UL && bidx < w.size) {
                val diff = w[bidx].toULong() - borrow
                if (diff > w[bidx].toULong()) {
                    w[bidx] = (diff + UINT_OVERFLOW_UL).toUInt()
                    borrow = 1UL
                } else {
                    w[bidx] = diff.toUInt()
                    borrow = 0UL
                }
                bidx++
            }

            // w[j] may still be nonzero if q was under-estimated; loop will revisit
            // But since q >= 1, we always reduce the value, so this terminates in O(1) retries
            if (w[j] != 0U) {
                // One more subtraction of p at this shift should suffice
                subShiftedInPlace(w, p, shift)
            }
        }

        // Extract low n limbs and do final reduction
        val result = UIntArray(n)
        for (i in 0 until n) result[i] = w[i]
        return if (cmp(result, p) >= 0) {
            subUnchecked(result, p)
        } else {
            result
        }
    }

    /** Decode big-endian bytes to little-endian UInt limbs. */
    private fun bytesToLimbs(bytes: ByteArray): UIntArray {
        val limbs = UIntArray(limbCount)
        for (i in 0 until limbCount) {
            val offset = bytes.size - BYTES_PER_LIMB - i * BYTES_PER_LIMB
            if (offset >= 0) {
                limbs[i] = ((bytes[offset].toInt() and BYTE_MASK).toUInt() shl SHIFT_BYTE3) or
                    ((bytes[offset + 1].toInt() and BYTE_MASK).toUInt() shl SHIFT_BYTE2) or
                    ((bytes[offset + 2].toInt() and BYTE_MASK).toUInt() shl SHIFT_BYTE1) or
                    (bytes[offset + LIMB_PADDING].toInt() and BYTE_MASK).toUInt()
            } else {
                // Partial last limb
                var v = 0U
                for (b in 0 until (BYTES_PER_LIMB + offset)) {
                    v = v or ((bytes[b].toInt() and BYTE_MASK).toUInt() shl ((LIMB_PADDING + offset - b) * BITS_PER_BYTE))
                }
                limbs[i] = v
            }
        }
        return limbs
    }

    companion object {
        private const val BYTES_PER_LIMB = 4
        private const val LIMB_PADDING = 3
        private const val LIMB_BITS_COUNT = 32
        private const val LIMB_TOP_BIT = 31
        private const val BITS_PER_BYTE = 8
        private const val SHIFT_BYTE3 = 24
        private const val SHIFT_BYTE2 = 16
        private const val SHIFT_BYTE1 = 8
        private const val BYTE_MASK = 0xFF
        private const val UINT_OVERFLOW = 0x100000000L
        private const val UINT_OVERFLOW_UL = 0x100000000UL
    }
}
