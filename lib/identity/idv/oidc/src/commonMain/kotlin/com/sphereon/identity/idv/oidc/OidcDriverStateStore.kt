package com.sphereon.identity.idv.oidc

import com.sphereon.oauth2.jwt.validation.OidcDiscoveryMetadata
import kotlinx.datetime.Instant
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

data class OidcDriverState(
    val executionId: String,
    val nodeId: String,
    val methodId: String,
    val state: String,
    val nonce: String,
    val callbackRef: String,
    val redirectUri: String,
    val clientId: String,
    val clientSecret: String?,
    val codeVerifier: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val metadata: OidcDiscoveryMetadata,
    val issuedAt: Instant,
    val expiresAt: Instant?,
)

interface OidcDriverStateStore {
    suspend fun put(state: OidcDriverState)
    suspend fun get(executionId: String, nodeId: String): OidcDriverState?
    suspend fun remove(executionId: String, nodeId: String): OidcDriverState?
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<OidcDriverStateStore>())
class InMemoryOidcDriverStateStore : OidcDriverStateStore {
    private val states = mutableMapOf<String, OidcDriverState>()

    override suspend fun put(state: OidcDriverState) {
        states[key(state.executionId, state.nodeId)] = state
    }

    override suspend fun get(executionId: String, nodeId: String): OidcDriverState? =
        states[key(executionId, nodeId)]

    override suspend fun remove(executionId: String, nodeId: String): OidcDriverState? =
        states.remove(key(executionId, nodeId))

    private fun key(executionId: String, nodeId: String): String = "$executionId:$nodeId"
}
