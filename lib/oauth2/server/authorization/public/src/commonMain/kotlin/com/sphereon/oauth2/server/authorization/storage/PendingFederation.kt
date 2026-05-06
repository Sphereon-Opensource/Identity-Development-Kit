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
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.provider.FlowContext
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
 * Tenant context is intentionally NOT carried on this record. The federation provider is
 * `SessionScope` and reads tenant from `SessionExecution` at the point of use, so a stale
 * tenant captured at initiate time can never override the live session at callback time.
 */
@Serializable
data class PendingFederation(
    val sessionId: String,
    val state: String,
    val nonce: String,
    val pkceData: PkceData?,
    val metadata: AuthorizationServerMetadata,
    val returnUrl: String,
    val callbackRedirectUri: String,
    val completed: Boolean = false,
    val userId: String? = null,
    val authenticatedAt: Instant? = null,
    val providerId: String = "default",
    val flowContext: FlowContext? = null,
    val upstreamAcr: String? = null,
    val upstreamAmr: List<String>? = null,
)
