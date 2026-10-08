/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.interaction.WalletInteractionDiagnostics
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** Routes wallet interaction diagnostics to the session log. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletInteractionDiagnostics>())
class SessionLogWalletInteractionDiagnostics(
    private val log: SessionLogService,
) : WalletInteractionDiagnostics {
    override fun warn(
        event: String,
        sessionId: WalletInteractionSessionId?,
        details: Map<String, String>,
    ) {
        val metadata = linkedMapOf(TAG_KEY to TAG)
        sessionId?.let { metadata["session"] = it.value }
        details.forEach { (name, value) -> metadata[name] = value.take(MAX_DETAIL_LENGTH) }
        log.warn(message = "$TAG $event", metadata = metadata)
    }

    private companion object {
        const val TAG = "wallet.interaction"
        const val TAG_KEY = "tag"
        const val MAX_DETAIL_LENGTH = 400
    }
}
