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

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.readClasspathResource
import com.sphereon.core.api.conf.readClasspathResourceBytes
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.server.authorization.provider.LoginPageAsset
import com.sphereon.oauth2.server.authorization.provider.LoginPageContext
import com.sphereon.oauth2.server.authorization.provider.LoginPageRenderer
import com.sphereon.oauth2.server.authorization.provider.LoginPageResponse
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * IDK default [LoginPageRenderer]: a single Sphereon-branded username + password page bundled with
 * its CSS, SVGs, and i18n property files in this module's `commonMain/resources/login/sphereon/`
 * tree. Ported from the Sphereon Keycloak theme but stripped of remember-me, forgot-password,
 * register, social-providers, OID4VP wallet tab, and tabs scaffolding so the IDK surface stays
 * narrow per the IDK / EDK strategic boundary.
 *
 * EDK overlays a tenant-aware implementation through
 * `@ContributesBinding(replaces = SphereonBrandedLoginPageRenderer::class, ...)`. Single binding
 * inside IDK; no registry interface here.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<LoginPageRenderer>())
class SphereonBrandedLoginPageRenderer : LoginPageRenderer {
    private val templateHtml: String by lazy {
        readClasspathResource(TEMPLATE_PATH)
            ?: error("Login renderer template missing: $TEMPLATE_PATH")
    }
    private val loginCss: ByteArray by lazy {
        readClasspathResourceBytes(CSS_PATH)
            ?: error("Login renderer asset missing: $CSS_PATH")
    }
    private val logoSvg: ByteArray by lazy {
        readClasspathResourceBytes(LOGO_PATH)
            ?: error("Login renderer asset missing: $LOGO_PATH")
    }
    private val backgroundSvg: ByteArray by lazy {
        readClasspathResourceBytes(BACKGROUND_PATH)
            ?: error("Login renderer asset missing: $BACKGROUND_PATH")
    }
    private val fontFiles: Map<String, ByteArray> by lazy {
        FONT_WEIGHTS.associate { weight ->
            val path = "$FONTS_PREFIX/poppins-$weight.woff2"
            val bytes =
                readClasspathResourceBytes(path)
                    ?: error("Login renderer font asset missing: $path")
            "fonts/poppins-$weight.woff2" to bytes
        }
    }
    private val messages: Map<String, Map<String, String>> by lazy {
        SUPPORTED_LOCALES.associateWith { locale ->
            val path = "$I18N_PREFIX/messages-$locale.properties"
            val text =
                readClasspathResource(path)
                    ?: error("Login renderer i18n bundle missing: $path")
            parseProperties(text)
        }
    }

    override suspend fun render(ctx: LoginPageContext): IdkResult<LoginPageResponse, IdkError> {
        val locale = if (messages.containsKey(ctx.locale)) ctx.locale else DEFAULT_LOCALE
        val msg = messages.getValue(locale)
        val errorBlock =
            ctx.errorMessage
                ?.let {
                    val errorText = msg["loginInvalidCredentials"] ?: it
                    "<div class=\"input-error\">${escapeHtml(errorText)}</div>"
                }.orEmpty()
        // Federation block: rendered only when the caller passed at least one option.
        // Empty options → the marker is replaced with the empty string and the divider /
        // button list disappear from the DOM (no orphan separator label, no empty
        // `<div class="federation-list"></div>` to style around).
        val federationBlock = renderFederationBlock(ctx, msg)
        // Optional informational notice (demo test-account hint, maintenance banner, …). Blank or
        // null → empty string so the marker vanishes and no empty `<div>` is left to style around.
        val noticeBlock =
            ctx.notice
                ?.takeIf { it.isNotBlank() }
                ?.let { "<div class=\"login-notice\">${escapeHtml(it)}</div>" }
                .orEmpty()
        // Path-prefixed deployment support: when the AS sits behind a reverse proxy that mounts
        // it under a public sub-path (Caddy `handle_path /auth/* {…}` for the demo), a bare
        // `/login` form action posts to the proxy's root and 404s. `returnUrl` is already the
        // public-facing callback URL (`{base}/authorize/callback?…`), so the prefix the form
        // needs to share is everything before `/authorize/callback`. Same trick the redirect
        // builder in `StandardAuthorizeRequestCommandImpl.buildLoginUrl` uses, applied here so
        // form action + asset URLs all carry the same public prefix.
        val basePath = ctx.returnUrl.substringBefore("/authorize/callback")
        val tokens =
            mapOf(
                "locale" to escapeHtml(locale),
                "assetsBase" to escapeHtml("$basePath$ASSETS_BASE"),
                "msg.loginAccountTitle" to escapeHtml(msg.getValue("loginAccountTitle")),
                "msg.username" to escapeHtml(msg.getValue("username")),
                "msg.usernamePlaceholder" to escapeHtml(msg.getValue("usernamePlaceholder")),
                "msg.password" to escapeHtml(msg.getValue("password")),
                "msg.passwordPlaceholder" to escapeHtml(msg.getValue("passwordPlaceholder")),
                "msg.doLogIn" to escapeHtml(msg.getValue("doLogIn")),
                "msg.doCancel" to escapeHtml(msg.getValue("doCancel")),
                "noticeBlockOrEmpty" to noticeBlock,
                "errorBlockOrEmpty" to errorBlock,
                "formAction" to escapeHtml("$basePath$FORM_ACTION"),
                "cancelAction" to escapeHtml("$basePath$CANCEL_ACTION"),
                "sessionId" to escapeHtml(ctx.sessionId),
                "returnUrl" to escapeHtml(ctx.returnUrl),
                "loginHintEscaped" to escapeHtml(ctx.loginHint.orEmpty()),
                // CSRF anti-replay tokens — empty when the caller (typically only legacy
                // tests) didn't mint a tokenizer. Production renders always populate both.
                "tabId" to escapeHtml(ctx.tabId.orEmpty()),
                "sessionCode" to escapeHtml(ctx.sessionCode.orEmpty()),
                "federationBlockOrEmpty" to federationBlock,
            )
        return Ok(LoginPageResponse(html = applyTemplate(templateHtml, tokens)))
    }

