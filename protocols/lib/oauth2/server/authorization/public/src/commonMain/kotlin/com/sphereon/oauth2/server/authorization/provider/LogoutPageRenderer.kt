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

package com.sphereon.oauth2.server.authorization.provider

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat

/**
 * Strategy for rendering the AS's post-logout HTML response. Mirrors [LoginPageRenderer]
 * for the OIDC RP-Initiated Logout 1.0 + Front-Channel Logout 1.0 flow served at
 * `GET/POST /logout`. IDK ships a single Sphereon-branded implementation; EDK / VDX
 * overlays a tenant-aware implementation through `@ContributesBinding(replaces = [...])`.
 *
 * The renderer takes responsibility for two outputs:
 *  - The "you have been logged out" confirmation when no `post_logout_redirect_uri` is
 *    registered (or the supplied one didn't validate).
 *  - The Front-Channel logout HTML carrying one hidden `<iframe>` per participating RP
 *    that registered a `frontchannel_logout_uri`, plus a JS / meta-refresh fallback
 *    redirect to `postLogoutLocation` when one was supplied.
 */
@JsExportCompat
interface LogoutPageRenderer {
    /**
     * Render the logout page for [ctx]. Implementations MUST escape any caller-controlled
     * string (RP names, URIs, state) before interpolating into HTML.
     */
    suspend fun render(ctx: LogoutPageContext): IdkResult<LogoutPageResponse, IdkError>
}

/**
 * One participating RP the AS will notify via Front-Channel logout iframe.
 *
 * @property clientId Registered client_id of the RP.
 * @property iframeUrl Fully-formed URL the AS embeds in the `<iframe src=...>` attribute.
 *   When the client has `frontchannel_logout_session_required = true` this carries the
 *   `iss` and `sid` query parameters per OIDC Front-Channel Logout 1.0 §3.
 */
@JsExportCompat
data class FrontChannelLogoutIframe(
    val clientId: String,
    val iframeUrl: String,
)

/**
 * Inputs to [LogoutPageRenderer.render]. Carries everything the renderer needs to
 * produce the final HTML without taking the active HTTP request as an argument.
 *
 * @property iframes Front-Channel logout iframes the renderer must embed. Empty when
 *   no participating RP registered a `frontchannel_logout_uri`.
 * @property postLogoutLocation Optional URL to fall through to once the iframes have
 *   loaded. When `null` the page renders only the confirmation message.
 * @property locale Negotiated UI locale for the confirmation copy.
 */
@JsExportCompat
data class LogoutPageContext(
    val iframes: List<FrontChannelLogoutIframe> = emptyList(),
    val postLogoutLocation: String? = null,
    val locale: String = "en",
)

/**
 * Output of [LogoutPageRenderer.render]. The HTTP layer wraps this in a 200 response
 * with the declared [contentType].
 */
@JsExportCompat
data class LogoutPageResponse(
    val html: String,
    val contentType: String = "text/html; charset=utf-8",
)
