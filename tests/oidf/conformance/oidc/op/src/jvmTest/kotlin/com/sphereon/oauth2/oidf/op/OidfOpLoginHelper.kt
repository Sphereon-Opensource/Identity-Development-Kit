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

package com.sphereon.oauth2.oidf.op

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters

/**
 * Shared helper for the conformance harness tests that drive POST /login.
 *
 * The OAuth2 AS login endpoint enforces RFC 6749 §10.12-style CSRF defense: every render of
 * GET /login mints a fresh `tab_id` + HMAC-bound `session_code` (embedded as hidden form inputs)
 * and pins the `tab_id` to an `oidc_login_csrf` cookie. POST /login refuses any submission
 * that does not echo all three values back, which means a test that hand-rolls
 * `client.submitForm` with only `username`/`password`/`session_id`/`return_url` gets a
 * `400 invalid_request` from `LoginSubmitHttpEndpointCommandImpl`.
 *
 * The helper performs the proper render-then-submit dance:
 *  1. GET `<loginUrl>` — the URL the AS handed back in the `Location` header of GET /authorize.
 *     The render sets `Set-Cookie: oidc_login_csrf=<tab_id>` and embeds matching `tab_id` +
 *     `session_code` hidden inputs in the form HTML.
 *  2. Extract the cookie value and the form fields.
 *  3. POST /login carrying all six form parameters and the captured cookie.
 *
 * Returns the raw [HttpResponse] from the POST so callers can assert on status and the
 * issued `Set-Cookie: oidc_login_sid=...` header.
 */
internal suspend fun submitLoginWithCsrf(
    client: HttpClient,
    baseUrl: String,
    loginUrl: String,
    sessionId: String,
    returnUrl: String,
    username: String,
    password: String,
): HttpResponse {
    val pageResponse = client.get(loginUrl)
    val csrfCookie =
        pageResponse.headers["Set-Cookie"]
            ?.split(",")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("oidc_login_csrf=") }
            ?: error("GET /login must Set-Cookie oidc_login_csrf=<tab_id>")
    val pageBody = pageResponse.bodyAsText()
    val tabId =
        TAB_ID_FORM_REGEX.find(pageBody)?.groupValues?.get(1)
            ?: error("login page HTML must contain a hidden input name=\"tab_id\"")
    val sessionCode =
        SESSION_CODE_FORM_REGEX.find(pageBody)?.groupValues?.get(1)
            ?: error("login page HTML must contain a hidden input name=\"session_code\"")
    return client.submitForm(
        url = "$baseUrl/login",
        formParameters =
            Parameters.build {
                append("username", username)
                append("password", password)
                append("session_id", sessionId)
                append("return_url", returnUrl)
                append("tab_id", tabId)
                append("session_code", sessionCode)
            },
    ) {
        header("Cookie", csrfCookie.substringBefore(";"))
    }
}

private val TAB_ID_FORM_REGEX = Regex("""<input[^>]*name="tab_id"[^>]*value="([^"]+)"""")
private val SESSION_CODE_FORM_REGEX = Regex("""<input[^>]*name="session_code"[^>]*value="([^"]+)"""")
