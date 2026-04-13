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

package com.sphereon.cbor.dsl

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborTagged
import com.sphereon.cbor.CoseLabel
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.toCborItem
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * DSL scope for building CBOR maps.
 *
 * This scope provides infix operators and helper methods for building
 * CBOR maps in a type-safe and concise manner.
 *
 * Example:
 * ```kotlin
 * cborMap {
 *     "name" to "John"           // String key
 *     1 to "algorithm"           // Int key
 *     -7 to "ES256"              // Negative int key
 *     optional("email", email)   // Skipped if null
 *     "address" map {            // Nested map
 *         "city" to "Amsterdam"
 *     }
 *     "tags" array {             // Nested array
 *         +"kotlin"
 *         +"cbor"
 *     }
 * }
 * ```
 */
@CborDslMarker
class CborMapScope {
    @PublishedApi
    internal val entries = mutableMapOf<CborItem<*>, CborItem<*>?>()

    // ========================================
    // String key operators
    // ========================================

    /**
     * Adds a key-value pair with a String key.
     */
    infix fun String.to(value: Any?) {
        if (value != null) {
            entries[StringLabel(this)] = value.toCborItem()
        }
    }

    /**
     * Adds a key with a nested map value.
     */
    @OptIn(ExperimentalContracts::class)
    inline infix fun String.map(builderAction: CborMapScope.() -> Unit) {
        contract {
            callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
        }
        entries[StringLabel(this)] = cborMap(builderAction)
    }

    /**
     * Adds a key with a nested array value.
     */
    @OptIn(ExperimentalContracts::class)
    inline infix fun String.array(builderAction: CborArrayScope.() -> Unit) {
        contract {
            callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
        }
        entries[StringLabel(this)] = cborArray(builderAction)
    }

    // ========================================
    // Int key operators
    // ========================================

    /**
     * Adds a key-value pair with an Int key.
     */
    infix fun Int.to(value: Any?) {
        if (value != null) {
            entries[NumberLabel(this)] = value.toCborItem()
        }
    }

    /**
     * Adds an Int key with a nested map value.
     */
    @OptIn(ExperimentalContracts::class)
    inline infix fun Int.map(builderAction: CborMapScope.() -> Unit) {
        contract {
            callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
        }
        entries[NumberLabel(this)] = cborMap(builderAction)
    }

    /**
     * Adds an Int key with a nested array value.
     */
    @OptIn(ExperimentalContracts::class)
    inline infix fun Int.array(builderAction: CborArrayScope.() -> Unit) {
        contract {
            callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
        }
        entries[NumberLabel(this)] = cborArray(builderAction)
    }

    // ========================================
    // Long key operators
    // ========================================

    /**
     * Adds a key-value pair with a Long key.
     */
    infix fun Long.to(value: Any?) {
        if (value != null) {
            entries[NumberLabel(this.toInt())] = value.toCborItem()
        }
    }

    // ========================================
    // CoseLabel key operators
    // ========================================

    /**
     * Adds a key-value pair with a CoseLabel key.
     */
    infix fun CoseLabel<*>.to(value: Any?) {
        if (value != null) {
            entries[this] = value.toCborItem()
        }
    }

    /**
     * Adds a CoseLabel key with a nested map value.
     */
    @OptIn(ExperimentalContracts::class)
    inline infix fun CoseLabel<*>.map(builderAction: CborMapScope.() -> Unit) {
        contract {
            callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
        }
        entries[this] = cborMap(builderAction)
    }

    /**
     * Adds a CoseLabel key with a nested array value.
     */
    @OptIn(ExperimentalContracts::class)
    inline infix fun CoseLabel<*>.array(builderAction: CborArrayScope.() -> Unit) {
        contract {
            callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
        }
        entries[this] = cborArray(builderAction)
    }

    // ========================================
    // Optional value handling
    // ========================================

    /**
     * Adds a key-value pair only if the value is non-null.
     */
    fun optional(
        key: String,
        value: Any?,
    ) {
        if (value != null) {
            entries[StringLabel(key)] = value.toCborItem()
        }
    }

