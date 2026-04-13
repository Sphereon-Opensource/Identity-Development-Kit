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

@file:Suppress("SERIALIZER_TYPE_INCOMPATIBLE")

package com.sphereon.cbor


import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.serialization.json.JsonElement
import com.sphereon.core.api.decodeFromHex
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.js.JsStatic


@JsExportCompat
class CborHexEncodedItem(hex: String) : CborByteString(
    hex.decodeFromHex()
)

/**
 * CBOR Tag 24 support (CBOR data item)
 *
 * From https://datatracker.ietf.org/doc/html/rfc7049#section-2.4.4.1 Encoded CBOR Data Item
 *
 * Sometimes it is beneficial to carry an embedded CBOR data item that
 *    is not meant to be decoded immediately at the time the enclosing data
 *    item is being parsed.  Tag 24 (CBOR data item) can be used to tag the
 *    embedded byte string as a data item encoded in CBOR format.
 *
 * We always expect a BytesString or ByteArray. That is the value that either comes in from remote, or how it will be encoded. Whenever it comes from remote, we do not expect data to be passed in!
 */
@JsExportCompat
open class CborEncodedItem<Type : Any>(
    value: CborByteString,
) : CborItem<CborTagged<cddl_bstr>>(CborTagged(24, value), CDDL.bstr) {
    lateinit var _data: Type
    val isOriginal: Boolean = !::_data.isInitialized

    @JsName("fromBytes")
    constructor(value: ByteArray, data: Type? = null) : this(CborByteString(value), data)

    internal constructor(value: CborByteString, data: Type? = null,) : this(value) {
        if (data != null) {
            this._data = data
        }
    }



    override fun toJsonSimple(): JsonElement {
        // TODO. Do we want to use the decoded value?
        return value.toJsonSimple()
    }

    override fun toJsonWithCDDL(): JsonElement {
        return super.toJsonWithCDDL()
    }

    fun wasEncoded() = isOriginal

    fun <NewType: Any> copy(data: NewType?): CborEncodedItem<NewType> {
        return CborEncodedItem(value = this.value.value, data)
    }


    fun isDataInitialized(): Boolean = this::_data.isInitialized

    fun data(fromCborItemCallback: ((bytes: ByteArray) -> Type)? = null): Type {
        if (this::_data.isInitialized) return _data
        if (fromCborItemCallback == null) {
            this._data = Cbor.decode(value.taggedItem.value) as Type
        } else {
            this._data = fromCborItemCallback(Cbor.decode(value.taggedItem.value))
        }
        return _data
    }


    companion object {
        @JsStatic
        fun cborSerializeStructureOrItem(deserializedValue: Any?): CborByteString {
            return when (deserializedValue) {
                is ByteArray -> CborByteString(deserializedValue)
                is CborByteString -> deserializedValue
                is CborEncodedItem<*> -> CborByteString(deserializedValue.value.value)
                is CborItem<*> -> CborByteString(cborSerializer.encode(deserializedValue))
                is CborStructure<*, *> -> CborByteString(deserializedValue.encodeCbor())
                else -> throw IllegalArgumentException("A CborEncoded item or CborStructure is required")
            }
        }

        @JsStatic
        fun <Type: Any> fromData(
            data: Type,
        ): CborEncodedItem<Type> {
            return CborEncodedItem(cborSerializeStructureOrItem(data), data)
        }

        @JsStatic
        fun <Type: Any> toData(encoded: CborEncodedItem<Type>): Type {
            return encoded.data()
        }


    }


    override fun encode(builder: ByteStringBuilder) {
        value.encode(builder)
        /*if (decodedValue is CborStructure<*, *, *>) {
            return CborTagged(
                CborTagged.ENCODED_CBOR,
                (decodedValue as CborStructure<*, *, *>).toCborItem() as CborItem<*>
            ).encode(builder)
        }

        return CborTagged(CborTagged.ENCODED_CBOR, cborSerializer.decode(value.value) as CborItem<*>).encode(builder)*/
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        return value.value.contentEquals((other as CborEncodedItem<*>).value.value)
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        return "CborEncodedItem(_data=${data()}, value=${value}, isOriginal=$isOriginal)"
    }

}

