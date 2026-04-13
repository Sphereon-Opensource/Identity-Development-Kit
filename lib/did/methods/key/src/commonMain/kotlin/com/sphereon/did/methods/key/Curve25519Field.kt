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

/**
 * Field arithmetic over GF(2^255 - 19) using radix-2^51 representation.
 *
 * Each field element is stored as 5 ULong limbs where:
 *   value = limbs[0] + limbs[1]*2^51 + limbs[2]*2^102 + limbs[3]*2^153 + limbs[4]*2^204
 *
 * This representation is standard for Curve25519 implementations and provides
 * sufficient headroom in 64-bit limbs for carry propagation during multiplication.
 */
internal object Curve25519Field {
    private const val LIMB_BITS = 51
    private const val LIMB_COUNT = 5
    private const val FIELD_ELEMENT_BYTES = 32
    private const val BITS_PER_BYTE = 8
    private const val BYTES_PER_WORD = 8
    private const val HALF_WORD_BITS = 32
    private const val REDUCTION_CONSTANT = 19UL
    private const val TWO_P0_OFFSET = 38UL
    private const val TWO_PN_OFFSET = 2UL

    private const val LIMB0_BYTE_OFFSET = 0
    private const val LIMB1_BYTE_OFFSET = 6
    private const val LIMB1_BIT_SHIFT = 3
    private const val LIMB2_BYTE_OFFSET = 12
    private const val LIMB2_BIT_SHIFT = 6
    private const val LIMB3_BYTE_OFFSET = 19
    private const val LIMB3_BIT_SHIFT = 1
    private const val LIMB4_BYTE_OFFSET = 24
    private const val LIMB4_BIT_SHIFT = 12

    private const val ENCODE_SHIFT_01 = 51
    private const val ENCODE_SHIFT_12 = 13
    private const val ENCODE_SHIFT_12_INV = 38
    private const val ENCODE_SHIFT_23 = 26
    private const val ENCODE_SHIFT_23_INV = 25
    private const val ENCODE_SHIFT_34 = 39
    private const val ENCODE_SHIFT_34_INV = 12

    private const val CARRY_SHIFT = 13

    private const val INVERSION_SQUARE_5 = 5
    private const val INVERSION_SQUARE_10 = 10
    private const val INVERSION_SQUARE_20 = 20
    private const val INVERSION_SQUARE_50 = 50
    private const val INVERSION_SQUARE_100 = 100

    private const val HALF_MASK = 0xFFFFFFFFUL

    private val MASK_51: ULong = (1UL shl LIMB_BITS) - 1UL

    val ZERO: ULongArray = ulongArrayOf(0UL, 0UL, 0UL, 0UL, 0UL)
    val ONE: ULongArray = ulongArrayOf(1UL, 0UL, 0UL, 0UL, 0UL)

    /**
     * Decodes a 32-byte little-endian representation into 5 radix-2^51 limbs.
     */
    fun decode(bytes: ByteArray): ULongArray {
        require(bytes.size == FIELD_ELEMENT_BYTES) { "Field element must be $FIELD_ELEMENT_BYTES bytes" }

        val limbs = ULongArray(LIMB_COUNT)

        // Read 8 bytes at a time as little-endian ULong values, then extract 51-bit limbs.
        // We need to read the 32 bytes as a stream of bits in little-endian order.
        fun loadLittleEndian64(offset: Int): ULong {
            var v = 0UL
            val end = minOf(offset + BYTES_PER_WORD, bytes.size)
            for (i in offset until end) {
                v = v or ((bytes[i].toUByte().toULong()) shl ((i - offset) * BITS_PER_BYTE))
            }
            return v
        }

        // limbs[0] = bits 0..50
        limbs[0] = loadLittleEndian64(LIMB0_BYTE_OFFSET) and MASK_51

        // limbs[1] = bits 51..101
        limbs[1] = (loadLittleEndian64(LIMB1_BYTE_OFFSET) shr LIMB1_BIT_SHIFT) and MASK_51

        // limbs[2] = bits 102..152
        limbs[2] = (loadLittleEndian64(LIMB2_BYTE_OFFSET) shr LIMB2_BIT_SHIFT) and MASK_51

        // limbs[3] = bits 153..203
        limbs[3] = (loadLittleEndian64(LIMB3_BYTE_OFFSET) shr LIMB3_BIT_SHIFT) and MASK_51

        // limbs[4] = bits 204..254
        limbs[4] = (loadLittleEndian64(LIMB4_BYTE_OFFSET) shr LIMB4_BIT_SHIFT) and MASK_51

        return limbs
    }

