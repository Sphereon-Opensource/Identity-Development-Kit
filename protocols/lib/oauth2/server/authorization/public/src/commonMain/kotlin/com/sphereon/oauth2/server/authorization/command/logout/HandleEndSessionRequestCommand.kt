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

package com.sphereon.oauth2.server.authorization.command.logout

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand

/**
 * Inputs to [HandleEndSessionRequestCommand]. Carries the parameters defined by
 * OIDC RP-Initiated Logout 1.0 §2 plus the cookie-derived current login session id.
 *
 * @property idTokenHint Optional id_token previously issued by this AS. The handler
 *   decodes it to identify the user and ensure the requesting client matches the
 *   token audience.
 * @property clientId Optional client identifier the RP self-identifies as. Required
 *   when `id_token_hint` is absent and the AS still wants to validate
 *   `post_logout_redirect_uri` against a registered client.
 * @property postLogoutRedirectUri Optional redirect URI the RP wants the user agent
 *   sent back to after logout. The AS MUST validate this against the resolved
 *   client's registered `post_logout_redirect_uris`; an unregistered value is
 *   treated as absent and the AS renders its own logged-out page instead.
 * @property state Optional opaque value the RP wants echoed on the redirect.
 * @property logoutHint Informational hint about the user (RP-Initiated §2). The
 *   IDK handler does not branch on this value.
 * @property uiLocales Informational locale preference (RP-Initiated §2). The IDK
 *   handler does not branch on this value.
 * @property currentLoginSessionId Cookie-derived `oidc_login_sid` value. When
 *   `id_token_hint` is missing the cookie alone identifies the session.
 * @property baseUrl Issuer-resolved base URL for the AS, used to build
 *   `iss=` query params on Front-Channel iframes and the `iss` claim on
 *   `logout_token` JWTs.
 */
data class HandleEndSessionRequestArgs(
    val idTokenHint: String? = null,
    val clientId: String? = null,
    val postLogoutRedirectUri: String? = null,
    val state: String? = null,
    val logoutHint: String? = null,
    val uiLocales: String? = null,
    val currentLoginSessionId: String? = null,
    val baseUrl: String,
)

/**
 * Decision shape the orchestrator hands back to the HTTP layer. The HTTP layer is
 * responsible for cookie clearing and either rendering [LogoutOutcome.RenderPage]
 * or 302-redirecting per [LogoutOutcome.Redirect]. Front-Channel iframe HTML is
 * carried inline on [LogoutOutcome.RenderPage.html] so the HTTP layer never needs
 * to know about the FC mechanics.
 */
sealed interface LogoutOutcome {
    /**
     * The AS has terminated the session and the user agent should be redirected to
     * a registered `post_logout_redirect_uri` (with [state] appended when supplied).
     */
    data class Redirect(
        val location: String,
    ) : LogoutOutcome

    /**
     * The AS has terminated the session and is rendering an HTML page in lieu of a
     * post-logout redirect. The [html] body either confirms the logout (no FC RPs
     * to notify) or carries hidden Front-Channel iframes for every participating RP
     * with a `frontchannel_logout_uri` registered, plus a meta-refresh / JS
     * fallback to the [postLogoutLocation] when one is registered.
     */
    data class RenderPage(
        val html: String,
        val contentType: String = "text/html; charset=utf-8",
    ) : LogoutOutcome
}

/**
 * Orchestrator for OIDC RP-Initiated Logout 1.0 §2 end-session flow. Consolidates
 * id_token_hint validation, post_logout_redirect_uri allow-list checking, login
 * session revocation, RP fan-out (Front- and Back-Channel), and the post-logout
 * redirect-vs-page decision.
 *
 * The HTTP layer translates [LogoutOutcome] into a [com.sphereon.core.api.http.GenericHttpResponse]
 * and is responsible for the `Set-Cookie: oidc_login_sid=; Max-Age=0` header.
 */
interface HandleEndSessionRequestCommand : ServiceCommand<HandleEndSessionRequestArgs, LogoutOutcome, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID: String = "oauth2.logout.handle-end-session"
    }
}
