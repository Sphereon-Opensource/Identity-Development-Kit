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
import com.sphereon.util.stringify
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.serialization.json.JsonArray
import kotlin.jvm.JvmOverloads

@Suppress("UNCHECKED_CAST")
@JsExportCompat
class CborArray<V : CborItem<*>>
    @JvmOverloads
    constructor(
        value: cddl_list<V> = mutableListOf(),
        val indefiniteLength: Boolean = false,
    ) : CborCollectionItem<cddl_list<V>>(value, CDDL.list) {
        fun <T> required(idx: Int): T {
            require(idx <= this.value.size) { "Index $idx out of bounds" }
            return value[idx] as T
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> optional(idx: Int): T? {
            val item = value.getOrNull(idx) ?: return null
            if (item is CborNil || item is CborNull) {
                return null
            }
            return item as T
        }

        override fun toJsonSimple(): JsonArray = JsonArray(value.map { it.toJsonSimple() })

        override fun toJsonWithCDDL(): JsonArray = JsonArray(value.map { it.toJsonWithCDDL() })

        override fun toJsonCborItem(): ICborItemValueJson =
            object : ICborItemValueJson {
                override val cddl = this@CborArray.cddl
                override val value = this@CborArray.toJsonWithCDDL()
            }

        override fun encode(builder: ByteStringBuilder) {
            if (indefiniteLength) {
                val majorTypeShifted = (majorType!!.type shl 5)
                builder.append((majorTypeShifted + 31).toByte())
                value.forEach { (it as CborItem<V>).encode(builder) }
                builder.append(0xff.toByte())
            } else {
                cborEncodeLength(builder, majorType!!, value.size)
                value.forEach { (it as CborItem<V>).encode(builder) }
            }
        }

        override fun toString(): String = "CborArray(value=${stringify(value)}, indefiniteLength=$indefiniteLength)"
    }
