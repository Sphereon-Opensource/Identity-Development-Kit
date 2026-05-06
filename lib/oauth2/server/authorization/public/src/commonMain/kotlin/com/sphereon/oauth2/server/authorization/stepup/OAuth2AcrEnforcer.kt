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

package com.sphereon.oauth2.server.authorization.stepup

import com.sphereon.core.api.service.AuthAssuranceLevel

/**
 * OAuth2 authorize-flow step-up enforcer (RFC 9470 §4 / OIDC Core §3.1.2.1).
 *
 * Compares the achieved authentication state (AAL, AMR, ACR, auth_time) against
 * the request's `acr_values` + `max_age` parameters at the moment the AS is about
 * to mint an authorization code. Refusing to mint when the achieved auth doesn't
 * satisfy the request is the AS's enforcement point — without it, an MFA-required
 * client can be silently downgraded to AAL1 by a server that ignores `acr_values`.
 *
 * Distinct from the EDK [com.sphereon.authz.authzen.stepup.StepUpEvaluator] (which
 * is keyed by command-id for resource-server-side step-up demands): this SPI is
 * narrower and sits in IDK so the AS commands here can call it without an inverse
 * EDK→IDK dependency. EDK deployments can rebind this SPI to a bridge that
 * delegates to the broader RFC 9470 framework if they want a single decision point.
 *
 * Implementations are stateless and thread-safe. AppScope binding by convention.
 */
interface OAuth2AcrEnforcer {
    /**
     * Decide whether the achieved authentication satisfies the request.
     *
     * @param requestedAcrValues OIDC `acr_values` parameter (already split into a list).
     * @param currentAcr Achieved ACR string from the authenticator, or null.
     * @param currentAal Achieved AAL.
     * @param currentAmr Achieved AMR set (RFC 8176 short codes such as "pwd", "mfa").
     * @param authTimeEpochSeconds When the achieved authentication happened, or null when unknown.
     * @param maxAge OIDC `max_age` parameter (seconds), or null when not supplied.
     * @param nowEpochSeconds Wall-clock at evaluation time. The caller passes it so the
     *   enforcer stays pure for tests.
     */
    fun enforce(
        requestedAcrValues: List<String>?,
        currentAcr: String?,
        currentAal: AuthAssuranceLevel,
        currentAmr: Set<String>,
        authTimeEpochSeconds: Long?,
        maxAge: Long?,
        nowEpochSeconds: Long,
    ): OAuth2AcrEnforcementResult
}

/**
 * Outcome of [OAuth2AcrEnforcer.enforce]. The Sufficient case carries the ACR string the
 * AS should emit on the id_token's `acr` claim (per OIDC Core §3.1.2.1: the OP SHOULD
 * echo one of the requested ACRs when satisfied; otherwise echo the achieved ACR).
 *
 * The Insufficient case carries the parameters the AS surfaces back to the caller —
 * either as an authorize-error redirect, or as an RFC 9470 `WWW-Authenticate:
 * insufficient_user_authentication` challenge for resource-server-style step-up.
 */
sealed class OAuth2AcrEnforcementResult {
    data class Sufficient(
        val grantedAcr: String?
    ) : OAuth2AcrEnforcementResult()

    data class Insufficient(
        val requiredAal: AuthAssuranceLevel,
        val currentAal: AuthAssuranceLevel,
        val acrValues: String?,
        val maxAge: Long?,
        val reason: Reason,
    ) : OAuth2AcrEnforcementResult() {
        /** Why enforcement refused — drives the error code surfaced to the caller. */
        enum class Reason {
            /** Achieved AAL below the strongest requested ACR's AAL (or none satisfied). */
            INSUFFICIENT_ACR,

            /** auth_time + max_age has elapsed — the achieved auth is too stale. */
            STALE_AUTH,
        }
    }
}
