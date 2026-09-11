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
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.jarm.JarmVerificationResult
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseArgs
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseCommand
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.impl.testutil.Oid4vpVerifierTestContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for ParseAuthorizationResponseCommandImpl
 *
 * Tests VP token parsing in DCQL format (JSON object with credential query IDs as keys).
 */
class ParseAuthorizationResponseCommandImplTest {
    private val testContext = Oid4vpVerifierTestContext("parse-auth-response-test", this)
    private val command = createTestCommand()

    @Test
    fun `test parse single query vp_token response`() =
        runTest {
            // Given: Response with VP token in DCQL object format
            val dcqlVpToken = """{"driver_license_query":["eyJhbGciOiJFUzI1NiIsInR5cCI6InZjK3NkLWp3dCJ9.payload.signature"]}"""
            val args =
                ParseAuthorizationResponseArgs(
                    responseParams =
                        mapOf(
                            "vp_token" to dcqlVpToken,
                            "state" to "state123",
                        ),
                )

            // When: Parsing the response
            val result = command.parseAuthorizationResponse(args)

            // Then: Should parse successfully
            assertIs<Ok<*>>(result)
            val parsed = result.value

            assertEquals(1, parsed.vpToken.presentations.size)
            assertEquals(
                "eyJhbGciOiJFUzI1NiIsInR5cCI6InZjK3NkLWp3dCJ9.payload.signature",
                parsed.vpToken.getSinglePresentation("driver_license_query"),
            )
            assertEquals("state123", parsed.state)
        }

    @Test
    fun `test parse multiple query vp_token response`() =
        runTest {
            // Given: Response with multiple credential queries
            val dcqlVpToken = """{
            "driver_license_query":["eyJhbGciOiJFUzI1NiJ9.payload1.sig1"],
            "employment_query":["eyJhbGciOiJFUzI1NiJ9.payload2.sig2"]
        }"""
            val args =
                ParseAuthorizationResponseArgs(
                    responseParams =
                        mapOf(
                            "vp_token" to dcqlVpToken,
                            "state" to "state456",
                        ),
                )

            // When: Parsing the response
            val result = command.parseAuthorizationResponse(args)

            // Then: Should parse as DCQL format with multiple queries
            assertIs<Ok<*>>(result)
            val parsed = result.value

            assertEquals(2, parsed.vpToken.presentations.size)
            assertEquals(2, parsed.vpToken.presentationCount)
            assertEquals(
                "eyJhbGciOiJFUzI1NiJ9.payload1.sig1",
                parsed.vpToken.getSinglePresentation("driver_license_query"),
            )
            assertEquals(
                "eyJhbGciOiJFUzI1NiJ9.payload2.sig2",
                parsed.vpToken.getSinglePresentation("employment_query"),
            )
            assertEquals("state456", parsed.state)
        }

    @Test
    fun `test parse vp_token with multiple presentations per query`() =
        runTest {
            // Given: Response with array of presentations for a query
            val dcqlVpToken = """{
            "employment_query":["eyJhbGc1.employer1.sig1","eyJhbGc2.employer2.sig2"]
        }"""
            val args =
                ParseAuthorizationResponseArgs(
                    responseParams =
                        mapOf(
                            "vp_token" to dcqlVpToken,
                        ),
                )

            val result = command.parseAuthorizationResponse(args)

            assertIs<Ok<*>>(result)
            val parsed = result.value

            assertEquals(1, parsed.vpToken.presentations.size)
            assertEquals(2, parsed.vpToken.presentationCount)
            assertEquals(2, parsed.vpToken.getPresentation("employment_query")?.size)
        }

    @Test
    fun `test parse response without state`() =
        runTest {
            val dcqlVpToken = """{"query1":["eyJhbGciOiJFUzI1NiJ9.payload.signature"]}"""
            val args =
                ParseAuthorizationResponseArgs(
                    responseParams =
                        mapOf(
                            "vp_token" to dcqlVpToken,
                        ),
                )

            val result = command.parseAuthorizationResponse(args)

            assertIs<Ok<*>>(result)
            val parsed = result.value
            assertEquals(null, parsed.state)
        }

