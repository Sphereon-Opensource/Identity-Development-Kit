/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.openid.oid4vp.universal.impl

import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.interaction.WalletCounterpartyEncounterRegistry
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Neutral encounter tracking for the platform-specific universal OID4VP test graphs. */
@ContributesTo(SessionScope::class)
interface UniversalOid4vpTestWalletInteractionBindings {
    @Provides
    @SingleIn(SessionScope::class)
    fun counterpartyEncounterRegistry(): WalletCounterpartyEncounterRegistry =
        WalletCounterpartyEncounterRegistry.none
}
