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

package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.http.GenericHttpResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locks down the default browser security header set applied to OAuth2 AS responses, plus the
 * Content-Type-driven category resolver, plus that the per-helper application points
 * ([oauth2HtmlErrorPage], [oauth2ErrorResponse]) include the expected subset.
 *
 * Spec-driven defaults (see [securityHeadersFor] for the full list):
 * - HTML responses: HSTS + X-Frame-Options SAMEORIGIN + nosniff + strict CSP
 *   (`default-src 'self'; frame-ancestors 'self'; object-src 'none'`) + Referrer-Policy
 *   + X-Robots-Tag. Pages with inline `<style>` / `<script>` opt-in to a per-response nonce
 *   that expands the CSP to allow exactly those blocks (`'nonce-X'` on style-src and
 *   script-src) — XSS payloads can't guess the nonce.
 * - REST/JSON responses: HSTS + nosniff + Referrer-Policy + X-Robots-Tag (no CSP /
 *   X-Frame-Options because there is no clickable surface).
 * - Binary asset responses: HSTS + nosniff only.
 *
 * Call-site headers take precedence over defaults so an endpoint that needs to opt out of
 * `X-Frame-Options: SAMEORIGIN` (e.g. a future iframe-served session-status page) can override
 * by writing the conflicting value before the helper runs.
 */
class BrowserSecurityHeadersTest {
    @Test
    fun htmlCategoryEmitsStrictCspByDefault() {
        val headers = securityHeadersFor(ResponseCategory.HTML)
        assertEquals("max-age=31536000; includeSubDomains", headers["Strict-Transport-Security"])
        assertEquals("SAMEORIGIN", headers["X-Frame-Options"])
        assertEquals("nosniff", headers["X-Content-Type-Options"])
        // Strict baseline: no `'unsafe-inline'`, no `'unsafe-eval'` — pages without an explicit
        // nonce cannot run inline content. Renderers that need inline blocks MUST opt in.
        assertEquals(
            "default-src 'self'; frame-ancestors 'self'; object-src 'none'",
            headers["Content-Security-Policy"],
        )
        assertEquals("no-referrer", headers["Referrer-Policy"])
        assertEquals("none", headers["X-Robots-Tag"])
    }

    @Test
    fun htmlCategoryWithNonceWhitelistsInlineStyleAndScript() {
        val headers = securityHeadersFor(ResponseCategory.HTML, nonce = "abc123")
        // Inline blocks with a matching `nonce="abc123"` attribute execute; everything else
        // falls back to `default-src 'self'` (same-origin only).
        assertEquals(
            "default-src 'self'; style-src 'self' 'nonce-abc123'; script-src 'self' 'nonce-abc123'; " +
                "frame-ancestors 'self'; object-src 'none'",
            headers["Content-Security-Policy"],
        )
        // Non-CSP defaults are unaffected by nonce opt-in.
        assertEquals("SAMEORIGIN", headers["X-Frame-Options"])
        assertEquals("nosniff", headers["X-Content-Type-Options"])
    }

    @Test
    fun newCspNonceProducesUniqueBase64UrlValues() {
        val a = newCspNonce()
        val b = newCspNonce()
        // Per CSP3 §6.1, nonces MUST be unguessable per response — two consecutive draws must
        // not collide, and the encoded form must be safe to drop into a header / attribute.
        assertTrue(a != b, "consecutive nonces must differ")
        assertTrue(a.isNotEmpty())
        assertTrue(a.all { it.isLetterOrDigit() || it == '-' || it == '_' }, "base64url alphabet only")
    }

    @Test
    fun restCategoryOmitsClickjackingDefenses() {
        val headers = securityHeadersFor(ResponseCategory.REST)
        assertEquals("max-age=31536000; includeSubDomains", headers["Strict-Transport-Security"])
        assertEquals("nosniff", headers["X-Content-Type-Options"])
        assertEquals("no-referrer", headers["Referrer-Policy"])
        assertEquals("none", headers["X-Robots-Tag"])
        assertNull(headers["X-Frame-Options"], "REST responses have no clickable surface so X-Frame-Options is omitted")
        assertNull(headers["Content-Security-Policy"], "REST responses carry no markup so CSP is omitted")
    }

    @Test
    fun binaryCategoryEmitsOnlyTransportAndMimeDefenses() {
        val headers = securityHeadersFor(ResponseCategory.BINARY)
        assertEquals("max-age=31536000; includeSubDomains", headers["Strict-Transport-Security"])
        assertEquals("nosniff", headers["X-Content-Type-Options"])
        assertNull(headers["X-Frame-Options"])
        assertNull(headers["Content-Security-Policy"])
        assertNull(headers["Referrer-Policy"])
        assertNull(headers["X-Robots-Tag"])
    }