    /**
     * Encodes 5 radix-2^51 limbs into a 32-byte little-endian representation.
     */
    fun encode(limbs: ULongArray): ByteArray {
        require(limbs.size == LIMB_COUNT) { "Expected $LIMB_COUNT limbs" }

        // Fully reduce before encoding
        val r = reduceStrong(limbs)

        val out = ByteArray(FIELD_ELEMENT_BYTES)

        // Pack 255 bits into 32 bytes (little-endian)
        // bit position of limb i starts at i*51

        // Helper: write a ULong value into the output at a given bit offset
        // We reconstruct a 256-bit integer and write it out byte-by-byte
        // Simpler approach: combine limbs into a single 256-bit value stored as 4 ULongs (64-bit words)

        val w0 = r[0] or (r[1] shl ENCODE_SHIFT_01) // bits 0..101
        val w1 = (r[1] shr ENCODE_SHIFT_12) or (r[2] shl ENCODE_SHIFT_12_INV) // bits 64..165 (starts at bit 64)
        val w2 = (r[2] shr ENCODE_SHIFT_23) or (r[3] shl ENCODE_SHIFT_23_INV) // bits 128..228 (starts at bit 128)
        val w3 = (r[3] shr ENCODE_SHIFT_34) or (r[4] shl ENCODE_SHIFT_34_INV) // bits 192..255 (starts at bit 192)

        for (i in 0 until BYTES_PER_WORD) out[i] = (w0 shr (i * BITS_PER_BYTE)).toByte()
        for (i in 0 until BYTES_PER_WORD) out[i + BYTES_PER_WORD] = (w1 shr (i * BITS_PER_BYTE)).toByte()
        for (i in 0 until BYTES_PER_WORD) out[i + BYTES_PER_WORD * 2] = (w2 shr (i * BITS_PER_BYTE)).toByte()
        for (i in 0 until BYTES_PER_WORD) out[i + BYTES_PER_WORD * 3] = (w3 shr (i * BITS_PER_BYTE)).toByte()

        return out
    }

    /**
     * Field addition: (a + b) mod p, with weak reduction (carry propagation only).
     */
    fun add(
        a: ULongArray,
        b: ULongArray,
    ): ULongArray {
        val r = ULongArray(LIMB_COUNT)
        r[0] = a[0] + b[0]
        r[1] = a[1] + b[1]
        r[2] = a[2] + b[2]
        r[3] = a[3] + b[3]
        r[4] = a[4] + b[4]
        return reduceWeak(r)
    }

    /**
     * Field subtraction: (a - b) mod p.
     * Adds a multiple of p to ensure no underflow before subtracting.
     */
    fun sub(
        a: ULongArray,
        b: ULongArray,
    ): ULongArray {
        // Add 2*p to a to ensure no underflow.
        // p = 2^255 - 19
        // In limb form, p = (2^51 - 19, 2^51 - 1, 2^51 - 1, 2^51 - 1, 2^51 - 1)
        // so 2p limbs = (2^52 - 38, 2^52 - 2, 2^52 - 2, 2^52 - 2, 2^52 - 2)
        val r = ULongArray(LIMB_COUNT)
        val twoP0 = (MASK_51 + 1UL) * 2UL - TWO_P0_OFFSET // 2^52 - 38
        val twoPN = (MASK_51 + 1UL) * 2UL - TWO_PN_OFFSET // 2^52 - 2

        r[0] = a[0] + twoP0 - b[0]
        r[1] = a[1] + twoPN - b[1]
        r[2] = a[2] + twoPN - b[2]
        r[3] = a[3] + twoPN - b[3]
        r[4] = a[4] + twoPN - b[4]
        return reduceWeak(r)
    }

