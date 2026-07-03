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

package com.sphereon.oauth2.server.authorization.impl.policy

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.TokenTypeIdentifier
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.policy.TokenExchangePolicy
import com.sphereon.oauth2.server.authorization.policy.TokenExchangePolicyDecision
import com.sphereon.oauth2.server.authorization.policy.TokenExchangePolicyRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default token exchange policy implementation.
 *
 * Policy that:
 * - Requires subject token signature verification (rejects unverified tokens)
 * - Requires actor token signature verification when present
 * - Uses delegation when actor_token is present, impersonation otherwise
 * - Defaults issued token type to access_token
 * - Enforces may_act claim on subject token when present
 * - Downscopes `scope` to what the subject token already carries (RFC 8693 §2.1:
 *   the issued token must be no broader than the subject's authorization); never
 *   grants a scope the subject did not hold
 * - Passes audience/resource through. The generic default cannot decide which
 *   audiences a client may target, so audience restriction is delegated to a
 *   deployment-specific policy (e.g. the platform admin-console exchange policy).
 *
 * Custom policy implementations can override this to accept unverified external tokens
 * (e.g. from trusted IdPs where signing keys aren't locally available) or to enforce a
 * per-client allow-list of grantable audiences.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<TokenExchangePolicy>())
class DefaultTokenExchangePolicy : TokenExchangePolicy {
    override suspend fun evaluate(request: TokenExchangePolicyRequest): IdkResult<TokenExchangePolicyDecision, AuthorizationServerError> {
        // Reject unverified subject tokens
        if (!request.subjectTokenVerified) {
            return Err(
                AuthorizationServerError.InvalidGrant(
                    details = "Subject token signature could not be verified",
                ),
            )
        }

        val isDelegation = request.actorTokenClaims != null

        // Reject unverified actor tokens
        if (isDelegation && request.actorTokenVerified == false) {
            return Err(
                AuthorizationServerError.InvalidGrant(
                    details = "Actor token signature could not be verified",
                ),
            )
        }

        // Enforce may_act claim if present on the subject token
        if (isDelegation) {
            val mayAct = request.subjectTokenClaims["may_act"]
            if (mayAct != null) {
                // may_act constrains which actors can act on behalf of the subject
                val mayActSub = extractMayActSub(mayAct)
                val actorSub = request.actorTokenClaims?.get("sub") as? String
                if (mayActSub != null && actorSub != null && mayActSub != actorSub) {
                    return Err(
                        AuthorizationServerError.AccessDenied(
                            reason = "Actor '$actorSub' is not authorized by may_act claim (expected '$mayActSub')",
                        ),
                    )
                }
            }
        }

        // Determine issued token type
        val issuedTokenType = request.requestedTokenType ?: TokenTypeIdentifier.ACCESS_TOKEN

        return Ok(
            TokenExchangePolicyDecision(
                allowed = true,
                isDelegation = isDelegation,
                issuedTokenType = issuedTokenType,
                grantedScope = downscope(request),
                grantedAudience = request.requestedAudiences,
                additionalClaims = authenticationContextClaims(request.subjectTokenClaims),
            ),
        )
    }

    /**
     * Bounds the issued scope by the subject token's own `scope` claim so the
     * exchanged token can never carry a scope the caller did not already hold.
     * When no scope is requested the subject's scope is inherited verbatim; when
     * a scope is requested only the subset the subject already holds is granted
     * (requested order preserved). A requested scope outside the subject's grant
     * is silently dropped rather than minted.
     */
    private fun downscope(request: TokenExchangePolicyRequest): String? {
        val subjectScopes =
            (request.subjectTokenClaims["scope"] as? String)
                ?.split(" ")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                .orEmpty()
        val requestedScopes =
            request.requestedScope
                ?.split(" ")
                ?.filter { it.isNotBlank() }
                ?: return (request.subjectTokenClaims["scope"] as? String)?.takeIf { it.isNotBlank() }
        return requestedScopes
            .filter { it in subjectScopes }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ")
    }

    private fun authenticationContextClaims(subjectClaims: Map<String, Any>): Map<String, Any> =
        buildMap {
            subjectClaims["acr"]?.let { put("acr", it) }
            subjectClaims["amr"]?.let { put("amr", it) }
            subjectClaims["auth_time"]?.let { put("auth_time", it) }
        }

    @Suppress("UNCHECKED_CAST")
    private fun extractMayActSub(mayAct: Any): String? =
        when (mayAct) {
            is Map<*, *> -> (mayAct as? Map<String, Any>)?.get("sub") as? String
            else -> null
        }
}
