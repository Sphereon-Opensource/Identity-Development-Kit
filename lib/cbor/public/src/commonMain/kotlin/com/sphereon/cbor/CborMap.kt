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

import com.sphereon.cbor.CborConst.CDDL_LITERAL
import com.sphereon.cbor.CborConst.VALUE_LITERAL
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.util.stringify
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.jvm.JvmOverloads

fun <T> Map<*, *>.getStringLabel(
    key: Any,
    required: Boolean? = false,
): T {
    val value = this[key]
    require(!(required == true && value == null)) { "Value is null for required label $key" }
    return value as T
}

fun <T> Map<*, *>.getNumberLabel(
    key: cddl_uint,
    required: Boolean? = false,
): T {
    val value = this[key.toInt()]
    require(!(required == true && value == null)) { "Value is null for required label $key" }
    return value as T
}

@Suppress("UNCHECKED_CAST")
open class CborMap<K : CborItem<*>, V : CborItem<*>?>
    @JvmOverloads
    constructor(
        value: MutableMap<K, V> = mutableMapOf(),
        val indefiniteLength: Boolean = false,
    ) : CborCollectionItem<MutableMap<K, V>>(value, CDDL.map) {
        operator fun <T> get(key: K): T = value[key] as T

        override fun toJsonSimple(): JsonObject {
//        println("jsonSimple Map:")
            return JsonObject(
                value.entries.associate {
                    val key =
                        it.key
                            .toJsonSimple()
                            .jsonPrimitive.content
//            println(" =simple= key: ${key}")
                    require(it.value is CborItem<*>) { "Map must contain cbor values" }
                    val value = (it.value as CborItem<Any>).toJsonSimple()
//            println("      =simple= key: ${key}, value: ${value}")
                    Pair(
                        key,
                        value,
                    )
                },
            )
        }

        override fun toJsonWithCDDL(): JsonArray {
//        println("==Array:")
            return JsonArray(
                value.entries.map {
                    require(it.value is CborItem<*>) { "Map must contain cbor values" }
                    val json = (it.value as CborItem<Any>).toJsonWithCDDL()
                    val arrayElement = json as? JsonArray
                    val isArray = arrayElement !== null
                    val objectElement = json as? JsonObject
                    val isObject = objectElement !== null
                    val primitiveElement = json as? JsonPrimitive
                    val isPrimitive = primitiveElement !== null
                    val key = it.key.toJsonSimple().jsonPrimitive
                    val cddl =
                        if (isArray) {
                            JsonPrimitive(CDDL.list.format)
                        } else if (isPrimitive) {
                            JsonPrimitive(
                                it.value?.cddl?.format ?: CDDL.nil.format,
                            )
                        } else {
                            json.jsonObject[CDDL_LITERAL]!!
                        }

                    val value = primitiveElement ?: arrayElement ?: json.jsonObject[VALUE_LITERAL]!!
//                println("   ==Object {cddl(${cddl}), value($value)}")
                    JsonObject(
                        mapOf(
                            "key" to key,
                            CDDL_LITERAL to cddl,
                            VALUE_LITERAL to value,
                        ),
                    )
                },
            )
        }

        fun toJsonWithCDDLObject(): JsonObject {
//        println("==Object:")
            return JsonObject(
                mapOf(
                    *value.entries
                        .map {
                            require(it.value is CborItem<*>) { "Map must contain cbor values" }
                            val json = (it.value as CborItem<Any>).toJsonWithCDDL()
                            val arrayElement = json as? JsonArray
                            val isArray = arrayElement !== null
                            val objectElement = json as? JsonObject
                            val isObject = objectElement !== null
                            val primitiveElement = json as? JsonPrimitive
                            val isPrimitive = primitiveElement !== null

                            val key =
                                it.key
                                    .toJsonSimple()
                                    .jsonPrimitive.content
                            val cddl =
                                if (isArray) {
                                    JsonPrimitive(CDDL.list.format)
                                } else if (isPrimitive) {
                                    JsonPrimitive(
                                        it.value?.cddl?.format ?: CDDL.nil.format,
                                    )
                                } else {
                                    json.jsonObject[CDDL_LITERAL]!!
                                }

                            val value = primitiveElement ?: arrayElement ?: json.jsonObject[VALUE_LITERAL]!!
//                println("key: ${it.key.toJsonSimple().jsonPrimitive.content}\r\n           => OBJECT {cddl(${cddl}), value($value)}")
                            Pair(
                                key,
                                JsonObject(
                                    mapOf(
                                        CDDL_LITERAL to cddl,
//                            Pair("key", it.key.toJsonSimple().jsonPrimitive),
                                        VALUE_LITERAL to value,
                                    ),
                                ),
                            )
                        }.toTypedArray(),
                ),
            )
        }

        override fun toJsonCborItem(): ICborItemValueJson =
            object : ICborItemValueJson {
                override val cddl = CDDL.map
                override val value = this@CborMap.toJsonWithCDDL()
            }

        fun <T> getStringLabel(
            key: cddl_tstr,
            required: Boolean? = false,
        ): T {
            val value = value[StringLabel(key) as K]
            require(!(required == true && value == null)) { "Value is null for required label $key" }
            return value as T
        }

        fun <T> getNumberLabel(
            key: cddl_uint,
            required: Boolean? = false,
        ): T {
            val value = value[NumberLabel(key.toInt()) as K]
            require(!(required == true && value == null)) { "Value is null for required label $key" }
            return value as T
        }

        override fun encode(builder: ByteStringBuilder) {
            if (indefiniteLength) {
                val majorTypeShifted = (majorType!!.type shl 5)
                builder.append((majorTypeShifted + 31).toByte())
                for ((keyItem, valueItem) in value) {
                    keyItem.encode(builder)
                    valueItem?.encode(builder)
                }
                builder.append(0xff.toByte())
            } else {
                cborEncodeLength(builder, majorType!!, value.size)
                for ((keyItem, valueItem) in value) {
                    keyItem.encode(builder)
                    valueItem?.encode(builder)
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is CborMap<*, *>) {
                return false
            }
            if (!super.equals(other)) {
                return false
            }

            if (indefiniteLength != other.indefiniteLength) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + indefiniteLength.hashCode()
            return result
        }

        override fun toString(): String = "CborMap(value=${stringify(value)}, indefiniteLength=$indefiniteLength)"
    }
