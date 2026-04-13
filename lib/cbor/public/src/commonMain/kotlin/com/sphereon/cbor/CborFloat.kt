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

package com.sphereon.cbor

import com.sphereon.core.compat.JsExportCompat
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.experimental.or

@JsExportCompat
open class CborFloat(
    value: Float,
    cddl: CDDLType,
) : CborNumber<Float>(value, cddl) {
    override fun encode(builder: ByteStringBuilder) {
        builder.run {
            val majorTypeShifted = (majorType!!.type shl 5).toByte()
            append(majorTypeShifted.or(26))

            val raw = value.toRawBits()
            append((raw shr 24).and(0xff).toByte())
            append((raw shr 16).and(0xff).toByte())
            append((raw shr 8).and(0xff).toByte())
            append((raw shr 0).and(0xff).toByte())
        }
    }
}

@JsExportCompat
class CborFloat16(
    value: cddl_float16,
) : CborFloat(value, CDDL.float16)

@JsExportCompat
class CborFloat32(
    value: cddl_float32,
) : CborFloat(value, CDDL.float32)

@JsExportCompat
class CborDouble(
    value: cddl_float64,
) : CborItem<cddl_float64>(value, CDDL.float64) {
    override fun toJsonSimple(): JsonElement = JsonPrimitive(toValue())

    override fun encode(builder: ByteStringBuilder) =
        builder.run {
            val majorTypeShifted = (majorType!!.type shl 5).toByte()
            append(majorTypeShifted.or(27))

            val raw = value.toRawBits()
            append((raw shr 56).and(0xff).toByte())
            append((raw shr 48).and(0xff).toByte())
            append((raw shr 40).and(0xff).toByte())
            append((raw shr 32).and(0xff).toByte())
            append((raw shr 24).and(0xff).toByte())
            append((raw shr 16).and(0xff).toByte())
            append((raw shr 8).and(0xff).toByte())
            append((raw shr 0).and(0xff).toByte())
        }
}

fun cddl_float.toCborFloat() = CborFloat32(this)

fun cddl_float64.toCborFloat64() = CborDouble(this)
