/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.readClasspathResource
import com.sphereon.core.api.conf.readClasspathResourceBytes
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.config.LoginMethod
import com.sphereon.oauth2.server.authorization.provider.LoginPageAsset
import com.sphereon.oauth2.server.authorization.provider.LoginPageContext
import com.sphereon.oauth2.server.authorization.provider.LoginPageRenderer
import com.sphereon.oauth2.server.authorization.provider.LoginPageResponse
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Quiet, portal-like AS login surface for white-label deployments. */
@Inject
@SingleIn(AppScope::class)
class NeutralLoginPageRenderer : LoginPageRenderer {
    private val template by lazy { readClasspathResource(TEMPLATE_PATH) ?: error("Missing $TEMPLATE_PATH") }
    private val css by lazy { readClasspathResourceBytes(CSS_PATH) ?: error("Missing $CSS_PATH") }
    private val fontFiles by lazy {
        FONT_WEIGHTS.associate { weight ->
            val assetPath = "fonts/poppins-$weight.woff2"
            assetPath to
                (readClasspathResourceBytes("$FONTS_PREFIX/poppins-$weight.woff2")
                    ?: error("Missing Poppins font weight $weight"))
        }
    }

    override suspend fun render(ctx: LoginPageContext): IdkResult<LoginPageResponse, IdkError> {
        val labels = if (ctx.locale == "nl") NL else EN
        val base = ctx.formActionBase.ifBlank { ctx.returnUrl.substringBefore("/authorize/callback") }
        val branding = ctx.lightBranding()
        val darkBranding = ctx.darkBranding()
        val password = if (ctx.showPasswordForm) passwordForm(ctx, base, labels) else ""
        val federation = federationForms(ctx, base, labels)
        val wallet = walletTabAction(ctx, labels)
        val available =
            mapOf(
                LoginMethod.FEDERATION to MethodPanel(LoginMethod.FEDERATION, labels.federationTab, federation),
                LoginMethod.WALLET to MethodPanel(LoginMethod.WALLET, labels.walletTab, tabAction = wallet),
                LoginMethod.PASSWORD to MethodPanel(LoginMethod.PASSWORD, labels.passwordTab, password),
            )
        val order =
            when (ctx.defaultMethod) {
                LoginMethod.FEDERATION -> listOf(LoginMethod.FEDERATION, LoginMethod.WALLET, LoginMethod.PASSWORD)
                LoginMethod.WALLET -> listOf(LoginMethod.WALLET, LoginMethod.FEDERATION, LoginMethod.PASSWORD)
                LoginMethod.PASSWORD -> listOf(LoginMethod.PASSWORD, LoginMethod.FEDERATION, LoginMethod.WALLET)
            }
        val sections = renderMethodSelector(order.mapNotNull(available::get).filter(MethodPanel::isAvailable), labels)
        val themeStyle = themeStyleBlock(ctx, "body.neutral-login")
        val logo = branding.logoUrl
        val logoBlock =
            logo?.let {
                val darkLogo = darkBranding.logoUrl ?: branding.logoDarkUrl ?: it
                """
                <picture class="brand-logo">
                  <source media="(prefers-color-scheme: dark)" srcset="${escapeHtml(darkLogo)}">
                  <img src="${escapeHtml(it)}" alt="${escapeHtml(branding.appName ?: labels.institution)}">
                </picture>
                """.trimIndent()
            }.orEmpty()
        val notice = ctx.notice?.takeIf(String::isNotBlank)?.let { "<div class=\"notice\">${escapeHtml(it)}</div>" }.orEmpty()
        val error = ctx.errorMessage?.let { "<div class=\"error\">${escapeHtml(labels.invalidCredentials)}</div>" }.orEmpty()
        val html =
            template
                .replace("{{locale}}", escapeHtml(ctx.locale))
                .replace("{{assetsBase}}", escapeHtml("$base$ASSETS_BASE"))
                .replace("{{pageClass}}", escapeHtml("display-${ctx.display}"))
                .replace("{{themeStyleBlockOrEmpty}}", themeStyle)
                .replace("{{logoBlockOrEmpty}}", logoBlock)
                .replace("{{title}}", escapeHtml(labels.title))
                .replace("{{noticeBlockOrEmpty}}", notice)
                .replace("{{errorBlockOrEmpty}}", error)
                .replace("{{methodSections}}", sections)
                .replace("{{cancelForm}}", cancelForm(ctx, base, labels))
        return Ok(LoginPageResponse(html = html, cspNonce = ctx.cspNonce?.takeIf { themeStyle.isNotEmpty() }))
    }

