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
import com.sphereon.cbor.CborConst.KEY_LITERAL
import com.sphereon.cbor.CborConst.VALUE_LITERAL
import com.sphereon.core.compat.JsExportCompat
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.native.ObjCName

/**
 * We use closed polymorphism here (sealed)
 */
@JsExportCompat
sealed class CborItem<Type>(
    val value: Type,
    cddl: CDDLType,
) : CborBaseItem(cddl),
    HasCborJsonRepresentation {
    protected val HEX_DIGITS = "0123456789abcdef".toCharArray()

    open val majorType = cddl.majorType
    val info = cddl.info

    val asStr: cddl_tstr
        get() {
            require(this is CborString)
            return value
        }

    val asBool: cddl_bool
        get() {
            require(this is CborBool)
            return value
        }

    val asBstr: cddl_bstr
        get() {
            require(this is CborByteString)
            return value
        }

    val asLong: Long
        get() {
            return when (this) {
                is CborNInt -> -value.toLong()
                is CborUInt -> value.toLong()
                else -> throw IllegalArgumentException("Value $value ($cddl) is not long")
            }
        }

    val asInt: Int
        get() {
            return when (this) {
                is CborNInt -> {
                    if (value.toLong() > Int.MAX_VALUE) {
                        throw IllegalArgumentException("Unsigned value does not fit in Int field")
                    } else {
                        -value.toInt()
                    }
                }

                is CborUInt -> {
                    if (value.toLong() > Int.MAX_VALUE) {
                        throw IllegalArgumentException("Unsigned value does not fit in Int field")
                    } else {
                        value.toInt()
                    }
                }

                else -> {
                    throw IllegalArgumentException("Value $value ($cddl) is not int")
                }
            }
        }

    @Suppress("UNCHECKED_CAST")
    val asMap: cddl_map<Any, Any>
        get() {
            require(this is CborMap<*, *>)
            return this.value as cddl_map<Any, Any>
        }

    @Suppress("UNCHECKED_CAST")
    val asList: cddl_list<Any>
        get() {
            require(this is CborArray<*>)
            return this.value as cddl_list<Any>
        }

    /**
     * The value of a [Tagged] data item.
     *
     * @throws IllegalArgumentException if not the data item isn't of type [Tagged].
     */
    val asTaggedSubject: CborItem<Type>
        get() {
            require(this is CborTagged<Type>)
            return this.taggedItem
        }

    init {
        this.validate()
    }

    internal abstract fun encode(builder: ByteStringBuilder)

    fun encodeCbor(): ByteArray {
        val builder = ByteStringBuilder()
        encode(builder)
        return builder.toByteString().toByteArray()
    }

    fun toBstr(): CborByteString {
        val builder = ByteStringBuilder()
        encode(builder)
        return builder.toByteString().toByteArray().toCborByteString()
    }

    fun toValue(): Type = value

    override fun toJsonSimple(): JsonElement = throw IllegalArgumentException("Because of boxing this method must be implemented in subclasses")

    override fun toJson(includeCDDL: Boolean): JsonElement =
        if (includeCDDL) {
            toJsonWithCDDL()
        } else {
            toJsonSimple()
        }

    override fun toJsonWithCDDL(): JsonElement {
        val cddl = JsonPrimitive(this.cddl.format)
        return JsonObject(mapOf(Pair(CDDL_LITERAL, cddl), Pair(VALUE_LITERAL, toJsonSimple())))
    }

    override fun toJsonCborItem(): ICborItemValueJson =
        object : ICborItemValueJson {
            override val cddl: CDDLType
                get() = this@CborItem.cddl
            override val value: JsonElement
                get() = this@CborItem.toJsonWithCDDL()
        }

    /**
     * Allows subclasses to perform validations on the value
     */
    protected open fun validate() {
    }

    override fun toString(): String = "CborItem($VALUE_LITERAL=$value, $CDDL_LITERAL=$cddl)"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is CborItem<*>) {
            return false
        }

        if (value != other.value) {
            return false
        }
        if (majorType != other.majorType) {
            return false
        }
        if (info != other.info) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = value?.hashCode() ?: 0
        result = 31 * result + (majorType?.hashCode() ?: 0)
        result = 31 * result + (info ?: 0)
        return result
    }
}

@JsExportCompat
abstract class CborCollectionItem<Type>(
    value: Type,
    cddl: CDDLType,
) : CborItem<Type>(value, cddl)

// typealias CborItem<*> = CborItem<*>

