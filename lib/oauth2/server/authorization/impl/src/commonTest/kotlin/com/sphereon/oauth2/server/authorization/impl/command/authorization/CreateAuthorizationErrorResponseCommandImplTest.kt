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
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseArgs
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.newCreateAuthorizationErrorResponseCommand
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for authorization-endpoint error responses shaped per
 * [OAuth2ResponseMode] by [CreateAuthorizationErrorResponseCommandImpl]. Mirrors
 * [CreateAuthorizationResponseCommandImplTest] for the success path.
 */
class CreateAuthorizationErrorResponseCommandImplTest {
    private val ctx = OAuth2ServerTestContext("create-auth-error-test", this)
    private val command = ctx.newCreateAuthorizationErrorResponseCommand()

    @Test
    fun queryErrorAppendsParametersAsQueryString() =
        runTest {
            val result =
                command.execute(
                    CreateAuthorizationErrorResponseArgs(
                        error = "invalid_scope",
                        errorDescription = "scope foo not allowed",
                        state = "s-42",
                        redirectUri = "https://rp.example.com/cb",
                        responseMode = OAuth2ResponseMode.QUERY,
                    ),
                )
            assertTrue(result.isOk)
            val response = result.value
            assertEquals(OAuth2ResponseMode.QUERY, response.responseMode)
            // Order: error, error_description, state (insertion order preserved)
            assertEquals(
                "https://rp.example.com/cb?error=invalid_scope&error_description=scope%20foo%20not%20allowed&state=s-42",
                response.redirectUri,
            )
            assertNull(response.formPostHtml)
        }

    @Test
    fun fragmentErrorAppendsParametersAsFragment() =
        runTest {
            val result =
                command.execute(
                    CreateAuthorizationErrorResponseArgs(
                        error = "access_denied",
                        state = "x",
                        redirectUri = "https://rp.example.com/cb",
                        responseMode = OAuth2ResponseMode.FRAGMENT,
                    ),
                )
            assertTrue(result.isOk)
            assertEquals(
                "https://rp.example.com/cb#error=access_denied&state=x",
                result.value.redirectUri,
            )
            assertNull(result.value.formPostHtml)
        }

    @Test
    fun formPostErrorPopulatesHtmlAndKeepsBareRedirectUri() =
        runTest {
            val result =
                command.execute(
                    CreateAuthorizationErrorResponseArgs(
                        error = "invalid_request",
                        errorDescription = "bad response_type",
                        state = "state-xyz",
                        redirectUri = "https://rp.example.com/cb",
                        responseMode = OAuth2ResponseMode.FORM_POST,
                    ),
                )
            assertTrue(result.isOk)
            val response = result.value
            assertEquals(OAuth2ResponseMode.FORM_POST, response.responseMode)
            assertEquals("https://rp.example.com/cb", response.redirectUri)

            val html = response.formPostHtml
            assertNotNull(html)
            assertTrue(html.contains("<form method=\"post\" action=\"https://rp.example.com/cb\""))
            assertTrue(html.contains("name=\"error\"") && html.contains("value=\"invalid_request\""))
            assertTrue(html.contains("name=\"error_description\""))
            assertTrue(html.contains("name=\"state\"") && html.contains("value=\"state-xyz\""))
            assertTrue(html.contains("<noscript>"))
        }

    @Test
    fun formPostErrorHtmlEscapesValues() =
        runTest {
            // Attacker-supplied state that would escape a hidden input without HTML escaping.
            val result =
                command.execute(
                    CreateAuthorizationErrorResponseArgs(
                        error = "invalid_request",
                        state = "\"><script>alert('xss')</script>",
                        redirectUri = "https://rp.example.com/cb",
                        responseMode = OAuth2ResponseMode.FORM_POST,
                    ),
                )
            assertTrue(result.isOk)
            val html = result.value.formPostHtml
            assertNotNull(html)
            assertFalse(html.contains("<script>"))
            assertFalse(html.contains("\"><script"))
            assertTrue(html.contains("&quot;") && html.contains("&lt;script&gt;"))
        }

    @Test
    fun omittedOptionalsAreAbsentFromOutput() =
        runTest {
            val result =
                command.execute(
                    CreateAuthorizationErrorResponseArgs(
                        error = "access_denied",
                        errorDescription = null,
                        errorUri = null,
                        state = null,
                        redirectUri = "https://rp.example.com/cb",
                        responseMode = OAuth2ResponseMode.QUERY,
                    ),
                )
            assertTrue(result.isOk)
            assertEquals("https://rp.example.com/cb?error=access_denied", result.value.redirectUri)
        }

    @Test
    fun formPostHtmlIsNullForNonFormPostModes() =
        runTest {
            // Restrict to the bare carriers, since the JARM `*.jwt` variants need a JARM-enabled
            // server config + a per-client signing alg, which the stubbed test setup does not wire.
            val nonFormPostBareModes = setOf(OAuth2ResponseMode.QUERY, OAuth2ResponseMode.FRAGMENT)
            nonFormPostBareModes.forEach { mode ->
                val result =
                    command.execute(
                        CreateAuthorizationErrorResponseArgs(
                            error = "access_denied",
                            redirectUri = "https://rp.example.com/cb",
                            responseMode = mode,
                        ),
                    )
                assertTrue(result.isOk, "mode=$mode failed: ${if (!result.isOk) result.error else ""}")
                assertNull(result.value.formPostHtml, "formPostHtml must be null for mode=$mode")
            }
        }
}
