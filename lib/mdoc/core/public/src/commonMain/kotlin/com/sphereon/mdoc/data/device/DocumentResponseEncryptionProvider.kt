/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.mdoc.data.device

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.mdoc.transfer.reader.SessionTranscript

/**
 * Provider SPI for the second-edition encrypted-document response.
 *
 * Implementations must bind the HPKE `info` input to the exact session transcript used by the
 * transfer. There is deliberately no clear-document fallback when a request asks for encryption.
 */
interface DocumentResponseEncryptionProvider {
    /** Whether this provider can process the requested recipient key and parameters. */
    fun supports(parameters: EncryptionParameters): Boolean

    suspend fun encrypt(
        plaintext: EncryptedDocumentsPlaintext,
        parameters: EncryptionParameters,
        sessionTranscript: SessionTranscript,
        docRequestID: UInt,
    ): IdkResult<EncryptedDocuments, IdkError>

    suspend fun decrypt(
        encrypted: EncryptedDocuments,
        recipientPrivateKey: com.sphereon.crypto.core.cose.CoseKey,
        sessionTranscript: SessionTranscript,
        parameters: EncryptionParameters,
    ): IdkResult<EncryptedDocumentsPlaintext, IdkError>
}

/** Shared fail-closed provider selection for holder and verifier integrations. */
object DocumentResponseEncryptionProviderResolver {
    fun resolve(
        parameters: EncryptionParameters,
        providers: Iterable<DocumentResponseEncryptionProvider>,
    ): IdkResult<DocumentResponseEncryptionProvider, IdkError> {
        val provider = providers.firstOrNull { it.supports(parameters) }
            ?: return Err(
                IdkError.fromString(
                    code = "MDOC_DOCUMENT_RESPONSE_ENCRYPTION_UNAVAILABLE",
                    message = "No compatible document-response encryption provider is available",
                ),
            )
        return Ok(provider)
    }
}
