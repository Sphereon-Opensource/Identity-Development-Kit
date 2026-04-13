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
import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborTagged
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.HasFromCborWithOriginal
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cborSerializer
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.core.api.encodeToHex
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.mdoc.transfer.reader.ReaderAuthenticationBytes
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.js.JsStatic

typealias docRequestBuilder = DocRequest.Builder

/**
 * 8.3.2.1.2.1 Device retrieval mdoc request
 *
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DocRequest", exact = true)
data class DocRequest(
    /**
     * ItemRequestBytes contains the ItemsRequest structure as a tagged (24) CBOR bytestring data item.
     */
    val itemsRequest: DeviceItemsRequest,

    /**
     * ReaderAuth is used for mdoc reader authentication as defined in 9.1.4.
     */
    val readerAuth: COSE_Sign1<ReaderAuthenticationBytes>? = null,
    override val original: ByteArray? = null,
) : CborStructure<DocRequest, CborMap<StringLabel, CborItem<*>>>(CDDL.map) {

    fun getNameSpaces(): Array<NameSpace> = itemsRequest.nameSpaces.map { it.key }.toTypedArray()
    fun getDocType(): DocType = itemsRequest.docType

    fun getIdentifiers(nameSpace: NameSpace) = itemsRequest.getIdentifiers(nameSpace)

    fun limitDisclosures(issuerSigned: IssuerSigned): IssuerSigned = issuerSigned.limitDisclosures(this)



    companion object: HasFromCborWithOriginal<CborMap<StringLabel, CborItem<*>>, DocRequest> {

        @JsStatic
        val ITEMS_REQUEST = StringLabel("itemsRequest")

        @JsStatic
        val READER_AUTH = StringLabel("readerAuth")

        override fun fromCborStructure(structure: CborMap<StringLabel, CborItem<*>>): DocRequest {
            val cborTagged: CborEncodedItem<CborMap<StringLabel, CborItem<*>>> = ITEMS_REQUEST.required(structure)
            val cborMap: CborMap<StringLabel, CborItem<*>> = cborTagged.data()
            return DocRequest(
                DeviceItemsRequest.Decoder.fromCborStructure(cborMap),
                READER_AUTH.optional<CborArray<CborItem<*>>>(structure)?.let { CoseSign1.fromCborItem(it) },
                original = null,
            )
        }

        override fun fromCborStructureWithOriginal(structure: CborMap<StringLabel, CborItem<*>>, original: ByteArray?): DocRequest {
            return fromCborStructure(structure).copy(original = original)
        }

        override fun decodeCbor(bytes: ByteArray): DocRequest = fromCborStructure(Cbor.decode(bytes))
    }

    override fun cborBuilder(): CborBuilder<DocRequest> = cborMapBuilder(this) {
        putTagged(ITEMS_REQUEST, CborTagged.ENCODED_CBOR, CborByteString(cborSerializer.encode(itemsRequest.toCborStructure())))
        optional(READER_AUTH, readerAuth?.toCborStructure())
    }

    override fun toString(): String {
        return "DocRequest(itemsRequest=$itemsRequest, readerAuth=$readerAuth, original=${original?.encodeToHex()})"
    }


    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Builder", exact = true)
    data class Builder(
        var deviceItemsRequestBuilder: DeviceItemsRequest.Builder? = null,
        var readerAuth: COSE_Sign1<ReaderAuthenticationBytes>? = null,
    ) {

        private fun builder() =
            deviceItemsRequestBuilder ?: throw IllegalArgumentException("A builder is not initialized!")

        init {
            if (this.deviceItemsRequestBuilder == null) {
                this.deviceItemsRequestBuilder = DeviceItemsRequest.Builder(this)
            }
        }

        @JsName("docType")
        fun docType(docType: DocType, requestInfo: Map<String, Any>? = null): DeviceItemsRequest.Builder {
            return builder().withDocType(docType).withRequestInfo(requestInfo)

        }

        @JsName("withReaderAuth")
        fun withReaderAuth(readerAuth: COSE_Sign1<ReaderAuthenticationBytes>) =
            apply { this.readerAuth = readerAuth }


        fun build(): DocRequest {
            val itemsRequestDataItem = builder().build()
            return DocRequest(itemsRequestDataItem, readerAuth)
        }

        override fun toString(): String {
            return "Builder(deviceItemsRequestBuilder=$deviceItemsRequestBuilder, readerAuth=$readerAuth)"
        }


    }

}
