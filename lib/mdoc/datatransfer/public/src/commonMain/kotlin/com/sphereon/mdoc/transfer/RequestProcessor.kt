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
import com.sphereon.mdoc.data.device.DeviceResponseCborCodec
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.data.device.DocumentWithKeyAlias
import com.sphereon.mdoc.data.device.DocumentResponseEncryptionProvider
import com.sphereon.mdoc.data.device.DocumentResponseEncryptionProviderResolver
import com.sphereon.mdoc.data.device.EncryptedDocuments
import com.sphereon.mdoc.data.device.EncryptedDocumentsPlaintext
import com.sphereon.mdoc.data.device.ZkDocument
import com.sphereon.mdoc.data.device.ZkProofProviderResolver
import com.sphereon.mdoc.data.device.ZkProofProvider
import com.sphereon.mdoc.data.device.createZkDocument
import com.sphereon.mdoc.data.device.matchesIssuerIdentifiers
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
        val issuerIdentifiers = docRequest.itemsRequest.docRequestInfo?.issuerIdentifiers.orEmpty()
        val matches =
            documentsProvider
                .getDocuments(selectorData ?: globalCustomSelectorData)
                .filter { candidate ->
                    candidate.document.docType == docType &&
                        candidate.document.issuerSigned.issuerAuth.matchesIssuerIdentifiers(issuerIdentifiers)
                }

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
    @Assisted val zkProofProviders: List<ZkProofProvider> = emptyList(),
    @Assisted val documentResponseEncryptionProviders: List<DocumentResponseEncryptionProvider> = emptyList(),
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
            zkProofProviders: List<ZkProofProvider> = emptyList(),
            documentResponseEncryptionProviders: List<DocumentResponseEncryptionProvider> = emptyList(),
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
            zkProofProviders = zkProofProviders,
            documentResponseEncryptionProviders = documentResponseEncryptionProviders,
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
        val useCases = deviceRequest.deviceRequestInfo?.useCases.orEmpty()
        val selected = mutableMapOf<DocRequest, DocumentWithKeyAlias>()
        for (request in docRequests) {
            val selection = docRequestSingleDocSelect.select(request, finalDocumentsSupplier, globalDocumentsProvider)
            if (selection.isErr) {
                // An optional use case is allowed to be unavailable. Keep every other
                // selector/provider failure fatal, including failures in legacy requests.
                if (useCases.isEmpty() || selection.error.code != "NOT_FOUND_ERROR") {
                    return selection.error.asErrorResult()
                }
                continue
            }
            selection.value?.let { selected[it.first] = it.second }
        }

        if (useCases.isNotEmpty()) {
            val selectedIndexesByRequest = docRequests.mapIndexedNotNull { index, request ->
                if (selected.containsKey(request)) index else null
            }.toSet()
            val chosenDocumentSets = mutableListOf<Set<UInt>>()
            useCases.forEachIndexed { useCaseIndex, useCase ->
                useCase.documentSets.forEach { documentSet ->
                    if (documentSet.any { it.toInt() < 0 || it.toInt() >= docRequests.size }) {
                        return IdkError.ILLEGAL_ARGUMENT_ERROR(
                            arg = "DeviceRequestInfo use case $useCaseIndex contains a DocRequestID outside docRequests",
                        ).asErrorResult()
                    }
                }
                val satisfied = useCase.documentSets.firstOrNull { documentSet ->
                    documentSet.all { it.toInt() in selectedIndexesByRequest }
                }
                if (satisfied != null) chosenDocumentSets += satisfied.toSet()
                if (useCase.mandatory && satisfied == null) {
                    return IdkError.NOT_FOUND_ERROR(
                        resource = "mandatory use case $useCaseIndex",
                    ).asErrorResult()
                }
            }
            if (chosenDocumentSets.isEmpty()) return emptyMap<DocRequest, DocumentWithKeyAlias>().asOkResult()
            val chosenIndexes = chosenDocumentSets.flatten().map(UInt::toInt).toSet()
            val chosenRequests = docRequests.filterIndexed { index, _ -> index in chosenIndexes }.toSet()
            val useCaseSelected = selected.filterKeys { it in chosenRequests }
            if (useCaseSelected.size < minDocRequests && minDocRequests <= selected.size) {
                log.debug("DeviceRequestInfo use cases selected ${useCaseSelected.size} of ${selected.size} documents; use-case rules take precedence over the default minimum")
            }
            return useCaseSelected.asOkResult()
        }

        val result = selected
        if (result.size < minDocRequests) {
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
    val deviceResponseCborCodec: DeviceResponseCborCodec? = null,
    val zkProofProviders: List<ZkProofProvider> = emptyList(),
    val documentResponseEncryptionProviders: List<DocumentResponseEncryptionProvider> = emptyList(),
) : RequestResponseProcessor {
    override suspend fun createDeviceResponse(
        deviceRequest: DeviceRequest,
        documentProvider: DocumentProvider?,
    ): IdkResult<DeviceResponse, IdkErrorType> {
        val finalDocumentsSupplier = documentProvider ?: globalDocumentsProvider
        val effectiveDocRequests = deviceRequest.effectiveDocRequests()
        effectiveDocRequests
            .mapNotNull { it.itemsRequest.docRequestInfo?.zkRequest }
            .forEach { request ->
                val provider = ZkProofProviderResolver.resolve(request, zkProofProviders)
                if (provider.isErr) {
                    return IdkError.ILLEGAL_ARGUMENT_ERROR(arg = provider.error.code).asErrorResult()
                }
            }
        val requestsToDocuments =
            documentsSelector
                .selectDocuments(
                    deviceRequest = deviceRequest,
                    minDocRequests = this.minDocRequests ?: effectiveDocRequests.size,
                    finalDocumentsSupplier,
                ).onFailure { return it.asErrorResult() }
                .value

        val requestsToSignedDocuments: List<Pair<DocRequest, Document>> =
            if (mdocDeviceSignService == null) {
                // A pre-signed document normally needs no transcript, but second-edition
                // encrypted responses and proof providers do. Keep accepting pre-signed
                // documents while allowing the caller to supply the transcript required by
                // those response forms.
                requestsToDocuments.map { (request, documentWithKeyAlias) ->
                    val document = documentWithKeyAlias.document
                    request to document.copy(
                        issuerSigned = document.limitDisclosures(request),
                        original = null,
                    )
                }
            } else {
                requireNotNull(sessionTranscript) { "`sessionTranscript` is required if `mdocDeviceSignService` is provided." }
                // Sign each document sequentially
                val signed = mutableListOf<Pair<DocRequest, Document>>()
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
                            macKeys = deviceRequest.macKeys,
                        )
                    signed.add(req to signedDoc)
                }
                signed
            }

        val documents = mutableListOf<Document>()
        val zkDocuments = mutableListOf<ZkDocument>()
        val encryptedDocuments = mutableListOf<EncryptedDocuments>()
        for ((request, document) in requestsToSignedDocuments) {
            val zkRequest = request.itemsRequest.docRequestInfo?.zkRequest
            val zkProvider =
                zkRequest?.let {
                    val resolved = ZkProofProviderResolver.resolve(it, zkProofProviders)
                    if (resolved.isErr) {
                        if (it.zkRequired) {
                            return resolved.error.asErrorResult()
                        }
                        // ZKP is an optional response form unless explicitly required. Continue
                        // with the ordinary document (or encrypted ordinary document) when no
                        // compatible proof backend is available.
                        null
                    } else {
                        resolved.value
                    }
                }
            val zkDocument =
                if (zkRequest != null && zkProvider != null) {
                    zkProvider
                        .createZkDocument(
                            request = zkRequest,
                            document = document,
                            sessionTranscript = sessionTranscript,
                        ).onFailure { return it.asErrorResult() }
                        .value
                } else {
                    null
                }
            val encryptionParameters = request.itemsRequest.docRequestInfo?.docResponseEncryption
            if (encryptionParameters == null && zkDocument == null) {
                documents += document
                continue
            }
            if (encryptionParameters == null) {
                zkDocuments += requireNotNull(zkDocument)
                continue
            }
            val transcript = sessionTranscript
                ?: return IdkError
                    .ILLEGAL_ARGUMENT_ERROR(arg = "Document response encryption requires a session transcript")
                    .asErrorResult()
            val provider = DocumentResponseEncryptionProviderResolver
                .resolve(encryptionParameters, documentResponseEncryptionProviders)
                .onFailure { return it.asErrorResult() }
                .value
            val requestIndex = effectiveDocRequests.indexOfFirst { it === request }
                .takeIf { it >= 0 }
                ?: effectiveDocRequests.indexOf(request)
            if (requestIndex < 0) {
                return IdkError
                    .ILLEGAL_ARGUMENT_ERROR(arg = "Encrypted document does not map to a DeviceRequest docRequests entry")
                    .asErrorResult()
            }
            val encrypted = provider
                .encrypt(
                    plaintext =
                        if (zkDocument == null) {
                            EncryptedDocumentsPlaintext(documents = listOf(document))
                        } else {
                            EncryptedDocumentsPlaintext(zkDocuments = listOf(zkDocument))
                        },
                    parameters = encryptionParameters,
                    sessionTranscript = transcript,
                    docRequestID = requestIndex.toUInt(),
                ).onFailure { return it.asErrorResult() }
                .value
            encryptedDocuments += encrypted
        }

        val response = DeviceResponse
            .Builder()
            .withDocuments(documents.toTypedArray())
            .withZkDocuments(zkDocuments.takeIf { it.isNotEmpty() }?.toTypedArray())
            .withEncryptedDocuments(encryptedDocuments.takeIf { it.isNotEmpty() }?.toTypedArray())
            .build()
        val maximumResponseSize =
            effectiveDocRequests
                .mapNotNull { it.itemsRequest.docRequestInfo?.maximumResponseSize }
                .minOrNull()
        if (maximumResponseSize == null) {
            return response.asOkResult()
        }
        val codec = deviceResponseCborCodec
            ?: return IdkError
                .ILLEGAL_ARGUMENT_ERROR(
                    arg = "maximumResponseSize was requested but no DeviceResponseCborCodec is configured",
                ).asErrorResult()
        val encoded = codec.encode(response)
        if (encoded.isErr) {
            return encoded.error.asErrorResult()
        }
        if (encoded.value.size.toUInt() > maximumResponseSize) {
            return IdkError
                .ILLEGAL_ARGUMENT_ERROR(arg = "DeviceResponse exceeds maximumResponseSize ($maximumResponseSize bytes)")
                .asErrorResult()
        }
        return response.asOkResult()
    }
}
