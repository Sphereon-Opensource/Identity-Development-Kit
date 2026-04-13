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

import com.sphereon.core.api.Ok
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.VpToken
import com.sphereon.openid.oid4vp.common.vpTokenOf
import com.sphereon.openid.oid4vp.dcql.DcqlClaimQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialSetOption
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialSetQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.verifier.ParsedAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.impl.testutil.Oid4vpVerifierTestContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for ValidateAuthorizationResponseCommandImpl
 */
class ValidateAuthorizationResponseCommandImplTest {
    private val testContext = Oid4vpVerifierTestContext("validate-auth-response-test", this)
    private val command = createTestCommand()

    @Test
    fun `test validate response with matching SD-JWT credential`() =
        runTest {
            // Given: DCQL query requesting SD-JWT credential
            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(
                                id = "identity_credential",
                                format = "dc+sd-jwt",
                                claims =
                                    listOf(
                                        DcqlClaimQuery(path = listOf("first_name")),
                                        DcqlClaimQuery(path = listOf("last_name")),
                                    ),
                            ),
                        ),
                )

            // And: Parsed response with SD-JWT in DCQL format
            val sdJwt = "eyJhbGciOiJFUzI1NiJ9.payload.signature~WyJhYmMxMjMiLCJmaXJzdF9uYW1lIiwiSm9obiJd~eyJhbGciOiJFUzI1NiJ9.kb.sig"
            val parsedResponse =
                ParsedAuthorizationResponse(
                    vpToken = vpTokenOf("identity_credential", sdJwt),
                    state = "state123",
                    rawVpToken = """{"identity_credential":"$sdJwt"}""",
                )

            val originalRequest =
                AuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                    state = "state123",
                )

            val args =
                ValidateAuthorizationResponseArgs(
                    parsedResponse = parsedResponse,
                    originalRequest = originalRequest,
                    dcqlQuery = dcqlQuery,
                    expectedNonce = "nonce123",
                )

            // When: Validating the response
            val result = command.validateAuthorizationResponse(args)

            // Then: Should validate successfully
            assertIs<Ok<*>>(result)
            val validation = result.value

            assertTrue(validation.valid)
            assertEquals(1, validation.matchedCredentials.size)
            assertEquals("identity_credential", validation.matchedCredentials[0].credentialQueryId)
            assertEquals("dc+sd-jwt", validation.matchedCredentials[0].format)
            assertTrue(validation.errors.isEmpty())
        }

    @Test
    fun `test validate response with matching mDoc credential`() =
        runTest {
            // Given: DCQL query requesting mDoc credential
            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(
                                id = "mdl_credential",
                                format = "mso_mdoc",
                            ),
                        ),
                )

            // And: Parsed response with mDoc (base64 CBOR - not containing dots or tildes)
            val mdocPresentation = "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBleBhvcmcuaXNvLjE4MDEzLjUuMS5tRExqaXNzdWVyU2lnbmVk"
            val parsedResponse =
                ParsedAuthorizationResponse(
                    vpToken = vpTokenOf("mdl_credential", mdocPresentation),
                    state = "state456",
                    rawVpToken = """{"mdl_credential":"$mdocPresentation"}""",
                )

            val originalRequest =
                AuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                    state = "state456",
                )

            val args =
                ValidateAuthorizationResponseArgs(
                    parsedResponse = parsedResponse,
                    originalRequest = originalRequest,
                    dcqlQuery = dcqlQuery,
                    expectedNonce = "nonce456",
                )

            // When: Validating the response
            val result = command.validateAuthorizationResponse(args)

            // Then: Should validate successfully
            assertIs<Ok<*>>(result)
            val validation = result.value

            assertTrue(validation.valid)
            assertEquals(1, validation.matchedCredentials.size)
            assertEquals("mdl_credential", validation.matchedCredentials[0].credentialQueryId)
            assertEquals("mso_mdoc", validation.matchedCredentials[0].format)
        }

    @Test
    fun `test validate response with state mismatch`() =
        runTest {
            val dcqlQuery =
                DcqlQuery(
                    credentials = listOf(DcqlCredentialQuery(id = "test")),
                )

            val sdjwt = "eyJhbGciOiJFUzI1NiJ9.payload.signature~disclosure~kb"
            val parsedResponse =
                ParsedAuthorizationResponse(
                    vpToken = vpTokenOf("test", sdjwt),
                    state = "wrong_state",
                    rawVpToken = """{"test":"$sdjwt"}""",
                )

            val originalRequest =
                AuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                    state = "expected_state",
                )

            val args =
                ValidateAuthorizationResponseArgs(
                    parsedResponse = parsedResponse,
                    originalRequest = originalRequest,
                    dcqlQuery = dcqlQuery,
                    expectedNonce = "nonce123",
                )

            val result = command.validateAuthorizationResponse(args)

            assertIs<Ok<*>>(result)
            val validation = result.value

            assertFalse(validation.valid)
            assertTrue(validation.errors.any { it.contains("State mismatch") })
        }

    @Test
    fun `test validate response with multiple credentials`() =
        runTest {
            // Given: DCQL query requesting multiple credentials
            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(id = "identity_cred", format = "dc+sd-jwt"),
                            DcqlCredentialQuery(id = "mdl_cred", format = "mso_mdoc"),
                        ),
                )

            // And: Response with multiple presentations (DCQL format)
            val sdJwt = "eyJhbGciOiJFUzI1NiJ9.payload.signature~disclosure~kb"
            val mdoc = "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBl"
            val parsedResponse =
                ParsedAuthorizationResponse(
                    vpToken =
                        VpToken(
                            mapOf(
                                "identity_cred" to listOf(sdJwt),
                                "mdl_cred" to listOf(mdoc),
                            ),
                        ),
                    state = "state789",
                    rawVpToken = """{"identity_cred":"$sdJwt","mdl_cred":"$mdoc"}""",
                )

            val originalRequest =
                AuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                    state = "state789",
                )

            val args =
                ValidateAuthorizationResponseArgs(
                    parsedResponse = parsedResponse,
                    originalRequest = originalRequest,
                    dcqlQuery = dcqlQuery,
                    expectedNonce = "nonce789",
                )

            val result = command.validateAuthorizationResponse(args)

            assertIs<Ok<*>>(result)
            val validation = result.value

            assertTrue(validation.valid)
            assertEquals(2, validation.matchedCredentials.size)
        }

    @Test
    fun `test validate response with credential_sets OR logic`() =
        runTest {
            // Given: DCQL query with credential_sets (OR logic)
            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(id = "eu_pid", format = "dc+sd-jwt"),
                            DcqlCredentialQuery(id = "mdl", format = "mso_mdoc"),
                        ),
                    credential_sets =
                        listOf(
                            DcqlCredentialSetQuery(
                                required = true,
                                options =
                                    listOf(
                                        DcqlCredentialSetOption(credential_ids = listOf("eu_pid")),
                                        DcqlCredentialSetOption(credential_ids = listOf("mdl")),
                                    ),
                            ),
                        ),
                )

            // And: Response with only one option satisfied (mdl)
            val mdoc = "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBl"
            val parsedResponse =
                ParsedAuthorizationResponse(
                    vpToken = vpTokenOf("mdl", mdoc),
                    state = "state_or",
                    rawVpToken = """{"mdl":"$mdoc"}""",
                )

            val originalRequest =
                AuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                    state = "state_or",
                )

            val args =
                ValidateAuthorizationResponseArgs(
                    parsedResponse = parsedResponse,
                    originalRequest = originalRequest,
                    dcqlQuery = dcqlQuery,
                    expectedNonce = "nonce_or",
                )

            val result = command.validateAuthorizationResponse(args)

            assertIs<Ok<*>>(result)
            val validation = result.value

            // Should be valid because one option is satisfied
            assertTrue(validation.valid)
            assertEquals(1, validation.matchedCredentials.size)
            assertEquals("mdl", validation.matchedCredentials[0].credentialQueryId)
        }

    @Test
    fun `test validate response with missing required credential`() =
        runTest {
            // Given: DCQL query requiring specific credential
            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(id = "required_cred", format = "dc+sd-jwt"),
                        ),
                )

            // And: Response with mismatched format (no SD-JWT, just regular JWT)
            val jwt = "eyJhbGciOiJFUzI1NiJ9.payload.signature"
            val parsedResponse =
                ParsedAuthorizationResponse(
                    vpToken = vpTokenOf("required_cred", jwt),
                    state = "state_missing",
                    rawVpToken = """{"required_cred":"$jwt"}""",
                )

            val originalRequest =
                AuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                    state = "state_missing",
                )

            val args =
                ValidateAuthorizationResponseArgs(
                    parsedResponse = parsedResponse,
                    originalRequest = originalRequest,
                    dcqlQuery = dcqlQuery,
                    expectedNonce = "nonce_missing",
                )

            val result = command.validateAuthorizationResponse(args)

            assertIs<Ok<*>>(result)
            val validation = result.value

            // Should fail because the response format doesn't match the required format
            assertFalse(validation.valid)
            assertTrue(validation.errors.any { it.contains("required_cred") || it.contains("not found") })
        }

    @Test
    fun `test validate response without state in original request`() =
        runTest {
            // Given: Original request without state
            val dcqlQuery =
                DcqlQuery(
                    credentials = listOf(DcqlCredentialQuery(id = "test")),
                )

            val sdjwt = "eyJhbGciOiJFUzI1NiJ9.payload.sig~disc~kb"
            val parsedResponse =
                ParsedAuthorizationResponse(
                    vpToken = vpTokenOf("test", sdjwt),
                    state = null,
                    rawVpToken = """{"test":"$sdjwt"}""",
                )

            val originalRequest =
                AuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                    state = null, // No state in original request
                )

            val args =
                ValidateAuthorizationResponseArgs(
                    parsedResponse = parsedResponse,
                    originalRequest = originalRequest,
                    dcqlQuery = dcqlQuery,
                    expectedNonce = "nonce_no_state",
                )

            val result = command.validateAuthorizationResponse(args)

            assertIs<Ok<*>>(result)
            val validation = result.value

            // Should pass - state validation skipped when original has no state
            assertTrue(validation.valid)
        }

    @Test
    fun `test validate response with format any match`() =
        runTest {
            // Given: DCQL query without specific format (any format accepted)
            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(
                                id = "any_format_cred",
                                format = null, // Accept any format
                            ),
                        ),
                )

            // And: Response with JWT VP
            val jwtVp = "eyJhbGciOiJFUzI1NiJ9.vp_payload.signature"
            val parsedResponse =
                ParsedAuthorizationResponse(
                    vpToken = vpTokenOf("any_format_cred", jwtVp),
                    state = "state_any",
                    rawVpToken = """{"any_format_cred":"$jwtVp"}""",
                )

            val originalRequest =
                AuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                    state = "state_any",
                )

            val args =
                ValidateAuthorizationResponseArgs(
                    parsedResponse = parsedResponse,
                    originalRequest = originalRequest,
                    dcqlQuery = dcqlQuery,
                    expectedNonce = "nonce_any",
                )

            val result = command.validateAuthorizationResponse(args)

            assertIs<Ok<*>>(result)
            val validation = result.value

            assertTrue(validation.valid)
            assertEquals(1, validation.matchedCredentials.size)
            assertEquals("any_format_cred", validation.matchedCredentials[0].credentialQueryId)
        }

    private fun createTestCommand(): ValidateAuthorizationResponseCommandImpl =
        ValidateAuthorizationResponseCommandImpl(
            execution = testContext.execution,
            authorizationSessionStore = TestAuthorizationSessionStore(),
        )
}
