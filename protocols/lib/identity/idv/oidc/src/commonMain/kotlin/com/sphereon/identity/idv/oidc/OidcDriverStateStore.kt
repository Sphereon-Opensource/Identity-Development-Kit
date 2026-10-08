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

package com.sphereon.identity.idv.oidc

import com.sphereon.oauth2.jwt.validation.OidcDiscoveryMetadata
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Instant

data class OidcDriverState(
    val executionId: String,
    val nodeId: String,
    val methodId: String,
    val state: String,
    val nonce: String,
    val callbackRef: String,
    val redirectUri: String,
    val clientId: String,
    val clientSecretId: String,
    val codeVerifier: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val metadata: OidcDiscoveryMetadata,
    val issuedAt: Instant,
    val expiresAt: Instant?,
)

interface OidcDriverStateStore {
    suspend fun put(state: OidcDriverState)

    suspend fun get(
        executionId: String,
        nodeId: String,
    ): OidcDriverState?

    suspend fun remove(
        executionId: String,
        nodeId: String,
    ): OidcDriverState?
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<OidcDriverStateStore>())
class InMemoryOidcDriverStateStore : OidcDriverStateStore {
    private val states = mutableMapOf<String, OidcDriverState>()

    override suspend fun put(state: OidcDriverState) {
        states[key(state.executionId, state.nodeId)] = state
    }

    override suspend fun get(
        executionId: String,
        nodeId: String,
    ): OidcDriverState? = states[key(executionId, nodeId)]

    override suspend fun remove(
        executionId: String,
        nodeId: String,
    ): OidcDriverState? = states.remove(key(executionId, nodeId))

    private fun key(
        executionId: String,
        nodeId: String,
    ): String = "$executionId:$nodeId"
}
