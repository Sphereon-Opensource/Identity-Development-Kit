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

import kotlinx.serialization.Serializable
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborBool
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.HasFromCbor
import com.sphereon.cbor.HasFromCborWithOriginal
import com.sphereon.cbor.HasToCbor
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.cbor.toCborBool
import com.sphereon.cbor.toCborString
import com.sphereon.util.stringify
import com.sphereon.mdoc.data.DataElement
import com.sphereon.mdoc.data.DataElementDef
import com.sphereon.mdoc.data.mdl.Mdl.MDL_NAMESPACE
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic
import kotlin.jvm.JvmInline


@JvmInline
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("DocType", exact = true)
value class DocType(private val dt: String) : HasToCbor<CborString> {
    init {
        check(dt.isNotEmpty()) { "DocTypeAlias cannot be empty" }
    }


    override fun toCborStructure(): CborString = dt.toCborString()
    override fun toString(): String = dt


    companion object Decoder : HasFromCbor<CborString, DocType> {
        override fun fromCborStructure(structure: CborString): DocType = DocType(structure.value)
    }
}


@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("NameSpace", exact = true)
value class NameSpace(private val ns: String) : HasToCbor<CborString> {
    init {
        check(ns.isNotEmpty()) { "NameSpace cannot be empty" }
    }

    override fun toCborStructure(): CborString = ns.toCborString()

    override fun toString(): String = ns

    companion object Decoder : HasFromCbor<CborString, NameSpace> {
        override fun fromCborStructure(structure: CborString): NameSpace = NameSpace(structure.value)
    }

}