    override fun staticAssets(): List<LoginPageAsset> =
        listOf(LoginPageAsset("css/neutral-login.css", "text/css; charset=utf-8", css)) +
            fontFiles.map { (path, bytes) ->
                LoginPageAsset(path, "font/woff2", bytes, maxAgeSeconds = 60L * 60 * 24 * 30)
            }

    private fun renderMethodSelector(
        panels: List<MethodPanel>,
        labels: Labels,
    ): String {
        if (panels.isEmpty()) return ""
        val description = "<p class=\"login-description\">${escapeHtml(labels.description)}</p>"
        if (panels.size == 1) {
            val panel = panels.single()
            return description + (panel.tabAction?.let { "<div class=\"method-tab-list\">$it</div>" } ?: panel.html)
        }
        val controls =
            panels.joinToString("") { panel ->
                val id = panel.method.name.lowercase()
                val checked = if (panel === panels.first()) " checked" else ""
                "<input class=\"method-tab-control\" type=\"radio\" name=\"login-method\" id=\"method-tab-$id\"$checked>"
            }
        val tabs =
            panels.joinToString("") { panel ->
                val id = panel.method.name.lowercase()
                panel.tabAction
                    ?: "<label class=\"method-tab\" data-tab-method=\"$id\" for=\"method-tab-$id\" role=\"tab\">${escapeHtml(panel.label)}</label>"
            }
        val content =
            panels.joinToString("") { panel ->
                val id = panel.method.name.lowercase()
                "<div class=\"method-tab-panel\" data-method=\"$id\" role=\"tabpanel\">${panel.html}</div>"
            }
        return "$description<div class=\"method-tabs\">$controls<div class=\"method-tab-list\" role=\"tablist\">$tabs</div><div class=\"method-tab-panels\">$content</div></div>"
    }

    private fun passwordForm(ctx: LoginPageContext, base: String, labels: Labels): String =
        """
        <section class="method-section method-password" data-method="password">
          <h2>${escapeHtml(labels.passwordHeading)}</h2>
          <form method="post" action="${escapeHtml("$base/login")}" autocomplete="on">
            ${sessionInputs(ctx, includeCsrf = true)}
            <label>${escapeHtml(labels.username)}<input name="username" type="text" value="${escapeHtml(ctx.loginHint.orEmpty())}" autocomplete="username"></label>
            <label>${escapeHtml(labels.password)}<input name="password" type="password" autocomplete="current-password"></label>
            <button class="primary-button" type="submit">${escapeHtml(labels.signIn)}</button>
          </form>
        </section>
        """.trimIndent()

    private fun federationForms(ctx: LoginPageContext, base: String, labels: Labels): String {
        if (ctx.federationOptions.isEmpty()) return ""
        val buttons =
            ctx.federationOptions.joinToString("\n") { option ->
                """
                <form method="get" action="${escapeHtml("$base/federation/authorize")}">
                  <input type="hidden" name="provider" value="${escapeHtml(option.id)}">
                  ${sessionInputs(ctx, includeCsrf = false)}
                  ${hiddenInput("login_hint", ctx.loginHint)}
                  ${hiddenInput("acr_values", ctx.acrValues.takeIf(List<String>::isNotEmpty)?.joinToString(" "))}
                  ${hiddenInput("force_reauth", if (ctx.forceReauth) "true" else null)}
                  <button class="provider-button" type="submit" data-provider="${escapeHtml(option.id)}">${escapeHtml(option.displayName)}</button>
                </form>
                """.trimIndent()
            }
        return """
            <section class="method-section method-federation" data-method="federation">
              <h2>${escapeHtml(labels.federationHeading)}</h2>
              <div class="provider-list">$buttons</div>
            </section>
        """.trimIndent()
    }

