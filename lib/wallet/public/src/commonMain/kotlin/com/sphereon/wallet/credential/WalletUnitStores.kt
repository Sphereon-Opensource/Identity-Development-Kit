/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.wallet.credential

/**
 * Tier 2 aggregate of the wallet-unit-scoped persistence stores: persisted credential
 * records/metadata and resumable OID4VCI issuance sessions.
 *
 * This exists so product facades (Tier 1, e.g. wallet/app's credential queries facade) can depend
 * on a single injected type instead of two, and so wallet-level orchestration no longer needs to
 * expose raw store access directly to get at either one. Session-scoped implementations simply
 * compose the existing [WalletCredentialStore] and [WalletIssuanceSessionStore] bindings; this
 * interface adds no behavior of its own.
 *
 * Not to be confused with the unrelated [WalletUnitStore] (singular), which persists the
 * wallet-unit records themselves rather than aggregating per-unit credential/session stores.
 */
interface WalletUnitStores {
    val credentials: WalletCredentialStore
    val issuanceSessions: WalletIssuanceSessionStore
}
