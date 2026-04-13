/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.cbor

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic

@JsExportCompat
open class CborByteString(value: cddl_bstr) :
    CborItem<cddl_bstr>(value, CDDL.bstr) {

    override fun toJsonSimple(): JsonElement {
        return JsonPrimitive(encodeValueTo(Encoding.BASE64URL))
    }

    override fun encode(builder: ByteStringBuilder) {
        Cbor.encodeLength(builder, majorType!!, value.size)
        builder.append(value)
    }

    fun <T : CborItem<*>> cborDecodeValue() = cborSerializer.decode<T>(value)

    fun encodeValueTo(encoding: Encoding): String = this.value.encodeTo(encoding)


    override fun equals(other: Any?): Boolean = other is CborByteString && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String {
        val sb = StringBuilder("bstr(")
        for (b in value) {
            sb.append(HEX_DIGITS[b.toInt().and(0xff) shr 4])
            sb.append(HEX_DIGITS[b.toInt().and(0x0f)])
        }
        sb.append(")")
        return sb.toString()
    }

    companion object {
        @JsStatic
        fun fromCborItem(value: CborItem<*>) = CborByteString(cborSerializer.encode(value))

        internal fun decode(encodedCbor: ByteArray, offset: Int): Pair<Int, CborByteString> {
            val (payloadBegin, length) = Cbor.decodeLength(encodedCbor, offset)
            val payloadEnd = payloadBegin + length.toInt()
            val slice = encodedCbor.sliceArray(IntRange(payloadBegin, payloadEnd - 1))
            return Pair(payloadEnd, CborByteString(slice))
        }
    }


}
fun cddl_bstr.toCborByteString() = CborByteString(this)
class CborByteStringIndefLength(value: List<cddl_bstr>) : CborItem<List<cddl_bstr>>(value, CDDL.bstr_indef_length) {
    override fun toJsonSimple(): JsonElement {
        TODO("Cbor bytestring indef length to JSON not implemented yet")
    }
    override fun encode(builder: ByteStringBuilder) {
        val majorTypeShifted = (majorType!!.type shl 5)
        builder.append((majorTypeShifted + 31).toByte())
        value.forEach {
            Cbor.encodeLength(builder, majorType, it.size)
            builder.append(it)
        }
        builder.append(0xff.toByte())
    }

    companion object {
        internal fun decode(encodedCbor: ByteArray, offset: Int): Pair<Int, CborByteStringIndefLength> {
            val majorTypeShifted = (MajorType.BYTE_STRING.type shl 5)
            val marker = (majorTypeShifted + 31).toByte()
            check(encodedCbor[offset] == marker)
            val chunks = mutableListOf<ByteArray>()
            var cursor = offset + 1
            while (true) {
                if (encodedCbor[cursor].toInt().and(0xff) == 0xff) {
                    // BREAK code, we're done
                    cursor += 1
                    break
                }
                val (chunkEndOffset, chunk) = Cbor.decode(encodedCbor, cursor)
                check(chunk is CborByteString)
                chunks.add(chunk.value)
                check(chunkEndOffset > cursor)
                cursor = chunkEndOffset
            }
            return Pair(cursor, CborByteStringIndefLength(chunks))
        }
    }
}

fun CborArray<CborByteString>.encodeToHexArray() = this.value.map { it.encodeValueTo(Encoding.HEX) }.toTypedArray()
fun CborArray<CborByteString>.encodeToBase64Array(urlSafe: Boolean = false) =
    this.value.map { if (urlSafe) it.encodeValueTo(Encoding.BASE64URL) else it.encodeValueTo(Encoding.BASE64) }.toTypedArray()

fun CborArray<CborByteString>.encodeToBase64UrlArray() =
    this.value.map { it.encodeValueTo(Encoding.BASE64URL) }.toTypedArray()

fun CborArray<CborByteString>.encodeToArray(encoding: Encoding) =
    this.value.map { it.encodeValueTo(encoding) }.toTypedArray()

fun Array<String>.encodeToCborByteArray(encoding: Encoding? = null) = CborArray(this.map { it.toCborByteString(encoding) }.toMutableList())