    override fun staticAssets(): List<LoginPageAsset> =
        listOf(
            LoginPageAsset(path = "css/login.css", contentType = "text/css; charset=utf-8", bytes = loginCss),
            LoginPageAsset(path = "img/sphereon-logo.svg", contentType = "image/svg+xml", bytes = logoSvg),
            LoginPageAsset(path = "img/login-background.svg", contentType = "image/svg+xml", bytes = backgroundSvg),
        ) +
            fontFiles.map { (path, bytes) ->
                // Vendored Poppins woff2 (Latin subset). Long-cached per RFC 9111: the file
                // contents are immutable for the lifetime of the deployment artifact, so a
                // 30-day max-age plus `immutable` is safe and removes the per-login asset
                // round-trip after the first visit.
                LoginPageAsset(
                    path = path,
                    contentType = "font/woff2",
                    bytes = bytes,
                    maxAgeSeconds = 60L * 60 * 24 * 30,
                )
            }

    private fun applyTemplate(
        template: String,
        tokens: Map<String, String>
    ): String = tokens.entries.fold(template) { acc, (key, value) -> acc.replace("{{$key}}", value) }

    /**
     * Build the federation provider block. Returns the empty string when no options are
     * supplied so the password form stands alone. When options exist, emits a divider with
     * the localised "Or continue with" label followed by one GET form per provider posting
     * to `{basePath}/federation/authorize?provider=<id>`.
     *
     * GET (not POST) for the federation entry deliberately: the federation authorize
     * endpoint already requires no CSRF token (the upstream IdP itself supplies the
     * security ceremony), and a GET form makes the buttons usable as bookmarkable
     * deep-links (`/federation/authorize?provider=corp-saml`) — common for kiosk
     * deployments.
     */
    private fun renderFederationBlock(
        ctx: LoginPageContext,
        msg: Map<String, String>,
    ): String {
        if (ctx.federationOptions.isEmpty()) return ""
        val basePath = ctx.returnUrl.substringBefore("/authorize/callback")
        val dividerLabel = escapeHtml(msg["loginFederationDivider"] ?: "Or continue with")
        val buttonPrefix = escapeHtml(msg["loginFederationButtonPrefix"] ?: "Continue with")
        val buttons =
            ctx.federationOptions.joinToString("\n") { option ->
                val safeId = escapeHtml(option.id)
                val safeName = escapeHtml(option.displayName)
                """
                <form class="form-sections form-federation" method="get" action="${escapeHtml("$basePath$FEDERATION_ACTION")}">
                  <input type="hidden" name="provider" value="$safeId">
                  <button type="submit" class="secondary-button federation-button" data-provider="$safeId">$buttonPrefix $safeName</button>
                </form>
                """.trimIndent()
            }
        return """
            <div class="federation-divider"><span>$dividerLabel</span></div>
            <div class="federation-list">
            $buttons
            </div>
            """.trimIndent()
    }

    private fun parseProperties(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            result[key] = value
        }
        return result
    }

    private fun escapeHtml(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    companion object {
        internal const val ASSETS_BASE: String = "/login/assets"
        internal const val FORM_ACTION: String = "/login"
        internal const val CANCEL_ACTION: String = "/login/cancel"

        // Mirrors the path baked into FederationAuthorizeHttpEndpointCommand.ENDPOINT
        // (`/federation/authorize`) — kept as a private constant here so the template
        // string composition stays self-contained without pulling the rest module's
        // command type in as a compile dependency.
        internal const val FEDERATION_ACTION: String = "/federation/authorize"

        private const val RESOURCE_BASE = "login/sphereon"
        private const val TEMPLATE_PATH = "$RESOURCE_BASE/template.html"
        private const val CSS_PATH = "$RESOURCE_BASE/css/login.css"
        private const val LOGO_PATH = "$RESOURCE_BASE/img/sphereon-logo.svg"
        private const val BACKGROUND_PATH = "$RESOURCE_BASE/img/login-background.svg"
        private const val FONTS_PREFIX = "$RESOURCE_BASE/fonts"
        private const val I18N_PREFIX = "$RESOURCE_BASE/i18n"
        private const val DEFAULT_LOCALE = "en"
        private val SUPPORTED_LOCALES = setOf("en", "nl")

        // Match the @font-face declarations in css/login.css; weights we actually use across
        // .login-title (600), labels (400), buttons (400), .login-title-strong (700), inputs (500).
        private val FONT_WEIGHTS = listOf(400, 500, 600, 700)
    }
}
