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

package com.sphereon.oauth2.server.authorization.policy

import com.sphereon.core.api.IdkResult
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError

/**
 * Token Exchange Policy SPI (RFC 8693)
 *
 * Pluggable policy for evaluating token exchange requests.
 * Implementations can enforce custom authorization logic such as:
 * - Which clients can perform token exchange
 * - Whether delegation or impersonation is allowed
 * - Scope/audience/resource restrictions
 * - may_act claim enforcement
 */
interface TokenExchangePolicy {
    suspend fun evaluate(request: TokenExchangePolicyRequest): IdkResult<TokenExchangePolicyDecision, AuthorizationServerError>
}

/**
 * Policy evaluation input
 */
data class TokenExchangePolicyRequest(
    val clientId: String,
    val subjectTokenClaims: Map<String, Any>,
    val subjectTokenType: String,
    val subjectTokenVerified: Boolean,
    val actorTokenClaims: Map<String, Any>?,
    val actorTokenType: String?,
    val actorTokenVerified: Boolean?,
    val requestedResources: List<String>,
    val requestedAudiences: List<String>,
    val requestedScope: String?,
    val requestedTokenType: String?,
)

/**
 * Policy evaluation result
 */
data class TokenExchangePolicyDecision(
    val allowed: Boolean,
    val isDelegation: Boolean,
    val issuedTokenType: String,
    val grantedScope: String?,
    val grantedAudience: List<String>,
    val additionalClaims: Map<String, Any> = emptyMap(),
    val denyReason: String? = null,
)
