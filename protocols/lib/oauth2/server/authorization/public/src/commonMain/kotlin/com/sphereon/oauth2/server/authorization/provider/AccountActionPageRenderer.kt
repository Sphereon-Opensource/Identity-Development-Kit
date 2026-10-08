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

import com.sphereon.conf.theme.core.model.ResolvedFeature
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat

/**
 * Strategy for rendering the AS account-action surface served at `GET /account-action`: the page a
 * user lands on from an activation, tenant-onboarding or password-change link. IDK ships a single
 * Sphereon-branded implementation; EDK overlays a tenant-themed one through
 * `@ContributesBinding(replaces = [...])`, mirroring [LoginPageRenderer].
 *
 * The action token travels in the URL fragment so it never reaches access logs. That means the
 * server cannot know at render time which of the three actions this is, whether the account needs
 * a password, or who the account belongs to. Implementations therefore render every branch and let
 * the page script reveal one once `POST /api/account-actions/v1/resolve` has answered.
 */
@JsExportCompat
interface AccountActionPageRenderer {
    /**
     * Render the account-action page for [ctx]. Implementations MUST escape every
     * caller-controlled string before interpolating it into HTML, and MUST keep the action token
     * out of any URL, form action or log line.
     */
    suspend fun render(ctx: AccountActionPageContext): IdkResult<AccountActionPageResponse, IdkError>
}

/**
 * Inputs to [AccountActionPageRenderer.render]. Everything here is known server-side before the
 * token is resolved. Anything that depends on the token reaches the page over the resolve call
 * instead, which is why there is no action kind and no `requiresPassword` here.
 */
@JsExportCompat
data class AccountActionPageContext(
    val asInstanceId: String,
    val tenantId: String?,
    /** BCP-47 primary subtag negotiated from `Accept-Language`, defaulting to English. */
    val locale: String = "en",
    /** Per-render CSP nonce the renderer MUST stamp on every inline `<style>` and `<script>`. */
    val cspNonce: String? = null,
    /** Sign-in path the completed state links to, derived from the account-action request path. */
    val loginPath: String = "/login",
    /**
     * Where a completed action sends the user when the link itself named no destination: the
     * tenant's own public base URL. Blank leaves the page on its completed state with the
     * sign-in link, rather than navigating somewhere unrelated.
     */
    val tenantRootUrl: String? = null,
    /** True when the AS has WebAuthn enabled, so the page may offer passkey enrollment. */
    val showWebAuthn: Boolean = false,
    /**
     * Organization or AS display name, used to label an enrolled passkey when the resolve response
     * carries no display label of its own. Null or blank falls back to a generic label.
     */
    val organizationName: String? = null,
    /**
     * Tenant-resolved LIGHT theme, or null when no resolver is assembled, no tenant is known, or
     * resolution failed. Renderers treat null as "use built-in defaults"; theming is never allowed
     * to break the page.
     */
    val resolvedThemeLight: ResolvedTheme? = null,
    /** Tenant-resolved DARK theme, with the same null semantics as [resolvedThemeLight]. */
    val resolvedThemeDark: ResolvedTheme? = null,
    /** The resolved `login` feature, reused here so activation and sign-in share their branding. */
    val loginFeature: ResolvedFeature? = null,
    /** The `login` feature resolved with the DARK variant, for dark-scheme element bindings. */
    val loginFeatureDark: ResolvedFeature? = null,
)

/**
 * Output of [AccountActionPageRenderer.render]. The HTTP layer wraps [html] in a 200 response and
 * threads [cspNonce] into the Content-Security-Policy header so the inline blocks are allowed.
 * [imageOrigins] lists cross-origin image hosts the theme introduced, for `img-src`.
 */
@JsExportCompat
data class AccountActionPageResponse(
    val html: String,
    val contentType: String = "text/html; charset=utf-8",
    val cspNonce: String? = null,
    val imageOrigins: List<String> = emptyList(),
)
