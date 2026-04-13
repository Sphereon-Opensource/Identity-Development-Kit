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

package com.sphereon.mdoc.data


import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cddl_bstr
import com.sphereon.cbor.cddl_int
import com.sphereon.cbor.cddl_tstr
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.DocumentError


/**
 * 8.3.1 Data model
 * The CDDL definitions for DocTypeAlias, NameSpaceAlias, DataElementIdentifierAlias and DataElementValueAlias are common
 * across different data retrieval methods and the applicable security mechanisms. The following CDDL
 * definitions shall be applied to the CDDL structures defined in Clause 8 and Clause 9:
 * DocTypeAlias = tstr
 * NameSpaceAlias = tstr
 * DataElementIdentifierAlias = tstr ; Data element identifier
 * DataElementValueAlias = any ; Data element value
 */
typealias DocTypeAlias = CborString
typealias NameSpaceAlias = StringLabel
typealias DataElementIdentifierAlias = CborString
typealias DataElementValueAlias<Type> = CborItem<Type>


/**
 * 8.3.2.1.2.1 Device retrieval mdoc request
 *
 * #6.24(bstr .cbor ItemsRequests
 */
typealias ItemsRequestBytesAlias = cddl_bstr


//fixme: This needs to be an object not a map
/**
 * requestInfo may be used by the mdoc reader to provide additional information. This document does
 * not define any key-value pairs for use in requestInfo. An mdoc shall ignore any key-value pairs that it
 * is not able to interpret.
 */
typealias RequestInfoAlias = CborMap<CborString, CborString>
//fixme: This needs to be an object not a map
/**
 * NameSpaces contains the requested data elements and the namespace they belong to.
 */
//typealias NameSpacesOrig = List<NameSpaceDataElements>


/*
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("NameSpaceDataElements", exact = true)
data class NameSpaceDataElements(val nameSpace: CborString, val dataElements: DataElements) {

    */
/**
 * Although Kotlin typically does not need builders given the named params, we want to make it a bit easier
 * cross-platform and not to have developers take into account what the serialization structure is. We cannot use overloads because of JS
 *//*

    class Builder(
        val nameSpacesBuilder: DeviceItemsRequest.NameSpacesBuilder,
        var nameSpace: NameSpaceAlias? = MDL_NAMESPACE_CBOR,
        val dataElements: Array<DataElementCbor> = arrayOf()
    ) {
        private val elementsBuilder = DataElements.Builder(this.dataElements)
        fun fromDataElements(nameSpace: NameSpaceAlias, vararg elements: DataElementCbor) = apply {
            this.nameSpace = nameSpace
            elementsBuilder.addUsingCborElements(*elements)
        }

        fun withNameSpace(nameSpace: cddl_tstr) = apply { this.nameSpace = nameSpace.toCborString() }

        fun add(identifier: cddl_tstr, intentToRetain: cddl_bool = false) = apply {
            addUsingElements(DataElementCbor(identifier.toCborString(), intentToRetain.toCborBool()))
        }

        fun addUsingElements(vararg elements: DataElementCbor) = apply {
            elementsBuilder.addUsingCborElements(*elements)
        }

        fun dockRequestBuild(): DocRequest {
            return nameSpacesBuilder.deviceItemsRequestBuilder?.docRequestBuilder?.build()
                ?: throw IllegalStateException("DocRequestBuilder or deviceItemsRequestBuilder cannot be null")
        }

        fun deviceItemsRequestBuild(): DeviceItemsRequest {
            return nameSpacesBuilder.deviceItemsRequestBuilder?.build()
                ?: throw IllegalStateException("DocRequestBuilder cannot be null")
        }

        fun nameSpacesBuild(): NameSpaces {
            return nameSpacesBuilder.build()
        }

        fun build() = NameSpaceDataElements(nameSpace ?: MDL_NAMESPACE_CBOR, elementsBuilder.build())
    }
}
*/

/*

*/
/**
 * DataElements contains the requested data elements identified by their data element identifier. For
 * each requested data element, the IntentToRetainAlias variable indicates whether the mdoc verifier
 * intends to retain the received data element. The mdoc verifier shall not retain any data, including
 * digests and signatures, or derived data received from the mdoc, except for data elements for which the
 * IntentToRetainAlias flag was set to true in the request. To retain is defined as “to store for a period longer
 * than necessary to conduct the transaction in realtime”.
 *//*

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DataElements", exact = true)
class DataElements(private val backing: MutableList<DataElementCbor> = mutableListOf()) :
    MutableList<DataElementCbor> by backing {
    @JsName("fromVarArgs")
    constructor(vararg elements: DataElementCbor) : this(elements.toMutableList())

    */
/**
 * Although Kotlin typically does not need builders given the named params, we want to make it a bit easier
 * cross-platform and not to have developers take into account what the serialization structure is
 *//*

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Builder", exact = true)
    class Builder(val elements: Array<DataElementCbor> = arrayOf()) {
        fun from(vararg elements: DataElementCbor) = apply { this.elements.plus(elements) }
        fun add(identifier: cddl_tstr, intentToRetain: Boolean = false) =
            apply { addUsingCborElements(DataElementCbor(identifier.toCborString(), intentToRetain.toCborBool())) }

        fun addUsingCborElements(vararg elements: DataElementCbor) =
            apply { this.elements.plus(elements) }

        fun addUsingSimpleElements(vararg elements: DataElementSimple) =
            apply {
                addUsingCborElements(*elements.map {
                    DataElementCbor(
                        it.identifier.toCborString(),
                        it.intentToRetain.toCborBool()
                    )
                }.toTypedArray())
            }

        fun build(): CborMap<CborString, CborBool> {
            return CborMap(mutableMapOf(*elements.map { Pair(it.identifier, it.intentToRetain ?: CborTrue()) }
                .toTypedArray()))
        }
    }

}
*/



typealias DeviceResponseDocumentErrorCborAlias = Map<DocType, DocumentError>


typealias ErrorCodeAlias = cddl_int


typealias JWTDocumentErrorsAlias = Map<NameSpaceAlias, ErrorCodesAlias>


// fixme: Map to object
typealias ErrorCodesAlias = Map<DataElementIdentifierAlias, ErrorCodeAlias>


typealias NameSpacesResponseAlias = Map<NameSpaceAlias, DataElementsValueAlias>

typealias DataElementsValueAlias = Map<DataElementIdentifierAlias, DataElementValueAlias<Any>>

typealias JWT = cddl_tstr
