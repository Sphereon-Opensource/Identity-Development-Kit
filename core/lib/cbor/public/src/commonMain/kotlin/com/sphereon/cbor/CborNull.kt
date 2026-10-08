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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlin.js.JsStatic

class CborStringIndefLength(
    value: List<cddl_tstr>,
) : CborItem<List<cddl_tstr>>(value, CDDL.tstr_indef_length) {
    override fun encode(builder: ByteStringBuilder) {
        val majorTypeShifted = (majorType!!.type shl 5)
        builder.append((majorTypeShifted + 31).toByte())
        value.forEach {
            val encodedStr = it.encodeToByteArray()
            cborEncodeLength(builder, majorType, encodedStr.size)
            builder.append(encodedStr)
        }
        builder.append(0xff.toByte())
    }

    override fun toJsonSimple(): JsonArray {
        TODO("Indef lengt to json not implemented yet")
    }
}

@JsExportCompat
class CborNil : CborSimple<cddl_nil>(null, CDDL.nil) {
    init {
        require(value == null) { "Nil requires value ${null}" }
    }

    override fun toJsonSimple(): JsonElement = JsonNull
}

@JsExportCompat
class CborNull : CborSimple<cddl_null>(null, CDDL.Null) {
    init {
        require(value == null) { "Nil requires value ${null}" }
    }

    override fun toJsonSimple(): JsonElement = JsonNull
}
