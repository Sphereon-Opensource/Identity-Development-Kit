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
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.server.authorization.provider.LogoutPageContext
import com.sphereon.oauth2.server.authorization.provider.LogoutPageRenderer
import com.sphereon.oauth2.server.authorization.provider.LogoutPageResponse
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * IDK default [LogoutPageRenderer]. Produces the OIDC RP-Initiated Logout 1.0 confirmation
 * page and the OIDC Front-Channel Logout 1.0 §3 iframe HTML in one self-contained template.
 *
 * Two response shapes:
 *  - `iframes` is empty AND no `postLogoutLocation` is supplied: a one-line "you have been
 *    signed out" confirmation.
 *  - `iframes` is non-empty OR a `postLogoutLocation` is supplied: hidden iframes for every
 *    participating RP plus a meta-refresh / inline JS redirect to the post-logout location
 *    after a short delay so the iframes have time to load.
 *
 * EDK / VDX overlays a tenant-aware implementation through
 * `@ContributesBinding(replaces = [SphereonBrandedLogoutPageRenderer::class], ...)`.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<LogoutPageRenderer>())
class SphereonBrandedLogoutPageRenderer : LogoutPageRenderer {
    override suspend fun render(ctx: LogoutPageContext): IdkResult<LogoutPageResponse, IdkError> {
        val title = TITLES[ctx.locale] ?: TITLES.getValue(DEFAULT_LOCALE)
        val message = MESSAGES[ctx.locale] ?: MESSAGES.getValue(DEFAULT_LOCALE)

        val iframesHtml =
            if (ctx.iframes.isEmpty()) {
                ""
            } else {
                ctx.iframes.joinToString(separator = "\n") { iframe ->
                    "<iframe src=\"${escapeHtml(iframe.iframeUrl)}\" " +
                        "style=\"display:none;\" width=\"0\" height=\"0\" " +
                        "sandbox=\"allow-same-origin allow-scripts\" " +
                        "title=\"front-channel-logout-${escapeHtml(iframe.clientId)}\"></iframe>"
                }
            }

        val redirectMetaTag =
            ctx.postLogoutLocation
                ?.let { url ->
                    "<meta http-equiv=\"refresh\" content=\"$REDIRECT_DELAY_SECONDS;url=${escapeHtml(url)}\">"
                }.orEmpty()

        val redirectScript =
            ctx.postLogoutLocation
                ?.let { url ->
                    """
                |<script>
                |  setTimeout(function() {
                |    window.location.replace(${jsString(url)});
                |  }, ${REDIRECT_DELAY_SECONDS * 1000});
                |</script>
                    """.trimMargin()
                }.orEmpty()

        val html =
            """
            |<!DOCTYPE html>
            |<html lang="${escapeHtml(ctx.locale)}">
            |<head>
            |<meta charset="utf-8">
            |<meta name="viewport" content="width=device-width, initial-scale=1">
            |<title>${escapeHtml(title)}</title>
            |$redirectMetaTag
            |<style>
            |  body { font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
            |         display: flex; align-items: center; justify-content: center;
            |         height: 100vh; margin: 0; background: #f4f6fb; color: #1f2937; }
            |  .card { background: #fff; padding: 2rem 2.5rem; border-radius: 0.75rem;
            |          box-shadow: 0 4px 16px rgba(15, 23, 42, 0.08); max-width: 28rem;
            |          text-align: center; }
            |  h1 { font-size: 1.25rem; margin: 0 0 0.5rem 0; color: #111827; }
            |  p  { font-size: 0.95rem; margin: 0; color: #4b5563; }
            |</style>
            |</head>
            |<body>
            |<main class="card" role="main">
            |  <h1>${escapeHtml(title)}</h1>
            |  <p>${escapeHtml(message)}</p>
            |</main>
            |$iframesHtml
            |$redirectScript
            |</body>
            |</html>
            """.trimMargin()

        return Ok(LogoutPageResponse(html = html))
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

    /**
     * JSON-style string-literal encoding for inline JS so URLs containing quotes or
     * backslashes don't break out of the `window.location.replace(...)` argument.
     */
    private fun jsString(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '\\' -> {
                    sb.append("\\\\")
                }

                '"' -> {
                    sb.append("\\\"")
                }

                '\n' -> {
                    sb.append("\\n")
                }

                '\r' -> {
                    sb.append("\\r")
                }

                '\t' -> {
                    sb.append("\\t")
                }

                '<' -> {
                    sb.append("\\u003c")
                }

                '>' -> {
                    sb.append("\\u003e")
                }

                '&' -> {
                    sb.append("\\u0026")
                }

                else -> {
                    if (ch.code < 0x20) {
                        val hex = ch.code.toString(16).padStart(4, '0')
                        sb.append("\\u").append(hex)
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    companion object {
        private const val DEFAULT_LOCALE: String = "en"
        private const val REDIRECT_DELAY_SECONDS: Int = 1
        private val TITLES: Map<String, String> =
            mapOf(
                "en" to "Signed out",
                "nl" to "Uitgelogd",
            )
        private val MESSAGES: Map<String, String> =
            mapOf(
                "en" to "You have been signed out.",
                "nl" to "U bent uitgelogd.",
            )
    }
}
