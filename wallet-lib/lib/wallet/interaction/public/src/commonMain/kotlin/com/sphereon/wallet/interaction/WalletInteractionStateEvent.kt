/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlinx.serialization.Serializable

@Serializable
data class WalletInteractionStateEvent(
    val sessionId: WalletInteractionSessionId,
    val revision: Long,
    val replayable: Boolean = true,
    val state: WalletInteractionState,
)