    @Test
    fun categoryFromContentTypeRoutesByMimeFamily() {
        assertEquals(ResponseCategory.HTML, html("text/html;charset=UTF-8").responseCategoryFromContentType())
        assertEquals(ResponseCategory.REST, html("application/json").responseCategoryFromContentType())
        assertEquals(ResponseCategory.REST, html("text/plain").responseCategoryFromContentType())
        assertEquals(ResponseCategory.BINARY, html("image/svg+xml").responseCategoryFromContentType())
        assertEquals(ResponseCategory.BINARY, html("font/woff2").responseCategoryFromContentType())
        assertEquals(ResponseCategory.BINARY, html("application/octet-stream").responseCategoryFromContentType())
        // Missing Content-Type defaults to REST (safest fallback: every header in REST applies
        // independently of payload shape, no risk of MIME-mismatch behaviour change).
        assertEquals(ResponseCategory.REST, GenericHttpResponse(statusCode = 200).responseCategoryFromContentType())
    }

    @Test
    fun categoryFromContentTypeIsCaseInsensitive() {
        assertEquals(ResponseCategory.HTML, html("Text/HTML;charset=UTF-8").responseCategoryFromContentType())
        assertEquals(ResponseCategory.BINARY, html("IMAGE/PNG").responseCategoryFromContentType())
    }

    @Test
    fun withSecurityHeadersDoesNotOverwriteCallerHeaders() {
        val response =
            GenericHttpResponse(
                statusCode = 200,
                headers =
                    mapOf(
                        "Content-Type" to "text/html;charset=UTF-8",
                        // Caller intentionally chose DENY (e.g. for a future session-management
                        // iframe); the wrapper must not silently downgrade to SAMEORIGIN.
                        "X-Frame-Options" to "DENY",
                    ),
                body = "<html/>",
            ).withSecurityHeaders(ResponseCategory.HTML)
        assertEquals("DENY", response.headers["X-Frame-Options"])
        // All other defaults still applied.
        assertEquals("max-age=31536000; includeSubDomains", response.headers["Strict-Transport-Security"])
        assertEquals(
            "default-src 'self'; frame-ancestors 'self'; object-src 'none'",
            response.headers["Content-Security-Policy"],
        )
    }

    @Test
    fun withSecurityHeadersIsIdempotent() {
        val once = html("text/html").withSecurityHeaders(ResponseCategory.HTML)
        val twice = once.withSecurityHeaders(ResponseCategory.HTML)
        assertEquals(once.headers, twice.headers)
    }

    @Test
    fun oauth2ErrorResponseCarriesRestHeaders() {
        val response = oauth2ErrorResponse(400, "invalid_request", "missing redirect_uri", Json)
        assertEquals(400, response.statusCode)
        assertEquals("max-age=31536000; includeSubDomains", response.headers["Strict-Transport-Security"])
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        assertEquals("no-referrer", response.headers["Referrer-Policy"])
        assertEquals("none", response.headers["X-Robots-Tag"])
        // RFC 6749 §5.2 cache discipline must survive header layering.
        assertEquals("no-store", response.headers["Cache-Control"])
        assertEquals("no-cache", response.headers["Pragma"])
        assertEquals("application/json", response.headers["Content-Type"])
    }

    @Test
    fun oauth2HtmlErrorPageCarriesHtmlHeaders() {
        val response = oauth2HtmlErrorPage(400, "invalid_request", "missing redirect_uri")
        assertEquals(400, response.statusCode)
        assertEquals("SAMEORIGIN", response.headers["X-Frame-Options"])
        assertEquals("max-age=31536000; includeSubDomains", response.headers["Strict-Transport-Security"])
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        // The error page contains inline <style> + <script>, so it MUST emit a nonce-bearing
        // CSP and stamp the same nonce onto both inline blocks. Without this, the strict
        // `default-src 'self'` baseline would block the page's own styling/JS.
        val csp = response.headers["Content-Security-Policy"] ?: error("CSP header missing")
        val nonceMatch =
            Regex("'nonce-([A-Za-z0-9_-]+)'").find(csp)
                ?: error("CSP did not declare a nonce: $csp")
        val nonce = nonceMatch.groupValues[1]
        assertTrue(csp.contains("default-src 'self'"))
        assertTrue(csp.contains("style-src 'self' 'nonce-$nonce'"))
        assertTrue(csp.contains("script-src 'self' 'nonce-$nonce'"))
        // Both inline blocks in the response body must carry the matching nonce so the browser
        // executes them under the strict CSP.
        val body = response.body ?: error("body missing")
        assertTrue(body.contains("<style nonce=\"$nonce\">"), "<style> must carry nonce")
        assertTrue(body.contains("<script nonce=\"$nonce\">"), "<script> must carry nonce")
        // The error page renders the OAuth `error` code + description for the user; verify the
        // body actually carries them so adding security headers did not regress the body shape.
        assertTrue(body.contains("invalid_request"))
        assertTrue(body.contains("missing redirect_uri"))
    }

    private fun html(contentType: String): GenericHttpResponse = GenericHttpResponse(statusCode = 200, headers = mapOf("Content-Type" to contentType), body = "")
}
