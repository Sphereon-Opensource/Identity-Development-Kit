/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.wallet.interaction

/**
 * Session-local hand-off from a validated wallet-interaction security grant to the WSCA operation
 * that immediately follows it. Implementations bind grants to both the Wallet Unit and the opaque
 * operation binding, and consume an authorization at most once.
 */
interface WalletAttendedAuthorizationRegistry {
    suspend fun authorize(
        walletUnitId: String,
        operationBinding: String,
        grant: WalletSecurityGrant,
    )

    suspend fun consume(
        walletUnitId: String,
        operationBinding: String,
    ): WalletSecurityGrant?

    companion object {
        val none: WalletAttendedAuthorizationRegistry =
            object : WalletAttendedAuthorizationRegistry {
                override suspend fun authorize(
                    walletUnitId: String,
                    operationBinding: String,
                    grant: WalletSecurityGrant,
                ) = Unit

                override suspend fun consume(
                    walletUnitId: String,
                    operationBinding: String,
                ): WalletSecurityGrant? = null
            }
    }
}
