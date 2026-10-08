/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.storage

import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.provider.FlowContext
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteDecision
import com.sphereon.oauth2.server.authorization.model.NormalizedAuthenticationEvidence
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Pending federation flow state: persisted between the initiate-time redirect to the upstream
 * IdP and the callback carrying the authorization code back.
 *
 * Keyed at the storage layer by [state] (the OAuth2 CSRF token, globally unique).
 *
 * [completed] / [userId] / [authenticatedAt] are null until the upstream token exchange has
 * succeeded. Readers (for example `getAuthenticatedUser`) must filter on `completed = true` to
 * avoid racing readers against writers mid-token-exchange. The store guarantees atomicity
 * between the completion flag and any associated [CachedUserInfo] write via
 * [FederationSessionStore.completePendingFederation].
 *
 * Tenant, hosted-AS, binding and upstream revisions are pinned at initiation and compared with
 * the live callback context. They are evidence to reject substitution, never an override for the
 * callback's resolved tenant.
 *
 * [applicationId] is the opaque application / login-surface id the originating authorization
 * request resolved (see `AuthenticationContext.applicationId`). Captured at initiate time so
 * the callback can hand it to the `FederatedIdentityLinker`, which scopes identity upsert and
 * session-record writes to the application the user is actually logging in to. Null when the
 * flow is not bound to a specific application.
 */
@Serializable
data class PendingFederation(
    val tenantId: String,
    val hostedAuthorizationServerId: String,
    val hostedAuthorizationServerRevision: Long,
    val federationBindingId: String,
    val federationBindingRevision: Long,
    val upstreamAuthorizationServerId: String,
    val upstreamAuthorizationServerRevision: Long,
    val upstreamIssuer: String,
    val downstreamClientId: String,
    val authenticationRoute: AuthenticationRouteDecision,
    val sessionId: String,
    val state: String,
    val nonce: String,
    val pkceData: PkceData,
    val metadata: AuthorizationServerMetadata,
    val returnUrl: String,
    val callbackRedirectUri: String,
    val completed: Boolean = false,
    val userId: String? = null,
    val authenticatedAt: Instant? = null,
    val providerId: String,
    val flowContext: FlowContext? = null,
    val upstreamAcr: String? = null,
    val upstreamAmr: List<String>? = null,
    val applicationId: String? = null,
    val createdAt: Instant,
    val expiresAt: Instant,
    val callbackConsumedAt: Instant? = null,
    val evidence: NormalizedAuthenticationEvidence? = null,
) {
    init {
        require(state.isNotBlank() && nonce.isNotBlank() && state != nonce) { "Upstream state and nonce must be independent values" }
        require(pkceData.codeChallengeMethod == PkceMethod.S256) { "Upstream PKCE must use S256" }
        require(pkceData.codeVerifier.length in 43..128 && pkceData.codeChallenge.isNotBlank()) { "Upstream PKCE verifier/challenge is invalid" }
        require(createdAt < expiresAt) { "Pending federation expiry must follow creation" }
        require(authenticationRoute.selectedBindingId == federationBindingId) { "Pending federation binding must match the pinned route" }
        require(authenticationRoute.hostedAuthorizationServerId == hostedAuthorizationServerId) { "Pending federation hosted AS must match the pinned route" }
        require(authenticationRoute.hostedAuthorizationServerRevision == hostedAuthorizationServerRevision) {
            "Pending federation hosted AS revision must match the pinned route"
        }
        val selectedBinding =
            requireNotNull(authenticationRoute.eligibleBindings.singleOrNull { it.bindingId == federationBindingId }) {
                "Pending federation route must contain exactly one selected binding"
            }
        require(selectedBinding.upstreamResourceId == upstreamAuthorizationServerId) {
            "Pending federation upstream resource must match the pinned route"
        }
        require(selectedBinding.upstreamResourceRevision == upstreamAuthorizationServerRevision) {
            "Pending federation upstream resource revision must match the pinned route"
        }
        require(selectedBinding.bindingRevision == federationBindingRevision) {
            "Pending federation binding revision must match the pinned route"
        }
        require(selectedBinding.upstreamIssuer == upstreamIssuer && metadata.issuer == upstreamIssuer) {
            "Pending federation issuer must match the pinned route and discovery metadata"
        }
        require(providerId == federationBindingId) {
            "Pending federation provider selector must be the exact binding id"
        }
    }
}
