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

package com.sphereon.identity.idv.wallet

import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Instant

data class WalletDriverState(
    val executionId: String,
    val nodeId: String,
    val methodId: String,
    val callbackRef: String,
    val state: String?,
    val nonce: String,
    val request: AuthorizationRequest,
    val dcqlQuery: DcqlQuery,
    val issuedAt: Instant,
)

interface WalletDriverStateStore {
    suspend fun put(state: WalletDriverState)

    suspend fun get(
        executionId: String,
        nodeId: String,
    ): WalletDriverState?

    suspend fun remove(
        executionId: String,
        nodeId: String,
    ): WalletDriverState?
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<WalletDriverStateStore>())
class InMemoryWalletDriverStateStore : WalletDriverStateStore {
    private val states = mutableMapOf<String, WalletDriverState>()

    override suspend fun put(state: WalletDriverState) {
        states[key(state.executionId, state.nodeId)] = state
    }

    override suspend fun get(
        executionId: String,
        nodeId: String,
    ): WalletDriverState? = states[key(executionId, nodeId)]

    override suspend fun remove(
        executionId: String,
        nodeId: String,
    ): WalletDriverState? = states.remove(key(executionId, nodeId))

    private fun key(
        executionId: String,
        nodeId: String,
    ): String = "$executionId:$nodeId"
}
