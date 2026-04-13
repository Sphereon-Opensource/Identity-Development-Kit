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

import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.toCborItem

/**
 * Map builder.
 *
 * @deprecated Use the DSL builder instead: `cborMap { "key" to "value" }`
 * @see com.sphereon.cbor.dsl.cborMap
 */
@Deprecated(
    message = "Use cborMap DSL instead",
    replaceWith = ReplaceWith("cborMap { }", "com.sphereon.cbor.dsl.cborMap")
)
class MapBuilder<T>(private val parent: T, private val map: CborMap<CborItem<*>, CborItem<*>?>) {

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: CborItem<*>, value: CborItem<*>?, optional: Boolean = false) = apply {
        if (!optional || value != null) {
            if (!optional && value == null) {
                throw IllegalArgumentException("Value for ${key} cannot be null")
            }
            // TODO: How do we want to handle an empty array or list. Probably needs another arg as there might be valid use cases to encode these
            map.value[key] = value
        }
    }

    /**
     * Ends building the array.
     *
     * @return the containing builder.
     */
    fun end(): T = parent


    /**
     * Puts a value that has built-in toCbor conversion method in the map
     *
     *
     */
    fun put(key: CborItem<*>, value: HasToCbor<*>?, optional: Boolean = false) = apply {
        put(key, value?.toCborStructure(), optional)
    }


    /**
     * Puts a new array in the map.
     *
     * This returns a new [ArrayBuilder], when done adding items to the array,
     * [ArrayBuilder.end] should be called to get the current builder back.
     *
     * @param key the key.
     * @return a [ArrayBuilder].
     */
    fun putArray(key: CborItem<*>): ArrayBuilder<MapBuilder<T>> {
        val array = CborArray(mutableListOf())
        put(key, array)
        return ArrayBuilder(this, array)
    }

    fun putCborMap(key: CborItem<*>, value: CborMap<CborItem<*>, CborItem<*>>?, optional: Boolean = false) = apply {
        put(key, value, optional)
    }

    fun putCborArray(key: CborItem<*>, value: CborArray<CborItem<*>>?, optional: Boolean = false) = apply {
        put(key, value, optional)
    }

    /**
     * Puts a new map in the map.
     *
     * This returns a new [MapBuilder], when done adding items to the map
     * [MapBuilder.end] should be called to get the current builder back.
     *
     * @param key the key.
     * @return a [MapBuilder].
     */
    fun putMap(key: CborItem<*>): MapBuilder<MapBuilder<T>> {
        val map = CborMap(mutableMapOf())
        put(key, map)
        return MapBuilder(this, map)
    }

    /**
     * Puts a tagged data item in the map.
     *
     * @param key the key.
     * @param tagNumber the number of the tag to use.
     * @param toTagItem the item to add.
     * @return the builder.
     */
    fun putTagged(key: CborItem<*>, tagNumber: Int, toTagItem: CborItem<*>?, optional: Boolean = false) = apply {
        put(key, toTagItem?.let { CborTagged(tagNumber, toTagItem) }, optional)
    }

    /**
     * Puts a tagged bstr with encoded CBOR in the map.
     *
     * @param key the key.
     * @param encodedCbor the bytes of the encoded CBOR.
     */
    fun <T : CborItem<*>> putTaggedEncodedCbor(key: CborItem<*>, encodedCbor: ByteArray?, optional: Boolean = false) = apply {
        putTagged(key, CborTagged.ENCODED_CBOR, encodedCbor?.let { CborByteString(encodedCbor) }, optional)
    }


    // Convenience putters for String keys

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: CborItem<*>?, optional: Boolean = false) = apply {
        put(key.toCborString(), value, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: String?, optional: Boolean = false) = apply {
        put(CDDL.tstr.newCborItem(key), value?.let { CDDL.tstr.newCborItem(value) }, optional)
    }

    /**
     * Puts a value that has built-in toCbor conversion method in the map
     *
     *
     */
    fun put(key: String, value: HasToCbor<*>?, optional: Boolean = false) = apply {
        put(CDDL.tstr.newCborItem(key), value?.toCborStructure(), optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: ByteArray?, optional: Boolean = false) = apply {
        put(CDDL.tstr.newCborItem(key), value?.let { CDDL.bstr.newCborItem(value) }, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Byte?, optional: Boolean = false) = apply {
        put(CDDL.tstr.newCborItem(key), value?.let { CDDL.int.newInt(value.toInt()) }, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Short?, optional: Boolean = false) = apply {
        put(CDDL.tstr.newCborItem(key), value?.let { CDDL.int.newInt(value.toInt()) }, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Int?, optional: Boolean = false) = apply {
        put(CDDL.tstr.newCborItem(key), value?.let { CDDL.int.newCborItem(value) }, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Long?, optional: Boolean = false) = apply {
        put(CDDL.tstr.newCborItem(key), value?.let { CDDL.int.newLong(value) }, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Boolean?, optional: Boolean = false) = apply {
        put(CDDL.tstr.newCborItem(key), value?.let { CDDL.bool.newCborItem(value) }, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Double?, optional: Boolean = false) = apply {
        put(CDDL.tstr.newCborItem(key), value?.let { CDDL.float64.newFloat64(value) }, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Float?, optional: Boolean = false) = apply {
        put(CDDL.tstr.newCborItem(key), value?.let { CDDL.float.newCborItem(value) }, optional)
    }

    @Suppress("UNCHECKED_CAST")
    fun <KeyType : CborItem<*>, ValueType : CborItem<*>> toCborMap(input: Map<Any, Any>): CborMap<KeyType, ValueType> {
        return CborMap(
            input.map { toCborItem(it.key) as KeyType to toCborItem(it.value) as ValueType }.toMap().toMutableMap()
        )
    }

    private fun toCborItem(value: Any?): CborItem<*> = value.toCborItem()

    fun put(key: String, value: Map<*, *>?, optional: Boolean = false) = apply {
        put(key, value?.let { toCborItem(value) }, optional)
    }

    fun put(key: CborItem<*>, value: Map<*, *>?, optional: Boolean = false) = apply {
        put(key, value?.let { toCborItem(value) }, optional)
    }

    fun put(key: String, value: List<*>?, optional: Boolean = false) = apply {
        put(key, value?.let { toCborItem(value) }, optional)
    }

    fun put(key: CborItem<*>, value: List<*>?, optional: Boolean = false) = apply {
        put(key, value?.let { toCborItem(value) }, optional)
    }

    fun put(key: String, value: Array<*>?, optional: Boolean = false) = apply {
        put(key, value?.let { toCborItem(value) }, optional)
    }

    fun put(key: CborItem<*>, value: Array<*>?, optional: Boolean = false) = apply {
        put(key, value?.let { toCborItem(value) }, optional)
    }


    /**
     * Puts a new array in the map.
     *
     * This returns a new [ArrayBuilder], when done adding items to the array,
     * [ArrayBuilder.end] should be called to get the current builder back.
     *
     * @param key the key.
     * @return a [ArrayBuilder].
     */
    fun putArray(key: String): ArrayBuilder<MapBuilder<T>> = putArray(key.toCborString())

    /**
     * Puts a new map in the map.
     *
     * This returns a new [MapBuilder], when done adding items to the map
     * [MapBuilder.end] should be called to get the current builder back.
     *
     * @param key the key.
     * @return a [MapBuilder].
     */
    fun putMap(key: String): MapBuilder<MapBuilder<T>> {
        return putMap(key.toCborString())
    }

    /**
     * Puts a tagged data item in the map.
     *
     * @param key the key.
     * @param tagNumber the number of the tag to use.
     * @param taggedItem the item to add.
     * @return the builder.
     */
    fun putTagged(key: String, tagNumber: Int, value: CborItem<Any>?, optional: Boolean = false) = apply {
        putTagged(key.toCborString(), tagNumber, value, optional)
    }

    /**
     * Puts a tagged bstr with encoded CBOR in the map.
     *
     * @param key the key.
     * @param encodedCbor the bytes of the encoded CBOR.
     */
    fun <T : CborItem<*>> putTaggedEncodedCbor(key: String, encodedCbor: ByteArray?, optional: Boolean = false) = apply {
        putTaggedEncodedCbor<T>(CDDL.tstr.newCborItem(key), encodedCbor, optional)
    }

    // Convenience putters for Long keys


    /**
     * Puts a value that has built-in toCbor conversion method in the map
     *
     *
     */
    fun put(key: Long, value: HasToCbor<*>?, optional: Boolean = false) = apply {
        put(key, value?.toCborStructure(), optional)
    }


    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: CborItem<*>?, optional: Boolean = false) = apply {
        put(CDDL.int.newLong(key), value, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: String?, optional: Boolean = false) = apply {
        put(CDDL.int.newLong(key), value?.let { value.toCborString() }, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: ByteArray?, optional: Boolean = false) = apply {
        put(CDDL.int.newLong(key), value?.let { CDDL.bstr.newCborItem(value) }, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Byte?, optional: Boolean = false) = apply {
        put(CDDL.int.newLong(key), value?.let { CDDL.int.newInt(value.toInt()) }, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Short?, optional: Boolean = false) = apply {
        put(CDDL.int.newLong(key), value?.let { CDDL.int.newInt(value.toInt()) }, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Int?, optional: Boolean = false) = apply {
        put(CDDL.int.newLong(key), value?.let { CDDL.int.newInt(value) }, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Long?, optional: Boolean = false) = apply {
        put(CDDL.int.newLong(key), value?.let { CDDL.int.newLong(value) }, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Boolean?, optional: Boolean = false) = apply {
        put(CDDL.int.newLong(key), value?.let { CDDL.bool.newCborItem(value) }, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Double?, optional: Boolean = false) = apply {
        put(CDDL.int.newLong(key), value?.let { CDDL.float64.newFloat64(value) }, optional)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Float?, optional: Boolean = false) = apply {
        put(CDDL.int.newLong(key), value?.let { CDDL.float.newCborItem(value) }, optional)
    }

    /**
     * Puts a new array in the map.
     *
     * This returns a new [ArrayBuilder], when done adding items to the array,
     * [ArrayBuilder.end] should be called to get the current builder back.
     *
     * @param key the key.
     * @return a [ArrayBuilder].
     */
    fun putArray(key: Long): ArrayBuilder<MapBuilder<T>> = putArray(CDDL.int.newLong(key))

    /**
     * Puts a new map in the map.
     *
     * This returns a new [MapBuilder], when done adding items to the map
     * [MapBuilder.end] should be called to get the current builder back.
     *
     * @param key the key.
     * @return a [MapBuilder].
     */
    fun putMap(key: Long): MapBuilder<MapBuilder<T>> = putMap(CDDL.int.newLong(key))

    /**
     * Puts a tagged data item in the map.
     *
     * @param key the key.
     * @param tagNumber the number of the tag to use.
     * @param taggedItem the item to add.
     * @return the builder.
     */
    fun putTagged(key: Long, tagNumber: Int, value: CborItem<Any>?, optional: Boolean = false): MapBuilder<T> =
        putTagged(CDDL.int.newLong(key), tagNumber, value, optional)


    /**
     * Puts a tagged bstr with encoded CBOR in the map.
     *
     * @param key the key.
     * @param encodedCbor the bytes of the encoded CBOR.
     */
    fun <T : CborItem<*>> putTaggedEncodedCbor(key: Long, encodedCbor: ByteArray?, optional: Boolean = false) = apply {
        putTaggedEncodedCbor<T>(CDDL.int.newLong(key), encodedCbor, optional)
    }
}
