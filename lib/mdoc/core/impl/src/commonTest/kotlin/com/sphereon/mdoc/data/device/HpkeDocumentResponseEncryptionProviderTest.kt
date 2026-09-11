/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.mdoc.data.device

import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.hpke.Rfc9180HpkeProvider
import com.sphereon.crypto.core.interop.derPrivateKeyToJwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.EcdhUtils
import com.sphereon.mdoc.transfer.reader.Handover
import com.sphereon.mdoc.transfer.reader.QrHandover
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HpkeDocumentResponseEncryptionProviderTest {
    @Test
    fun encrypts_and_decrypts_second_edition_plaintext_with_rfc9180_hpke() =
        runTest {
            val generated = EcdhUtils.generateEphemeralKeyPair(Curve.P_256)
            // PKCS#8 private-key encodings may omit the optional public point.  Add the
            // already generated point before converting to COSE so the private JWK is
            // complete and can be thumbprinted.
            val privateJwk =
                derPrivateKeyToJwk(generated.privateKeyDer).copy(
                    x = generated.publicKeyJwk.x,
                    y = generated.publicKeyJwk.y,
                    use = JwkUse.enc.value,
                )
            val recipientPrivateKey = CoseJoseKeyMappingService.toCoseKey(privateJwk)
            val recipientPublicKey = CoseJoseKeyMappingService.toCoseKey(generated.publicKeyJwk)
            val parameters = EncryptionParameters(recipientPublicKey = recipientPublicKey)
            val transcript = SessionTranscript(handover = QrHandover() as Handover<*, com.sphereon.cbor.CborItem<*>>, original = null)
            val plaintext =
                EncryptedDocumentsPlaintext(
                    zkDocuments =
                        listOf(
                            ZkDocument(
                                documentData =
                                    ZkDocumentData(
                                        docType = DocType("org.iso.18013.5.1.mDL"),
                                        zkSystemId = "example-zk-system",
                                        timestamp = "2026-01-01",
                                    ),
                                proof = byteArrayOf(0x01, 0x02, 0x03),
                            ),
                        ),
                )

            val provider = HpkeDocumentResponseEncryptionProvider(hpkeProvider = Rfc9180HpkeProvider())
            assertTrue(provider.supports(parameters))
            val encrypted = provider.encrypt(plaintext, parameters, transcript, 7u).getOrThrow()

            assertEquals(65, encrypted.enc.size)
            assertEquals(7u, encrypted.docRequestID)
            val decrypted = provider.decrypt(encrypted, recipientPrivateKey, transcript, parameters).getOrThrow()
            assertEquals(plaintext, decrypted)
        }

    @Test
    fun refuses_a_private_key_that_does_not_match_the_requested_recipient() =
        runTest {
            val recipient = EcdhUtils.generateEphemeralKeyPair(Curve.P_256)
            val recipientPrivateJwk =
                derPrivateKeyToJwk(recipient.privateKeyDer).copy(
                    x = recipient.publicKeyJwk.x,
                    y = recipient.publicKeyJwk.y,
                    use = JwkUse.enc.value,
                )
            val recipientPublicKey = CoseJoseKeyMappingService.toCoseKey(recipient.publicKeyJwk)
            val parameters = EncryptionParameters(recipientPublicKey = recipientPublicKey)
            val other = EcdhUtils.generateEphemeralKeyPair(Curve.P_256)
            val otherPrivateJwk =
                derPrivateKeyToJwk(other.privateKeyDer).copy(
                    x = other.publicKeyJwk.x,
                    y = other.publicKeyJwk.y,
                    use = JwkUse.enc.value,
                )
            val otherKey = CoseJoseKeyMappingService.toCoseKey(otherPrivateJwk)
            val transcript = SessionTranscript(handover = QrHandover() as Handover<*, com.sphereon.cbor.CborItem<*>>, original = null)
            val provider = HpkeDocumentResponseEncryptionProvider(hpkeProvider = Rfc9180HpkeProvider())
            val encrypted =
                provider.encrypt(
                    EncryptedDocumentsPlaintext(documents = emptyList()),
                    parameters,
                    transcript,
                    0u,
                )

            // The plaintext constructor accepts the empty list for this test, but encryption
            // itself must reject it before an envelope is created.
            assertTrue(encrypted.isErr)

            val validEncrypted =
                provider.encrypt(
                    EncryptedDocumentsPlaintext(
                        zkDocuments =
                            listOf(
                                ZkDocument(
                                    documentData =
                                        ZkDocumentData(
                                            docType = DocType("org.iso.18013.5.1.mDL"),
                                            zkSystemId = "key-binding-test",
                                            timestamp = "2026-01-01",
                                        ),
                                    proof = byteArrayOf(1),
                                ),
                            ),
                    ),
                    parameters,
                    transcript,
                    0u,
                ).getOrThrow()
            assertTrue(provider.decrypt(validEncrypted, otherKey, transcript, parameters).isErr)
        }
}