    /**
     * Adds a key-value pair only if the value is non-null.
     */
    fun optional(
        key: Int,
        value: Any?,
    ) {
        if (value != null) {
            entries[NumberLabel(key)] = value.toCborItem()
        }
    }

    /**
     * Adds a key-value pair only if the value is non-null.
     */
    fun optional(
        key: CoseLabel<*>,
        value: Any?,
    ) {
        if (value != null) {
            entries[key] = value.toCborItem()
        }
    }

    // ========================================
    // Collection handling
    // ========================================

    /**
     * Adds an array only if the collection is non-null and non-empty.
     */
    fun <T> nonEmptyArray(
        key: String,
        items: Collection<T>?,
    ) {
        if (!items.isNullOrEmpty()) {
            entries[StringLabel(key)] = CborArray(items.map { it.toCborItem() }.toMutableList())
        }
    }

    /**
     * Adds an array only if the collection is non-null and non-empty,
     * with a mapper function to transform items.
     */
    fun <T> nonEmptyArray(
        key: String,
        items: Collection<T>?,
        mapper: (T) -> Any?,
    ) {
        if (!items.isNullOrEmpty()) {
            entries[StringLabel(key)] = CborArray(items.mapNotNull { mapper(it)?.toCborItem() }.toMutableList())
        }
    }

    /**
     * Adds an array only if the collection is non-null and non-empty.
     */
    fun <T> nonEmptyArray(
        key: Int,
        items: Collection<T>?,
    ) {
        if (!items.isNullOrEmpty()) {
            entries[NumberLabel(key)] = CborArray(items.map { it.toCborItem() }.toMutableList())
        }
    }

    /**
     * Adds an array only if the collection is non-null and non-empty,
     * with a mapper function to transform items.
     */
    fun <T> nonEmptyArray(
        key: Int,
        items: Collection<T>?,
        mapper: (T) -> Any?,
    ) {
        if (!items.isNullOrEmpty()) {
            entries[NumberLabel(key)] = CborArray(items.mapNotNull { mapper(it)?.toCborItem() }.toMutableList())
        }
    }

    /**
     * Adds an array only if the collection is non-null and non-empty.
     */
    fun <T> nonEmptyArray(
        key: CoseLabel<*>,
        items: Collection<T>?,
    ) {
        if (!items.isNullOrEmpty()) {
            entries[key] = CborArray(items.map { it.toCborItem() }.toMutableList())
        }
    }

    /**
     * Adds an array only if the collection is non-null and non-empty,
     * with a mapper function to transform items.
     */
    fun <T> nonEmptyArray(
        key: CoseLabel<*>,
        items: Collection<T>?,
        mapper: (T) -> Any?,
    ) {
        if (!items.isNullOrEmpty()) {
            entries[key] = CborArray(items.mapNotNull { mapper(it)?.toCborItem() }.toMutableList())
        }
    }

    // ========================================
    // Raw entry manipulation
    // ========================================

    /**
     * Adds a raw CborItem key-value pair.
     */
    fun put(
        key: CborItem<*>,
        value: CborItem<*>?,
    ) {
        if (value != null) {
            entries[key] = value
        }
    }

    /**
     * Adds a tagged value with a String key.
     */
    fun putTagged(
        key: String,
        tagNumber: Int,
        value: CborItem<*>?,
    ) {
        if (value != null) {
            entries[StringLabel(key)] = CborTagged(tagNumber, value)
        }
    }

    /**
     * Adds a tagged value with a CoseLabel key.
     */
    fun putTagged(
        key: CoseLabel<*>,
        tagNumber: Int,
        value: CborItem<*>?,
    ) {
        if (value != null) {
            entries[key] = CborTagged(tagNumber, value)
        }
    }

    /**
     * Builds and returns the CBOR map.
     */
    fun build(): CborMap<CborItem<*>, CborItem<*>?> = CborMap(entries)
}
