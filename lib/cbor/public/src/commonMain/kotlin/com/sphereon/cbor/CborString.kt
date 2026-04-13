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
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.decodeFromBase64Url
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import com.sphereon.core.compat.JsExportCompat

@JsExportCompat
class CborString(value: cddl_tstr) : CborItem<cddl_tstr>(value, CDDL.tstr) {
    override fun encode(builder: ByteStringBuilder) {
        val encodedValue = value.encodeToByteArray()
        Cbor.encodeLength(builder, majorType!!, encodedValue.size)
        builder.append(value.encodeToByteArray())
    }

    companion object {
        internal fun decode(encodedCbor: ByteArray, offset: Int): Pair<Int, CborString> {
            val (payloadBegin, length) = Cbor.decodeLength(encodedCbor, offset)
            val payloadEnd = payloadBegin + length.toInt()
            val slice = encodedCbor.sliceArray(IntRange(payloadBegin, payloadEnd - 1))
            return Pair(payloadEnd, CborString(slice.decodeToString()))
        }
    }

    override fun toJsonSimple(): JsonElement {
        return JsonPrimitive(toValue())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        if (!super.equals(other)) return false
        return true
    }

    override fun hashCode(): Int {
        return super.value.hashCode()
    }

    override fun toString(): String {
        return "tstr(\"${value}\")"
    }


}

fun cddl_tstr.toCborString() = CborString(this)
fun Array<String>.toCborStringArray() = CborArray(this.map { it.toCborString() }.toMutableList())
fun CborArray<CborString>.toStringArray() = this.value.map { it.value }.toTypedArray()

private val HEX_ALPHABET = "0123456789abcdefABCDEF"
private const val BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
private const val BASE64_URL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_="

@OptIn(ExperimentalStdlibApi::class)
fun cddl_tstr.toCborByteString(encoding: Encoding? = null): CborByteString {
    if (encoding === Encoding.HEX || (encoding === null && this.length % 2 == 0)) {
        if (this.equals(this.filter { HEX_ALPHABET.contains(it) })) {
            return CborByteString(this.hexToByteArray())
        }
    }
    if ((encoding === Encoding.BASE64URL || encoding === Encoding.BASE64 ||
                (encoding === null &&
                        (this == this.filter { BASE64_CHARS.contains(it) } ||
                                this == this.filter { BASE64_URL_CHARS.contains(it) })))
    ) {
        if (encoding === Encoding.BASE64URL || this.contains("-") || this.contains("_")) {
            return CborByteString(this.decodeFromBase64Url())
        }
        // Base64
        return CborByteString(this.decodeFromBase64())
    }
    // UTF-8
    return CborByteString(this.decodeFrom(Encoding.UTF8))
}
