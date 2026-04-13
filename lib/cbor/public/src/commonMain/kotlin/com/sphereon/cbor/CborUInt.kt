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

import kotlinx.io.bytestring.ByteStringBuilder
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName

@JsExportCompat
open class CborUInt(value: Long) : AbstractCborInt<cddl_uint>(value, CDDL.uint) {
    @JsName("fromNumber")
    constructor(value: Number) : this(value.toLong())

    override fun validate() {
        if (value < 0) {
//            throw IllegalArgumentException("Negative number ${value} not allowed for Cbor uint type")
        }
    }

    override fun encode(builder: ByteStringBuilder) {
        Cbor.encodeLength(builder, majorType!!, value.toULong())
    }

    companion object {
        internal fun decode(encodedCbor: ByteArray, offset: Int): Pair<Int, CborUInt> {
            val (newOffset, value) = Cbor.decodeLength(encodedCbor, offset)
            return Pair(newOffset, CborUInt(value.toLong()))
        }
    }
}

fun CborUInt.toUInt() = value.toUInt()
fun UInt.toCborUIntFromUint() = CborUInt(this.toLong())
fun cddl_uint.toCborUInt() = CborUInt(this)
