/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.sphereon.wallet.wsca

import com.sphereon.wallet.unit.WalletAttestedKeyRef
import kotlin.test.Test
import kotlin.test.assertContentEquals

class WscaPreparedSigningTest {
    @Test
    fun preparedSigningOwnsAnImmutableCopyOfExactSigningBytes() {
        val input = byteArrayOf(1, 2, 3)
        val prepared =
            WscaPreparedSigningFactory.create().mint(
                walletUnitId = "wallet-unit-1",
                keyRef = WalletAttestedKeyRef("key-1", "ES256", walletUnitId = "wallet-unit-1"),
                walletAccountId = null,
                operationBinding = "operation-1",
                operationType = "wallet.wsca.test.sign",
                digestBinding = "sha256:test",
                nonce = "nonce-1",
                audience = "audience-1",
                signingInput = input,
            )

        input[0] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), prepared.signingInput)

        val exposed = prepared.signingInput
        exposed[1] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), prepared.signingInput)
    }
}