@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("DataElementIdentifier", exact = true)
value class DataElementIdentifier(private val identifier: String) : HasToCbor<CborString> {
    // FIXME: Reenable this check once customer fixed their pyropass mdoc device request
   /* init {
        check(identifier.isNotEmpty()) { "DataElementIdentifier cannot be empty" }
    }*/

    override fun toCborStructure(): CborString = identifier.toCborString()


    override fun toString(): String = identifier

    companion object Decoder : HasFromCbor<CborString, DataElementIdentifier> {
        override fun fromCborStructure(structure: CborString): DataElementIdentifier = DataElementIdentifier(structure.value)
    }

}

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("IntentToRetain", exact = true)
value class IntentToRetain(private val intentToRetain: Boolean) : HasToCbor<CborBool> {
    override fun toCborStructure(): CborBool = intentToRetain.toCborBool()

    companion object Decoder : HasFromCbor<CborBool, IntentToRetain> {
        override fun fromCborStructure(structure: CborBool): IntentToRetain = IntentToRetain(structure.value)
    }

    override fun toString(): String {
        return intentToRetain.toString()
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceItemsRequest", exact = true)
data class DeviceItemsRequest(
    /**
     * docType is the requested document type
     */
    val docType: DocType,

    /**
     * NameSpaces contains the requested data elements and the namespace they belong to.
     */
    val nameSpaces: Map<NameSpace, Map<DataElementIdentifier, IntentToRetain>>,

    /**
     * requestInfo may be used by the mdoc reader to provide additional information. This document does
     * not define any key-value pairs for use in requestInfo. An IA infrastructure shall ignore any key-value
     * pairs that it is not able to interpret.
     */
    val requestInfo: Map<String, Any>? = null,

    override val original: ByteArray? = null,

    ) : CborStructure<DeviceItemsRequest, CborMap<StringLabel, CborItem<*>>>(CDDL.map) {
    companion object Decoder : HasFromCborWithOriginal<CborMap<StringLabel, CborItem<*>>, DeviceItemsRequest> {
        @JsStatic
        val DOC_TYPE = StringLabel("docType")

        @JsStatic
        val NAME_SPACES = StringLabel("nameSpaces")

        @JsStatic
        val REQUEST_INFO = StringLabel("requestInfo")

        override fun fromCborStructureWithOriginal(structure: CborMap<StringLabel, CborItem<*>>, original: ByteArray?): DeviceItemsRequest {
            return fromCborStructure(structure).copy(original = original)
        }

        override fun fromCborStructure(structure: CborMap<StringLabel, CborItem<*>>): DeviceItemsRequest {
            val ns: CborMap<StringLabel, CborMap<StringLabel, CborBool>> = NAME_SPACES.required(structure)
            val nameSpaces = ns.value.map { (nsLabel, elements) ->
                NameSpace.Decoder.fromCborStructure(nsLabel.value.toCborString()) to elements.value.map { (identifierLabel, intentToRetain) ->
                    DataElementIdentifier.Decoder.fromCborStructure(
                        identifierLabel.value.toCborString()
                    ) to IntentToRetain.Decoder.fromCborStructure(intentToRetain)
                }.toMap()
            }.toMap()

            val reqInfo = REQUEST_INFO.optional<CborMap<StringLabel, CborItem<*>>>(structure)?.value?.map { (key, value) -> key.value to value.value!! }?.toMap()

            return DeviceItemsRequest(
                docType = DocType.Decoder.fromCborStructure(DOC_TYPE.required(structure)),
                nameSpaces = nameSpaces,
                requestInfo = reqInfo,
            )
        }

        override fun decodeCbor(bytes: ByteArray): DeviceItemsRequest = fromCborStructureWithOriginal(Cbor.decode(bytes), bytes)

    }

    override fun cborBuilder(): CborBuilder<DeviceItemsRequest> {
        val nsCbor = CborMap(mutableMapOf(* nameSpaces.map { (ns, identifiers) ->
            ns.toCborStructure() to CborMap(mutableMapOf(* identifiers.map { (identifier, intentToRetain) -> identifier.toCborStructure() to intentToRetain.toCborStructure() }
                .toTypedArray()))
        }.toTypedArray()))

        return cborMapBuilder(this) {
            DOC_TYPE to docType
            NAME_SPACES to nsCbor
            optional(REQUEST_INFO, requestInfo)
        }
    }

    fun getNameSpaces(): Array<NameSpace> = nameSpaces.keys.toTypedArray()

    fun getIdentifiers(nameSpace: NameSpace): Map<DataElementIdentifier, IntentToRetain> {
        return nameSpaces[nameSpace]?.toMap() ?: emptyMap()
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceItemsRequest) return false

        if (docType != other.docType) return false
        if (nameSpaces != other.nameSpaces) return false
        if (requestInfo != other.requestInfo) return false

        return true
    }

    override fun hashCode(): Int {
        var result = docType.hashCode()
        result = 31 * result + nameSpaces.hashCode()
        result = 31 * result + (requestInfo?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "DeviceItemsRequest(docType=$docType, nameSpaces=${stringify(nameSpaces)}, requestInfo=${stringify(requestInfo)})"
    }


    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Builder", exact = true)
    data class Builder(
        var docRequestBuilder: DocRequest.Builder? = null,
        private var docType: DocType? = null,
        private var requestInfo: Map<String, Any>? = null,
    ) {

        private val nameSpaceBuilders: MutableMap<NameSpace, DeviceRequestNameSpace.Builder> = mutableMapOf()

        init {
            if (docRequestBuilder == null) {
                this.docRequestBuilder = docRequestBuilder(this)
            }
        }


        fun withDocType(docType: DocType) = apply { this.docType = docType }
        fun withRequestInfo(requestInfo: Map<String, Any>?) = apply { this.requestInfo = requestInfo }

        fun nameSpace(nameSpace: NameSpace): DeviceRequestNameSpace.Builder {
            val builder = DeviceRequestNameSpace.Builder(this, nameSpace)
            addUsingBuilder(builder)
            return builder
        }
        fun addUsingDefinitions(vararg definitions: DataElementDef) = apply {
            definitions.forEach { addUsingElements(it.nameSpace, DataElement.fromDefinition(it)) }
        }

        fun addUsingElements(nameSpace: NameSpace, vararg elements: DataElement) = apply {
            getOrPut(nameSpace).addElements(*elements)
        }

        fun add(nameSpace: NameSpace, identifier: DataElementIdentifier, intentToRetain: IntentToRetain = IntentToRetain(false)) = apply {
            getOrPut(nameSpace).add(identifier, intentToRetain)
        }

        fun addUsingBuilder(vararg builders: DeviceRequestNameSpace.Builder) = apply {
            builders.forEach { this.nameSpaceBuilders[it.nameSpace] = it }
        }


        private fun getOrPut(nameSpace: NameSpace): DeviceRequestNameSpace.Builder {
            return nameSpaceBuilders.getOrPut(
                nameSpace
            ) { DeviceRequestNameSpace.Builder(this, nameSpace) }
        }


        fun build(): DeviceItemsRequest {
            if (docType == null) {
                throw IllegalArgumentException("DocTypeAlias cannot be null")
            }
            val nameSpacePairs = nameSpaceBuilders.map {
                Pair<NameSpace, Map<DataElementIdentifier, IntentToRetain>>(
                    it.key, it.value.dataElements
                )
            }
            return DeviceItemsRequest(docType!!, mutableMapOf(*nameSpacePairs.toTypedArray()))
        }


        fun buildDocRequest(): DocRequest {
            return docRequestBuilder?.build() ?: throw IllegalArgumentException("Cannot build document as builder is null")
        }

        override fun toString(): String {
            // Note: We don't include docRequestBuilder to avoid circular reference with DocRequest.Builder.toString()
            return "Builder(docType=$docType, requestInfo=${stringify(requestInfo)}, nameSpaceBuilders=${stringify(nameSpaceBuilders)})"
        }


    }


}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRequestNameSpace", exact = true)
data class DeviceRequestNameSpace(val nameSpace: NameSpace, val dataElements: MutableMap<DataElementIdentifier, IntentToRetain>) {

    class Builder(
        val itemsRequestBuilder: DeviceItemsRequest.Builder? = null,
        val nameSpace: NameSpace = MDL_NAMESPACE,
        val dataElements: MutableMap<DataElementIdentifier, IntentToRetain> = mutableMapOf(),
    ) {

        fun add(identifier: DataElementIdentifier, intentToRetain: IntentToRetain = IntentToRetain(false)) = apply {
            dataElements[identifier] = intentToRetain
        }

        fun addElements(vararg elements: DataElement) = apply {
            elements.map { add(it.identifier, it.intentToRetain) }
        }

        fun end(): DeviceItemsRequest.Builder {
            if (itemsRequestBuilder == null) {
                throw IllegalArgumentException("NameSpaces Builder cannot be null when calling end")
            }
            return itemsRequestBuilder
        }

        fun build() = DeviceRequestNameSpace(nameSpace, dataElements)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceRequestNameSpace) return false

        if (nameSpace != other.nameSpace) return false
        if (dataElements != other.dataElements) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nameSpace.hashCode()
        result = 31 * result + dataElements.hashCode()
        return result
    }

    override fun toString(): String {
        return "DeviceRequestNameSpace(nameSpace=$nameSpace, dataElements=${stringify(dataElements)})"
    }
}
