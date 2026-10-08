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
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.math.abs

@JsExportCompat
class NumberLabel(
    value: Long,
) : CoseLabel<Long>(
        abs(value),
        if (value >= 0) {
            CDDL.uint
        } else {
            CDDL.nint
        },
        LabelType.Number
    ) {
    @JsName("fromInt")
    constructor(value: Int) : this(value.toLong())

    override fun toJsonSimple(): JsonElement = JsonPrimitive(toValue())

    override fun encode(builder: ByteStringBuilder) {
        return toCborItem().encode(builder)/*
        if (majorType == MajorType.NEGATIVE_INTEGER) {
            CborNInt(value).encode(builder)
        } else {
            CborUInt(value).encode(builder)
        }*/
    }

    override fun toString(): String = value.toString()

    override fun toCborItem(): CborItem<Long> =
        if (majorType == MajorType.NEGATIVE_INTEGER) {
            CborNInt(value)
        } else {
            CborUInt(value)
        }

    companion object {
        @JsStatic
        @JvmStatic
        fun fromCborItem(structure: CborItem<*>): NumberLabel {
            val generic = CoseLabel.fromCborItem(structure)
            require(generic is NumberLabel) { "Label passed in was not convertable a NumberLabel" }
            return generic
        }
    }
}
