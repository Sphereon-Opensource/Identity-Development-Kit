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
 */

package com.sphereon.cbor

import kotlin.test.assertTrue

/**
 * Whether the platform has distinct Float and Double types at runtime.
 * On JVM/Native: true (Float and Double are separate types)
 * On JS/WasmJs: false (all numbers are a single Number type)
 */
val hasDistinctFloatType: Boolean = (1.0f as Any) !is Double

/**
 * Assert that a CBOR item is a float type (CborFloat32 on JVM, CborDouble on JS).
 * On JS/WasmJs, Float values become Double since the platform has no distinct Float type.
 */
fun assertIsCborFloat(
    item: CborItem<*>,
    message: String = "",
) {
    if (hasDistinctFloatType) {
        assertTrue(item is CborFloat32, "${message}Expected CborFloat32 but got ${item::class.simpleName}")
    } else {
        assertTrue(
            item is CborFloat32 || item is CborDouble,
            "${message}Expected CborFloat32 or CborDouble but got ${item::class.simpleName}",
        )
    }
}

/**
 * Assert that a CBOR item is a CborDouble.
 * On JS/WasmJs, this also passes since all numeric values are Double.
 */
fun assertIsCborDouble(
    item: CborItem<*>,
    message: String = "",
) {
    assertTrue(item is CborDouble, "${message}Expected CborDouble but got ${item::class.simpleName}")
}
