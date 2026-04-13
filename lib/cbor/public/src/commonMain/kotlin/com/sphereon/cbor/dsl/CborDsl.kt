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

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * DSL marker annotation to prevent scope pollution in nested builders.
 *
 * This annotation ensures that methods from outer scopes are not accidentally
 * called in nested DSL blocks.
 */
@DslMarker
annotation class CborDslMarker

/**
 * Creates a CBOR map using a DSL builder.
 *
 * Example:
 * ```kotlin
 * val map = cborMap {
 *     "name" to "John"
 *     "age" to 30
 *     optional("email", email) // skips if null
 *     "address" map {
 *         "city" to "Amsterdam"
 *         "country" to "NL"
 *     }
 * }
 * ```
 *
 * @param builderAction the DSL block to build the map
 * @return the constructed [CborMap]
 */
@OptIn(ExperimentalContracts::class)
inline fun cborMap(builderAction: CborMapScope.() -> Unit): CborMap<CborItem<*>, CborItem<*>?> {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }
    val scope = CborMapScope()
    scope.builderAction()
    return scope.build()
}

/**
 * Creates a CBOR array using a DSL builder.
 *
 * Example:
 * ```kotlin
 * val array = cborArray {
 *     +"hello"
 *     +42
 *     +true
 *     map {
 *         "nested" to "value"
 *     }
 * }
 * ```
 *
 * @param builderAction the DSL block to build the array
 * @return the constructed [CborArray]
 */
@OptIn(ExperimentalContracts::class)
inline fun cborArray(builderAction: CborArrayScope.() -> Unit): CborArray<CborItem<*>> {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }
    val scope = CborArrayScope()
    scope.builderAction()
    return scope.build()
}

/**
 * Creates a CBOR map builder with a subject object for context.
 *
 * This is useful when building CBOR from domain objects, allowing
 * the builder to retain a reference to the source object.
 *
 * Example:
 * ```kotlin
 * data class Person(val name: String, val age: Int)
 *
 * val person = Person("John", 30)
 * val builder = cborMapBuilder(person) {
 *     "name" to person.name
 *     "age" to person.age
 * }
 * val map = builder.build()
 * val subject = builder.subject() // returns the Person instance
 * ```
 *
 * @param subject the subject object to associate with this builder
 * @param builderAction the DSL block to build the map
 * @return a [CborBuilder] containing both the map and subject reference
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> cborMapBuilder(subject: T, builderAction: CborMapScope.() -> Unit): CborBuilder<T> {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }
    val scope = CborMapScope()
    scope.builderAction()
    return CborBuilder(scope.build(), subject)
}

/**
 * Creates a CBOR array builder with a subject object for context.
 *
 * @param subject the subject object to associate with this builder
 * @param builderAction the DSL block to build the array
 * @return a [CborBuilder] containing both the array and subject reference
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> cborArrayBuilder(subject: T, builderAction: CborArrayScope.() -> Unit): CborBuilder<T> {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }
    val scope = CborArrayScope()
    scope.builderAction()
    return CborBuilder(scope.build(), subject)
}

/**
 * Extension function to encode a CborItem to bytes.
 */
fun CborItem<*>.encode(): ByteArray = Cbor.encode(this)
