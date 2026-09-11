/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.conf.theme.core.model.AssetElementValue
import com.sphereon.conf.theme.core.model.ResolvedFeature
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.oauth2.server.authorization.provider.LoginPageContext

internal data class LoginPageBranding(
    val primary: String?,
    val surface: String?,
    val onSurface: String?,
    val logoUrl: String?,
    val logoDarkUrl: String?,
    val backgroundUrl: String?,
    val appName: String?,
)

internal fun LoginPageContext.lightBranding(): LoginPageBranding = branding(resolvedThemeLight, loginFeature, dark = false)

internal fun LoginPageContext.darkBranding(): LoginPageBranding = branding(resolvedThemeDark, loginFeatureDark ?: loginFeature, dark = true)

private fun branding(
    theme: ResolvedTheme?,
    feature: ResolvedFeature?,
    dark: Boolean,
): LoginPageBranding {
    val logoElement = if (dark) "logoDark" else "logo"
    return LoginPageBranding(
        primary = safeCssColor(theme?.tokens?.get("color.primary") ?: theme?.branding?.primaryColor),
        surface = safeCssColor(theme?.tokens?.get("color.surface")),
        onSurface = safeCssColor(theme?.tokens?.get("color.onSurface")),
        logoUrl = assetUri(feature, logoElement) ?: if (dark) theme?.branding?.logoDarkUrl else theme?.branding?.logoUrl,
        logoDarkUrl = assetUri(feature, "logoDark") ?: theme?.branding?.logoDarkUrl,
        backgroundUrl = assetUri(feature, "background"),
        appName = theme?.branding?.appName,
    )
}

private fun assetUri(feature: ResolvedFeature?, elementId: String): String? =
    (feature?.elements?.get(elementId)?.value as? AssetElementValue)?.asset?.uri

private fun safeCssColor(value: String?): String? =
    value?.trim()?.takeIf { CSS_COLOR.matches(it) }

internal fun themeStyleBlock(
    ctx: LoginPageContext,
    bodySelector: String,
): String {
    val nonce = ctx.cspNonce?.takeIf { CSP_NONCE.matches(it) } ?: return ""
    val light = ctx.lightBranding()
    val dark = ctx.darkBranding()
    val lightVars = cssVariables(light)
    val darkVars = cssVariables(dark)
    if (lightVars.isEmpty() && darkVars.isEmpty()) return ""
    return buildString {
        append("<style nonce=\"")
        append(escapeHtml(nonce))
        append("\">")
        if (lightVars.isNotEmpty()) append(bodySelector).append('{').append(lightVars).append('}')
        if (darkVars.isNotEmpty()) {
            append("@media (prefers-color-scheme: dark){")
            append(bodySelector).append('{').append(darkVars).append("}}")
        }
        append("</style>")
    }
}

private fun cssVariables(branding: LoginPageBranding): String =
    buildString {
        branding.primary?.let { append("--color-primary:").append(it).append(';') }
        branding.surface?.let { append("--color-surface:").append(it).append(';') }
        branding.onSurface?.let { append("--color-on-surface:").append(it).append(';') }
    }

internal fun escapeHtml(value: String): String =
    buildString(value.length) {
        for (char in value) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }

private val CSS_COLOR = Regex("""#[0-9A-Fa-f]{3,8}""")
private val CSP_NONCE = Regex("""[A-Za-z0-9_-]{16,}""")
