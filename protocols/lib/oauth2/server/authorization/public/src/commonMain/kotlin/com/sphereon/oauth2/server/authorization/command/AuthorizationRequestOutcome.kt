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

package com.sphereon.oauth2.server.authorization.command

import com.sphereon.oauth2.common.model.OAuth2ResponseMode

/**
 * Outcome of the full authorization-endpoint request pipeline (parse, verify, create session,
 * initiate auth or wallet completion).
 *
 * Error handling at `/authorize` is split into **pre-redirect** and **post-redirect** classes per
 * RFC 6749 §4.1.2.1 and OIDC Core §3.1.2.6. The split hinges on whether the redirect URI has been
 * validated against the client registration:
 *
 *  - **Pre-redirect** (redirect URI **not** trusted): parse failure, unknown client, unauthorized
 *    grant type, or `redirect_uri` mismatch / omission with multiple registered URIs. The server
 *    MUST NOT issue an HTTP redirect to the request's stated `redirect_uri` in these cases,
 *    blindly doing so would let an attacker bounce errors (and, via open-redirect chaining, user
 *    input) off the AS through an untrusted URI. The adapter serves a JSON error body instead.
 *  - **Post-redirect** (redirect URI **validated**): anything that fails later in verification or
 *    during session creation (response-type unsupported, PKCE policy violation, scope not
 *    permitted, authorization_details not allowed, etc.). These errors MUST be delivered to the
 *    trusted redirect URI using the resolved response mode, preserving the client-supplied
 *    `state`.
 *
 * Emitting a post-redirect error as JSON (the earlier behavior) fails OIDF RP tests that expect
 * to observe `error=...&state=...` on the callback URL, not a JSON body from the `/authorize` endpoint.
 */
public sealed class AuthorizationRequestOutcome {
    /**
     * Standard flow has fully created the authorization session, persisted it in the pending
     * store, and obtained the auth-provider redirect URL. The HTTP layer renders a 302 to
     * [authProviderRedirectUrl].
     */
    public data class AuthInitiated(
        val authProviderRedirectUrl: String,
    ) : AuthorizationRequestOutcome()

    /**
     * Wallet flow (`login_hint = "oid4vp:..."`) has completed inline: the wallet authentication
     * was already finished, claims were fetched, the authorization code was issued, and the
     * authorization response was assembled. The HTTP layer just renders [authorizationResponseData]
     * per its [OAuth2ResponseMode].
     */
    public data class WalletCompleted(
        val authorizationResponseData: AuthorizationResponseData,
    ) : AuthorizationRequestOutcome()

    /**
     * Error occurred before the redirect URI could be trusted. The adapter serves a JSON error
     * response per RFC 6749 §5.2. [httpStatus] defaults to 400 for OAuth2 authorization-endpoint
     * errors; pass 401 / 403 only for shapes that spec explicitly designates as such.
     */
    public data class PreRedirectError(
        val error: String,
        val errorDescription: String?,
        val httpStatus: Int = 400,
    ) : AuthorizationRequestOutcome()

    /**
     * Error occurred after the redirect URI was validated. The adapter delivers the error to
     * [redirectUri] using the resolved [responseMode], echoing [state] when present.
     */
    public data class PostRedirectError(
        val error: String,
        val errorDescription: String?,
        val redirectUri: String,
        val state: String?,
        val responseMode: OAuth2ResponseMode,
    ) : AuthorizationRequestOutcome()

    /**
     * Group I outcome: the user MUST authenticate (or re-authenticate) on the OP's own login
     * surface before authorization can proceed. Distinct from [AuthInitiated] because that
     * variant denotes a redirect to an upstream federation IdP for which `oidc_login_sid`
     * semantics do not apply, whereas [NeedsLogin] always lands on the AS's first-party
     * login renderer (Group J).
     *
     * [forceReauth] is `true` for `prompt=login` / `prompt=select_account` and for `max_age`
     * staleness; the HTTP layer SHOULD clear the existing `oidc_login_sid` cookie when
     * rendering this outcome so the renderer cannot silently re-use the session it was just
     * told to reject. `false` is the cold-start path (no session cookie, no `prompt=none`).
     */
    public data class NeedsLogin(
        val loginUrl: String,
        val forceReauth: Boolean,
    ) : AuthorizationRequestOutcome()
}
