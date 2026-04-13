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

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.HasFromCborWithOriginal
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cborSerializer
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.util.stringify
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.oid4vp.IOid4VPPresentationDefinition
import com.sphereon.mdoc.oid4vp.Oid4VPPresentationDefinition
import com.sphereon.mdoc.data.ErrorCodeAlias
import com.sphereon.mdoc.data.mso.DeviceKeyInfoCbor
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.js.JsStatic

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Document", exact = true)
data class Document(
    /**
     * In the Document structure, the document type of the returned document is indicated by the
     * docType element. The document type shall match the document type as indicated in the issuer data
     *
     * authentication (see 9.1.2) and mdoc authentication structures (see 9.1.3). errors can contain error
     * codes for data elements that are not returned.
     */
    val docType: DocType,

    /**
     * IssuerSigned contains the mobile security object for issuer data authentication and the data elements
     * protected by issuer data authentication. nameSpaces contains the returned data elements as part of
     * their corresponding namespaces.
     */
    val issuerSigned: IssuerSigned,

    /**
     * DeviceSigned contains the mdoc authentication structure and the data elements protected by mdoc
     * authentication. nameSpaces contains the returned data elements as part of their corresponding
     * namespaces. nameSpaces is a mandatory element because the element is authenticated using mdoc
     * authentication. The DeviceNameSpaces structure can be an empty structure. The DeviceAuth structure
     * contains either the DeviceSignature or the DeviceMac element, both are defined in 9.1.3.
     */
    val deviceSigned: DeviceSigned?,  // required when presenting. No null default on purpose!

    /**
     * If the device retrieval mdoc response structure does not include some data element or document
     * requested in the device retrieval mdoc request, an error code may be returned as part of the
     * documentErrors or errors structures.
     * If present, ErrorCodeAlias shall contain an error code according to 8.3.2.1.2.3.
     */
    val errors: Map<NameSpace, Map<DataElementIdentifier, ErrorCodeAlias>>? = null,

    override val original: ByteArray?,
) : CborStructure<Document, CborMap<StringLabel, CborItem<*>>>(CDDL.map) {


    val MSO = issuerSigned.MSO

    val deviceKeyInfo: DeviceKeyInfoCbor
        get() = issuerSigned.deviceKeyInfo

    override fun cborBuilder(): CborBuilder<Document> = cborMapBuilder(this) {
        DOC_TYPE to docType
        ISSUER_SIGNED to issuerSigned.toCborStructure()
        optional(DEVICE_SIGNED, deviceSigned?.toCborStructure())
        // FIXME: Do not put null in. We have an issue with mapping the above errors
        optional(ERRORS, if (errors.isNullOrEmpty()) null else errors)
    }

    override fun encodeCbor(): ByteArray {
        if (original != null) {
            return original
        }
        return super.encodeCbor()
    }

    fun limitDisclosures(docRequest: DocRequest): IssuerSigned {
        return docRequest.limitDisclosures(issuerSigned)
    }

    fun limitDisclosureFromPresentationDefinition(
        presentationDefinition: IOid4VPPresentationDefinition,
        deviceSigned: DeviceSigned? = null,
    ): Document {
        val docRequest = Oid4VPPresentationDefinition.fromDTO(presentationDefinition).toDocRequest()
        if (docRequest.itemsRequest.docType != this.docType) {
            throw IllegalArgumentException("Document request docType ${docRequest.itemsRequest.docType} does not match docType ${this.docType}")
        }
        return Document(docType = this.docType, issuerSigned = limitDisclosures(docRequest), deviceSigned = deviceSigned ?: this.deviceSigned, original = null)
    }


    fun toSingleDocDeviceResponse(
        presentationDefinition: IOid4VPPresentationDefinition,
    ): DeviceResponse {
        // device signing
        val docRequest = Oid4VPPresentationDefinition.fromDTO(presentationDefinition).toDocRequest()
        val issuerSigned = limitDisclosures(docRequest)
        val mdoc = Document(docType = this.docType, issuerSigned = issuerSigned, deviceSigned = deviceSigned, original = null)
        return DeviceResponse(documents = arrayOf(mdoc), original = null)
    }

    fun getNameSpaces() = issuerSigned.nameSpaces?.map { it.key }?.toTypedArray() ?: arrayOf()
    override fun toString(): String {
        return "Document(docType=$docType, issuerSigned=$issuerSigned, deviceSigned=$deviceSigned, errors=${stringify(errors)}, original=${stringify(original)}, MSO=$MSO, deviceKeyInfo=$deviceKeyInfo)"
    }


    companion object: HasFromCborWithOriginal<CborMap<StringLabel, CborItem<*>>, Document> {
        @JsStatic
        val DOC_TYPE = StringLabel("docType")
        @JsStatic
        val ISSUER_SIGNED = StringLabel("issuerSigned")
        @JsStatic
        val DEVICE_SIGNED = StringLabel("deviceSigned")
        @JsStatic
        val ERRORS = StringLabel("errors")

        @JsStatic
        fun fromIssuerSigned(issuerSigned: IssuerSigned) = issuerSigned.toDocument()

        @Suppress("UNCHECKED_CAST")
        @JsStatic
        @JsName("fromDeviceResponse")
        fun fromDeviceResponse(items: CborArray<CborItem<*>>?): Array<Document>? {
            if (items == null || items.value.isEmpty()) {
                return null
            }
            return items.value.map { fromCborStructure(it as CborMap<StringLabel, CborItem<*>>) }.toTypedArray()

        }

        override fun fromCborStructure(structure: CborMap<StringLabel, CborItem<*>>) =
            Document(
                docType = DocType.Decoder.fromCborStructure(DOC_TYPE.required(structure)),
                issuerSigned = IssuerSigned.Decoder.fromCborStructure(ISSUER_SIGNED.required(structure)),
                deviceSigned = DEVICE_SIGNED.optional<CborMap<StringLabel, CborItem<*>>>(structure)?.let{DeviceSigned.fromCborStructure(it)},
                errors = ERRORS.optional(structure),
                original = null
            )

        override fun fromCborStructureWithOriginal(structure: CborMap<StringLabel, CborItem<*>>, original: ByteArray?): Document {
            return fromCborStructure(structure).copy(original = original)
        }

        override fun decodeCbor(bytes: ByteArray): Document = fromCborStructureWithOriginal(cborSerializer.decode(bytes), bytes)
    }
}



@OptIn(ExperimentalObjCName::class)
@ObjCName("DocumentWithKeyAlias", exact = true)
interface DocumentWithKeyAlias {
    val providerId: String
    val keyAlias: String
    val document: Document

    fun createKeyInfo(): KeyInfoType<CoseKeyType> = KeyInfo(providerId = providerId, alias = keyAlias)
}
