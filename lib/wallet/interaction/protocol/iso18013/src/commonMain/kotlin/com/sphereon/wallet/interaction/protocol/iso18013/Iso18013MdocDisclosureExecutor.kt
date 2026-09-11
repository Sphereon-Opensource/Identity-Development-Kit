/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.iso18013

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.data.device.DeviceNameSpaces
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.data.device.DeviceResponseCborCodec
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.DocumentWithKeyAlias
import com.sphereon.mdoc.data.device.DocumentResponseEncryptionProvider
import com.sphereon.mdoc.data.device.DocumentResponseEncryptionProviderResolver
import com.sphereon.mdoc.data.device.EncryptedDocumentsPlaintext
import com.sphereon.mdoc.data.device.ZkProofProvider
import com.sphereon.mdoc.data.device.ZkProofProviderResolver
import com.sphereon.mdoc.data.device.createZkDocument
import com.sphereon.mdoc.data.device.matchesIssuerIdentifiers
import com.sphereon.mdoc.engagement.MdocEngagementManager
import com.sphereon.mdoc.transfer.DocumentProvider
import com.sphereon.mdoc.transfer.DocumentRequestSingleDocumentSelector
import com.sphereon.mdoc.transfer.RequestDocumentsSelector
import com.sphereon.mdoc.transfer.RequestResponseProcessor
import com.sphereon.mdoc.transfer.TransferManager
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionFailureCodes
import com.sphereon.wallet.interaction.WalletInteractionState
import kotlinx.coroutines.CancellationException

fun interface Iso18013DocumentProviderResolver {
    suspend fun resolve(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): DocumentProvider?
}

class Iso18013MdocDisclosureExecutor(
    private val engagementManager: MdocEngagementManager,
    private val documentProvider: DocumentProvider? = null,
    private val documentProviderResolver: Iso18013DocumentProviderResolver? = null,
    private val deviceResponseCborCodec: DeviceResponseCborCodec? = null,
    private val zkProofProviders: List<ZkProofProvider> = emptyList(),
    private val documentResponseEncryptionProviders: List<DocumentResponseEncryptionProvider> = emptyList(),
) : Iso18013DisclosureExecutor {
    override suspend fun sendDeviceResponse(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): Iso18013DisclosureExecutionResult {
        val rawEntryPoint =
            context.privateSessionStore
                .get(context.sessionId, Iso18013WalletInteractionProtocolAdapter.ADAPTER_ID)
                ?.values
                ?.get("entry_point.raw")
                .orEmpty()
        if (rawEntryPoint.isBlank()) {
            return failed(
                code = WalletInteractionFailureCodes.ISO18013_ENGAGEMENT_MISSING,
                messageKey = "wallet.interaction.error.iso18013_engagement_missing",
                retryable = true,
            )
        }
        return try {
            val engagement = engagementManager.toApp(rawEntryPoint, autoStart = false)
            if (engagement.isErr) {
                return failed(
                    code = WalletInteractionFailureCodes.ISO18013_ENGAGEMENT_FAILED,
                    messageKey = "wallet.interaction.error.iso18013_engagement_failed",
                    providerErrorCode = engagement.error.code,
                )
            }
            val transfer = engagement.value.start()
            val deviceRequest = transfer.receiveDeviceRequest()
            val effectiveDocumentProvider = documentProviderResolver?.resolve(context, state) ?: documentProvider
            if (effectiveDocumentProvider != null) {
                transfer.registerIso18013SigningResponseProcessor(
                    documentProvider = effectiveDocumentProvider,
                    deviceResponseCborCodec = deviceResponseCborCodec,
                    zkProofProviders = zkProofProviders,
                    documentResponseEncryptionProviders = documentResponseEncryptionProviders,
                )
            }
            val deviceResponse = transfer.createResponse(deviceRequest, effectiveDocumentProvider)
            transfer.sendDeviceResponse(deviceResponse)
            Iso18013DisclosureExecutionResult.Sent()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed(
                code = WalletInteractionFailureCodes.ISO18013_DEVICE_RESPONSE_FAILED,
                messageKey = "wallet.interaction.error.iso18013_device_response_failed",
                retryable = true,
            )
        }
    }

    private fun failed(
        code: String,
        messageKey: String,
        retryable: Boolean = false,
        providerErrorCode: String? = null,
    ): Iso18013DisclosureExecutionResult.Failed =
        Iso18013DisclosureExecutionResult.Failed(
            code = code,
            messageKey = messageKey,
            retryable = retryable,
            arguments = providerErrorCode?.let { mapOf("providerErrorCode" to it) }.orEmpty(),
        )
}

private fun TransferManager.registerIso18013SigningResponseProcessor(
    documentProvider: DocumentProvider,
    deviceResponseCborCodec: DeviceResponseCborCodec?,
    zkProofProviders: List<ZkProofProvider>,
    documentResponseEncryptionProviders: List<DocumentResponseEncryptionProvider>,
) {
    registerCustomResponseSelectors(
        requestResponseProcesser =
            Iso18013SigningRequestResponseProcessor(
                transferManager = this,
                documentProvider = documentProvider,
                deviceResponseCborCodec = deviceResponseCborCodec,
                zkProofProviders = zkProofProviders,
                documentResponseEncryptionProviders = documentResponseEncryptionProviders,
            ),
        requestDocumentsSelector = UnusedIso18013RequestDocumentsSelector,
        docRequestSingleDocumentSelector = UnusedIso18013DocumentSelector,
    )
}

