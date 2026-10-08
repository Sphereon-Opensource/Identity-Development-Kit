/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.wallet.wsca.impl

import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.wsca.Wsca
import dev.zacsweers.metro.ContributesTo

/** Local WSCA graph accessor for test and local-custody compositions. */
@ContributesTo(SessionScope::class)
interface WscaGraph {
    val wsca: Wsca
}
