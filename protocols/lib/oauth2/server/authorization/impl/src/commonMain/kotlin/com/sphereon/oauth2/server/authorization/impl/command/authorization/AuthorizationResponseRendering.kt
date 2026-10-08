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

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.core.api.encodeUrlGraph

/*
 * Shared rendering helpers for the authorization endpoint's success and error responses.
 *
 * Both CreateAuthorizationResponseCommandImpl and CreateAuthorizationErrorResponseCommandImpl
 * need identical URL-parameter shaping and (for `form_post`) identical HTML escaping rules, so
 * the logic lives here once.
 */

/** Append [parameters] to [baseUri] as a query string, preserving any pre-existing `?` segment. */
internal fun buildQueryRedirect(
    baseUri: String,
    parameters: Map<String, String>,
): String {
    if (parameters.isEmpty()) return baseUri
    val separator = if (baseUri.contains("?")) "&" else "?"
    val queryString = parameters.entries.joinToString("&") { (k, v) -> "${k.encodeUrlGraph()}=${v.encodeUrlGraph()}" }
    return "$baseUri$separator$queryString"
}

/** Append [parameters] to [baseUri] as a `#fragment` string. */
internal fun buildFragmentRedirect(
    baseUri: String,
    parameters: Map<String, String>,
): String {
    if (parameters.isEmpty()) return baseUri
    val fragmentString = parameters.entries.joinToString("&") { (k, v) -> "${k.encodeUrlGraph()}=${v.encodeUrlGraph()}" }
    return "$baseUri#$fragmentString"
}

/**
 * Build an auto-submitting HTML form per OAuth 2.0 Form Post Response Mode §2. All values
 * (including [redirectUri], which is the form `action`) are HTML-escaped. A `<noscript>`
 * button lets users without JS submit manually.
 */
internal fun buildFormPostHtml(
    redirectUri: String,
    parameters: Map<String, String>,
): String {
    val action = redirectUri.htmlEscape()
    val inputs =
        parameters.entries.joinToString("\n    ") { (name, value) ->
            "<input type=\"hidden\" name=\"${name.htmlEscape()}\" value=\"${value.htmlEscape()}\"/>"
        }
    return buildString {
        append("<!doctype html>\n")
        append("<html><head><meta charset=\"UTF-8\"><title>Submitting…</title></head>")
        append("<body onload=\"document.forms[0].submit()\">")
        append("<form method=\"post\" action=\"").append(action).append("\">\n    ")
        append(inputs)
        append("\n    <noscript><button type=\"submit\">Continue</button></noscript>")
        append("</form></body></html>")
    }
}

/**
 * Escape the five core HTML entities in attribute values and text nodes. `'` is emitted as
 * `&#39;` (not `&apos;`) for broadest legacy-browser compatibility.
 */
internal fun String.htmlEscape(): String =
    buildString(length) {
        for (ch in this@htmlEscape) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }
