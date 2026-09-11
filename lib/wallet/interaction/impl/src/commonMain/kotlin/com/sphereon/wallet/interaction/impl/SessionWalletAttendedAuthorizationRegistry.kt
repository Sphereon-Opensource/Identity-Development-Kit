/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.WalletAttendedAuthorizationRegistry
import com.sphereon.wallet.interaction.WalletSecurityGrant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One-use attended authorizations held only for the authenticated DI session. */
class SessionWalletAttendedAuthorizationRegistry : WalletAttendedAuthorizationRegistry {
    private val lock = Mutex()
    private val grants = mutableMapOf<AuthorizationKey, WalletSecurityGrant>()

    override suspend fun authorize(
        walletUnitId: String,
        operationBinding: String,
        grant: WalletSecurityGrant,
    ) {
        require(walletUnitId.isNotBlank()) { "wallet_attended_authorization_wallet_unit_blank" }
        require(operationBinding.isNotBlank()) { "wallet_attended_authorization_operation_binding_blank" }
        lock.withLock {
            grants[AuthorizationKey(walletUnitId, operationBinding)] = grant
        }
    }

    override suspend fun consume(
        walletUnitId: String,
        operationBinding: String,
    ): WalletSecurityGrant? =
        lock.withLock {
            grants.remove(AuthorizationKey(walletUnitId, operationBinding))
        }

    private data class AuthorizationKey(
        val walletUnitId: String,
        val operationBinding: String,
    )
}
