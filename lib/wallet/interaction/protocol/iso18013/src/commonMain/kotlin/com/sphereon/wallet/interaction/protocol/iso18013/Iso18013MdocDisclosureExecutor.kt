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
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.DocumentWithKeyAlias
import com.sphereon.mdoc.engagement.MdocEngagementManager
import com.sphereon.mdoc.transfer.DocumentProvider
import com.sphereon.mdoc.transfer.DocumentRequestSingleDocumentSelector
import com.sphereon.mdoc.transfer.RequestDocumentsSelector
import com.sphereon.mdoc.transfer.RequestResponseProcessor
import com.sphereon.mdoc.transfer.TransferManager
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionState

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
                code = "iso18013.engagement_missing",
                messageKey = "wallet.interaction.error.iso18013_engagement_missing",
                retryable = true,
            )
        }
        return try {
            val engagement = engagementManager.toApp(rawEntryPoint, autoStart = false)
            if (engagement.isErr) {
                return failed(
                    code = "iso18013.engagement_failed",
                    messageKey = "wallet.interaction.error.iso18013_engagement_failed",
                    providerErrorCode = engagement.error.code,
                )
            }
            val transfer = engagement.value.start()
            val deviceRequest = transfer.receiveDeviceRequest()
            val effectiveDocumentProvider = documentProviderResolver?.resolve(context, state) ?: documentProvider
            if (effectiveDocumentProvider != null) {
                transfer.registerIso18013SigningResponseProcessor(effectiveDocumentProvider)
            }
            val deviceResponse = transfer.createResponse(deviceRequest, effectiveDocumentProvider)
            transfer.sendDeviceResponse(deviceResponse)
            Iso18013DisclosureExecutionResult.Sent()
        } catch (_: Exception) {
            failed(
                code = "iso18013.device_response_failed",
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

private fun TransferManager.registerIso18013SigningResponseProcessor(documentProvider: DocumentProvider) {
    registerCustomResponseSelectors(
        requestResponseProcesser = Iso18013SigningRequestResponseProcessor(this, documentProvider),
        requestDocumentsSelector = UnusedIso18013RequestDocumentsSelector,
        docRequestSingleDocumentSelector = UnusedIso18013DocumentSelector,
    )
}

private class Iso18013SigningRequestResponseProcessor(
    private val transferManager: TransferManager,
    private val documentProvider: DocumentProvider,
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

        return try {
            val provider = documentProvider ?: this.documentProvider
            val requestedDocTypes = docRequests.map { it.itemsRequest.docType.toString() }.toSet()
            val availableDocuments = provider.getDocuments(Iso18013DocumentSelectorData(requestedDocTypes))
            val signedDocuments =
                docRequests.map { request ->
                    val selectedDocument =
                        availableDocuments.firstOrNull { candidate -> candidate.document.docType == request.itemsRequest.docType }
                            ?: return IdkError
                                .NOT_FOUND_ERROR(resource = request.itemsRequest.docType.toString())
                                .asErrorResult()
                    transferManager.signDocument(
                        request = request,
                        document = selectedDocument.document,
                        deviceKeyInfo = selectedDocument.signingKeyInfo(),
                        deviceNamespaces = DeviceNameSpaces(mapOf()),
                    )
                }
            DeviceResponse
                .Builder()
                .withDocuments(signedDocuments.toTypedArray())
                .build()
                .asOkResult()
        } catch (expected: Exception) {
            IdkError
                .UNKNOWN_ERROR(
                    message = "Failed to create ISO 18013 device response: ${expected.message}",
                    exception = expected,
                ).asErrorResult()
        }
    }
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