/**
 * Converts any value to a [CborItem].
 *
 * Supported types:
 * - [CborItem]: returned as-is
 * - [String]: converted to [CborString]
 * - [ByteArray]: converted to [CborByteString]
 * - [Byte], [Short], [Int], [Long]: converted to appropriate integer types
 * - [UInt]: converted to unsigned integer
 * - [Boolean]: converted to [CborBool]
 * - [Double], [Float]: converted to floating point types
 * - [Enum]: converted to [CborString] using the enum name
 * - [List], [Array]: converted to [CborArray]
 * - [Map]: converted to [CborMap] with [CoseLabel] keys
 * - `null`: converted to [CborNull]
 *
 * @throws IllegalArgumentException if the value type is not supported
 */
@JsExportCompat
fun Any?.toCborItem(): CborItem<*> =
    when (this) {
        is CborItem<*> -> this

        is Enum<*> -> CDDL.tstr.newCborItem(this.name)

        is String -> CDDL.tstr.newCborItem(this)

        is ByteArray -> CDDL.bstr.newCborItem(this)

        is Boolean -> CDDL.bool.newCborItem(this)

        is UInt -> CDDL.uint.newUint(this.toLong())

        is ULong -> CDDL.uint.newUint(this.toLong())

        is Number -> numberToCborItem()

        is List<*> -> CborArray(map { it.toCborItem() }.toMutableList())

        is Array<*> -> CborArray(this.map { it.toCborItem() }.toMutableList())

        is Map<*, *> -> CborMap(this.map { (k, v) -> CoseLabel.fromCborItem(k.toCborItem()) to v.toCborItem() }.toMap().toMutableMap())

        null -> CborNull()

        else -> throw IllegalArgumentException(
            "Value must be a String, ByteArray, Byte, Short, Int, Long, Boolean, Double, Float, Enum, CborItem<*>, List, Array or Map, not ${this::class.simpleName} with value $this",
        )
    }

/**
 * Convert a [Number] to the appropriate [CborItem].
 *
 * On JVM/Native, runtime type information distinguishes Float, Double, Int, Long, etc.
 * On JS/WasmJs, all numbers are a single `Number` type, so we use value inspection
 * to determine the correct CBOR representation.
 */
private fun Number.numberToCborItem(): CborItem<*> {
    // Detect JS-like platforms where all numeric types map to a single Number type.
    // On JVM/Native: an Int is never simultaneously a Double → false.
    // On JS/WasmJs: all numbers satisfy all numeric type checks → true.
    val jsLikeNumberSystem = this is Int && this is Double

    if (!jsLikeNumberSystem) {
        // JVM/Native: use runtime type information
        return when (this) {
            is Float -> CDDL.float.newCborItem(this)
            is Double -> CDDL.float64.newFloat64(this)
            is Byte -> CDDL.int.newInt(toInt())
            is Short -> CDDL.int.newInt(toInt())
            is Int -> CDDL.int.newInt(this)
            is Long -> CDDL.int.newLong(this)
            else -> CDDL.float64.newFloat64(toDouble())
        }
    }

    // JS/WasmJs: inspect the value to determine type
    val d = toDouble()
    return if (d != kotlin.math.floor(d) || d.isInfinite() || d.isNaN()) {
        // Fractional or special value → floating point (always Double on JS)
        CDDL.float64.newFloat64(d)
    } else {
        // Integer value
        val l = d.toLong()
        if (l in Int.MIN_VALUE..Int.MAX_VALUE) {
            CDDL.int.newInt(l.toInt())
        } else {
            CDDL.int.newLong(l)
        }
    }
}

