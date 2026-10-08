/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.wscd.software

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

/** Node-target proof that the wallet authority never selects page-lifetime browser custody. */
class WscdKeyCustodyJsTest {
    @Test
    fun nodeUsesTheConfiguredKmsForEcdsaInsteadOfBrowserWebCrypto() =
        runTest {
            assertNull(
                tryGenerateBrowserWscdKeyPair(
                    alias = "node-wallet-holder-key",
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                ),
                "Node must fall back to the configured durable KMS even when globalThis.crypto exists",
            )
        }

    @Test
    fun unsupportedAlgorithmsAlsoUseTheConfiguredKms() =
        runTest {
            assertNull(
                tryGenerateBrowserWscdKeyPair(
                    alias = "node-wallet-rsa-key",
                    algorithm = SignatureAlgorithm.RSA_SHA256,
                ),
            )
        }
}
