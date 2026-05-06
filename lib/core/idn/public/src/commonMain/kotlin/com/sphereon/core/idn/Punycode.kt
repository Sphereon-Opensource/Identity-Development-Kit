/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.core.idn

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError

/**
 * RFC 3492 Punycode encoder and decoder.
 *
 * Operates on a single label (no dot characters). The IDNA wrapper in [Idna]
 * handles label splitting, NFC normalization, and the `xn--` prefix.
 *
 * Reference: https://www.rfc-editor.org/rfc/rfc3492
 */
object Punycode {
    private const val BASE = 36
    private const val TMIN = 1
    private const val TMAX = 26
    private const val SKEW = 38
    private const val DAMP = 700
    private const val INITIAL_BIAS = 72
    private const val INITIAL_N = 0x80
    private const val DELIMITER = '-'.code
    private const val LETTER_BASE = 26
    private const val ASCII_CUTOFF = 0x80
    private const val DECODE_OVERFLOW_MSG = "Punycode decode overflow"
    private const val ENCODE_OVERFLOW_MSG = "Punycode encode overflow"

    /**
     * Encodes a single label of Unicode code points to ASCII Punycode.
     *
     * The result does NOT include the `xn--` ACE prefix; that is added at the
     * IDNA layer.
     */
    fun encode(input: String): IdkResult<String, IdkError> {
        val codePoints = input.toCodePoints()
        val output = StringBuilder()

        for (cp in codePoints) {
            if (cp < ASCII_CUTOFF) {
                output.append(cp.toChar())
            }
        }
        val basicCount = output.length
        if (basicCount > 0) {
            output.append(DELIMITER.toChar())
        }

        var state = EncodeState(handled = basicCount, n = INITIAL_N, delta = 0, bias = INITIAL_BIAS)
        while (state.handled < codePoints.size) {
            val nextN = nextNonBasicCodePoint(codePoints, state.n)
            val advance = (nextN - state.n) * (state.handled + 1)
            if (advance > Int.MAX_VALUE - state.delta) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = ENCODE_OVERFLOW_MSG))
            }
            state = state.copy(delta = state.delta + advance, n = nextN)

            val emitted =
                emitBatch(state, codePoints, output, basicCount)
                    ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = ENCODE_OVERFLOW_MSG))
            state = emitted.copy(delta = emitted.delta + 1, n = emitted.n + 1)
        }
        return Ok(output.toString())
    }

    /**
     * Decodes a single ASCII Punycode label back to Unicode code points.
     *
     * The input must NOT include the `xn--` ACE prefix.
     */
    fun decode(input: String): IdkResult<String, IdkError> {
        val output = mutableListOf<Int>()
        var state = DecodeState(n = INITIAL_N, i = 0, bias = INITIAL_BIAS)

        val lastDelim = input.lastIndexOf(DELIMITER.toChar())
        if (lastDelim > 0) {
            for (idx in 0..<lastDelim) {
                val ch = input[idx].code
                if (ch >= ASCII_CUTOFF) {
                    return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Punycode: non-basic code point in basic prefix"))
                }
                output.add(ch)
            }
        }

        var pos =
            if (lastDelim >= 0) {
                lastDelim + 1
            } else {
                0
            }
        while (pos < input.length) {
            val oldi = state.i
            val advance =
                readDelta(input, pos, state.bias)
                    ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = DECODE_OVERFLOW_MSG))
            pos = advance.nextPos
            if (state.i > Int.MAX_VALUE - advance.iIncrement) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = DECODE_OVERFLOW_MSG))
            }
            val newI = state.i + advance.iIncrement
            val outLen = output.size + 1
            val newBias = adapt(newI - oldi, outLen, oldi == 0)
            if (newI / outLen > Int.MAX_VALUE - state.n) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = DECODE_OVERFLOW_MSG))
            }
            val nextN = state.n + (newI / outLen)
            val insertedAt = newI % outLen
            output.add(insertedAt, nextN)
            state = DecodeState(n = nextN, i = insertedAt + 1, bias = newBias)
        }
        return Ok(output.codePointsToString())
    }

    private fun nextNonBasicCodePoint(
        codePoints: IntArray,
        lower: Int
    ): Int {
        var m = Int.MAX_VALUE
        for (cp in codePoints) {
            if (cp in lower..<m) {
                m = cp
            }
        }
        return m
    }

    /**
     * Inner per-codepoint loop of the spec's main encoding loop. Returns
     * the new state when successful, or null when an arithmetic overflow
     * is detected so the caller can surface it as an [IdkError].
     */
    private fun emitBatch(
        initial: EncodeState,
        codePoints: IntArray,
        output: StringBuilder,
        basicCount: Int,
    ): EncodeState? {
        var state = initial
        for (cp in codePoints) {
            if (cp < state.n) {
                if (state.delta == Int.MAX_VALUE) {
                    return null
                }
                state = state.copy(delta = state.delta + 1)
            }
            if (cp == state.n) {
                emitVariableLengthInteger(state.delta, state.bias, output)
                val newBias = adapt(state.delta, state.handled + 1, state.handled == basicCount)
                state = state.copy(delta = 0, bias = newBias, handled = state.handled + 1)
            }
        }
        return state
    }

    private fun emitVariableLengthInteger(
        delta: Int,
        bias: Int,
        output: StringBuilder
    ) {
        var q = delta
        var k = BASE
        while (true) {
            val t = thresholdAt(k, bias)
            if (q < t) {
                break
            }
            output.append(digitToCodePoint(t + ((q - t) % (BASE - t))))
            q = (q - t) / (BASE - t)
            k += BASE
        }
        output.append(digitToCodePoint(q))
    }

    private fun readDelta(
        input: String,
        startPos: Int,
        bias: Int
    ): DeltaRead? {
        var pos = startPos
        var w = 1
        var k = BASE
        var iIncrement = 0
        while (true) {
            if (pos >= input.length) {
                return null
            }
            val digit = digitFromCodePoint(input[pos].code) ?: return null
            pos++
            if (digit > (Int.MAX_VALUE - iIncrement) / w) {
                return null
            }
            iIncrement += digit * w
            val t = thresholdAt(k, bias)
            if (digit < t) {
                break
            }
            if (w > Int.MAX_VALUE / (BASE - t)) {
                return null
            }
            w *= (BASE - t)
            k += BASE
        }
        return DeltaRead(nextPos = pos, iIncrement = iIncrement)
    }

    private fun thresholdAt(
        k: Int,
        bias: Int
    ): Int =
        when {
            k <= bias + TMIN -> TMIN
            k >= bias + TMAX -> TMAX
            else -> k - bias
        }

    private fun adapt(
        deltaIn: Int,
        numPoints: Int,
        firstTime: Boolean
    ): Int {
        var delta =
            if (firstTime) {
                deltaIn / DAMP
            } else {
                deltaIn / 2
            }
        delta += delta / numPoints
        var k = 0
        while (delta > ((BASE - TMIN) * TMAX) / 2) {
            delta /= (BASE - TMIN)
            k += BASE
        }
        return k + ((BASE - TMIN + 1) * delta) / (delta + SKEW)
    }

    private fun digitToCodePoint(d: Int): Char =
        if (d < LETTER_BASE) {
            ('a'.code + d).toChar()
        } else {
            ('0'.code + d - LETTER_BASE).toChar()
        }

    private fun digitFromCodePoint(cp: Int): Int? =
        when (cp) {
            in 'A'.code..'Z'.code -> cp - 'A'.code
            in 'a'.code..'z'.code -> cp - 'a'.code
            in '0'.code..'9'.code -> cp - '0'.code + LETTER_BASE
            else -> null
        }

    private data class EncodeState(
        val handled: Int,
        val n: Int,
        val delta: Int,
        val bias: Int
    )

    private data class DecodeState(
        val n: Int,
        val i: Int,
        val bias: Int
    )

    private data class DeltaRead(
        val nextPos: Int,
        val iIncrement: Int
    )
}

