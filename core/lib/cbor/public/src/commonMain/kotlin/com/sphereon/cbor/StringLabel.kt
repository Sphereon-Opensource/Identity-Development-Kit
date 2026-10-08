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
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

@JsExportCompat
class StringLabel(
    value: cddl_tstr,
) : CoseLabel<cddl_tstr>(value, CDDL.tstr, LabelType.String) {
    override fun toJsonSimple(): JsonElement = JsonPrimitive(toValue())

    override fun encode(builder: ByteStringBuilder) {
        val encodedValue = value.encodeToByteArray()
        cborEncodeLength(builder, majorType!!, encodedValue.size)
        builder.append(value.encodeToByteArray())
    }

    override fun toString(): String = value

    override fun toCborItem(): CborItem<cddl_tstr> = CborString(value)

    companion object {
        @JsStatic
        @JvmStatic
        fun fromCborItem(structure: CborItem<*>): StringLabel {
            val generic = CoseLabel.fromCborItem(structure)
            require(generic is StringLabel) { "Label passed in was not convertable a StringLabel" }
            return generic
        }
    }
}
