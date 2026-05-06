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

package com.sphereon.oauth2.client.impl.authorization

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.oauth2.client.command.AuthorizationResponseSource
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseArgs
import com.sphereon.oauth2.client.command.ParsedAuthorizationResponse
import com.sphereon.oauth2.client.testutil.createOAuth2ClientTestAppGraph
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParseAuthorizationResponseCommandImplTest {
    private val app = createOAuth2ClientTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("parse-auth-response-test")
    private val execution = session.asCoreApiServiceGraph().serviceExecution
    private val command = ParseAuthorizationResponseCommandImpl(execution)

    @Test
    fun parse_queryHappyPath_extractsCodeAndState() =
        runTest {
            val url = "https://rp.example.com/callback?code=abc123&state=xyz789"
            val result = command.execute(ParseAuthorizationResponseArgs(redirectUrl = url))
            assertTrue(result.isOk, "expected success: ${if (result.isErr) result.error else ""}")
            val parsed = result.value
            assertTrue(parsed is ParsedAuthorizationResponse.Success)
            assertEquals("abc123", parsed.response.code)
            assertEquals("xyz789", parsed.response.state)
        }

    @Test
    fun parse_formPostHappyPath_extractsCodeAndState() =
        runTest {
            val result =
                command.execute(
                    ParseAuthorizationResponseArgs(
                        redirectUrl = "https://rp.example.com/callback",
                        source = AuthorizationResponseSource.FORM_POST,
                        formBody = "code=abc123&state=xyz789",
                    ),
                )
            assertTrue(result.isOk, "expected success: ${if (result.isErr) result.error else ""}")
            val parsed = result.value
            assertTrue(parsed is ParsedAuthorizationResponse.Success)
            assertEquals("abc123", parsed.response.code)
            assertEquals("xyz789", parsed.response.state)
        }

    @Test
    fun parse_formPost_urlEncodedValues_areDecoded() =
        runTest {
            val result =
                command.execute(
                    ParseAuthorizationResponseArgs(
                        redirectUrl = "https://rp.example.com/callback",
                        source = AuthorizationResponseSource.FORM_POST,
                        formBody = "code=ab%3Dcd&state=sp%20ace",
                    ),
                )
            assertTrue(result.isOk)
            val parsed = result.value as ParsedAuthorizationResponse.Success
            assertEquals("ab=cd", parsed.response.code)
            assertEquals("sp ace", parsed.response.state)
        }

    @Test
    fun parse_errorResponse_query_extractsErrorFields() =
        runTest {
            val url =
                "https://rp.example.com/callback?error=access_denied" +
                    "&error_description=user+refused&error_uri=https%3A%2F%2Fdocs.example.com%2Ferrors&state=xyz"
            val result = command.execute(ParseAuthorizationResponseArgs(redirectUrl = url))
            assertTrue(result.isOk)
            val parsed = result.value
            assertTrue(parsed is ParsedAuthorizationResponse.Error)
            assertEquals("access_denied", parsed.response.error)
            assertEquals("user refused", parsed.response.errorDescription)
            assertEquals("https://docs.example.com/errors", parsed.response.errorUri)
            assertEquals("xyz", parsed.response.state)
        }

    @Test
    fun parse_errorResponse_formPost_extractsErrorFields() =
        runTest {
            val result =
                command.execute(
                    ParseAuthorizationResponseArgs(
                        redirectUrl = "https://rp.example.com/callback",
                        source = AuthorizationResponseSource.FORM_POST,
                        formBody = "error=access_denied&error_description=user%20cancelled&state=xyz",
                    ),
                )
            assertTrue(result.isOk)
            val parsed = result.value as ParsedAuthorizationResponse.Error
            assertEquals("access_denied", parsed.response.error)
            assertEquals("user cancelled", parsed.response.errorDescription)
            assertEquals("xyz", parsed.response.state)
        }

    @Test
    fun parse_fragment_returnsUnsupportedForNow() =
        runTest {
            val result =
                command.execute(
                    ParseAuthorizationResponseArgs(
                        redirectUrl = "https://rp.example.com/callback#code=abc&state=xyz",
                        source = AuthorizationResponseSource.FRAGMENT,
                    ),
                )
            assertTrue(result.isErr, "fragment source must not silently succeed")
            assertEquals("invalid_request", result.error.code)
        }

    @Test
    fun parse_formPost_missingBody_returnsErr() =
        runTest {
            val result =
                command.execute(
                    ParseAuthorizationResponseArgs(
                        redirectUrl = "https://rp.example.com/callback",
                        source = AuthorizationResponseSource.FORM_POST,
                        formBody = null,
                    ),
                )
            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
        }

    @Test
    fun parse_query_missingCode_returnsErr() =
        runTest {
            val result =
                command.execute(
                    ParseAuthorizationResponseArgs(
                        redirectUrl = "https://rp.example.com/callback?state=xyz",
                    ),
                )
            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
        }
}
