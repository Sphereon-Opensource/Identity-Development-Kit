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

import com.sphereon.core.compat.JsExportCompat


val cborSerializer = CborSupport.serializer


@JsExportCompat
object CborSupport {
    val serializer by lazy {
        return@lazy Cbor
    }

    fun <Type> itemFromValue(value: Type, cddl: CDDL): CborItem<Type> {
        TODO()
//        return cddl.newCborItem(value)
    }

    fun <Type> itemToValue(value: CborItem<Type>): Type? {
        return value.value
    }

    fun <Type> itemToByteArray(value: CborItem<Type>): ByteArray = serializer.encode(value)
    fun <Type> itemFromByteArray(value: ByteArray): CborItem<Type> = serializer.decode(value)

    fun <Type: Any> dataItemFromValue(bytes: ByteArray, value: Type? = null): CborEncodedItem<Type> {
        return CborEncodedItem(CborByteString(bytes))
    }

    fun <Type : Any> dataItemToValue(value: CborEncodedItem<Type>): Type {
        return CborEncodedItem.toData(value)
    }

    fun <Type: Any> dataItemToByteArray(
        value: CborEncodedItem<Type>,
    ): ByteArray = serializer.encode(value)

    fun <Type: Any> dataItemFromByteArray(value: ByteArray): CborEncodedItem<Type> =
        serializer.decode(value)

}
