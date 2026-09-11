/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.credential.store

import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.credential.WalletUnitStore
import dev.zacsweers.metro.ContributesTo

/**
 * Public session-graph accessor for the wallet-unit store. The graph implementation remains a
 * final-app choice; consumers only depend on this contract and never on wallet-app-impl.
 */
@ContributesTo(SessionScope::class)
interface WalletUnitStoreGraph {
    val walletUnitStore: WalletUnitStore
}