private class Iso18013SigningRequestResponseProcessor(
    private val transferManager: TransferManager,
    private val documentProvider: DocumentProvider,
    private val deviceResponseCborCodec: DeviceResponseCborCodec?,
    private val zkProofProviders: List<ZkProofProvider>,
    private val documentResponseEncryptionProviders: List<DocumentResponseEncryptionProvider>,
) : RequestResponseProcessor {
    override suspend fun createDeviceResponse(
        deviceRequest: DeviceRequest,
        documentProvider: DocumentProvider?,
    ): IdkResult<DeviceResponse, IdkErrorType> {
        val docRequests = deviceRequest.effectiveDocRequests()
        if (docRequests.isEmpty()) {
            return IdkError
                .ILLEGAL_ARGUMENT_ERROR(arg = "DeviceRequest contains no document requests")
                .asErrorResult()
        }

        docRequests
            .mapNotNull { it.itemsRequest.docRequestInfo?.zkRequest }
            .forEach { request ->
                val provider = ZkProofProviderResolver.resolve(request, zkProofProviders)
                if (provider.isErr) {
                    return IdkError
                        .ILLEGAL_ARGUMENT_ERROR(arg = provider.error.code)
                        .asErrorResult()
                }
            }

        return try {
            val provider = documentProvider ?: this.documentProvider
            val requestedDocTypes = docRequests.map { it.itemsRequest.docType.toString() }.toSet()
            val availableDocuments = provider.getDocuments(Iso18013DocumentSelectorData(requestedDocTypes))
            val selectedDocuments = selectIso18013Documents(deviceRequest, docRequests, availableDocuments)
                .onFailure { return it.asErrorResult() }
                .value
            val signedDocuments = selectedDocuments.map { (request, selectedDocument) ->
                request to transferManager.signDocument(
                    request = request,
                    document = selectedDocument.document,
                    deviceKeyInfo = selectedDocument.signingKeyInfo(),
                    deviceNamespaces = DeviceNameSpaces(mapOf()),
                )
            }
            val clearDocuments = mutableListOf<com.sphereon.mdoc.data.device.Document>()
            val zkDocuments = mutableListOf<com.sphereon.mdoc.data.device.ZkDocument>()
            val encryptedDocuments = mutableListOf<com.sphereon.mdoc.data.device.EncryptedDocuments>()
            signedDocuments.forEach { (request, signedDocument) ->
                val zkRequest = request.itemsRequest.docRequestInfo?.zkRequest
                val zkProvider =
                    zkRequest?.let {
                        val resolved = ZkProofProviderResolver.resolve(it, zkProofProviders)
                        if (resolved.isErr) {
                            return resolved.error.asErrorResult()
                        }
                        resolved.value
                    }
                val zkDocument =
                    if (zkRequest != null && zkProvider != null) {
                        zkProvider
                            .createZkDocument(
                                request = zkRequest,
                                document = signedDocument,
                                sessionTranscript = transferManager.getSessionTranscript().data(),
                            ).onFailure { return it.asErrorResult() }
                            .value
                    } else {
                        null
                    }
                val encryptionParameters = request.itemsRequest.docRequestInfo?.docResponseEncryption
                if (encryptionParameters == null && zkDocument == null) {
                    clearDocuments += signedDocument
                } else if (encryptionParameters == null) {
                    zkDocuments += requireNotNull(zkDocument)
                } else {
                    val docRequestId =
                        docRequests.indexOfFirst { it === request }.takeIf { it >= 0 }
                            ?: docRequests.indexOf(request).takeIf { it >= 0 }
                            ?: return IdkError
                                .ILLEGAL_ARGUMENT_ERROR(arg = "Encrypted document does not map to a DeviceRequest docRequests entry")
                                .asErrorResult()
                    val encryptionProvider =
                        DocumentResponseEncryptionProviderResolver
                            .resolve(encryptionParameters, documentResponseEncryptionProviders)
                            .onFailure { return it.asErrorResult() }
                            .value
                    val encrypted =
                        encryptionProvider
                            .encrypt(
                                plaintext =
                                    if (zkDocument == null) {
                                        EncryptedDocumentsPlaintext(documents = listOf(signedDocument))
                                    } else {
                                        EncryptedDocumentsPlaintext(zkDocuments = listOf(zkDocument))
                                    },
                                parameters = encryptionParameters,
                                sessionTranscript = transferManager.getSessionTranscript().data(),
                                docRequestID = docRequestId.toUInt(),
                            )
                            .onFailure { return it.asErrorResult() }
                            .value
                    encryptedDocuments += encrypted
                }
            }
            val response = DeviceResponse
                .Builder()
                .withDocuments(clearDocuments.toTypedArray())
                .withZkDocuments(zkDocuments.takeIf { it.isNotEmpty() }?.toTypedArray())
                .withEncryptedDocuments(encryptedDocuments.takeIf { it.isNotEmpty() }?.toTypedArray())
                .build()
            val maximumResponseSize =
                docRequests
                    .mapNotNull { it.itemsRequest.docRequestInfo?.maximumResponseSize }
                    .minOrNull()
            if (maximumResponseSize == null) {
                response.asOkResult()
            } else {
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
                        .ILLEGAL_ARGUMENT_ERROR(
                            arg = "DeviceResponse exceeds maximumResponseSize ($maximumResponseSize bytes)",
                        ).asErrorResult()
                }
                response.asOkResult()
            }
        } catch (expected: Exception) {
            IdkError
                .UNKNOWN_ERROR(
                    message = "Failed to create ISO 18013 device response: ${expected.message}",
                    exception = expected,
                ).asErrorResult()
        }
    }
}

