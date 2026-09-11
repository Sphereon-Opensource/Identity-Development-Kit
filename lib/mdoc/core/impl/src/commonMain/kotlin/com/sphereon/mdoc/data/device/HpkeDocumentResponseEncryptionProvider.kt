/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.mdoc.data.device

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyCborCodec
import com.sphereon.crypto.core.cose.CoseKeyCborCodecImpl
import com.sphereon.crypto.core.hpke.HpkeProvider
import com.sphereon.crypto.core.hpke.HpkeSuite
import com.sphereon.mdoc.SessionTranscriptCborCodec
import com.sphereon.mdoc.SessionTranscriptCborCodecImpl
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException

/**
 * RFC 9180 HPKE Base-mode provider for ISO/IEC 18013-5 document-response encryption.
 *
 * The ballot-resolution profile is fixed to DHKEM(P-256, HKDF-SHA256), HKDF-SHA256 and
 * AES-128-GCM. The provider rejects other curves/parameters rather than silently switching to a
 * different KEM or content-encryption scheme.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DocumentResponseEncryptionProvider>())
class HpkeDocumentResponseEncryptionProvider(
    private val plaintextCodec: EncryptedDocumentsPlaintextCborCodec = EncryptedDocumentsPlaintextCborCodecImpl(),
    private val sessionTranscriptCodec: SessionTranscriptCborCodec = SessionTranscriptCborCodecImpl(),
    private val coseKeyCodec: CoseKeyCborCodec = CoseKeyCborCodecImpl(),
    private val hpkeProvider: HpkeProvider,
) : DocumentResponseEncryptionProvider {
    override fun supports(parameters: EncryptionParameters): Boolean =
        hpkeProvider.supports(HpkeSuite.DHKEM_P256_HKDF_SHA256_HKDF_SHA256_AES_128_GCM, parameters.recipientPublicKey)

    override suspend fun encrypt(
        plaintext: EncryptedDocumentsPlaintext,
        parameters: EncryptionParameters,
        sessionTranscript: SessionTranscript,
        docRequestID: UInt,
    ): IdkResult<EncryptedDocuments, IdkError> =
        try {
            require(!plaintext.documents.isNullOrEmpty() || !plaintext.zkDocuments.isNullOrEmpty()) {
                "EncryptedDocumentsPlaintext must contain at least one document"
            }
            val plainBytes = plaintextCodec.encode(plaintext).getOrThrow()
            hpkeProvider.seal(
                plaintext = plainBytes,
                recipientPublicKey = parameters.recipientPublicKey,
                suite = HpkeSuite.DHKEM_P256_HKDF_SHA256_HKDF_SHA256_AES_128_GCM,
                info = sessionTranscriptBytes(sessionTranscript, parameters),
            ).map { encrypted ->
                EncryptedDocuments(enc = encrypted.enc, cipherText = encrypted.cipherText, docRequestID = docRequestID)
            }
        } catch (e: IllegalArgumentException) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to encrypt mdoc response: ${e.message}", throwable = e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to encrypt mdoc response: ${e.message}", exception = e))
        }

    override suspend fun decrypt(
        encrypted: EncryptedDocuments,
        recipientPrivateKey: CoseKey,
        sessionTranscript: SessionTranscript,
        parameters: EncryptionParameters,
    ): IdkResult<EncryptedDocumentsPlaintext, IdkError> =
        try {
            hpkeProvider.open(
                ciphertext = com.sphereon.crypto.core.hpke.HpkeCiphertext(
                    enc = encrypted.enc,
                    cipherText = encrypted.cipherText,
                ),
                recipientPrivateKey = recipientPrivateKey,
                suite = HpkeSuite.DHKEM_P256_HKDF_SHA256_HKDF_SHA256_AES_128_GCM,
                info = sessionTranscriptBytes(sessionTranscript, parameters),
                recipientPublicKey = parameters.recipientPublicKey,
            ).map { plaintextCodec.decode(it).getOrThrow() }
        } catch (e: IllegalArgumentException) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to decrypt mdoc response: ${e.message}", throwable = e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to decrypt mdoc response: ${e.message}", exception = e))
        }

    /**
     * 12.6.4 replaces the reader-key/null slot with EncryptionParametersBytes for this
     * mechanism.  Build that replacement from the already encoded transcript so the device
     * engagement and handover retain their exact wire representation.
     */
    private fun sessionTranscriptBytes(value: SessionTranscript, parameters: EncryptionParameters): ByteArray {
        val encoded = sessionTranscriptCodec.encode(value).getOrThrow()
        val structure = Cbor.tryDecode(encoded).getOrThrow() as? CborArray<*>
            ?: error("SessionTranscript must be a CBOR array")
        require(structure.value.size == 3) { "SessionTranscript must contain three elements" }
        val encryptionParametersBytes =
            CborEncodedItem(
                Cbor.encode(encodeEncryptionParameters(parameters, coseKeyCodec)),
                parameters,
            )
        @Suppress("UNCHECKED_CAST")
        return Cbor.encode(
            CborArray(
                mutableListOf(
                    structure.value[0] as CborItem<*>,
                    encryptionParametersBytes,
                    structure.value[2] as CborItem<*>,
                ),
            ),
        )
    }

}
