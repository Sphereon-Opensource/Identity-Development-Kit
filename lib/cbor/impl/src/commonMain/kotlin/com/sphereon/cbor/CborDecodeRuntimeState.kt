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

internal class CborDecodeRuntimeState(
    val config: CborDecoderConfig,
) {
    var currentDepth: Int = 0
        private set

    var itemCount: Int = 0
        private set

    fun enterContainer(): Boolean {
        currentDepth++
        return currentDepth <= config.maxDepth
    }

    fun exitContainer() {
        currentDepth--
    }

    fun incrementItemCount(): Boolean {
        itemCount++
        return itemCount <= config.maxItems
    }

    fun checkStringLength(length: Int): Boolean = length <= config.maxStringLength

    fun reset() {
        currentDepth = 0
        itemCount = 0
    }
}
