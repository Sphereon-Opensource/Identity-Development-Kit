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
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.server.authorization.provider.DeviceApprovalContext
import com.sphereon.oauth2.server.authorization.provider.DeviceEntryContext
import com.sphereon.oauth2.server.authorization.provider.DeviceResultContext
import com.sphereon.oauth2.server.authorization.provider.DeviceResultOutcome
import com.sphereon.oauth2.server.authorization.provider.DeviceVerificationPageRenderer
import com.sphereon.oauth2.server.authorization.provider.DeviceVerificationResponse
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * IDK default [DeviceVerificationPageRenderer]: renders the three RFC 8628 §3.3 surfaces with
 * minimal Sphereon-branded HTML. Reuses the bundled `/login/assets/css/login.css` so the visual
 * style matches the AS login page without bundling a duplicate stylesheet. i18n strings are
 * loaded from `device/sphereon/i18n/messages-<locale>.properties` (en + nl), mirroring the
 * [SphereonBrandedLoginPageRenderer] convention.
 *
 * EDK / VDX overlays a tenant-aware implementation through
 * `@ContributesBinding(replaces = SphereonBrandedDeviceVerificationPageRenderer::class, ...)`.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DeviceVerificationPageRenderer>())
class SphereonBrandedDeviceVerificationPageRenderer : DeviceVerificationPageRenderer {
    private val messages: Map<String, Map<String, String>> by lazy {
        SUPPORTED_LOCALES.associateWith { locale ->
            val path = "$I18N_PREFIX/messages-$locale.properties"
            val text =
                readClasspathResource(path)
                    ?: error("Device verification renderer i18n bundle missing: $path")
            parseProperties(text)
        }
    }

    override suspend fun renderEntryForm(context: DeviceEntryContext): IdkResult<DeviceVerificationResponse, IdkError> {
        val msg = resolveMessages(context.locale)
        val errorBlock =
            context.errorMessage
                ?.takeIf { it.isNotBlank() }
                ?.let { raw ->
                    val displayed = msg["device.entry.invalidCode"] ?: raw
                    "<div class=\"input-error\">${escapeHtml(displayed)}</div>"
                }.orEmpty()
        val prefilled = context.prefilledUserCode?.let { escapeHtml(it) }.orEmpty()
        val html =
            buildString {
                append(htmlOpen(context.locale, msg.getValue("device.entry.title")))
                append(shellHeader(msg.getValue("device.entry.title")))
                append("<p class=\"login-subtitle\">")
                append(escapeHtml(msg.getValue("device.entry.intro")))
                append("</p>")
                append(errorBlock)
                append("<form class=\"form-sections\" method=\"post\" action=\"/device\" autocomplete=\"off\">")
                append("<div class=\"form-fields\">")
                append("<div class=\"input-group\"><div class=\"input-container\"><div class=\"input-wrapper\">")
                append("<input id=\"user_code\" name=\"user_code\" type=\"text\" class=\"input-field\" ")
                append("pattern=\"[A-Za-z0-9]{4}-?[A-Za-z0-9]{4}\" required autofocus ")
                append("placeholder=\"")
                append(escapeHtml(msg.getValue("device.entry.placeholder")))
                append("\" value=\"")
                append(prefilled)
                append("\">")
                append("<label for=\"user_code\" class=\"input-label\">")
                append(escapeHtml(msg.getValue("device.entry.label")))
                append("</label>")
                append("</div></div></div>")
                append("</div>")
                append("<button type=\"submit\" class=\"primary-button\">")
                append(escapeHtml(msg.getValue("device.entry.submit")))
                append("</button>")
                append("</form>")
                append(shellClose())
            }
        return Ok(DeviceVerificationResponse(html = html))
    }

