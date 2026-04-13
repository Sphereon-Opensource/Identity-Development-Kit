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

package com.sphereon.mdoc.data.device

import com.sphereon.cbor.CborString
import com.sphereon.cbor.StringLabel
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.data.ErrorCodeAlias
import com.sphereon.mdoc.oid4vp.IOid4VPPresentationDefinition
import com.sphereon.mdoc.oid4vp.Oid4VPPresentationDefinition
import com.sphereon.util.stringify
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.native.ObjCName

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
     *
     * Required when presenting. No null default on purpose!
     */
    val deviceSigned: DeviceSigned?,
    /**
     * If the device retrieval mdoc response structure does not include some data element or document
     * requested in the device retrieval mdoc request, an error code may be returned as part of the
     * documentErrors or errors structures.
     * If present, ErrorCodeAlias shall contain an error code according to 8.3.2.1.2.3.
     */
    val errors: Map<NameSpace, Map<DataElementIdentifier, ErrorCodeAlias>>? = null,
    val original: ByteArray?,
) {
    fun limitDisclosures(docRequest: DocRequest): IssuerSigned = docRequest.limitDisclosures(issuerSigned)

    fun limitDisclosureFromPresentationDefinition(
        presentationDefinition: IOid4VPPresentationDefinition,
        deviceSigned: DeviceSigned? = null,
    ): Document {
        val docRequest = Oid4VPPresentationDefinition.fromDTO(presentationDefinition).toDocRequest()
        require(docRequest.itemsRequest.docType == this.docType) {
            "Document request docType ${docRequest.itemsRequest.docType} does not match docType ${this.docType}"
        }
        return Document(docType = this.docType, issuerSigned = limitDisclosures(docRequest), deviceSigned = deviceSigned ?: this.deviceSigned, original = null)
    }

    fun toSingleDocDeviceResponse(presentationDefinition: IOid4VPPresentationDefinition): DeviceResponse {
        // device signing
        val docRequest = Oid4VPPresentationDefinition.fromDTO(presentationDefinition).toDocRequest()
        val issuerSigned = limitDisclosures(docRequest)
        val mdoc = Document(docType = this.docType, issuerSigned = issuerSigned, deviceSigned = deviceSigned, original = null)
        return DeviceResponse(documents = arrayOf(mdoc), original = null)
    }

    fun getNameSpaces() = issuerSigned.nameSpaces?.map { it.key }?.toTypedArray() ?: arrayOf()

    override fun toString(): String = "Document(docType=$docType, issuerSigned=$issuerSigned, deviceSigned=$deviceSigned, errors=${stringify(errors)}, original=${stringify(original)})"

    companion object {
        @JsStatic
        val DOC_TYPE = StringLabel("docType")

        @JsStatic
        val ISSUER_SIGNED = StringLabel("issuerSigned")

        @JsStatic
        val DEVICE_SIGNED = StringLabel("deviceSigned")

        @JsStatic
        val ERRORS = StringLabel("errors")
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
