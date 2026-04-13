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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.common.buildOid4vpAuthorizationRequest
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriArgs
import com.sphereon.openid.oid4vp.verifier.Oid4vpUriScheme
import com.sphereon.openid.oid4vp.verifier.impl.testutil.Oid4vpVerifierTestContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for BuildAuthorizationRequestUriCommandImpl
 */
class BuildAuthorizationRequestUriCommandImplTest {
    private val testContext = Oid4vpVerifierTestContext("build-uri-test", this)
    private val command = createTestCommand()

    // ============================================================================
    // Full Parameter URI Tests
    // ============================================================================

    @Test
    fun testBuildBasicUriWithOpenid4vpScheme() =
        runTest {
            // Given: Basic authorization request
            val dcqlJson =
                Json.encodeToJsonElement(
                    DcqlQuery.serializer(),
                    DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "test_cred"))),
                ) as JsonObject

            val request =
                buildOid4vpAuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                ) {
                    nonce("nonce12345678")
                    state("state123")
                    responseMode(ResponseMode.DIRECT_POST)
                    dcqlQuery(dcqlJson)
                }

            val args =
                BuildAuthorizationRequestUriArgs(
                    request = request,
                    scheme = Oid4vpUriScheme.OPENID4VP,
                )

            // When: Building the URI
            val result = command.buildAuthorizationRequestUri(args)

            // Then: Should build valid openid4vp:// URI
            assertIs<Ok<*>>(result)
            val uri = result.value.value

            assertTrue(uri.startsWith("openid4vp://?"))
            assertTrue(uri.contains("client_id="))
            assertTrue(uri.contains("response_type="))
            assertTrue(uri.contains("redirect_uri="))
            assertTrue(uri.contains("nonce="))
            assertTrue(uri.contains("state="))
        }

    @Test
    fun testBuildUriWithOpenidScheme() =
        runTest {
            val request =
                buildOid4vpAuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                ) {
                    nonce("nonce12345678")
                }

            val args =
                BuildAuthorizationRequestUriArgs(
                    request = request,
                    scheme = Oid4vpUriScheme.OPENID,
                )

            val result = command.buildAuthorizationRequestUri(args)

            assertIs<Ok<*>>(result)
            assertTrue(result.value.value.startsWith("openid://?"))
        }

    @Test
    fun testBuildUriWithHaipScheme() =
        runTest {
            val request =
                buildOid4vpAuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                ) {
                    nonce("nonce12345678")
                }

            val args =
                BuildAuthorizationRequestUriArgs(
                    request = request,
                    scheme = Oid4vpUriScheme.HAIP,
                )

            val result = command.buildAuthorizationRequestUri(args)

            assertIs<Ok<*>>(result)
            assertTrue(result.value.value.startsWith("haip://?"))
        }

    @Test
    fun testUriIncludesDcqlQuery() =
        runTest {
            val dcqlJson =
                Json.encodeToJsonElement(
                    DcqlQuery.serializer(),
                    DcqlQuery(
                        credentials =
                            listOf(
                                DcqlCredentialQuery(id = "identity_credential", format = "dc+sd-jwt"),
                            ),
                    ),
                ) as JsonObject

            val request =
                buildOid4vpAuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                ) {
                    nonce("nonce12345678")
                    dcqlQuery(dcqlJson)
                }

            val args =
                BuildAuthorizationRequestUriArgs(
                    request = request,
                    scheme = Oid4vpUriScheme.OPENID4VP,
                )

            val result = command.buildAuthorizationRequestUri(args)

            assertIs<Ok<*>>(result)
            val uri = result.value.value

            // Should contain dcql_query parameter (URL encoded)
            assertTrue(uri.contains("dcql_query="))
            // Should contain credential format in encoded form
            assertTrue(uri.contains("dc%2Bsd-jwt") || uri.contains("identity_credential"))
        }

    @Test
    fun testUriIncludesResponseUriForDirectPost() =
        runTest {
            val request =
                buildOid4vpAuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                ) {
                    nonce("nonce12345678")
                    responseMode(ResponseMode.DIRECT_POST)
                    responseUri("https://verifier.example.com/response")
                }

            val args =
                BuildAuthorizationRequestUriArgs(
                    request = request,
                    scheme = Oid4vpUriScheme.OPENID4VP,
                )

            val result = command.buildAuthorizationRequestUri(args)

            assertIs<Ok<*>>(result)
            val uri = result.value.value

            assertTrue(uri.contains("response_uri="))
            assertTrue(uri.contains("response_mode=direct_post"))
        }

    // ============================================================================
    // Request URI Mode Tests (PAR)
    // ============================================================================

    @Test
    fun testBuildUriWithRequestUriModePar() =
        runTest {
            val request =
                buildOid4vpAuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                ) {
                    nonce("nonce12345678")
                }

            val args =
                BuildAuthorizationRequestUriArgs(
                    request = request,
                    scheme = Oid4vpUriScheme.OPENID4VP,
                    useRequestUri = true,
                    requestUri = "https://verifier.example.com/par/request123",
                )

            val result = command.buildAuthorizationRequestUri(args)

            assertIs<Ok<*>>(result)
            val uri = result.value.value

            // Should be minimal: scheme://?client_id=...&request_uri=...
            assertTrue(uri.startsWith("openid4vp://?"))
            assertTrue(uri.contains("client_id="))
            assertTrue(uri.contains("request_uri="))
            // Should NOT contain full parameters
            assertFalse(uri.contains("nonce="))
            assertFalse(uri.contains("dcql_query="))
        }

    @Test
    fun testRequestUriModeWithoutRequestUriFails() =
        runTest {
            val request =
                buildOid4vpAuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                ) {
                    nonce("nonce12345678")
                }

            val args =
                BuildAuthorizationRequestUriArgs(
                    request = request,
                    scheme = Oid4vpUriScheme.OPENID4VP,
                    useRequestUri = true,
                    requestUri = null, // Missing!
                )

            val result = command.buildAuthorizationRequestUri(args)

            assertIs<Err<*>>(result)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("requestUri"),
            )
        }

    // ============================================================================
    // URL Encoding Tests
    // ============================================================================

    @Test
    fun testSpecialCharactersAreUrlEncoded() =
        runTest {
            val request =
                buildOid4vpAuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback?param=value",
                ) {
                    nonce("nonce12345678")
                    state("state with spaces & special=chars")
                }

            val args =
                BuildAuthorizationRequestUriArgs(
                    request = request,
                    scheme = Oid4vpUriScheme.OPENID4VP,
                )

            val result = command.buildAuthorizationRequestUri(args)

            assertIs<Ok<*>>(result)
            val uri = result.value.value

            // Special characters should be encoded
            assertTrue(uri.contains("%3A")) // :
            assertTrue(uri.contains("%2F")) // /
            assertTrue(uri.contains("%26") || uri.contains("%20")) // & or space
        }

    @Test
    fun testClientIdSchemeIsIncluded() =
        runTest {
            val request =
                buildOid4vpAuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                ) {
                    nonce("nonce12345678")
                    clientIdScheme("redirect_uri")
                }

            val args =
                BuildAuthorizationRequestUriArgs(
                    request = request,
                    scheme = Oid4vpUriScheme.OPENID4VP,
                )

            val result = command.buildAuthorizationRequestUri(args)

            assertIs<Ok<*>>(result)
            val uri = result.value.value

            assertTrue(uri.contains("client_id_scheme="))
        }

    private fun createTestCommand(): BuildAuthorizationRequestUriCommandImpl =
        BuildAuthorizationRequestUriCommandImpl(
            execution = testContext.execution,
        )

    companion object {
        private fun assertFalse(condition: Boolean) {
            assertEquals(false, condition)
        }
    }
}
