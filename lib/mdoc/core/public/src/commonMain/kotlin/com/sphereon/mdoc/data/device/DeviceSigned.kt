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

package com.sphereon.mdoc.data.device

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.HasFromCbor
import com.sphereon.cbor.HasFromCborWithOriginal
import com.sphereon.cbor.HasToCbor
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.cbor.toCborItem
import com.sphereon.util.stringify
import com.sphereon.core.compat.JsExportCompat


/**
 * Device Signed are essentially self-asserted claims/dataElements, contrary to issuer signed items, which are externally asserted
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceSigned", exact = true)
data class DeviceSigned(
    val nameSpaces: DeviceNameSpaces = DeviceNameSpaces(), val deviceAuth: DeviceAuth, override val original: ByteArray?
) : CborStructure<DeviceSigned, CborMap<StringLabel, CborItem<*>>>(cddl = CDDL.map, original = original) {
    override fun cborBuilder(): CborBuilder<DeviceSigned> = cborMapBuilder(this) {
        NAME_SPACES to CborEncodedItem.fromData(nameSpaces.toCborStructure())
        DEVICE_AUTH to deviceAuth
    }

    override fun toString(): String {
        return "DeviceSigned(nameSpaces=$nameSpaces, deviceAuth=$deviceAuth, original=${stringify(original)})"
    }


    companion object: HasFromCborWithOriginal<CborMap<StringLabel, CborItem<*>>, DeviceSigned> {
        val NAME_SPACES = StringLabel("nameSpaces")

        val DEVICE_AUTH = StringLabel("deviceAuth")

        override fun fromCborStructure(structure: CborMap<StringLabel, CborItem<*>>): DeviceSigned {
            val nameSpaces = DeviceNameSpaces.fromCborStructure(NAME_SPACES.required(structure))

            return DeviceSigned(
                nameSpaces = nameSpaces, deviceAuth = DeviceAuth.fromCborStructure(DEVICE_AUTH.required(structure)), original = null
            )
        }

        override fun fromCborStructureWithOriginal(structure: CborMap<StringLabel, CborItem<*>>, original: ByteArray?): DeviceSigned {
            return fromCborStructure(structure).copy(original = original)
        }

    }
}


@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceNameSpaces", exact = true)
data class DeviceNameSpaces(val value: Map<NameSpace, DeviceSignedItems> = mutableMapOf()) : HasToCbor<CborMap<CborString, CborMap<CborString, CborItem<*>>>> {
    constructor(vararg items: DeviceNameSpace) : this(items.toMap())

    @Suppress("UNCHECKED_CAST")
    override fun toCborStructure(): CborMap<CborString, CborMap<CborString, CborItem<*>>> = value.toCborItem() as CborMap<CborString, CborMap<CborString, CborItem<*>>>
    override fun toString(): String {
        return "DeviceNameSpaces(value=${stringify(value)})"
    }

    companion object: HasFromCbor<CborMap<CborString, CborMap<CborString, CborItem<*>>>, DeviceNameSpaces> {
        override fun fromCborStructure(structure: CborMap<CborString, CborMap<CborString, CborItem<*>>>): DeviceNameSpaces {
            return DeviceNameSpaces(structure.value.map { (key, value) -> NameSpace(key.value) to DeviceSignedItems.fromCborStructure(value) }.toMap())
        }
    }



}


typealias DeviceNameSpace = Pair<NameSpace, DeviceSignedItems>

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceSignedItems", exact = true)
data class DeviceSignedItems(val value: Map<DataElementIdentifier, Any> = mutableMapOf()) : HasToCbor<CborMap<CborString, CborItem<*>>> {
    constructor(vararg items: DeviceSignedItem) : this(items.toMap())



    @Suppress("UNCHECKED_CAST")
    override fun toCborStructure(): CborMap<CborString, CborItem<*>> = value.toCborItem() as CborMap<CborString, CborItem<*>>
    override fun toString(): String {
        return "DeviceSignedItems(value=${stringify(value)})"
    }

    companion object: HasFromCbor<CborMap<CborString, CborItem<*>>, DeviceSignedItems> {
        override fun fromCborStructure(structure: CborMap<CborString, CborItem<*>>): DeviceSignedItems {
            return DeviceSignedItems(structure.value.map { (key, value) -> DataElementIdentifier(key.value) to value.value as Any}.toMap())
        }
    }
}
typealias DeviceSignedItem = Pair<DataElementIdentifier, Any>