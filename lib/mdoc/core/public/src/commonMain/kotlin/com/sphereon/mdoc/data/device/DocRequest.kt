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

import com.sphereon.cbor.StringLabel
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.mdoc.transfer.reader.ReaderAuthenticationBytes
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

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
    val original: ByteArray? = null,
) {
    fun getNameSpaces(): Array<NameSpace> = itemsRequest.nameSpaces.map { it.key }.toTypedArray()

    fun getDocType(): DocType = itemsRequest.docType

    fun getIdentifiers(nameSpace: NameSpace) = itemsRequest.getIdentifiers(nameSpace)

    fun limitDisclosures(issuerSigned: IssuerSigned): IssuerSigned = issuerSigned.limitDisclosures(this)

    override fun toString(): String = "DocRequest(itemsRequest=$itemsRequest, readerAuth=$readerAuth, original=${original?.encodeToHex()})"

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Builder", exact = true)
    data class Builder(
        var deviceItemsRequestBuilder: DeviceItemsRequest.Builder? = null,
        var readerAuth: COSE_Sign1<ReaderAuthenticationBytes>? = null,
    ) {
        init {
            if (this.deviceItemsRequestBuilder == null) {
                this.deviceItemsRequestBuilder = DeviceItemsRequest.Builder(this)
            }
        }

        @JsName("docType")
        fun docType(
            docType: DocType,
            requestInfo: Map<String, Any>? = null,
        ): DeviceItemsRequest.Builder = builder().withDocType(docType).withRequestInfo(requestInfo)

        fun withDocRequestInfo(docRequestInfo: DocRequestInfo?) = apply {
            builder().withDocRequestInfo(docRequestInfo)
        }

        @JsName("withReaderAuth")
        fun withReaderAuth(readerAuth: COSE_Sign1<ReaderAuthenticationBytes>) = apply { this.readerAuth = readerAuth }

        fun build(): DocRequest {
            val itemsRequestDataItem = builder().build()
            return DocRequest(itemsRequestDataItem, readerAuth)
        }

        override fun toString(): String = "Builder(deviceItemsRequestBuilder=$deviceItemsRequestBuilder, readerAuth=$readerAuth)"

        private fun builder() = deviceItemsRequestBuilder ?: throw IllegalArgumentException("A builder is not initialized!")
    }

    companion object {
        @JsStatic
        @JvmStatic
        val ITEMS_REQUEST = StringLabel("itemsRequest")

        @JsStatic
        @JvmStatic
        val READER_AUTH = StringLabel("readerAuth")
    }
}