    /**
     * Field multiplication: (a * b) mod p using schoolbook 5x5 multiply with reduction.
     *
     * Uses the identity 2^255 = 19 (mod p) so that terms with index sum >= 5
     * are folded back with a factor of 19. Intermediate products are tracked as
     * 128-bit (hi, lo) pairs via [mul64] and [addU128] since limb products can
     * exceed 64 bits.
     */
    fun mul(
        a: ULongArray,
        b: ULongArray,
    ): ULongArray {
        // We compute a 10-coefficient schoolbook product, then reduce mod p.
        // Using 128-bit arithmetic via hi:lo pairs.

        val a0 = a[0]
        val a1 = a[1]
        val a2 = a[2]
        val a3 = a[3]
        val a4 = a[4]
        val b0 = b[0]
        val b1 = b[1]
        val b2 = b[2]
        val b3 = b[3]
        val b4 = b[4]

        // Pre-multiply by 19 for the folding terms
        val b1_19 = b1 * REDUCTION_CONSTANT
        val b2_19 = b2 * REDUCTION_CONSTANT
        val b3_19 = b3 * REDUCTION_CONSTANT
        val b4_19 = b4 * REDUCTION_CONSTANT

        // Each coefficient c[i] = sum of a[j]*b[k] where (j+k)%5 == i,
        // with terms where j+k >= 5 multiplied by 19.
        // c[0] = a0*b0 + 19*(a1*b4 + a2*b3 + a3*b2 + a4*b1)
        // c[1] = a0*b1 + a1*b0 + 19*(a2*b4 + a3*b3 + a4*b2)
        // c[2] = a0*b2 + a1*b1 + a2*b0 + 19*(a3*b4 + a4*b3)
        // c[3] = a0*b3 + a1*b2 + a2*b1 + a3*b0 + 19*(a4*b4)
        // c[4] = a0*b4 + a1*b3 + a2*b2 + a3*b1 + a4*b0

        // Since limbs are <= ~2^51 and 19*limb <= ~2^55, each product is <= ~2^106.
        // We need 128-bit arithmetic. We'll use a two-ULong accumulator.

        // Accumulate c[0]
        var lo: ULong
        var hi: ULong
        var cLo: ULong
        var cHi: ULong

        mul64(a0, b0).let {
            cLo = it.first
            cHi = it.second
        }
        mul64(a1, b4_19).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        mul64(a2, b3_19).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        mul64(a3, b2_19).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        mul64(a4, b1_19).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        val c0 = cLo and MASK_51
        // Carry: shift right by 51 (concatenate hi:lo, shift right 51)
        var carry = (cLo shr LIMB_BITS) or (cHi shl CARRY_SHIFT)

        // Accumulate c[1]
        mul64(a0, b1).let {
            cLo = it.first
            cHi = it.second
        }
        mul64(a1, b0).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        mul64(a2, b4_19).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        mul64(a3, b3_19).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        mul64(a4, b2_19).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        // Add carry from c[0]
        addU128(cHi, cLo, 0UL, carry).let {
            cHi = it.first
            cLo = it.second
        }
        val c1 = cLo and MASK_51
        carry = (cLo shr LIMB_BITS) or (cHi shl CARRY_SHIFT)

        // Accumulate c[2]
        mul64(a0, b2).let {
            cLo = it.first
            cHi = it.second
        }
        mul64(a1, b1).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        mul64(a2, b0).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        mul64(a3, b4_19).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        mul64(a4, b3_19).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        addU128(cHi, cLo, 0UL, carry).let {
            cHi = it.first
            cLo = it.second
        }
        val c2 = cLo and MASK_51
        carry = (cLo shr LIMB_BITS) or (cHi shl CARRY_SHIFT)

        // Accumulate c[3]
        mul64(a0, b3).let {
            cLo = it.first
            cHi = it.second
        }
        mul64(a1, b2).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        mul64(a2, b1).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        mul64(a3, b0).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        mul64(a4, b4_19).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        addU128(cHi, cLo, 0UL, carry).let {
            cHi = it.first
            cLo = it.second
        }
        val c3 = cLo and MASK_51
        carry = (cLo shr LIMB_BITS) or (cHi shl CARRY_SHIFT)

        // Accumulate c[4]
        mul64(a0, b4).let {
            cLo = it.first
            cHi = it.second
        }
        mul64(a1, b3).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        mul64(a2, b2).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        mul64(a3, b1).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        mul64(a4, b0).let {
            lo = it.first
            hi = it.second
        }
        addU128(cHi, cLo, hi, lo).let {
            cHi = it.first
            cLo = it.second
        }
        addU128(cHi, cLo, 0UL, carry).let {
            cHi = it.first
            cLo = it.second
        }
        val c4 = cLo and MASK_51
        carry = (cLo shr LIMB_BITS) or (cHi shl CARRY_SHIFT)

        // Final carry: fold carry * 19 back into c[0]
        val r0 = c0 + carry * REDUCTION_CONSTANT
        val extraCarry = r0 shr LIMB_BITS

        val result = ULongArray(LIMB_COUNT)
        result[0] = r0 and MASK_51
        result[1] = c1 + extraCarry
        result[2] = c2
        result[3] = c3
        result[4] = c4

        return result
    }

