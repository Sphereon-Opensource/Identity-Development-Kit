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

package com.sphereon.mdoc.transfer.device

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.HasFromCborWithOriginal
import com.sphereon.cbor.dsl.cborArrayBuilder
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRetrievalMethod", exact = true)
data class DeviceRetrievalMethod(
    val type: DeviceRetrievalMethodType,
    val version: DeviceRetrievalMethodVersion = DeviceRetrievalMethodVersion(1u),
    val retrievalOptions: DeviceRetrievalOptions,
    override val original: ByteArray? = null
) : CborStructure<DeviceRetrievalMethod, CborArray<CborItem<*>>>(CDDL.list, original = original) {

    override fun cborBuilder(): CborBuilder<DeviceRetrievalMethod> = cborArrayBuilder(this) {
        add(type.toCborItem())
        add(version.toCborItem())
        add(retrievalOptions.toCborStructure())
    }


    companion object Decoder : HasFromCborWithOriginal<CborArray<CborItem<*>>, DeviceRetrievalMethod> {
        @Suppress("UNCHECKED_CAST")
        @JsStatic
        fun fromDeviceEngagementCborStructure(items: CborArray<CborItem<*>>?): Array<DeviceRetrievalMethod>? {
            if (items == null || items.value.isEmpty()) {
                return null
            }
            return items.value.map { fromCborStructure(it as CborArray<CborItem<*>>) }.toTypedArray()

        }

        override fun fromCborStructure(structure: CborArray<CborItem<*>>): DeviceRetrievalMethod {
            val type: CborUInt = structure.required(0)
            val retrievalOptions = when (DeviceRetrievalMethodType.fromCborStructure(type)) {
                DeviceRetrievalMethodType.NFC -> NfcOptions.Decoder.fromCborStructure(structure.required(2))
                DeviceRetrievalMethodType.BLE -> BleOptions.Decoder.fromCborStructure(structure.required(2))
                DeviceRetrievalMethodType.WIFI_WARE -> WifiAwareOptions.Decoder.fromCborStructure(structure.required(2))
                DeviceRetrievalMethodType.WEBSITE -> RestApiOptions.Decoder.fromCborStructure(structure.required(2))
                DeviceRetrievalMethodType.OID4VP -> Oid4vpOptions.Decoder.fromCborStructure(structure.required(2))
            }
            return DeviceRetrievalMethod(
                type = DeviceRetrievalMethodType.fromCborStructure(type),
                version = DeviceRetrievalMethodVersion.fromCborStructure(structure.required(1)),
                retrievalOptions = retrievalOptions,
                original = null,
            )
        }



        override fun fromCborStructureWithOriginal(structure: CborArray<CborItem<*>>, original: ByteArray?): DeviceRetrievalMethod = fromCborStructure(structure).copy(original = original)


        override fun decodeCbor(bytes: ByteArray): DeviceRetrievalMethod = fromCborStructureWithOriginal(Cbor.decode(bytes), bytes)
    }

    override fun toString(): String {
        return "DeviceRetrievalMethod(type=${type.name}, version=${version.version}, retrievalOptions=$retrievalOptions)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DeviceRetrievalMethod

        if (type != other.type) return false
        if (version != other.version) return false
        if (retrievalOptions != other.retrievalOptions) return false
        if (!original.contentEquals(other.original)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + retrievalOptions.hashCode()
        result = 31 * result + (original?.contentHashCode() ?: 0)
        return result
    }
}