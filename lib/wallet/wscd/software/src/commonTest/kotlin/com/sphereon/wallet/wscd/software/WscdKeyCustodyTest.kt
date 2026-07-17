/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd.software

import com.sphereon.crypto.core.KeyInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-function coverage for [wscdCustodyEvidence], runs on every target (including jsNodeTest)
 * since it needs no KMS or WebCrypto: it is a plain [KeyCustody] -> evidence-map mapping.
 */
class WscdKeyCustodyTest {
    @Test
    fun kmsCustodyEvidenceIsEmpty() {
        val custody = KeyCustody.Kms(KeyInfo<Nothing>(alias = "wallet-units/wallet-a/wallet_credential_proof/es256"))

        val evidence = wscdCustodyEvidence(custody)

        assertTrue(evidence.isEmpty(), "KMS-held software keys make no platform attestation claim")
    }

    @Test
    fun browserWebCryptoCustodyEvidenceIsHonestAndBoundedToTheSoftwareProfile() {
        val evidence = wscdCustodyEvidence(KeyCustody.BrowserWebCrypto)

        assertEquals("browser-webcrypto", evidence["custody"])
        assertEquals("true", evidence["non_exportable"])
        // Software-profile ceiling: never an ISO 18045 (hardware assurance) claim, for either
        // custody kind.
        assertTrue(evidence.keys.none { it.contains("iso_18045", ignoreCase = true) })
        assertTrue(evidence.values.none { it.contains("iso_18045", ignoreCase = true) })
    }
}
