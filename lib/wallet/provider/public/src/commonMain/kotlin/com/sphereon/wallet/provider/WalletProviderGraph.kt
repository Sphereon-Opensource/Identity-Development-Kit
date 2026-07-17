/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.provider

import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo

/**
 * Session-graph accessor for [WalletProvider]: consumers that only hold an
 * [com.sphereon.di.session.SessionInstance] (not a constructor-injected [WalletProvider]) reach the
 * session's bound provider by casting `SessionInstance.graph` to this interface, mirroring
 * `WalletUnitStoresGraph`'s (`lib-wallet-impl`) same cast-accessor pattern.
 *
 * This bridges the scope gap between [WalletProvider] bindings (`@SingleIn(SessionScope::class)` -
 * `LocalWalletProvider`'s OSS default, `RemoteWalletProvider`'s EDK/MANAGED adapter) and
 * `ProfileProvisioner` implementations (AppScope, merged into `DefaultProfileRegistry`'s multibound
 * `Set<ProfileProvisioner>`): a provisioner cannot resolve a SessionScope dependency through plain
 * constructor injection, so `DefaultProfileRegistry` creates the profile's context/session first and
 * passes it into `ProfileProvisioner.provision(request, session)`, which casts `session.graph` to
 * this interface instead.
 */
@ContributesTo(SessionScope::class)
interface WalletProviderGraph {
    val walletProvider: WalletProvider
}