    @Test
    fun `test parse error response`() =
        runTest {
            // Given: Error response from wallet
            val args =
                ParseAuthorizationResponseArgs(
                    responseParams =
                        mapOf(
                            "error" to "invalid_request",
                            "error_description" to "Missing required parameter",
                        ),
                )

            // When: Parsing the response
            val result = command.parseAuthorizationResponse(args)

            // Then: Should return error
            assertIs<Err<*>>(result)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("invalid_request"),
            )
        }

    @Test
    fun `test parse response with missing vp_token`() =
        runTest {
            val args =
                ParseAuthorizationResponseArgs(
                    responseParams =
                        mapOf(
                            "state" to "state123",
                            // Missing vp_token
                        ),
                )

            val result = command.parseAuthorizationResponse(args)

            assertIs<Err<*>>(result)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("vp_token"),
            )
        }

    @Test
    fun `test state validation with original request`() =
        runTest {
            // Given: Original request with specific state
            val originalRequest =
                AuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                    state = "expected_state",
                )

            // And: Response with mismatched state
            val dcqlVpToken = """{"query1":["eyJhbGciOiJFUzI1NiJ9.payload.signature"]}"""
            val args =
                ParseAuthorizationResponseArgs(
                    responseParams =
                        mapOf(
                            "vp_token" to dcqlVpToken,
                            "state" to "wrong_state",
                        ),
                    originalRequest = originalRequest,
                )

            // When: Parsing the response
            val result = command.parseAuthorizationResponse(args)

            // Then: Should fail with state mismatch
            assertIs<Err<*>>(result)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("State mismatch"),
            )
        }

    @Test
    fun `test state validation with matching state`() =
        runTest {
            val originalRequest =
                AuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                    state = "matching_state",
                )

            val dcqlVpToken = """{"query1":["eyJhbGciOiJFUzI1NiJ9.payload.signature"]}"""
            val args =
                ParseAuthorizationResponseArgs(
                    responseParams =
                        mapOf(
                            "vp_token" to dcqlVpToken,
                            "state" to "matching_state",
                        ),
                    originalRequest = originalRequest,
                )

            val result = command.parseAuthorizationResponse(args)

            assertIs<Ok<*>>(result)
            assertEquals("matching_state", result.value.state)
        }

    @Test
    fun `test parse SD-JWT with disclosures`() =
        runTest {
            // SD-JWT format: issuer-jwt~disclosure1~disclosure2~kb-jwt
            val sdJwt = "eyJhbGciOiJFUzI1NiJ9.payload.signature~WyJhYmMxMjMiLCJmaXJzdF9uYW1lIiwiSm9obiJd~WyJkZWY0NTYiLCJsYXN0X25hbWUiLCJEb2UiXQ~eyJhbGciOiJFUzI1NiJ9.kb.signature"
            val dcqlVpToken = """{"identity_query":["$sdJwt"]}"""

            val args =
                ParseAuthorizationResponseArgs(
                    responseParams =
                        mapOf(
                            "vp_token" to dcqlVpToken,
                        ),
                )

            val result = command.parseAuthorizationResponse(args)

            assertIs<Ok<*>>(result)
            val parsed = result.value
            assertEquals(1, parsed.vpToken.presentations.size)
            assertEquals(sdJwt, parsed.vpToken.getSinglePresentation("identity_query"))
        }

    @Test
    fun `test reject non-object vp_token`() =
        runTest {
            // Plain string is not valid DCQL format
            val args =
                ParseAuthorizationResponseArgs(
                    responseParams =
                        mapOf(
                            "vp_token" to "eyJhbGciOiJFUzI1NiJ9.payload.signature",
                        ),
                )

            val result = command.parseAuthorizationResponse(args)

            assertIs<Err<*>>(result)
        }

    @Test
    fun `test reject scalar presentation value in DCQL object`() =
        runTest {
            val result =
                command.parseAuthorizationResponse(
                    ParseAuthorizationResponseArgs(
                        responseParams = mapOf("vp_token" to """{"query1":"presentation"}"""),
                    ),
                )

            assertIs<Err<*>>(result)
        }

    @Test
    fun `test parse DCQL vp_token with string and JSON-object presentation values`() =
        runTest {
            // OID4VP §8.1: a DCQL vp_token is a JSON object keyed by credential-query id whose
            // Every credential-query value is an array. Its Presentation elements remain
            // Credential-Format dependent: strings for compact formats and JSON objects for
            // W3C Data Integrity formats.
            val compactSdJwt = "eyJhbGciOiJFUzI1NiJ9.payload.sig~WyJhYmMiLCJnaXZlbl9uYW1lIiwiQWxpY2UiXQ~"
            val dcqlVpToken =
                """{
                    "compact_query": ["$compactSdJwt"],
                    "ldp_query": [{ "@context": ["https://www.w3.org/ns/credentials/v2"], "type": "VerifiablePresentation", "proof": { "type": "DataIntegrityProof" } }]
                }"""

            val args =
                ParseAuthorizationResponseArgs(
                    responseParams =
                        mapOf(
                            "vp_token" to dcqlVpToken,
                            "state" to "mixed-state",
                        ),
                )

            val result = command.parseAuthorizationResponse(args)

            // Parsing succeeds for both presentation shapes (no crash on the JSON object).
            assertIs<Ok<*>>(result)
            val parsed = result.value
            assertEquals(2, parsed.vpToken.presentations.size)
            assertEquals("mixed-state", parsed.state)

            // The compact (string) presentation round-trips as its raw content.
            assertEquals(compactSdJwt, parsed.vpToken.getSinglePresentation("compact_query"))

            // The ldp_vc presentation is preserved as a JSON object element (not a string),
            // accessible via the shape-preserving accessor.
            val ldpElement = parsed.vpToken.getSinglePresentationElement("ldp_query")
            assertIs<kotlinx.serialization.json.JsonObject>(ldpElement)
            assertEquals(
                "VerifiablePresentation",
                ldpElement["type"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content },
            )
        }

    private fun createTestCommand(): ParseAuthorizationResponseCommandImpl {
        val mockJarmCommand = MockVerifyJarmResponseCommand()
        return ParseAuthorizationResponseCommandImpl(
            execution = testContext.execution,
            verifyJarmCommand = mockJarmCommand,
        )
    }

    /**
     * Mock JARM verification command for testing.
     * Always returns an error since regular responses don't use JARM.
     */
    private class MockVerifyJarmResponseCommand : VerifyJarmResponseCommand {
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<VerifyJarmResponseArgs> = typeToken<VerifyJarmResponseArgs>()
        override val outputTypeToken: TypeToken<JarmVerificationResult> = typeToken<JarmVerificationResult>()

        override suspend fun execute(args: VerifyJarmResponseArgs): IdkResult<JarmVerificationResult, IdkError> {
            // Return error for mock - JARM not used in basic tests
            return Err(IdkError.fromString("Mock JARM verification not implemented"))
        }
    }
}
