/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.holder.VerifierInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Oid4vpVerifierClientAuthenticationTest {
    @Test
    fun validatedX509ClientCarriesTheBareIdentifierAndTheValidatedRequestObjectChain() {
        val authentication =
            VerifierInfo(
                clientId = "x509_san_dns:verifier.example",
                clientIdScheme = ClientIdScheme.X509_SAN_DNS,
                requestObjectCertificateChain = listOf("bGVhZg==", "Y2E="),
            ).toWalletCounterpartyClientAuthentication()

        assertEquals("x509_san_dns", authentication?.scheme)
        assertEquals("verifier.example", authentication?.identifier)
        assertEquals(listOf("bGVhZg==", "Y2E="), authentication?.certificateChain)
    }

    @Test
    fun validatedX509HashClientCarriesTheHashAndTheValidatedRequestObjectChain() {
        val authentication =
            VerifierInfo(
                clientId = "x509_hash:Uvo3HtuIxuhC92rShpgqcT3YXwrqRxWEviRiA0OZszk",
                clientIdScheme = ClientIdScheme.X509_HASH,
                requestObjectCertificateChain = listOf("bGVhZg==", "Y2E="),
            ).toWalletCounterpartyClientAuthentication()

        assertEquals("x509_hash", authentication?.scheme)
        assertEquals("Uvo3HtuIxuhC92rShpgqcT3YXwrqRxWEviRiA0OZszk", authentication?.identifier)
        assertEquals(listOf("bGVhZg==", "Y2E="), authentication?.certificateChain)
    }

    @Test
    fun validatedDecentralizedIdentifierClientCarriesTheBareDid() {
        val authentication =
            VerifierInfo(
                clientId = "decentralized_identifier:did:web:verifier.example",
                clientIdScheme = ClientIdScheme.DECENTRALIZED_IDENTIFIER,
            ).toWalletCounterpartyClientAuthentication()

        assertEquals("decentralized_identifier", authentication?.scheme)
        assertEquals("did:web:verifier.example", authentication?.identifier)
    }

    @Test
    fun clientIdTheHolderCouldNotValidateYieldsNoClientAuthentication() {
        val authentication =
            VerifierInfo(
                clientId = "x509_san_dns:verifier.example",
                clientIdScheme = ClientIdScheme.X509_SAN_DNS,
                clientIdValid = false,
                requestObjectCertificateChain = listOf("bGVhZg=="),
            ).toWalletCounterpartyClientAuthentication()

        assertNull(authentication)
    }

    @Test
    fun bareClientIdWithADeclaredSchemeKeepsItsIdentifier() {
        val authentication =
            VerifierInfo(
                clientId = "verifier.example",
                clientIdScheme = ClientIdScheme.X509_SAN_DNS,
                requestObjectCertificateChain = listOf("bGVhZg=="),
            ).toWalletCounterpartyClientAuthentication()

        assertEquals("x509_san_dns", authentication?.scheme)
        assertEquals("verifier.example", authentication?.identifier)
    }
}
