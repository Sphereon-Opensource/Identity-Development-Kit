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

package com.sphereon.mdoc.transfer

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborStructure
import kotlinx.serialization.Serializable
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.HasFromCbor
import com.sphereon.cbor.HasFromCborWithOriginal
import com.sphereon.cbor.HasToCbor
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cborViewArrayToCborItem
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.engagement.DeviceEngagement.Decoder.DEVICE_RETRIEVAL_METHODS
import com.sphereon.mdoc.engagement.DeviceEngagement.Decoder.PROTOCOL_INFO
import com.sphereon.mdoc.engagement.DeviceEngagement.Decoder.SECURITY
import com.sphereon.mdoc.engagement.DeviceEngagement.Decoder.SERVER_RETRIEVAL_METHOD
import com.sphereon.mdoc.engagement.DeviceEngagement.Decoder.VERSION
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("OriginInfoCategory", exact = true)
value class OriginInfoCategory(val category: UInt) : HasToCbor<CborUInt> {

    override fun toCborStructure(): CborUInt = CborUInt(category.toLong())

    companion object Decoder : HasFromCbor<CborUInt, OriginInfoCategory> {

        override fun fromCborStructure(structure: CborUInt): OriginInfoCategory = OriginInfoCategory(structure.value.toUInt())
    }

    override fun toString(): String {
        return category.toString()
    }
}

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("OriginInfoType", exact = true)
value class OriginInfoType(val infoType: UInt) : HasToCbor<CborUInt> {

    override fun toCborStructure(): CborUInt = CborUInt(infoType.toLong())

    companion object Decoder : HasFromCbor<CborUInt, OriginInfoType> {

        override fun fromCborStructure(structure: CborUInt): OriginInfoType = OriginInfoType(structure.value.toUInt())
    }

    override fun toString(): String {
        return infoType.toString()
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("OriginInfoDetails", exact = true)
data class OriginInfoDetails(val map: Map<String, Any?>) : Map<String, Any?> by map {
    companion object {
        const val DOMAIN = "domain"
    }
}


typealias OriginInfos = Array<OriginInfo>
data class OriginInfo(val cat: OriginInfoCategory, val type: OriginInfoType, val details: OriginInfoDetails? = null, override val original: ByteArray?): CborStructure<OriginInfo, CborMap<StringLabel, CborItem<*>>>(
    CDDL.map, original = original) {
    override fun cborBuilder(): CborBuilder<OriginInfo> = cborMapBuilder(this) {
        CAT to cat
        TYPE to type
        optional(DETAILS, details)
    }

    companion object Decoder : HasFromCborWithOriginal<CborMap<StringLabel, CborItem<*>>, OriginInfo> {

        val CAT = StringLabel("cat")
        val TYPE = StringLabel("type")
        val DETAILS = StringLabel("details")

        override fun fromCborStructureWithOriginal(
            structure: CborMap<StringLabel, CborItem<*>>,
            original: ByteArray?
        ): OriginInfo {
            

            val details = DETAILS.optional<CborMap<StringLabel, CborItem<*>>>(structure)?.let { map ->
                OriginInfoDetails(map.value.entries.associate { (key, value) ->
                    key.toString() to (value.toString() ?: "")
                })
            }
            return OriginInfo(
                cat = OriginInfoCategory.fromCborStructure(CAT.required(structure)),
                type = OriginInfoType.fromCborStructure(TYPE.required(structure)),
                details = details,
                original = original
            )
        }

        override fun fromCborStructure(structure: CborMap<StringLabel, CborItem<*>>): OriginInfo {
            return fromCborStructureWithOriginal(structure, null)
        }

    }
}