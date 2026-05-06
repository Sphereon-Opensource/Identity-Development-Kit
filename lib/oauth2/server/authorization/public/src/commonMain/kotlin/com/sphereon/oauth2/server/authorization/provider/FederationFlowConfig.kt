/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.provider

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Tunable timing for the federation authentication flow. Replaces the four `Duration` defaults
 * that used to live on `AbstractFederatedUserAuthenticationProvider`'s constructor. EDK reads
 * the corresponding `federation.*` properties via `PropertyResolver` and contributes a populated
 * binding; pure-IDK deployments keep the conservative built-in defaults.
 *
 * @property pendingTtl Lifetime of an in-flight federation record (`state -> PendingFederation`).
 *   Most upstream flows complete in seconds; anything older is a stale session.
 * @property claimsCacheTtl Lifetime of the federated claims cache entry written on successful
 *   completion.
 * @property sessionTtl Lifetime of the local `AuthenticationSessionRecord` when an
 *   `FederatedIdentityLinker` is wired. Aligns with browser-session expectations.
 */
interface FederationFlowConfig {
    val pendingTtl: Duration
    val claimsCacheTtl: Duration
    val sessionTtl: Duration

    companion object {
        val DEFAULT_PENDING_TTL: Duration = 10.minutes
        val DEFAULT_CLAIMS_CACHE_TTL: Duration = 5.minutes
        val DEFAULT_SESSION_TTL: Duration = 8.hours
    }
}
