/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wsca.impl

import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.wsca.Wsca
import dev.zacsweers.metro.ContributesTo

/**
 * Session-graph accessor for [Wsca], mirroring the `WalletUnitStoresGraph`
 * (`com.sphereon.wallet.credential.store.WalletUnitStoresGraph`) precedent. Consumers that
 * manually assemble a session (composition roots such as `WalletBootstrap`, rather than
 * Metro-constructed classes that simply take a [Wsca] constructor parameter) reach the
 * session-scoped [LocalWsca] binding by casting `SessionInstance.graph` to this interface.
 *
 * This is the only route composition-root tests have to session-scoped local-WSCD key
 * provisioning: they do not go through a facade, only through this graph accessor.
 */
@ContributesTo(SessionScope::class)
interface WscaGraph {
    val wsca: Wsca
}
