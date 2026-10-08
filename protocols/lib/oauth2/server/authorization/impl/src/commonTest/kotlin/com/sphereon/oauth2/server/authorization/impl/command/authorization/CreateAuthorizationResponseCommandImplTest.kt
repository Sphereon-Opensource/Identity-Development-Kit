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

import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseArgs
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.newCreateAuthorizationResponseCommand
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [CreateAuthorizationResponseCommandImpl] per resolved [OAuth2ResponseMode].
 *
 * Covers query/fragment redirect shaping, form_post HTML generation (auto-submit form, hidden
 * fields, HTML escaping), and the invariant that `formPostHtml` is populated iff mode is
 * FORM_POST.
 */
class CreateAuthorizationResponseCommandImplTest {
    private val ctx = OAuth2ServerTestContext("create-auth-response-test", this)
    private val command = ctx.newCreateAuthorizationResponseCommand()

    // ─── QUERY ──────────────────────────────────────────────────────────────

    @Test
    fun queryResponseAppendsCodeAndStateAsQueryString() =
        runTest {
            val result =
                command.execute(
                    CreateAuthorizationResponseArgs(
                        code = "auth-code-xyz",
                        state = "s-42",
                        redirectUri = "https://rp.example.com/cb",
                        responseMode = OAuth2ResponseMode.QUERY,
                    ),
                )
            assertTrue(result.isOk)
            val response = result.value
            assertEquals(OAuth2ResponseMode.QUERY, response.responseMode)
            assertEquals("https://rp.example.com/cb?code=auth-code-xyz&state=s-42", response.redirectUri)
            assertNull(response.formPostHtml, "QUERY response must not populate formPostHtml")
        }

    @Test
    fun queryResponsePreservesExistingQueryParams() =
        runTest {
            val result =
                command.execute(
                    CreateAuthorizationResponseArgs(
                        code = "c",
                        state = null,
                        redirectUri = "https://rp.example.com/cb?prior=1",
                        responseMode = OAuth2ResponseMode.QUERY,
                    ),
                )
            assertTrue(result.isOk)
            assertEquals("https://rp.example.com/cb?prior=1&code=c", result.value.redirectUri)
        }

    // ─── FRAGMENT ───────────────────────────────────────────────────────────

    @Test
    fun fragmentResponseAppendsParametersAsFragment() =
        runTest {
            val result =
                command.execute(
                    CreateAuthorizationResponseArgs(
                        code = "code-abc",
                        state = "s-1",
                        redirectUri = "https://rp.example.com/cb",
                        responseMode = OAuth2ResponseMode.FRAGMENT,
                    ),
                )
            assertTrue(result.isOk)
            assertEquals("https://rp.example.com/cb#code=code-abc&state=s-1", result.value.redirectUri)
            assertNull(result.value.formPostHtml)
        }

    // ─── FORM_POST ──────────────────────────────────────────────────────────

    @Test
    fun formPostResponsePopulatesHtmlAndPreservesBareRedirectUri() =
        runTest {
            val result =
                command.execute(
                    CreateAuthorizationResponseArgs(
                        code = "code-123",
                        state = "state-xyz",
                        redirectUri = "https://rp.example.com/cb",
                        responseMode = OAuth2ResponseMode.FORM_POST,
                    ),
                )
            assertTrue(result.isOk)
            val response = result.value
            assertEquals(OAuth2ResponseMode.FORM_POST, response.responseMode)
            assertEquals("https://rp.example.com/cb", response.redirectUri, "FORM_POST keeps the bare redirect URI; params live in formPostHtml")

            val html = response.formPostHtml
            assertNotNull(html, "FORM_POST response must populate formPostHtml")
            assertTrue(html.contains("<form method=\"post\" action=\"https://rp.example.com/cb\""), "HTML must target the registered redirect URI: $html")
            assertTrue(html.contains("name=\"code\""), "HTML must contain a hidden input for code")
            assertTrue(html.contains("value=\"code-123\""))
            assertTrue(html.contains("name=\"state\""))
            assertTrue(html.contains("value=\"state-xyz\""))
            assertTrue(html.contains("onload=\"document.forms[0].submit()\""), "HTML must auto-submit on load")
            assertTrue(html.contains("<noscript>"), "HTML must include a <noscript> fallback")
        }

    @Test
    fun formPostResponseHtmlEscapesParameterValues() =
        runTest {
            val result =
                command.execute(
                    CreateAuthorizationResponseArgs(
                        // malicious `state` that would break out of the hidden input if not escaped
                        code = "normal-code",
                        state = "\"><script>alert('xss')</script><input ",
                        redirectUri = "https://rp.example.com/cb",
                        responseMode = OAuth2ResponseMode.FORM_POST,
                    ),
                )
            assertTrue(result.isOk)
            val html = result.value.formPostHtml
            assertNotNull(html)
            assertFalse(html.contains("<script>"), "raw <script> must not appear — state value must be HTML-escaped: $html")
            assertFalse(html.contains("\"><script"), "attribute-breakout attempt must be escaped: $html")
            assertTrue(html.contains("&quot;"))
            assertTrue(html.contains("&lt;script&gt;"))
        }

    @Test
    fun formPostResponseWithNullStateOmitsStateField() =
        runTest {
            val result =
                command.execute(
                    CreateAuthorizationResponseArgs(
                        code = "c",
                        state = null,
                        redirectUri = "https://rp.example.com/cb",
                        responseMode = OAuth2ResponseMode.FORM_POST,
                    ),
                )
            assertTrue(result.isOk)
            val html = result.value.formPostHtml
            assertNotNull(html)
            assertTrue(html.contains("name=\"code\""))
            assertFalse(html.contains("name=\"state\""), "absent state must not produce a state hidden input: $html")
        }

    // ─── Invariant: formPostHtml null iff mode != FORM_POST ─────────────────

    @Test
    fun formPostHtmlIsNullForNonFormPostModes() =
        runTest {
            // The bare carriers (`query`, `fragment`) are JARM-disabled and reach the response
            // command directly. JARM `*.jwt` modes need a JARM-enabled config + a per-client
            // signing alg, which the stubbed test setup does not wire — they're covered in
            // [OidfOpJarmTest] in the conformance harness instead.
            val nonFormPostBareModes = setOf(OAuth2ResponseMode.QUERY, OAuth2ResponseMode.FRAGMENT)
            nonFormPostBareModes.forEach { mode ->
                val result =
                    command.execute(
                        CreateAuthorizationResponseArgs(
                            code = "c",
                            state = "s",
                            redirectUri = "https://rp.example.com/cb",
                            responseMode = mode,
                        ),
                    )
                assertTrue(result.isOk)
                assertNull(result.value.formPostHtml, "formPostHtml must be null for mode=$mode")
            }
        }
}
