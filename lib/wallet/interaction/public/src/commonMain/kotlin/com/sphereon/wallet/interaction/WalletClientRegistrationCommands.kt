/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.wallet.interaction

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import kotlinx.serialization.Serializable

/**
 * Ensures the wallet-unit-held asymmetric key used when registering an OAuth client and returns
 * only its public registration data. The private key remains behind the WSCA/WSCD boundary.
 */
interface EnsureWalletClientRegistrationKeyCommand :
    ServiceCommand<EnsureWalletClientRegistrationKeyArgs, WalletClientRegistrationKey, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE

    companion object {
        const val COMMAND_ID: String = "wallet.interaction.ensure-client-registration-key"
    }
}

@Serializable
data class EnsureWalletClientRegistrationKeyArgs(
    val walletUnitId: String,
) {
    init {
        require(walletUnitId.isNotBlank()) { "wallet_client_registration_wallet_unit_id_blank" }
    }
}

@Serializable
data class WalletClientRegistrationKey(
    val keyId: String,
    val algorithm: String,
    val publicJwk: String,
)