    override suspend fun renderApprovalPrompt(context: DeviceApprovalContext): IdkResult<DeviceVerificationResponse, IdkError> {
        val msg = resolveMessages(context.locale)
        val displayName = context.clientName?.takeIf { it.isNotBlank() } ?: context.clientId
        val scopeItems =
            context.requestedScopes.joinToString(separator = "") { scope ->
                val pretty = msg["device.scope.$scope"] ?: scope
                "<li>${escapeHtml(pretty)}</li>"
            }
        val html =
            buildString {
                append(htmlOpen(context.locale, msg.getValue("device.approve.title")))
                append(shellHeader(msg.getValue("device.approve.title")))
                append("<p class=\"login-subtitle\">")
                append(escapeHtml(msg.getValue("device.approve.intro").replace("{client}", displayName)))
                append("</p>")
                if (context.requestedScopes.isNotEmpty()) {
                    append("<p class=\"device-scope-heading\">")
                    append(escapeHtml(msg.getValue("device.approve.scopesHeading")))
                    append("</p>")
                    append("<ul class=\"device-scope-list\">")
                    append(scopeItems)
                    append("</ul>")
                }
                append("<form class=\"form-sections\" method=\"post\" action=\"/device/approve\" autocomplete=\"off\">")
                append("<input type=\"hidden\" name=\"user_code\" value=\"")
                append(escapeHtml(context.userCode))
                append("\">")
                append("<div class=\"device-actions\">")
                append("<button type=\"submit\" name=\"action\" value=\"deny\" class=\"secondary-button\">")
                append(escapeHtml(msg.getValue("device.approve.deny")))
                append("</button>")
                append("<button type=\"submit\" name=\"action\" value=\"allow\" class=\"primary-button\">")
                append(escapeHtml(msg.getValue("device.approve.allow")))
                append("</button>")
                append("</div>")
                append("</form>")
                append(shellClose())
            }
        return Ok(DeviceVerificationResponse(html = html))
    }

    override suspend fun renderResultPage(context: DeviceResultContext): IdkResult<DeviceVerificationResponse, IdkError> {
        val msg = resolveMessages(context.locale)
        val (titleKey, bodyKey) =
            when (context.outcome) {
                DeviceResultOutcome.APPROVED -> "device.result.approved.title" to "device.result.approved.body"
                DeviceResultOutcome.DENIED -> "device.result.denied.title" to "device.result.denied.body"
                DeviceResultOutcome.EXPIRED -> "device.result.expired.title" to "device.result.expired.body"
                DeviceResultOutcome.INVALID_CODE -> "device.result.invalidCode.title" to "device.result.invalidCode.body"
            }
        val html =
            buildString {
                append(htmlOpen(context.locale, msg.getValue(titleKey)))
                append(shellHeader(msg.getValue(titleKey)))
                append("<p class=\"login-subtitle\">")
                append(escapeHtml(msg.getValue(bodyKey)))
                append("</p>")
                append(shellClose())
            }
        return Ok(DeviceVerificationResponse(html = html))
    }

    private fun resolveMessages(locale: String): Map<String, String> {
        val resolved = if (messages.containsKey(locale)) locale else DEFAULT_LOCALE
        return messages.getValue(resolved)
    }

    private fun htmlOpen(
        locale: String,
        title: String,
    ): String =
        buildString {
            append("<!DOCTYPE html><html lang=\"").append(escapeHtml(locale)).append("\">")
            append("<head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<meta name=\"robots\" content=\"noindex, nofollow\">")
            append("<title>").append(escapeHtml(title)).append("</title>")
            append("<link rel=\"stylesheet\" href=\"").append(LOGIN_ASSETS_BASE).append("/css/login.css\">")
            append("<style>")
            append(".device-scope-heading{margin-top:1rem;font-weight:600}")
            append(".device-scope-list{margin:0.5rem 0 1.25rem 1.25rem;padding:0}")
            append(".device-scope-list li{margin:0.25rem 0}")
            append(".device-actions{display:flex;gap:0.75rem;margin-top:1rem;flex-wrap:wrap}")
            append(".secondary-button{flex:1;padding:0.65rem 1rem;border-radius:8px;background:transparent;border:1px solid #ccd2d8;color:#1f2933;cursor:pointer;font-size:1rem}")
            append(".primary-button{flex:1}")
            append("</style>")
            append("</head><body class=\"sphereon-login\"><div class=\"login-page\">")
            append("<div class=\"login-shell\">")
        }

    private fun shellHeader(title: String): String =
        buildString {
            append("<header class=\"login-header\">")
            append("<img class=\"login-logo\" src=\"").append(LOGIN_ASSETS_BASE).append("/img/sphereon-logo.svg\" alt=\"Sphereon\">")
            append("</header>")
            append("<main class=\"login-card\">")
            append("<h1 class=\"login-title\">").append(escapeHtml(title)).append("</h1>")
        }

    private fun shellClose(): String = "</main></div></div></body></html>"

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
        /**
         * Reuse the login renderer's bundled CSS + logo so the device verification surface inherits
         * the same Sphereon-branded look without bundling duplicate assets. The login asset endpoint
         * is mounted unconditionally as part of the AS login surface.
         */
        internal const val LOGIN_ASSETS_BASE: String = "/login/assets"

        private const val RESOURCE_BASE: String = "device/sphereon"
        private const val I18N_PREFIX: String = "$RESOURCE_BASE/i18n"
        private const val DEFAULT_LOCALE: String = "en"
        private val SUPPORTED_LOCALES: Set<String> = setOf("en", "nl")
    }
}
