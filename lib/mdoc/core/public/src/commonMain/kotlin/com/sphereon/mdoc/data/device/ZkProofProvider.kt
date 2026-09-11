/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.mdoc.data.device

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.mdoc.transfer.reader.SessionTranscript

/**
 * SPI for a concrete ISO/IEC 18013-5 ZKP implementation.
 *
 * The mdoc core owns negotiation and wire encoding, but deliberately does not pretend to create
 * or verify a proof. Providers must advertise every system identifier they implement. A required
 * ZKP request is rejected when no provider can satisfy any advertised system specification.
 */
interface ZkProofProvider {
    val supportedSystemIds: Set<String>

    fun supports(systemId: String): Boolean = systemId in supportedSystemIds

    suspend fun createProof(
        request: ZkRequest,
        documentData: ZkDocumentData,
        sessionTranscript: SessionTranscript?,
    ): IdkResult<ByteArray, IdkError>

    suspend fun verifyProof(
        request: ZkRequest,
        document: ZkDocument,
        sessionTranscript: SessionTranscript?,
    ): IdkResult<Boolean, IdkError>
}

/**
 * Creates the complete second-edition ZkDocument envelope after provider negotiation. Keeping
 * this operation next to the SPI prevents holder implementations from accidentally returning a
 * clear Document after a provider has accepted a ZKP request.
 */
suspend fun ZkProofProvider.createZkDocument(
    request: ZkRequest,
    document: Document,
    sessionTranscript: SessionTranscript?,
): IdkResult<ZkDocument, IdkError> {
    val spec = request.systemSpecs.firstOrNull { supports(it.zkSystemId) }
        ?: return Err(
            IdkError.fromString(
                code = "MDOC_ZKP_SYSTEM_UNSUPPORTED",
                message = "The selected ZkProofProvider does not support any requested ZKP system",
            ),
        )
    val data =
        try {
            document.toZkDocumentData(spec.zkSystemId)
        } catch (e: IllegalArgumentException) {
            return Err(
                IdkError.fromString(
                    code = "MDOC_ZKP_DOCUMENT_DATA_INVALID",
                    message = e.message ?: "The mdoc cannot be represented as ZkDocumentData",
                ),
            )
        }
    val proof = createProof(request, data, sessionTranscript)
    if (proof.isErr) {
        return Err(proof.error)
    }
    return Ok(ZkDocument(data, proof.value))
}

/**
 * Resolves the negotiated provider without an unsafe fallback. Keep this small utility in the
 * public module so holder and verifier integrations apply the same fail-closed rule.
 */
object ZkProofProviderResolver {
    fun resolve(
        request: ZkRequest,
        providers: Iterable<ZkProofProvider>,
    ): IdkResult<ZkProofProvider?, IdkError> {
        val provider = providers.firstOrNull { candidate -> request.systemSpecs.any { candidate.supports(it.zkSystemId) } }
        if (provider == null && request.zkRequired) {
            return Err(
                IdkError.fromString(
                    code = "MDOC_ZKP_PROVIDER_UNAVAILABLE",
                    message = "The request requires ZKP but no compatible ZkProofProvider is available",
                ),
            )
        }
        return Ok(provider)
    }
}