    /**
     * Field squaring: a^2 mod p. Delegates to mul for simplicity since this
     * is not performance-critical.
     */
    fun square(a: ULongArray): ULongArray = mul(a, a)

    /**
     * Modular inverse via Fermat's little theorem: a^(p-2) mod p.
     * p - 2 = 2^255 - 21.
     *
     * Uses an addition chain optimized for 2^255 - 21, following the approach
     * from the Curve25519 paper. This requires 254 squarings and 11 multiplications.
     */
    fun invert(a: ULongArray): ULongArray {
        // Compute a^(p-2) where p-2 = 2^255 - 21
        // Using the standard addition chain for Curve25519 field inversion

        val a2 = square(a) // a^2
        val a4 = square(a2) // a^4 (extra squaring)
        val a8 = square(a4) // a^8 (extra squaring)
        val a9 = mul(a8, a) // a^9
        val a11 = mul(a9, a2) // a^11
        val a_2_5_m1 =
            run {
                // a^(2^5 - 1) = a^31
                val x5 = square(a11) // a^22
                mul(x5, a9) // a^31
            }
        val a_2_10_m1 =
            run {
                // a^(2^10 - 1)
                var t = a_2_5_m1
                repeat(INVERSION_SQUARE_5) { t = square(t) } // a^(31 * 2^5) = a^(2^10 - 2^5)
                mul(t, a_2_5_m1) // a^(2^10 - 1)
            }
        val a_2_20_m1 =
            run {
                // a^(2^20 - 1)
                var t = a_2_10_m1
                repeat(INVERSION_SQUARE_10) { t = square(t) } // a^((2^10-1) * 2^10) = a^(2^20 - 2^10)
                mul(t, a_2_10_m1) // a^(2^20 - 1)
            }
        val a_2_40_m1 =
            run {
                // a^(2^40 - 1)
                var t = a_2_20_m1
                repeat(INVERSION_SQUARE_20) { t = square(t) }
                mul(t, a_2_20_m1)
            }
        val a_2_50_m1 =
            run {
                // a^(2^50 - 1)
                var t = a_2_40_m1
                repeat(INVERSION_SQUARE_10) { t = square(t) }
                mul(t, a_2_10_m1)
            }
        val a_2_100_m1 =
            run {
                // a^(2^100 - 1)
                var t = a_2_50_m1
                repeat(INVERSION_SQUARE_50) { t = square(t) }
                mul(t, a_2_50_m1)
            }
        val a_2_200_m1 =
            run {
                // a^(2^200 - 1)
                var t = a_2_100_m1
                repeat(INVERSION_SQUARE_100) { t = square(t) }
                mul(t, a_2_100_m1)
            }
        val a_2_250_m1 =
            run {
                // a^(2^250 - 1)
                var t = a_2_200_m1
                repeat(INVERSION_SQUARE_50) { t = square(t) }
                mul(t, a_2_50_m1)
            }

        // a^(2^255 - 21)
        // = a^(2^250 - 1) * a^(2^5) * ... we need:
        // 2^255 - 21 = (2^250 - 1) * 2^5 + (2^5 - 21) = (2^250 - 1) * 2^5 + 11
        // So: square 5 times, then multiply by a^11
        var t = a_2_250_m1
        repeat(INVERSION_SQUARE_5) { t = square(t) } // a^((2^250 - 1) * 2^5) = a^(2^255 - 2^5)
        return mul(t, a11) // a^(2^255 - 2^5 + 11) = a^(2^255 - 21) = a^(p-2)
    }

