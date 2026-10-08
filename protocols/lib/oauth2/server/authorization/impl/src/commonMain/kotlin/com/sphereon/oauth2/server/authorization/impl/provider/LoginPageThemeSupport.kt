/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.oauth2.server.authorization.provider.LoginPageBranding
import com.sphereon.oauth2.server.authorization.provider.LoginPageContext

internal fun LoginPageContext.resolvedLightBranding(): LoginPageBranding =
    lightBranding ?: LoginPageBranding()

internal fun LoginPageContext.resolvedDarkBranding(): LoginPageBranding =
    darkBranding ?: lightBranding ?: LoginPageBranding()

internal fun themeStyleBlock(
    ctx: LoginPageContext,
    bodySelector: String,
): String {
    val nonce = ctx.cspNonce?.takeIf { CSP_NONCE.matches(it) } ?: return ""
    val light = ctx.resolvedLightBranding()
    val dark = ctx.resolvedDarkBranding()
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

private val CSP_NONCE = Regex("""[A-Za-z0-9_-]{16,}""")
