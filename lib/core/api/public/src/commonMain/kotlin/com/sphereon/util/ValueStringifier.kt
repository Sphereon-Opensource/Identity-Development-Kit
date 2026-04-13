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

package com.sphereon.util


/**
 * Recursively stringify values so maps of maps, collections and arrays are readable.
 */
fun stringify(value: Any?): String = when (value) {
    null -> "null"
    is Map<*, *> -> stringifyMap(value)
    is BooleanArray -> value.joinToString(prefix = "booleanArrayOf(", postfix = ")")
    is ByteArray -> "byteArrayOf(${toHexString(byteArray = value)})"
    is ShortArray -> value.joinToString(prefix = "shortArrayOf(", postfix = ")")
    is IntArray -> value.joinToString(prefix = "intArrayOf(", postfix = ")")
    is LongArray -> value.joinToString(prefix = "longArrayOf(", postfix = ")")
    is FloatArray -> value.joinToString(prefix = "floatArrayOf(", postfix = ")")
    is DoubleArray -> value.joinToString(prefix = "doubleArrayOf(", postfix = ")")
    is CharArray -> value.joinToString(prefix = "charArrayOf(", postfix = ")")
    is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { stringify(it) }
    is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { stringify(it) }
//    is Boolean, is Number, is Char -> toString()
    else -> value.toString()
}

/**
 * Stringify a Map with recursive key/value rendering.
 */
fun stringifyMap(map: Map<*, *>): String =
    map.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
        "${stringify(k)}=${stringify(v)}"
    }


fun toHexString(byteArray: ByteArray): String = byteArray.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }