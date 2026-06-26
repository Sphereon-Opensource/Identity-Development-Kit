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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.VpToken
import com.sphereon.openid.oid4vp.common.vpTokenOf
import com.sphereon.openid.oid4vp.dcql.DcqlClaimQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialSetOption
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialSetQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.verifier.CredentialTrustValidation
import com.sphereon.openid.oid4vp.verifier.CredentialTrustValidationMode
import com.sphereon.openid.oid4vp.verifier.HolderBindingResult
import com.sphereon.openid.oid4vp.verifier.Oid4vpCredentialTrustValidationArgs
import com.sphereon.openid.oid4vp.verifier.Oid4vpCredentialTrustValidator
import com.sphereon.openid.oid4vp.verifier.ParsedAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.VerifyHolderBindingArgs
import com.sphereon.openid.oid4vp.verifier.VerifyHolderBindingCommand
import com.sphereon.openid.oid4vp.verifier.impl.testutil.Oid4vpVerifierTestContext
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
    fun `credential trust validation receives stored dcql query id separately from credential query id`() =
        runTest {
            val trustValidator = CapturingTrustValidator()
            val command = createTestCommand(credentialTrustValidators = setOf(trustValidator))
            val dcqlQuery =
                DcqlQuery(
                    credentials = listOf(DcqlCredentialQuery(id = "identity_credential", format = "dc+sd-jwt")),
                )
            val sdJwt = "eyJhbGciOiJFUzI1NiJ9.payload.signature~WyJhYmMxMjMiLCJmaXJzdF9uYW1lIiwiSm9obiJd~eyJhbGciOiJFUzI1NiJ9.kb.sig"
            val result =
                command.validateAuthorizationResponse(
                    ValidateAuthorizationResponseArgs(
                        parsedResponse =
                            ParsedAuthorizationResponse(
                                vpToken = vpTokenOf("identity_credential", sdJwt),
                                state = "state123",
                                rawVpToken = """{"identity_credential":"$sdJwt"}""",
                            ),
                        originalRequest =
                            AuthorizationRequest(
                                clientId = "https://verifier.example.com",
                                redirectUri = "https://verifier.example.com/callback",
                                state = "state123",
                            ),
                        dcqlQuery = dcqlQuery,
                        expectedNonce = "nonce123",
                        verifierId = "verifier-a",
                        dcqlQueryId = "employee-vp",
                    ),
                )

            assertIs<Ok<*>>(result)
            assertTrue(result.value.valid)
            val validationArgs = assertNotNull(trustValidator.lastArgs)
            assertEquals("verifier-a", validationArgs.verifierId)
            assertEquals("employee-vp", validationArgs.dcqlQueryId)
            assertEquals("identity_credential", validationArgs.credentialQueryId)
        }

    @Test
    fun `test revoked credential is rejected by default status policy`() =
        runTest {
            val dcqlQuery =
                DcqlQuery(
                    credentials = listOf(DcqlCredentialQuery(id = "identity_credential", format = "dc+sd-jwt")),
                )
            val sdJwt = "eyJhbGciOiJFUzI1NiJ9.payload.signature~WyJhYmMxMjMiLCJmaXJzdF9uYW1lIiwiSm9obiJd~eyJhbGciOiJFUzI1NiJ9.kb.sig"
            val args =
                ValidateAuthorizationResponseArgs(
                    parsedResponse =
                        ParsedAuthorizationResponse(
                            vpToken = vpTokenOf("identity_credential", sdJwt),
                            state = "state123",
                            rawVpToken = """{"identity_credential":"$sdJwt"}""",
                        ),
                    originalRequest =
                        AuthorizationRequest(
                            clientId = "https://verifier.example.com",
                            redirectUri = "https://verifier.example.com/callback",
                            state = "state123",
                        ),
                    dcqlQuery = dcqlQuery,
                    expectedNonce = "nonce123",
                )

            // A revoked status with no per-query policy uses the strict default → reject + discard.
            val command = createTestCommand(setOf(FixedStatusVerifier(value = 1)))
            val result = command.validateAuthorizationResponse(args)

            assertIs<Ok<*>>(result)
            assertFalse(result.value.valid)
            // Short, user-facing message naming the credential + state; the misleading "not found" for a
            // submitted-but-discarded credential is suppressed, and the technical status-list URI/index
            // never leaks into the returned error (it goes to logs + an event instead).
            assertTrue(result.value.errors.any { it == "identity_credential is revoked" })
            assertTrue(result.value.errors.none { it.contains("not found") })
            assertTrue(result.value.errors.none { it.contains("status list") || it.contains("https://") })

            // An active status passes the same default policy.
            val activeResult = createTestCommand(setOf(FixedStatusVerifier(value = 0))).validateAuthorizationResponse(args)
            assertIs<Ok<*>>(activeResult)
            assertTrue(activeResult.value.valid)
            assertEquals(1, activeResult.value.matchedCredentials.size)
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
                        VpToken.fromStrings(
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

    @Test
    fun testValidateResponseRejectsVcLdJsonJwtPresentationContainingVocab() =
        runTest {
            // VCDM 2.0 JWT body whose @context contains an embedded @vocab. The
            // validator catches this without any remote resolution: @vocab in any
            // embedded context object is forbidden by UNTP 0.7.0 VCP. The JWT is
            // unsigned for test purposes; AlwaysValidHolderBindingCommand stands
            // in for cryptographic verification.
            val jwt =
                unsignedVcLdJwt(
                    atVocab = "https://example.com/poison/",
                    primaryType = "DigitalProductPassport",
                )

            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(id = "dpp", format = "vc+ld+json+jwt"),
                        ),
                )
            val parsedResponse =
                ParsedAuthorizationResponse(
                    vpToken = vpTokenOf("dpp", jwt),
                    state = "state123",
                    rawVpToken = """{"dpp":"$jwt"}""",
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

            val result = command.validateAuthorizationResponse(args)
            assertIs<Ok<*>>(result)
            val validation = result.value

            assertFalse(validation.valid, "presentation with @vocab must fail validation")
            assertTrue(
                validation.errors.any { it.contains("JSONLD_INVALID_VOCAB_MAPPING") },
                "expected JSONLD_INVALID_VOCAB_MAPPING in errors: ${validation.errors}",
            )
        }

    @Test
    fun `test validate response accepts non-vc_ld_json_jwt presentation unchanged`() =
        runTest {
            // SD-JWT presentation: the new VCDM 2.0 validator path must skip it
            // entirely (referencesVcdm2Context returns false because there is no
            // @context at the JWT body root) and the SD-JWT path goes through as
            // before. This is the regression-guard for the older 8 tests.
            val sdJwt = "eyJhbGciOiJFUzI1NiJ9.payload.signature~WyJhYmMxMjMiLCJmaXJzdF9uYW1lIiwiSm9obiJd~eyJhbGciOiJFUzI1NiJ9.kb.sig"
            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(id = "id", format = "dc+sd-jwt"),
                        ),
                )
            val parsedResponse =
                ParsedAuthorizationResponse(
                    vpToken = vpTokenOf("id", sdJwt),
                    state = "s",
                    rawVpToken = """{"id":"$sdJwt"}""",
                )
            val originalRequest =
                AuthorizationRequest(
                    clientId = "https://v.example.com",
                    redirectUri = "https://v.example.com/cb",
                    state = "s",
                )
            val args =
                ValidateAuthorizationResponseArgs(
                    parsedResponse = parsedResponse,
                    originalRequest = originalRequest,
                    dcqlQuery = dcqlQuery,
                    expectedNonce = "n",
                )
            val result = command.validateAuthorizationResponse(args)
            assertIs<Ok<*>>(result)
            assertTrue(result.value.valid)
            assertEquals(1, result.value.matchedCredentials.size)
        }

    /**
     * Build an unsigned JWT-shaped string with a VCDM 2.0 + JSON-LD body whose
     * @context contains the supplied @vocab keyword. Suitable for testing the
     * validator path; AlwaysValidHolderBindingCommand stubs out the signature
     * check.
     */
    private fun unsignedVcLdJwt(
        atVocab: String,
        primaryType: String
    ): String {
        val payload =
            """
            {
                "@context": [
                  "https://www.w3.org/ns/credentials/v2",
                  {"@vocab": "$atVocab"}
                ],
                "type": ["VerifiableCredential", "$primaryType"],
                "issuer": "https://issuer.example.com",
                "validFrom": "2026-01-01T00:00:00Z",
                "credentialSubject": {"id": "did:example:holder"}
            }
            """.trimIndent()
        val header = """{"alg":"none","typ":"vc+ld+json+jwt"}"""
        return "${header.encodeToByteArray().encodeToBase64Url()}.${payload.encodeToByteArray().encodeToBase64Url()}.fakesig"
    }

    private fun createTestCommand(
        credentialStatusVerifiers: Set<com.sphereon.statuslist.spi.CredentialStatusVerifier> = emptySet(),
        credentialTrustValidators: Set<Oid4vpCredentialTrustValidator> = emptySet(),
        authorizationSessionStore: AuthorizationSessionStore = TestAuthorizationSessionStore(),
    ): ValidateAuthorizationResponseCommandImpl {
        // Validators wired through the built-in W3C/UNTP @context bundle so
        // VCDM 2.0 references resolve from the JAR (no network). For
        // non-VCDM-2.0 presentations the validator path is skipped entirely
        // (referencesVcdm2Context returns false).
        val builtInLoader =
            com.sphereon.jsonld.loader.BuiltInContextLinkedDataDocumentLoader(
                com.sphereon.jsonld.loader
                    .DefaultBuiltInContextRegistry(),
            )
        return ValidateAuthorizationResponseCommandImpl(
            execution = testContext.execution,
            authorizationSessionStore = authorizationSessionStore,
            verifyHolderBindingCommand = AlwaysValidHolderBindingCommand,
            jsonLdContextValidator =
                com.sphereon.jsonld.command
                    .JsonLdContextValidator(builtInLoader),
            jsonLdSchemaValidator =
                com.sphereon.jsonld.command.JsonLdSchemaValidator(
                    com.sphereon.jsonld.command
                        .MapBackedJsonLdSchemaRegistry(emptyMap()),
                ),
            deviceResponseCborCodec =
                com.sphereon.mdoc.data.device
                    .DeviceResponseCborCodecImpl(),
            credentialStatusVerifiers = credentialStatusVerifiers,
            credentialTrustValidators = credentialTrustValidators,
        )
    }
}

private class CapturingTrustValidator : Oid4vpCredentialTrustValidator {
    var lastArgs: Oid4vpCredentialTrustValidationArgs? = null
        private set

    override suspend fun validate(args: Oid4vpCredentialTrustValidationArgs): IdkResult<CredentialTrustValidation, IdkError> {
        lastArgs = args
        return Ok(
            CredentialTrustValidation(
                enabled = false,
                mode = CredentialTrustValidationMode.DISABLED,
                details = "captured",
            ),
        )
    }
}

/**
 * Test verifier that always reports one status reference and resolves it to a fixed [value],
 * independent of the credential claims — lets the integration tests drive the verifier's status
 * decision without hand-crafting a parseable signed status-list token.
 */
private class FixedStatusVerifier(
    private val value: Int,
) : com.sphereon.statuslist.spi.CredentialStatusVerifier {
    override val mechanism: String = "test"

    override fun references(credentialClaims: kotlinx.serialization.json.JsonObject) =
        listOf(com.sphereon.statuslist.CredentialStatusReference(mechanism = "test", uri = "https://issuer.example.com/sl", index = 0))

    override suspend fun resolve(reference: com.sphereon.statuslist.CredentialStatusReference) =
        Ok(com.sphereon.statuslist.ResolvedStatus(value = value, valid = value == 0, statusListUri = reference.uri))
}

/**
 * Test stub that always returns a verified holder binding. The unit tests in this file
 * exercise the validation/matching pipeline with synthesized vp_token strings; cryptographic
 * holder-binding checks are covered by [VerifyHolderBindingCommandImplTest].
 */
private object AlwaysValidHolderBindingCommand : VerifyHolderBindingCommand {
    override val commandId: String = VerifyHolderBindingCommand.COMMAND_ID
    override val inputTypeToken: TypeToken<VerifyHolderBindingArgs> = typeToken<VerifyHolderBindingArgs>()
    override val outputTypeToken: TypeToken<HolderBindingResult> = typeToken<HolderBindingResult>()
    override val isEnabled: Boolean = true

    override suspend fun supports(args: Any): Boolean = args is VerifyHolderBindingArgs

    override suspend fun execute(args: VerifyHolderBindingArgs): IdkResult<HolderBindingResult, IdkError> =
        Ok(
            HolderBindingResult(
                verified = true,
                bindingMethod = "stub",
                signatureValid = true,
                nonceValid = true,
                audienceValid = true,
            ),
        )
}