    /**
     * Weak reduction: propagate carries through limbs so each is < 2^52.
     * The result may still be >= p but each limb fits comfortably in a ULong.
     */
    private fun reduceWeak(limbs: ULongArray): ULongArray {
        val r = ULongArray(LIMB_COUNT)
        var carry: ULong

        carry = limbs[0] shr LIMB_BITS
        r[0] = limbs[0] and MASK_51
        val l1 = limbs[1] + carry

        carry = l1 shr LIMB_BITS
        r[1] = l1 and MASK_51
        val l2 = limbs[2] + carry

        carry = l2 shr LIMB_BITS
        r[2] = l2 and MASK_51
        val l3 = limbs[3] + carry

        carry = l3 shr LIMB_BITS
        r[3] = l3 and MASK_51
        val l4 = limbs[4] + carry

        carry = l4 shr LIMB_BITS
        r[4] = l4 and MASK_51

        // Fold the top carry back: carry * 19 added to limb 0 (since 2^255 = 19 mod p)
        r[0] = r[0] + carry * REDUCTION_CONSTANT
        // One more carry propagation from limb 0
        carry = r[0] shr LIMB_BITS
        r[0] = r[0] and MASK_51
        r[1] = r[1] + carry

        return r
    }

    /**
     * Strong reduction: ensures the result is fully canonical (in [0, p)).
     * Called before encoding to bytes.
     */
    private fun reduceStrong(limbs: ULongArray): ULongArray {
        // First do a weak reduction
        var r = reduceWeak(limbs)

        // Now r is weakly reduced. We need to check if r >= p and subtract p if so.
        // p in limb form: (2^51 - 19, 2^51 - 1, 2^51 - 1, 2^51 - 1, 2^51 - 1)

        // Try subtracting p: compute r - p. If the result doesn't borrow, use it.
        // We add 19 to limb 0 and see if carries propagate cleanly.
        val t = ULongArray(LIMB_COUNT)
        t[0] = r[0] + REDUCTION_CONSTANT

        var carry = t[0] shr LIMB_BITS
        t[0] = t[0] and MASK_51
        t[1] = r[1] + carry
        carry = t[1] shr LIMB_BITS
        t[1] = t[1] and MASK_51
        t[2] = r[2] + carry
        carry = t[2] shr LIMB_BITS
        t[2] = t[2] and MASK_51
        t[3] = r[3] + carry
        carry = t[3] shr LIMB_BITS
        t[3] = t[3] and MASK_51
        t[4] = r[4] + carry
        carry = t[4] shr LIMB_BITS
        t[4] = t[4] and MASK_51

        // If carry is nonzero, the value was >= p, so use t (which is r - p mod 2^255 + 19, i.e., r + 19 mod 2^255).
        // If carry is zero, the value was < p, keep r.
        // Use constant-time selection (not critical for this use case, but good practice)
        val useT = carry // 0 or 1
        val mask = 0UL - useT // 0x0000... or 0xFFFF...

        val result = ULongArray(LIMB_COUNT)
        result[0] = (t[0] and mask) or (r[0] and mask.inv())
        result[1] = (t[1] and mask) or (r[1] and mask.inv())
        result[2] = (t[2] and mask) or (r[2] and mask.inv())
        result[3] = (t[3] and mask) or (r[3] and mask.inv())
        result[4] = (t[4] and mask) or (r[4] and mask.inv())

        return result
    }

    /**
     * Multiply two ULong values and return the full 128-bit result as (lo, hi).
     * Uses the standard decomposition: split each 64-bit value into two 32-bit halves.
     */
    private fun mul64(
        a: ULong,
        b: ULong,
    ): Pair<ULong, ULong> {
        val aLo = a and HALF_MASK
        val aHi = a shr HALF_WORD_BITS
        val bLo = b and HALF_MASK
        val bHi = b shr HALF_WORD_BITS

        val ll = aLo * bLo
        val lh = aLo * bHi
        val hl = aHi * bLo
        val hh = aHi * bHi

        // Combine: result = hh << 64 + (lh + hl) << 32 + ll
        val mid = lh + hl
        val midCarry =
            if (mid < lh) {
                1UL
            } else {
                0UL
            } // carry from (lh + hl) overflow

        val lo = ll + (mid shl HALF_WORD_BITS)
        val loCarry =
            if (lo < ll) {
                1UL
            } else {
                0UL
            }

        val hi = hh + (mid shr HALF_WORD_BITS) + (midCarry shl HALF_WORD_BITS) + loCarry

        return Pair(lo, hi)
    }

    /**
     * Add two 128-bit values represented as (hi, lo) pairs.
     * Returns (resultHi, resultLo).
     */
    private fun addU128(
        aHi: ULong,
        aLo: ULong,
        bHi: ULong,
        bLo: ULong,
    ): Pair<ULong, ULong> {
        val lo = aLo + bLo
        val carry =
            if (lo < aLo) {
                1UL
            } else {
                0UL
            }
        val hi = aHi + bHi + carry
        return Pair(hi, lo)
    }
}
