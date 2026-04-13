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

package com.sphereon.cbor.dsl

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborNull
import com.sphereon.cbor.HasToCbor
import com.sphereon.cbor.toCborItem
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * DSL scope for building CBOR arrays.
 *
 * This scope provides unary plus operators and helper methods for building
 * CBOR arrays in a type-safe and concise manner.
 *
 * Example:
 * ```kotlin
 * cborArray {
 *     +"hello"           // String item
 *     +42                // Int item
 *     +true              // Boolean item
 *     +byteArrayOf(1,2)  // ByteArray item
 *     map {              // Nested map
 *         "key" to "value"
 *     }
 *     array {            // Nested array
 *         +1
 *         +2
 *     }
 * }
 * ```
 */
@CborDslMarker
class CborArrayScope {
    @PublishedApi
    internal val items = mutableListOf<CborItem<*>>()

    // ========================================
    // Unary plus operators for adding items
    // ========================================

    /**
     * Adds a String item to the array.
     */
    operator fun String.unaryPlus() {
        items.add(this.toCborItem())
    }

    /**
     * Adds an Int item to the array.
     */
    operator fun Int.unaryPlus() {
        items.add(this.toCborItem())
    }

    /**
     * Adds a Long item to the array.
     */
    operator fun Long.unaryPlus() {
        items.add(this.toCborItem())
    }

    /**
     * Adds a Boolean item to the array.
     */
    operator fun Boolean.unaryPlus() {
        items.add(this.toCborItem())
    }

    /**
     * Adds a Double item to the array.
     */
    operator fun Double.unaryPlus() {
        items.add(this.toCborItem())
    }

    /**
     * Adds a Float item to the array.
     */
    operator fun Float.unaryPlus() {
        items.add(this.toCborItem())
    }

    /**
     * Adds a ByteArray item to the array.
     */
    operator fun ByteArray.unaryPlus() {
        items.add(this.toCborItem())
    }

    /**
     * Adds a CborItem to the array.
     */
    operator fun CborItem<*>.unaryPlus() {
        items.add(this)
    }

    /**
     * Adds a HasToCbor item to the array.
     */
    operator fun HasToCbor<*>.unaryPlus() {
        items.add(this.toCborStructure())
    }

    /**
     * Adds an Enum item to the array (as its name string).
     */
    operator fun Enum<*>.unaryPlus() {
        items.add(this.toCborItem())
    }

    // ========================================
    // Explicit add methods
    // ========================================

    /**
     * Adds an item to the array. Null values are skipped.
     */
    fun add(value: Any?) {
        if (value != null) {
            items.add(value.toCborItem())
        }
    }

    /**
     * Adds all items from a collection to the array. Null values are skipped.
     */
    fun addAll(values: Collection<Any?>) {
        for (value in values) {
            if (value != null) {
                items.add(value.toCborItem())
            }
        }
    }

    /**
     * Adds a null value to the array.
     */
    fun addNull() {
        items.add(CborNull())
    }

    /**
     * Adds a CborItem directly to the array.
     */
    fun addItem(item: CborItem<*>) {
        items.add(item)
    }

    // ========================================
    // Nested structure builders
    // ========================================

    /**
     * Adds a nested map to the array.
     */
    @OptIn(ExperimentalContracts::class)
    inline fun map(builderAction: CborMapScope.() -> Unit) {
        contract {
            callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
        }
        items.add(cborMap(builderAction))
    }

    /**
     * Adds a nested array to the array.
     */
    @OptIn(ExperimentalContracts::class)
    inline fun array(builderAction: CborArrayScope.() -> Unit) {
        contract {
            callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
        }
        items.add(cborArray(builderAction))
    }

    // ========================================
    // Build
    // ========================================

    /**
     * Builds and returns the CBOR array.
     */
    fun build(): CborArray<CborItem<*>> = CborArray(items)
}
