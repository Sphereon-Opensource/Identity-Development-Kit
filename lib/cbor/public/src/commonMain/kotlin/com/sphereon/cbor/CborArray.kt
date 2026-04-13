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
import kotlinx.serialization.json.JsonArray
import com.sphereon.util.stringify
import com.sphereon.util.getUInt8
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic

@Suppress("UNCHECKED_CAST")
@JsExportCompat
class CborArray<V : CborItem<*>>(value: cddl_list<V> = mutableListOf(), val indefiniteLength: Boolean = false) :
    CborCollectionItem<cddl_list<V>>(value, CDDL.list) {




    fun <T> required(idx: Int): T {
        if (idx > this.value.size) {
            throw IllegalArgumentException("Index $idx out of bounds")
        }
        return value[idx] as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> optional(idx: Int): T? {
        val item = value.getOrNull(idx) ?: return null
        if (item is CborNil || item is CborNull) return null
        return item as T
    }

    override fun toJsonSimple(): JsonArray {
        return JsonArray(value.map { it.toJsonSimple() })
    }

    override fun toJsonWithCDDL(): JsonArray {
        return JsonArray(value.map { it.toJsonWithCDDL() })
    }

    override fun toJsonCborItem(): ICborItemValueJson {
        return object : ICborItemValueJson {
            override val cddl = this@CborArray.cddl
            override val value = this@CborArray.toJsonWithCDDL()
        }
    }

    override fun encode(builder: ByteStringBuilder) {
        if (indefiniteLength) {
            val majorTypeShifted = (majorType!!.type shl 5)
            builder.append((majorTypeShifted + 31).toByte())
            value.forEach { (it as CborItem<V>).encode(builder) }
            builder.append(0xff.toByte())
        } else {
            Cbor.encodeLength(builder, majorType!!, value.size)
            value.forEach { (it as CborItem<V>).encode(builder) }
        }
    }

    override fun toString(): String {
        return "CborArray(value=${stringify(value)}, indefiniteLength=$indefiniteLength)"
    }


    companion object {
        /**
         * Creates a new builder.
         *
         * @return an [ArrayBuilder], call [ArrayBuilder.end] when done adding items to get a
         * [CborBuilder].
         * @deprecated Use the DSL builder instead: `cborArray { +"item" }`
         * @see com.sphereon.cbor.dsl.cborArray
         */
        @JsStatic
        @Deprecated(
            message = "Use cborArray DSL instead",
            replaceWith = ReplaceWith("cborArray { }", "com.sphereon.cbor.dsl.cborArray")
        )
        fun <T: Any?> builder(subject: T? = null): ArrayBuilder<CborBuilder<T>> {
            @Suppress("DEPRECATION")
            val dataItem = CborArray(mutableListOf())
            return ArrayBuilder(CborBuilder(dataItem, subject), dataItem)
        }

        @JsStatic
        @Deprecated(
            message = "Use cborArray DSL instead",
            replaceWith = ReplaceWith("cborArray { }", "com.sphereon.cbor.dsl.cborArray")
        )
        fun simpleBuilder(): ArrayBuilder<SimpleCborBuilder> {
            @Suppress("DEPRECATION")
            val dataItem = CborArray(mutableListOf())
            return ArrayBuilder(SimpleCborBuilder(dataItem), dataItem)
        }

        internal fun decode(encodedCbor: ByteArray, offset: Int): Pair<Int, CborArray<CborItem<*>>> {
            val lowBits = encodedCbor[offset].toInt().and(0x1f)
            if (lowBits == 31) {
                // indefinite length
                var cursor = offset + 1
                val items = mutableListOf<CborItem<*>>()
                while (true) {
                    if (encodedCbor.getUInt8(cursor) == Cbor.BREAK) {
                        // BREAK code, we're done
                        cursor += 1
                        break
                    }
                    val (nextItemOffset, item) = Cbor.decode(encodedCbor, cursor)
                    items.add(item)
                    check(nextItemOffset > cursor)
                    cursor = nextItemOffset
                }
                return Pair(cursor, CborArray(items, true))
            } else {
                var (cursor, numItems) = Cbor.decodeLength(encodedCbor, offset)
                val items = mutableListOf<CborItem<*>>()
                if (numItems == 0UL) {
                    return Pair(cursor, CborArray(mutableListOf()))
                }
                for (n in IntRange(0, numItems.toInt() - 1)) {
                    val (nextItemOffset, item) = Cbor.decode(encodedCbor, cursor)
                    items.add(item)
                    check(nextItemOffset > cursor)
                    cursor = nextItemOffset
                }
                return Pair(cursor, CborArray(items))
            }
        }
    }
}

fun Array<out CborStructure<*, *>>.cborViewArrayToCborItem() =
    if (this.isEmpty()) null else CborArray(this.map { it.toCborStructure() as CborItem<*> }.toMutableList())

fun List<CborStructure<*, *>>.cborViewListToCborItem() =
    if (this.isEmpty()) null else CborArray(this.map { it.toCborStructure() as CborItem<*> }.toMutableList())

@OptIn(ExperimentalContracts::class)
fun buildCborArray(
    builderAction: ArrayBuilder<SimpleCborBuilder>.() -> Unit
): CborItem<*> {
    contract { callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE) }
    val builder = CborArray.simpleBuilder()
    builder.builderAction()
    return builder.end().build()
}