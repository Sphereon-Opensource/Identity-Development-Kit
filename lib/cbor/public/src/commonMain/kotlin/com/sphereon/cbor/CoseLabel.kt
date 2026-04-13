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


import com.sphereon.cbor.NumberLabeledMap.Companion.decodeNumberLabeledMap
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic
import kotlin.native.ObjCName


@OptIn(ExperimentalObjCName::class)
@ObjCName("LabelType", exact = true)
@JsExportCompat
enum class LabelType {
    String,
    Number
}

@JsExportCompat
sealed class CoseLabeled<CoseLabelType : CoseLabel<*>, CborItemType : CborItem<*>>(
    val label: CoseLabelType,
    val value: CborItemType,
)


@JsExportCompat
sealed class CoseLabel<Type>(
    value: Type,
    cddl: CDDLType,
    val type: LabelType,
) : CborItem<Type>(value, cddl) {


    abstract fun toCborStructure(): CborItem<Type>

    /**
     * By default we extract the regular value sub object out of the map value in Cbor. Unless a class is provided which is of type Cbor, then we extract the map value itself
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : CborBaseItem> required(map: CborMap<out CoseLabel<*>, CborItem<*>>): T {
        if (!map.value.containsKey(this)) {
            throw IllegalArgumentException("Key (label: ${this.value}, type: ${this.cddl}) not found in cbor map")
        }
        return map.value[this] as T
    }

    fun requiredAsCborMap(map: CborMap<out CoseLabel<*>, CborItem<*>>): CborMap<CborItem<*>, CborItem<*>> {
        return required(map)
    }

    fun <T : CborItem<*>> requiredAsCborArray(map: CborMap<out CoseLabel<*>, CborItem<*>>): CborArray<T> {
        return required(map)
    }

    fun <T> requiredAsListFromCborArray(map: CborMap<out CoseLabel<*>, CborItem<*>>): cddl_list<T> {
        return requiredAsCborArray<CborItem<*>>(map).value as cddl_list<T>
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : CborBaseItem> optional(map: CborMap<out CoseLabel<*>, CborItem<*>>): T? {
        if (!map.value.containsKey(this)) {
            return null
        }
        return map.value[this] as T
    }

    fun optionalAsCborMap(map: CborMap<out CoseLabel<*>, CborItem<*>>): CborMap<CborItem<*>, CborItem<*>>? {
        return optional(map)
    }

    fun optionalAsCborArray(map: CborMap<out CoseLabel<*>, CborItem<*>>): CborArray<CborItem<*>>? {
        return optional(map)
    }

    companion object {
        @JsStatic
        fun fromCborStructure(cborItem: CborItem<*>): CoseLabel<out Comparable<*>> =
            when (cborItem) {
                is CborUInt -> NumberLabel(cborItem.value.toInt())
                is CborNInt -> NumberLabel(-cborItem.value.toInt())
                is CborString -> StringLabel(cborItem.value)
                is CoseLabel<*> -> cborItem as CoseLabel<out Comparable<*>>
                else -> throw IllegalStateException("Cannot create a label from cbor item with type ${cborItem.cddl}")
            }
    }


}

@Suppress("UNCHECKED_CAST")
@JsExportCompat
open class NumberLabeledMap(
    protected val labeledItems: CborMap<NumberLabel, CborItem<*>> = CborMap(mutableMapOf()),
) {
    protected fun <T> putLabel(label: Int, cborItem: CborItem<T>?) {
        if (cborItem == null) {
            return
        }
        labeledItems.value[NumberLabel(label)] = cborItem
    }

    fun <Type> requiredLabel(label: Int): Type {
        return labeledItems.value[NumberLabel(label)] as Type
    }

    fun <Type> optionalLabel(label: Int): Type? {
        val numberLabel = NumberLabel(label)
        val result = labeledItems.value[numberLabel] ?: return null
        return result as Type
    }

    fun hasLabel(label: Int): Boolean {
        return labeledItems.value.containsKey(NumberLabel(label))
    }

    fun getLabels(): Set<NumberLabel> {
        return labeledItems.value.keys
    }
    /*
        fun <Type : Any> getLabeledValue(label: Int): NumberLabelValue<Type> {
            if (!hasLabel(label)) {
                throw IllegalArgumentException("Label with value $label is not valid for this object")
            }
            val value = optionalLabel<Type>(label)
            return NumberLabelValue(label, value)
        }

        fun toLabeledValues(): List<NumberLabelValue<Any>> {
            return value.map { NumberLabelValue(it.key.value, it.value) }
        }*/

    protected open fun connectLabels(): CborMap<NumberLabel, CborItem<*>> {
        if (labeledItems.value.isEmpty()) {
            // We do this instead of an abstract method to ensure a subclass not only overrides this method, but also calls the method from an init block
            throw IllegalArgumentException("Please implement connectLabels() in a sub class")
        }
        return labeledItems
    }


    /*open fun instanceFromLabels(): NumberLabeledMapObject {
        throw IllegalArgumentException("Please implement instanceFromLabels() in a sub class")
    }
*/


    /**
     * We call connectLabels, to ensure a subclass will implement this. The default implementation throws an error
     * The drawback is that we populate the labels map 2 times.
     *   Once when it is called as part of init of this super class. The values will be null at that time.
     *   Second from the subclass. Then the properties will be available and punt in the map
     *
     * Since the overhead is minimal, and we want to ensure developers follow the pattern we are "okay" with it.
     */

    /*init {
        this.connectLabels()
    }*/


    fun encode(): ByteArray {
        return Cbor.encode(connectLabels())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NumberLabeledMap) return false

        if (labeledItems != other.labeledItems) return false

        return true
    }

    override fun hashCode(): Int {
        return labeledItems.hashCode()
    }

    companion object {
        @JsStatic
        fun decodeNumberLabeledMap(numberMappedObject: ByteArray): NumberLabeledMap {
            val map: CborMap<NumberLabel, CborItem<*>> = Cbor.decode(numberMappedObject)
            return NumberLabeledMap(map)
        }
    }

}

fun ByteArray.toNumberLabeledMap() = decodeNumberLabeledMap(this)


@JsExportCompat
fun Int.toNumberLabel() = NumberLabel(this)

@JsExportCompat
fun Long.longToNumberLabel() = NumberLabel(this.toInt())

@JsExportCompat
fun String.toStringLabel() = StringLabel(this)
/*

@JsExportCompat
fun <Type : Any> CborItem<Type>.toCoseNumberLabel(label: Int) = NumberLabelValue(label, this)

@JsExportCompat
fun <Type : Any> CborItem<Type>.toCoseStringLabel(label: String) = StringLabelValue(label, this)
*/
