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

import com.sphereon.cbor.CborItem
import com.sphereon.core.compat.JsExportCompat

/**
 * CBOR data item builder.
 */
@JsExportCompat
class CborBuilder<out T: Any?>(private val item: CborItem<*>, private val subject:T?) {
    /**
     * Builds the CBOR data items.
     *
     * @return a [CborItem<Any>]
     */
    fun build(): CborItem<*> = item

    fun subject(): T? = subject

    fun encodedBuild(): ByteArray = Cbor.encode(build())
}