    private fun walletTabAction(ctx: LoginPageContext, labels: Labels): String {
        val action = ctx.walletAuthorizationUrl?.takeIf(String::isNotBlank) ?: return ""
        if (!ctx.showWallet) return ""
        return """
            <form class="method-tab-action" method="get" action="${escapeHtml(action)}">
              <input type="hidden" name="oauth_session_id" value="${escapeHtml(ctx.sessionId)}">
              <input type="hidden" name="return_url" value="${escapeHtml(ctx.returnUrl)}">
              <button class="method-tab" data-tab-method="wallet" type="submit" role="tab">${escapeHtml(labels.walletTab)}</button>
            </form>
        """.trimIndent()
    }

    private fun cancelForm(ctx: LoginPageContext, base: String, labels: Labels): String =
        """
        <form class="cancel-form" method="post" action="${escapeHtml("$base/login/cancel")}">
          <input type="hidden" name="session_id" value="${escapeHtml(ctx.sessionId)}">
          <input type="hidden" name="tab_id" value="${escapeHtml(ctx.tabId.orEmpty())}">
          <input type="hidden" name="session_code" value="${escapeHtml(ctx.sessionCode.orEmpty())}">
          <button type="submit">${escapeHtml(labels.cancel)}</button>
        </form>
        """.trimIndent()

    private fun sessionInputs(ctx: LoginPageContext, includeCsrf: Boolean): String =
        buildString {
            append("<input type=\"hidden\" name=\"session_id\" value=\"").append(escapeHtml(ctx.sessionId)).append("\">")
            append("<input type=\"hidden\" name=\"return_url\" value=\"").append(escapeHtml(ctx.returnUrl)).append("\">")
            if (includeCsrf) {
                append("<input type=\"hidden\" name=\"tab_id\" value=\"").append(escapeHtml(ctx.tabId.orEmpty())).append("\">")
                append("<input type=\"hidden\" name=\"session_code\" value=\"").append(escapeHtml(ctx.sessionCode.orEmpty())).append("\">")
            }
        }

    private fun hiddenInput(name: String, value: String?): String =
        value?.takeIf(String::isNotBlank)?.let { "<input type=\"hidden\" name=\"$name\" value=\"${escapeHtml(it)}\">" }.orEmpty()

    private data class Labels(
        val title: String,
        val institution: String,
        val description: String,
        val federationTab: String,
        val walletTab: String,
        val passwordTab: String,
        val federationHeading: String,
        val passwordHeading: String,
        val username: String,
        val password: String,
        val signIn: String,
        val cancel: String,
        val invalidCredentials: String,
    )

    private data class MethodPanel(
        val method: LoginMethod,
        val label: String,
        val html: String = "",
        val tabAction: String? = null,
    ) {
        fun isAvailable(): Boolean = html.isNotBlank() || tabAction?.isNotBlank() == true
    }

    companion object {
        private const val RESOURCE_BASE = "login/neutral"
        private const val TEMPLATE_PATH = "$RESOURCE_BASE/template.html"
        private const val CSS_PATH = "$RESOURCE_BASE/css/neutral-login.css"
        private const val ASSETS_BASE = "/login/assets"
        private const val FONTS_PREFIX = "login/sphereon/fonts"
        private val FONT_WEIGHTS = listOf(400, 500, 600, 700)
        private val EN = Labels("Sign in", "Institution", "Choose your preferred authentication method.", "Institutional Account", "Wallet", "Account", "Sign in with your institutional account", "Sign in with an account", "Username", "Password", "Sign in", "Cancel", "The username or password is incorrect.")
        private val NL = Labels("Inloggen", "Instelling", "Kies de gewenste authenticatiemethode.", "Instellingsaccount", "Wallet", "Account", "Log in met je instellingsaccount", "Inloggen met een account", "Gebruikersnaam", "Wachtwoord", "Inloggen", "Annuleren", "De gebruikersnaam of het wachtwoord is onjuist.")
    }
}
