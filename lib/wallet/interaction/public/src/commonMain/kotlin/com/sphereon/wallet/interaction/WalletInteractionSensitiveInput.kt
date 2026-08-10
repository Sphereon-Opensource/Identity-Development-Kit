/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlinx.serialization.Serializable

/** A bearer reference to sensitive protocol material. The referenced value never enters public state. */
@Serializable
data class WalletInteractionSensitiveInputRef(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "wallet_interaction_sensitive_input_ref_blank" }
    }

    override fun toString(): String = "WalletInteractionSensitiveInputRef([redacted])"
}

@Serializable
enum class WalletInteractionSensitiveInputPurpose {
    OID4VCI_TRANSACTION_CODE,
    OID4VCI_AUTHORIZATION_HANDOFF,
    OID4VCI_AUTHORIZATION_CALLBACK,
    PROTOCOL_COMPLETION_HANDOFF,
    INTERACTION_SECURITY_GRANT,
}

/**
 * Non-serializable, session-scoped authority for one-use sensitive inputs. Implementations must
 * bind every value to both the interaction session and [WalletInteractionSensitiveInputPurpose],
 * return it at most once, and remove all outstanding values when the interaction terminates.
 */
interface WalletInteractionSensitiveInputAuthority {
    suspend fun register(
        sessionId: WalletInteractionSessionId,
        purpose: WalletInteractionSensitiveInputPurpose,
        value: String,
    ): WalletInteractionSensitiveInputRef

    suspend fun consume(
        sessionId: WalletInteractionSessionId,
        purpose: WalletInteractionSensitiveInputPurpose,
        ref: WalletInteractionSensitiveInputRef,
    ): String?

    suspend fun registerSecurityGrant(
        sessionId: WalletInteractionSessionId,
        grant: WalletSecurityGrant,
    ): WalletInteractionSensitiveInputRef

    suspend fun consumeSecurityGrant(
        sessionId: WalletInteractionSessionId,
        ref: WalletInteractionSensitiveInputRef,
    ): WalletSecurityGrant?

    suspend fun clear(sessionId: WalletInteractionSessionId)
}