private const val SURROGATE_HIGH_START = 0xD800
private const val SURROGATE_HIGH_END = 0xDBFF
private const val SURROGATE_LOW_START = 0xDC00
private const val SURROGATE_LOW_END = 0xDFFF
private const val SUPPLEMENTARY_PLANE_START = 0x10000
private const val BMP_END = 0xFFFF
private const val SURROGATE_SHIFT = 10
private const val SURROGATE_LOW_MASK = 0x3FF

/**
 * UTF-16 string to Unicode code-point array, handling surrogate pairs.
 */
internal fun String.toCodePoints(): IntArray {
    val result = IntArray(length)
    var idx = 0
    var i = 0
    while (i < length) {
        val high = this[i].code
        val cp =
            if (high in SURROGATE_HIGH_START..SURROGATE_HIGH_END && i + 1 < length) {
                val low = this[i + 1].code
                if (low in SURROGATE_LOW_START..SURROGATE_LOW_END) {
                    i++
                    SUPPLEMENTARY_PLANE_START + ((high - SURROGATE_HIGH_START) shl SURROGATE_SHIFT) + (low - SURROGATE_LOW_START)
                } else {
                    high
                }
            } else {
                high
            }
        result[idx++] = cp
        i++
    }
    return result.copyOf(idx)
}

/**
 * Code-point list back to UTF-16 string, encoding code points beyond the BMP
 * as surrogate pairs.
 */
internal fun List<Int>.codePointsToString(): String {
    val sb = StringBuilder(size)
    for (cp in this) {
        if (cp <= BMP_END) {
            sb.append(cp.toChar())
        } else {
            val adjusted = cp - SUPPLEMENTARY_PLANE_START
            sb.append((SURROGATE_HIGH_START + (adjusted shr SURROGATE_SHIFT)).toChar())
            sb.append((SURROGATE_LOW_START + (adjusted and SURROGATE_LOW_MASK)).toChar())
        }
    }
    return sb.toString()
}
