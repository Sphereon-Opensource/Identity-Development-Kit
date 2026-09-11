/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.wallet.interaction

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.wallet.unit.WalletAttestedKeyRef

/** Holder-facing contract for provisioning and registering a wallet credential key. */
interface WalletHolderKeyProvisioningService {
    suspend fun provision(
        walletUnitId: String,
        signingAlgorithm: SignatureAlgorithm,
    ): WalletAttestedKeyRef
}
