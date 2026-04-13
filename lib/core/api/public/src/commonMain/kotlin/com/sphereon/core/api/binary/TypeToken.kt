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
 */

package com.sphereon.core.api.binary

import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A type token that captures type information at compile time for use at runtime.
 *
 * This is essential for generic serialization/deserialization where type information
 * would otherwise be erased. TypeToken captures the full generic type including
 * type parameters.
 *
 * **Usage:**
 * ```kotlin
 * // Create a type token for a specific type
 * val listOfStrings = typeToken<List<String>>()
 *
 * // Use with codec for deserialization
 * val result = codec.decode(bytes, typeToken<MyDataClass>())
 *
 * // Compare type tokens
 * typeToken<String>() == typeToken<String>() // true
 * typeToken<List<String>>() == typeToken<List<Int>>() // false
 * ```
 *
 * **Platform Notes:**
 * - JVM: Full type parameter support
 * - Native: Full type parameter support
 * - JS: Limited - some reflection features may not work
 *
 * @param T The type to capture
 * @property kType The Kotlin type representation
 */
class TypeToken<T> @PublishedApi internal constructor(
    val kType: KType
) {
    /**
     * Returns the simple class name without package.
     */
    val simpleName: String
        get() = kType.toString().substringAfterLast('.')

    /**
     * Returns the full qualified type name.
     */
    val qualifiedName: String
        get() = kType.toString()

    /**
     * Whether this type is nullable.
     */
    val isNullable: Boolean
        get() = kType.isMarkedNullable

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeToken<*>) return false
        return kType == other.kType
    }

    override fun hashCode(): Int = kType.hashCode()

    override fun toString(): String = "TypeToken<$kType>"

    companion object {
        /**
         * Creates a TypeToken for Unit (void) type.
         */
        val UNIT: TypeToken<Unit> = TypeToken(typeOf<Unit>())

        /**
         * Creates a TypeToken for String type.
         */
        val STRING: TypeToken<String> = TypeToken(typeOf<String>())

        /**
         * Creates a TypeToken for ByteArray type.
         */
        val BYTE_ARRAY: TypeToken<ByteArray> = TypeToken(typeOf<ByteArray>())
    }
}

/**
 * Creates a TypeToken for the specified type.
 *
 * This inline function uses reified type parameters to capture the full type
 * information at compile time.
 *
 * **Example:**
 * ```kotlin
 * val stringToken = typeToken<String>()
 * val listToken = typeToken<List<Map<String, Int>>>()
 * ```
 */
inline fun <reified T> typeToken(): TypeToken<T> = TypeToken(typeOf<T>())
