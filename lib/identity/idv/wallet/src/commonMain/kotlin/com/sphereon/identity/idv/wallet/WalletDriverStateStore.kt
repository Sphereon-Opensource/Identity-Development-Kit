package com.sphereon.identity.idv.wallet

import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import kotlinx.datetime.Instant
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

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
    suspend fun get(executionId: String, nodeId: String): WalletDriverState?
    suspend fun remove(executionId: String, nodeId: String): WalletDriverState?
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<WalletDriverStateStore>())
class InMemoryWalletDriverStateStore : WalletDriverStateStore {
    private val states = mutableMapOf<String, WalletDriverState>()

    override suspend fun put(state: WalletDriverState) {
        states[key(state.executionId, state.nodeId)] = state
    }

    override suspend fun get(executionId: String, nodeId: String): WalletDriverState? =
        states[key(executionId, nodeId)]

    override suspend fun remove(executionId: String, nodeId: String): WalletDriverState? =
        states.remove(key(executionId, nodeId))

    private fun key(executionId: String, nodeId: String): String = "$executionId:$nodeId"
}
