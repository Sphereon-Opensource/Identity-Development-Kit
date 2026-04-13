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

package com.sphereon.mdoc.data.mso

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cborSerializer
import com.sphereon.crypto.core.cose.CoseSign1
import kotlinx.serialization.Serializable
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.HasFromCbor
import com.sphereon.cbor.HasFromCborWithOriginal
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.cbor.toCborUIntFromUint
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.cbor.HasToCbor
import com.sphereon.cbor.NumberLabel
import com.sphereon.util.stringify
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("MsoVersion", exact = true)
value class MsoVersion(private val value: String) : HasToCbor<CborString> {
    init {
        require(value == "1.0") { "Version must be '1.0' but was '$value' instead" }
    }

    override fun toCborStructure(): CborString = CborString(value)

    companion object Decoder : HasFromCbor<CborString, MsoVersion> {
        override fun fromCborStructure(structure: CborString): MsoVersion {
            return MsoVersion(structure.value)
        }
    }

    override fun toString(): String {
        return value
    }
}

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("DigestAlgorithm", exact = true)
value class DigestAlgorithm(private val alg: String) : HasToCbor<CborString> {
    override fun toCborStructure(): CborString = CborString(alg)
    override fun toString(): String {
        return alg
    }

    companion object Decoder : HasFromCbor<CborString, DigestAlgorithm> {
        override fun fromCborStructure(structure: CborString): DigestAlgorithm = DigestAlgorithm(structure.value)
    }
}


@JvmInline
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("DigestID", exact = true)
value class DigestID(private val value: UInt) : HasToCbor<CborUInt> {
    override fun toCborStructure(): CborUInt = value.toCborUIntFromUint()
    override fun toString(): String = value.toString()

    companion object Decoder : HasFromCbor<CborUInt, DigestID> {
        override fun fromCborStructure(structure: CborUInt): DigestID = DigestID(structure.value.toUInt())
    }

}


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("MobileSecurityObject", exact = true)
data class MobileSecurityObject(
    val version: MsoVersion = MsoVersion("1.0"),
    val digestAlgorithm: DigestAlgorithm,
    val valueDigests: Map<NameSpace, Map<DigestID, ByteArray>>,
    val deviceKeyInfo: DeviceKeyInfoCbor,
    val docType: DocType,
    val validityInfo: ValidityInfo,
    override val original: ByteArray?,
) : CborStructure<MobileSecurityObject, CborMap<StringLabel, CborItem<*>>>(CDDL.map, original = original) {
    override fun cborBuilder(): CborBuilder<MobileSecurityObject> = cborMapBuilder(this) {
        VERSION to CborString(version.toString())
        DIGEST_ALGORITHM to CborString(digestAlgorithm.toString())
        VALUE_DIGESTS to valueDigests
        DEVICE_KEY_INFO to deviceKeyInfo
        DOC_TYPE to CborString(docType.toString())
        VALIDITY_INFO to validityInfo
    }

    fun getKeyInfo() = deviceKeyInfo.toKeyInfo()


    companion object Decoder : HasFromCborWithOriginal<CborMap<StringLabel, CborItem<*>>, MobileSecurityObject> {
        val VERSION = StringLabel("version")
        val DIGEST_ALGORITHM = StringLabel("digestAlgorithm")
        val VALUE_DIGESTS = StringLabel("valueDigests")
        val DEVICE_KEY_INFO = StringLabel("deviceKeyInfo")
        val DOC_TYPE = StringLabel("docType")
        val VALIDITY_INFO = StringLabel("validityInfo")


        override fun fromCborStructure(structure: CborMap<StringLabel, CborItem<*>>): MobileSecurityObject {
            val valueDigests: ValueDigestsAlias = VALUE_DIGESTS.required(structure)
            return MobileSecurityObject(
                version = MsoVersion.Decoder.fromCborStructure(VERSION.required(structure)),
                digestAlgorithm = DigestAlgorithm.Decoder.fromCborStructure(DIGEST_ALGORITHM.required(structure)),
                valueDigests = valueDigests.value.map { (ns, map) -> NameSpace(ns.value) to map.value.map { (key, value) -> DigestID(key.value.toUInt()) to value.value }.toMap() }
                    .toMap(),
                deviceKeyInfo = DeviceKeyInfoCbor.Decoder.fromCborStructure(DEVICE_KEY_INFO.required(structure)),
                docType = DocType.Decoder.fromCborStructure(DOC_TYPE.required(structure)),
                validityInfo = ValidityInfo.Decoder.fromCborStructure(VALIDITY_INFO.required(structure)),
                original = null
            )
        }

        override fun fromCborStructureWithOriginal(structure: CborMap<StringLabel, CborItem<*>>, original: ByteArray?): MobileSecurityObject {
            return fromCborStructure(structure).copy(original = original)
        }

        @JsName("decodeCoseSign1")
        fun decodeCoseSign1(sign: CoseSign1<MobileSecurityObject>) =
            sign.payload?.value?.let { decodeCbor(it) }.also { println("MSO: ${it}") } ?: throw IllegalArgumentException("Payload is null for MSO, that is not allowed")

        @Suppress("UNCHECKED_CAST")
        override fun decodeCbor(bytes: ByteArray): MobileSecurityObject {
            val result: CborItem<*> = cborSerializer.decode(bytes)
            val decoded: CborMap<StringLabel, CborItem<*>> = when (result) {
                is CborMap<*, *> -> result as CborMap<StringLabel, CborItem<*>>
                is CborEncodedItem<*> -> result.data() as CborMap<StringLabel, CborItem<*>>
                else -> throw IllegalArgumentException("Can't decode MobileSecurityObject from ${result::class.simpleName}")
            }
            return fromCborStructureWithOriginal(decoded, bytes)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MobileSecurityObject

        if (version != other.version) return false
        if (digestAlgorithm != other.digestAlgorithm) return false
        if (valueDigests != other.valueDigests) return false
        if (deviceKeyInfo != other.deviceKeyInfo) return false
        if (docType != other.docType) return false
        if (validityInfo != other.validityInfo) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + digestAlgorithm.hashCode()
        result = 31 * result + valueDigests.hashCode()
        result = 31 * result + deviceKeyInfo.hashCode()
        result = 31 * result + docType.hashCode()
        result = 31 * result + validityInfo.hashCode()
        return result
    }

    override fun toString(): String {
        return "MobileSecurityObject(version=$version, digestAlgorithm=${digestAlgorithm}, valueDigests=${stringify(valueDigests.map { it.value.keys })}, deviceKeyInfo=$deviceKeyInfo, docType=$docType, validityInfo=$validityInfo, original=${
            stringify(
                original
            )
        })"
    }


}

typealias ValueDigestsAlias = CborMap<StringLabel, CborMap<NumberLabel, CborByteString>>