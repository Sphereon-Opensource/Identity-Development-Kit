/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.core.hpke

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.interop.derPrivateKeyToJwk
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.EcdhUtils
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class Rfc9180HpkeProviderTest {
    @Test
    fun baseModeRoundTripsPlaintextAndBindsInfoAndAad() =
        runTest {
            val generated = EcdhUtils.generateEphemeralKeyPair(Curve.P_256)
            val privateJwk =
                derPrivateKeyToJwk(generated.privateKeyDer).copy(
                    x = generated.publicKeyJwk.x,
                    y = generated.publicKeyJwk.y,
                    use = JwkUse.enc.value,
                )
            val recipientPrivateKey = CoseJoseKeyMappingService.toCoseKey(privateJwk)
            val recipientPublicKey = CoseJoseKeyMappingService.toCoseKey(generated.publicKeyJwk)
            val provider = Rfc9180HpkeProvider()
            val suite = HpkeSuite.DHKEM_P256_HKDF_SHA256_HKDF_SHA256_AES_128_GCM
            val info = "protocol-transcript".encodeToByteArray()
            val aad = "authenticated-context".encodeToByteArray()
            val plaintext = "generic HPKE payload".encodeToByteArray()

            assertTrue(provider.supports(suite, recipientPublicKey))
            val sealed = provider.seal(plaintext, recipientPublicKey, suite, info, aad).getOrThrow()
            val opened = provider.open(
                sealed,
                recipientPrivateKey,
                suite,
                info,
                aad,
                recipientPublicKey = recipientPublicKey,
            ).getOrThrow()
            assertContentEquals(plaintext, opened)

            assertTrue(provider.open(sealed, recipientPrivateKey, suite, "other".encodeToByteArray(), aad).isErr)
            assertTrue(provider.open(sealed, recipientPrivateKey, suite, info, "other".encodeToByteArray()).isErr)
        }

    @Test
    fun openRejectsAKeyThatDoesNotMatchTheBoundRecipient() =
        runTest {
            val recipient = EcdhUtils.generateEphemeralKeyPair(Curve.P_256)
            val recipientJwk =
                derPrivateKeyToJwk(recipient.privateKeyDer).copy(
                    x = recipient.publicKeyJwk.x,
                    y = recipient.publicKeyJwk.y,
                    use = JwkUse.enc.value,
                )
            val other = EcdhUtils.generateEphemeralKeyPair(Curve.P_256)
            val otherJwk =
                derPrivateKeyToJwk(other.privateKeyDer).copy(
                    x = other.publicKeyJwk.x,
                    y = other.publicKeyJwk.y,
                    use = JwkUse.enc.value,
                )
            val recipientPrivateKey = CoseJoseKeyMappingService.toCoseKey(recipientJwk)
            val recipientPublicKey = CoseJoseKeyMappingService.toCoseKey(recipient.publicKeyJwk)
            val otherKey = CoseJoseKeyMappingService.toCoseKey(otherJwk)
            val provider = Rfc9180HpkeProvider()
            val sealed = provider.seal("payload".encodeToByteArray(), recipientPublicKey).getOrThrow()

            assertTrue(
                provider.open(
                    sealed,
                    otherKey,
                    recipientPublicKey = recipientPublicKey,
                ).isErr,
            )
        }

    @Test
    fun baseModeSupportsAnEmptyPlaintext() =
        runTest {
            val recipient = EcdhUtils.generateEphemeralKeyPair(Curve.P_256)
            val recipientJwk =
                derPrivateKeyToJwk(recipient.privateKeyDer).copy(
                    x = recipient.publicKeyJwk.x,
                    y = recipient.publicKeyJwk.y,
                    use = JwkUse.enc.value,
                )
            val recipientPrivateKey = CoseJoseKeyMappingService.toCoseKey(recipientJwk)
            val recipientPublicKey = CoseJoseKeyMappingService.toCoseKey(recipient.publicKeyJwk)
            val provider = Rfc9180HpkeProvider()

            val sealed = provider.seal(ByteArray(0), recipientPublicKey).getOrThrow()
            assertContentEquals(
                ByteArray(0),
                provider.open(sealed, recipientPrivateKey, recipientPublicKey = recipientPublicKey).getOrThrow(),
            )
        }

    @Test
    fun openMatchesRfc9180AppendixA3BaseVector() =
        runTest {
            // RFC 9180 A.3.1, mode 0, first encryption. This is an external deterministic
            // vector and catches labeled-KDF ordering errors that a local round trip cannot.
            val recipient =
                Jwk.Builder()
                    .withKty(JwaKeyType.EC)
                    .withCrv(JwaCurve.P_256)
                    // RFC text wraps the uncompressed point across an arbitrary 60-character
                    // line boundary; concatenate first, then split after the 04 prefix.
                    .withX(b64("fe8c19ce0905191ebc298a9245792531f26f0cece2460639e8bc39cb7f706a82"))
                    .withY(b64("6a779b4cf969b8a0e539c7f62fb3d30ad6aa8f80e30f1d128aafd68a2ce72ea0"))
                    .withD(b64("f3ce7fdae57e1a310d87f1ebbde6f328be0a99cdbcadf4d6589cf29de4b8ffd2"))
                    .withUse(JwkUse.enc.value)
                    .build()
            val provider = Rfc9180HpkeProvider()
            val ciphertext =
                com.sphereon.crypto.core.hpke.HpkeCiphertext(
                    enc = hex("04a92719c6195d5085104f469a8b9814d5838ff72b60501e2c4466e5e67b325ac98536d7b61a1af4b78e5b7f951c0900be863c403ce65c9bfcb9382657222d18c4"),
                    cipherText = hex("5ad590bb8baa577f8619db35a36311226a896e7342a6d836d8b7bcd2f20b6c7f9076ac232e3ab2523f39513434"),
                )

            val opened =
                provider.open(
                    ciphertext = ciphertext,
                    recipientPrivateKey = CoseJoseKeyMappingService.toCoseKey(recipient),
                    info = hex("4f6465206f6e2061204772656369616e2055726e"),
                    associatedData = hex("436f756e742d30"),
                ).getOrThrow()

            assertContentEquals(hex("4265617574792069732074727574682c20747275746820626561757479"), opened)
        }

    private fun hex(value: String): ByteArray = value.decodeFrom(Encoding.HEX)

    private fun b64(value: String): String = hex(value).encodeToBase64Url()
}