/**
 * Selects the declared document set for each mandatory/optional use case. The old
 * implementation selected one document per request and therefore ignored the
 * second-edition use-case contract entirely.
 */
private fun selectIso18013Documents(
    deviceRequest: DeviceRequest,
    docRequests: List<DocRequest>,
    availableDocuments: Set<DocumentWithKeyAlias>,
): IdkResult<List<Pair<DocRequest, DocumentWithKeyAlias>>, IdkErrorType> {
    val available = availableDocuments.toList()
    val uniqueDocumentSetRequired = docRequests.any { it.itemsRequest.docRequestInfo?.uniqueDocSetRequired == true }

    fun candidateFor(request: DocRequest): DocumentWithKeyAlias? {
        val identifiers = request.itemsRequest.docRequestInfo?.issuerIdentifiers.orEmpty()
        return available.firstOrNull { candidate ->
            candidate.document.docType == request.itemsRequest.docType &&
                candidate.document.issuerSigned.issuerAuth.matchesIssuerIdentifiers(identifiers)
        }
    }

    fun resolveRequests(requests: List<DocRequest>): List<Pair<DocRequest, DocumentWithKeyAlias>>? {
        val selected = requests.map { request -> request to (candidateFor(request) ?: return null) }
        if (uniqueDocumentSetRequired && selected.map { it.second }.distinct().size != selected.size) {
            return null
        }
        return selected
    }

    val useCases = deviceRequest.deviceRequestInfo?.useCases.orEmpty()
    if (useCases.isEmpty()) {
        val selected = resolveRequests(docRequests)
            ?: return IdkError.NOT_FOUND_ERROR(resource = "No document set satisfies the DeviceRequest").asErrorResult()
        return selected.asOkResult()
    }

    val selected = mutableListOf<Pair<DocRequest, DocumentWithKeyAlias>>()
    for ((useCaseIndex, useCase) in useCases.withIndex()) {
        val chosen =
            useCase.documentSets
                .asSequence()
                .filter { it.isNotEmpty() }
                .mapNotNull { documentSet ->
                    if (documentSet.any { requestIndex ->
                            requestIndex > Int.MAX_VALUE.toUInt() || docRequests.getOrNull(requestIndex.toInt()) == null
                        }
                    ) {
                        null
                    } else {
                        resolveRequests(documentSet.map { requestIndex -> docRequests[requestIndex.toInt()] })
                    }
                }.firstOrNull { candidateSet ->
                    !uniqueDocumentSetRequired || candidateSet.none { (_, document) -> selected.any { it.second == document } }
                }
        if (chosen == null) {
            if (useCase.mandatory) {
                return IdkError.NOT_FOUND_ERROR(resource = "Mandatory ISO 18013 use case $useCaseIndex cannot be satisfied").asErrorResult()
            }
            continue
        }
        selected += chosen
    }
    return selected.asOkResult()
}

internal data class Iso18013DocumentSelectorData(
    val requestedDocTypes: Set<String>,
)

private fun DocumentWithKeyAlias.signingKeyInfo(): KeyInfo<CoseKeyType>? {
    val alias = keyAlias.takeIf { it.isNotBlank() } ?: return null
    return KeyInfo(alias = alias, providerId = providerId.takeIf { it.isNotBlank() })
}

private object UnusedIso18013RequestDocumentsSelector : RequestDocumentsSelector {
    override suspend fun selectDocuments(
        deviceRequest: DeviceRequest,
        minDocRequests: Int,
        documentProvider: DocumentProvider?,
    ): IdkResult<Map<DocRequest, DocumentWithKeyAlias>, IdkErrorType> =
        IdkError
            .UNKNOWN_ERROR(message = "ISO 18013 signing response processor should handle document selection directly")
            .asErrorResult()
}

private object UnusedIso18013DocumentSelector : DocumentRequestSingleDocumentSelector {
    override suspend fun select(
        docRequest: DocRequest,
        documentProvider: DocumentProvider?,
        selectorData: Any?,
    ): IdkResult<Pair<DocRequest, DocumentWithKeyAlias>?, IdkErrorType> =
        IdkError
            .UNKNOWN_ERROR(message = "ISO 18013 signing response processor should handle document selection directly")
            .asErrorResult()
}