/**
 * Json representation
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ICborItemValueJson", exact = true)
@JsExportCompat
interface ICborItemValueJson {
    val cddl: CDDLType
    val value: JsonElement
}

/**
 * Json representation
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ICborItemJson", exact = true)
@JsExportCompat
interface ICborItemJson : ICborItemValueJson {
    val key: String
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("HasCborJsonRepresentation", exact = true)
@JsExportCompat
interface HasCborJsonRepresentation {
    fun toJsonWithCDDL(): JsonElement // Array or Object

    fun toJson(includeCDDL: Boolean = false): JsonElement

    fun toJsonSimple(): JsonElement

    fun toJsonCborItem(): ICborItemValueJson
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("CborItemJson", exact = true)
@JsExportCompat
@Serializable
data class CborItemJson(
    override val key: String,
    override val value: JsonElement,
    override val cddl: CDDLType,
) : HasCborJsonRepresentation,
    ICborItemJson {
    override fun toJsonWithCDDL() =
        JsonObject(
            mapOf(
                Pair(
                    JsonPrimitive(key).content,
                    JsonObject(mapOf(Pair(VALUE_LITERAL, value), Pair(CDDL_LITERAL, JsonPrimitive(cddl.format)))),
                ),
            ),
        )

    override fun toJsonSimple() = JsonObject(mapOf(Pair(JsonPrimitive(key).content, value)))

    override fun toJson(includeCDDL: Boolean) =
        if (includeCDDL) {
            toJsonWithCDDL()
        } else {
            toJsonSimple()
        }

    override fun toJsonCborItem(): ICborItemValueJson = this

    companion object {
        @JsStatic
        fun isCborItemValueJson(jsonElement: JsonElement): Boolean {
            if (jsonElement !is JsonObject) {
                return false
            }
            if (jsonElement.size == 2 || jsonElement.size == 3) {
                return jsonElement.containsKey(CDDL_LITERAL) && jsonElement.containsKey(VALUE_LITERAL)
            }
            return false
        }

        @JsStatic
        fun isCborItemJson(jsonElement: JsonElement): Boolean {
            if (jsonElement !is JsonObject) {
                return false
            }
            if (jsonElement.size == 3) {
                return jsonElement.containsKey(CDDL_LITERAL) && jsonElement.containsKey(VALUE_LITERAL) && jsonElement.containsKey(KEY_LITERAL)
            }
            return false
        }

        @JsStatic
        fun fromJsonPrimitive(
            jsonPrimitive: JsonPrimitive,
            cddl: CDDLType,
            key: String? = null,
        ): ICborItemValueJson {
            if (key !== null) {
                return CborItemJson(key, jsonPrimitive, cddl)
            }

            return object : ICborItemValueJson {
                override val cddl: CDDLType
                    get() = cddl
                override val value: JsonElement
                    get() = jsonPrimitive
            }
        }

        @JsStatic
        fun fromJsonArray(jsonArray: JsonArray): Array<ICborItemValueJson> =
            jsonArray
                .map {
                    if (it as? JsonObject !== null) {
                        fromJsonObjectAsValueJson(it.jsonObject)
                    } else if (it as? JsonPrimitive !== null) {
                        if (it.isString) {
                            fromJsonPrimitive(it.jsonPrimitive, CDDL.tstr)
                        } else {
                            fromJsonPrimitive(it.jsonPrimitive, CDDL.any)
                        }
                    } else if (it as? JsonArray !== null) {
                        fromJsonArray(it.jsonArray)
                    }
                    error("JsonObject does not contain 3 elements from a Cbor Json Item")
                }.toTypedArray()

        @JsStatic
        fun fromJsonObjectAsCborItemJson(jsonObject: JsonObject): ICborItemJson {
            check(isCborItemJson(jsonObject)) { "JsonObject does not contain 3 elements from a Cbor Json Item" }
            return fromJsonObjectAsValueJson(jsonObject) as ICborItemJson
        }

        @JsStatic
        fun fromJsonObjectAsValueJson(jsonObject: JsonObject): ICborItemValueJson {
            check(isCborItemValueJson(jsonObject) || isCborItemValueJson(jsonObject)) { "JsonObject does not contain 2 or 3 elements from a Cbor Json Item" }
            val value = jsonObject[VALUE_LITERAL] ?: error("value not available")
            val cddl =
                CDDL.util.fromFormat(
                    jsonObject[CDDL_LITERAL]?.jsonPrimitive?.content ?: error("cddl key not available"),
                )
            if (jsonObject.containsKey(KEY_LITERAL)) {
                return object : ICborItemJson {
                    override val cddl = cddl
                    override val key = jsonObject[KEY_LITERAL]?.jsonPrimitive?.content ?: error("'key' key not available")
                    override val value = value
                }
            }
            return object : ICborItemValueJson {
                override val cddl = cddl
                override val value = value
            }
        }

        @JsStatic
        fun fromDTO(cborItemJson: ICborItemJson): CborItemJson = CborItemJson(cborItemJson.key, cborItemJson.value, cborItemJson.cddl)
    }
}

@JsExportCompat
fun JsonObject.jsonObjectToCborJsonItem() = CborItemJson.fromJsonObjectAsValueJson(this)

/**
 * CBOR data item builder.
 */
@JsExportCompat
class SimpleCborBuilder(
    private val item: CborItem<*>,
) {
    /**
     * Builds the CBOR data items.
     *
     * @return a [CborItem]
     */
    fun build(): CborItem<*> = item
}
