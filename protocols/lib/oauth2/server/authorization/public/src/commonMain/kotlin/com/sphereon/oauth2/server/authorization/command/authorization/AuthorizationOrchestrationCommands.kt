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

package com.sphereon.oauth2.server.authorization.command.authorization

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestOutcome
import com.sphereon.oauth2.server.authorization.command.AuthorizationResponseData

/*
 * Orchestration ServiceCommand contracts behind the browser-facing `/authorize` and
 * `/authorize/callback` flow. Args carry no caller-identity fields: tenant is read from
 * `SessionExecution.tenantId` inside each impl. Errors stay as
 * `IdkError` to match the surrounding parse/verify/create-code/create-response chain; the lifted
 * code already speaks `IdkError`, so converting to a domain error here would be a refactor, not a
 * verbatim lift.
 */

// ============================================================================
// HandleAuthorizeRequestCommand
// ============================================================================

data class HandleAuthorizeRequestArgs(
    val queryParameters: Map<String, String>,
    /**
     * Return URL the auth provider should redirect back to after authentication. Built by the
     * HTTP layer from the request's resolved base URL (`<baseUrl>/authorize/callback?session_id=...`)
     * since the command itself has no transport-level base-URL context.
     *
     * Optional only because the wallet flow (`login_hint = "oid4vp:..."`) does not hop out to an
     * IdP; the standard impl validates non-null when it actually needs the value.
     */
    val returnUrl: String? = null,
    /**
     * Per-request base URL the HTTP shell resolved from `Host` + `X-Forwarded-Proto`. Threaded to
     * the response-shaping path so JARM JWT minting can populate the response JWT's `iss` claim
     * when the AS does not have a static `issuer` configured (the OIDF harness deliberately
     * leaves it unset to support multi-host deployments).
     */
    val baseUrlOverride: String? = null,
) {
    /** RFC 6749 / OIDC `login_hint` parameter, lifted out for `supports()` dispatch. */
    val loginHint: String? get() = queryParameters["login_hint"]
}

/**
 * Run the full authorization-endpoint pipeline (parse, trusted-redirect resolution, verify,
 * create session) and either initiate IdP authentication (standard flow) or complete inline
 * (wallet flow with `login_hint = "oid4vp:..."`). Two impls dispatch via `supports()`:
 *
 *  - Standard impl persists the [com.sphereon.oauth2.server.authorization.model.AuthorizationSession]
 *    in [com.sphereon.oauth2.server.authorization.storage.PendingAuthorizationSessionStore],
 *    initiates auth via [com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider],
 *    and returns [AuthorizationRequestOutcome.AuthInitiated].
 *  - Wallet impl reads the already-authenticated user, fetches claims, issues the authorization
 *    code inline, and returns [AuthorizationRequestOutcome.WalletCompleted].
 *
 * Pre / post-redirect error variants are common to both. The HTTP layer iterates a
 * `Set<HandleAuthorizeRequestCommand>` and picks the first impl that `supports(args)`.
 */
interface HandleAuthorizeRequestCommand : ServiceCommand<HandleAuthorizeRequestArgs, AuthorizationRequestOutcome, IdkError>

// ============================================================================
// HandleAuthorizeCallbackCommand
// ============================================================================

/**
 * Args carry the pending authorization session id and, when authentication used a separately
 * keyed provider session, that authentication session id. The impl removes the pending session
 * (single-use), pulls the authenticated user + claims, and runs code issuance + response assembly.
 */
data class HandleAuthorizeCallbackArgs(
    val sessionId: String,
    val authenticationSessionId: String? = null,
    /**
     * Per-request base URL the HTTP shell resolved from `Host` + `X-Forwarded-Proto`. Threaded
     * to the response-shaping path so JARM JWT minting can populate the `iss` claim when the
     * AS does not have a static `issuer` configured (the OIDF harness deliberately leaves it
     * unset to support multi-host deployments).
     */
    val baseUrlOverride: String? = null,
)

/**
 * Look up the pending [com.sphereon.oauth2.server.authorization.model.AuthorizationSession] by id,
 * remove it, fetch the authenticated user and claims via
 * [com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider],
 * issue the authorization code, and assemble the authorization response per the session's
 * resolved response mode (query / fragment / form_post). The adapter is responsible only for
 * parsing the query parameter and rendering [AuthorizationResponseData] over HTTP.
 */
interface HandleAuthorizeCallbackCommand : ServiceCommand<HandleAuthorizeCallbackArgs, AuthorizationResponseData, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.authorization.handle-authorize-callback"
    }
}
