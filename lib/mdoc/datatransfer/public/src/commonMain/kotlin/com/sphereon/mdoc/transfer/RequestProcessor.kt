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

package com.sphereon.mdoc.transfer

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.LogService
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.MdocSignService
import com.sphereon.mdoc.data.device.DeviceAuthentication
import com.sphereon.mdoc.data.device.DeviceNameSpaces
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.data.device.DocumentWithKeyAlias
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("DocumentProvider", exact = true)
@JsExportCompat
interface DocumentProvider {
    suspend fun getDocuments(selectorData: Any? = null): Set<DocumentWithKeyAlias>
}

/**
 * Represents a functional interface responsible for selecting a single document
 * from a list of available documents based on a given document request.
 */
@JsExportCompat
interface DocumentRequestSingleDocumentSelector {
    /**
     * Selects a single document from a set of available documents based on the given document request.
     *
     * @param docRequest The request object that describes the criteria for selecting the document.
     * @param documentProvider A function that takes optional custom selector data and returns a set of available documents matching the criteria.
     * @return A result containing the selected document if the operation is successful, or an error if the selection fails.
     */
    suspend fun select(
        docRequest: DocRequest,
        documentProvider: DocumentProvider? = null,
        selectorData: Any? = null,
    ): IdkResult<Pair<DocRequest, DocumentWithKeyAlias>?, IdkErrorType>
}

/**
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("representing", exact = true)
 * Functional interface representing a selector for documents based on a given device request.
 * This is primarily used to process a `DeviceRequest` and determine the relevant set of `Document` objects.
 * If an error occurs during selection, an `SureError` should be returned.
 *
 * The DeviceRequest contains an array of Doc Request object (DocRequest). So an implementation of this interface is likely to call
 * the DocumentRequestSingleDocumentSelector interface
 */
@JsExportCompat
interface RequestDocumentsSelector {
    /**
     * Selects documents based on the provided device request in CBOR format.
     *
     * @param deviceRequest the device request containing parameters and details in CBOR format.
     * @param minDocRequests the minimum number of document requests that should be satisfied. Typically, all. But can be set to 1 or 0 for instance
     * @return a result object representing either a map of Doc requests to documents with key alias or an error.
     */
    suspend fun selectDocuments(
        deviceRequest: DeviceRequest,
        minDocRequests: Int = deviceRequest.effectiveDocRequests().size,
        documentProvider: DocumentProvider? = null,
    ): IdkResult<Map<DocRequest, DocumentWithKeyAlias>, IdkErrorType>
}

/**
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("defining", exact = true)
 * A functional interface defining a processor responsible for handling a mdoc request
 * and generating a corresponding response. Implements a single method to process the
 * given device request and produce a device response or an error in case of failure.
 *
 * It is likely that an implementation of this interface delegates to the above functional interfaces as well.
 */
