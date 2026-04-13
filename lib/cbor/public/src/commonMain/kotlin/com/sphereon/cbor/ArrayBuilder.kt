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

import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.native.ObjCName

/**
 * Array builder.
 *
 * @deprecated Use the DSL builder instead: `cborArray { +"item" }`
 * @see com.sphereon.cbor.dsl.cborArray
 */
@Deprecated(
    message = "Use cborArray DSL instead",
    replaceWith = ReplaceWith("cborArray { }", "com.sphereon.cbor.dsl.cborArray")
)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ArrayBuilder", exact = true)
@JsExportCompat
data class ArrayBuilder<T>(private val parent: T, private val array: CborArray<CborItem<*>>) {
    fun addRequired(vararg item: CborItem<*>) = apply {
        if (item.isEmpty()) {
            throw IllegalArgumentException("item can not be empty")
        }
        item.map {
            if (it == null) {
                throw IllegalArgumentException("item can not be null")
            }
            array.value.add(it)
        }
    }


    /**
     * Adds a new data item.
     *
     * @param item the item to add.
     * @return the builder.
     */
    fun add(vararg item: CborItem<*>?) = apply {
        item.map {
            if (it != null) {
                array.value.add(it)
            }
        }
    }

    @JsName("addByteArray")
    fun add(vararg item: ByteArray?) = apply {
        array.value.addAll(item.mapNotNull { it?.let { CborByteString(it) } })
    }


    @JsName("addHasToCbor")
    fun add(vararg item: HasToCbor<*>?) = apply {
        item.map {
            if (it != null) {
                array.value.add(it.toCborStructure())
            }
        }
    }
    fun addCborArray(item: CborArray<CborItem<*>>?) = apply {
        item?.value?.map { add(it) }
    }

    fun addCborMap(item: CborMap<CborItem<*>, CborItem<*>>) = apply {
        add(item)
    }

    /**
     * Adds a tagged data item.
     *
     * @param tagNumber the number of the tag to use.
     * @param taggedItem the item to add.
     * @return the builder.
     */
    fun addTagged(tagNumber: Int, taggedItem: CborItem<*>) = apply {
        array.value.add(CborTagged(tagNumber, taggedItem))
    }

    /**
     * Adds a tagged bstr with encoded CBOR.
     *
     * @param encodedCbor the bytes of the encoded CBOR.
     */
    fun <T:CborItem<*>>addTaggedEncodedCbor(encodedCbor: ByteArray) = apply {
        array.value.add(CborTagged(CborTagged.ENCODED_CBOR, CborByteString(encodedCbor)))
    }

    /**
     * Adds a new map.
     *
     * This returns a new [MapBuilder], when done adding items to the map
     * [MapBuilder.end] should be called to get the current builder back.
     *
     * @return a builder for the map.
     */
    fun addMap(): MapBuilder<ArrayBuilder<T>> {
        val map = CborMap(mutableMapOf())
        add(map)
        return MapBuilder(this, map)
    }

    /**
     * Adds a new array.
     *
     * This returns a new [ArrayBuilder], when done adding items to the array,
     * [ArrayBuilder.end] should be called to get the current builder back.
     *
     * @return a builder for the array.
     */
    fun addArray(): ArrayBuilder<ArrayBuilder<T>> {
        val array = CborArray(mutableListOf())
        add(array)
        return ArrayBuilder(this, array)
    }

    /**
     * Ends building the array
     *
     * @return the containing builder.
     */
    fun end(): T = parent

// Convenience adders

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     *//*
    fun addByteArray(value: ByteArray) = apply {
        add(CDDL.bstr.newCborItem(value))
    }*/

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun addString(value: String) = apply {
        add(CDDL.tstr.newCborItem(value))
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun addByte(value: Byte) = apply {
        add(CDDL.int.newLong(value.toLong()))
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun addShort(value: Short) = apply {
        add(CDDL.int.newLong(value.toLong()))
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun addInt(value: Int) = apply {
        add(CDDL.int.newCborItem(value))
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun addLong(value: Long) = apply {
        add(CDDL.int.newLong(value))
    }

    /**
     * Adds a boolean.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun addBoolean(value: Boolean) = apply {
        add(CDDL.bool.newCborItem(value))
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun addDouble(value: Double) = apply {
        add(CDDL.float64.newCborItem(value))
    }


    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun addFloat(value: Float) = apply {
        add(CDDL.float.newCborItem(value))
    }

    fun addNull() = apply {
        add(CDDL.nil.newCborItem(null))
    }
}