@JsExportCompat
interface RequestResponseProcessor {
    /**
     * Creates a response based on the given device request.
     *
     * @param deviceRequest The device request represented as a `DeviceRequest` object.
     * This input contains data such as version, document requests, and optional OID4VP request.
     * @return A `IdkResult` object which either contains a successful `DeviceResponse` or an error of type `SureError`.
     */
    suspend fun createDeviceResponse(
        deviceRequest: DeviceRequest,
        documentProvider: DocumentProvider? = null,
    ): IdkResult<DeviceResponse, IdkErrorType>
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("SimpleDocumentRequestSingleDocumentSelector", exact = true)
@JsExportCompat
class SimpleDocumentRequestSingleDocumentSelector(
    val log: LogService,
    val globalCustomSelectorData: Any? = null,
    val globalDocumentsSupplier: DocumentProvider? = null,
) : DocumentRequestSingleDocumentSelector {
    override suspend fun select(
        docRequest: DocRequest,
        documentProvider: DocumentProvider?,
        selectorData: Any?,
    ): IdkResult<Pair<DocRequest, DocumentWithKeyAlias>, IdkErrorType> {
        val documentsProvider = documentProvider ?: globalDocumentsSupplier ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(arg = "No document supplier provided").asErrorResult()
        val docType = docRequest.itemsRequest.docType
        val matches = documentsProvider.getDocuments(selectorData ?: globalCustomSelectorData).filter { it.document.docType == docType }

        if (matches.isEmpty()) {
            return IdkError
                .NOT_FOUND_ERROR(resource = docType.toString())
                .asErrorResult()
                .also { log.error("No mdoc found for request ${it.error}") }
        } else if (matches.size > 1) {
            log.error("Multiple mdocs found for $docType. Only the first one will be returned!")
        }
        val matchingDocument = matches[0]
        return (docRequest to matchingDocument).asOkResult()
    }
}

/**
 * A custom document selector that uses a pre-filled map of user selections to resolve a DocRequest.
 * This is used as a very simple bridge the UI selection process with the data transfer layer. Instead of using the functional interfaces,
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("can", exact = true)
 * this class can be used directly by passing in combinations of DocRequest and Document objects. You are responsible for ensuring that the
 * DocRequest matches the Document object.
 */
@AssistedInject
@JsExportCompat
data class MapDrivenDocRequestSelector(
    @Assisted var selections: Map<DocRequest, DocumentWithKeyAlias>,
    @Assisted val sessionTranscript: SessionTranscript? = null, // If not provided the assumption is that the documents are already signed.
    @Assisted val mdocDeviceSignService: MdocSignService? = null, // If not provided the assumption is that the documents are already device signed.
    @Assisted val minDocRequests: Int? = null,
) : DocumentRequestSingleDocumentSelector,
    RequestDocumentsSelector,
    DocumentProvider,
    RequestResponseProcessor {
    @AssistedFactory
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Factory", exact = true)
    interface Factory {
        fun create(
            selections: Map<DocRequest, DocumentWithKeyAlias>,
            sessionTranscript: SessionTranscript? = null,
            mdocDeviceSignService: MdocSignService? = null,
            minDocRequests: Int? = null,
        ): MapDrivenDocRequestSelector
    }

    override suspend fun select(
        docRequest: DocRequest,
        documentProvider: DocumentProvider?,
        selectorData: Any?,
    ): IdkResult<Pair<DocRequest, DocumentWithKeyAlias>?, IdkErrorType> = selections[docRequest]?.let { docRequest to it }.asOkResult()

    override suspend fun selectDocuments(
        deviceRequest: DeviceRequest,
        minDocRequests: Int,
        documentProvider: DocumentProvider?,
    ): IdkResult<Map<DocRequest, DocumentWithKeyAlias>, IdkErrorType> {
        // In the map implementation we allow to override the default minDocRequests value at the instance level, so take that first
        val min = this.minDocRequests ?: minDocRequests
        if (documentProvider != null && documentProvider != this) {
            return IdkError
                .ILLEGAL_ARGUMENT_ERROR(
                    arg = "Documents supplier provided not supported by MapDrivenDocRequestSingleDocumentSelector",
                ).asErrorResult()
        }
        if (selections.size < min) {
            return IdkError.NOT_FOUND_ERROR(resource = "Not enough documents selected ${selections.size} (min: $min)").asErrorResult()
        }
        return selections.asOkResult()
    }

    override suspend fun getDocuments(selectorData: Any?): Set<DocumentWithKeyAlias> = selections.values.toSet()

    override suspend fun createDeviceResponse(
        deviceRequest: DeviceRequest,
        documentProvider: DocumentProvider?,
    ): IdkResult<DeviceResponse, IdkErrorType> {
        if (documentProvider != null && documentProvider != this) {
            return IdkError
                .ILLEGAL_ARGUMENT_ERROR(
                    arg = "Documents supplier provided not supported by MapDrivenDocRequestSingleDocumentSelector",
                ).asErrorResult()
        }
        return SimpleRequestResponseProcessor(
            this,
            globalDocumentsProvider = this,
            globalSelectorData = null,
            sessionTranscript = sessionTranscript,
            mdocDeviceSignService = mdocDeviceSignService,
        ).createDeviceResponse(
            deviceRequest = deviceRequest,
            documentProvider = this,
        )
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("SimpleRequestDocumentsSelector", exact = true)
@JsExportCompat
class SimpleRequestDocumentsSelector(
    val log: LogService,
    val docRequestSingleDocSelect: DocumentRequestSingleDocumentSelector,
    val globalDocumentsProvider: DocumentProvider? = null,
) : RequestDocumentsSelector {
    override suspend fun selectDocuments(
        deviceRequest: DeviceRequest,
        minDocRequests: Int,
        documentProvider: DocumentProvider?,
    ): IdkResult<Map<DocRequest, DocumentWithKeyAlias>, IdkErrorType> {
        val finalDocumentsSupplier =
            documentProvider ?: globalDocumentsProvider ?: return IdkError
                .ILLEGAL_ARGUMENT_ERROR(arg = "No documents supplier provided")
                .asErrorResult()
                .also { log.error(it.error.message.defaultMessage) }
        val docRequests = deviceRequest.effectiveDocRequests()
        if (docRequests.isEmpty() && minDocRequests > 0) {
            return IdkError
                .ILLEGAL_ARGUMENT_ERROR(arg = "Unsupported device request: empty or null doc requests")
                .asErrorResult()
                .also { log.error(it.error.message.defaultMessage) }
        }
        val selected =
            docRequests
                .map { req ->
                    docRequestSingleDocSelect
                        .select(req, finalDocumentsSupplier, globalDocumentsProvider)
                        .onFailure { return it.asErrorResult() }
                        .value
                }.filterNotNull()
                .toMap()

        val result = selected ?: emptyMap()
        if (result.isEmpty() && minDocRequests > 0) {
            return IdkError.NOT_FOUND_ERROR(resource = "Not enough documents ${result.size}  (min: $minDocRequests) selected").asErrorResult()
        }
        return result.asOkResult()
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("SimpleRequestResponseProcessor", exact = true)
@JsExportCompat
open class SimpleRequestResponseProcessor(
    val documentsSelector: RequestDocumentsSelector,
    val globalDocumentsProvider: DocumentProvider? = null,
    val sessionTranscript: SessionTranscript? = null, // If not provided the assumption is that the documents are already signed.
    val mdocDeviceSignService: MdocSignService? = null, // If not provided the assumption is that the documents are already device signed.
    val globalSelectorData: Any? = null,
    val minDocRequests: Int? = null,
) : RequestResponseProcessor {
    override suspend fun createDeviceResponse(
        deviceRequest: DeviceRequest,
        documentProvider: DocumentProvider?,
    ): IdkResult<DeviceResponse, IdkErrorType> {
        val finalDocumentsSupplier = documentProvider ?: globalDocumentsProvider
        val effectiveDocRequests = deviceRequest.effectiveDocRequests()
        val requestsToDocuments =
            documentsSelector
                .selectDocuments(
                    deviceRequest = deviceRequest,
                    minDocRequests = this.minDocRequests ?: effectiveDocRequests.size,
                    finalDocumentsSupplier,
                ).onFailure { return it.asErrorResult() }
                .value

        val documents: Array<Document> =
            if (mdocDeviceSignService == null) {
                require(sessionTranscript == null) { "`sessionTranscript` is not allowed if `mdocDeviceSignService` is not provided." }
                requestsToDocuments.values.map { it.document }.toTypedArray()
            } else {
                requireNotNull(sessionTranscript) { "`sessionTranscript` is required if `mdocDeviceSignService` is provided." }
                // Sign each document sequentially
                val signed = mutableListOf<Document>()
                for ((req: DocRequest, docWithKeyAlias: DocumentWithKeyAlias) in requestsToDocuments) {
                    val doc = docWithKeyAlias.document
                    val deviceAuthentication =
                        DeviceAuthentication(
                            sessionTranscript = sessionTranscript,
                            docType = doc.docType,
                            deviceNamespaces = DeviceNameSpaces(mapOf()),
                            original = null,
                        )
                    val deviceKeyInfo = KeyInfo<CoseKeyType>(providerId = docWithKeyAlias.providerId, alias = docWithKeyAlias.keyAlias)
                    val signedDoc =
                        mdocDeviceSignService.deviceSignDocument(
                            request = req,
                            document = doc,
                            deviceAuthentication = deviceAuthentication,
                            deviceKeyInfo = deviceKeyInfo,
                            requireDeviceX5Chain = false,
                        )
                    signed.add(signedDoc)
                }
                signed.toTypedArray()
            }

        return DeviceResponse
            .Builder()
            .withDocuments(documents)
            .build()
            .asOkResult()
    }
}
