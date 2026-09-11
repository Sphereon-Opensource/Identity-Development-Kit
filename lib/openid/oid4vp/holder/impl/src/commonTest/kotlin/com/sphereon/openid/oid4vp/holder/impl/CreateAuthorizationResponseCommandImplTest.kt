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

package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.crypto.core.CoseCryptoServiceImpl
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.jose.jws.JwsJsonFlattened
import com.sphereon.crypto.jose.jws.JwsJsonGeneral
import com.sphereon.crypto.jose.jws.JwsValidationResult
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.PreparedJwsObject
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.dataintegrity.command.AddProofInput
import com.sphereon.crypto.dataintegrity.command.AddProofOutput
import com.sphereon.crypto.dataintegrity.command.AddProofServiceCommand
import com.sphereon.mdoc.MdocSignServiceImpl
import com.sphereon.mdoc.SessionTranscriptCborCodecImpl
import com.sphereon.mdoc.data.device.DeviceResponseCborCodecImpl
import com.sphereon.mdoc.data.device.IssuerSignedCborCodecImpl
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodecImpl
import com.sphereon.mdoc.oid4vp.MdocOid4vpServiceImpl
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ClientMetadata
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.PresentationFormat
import com.sphereon.openid.oid4vp.common.VpFormatInfo
import com.sphereon.openid.oid4vp.common.ParsedTransactionDataEntry
import com.sphereon.openid.oid4vp.common.TransactionDataEntry
import com.sphereon.openid.oid4vp.common.VpToken
import com.sphereon.openid.oid4vp.common.vpToken
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningIdentifier
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.PreparedPresentation
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.openid.oid4vp.holder.VerifierInfo
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for CreateAuthorizationResponseCommandImpl.
 *
 * These tests verify the authorization response creation logic:
 * - Building VP tokens from selected credentials in DCQL format
 * - Creating proper authorization responses with query ID mapping
 * - Handling single vs multiple presentations per query
 * - Validating credential selection
 */
class CreateAuthorizationResponseCommandImplTest {
    /**
     * Create a minimal resolved request for testing
     */
    private fun createResolvedRequest(state: String? = "test-state"): ResolvedOid4vpRequest {
        val authRequest =
            AuthorizationRequest(
                clientId = "test-client",
                redirectUri = "https://example.com/callback",
                responseType = "vp_token",
                scope = null,
                state = state,
                nonce = "test-nonce",
                additionalParameters =
                    mapOf(
                        "response_mode" to JsonPrimitive("direct_post"),
                    ),
            )

        return ResolvedOid4vpRequest(
            request = authRequest,
            dcqlQuery = null,
            clientMetadata = ClientMetadata(
                vpFormatsSupported = mapOf(
                    "jwt_vc_json" to VpFormatInfo(algValuesSupported = listOf("ES256")),
                    "jwt_vc_json-ld" to VpFormatInfo(algValuesSupported = listOf("ES256")),
                ),
            ),
            verifierInfo =
                VerifierInfo(
                    clientId = "test-client",
                    clientIdScheme = ClientIdScheme.PRE_REGISTERED,
                    displayName = "Test Verifier",
                ),
        )
    }

    @Test
    fun `test create response with single credential`() =
        runTest {
            val command = createCommand()

            val resolvedRequest = createResolvedRequest()
            val selectedCredentials =
                listOf(
                    SelectedCredential(
                        credentialQueryId = "driver_license_query",
                        credentialId = "cred-1",
                        presentation = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIxMjM0In0.signature"),
                        credentialFormat = CredentialFormat.SD_JWT_VC,
                        disclosedClaims = mapOf("given_name" to "John"),
                    ),
                )

            val args = CreateAuthorizationResponseArgs(resolvedRequest, selectedCredentials)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val response = result.value

            // Verify response has vp_token in DCQL format
            val vpToken = response.vpToken
            assertNotNull(vpToken)
            assertEquals(1, vpToken.presentations.size)
            assertEquals(1, vpToken.presentationCount)
            assertEquals(
                "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIxMjM0In0.signature",
                vpToken.getSinglePresentation("driver_license_query"),
            )
            val wireVpToken = assertIs<JsonObject>(response.additionalParameters["vp_token"])
            assertEquals(1, assertIs<JsonArray>(wireVpToken["driver_license_query"]).size)

            // Verify state is preserved
            assertEquals("test-state", response.state)
        }

    @Test
    fun `test create response with multiple credentials for different queries`() =
        runTest {
            val command = createCommand()

            val resolvedRequest = createResolvedRequest()
            val selectedCredentials =
                listOf(
                    SelectedCredential(
                        credentialQueryId = "driver_license_query",
                        credentialId = "cred-1",
                        presentation = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIxMjM0In0.sig1"),
                        credentialFormat = CredentialFormat.SD_JWT_VC,
                        disclosedClaims = mapOf("given_name" to "John"),
                    ),
                    SelectedCredential(
                        credentialQueryId = "employment_query",
                        credentialId = "cred-2",
                        presentation = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiI1Njc4In0.sig2"),
                        credentialFormat = CredentialFormat.SD_JWT_VC,
                        disclosedClaims = mapOf("employer" to "Acme Inc"),
                    ),
                )

            val args = CreateAuthorizationResponseArgs(resolvedRequest, selectedCredentials)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val response = result.value

            // Verify response has vp_token with both query IDs
            val vpToken = response.vpToken
            assertNotNull(vpToken)
            assertEquals(2, vpToken.presentations.size)
            assertEquals(2, vpToken.presentationCount)
            assertEquals(setOf("driver_license_query", "employment_query"), vpToken.queryIds)
            assertEquals(
                "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIxMjM0In0.sig1",
                vpToken.getSinglePresentation("driver_license_query"),
            )
            assertEquals(
                "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiI1Njc4In0.sig2",
                vpToken.getSinglePresentation("employment_query"),
            )
        }

    @Test
    fun `test create response with multiple presentations for same query`() =
        runTest {
            val command = createCommand()

            val resolvedRequest = createResolvedRequest()
            val selectedCredentials =
                listOf(
                    SelectedCredential(
                        credentialQueryId = "employment_query",
                        credentialId = "cred-1",
                        presentation = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.employer1.sig1"),
                        credentialFormat = CredentialFormat.SD_JWT_VC,
                    ),
                    SelectedCredential(
                        credentialQueryId = "employment_query",
                        credentialId = "cred-2",
                        presentation = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.employer2.sig2"),
                        credentialFormat = CredentialFormat.SD_JWT_VC,
                    ),
                )

            val args = CreateAuthorizationResponseArgs(resolvedRequest, selectedCredentials)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val response = result.value

            // Verify vp_token has array for the query with multiple presentations
            val vpToken = response.vpToken
            assertNotNull(vpToken)
            assertEquals(1, vpToken.presentations.size)
            assertEquals(2, vpToken.presentationCount)
            assertEquals(2, vpToken.getPresentation("employment_query")?.size)
        }

    @Test
    fun `test create response with empty credentials list fails`() =
        runTest {
            val command = createCommand()

            val resolvedRequest = createResolvedRequest()
            val selectedCredentials = emptyList<SelectedCredential>()

            val args = CreateAuthorizationResponseArgs(resolvedRequest, selectedCredentials)
            val result = command.execute(args)

            assertIs<Err<*>>(result)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("at least one credential", ignoreCase = true),
            )
        }

    // The mso_mdoc branch (DeviceResponse construction + DeviceAuth signing) is covered end to end
    // by the jvmTest sibling CreateAuthorizationResponseCommandImplMdocTest, which issues a real
    // device-key-bound mdoc and decodes the produced DeviceResponse. A common test cannot assert a
    // real mdoc presentation without the JVM crypto/KMS stack, and the holder no longer passes the
    // stored IssuerSigned through verbatim (the old behaviour this test used to assert was a bug).

    @Test
    fun `test create response without state`() =
        runTest {
            val command = createCommand()

            val resolvedRequest = createResolvedRequest(state = null)
            val selectedCredentials =
                listOf(
                    SelectedCredential(
                        credentialQueryId = "identity_query",
                        credentialId = "cred-1",
                        presentation = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIxMjM0In0.signature"),
                        credentialFormat = CredentialFormat.SD_JWT_VC,
                    ),
                )

            val args = CreateAuthorizationResponseArgs(resolvedRequest, selectedCredentials)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val response = result.value

            // Verify response has vp_token but no state
            assertNotNull(response.vpToken)
            assertEquals(null, response.state)
        }

    @Test
    fun `test create response with SD-JWT presentation`() =
        runTest {
            val command = createCommand()

            val resolvedRequest = createResolvedRequest()

            // SD-JWT format: JWT~disclosure1~disclosure2~...~KB-JWT
            val sdJwtPresentation =
                "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIxMjM0In0.sig~" +
                    "WyJzYWx0MSIsICJnaXZlbl9uYW1lIiwgIkpvaG4iXQ~" +
                    "WyJzYWx0MiIsICJmYW1pbHlfbmFtZSIsICJEb2UiXQ~" +
                    "eyJhbGciOiJFUzI1NiJ9.eyJhdWQiOiJ2ZXJpZmllciJ9.kbjwt"

            val selectedCredentials =
                listOf(
                    SelectedCredential(
                        credentialQueryId = "identity_credential_query",
                        credentialId = "sdjwt-1",
                        presentation = JsonPrimitive(sdJwtPresentation),
                        credentialFormat = CredentialFormat.SD_JWT_VC,
                        disclosedClaims = mapOf("given_name" to "John", "family_name" to "Doe"),
                    ),
                )

            val args = CreateAuthorizationResponseArgs(resolvedRequest, selectedCredentials)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val response = result.value

            // Verify SD-JWT presentation is preserved as-is
            val vpToken = response.vpToken
            assertNotNull(vpToken)
            assertEquals(sdJwtPresentation, vpToken.getSinglePresentation("identity_credential_query"))
        }

    @Test
    fun dcqlExplicitlyDisablingHolderBindingCreatesSelectiveSdJwtWithoutKb() =
        runTest {
            val command = createCommand()
            val resolvedRequest =
                createResolvedRequest().copy(
                    dcqlQuery =
                        DcqlQuery(
                            credentials =
                                listOf(
                                    DcqlCredentialQuery(
                                        id = "identity_credential_query",
                                        format = "dc+sd-jwt",
                                        meta =
                                            JsonObject(
                                                mapOf(
                                                    "vct_values" to JsonArray(listOf(JsonPrimitive("https://credentials.example/identity"))),
                                                ),
                                            ),
                                        require_cryptographic_holder_binding = false,
                                    ),
                                ),
                        ),
                )
            val selectedCredential =
                SelectedCredential(
                    credentialQueryId = "identity_credential_query",
                    credentialId = "sdjwt-1",
                    presentation =
                        JsonPrimitive(
                            "eyJhbGciOiJFUzI1NiJ9." +
                                "eyJpc3MiOiJodHRwczovL2lzc3Vlci5leGFtcGxlIiwidmN0IjoiaHR0cHM6Ly9jcmVkZW50aWFscy5leGFtcGxlL2lkZW50aXR5In0.signature~",
                        ),
                    credentialFormat = CredentialFormat.SD_JWT_VC,
                    holderKeyRef = "opaque-holder-key-ref",
                )

            val result = command.execute(CreateAuthorizationResponseArgs(resolvedRequest, listOf(selectedCredential)))

            assertIs<Ok<*>>(result)
            assertEquals(
                selectedCredential.presentation.jsonPrimitive.content,
                result.value.vpToken?.getSinglePresentation("identity_credential_query"),
            )
        }

    @Test
    fun `DCQL default holder binding rejects SD-JWT without holder key`() =
        runTest {
            val command = createCommand()
            val resolvedRequest =
                createResolvedRequest().copy(
                    dcqlQuery =
                        DcqlQuery(
                            credentials =
                                listOf(
                                    DcqlCredentialQuery(
                                        id = "identity_credential_query",
                                        format = "dc+sd-jwt",
                                        meta =
                                            JsonObject(
                                                mapOf(
                                                    "vct_values" to JsonArray(listOf(JsonPrimitive("https://credentials.example/identity"))),
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                )
            val selectedCredential =
                SelectedCredential(
                    credentialQueryId = "identity_credential_query",
                    credentialId = "sdjwt-1",
                    presentation = JsonPrimitive("issuer.jwt.signature~disclosure~"),
                    credentialFormat = CredentialFormat.SD_JWT_VC,
                )

            val result = command.execute(CreateAuthorizationResponseArgs(resolvedRequest, listOf(selectedCredential)))

            assertIs<Err<*>>(result)
            assertTrue(result.error.message.defaultMessage.contains("has not been prepared by the holder signing surface"))
        }

    @Test
    fun dcqlDefaultHolderBindingAcceptsSdJwtPreparedByHolderSigningDelegate() =
        runTest {
            val command = createCommand()
            val resolvedRequest =
                createResolvedRequest().let { base ->
                    base.copy(
                        request = base.request.copy(nonce = "test-nonce"),
                        dcqlQuery =
                            DcqlQuery(
                                credentials =
                                    listOf(
                                        DcqlCredentialQuery(
                                            id = "identity_credential_query",
                                            format = "dc+sd-jwt",
                                            meta =
                                                JsonObject(
                                                    mapOf(
                                                        "vct_values" to JsonArray(listOf(JsonPrimitive("https://credentials.example/identity"))),
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                    )
                }
            val preparedPresentation =
                "eyJhbGciOiJFUzI1NiJ9.e30.signature~" +
                    "eyJhbGciOiJFUzI1NiJ9." +
                    "eyJhdWQiOiJ0ZXN0LWNsaWVudCIsIm5vbmNlIjoidGVzdC1ub25jZSIsImlhdCI6MSwic2RfaGFzaCI6IngifQ.signature"
            val selectedCredential =
                SelectedCredential(
                    credentialQueryId = "identity_credential_query",
                    credentialId = "sdjwt-1",
                    presentation = JsonPrimitive(preparedPresentation),
                    credentialFormat = CredentialFormat.SD_JWT_VC,
                    sdJwtKeyBindingApplied = true,
                )

            val result = command.execute(CreateAuthorizationResponseArgs(resolvedRequest, listOf(selectedCredential)))

            assertIs<Ok<*>>(result, result.toString())
            assertEquals(preparedPresentation, result.value.vpToken?.getSinglePresentation("identity_credential_query"))
        }

    @Test
    fun transactionDataRejectsCredentialQueryThatDisablesHolderBinding() =
        runTest {
            val command = createCommand()
            val resolvedRequest =
                createResolvedRequest().copy(
                    dcqlQuery =
                        DcqlQuery(
                            credentials =
                                listOf(
                                    DcqlCredentialQuery(
                                        id = "identity_credential_query",
                                        format = "dc+sd-jwt",
                                        meta =
                                            JsonObject(
                                                mapOf(
                                                    "vct_values" to JsonArray(listOf(JsonPrimitive("https://credentials.example/identity"))),
                                                ),
                                            ),
                                        require_cryptographic_holder_binding = false,
                                    ),
                                ),
                        ),
                    transactionData =
                        listOf(
                            ParsedTransactionDataEntry(
                                transactionData =
                                    TransactionDataEntry(
                                        type = "payment",
                                        credentialIds = listOf("identity_credential_query"),
                                    ),
                                transactionDataIndex = 0,
                                encoded = "encoded-transaction-data",
                            ),
                        ),
                )
            val selectedCredential =
                SelectedCredential(
                    credentialQueryId = "identity_credential_query",
                    credentialId = "sdjwt-1",
                    presentation = JsonPrimitive("issuer.jwt.signature~disclosure~"),
                    credentialFormat = CredentialFormat.SD_JWT_VC,
                )

            val result = command.execute(CreateAuthorizationResponseArgs(resolvedRequest, listOf(selectedCredential)))

            assertIs<Err<*>>(result)
            assertTrue(result.error.message.defaultMessage.contains("Transaction data references Credential Query"))
        }

    @Test
    fun `test create response with mixed non-mdoc credential formats`() =
        runTest {
            // Two SD-JWT credential profiles (no holder key -> pass-through). The mso_mdoc format is
            // exercised separately in the jvmTest sibling because it now produces a real
            // DeviceResponse (it can no longer be represented by an opaque stub string here).
            val command = createCommand()

            val resolvedRequest = createResolvedRequest()
            val selectedCredentials =
                listOf(
                    SelectedCredential(
                        credentialQueryId = "sdjwt_query",
                        credentialId = "sdjwt-1",
                        presentation = JsonPrimitive("eyJ...~WyJz...~eyJ...kb"),
                        credentialFormat = CredentialFormat.SD_JWT_VC,
                        disclosedClaims = mapOf("name" to "John"),
                    ),
                    SelectedCredential(
                        credentialQueryId = "jwt_query",
                        credentialId = "jwt-1",
                        presentation = JsonPrimitive("eyJhbG...signature"),
                        credentialFormat = CredentialFormat.W3C_VC_SD_JWT,
                    ),
                )

            val args = CreateAuthorizationResponseArgs(resolvedRequest, selectedCredentials)
            val result = command.execute(args)

            assertIs<Ok<*>>(result)
            val response = result.value

            // Verify all presentations are included with their query IDs
            val vpToken = response.vpToken
            assertNotNull(vpToken)
            assertEquals(2, vpToken.presentations.size)
            assertEquals(2, vpToken.presentationCount)
            assertEquals("eyJ...~WyJz...~eyJ...kb", vpToken.getSinglePresentation("sdjwt_query"))
            assertEquals("eyJhbG...signature", vpToken.getSinglePresentation("jwt_query"))
        }

    @Test
    fun `vcdm credentials produce one independently bound VP per selected credential`() =
        runTest {
            val signer = RecordingJwtService()
            val command = createCommand(signer)
            val request = createResolvedRequest()
            val v1Credential = vcdm11Credential()
            val v2Credential = vcdm20Credential()
            val credentials =
                listOf(
                    SelectedCredential(
                        credentialQueryId = "v1",
                        credentialId = "credential-v1",
                        presentation = JsonPrimitive(v1Credential),
                        credentialFormat = CredentialFormat.JWT_VC_JSON,
                        holderKeyRef = "same-holder-key",
                        holderId = "did:example:holder",
                        holderVerificationMethod = "https://wallet.example/jwks#holder-1",
                        holderJwtVpSigningIdentifier =
                            HolderJwtVpSigningIdentifier.JwksKid("https://wallet.example/jwks#holder-1"),
                        holderSigningAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    ),
                    SelectedCredential(
                        credentialQueryId = "v2",
                        credentialId = "credential-v2",
                        presentation = JsonPrimitive(v2Credential),
                        credentialFormat = CredentialFormat.JWT_VC_JSON_LD,
                        holderKeyRef = "same-holder-key",
                        holderId = "did:example:holder",
                        holderVerificationMethod = "https://wallet.example/jwks#holder-1",
                        holderJwtVpSigningIdentifier =
                            HolderJwtVpSigningIdentifier.JwksKid("https://wallet.example/jwks#holder-1"),
                        holderSigningAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    ),
                )

            val result = command.execute(CreateAuthorizationResponseArgs(request, credentials))

            assertTrue(result.isOk, "VCDM VP production failed: ${if (result.isErr) result.error.message.defaultMessage else ""}")
            assertIs<Ok<*>>(result)
            assertEquals(2, result.value.vpToken?.presentationCount)
            assertEquals(2, signer.calls.size)
            assertEquals(
                setOf("v1", "v2"),
                result.value.vpToken?.queryIds,
            )
            assertTrue(
                result.value.vpToken!!.allPresentations.distinct().size == 2,
                "each selected credential must yield a separately signed VP",
            )

            val v1 = signer.calls[0].payload
            val v1Vp = v1["vp"]!!.jsonObject
            assertEquals("https://www.w3.org/2018/credentials/v1", v1Vp["@context"]!!.jsonArray.first().toString().trim('"'))
            assertEquals("VerifiablePresentation", v1Vp["type"]!!.jsonArray.first().toString().trim('"'))
            assertEquals("did:example:holder", v1Vp["holder"]!!.jsonPrimitive.content)
            assertEquals(v1Credential, v1Vp["verifiableCredential"]!!.jsonArray.single().jsonPrimitive.content)
            assertEquals("test-nonce", v1["nonce"]!!.jsonPrimitive.content)
            assertEquals("test-client", v1["aud"]!!.jsonPrimitive.content)

            val v2 = signer.calls[1].payload
            assertEquals("@context", v2.keys.first())
            assertEquals("https://www.w3.org/ns/credentials/v2", v2["@context"]!!.jsonArray.first().jsonPrimitive.content)
            assertEquals("VerifiablePresentation", v2["type"]!!.jsonArray.first().jsonPrimitive.content)
            assertEquals("did:example:holder", v2["holder"]!!.jsonPrimitive.content)
            val child = v2["verifiableCredential"]!!.jsonArray.single().jsonObject
            assertEquals("https://www.w3.org/ns/credentials/v2", child["@context"]!!.jsonPrimitive.content)
            assertEquals("EnvelopedVerifiableCredential", child["type"]!!.jsonPrimitive.content)
            assertEquals("data:application/vc+jwt,$v2Credential", child["id"]!!.jsonPrimitive.content)
            assertEquals("JWT", signer.calls[0].protectedHeader["typ"]!!.jsonPrimitive.content)
            assertEquals("vp+jwt", signer.calls[1].protectedHeader["typ"]!!.jsonPrimitive.content)
            assertEquals("vp", signer.calls[1].protectedHeader["cty"]!!.jsonPrimitive.content)
        }

    @Test
    fun `compact JWT credentials with distinct subjects and holder bindings stay independently secured`() = runTest {
        val signer = RecordingJwtService()
        val command = createCommand(signer)
        val credentials = listOf(
            SelectedCredential(
                credentialQueryId = "first",
                credentialId = "credential-first",
                presentation = JsonPrimitive(vcdm11Credential("urn:subject:first")),
                credentialFormat = CredentialFormat.JWT_VC_JSON,
                holderKeyRef = "holder-key-first",
                holderId = "did:example:holder:first",
                holderJwtVpSigningIdentifier = HolderJwtVpSigningIdentifier.JwksKid("https://wallet.example/jwks#first"),
                holderSigningAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            ),
            SelectedCredential(
                credentialQueryId = "second",
                credentialId = "credential-second",
                presentation = JsonPrimitive(vcdm11Credential("urn:subject:second")),
                credentialFormat = CredentialFormat.JWT_VC_JSON,
                holderKeyRef = "holder-key-second",
                holderId = "did:example:holder:second",
                holderJwtVpSigningIdentifier = HolderJwtVpSigningIdentifier.JwksKid("https://wallet.example/jwks#second"),
                holderSigningAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            ),
        )

        val result = command.execute(CreateAuthorizationResponseArgs(createResolvedRequest(), credentials))

        assertTrue(result.isOk, "compact JWT VP production failed: ${if (result.isErr) result.error.message.defaultMessage else ""}")
        assertEquals(2, signer.calls.size)
        assertEquals(2, result.value.vpToken!!.presentationCount)
        assertEquals(2, result.value.vpToken!!.allPresentations.distinct().size)
        assertEquals(
            listOf("https://wallet.example/jwks#first", "https://wallet.example/jwks#second"),
            signer.calls.map { it.issuerIdentifier },
            "each VP must be signed with its explicitly resolved holder identifier",
        )
        assertEquals(
            listOf("holder-key-first", "holder-key-second"),
            signer.calls.map { it.keyReference },
            "each VP must resolve the corresponding holder key reference",
        )
        assertEquals("urn:subject:first", signer.calls[0].payload["vp"]!!.jsonObject["verifiableCredential"]!!.jsonArray.single().jsonPrimitive.content.let { decodeJwtSubject(it) })
        assertEquals("urn:subject:second", signer.calls[1].payload["vp"]!!.jsonObject["verifiableCredential"]!!.jsonArray.single().jsonPrimitive.content.let { decodeJwtSubject(it) })
    }

    @Test
    fun `vcdm VP production preserves an explicitly admitted X509 chain`() =
        runTest {
            val signer = RecordingJwtService()
            val command = createCommand(signer)
            val chain = listOf("bGVhZi1jZXJ0", "aW50ZXJtZWRpYXRlLWNlcnQ=")
            val credential =
                SelectedCredential(
                    credentialQueryId = "v2-x509",
                    credentialId = "credential-v2-x509",
                    presentation = JsonPrimitive(vcdm20Credential()),
                    credentialFormat = CredentialFormat.JWT_VC_JSON_LD,
                    holderKeyRef = "opaque-holder-key",
                    holderId = "https://wallet.example/holders/123",
                    holderJwtVpSigningIdentifier = HolderJwtVpSigningIdentifier.X509("holder-certificate", chain),
                    holderSigningAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                )

            val result = command.execute(CreateAuthorizationResponseArgs(createResolvedRequest(), listOf(credential)))

            assertTrue(result.isOk, "X.509 VCDM VP production failed: ${if (result.isErr) result.error.message.defaultMessage else ""}")
            assertIs<Ok<*>>(result)
            assertEquals(JsonArray(chain.map(::JsonPrimitive)), signer.calls.single().protectedHeader["x5c"])
            assertTrue("kid" !in signer.calls.single().protectedHeader)
        }

    @Test
    fun `vcdm VP production rejects a holder algorithm outside verifier metadata`() =
        runTest {
            val signer = RecordingJwtService()
            val command = createCommand(signer)
            val credential =
                SelectedCredential(
                    credentialQueryId = "v2-algorithm-mismatch",
                    credentialId = "credential-v2-algorithm-mismatch",
                    presentation = JsonPrimitive(vcdm20Credential()),
                    credentialFormat = CredentialFormat.JWT_VC_JSON_LD,
                    holderKeyRef = "holder-key",
                    holderId = "did:example:holder",
                    holderJwtVpSigningIdentifier = HolderJwtVpSigningIdentifier.ManagedKid("holder-key"),
                    holderSigningAlgorithm = SignatureAlgorithm.ECDSA_SHA384,
                )

            val result = command.execute(CreateAuthorizationResponseArgs(createResolvedRequest(), listOf(credential)))

            assertIs<Err<*>>(result)
            assertTrue(result.error.message.defaultMessage.contains("not accepted", ignoreCase = true))
            assertTrue(signer.calls.isEmpty(), "an unadvertised holder algorithm must be rejected before signing")
        }

    @Test
    fun `holder rejects a selected credential whose DCQL format differs before signing`() =
        runTest {
            val signer = RecordingJwtService()
            val command = createCommand(signer)
            val resolvedRequest =
                createResolvedRequest().copy(
                    dcqlQuery =
                        DcqlQuery(
                            credentials =
                                listOf(
                                    DcqlCredentialQuery(
                                        id = "vcdm-query",
                                        format = CredentialFormat.JWT_VC_JSON_LD.value,
                                        meta = buildJsonObject {
                                            putJsonArray("type_values") { add(JsonArray(listOf(JsonPrimitive("VerifiableCredential")))) }
                                        },
                                    ),
                                ),
                        ),
                )
            val selected =
                SelectedCredential(
                    credentialQueryId = "vcdm-query",
                    credentialId = "vcdm-credential",
                    presentation = JsonPrimitive(vcdm20Credential()),
                    credentialFormat = CredentialFormat.JWT_VC_JSON,
                    holderKeyRef = "holder-key",
                    holderId = "did:example:holder",
                    holderJwtVpSigningIdentifier = HolderJwtVpSigningIdentifier.ManagedKid("holder-key"),
                    holderSigningAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                )

            val result = command.execute(CreateAuthorizationResponseArgs(resolvedRequest, listOf(selected)))

            assertIs<Err<*>>(result)
            assertTrue(result.error.message.defaultMessage.contains("DCQL", ignoreCase = true))
            assertTrue(signer.calls.isEmpty(), "credential-format mismatch must be rejected before holder signing")
        }

    @Test
    fun `prepared ldp VP cannot aggregate JWT credentials or reuse a selected credential`() =
        runTest {
            val signer = RecordingJwtService()
            val command = createCommand(signer)
            val first =
                SelectedCredential(
                    credentialQueryId = "first",
                    credentialId = "credential-first",
                    presentation = JsonPrimitive(vcdm20Credential()),
                    credentialFormat = CredentialFormat.JWT_VC_JSON_LD,
                )
            val second = first.copy(credentialQueryId = "second", credentialId = "credential-second")
            val prepared =
                PreparedPresentation(
                    presentation = buildJsonObject { put("type", "VerifiablePresentation") },
                    presentationFormat = PresentationFormat.LDP_VP,
                    credentialQueryIds = listOf("first", "second"),
                    credentialIds = listOf(first.credentialId, second.credentialId),
                )

            val result =
                command.execute(
                    CreateAuthorizationResponseArgs(
                        request = createResolvedRequest(),
                        selectedCredentials = listOf(first, second),
                        preparedPresentations = listOf(prepared),
                    ),
                )

            assertIs<Err<*>>(result)
            assertTrue(result.error.message.defaultMessage.contains("ldp_vp", ignoreCase = true))
            assertTrue(signer.calls.isEmpty())
        }

    @Test
    fun `prepared presentations reject partial credential overlap`() =
        runTest {
            val command = createCommand()
            val first = SelectedCredential("q1", "credential-a", JsonObject(emptyMap()), CredentialFormat.LDP_VC)
            val second = SelectedCredential("q2", "credential-b", JsonObject(emptyMap()), CredentialFormat.LDP_VC)
            val preparedA = PreparedPresentation(
                presentation = buildJsonObject { put("type", "VerifiablePresentation") },
                presentationFormat = PresentationFormat.LDP_VP,
                credentialQueryIds = listOf("q1"),
                credentialIds = listOf("credential-a"),
            )
            val preparedAB = preparedA.copy(
                credentialQueryIds = listOf("q1", "q2"),
                credentialIds = listOf("credential-a", "credential-b"),
            )

            val result = command.execute(
                CreateAuthorizationResponseArgs(
                    request = createResolvedRequest(),
                    selectedCredentials = listOf(first, second),
                    preparedPresentations = listOf(preparedA, preparedAB),
                ),
            )

            assertIs<Err<*>>(result)
            assertTrue(result.error.message.defaultMessage.contains("multiple prepared", ignoreCase = true))
        }

    @Test
    fun `vcdm VP production rejects a credential whose classified version contradicts its declared format`() =
        runTest {
            val signer = RecordingJwtService()
            val command = createCommand(signer)
            val credential =
                SelectedCredential(
                    credentialQueryId = "mismatch",
                    credentialId = "credential-mismatch",
                    presentation = JsonPrimitive(vcdm11Credential()),
                    credentialFormat = CredentialFormat.JWT_VC_JSON_LD,
                    holderKeyRef = "holder-key",
                    holderId = "did:example:holder",
                    holderJwtVpSigningIdentifier = HolderJwtVpSigningIdentifier.ManagedKid("holder-key"),
                    holderSigningAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                )

            val result = command.execute(CreateAuthorizationResponseArgs(createResolvedRequest(), listOf(credential)))

            assertIs<Err<*>>(result)
            assertTrue(result.error.message.defaultMessage.contains("declared", ignoreCase = true))
            assertTrue(signer.calls.isEmpty(), "a format-confused credential must not be holder-signed")
        }

    @Test
    fun `vcdm VP production rejects a structurally classified credential that violates its profile`() =
        runTest {
            val signer = RecordingJwtService()
            val command = createCommand(signer)
            val missingIssuer =
                compactCredential(
                    header = buildJsonObject {
                        put("alg", "ES256")
                        put("typ", "vc+jwt")
                        put("cty", "vc")
                    },
                    payload = buildJsonObject {
                        putJsonArray("@context") { add("https://www.w3.org/ns/credentials/v2") }
                        putJsonArray("type") { add("VerifiableCredential") }
                        putJsonObject("credentialSubject") { put("id", "https://subject.example") }
                    },
                )
            val credential =
                SelectedCredential(
                    credentialQueryId = "invalid-profile",
                    credentialId = "credential-invalid-profile",
                    presentation = JsonPrimitive(missingIssuer),
                    credentialFormat = CredentialFormat.JWT_VC_JSON_LD,
                    holderKeyRef = "holder-key",
                    holderId = "did:example:holder",
                    holderJwtVpSigningIdentifier = HolderJwtVpSigningIdentifier.ManagedKid("holder-key"),
                )

            val result = command.execute(CreateAuthorizationResponseArgs(createResolvedRequest(), listOf(credential)))

            assertIs<Err<*>>(result)
            assertTrue(result.error.message.defaultMessage.contains("VCDM profile", ignoreCase = true))
            assertTrue(signer.calls.isEmpty(), "a profile-invalid credential must not be holder-signed")
        }

    @Test
    fun `vcdm VP production fails closed when binding context or holder key is missing`() =
        runTest {
            val command = createCommand(RecordingJwtService())
            val base =
                SelectedCredential(
                    credentialQueryId = "v2",
                    credentialId = "credential-v2",
                    presentation = JsonPrimitive(vcdm20Credential()),
                    credentialFormat = CredentialFormat.JWT_VC_JSON_LD,
                    holderKeyRef = "holder-key",
                    holderId = "did:example:holder",
                    holderVerificationMethod = "https://wallet.example/jwks#holder-1",
                    holderJwtVpSigningIdentifier =
                        HolderJwtVpSigningIdentifier.JwksKid("https://wallet.example/jwks#holder-1"),
                    holderSigningAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                )

            val missingNonce = command.execute(
                CreateAuthorizationResponseArgs(
                    createResolvedRequest().copy(
                        request = createResolvedRequest().request.copy(nonce = null),
                    ),
                    listOf(base),
                ),
            )
            assertIs<Err<*>>(missingNonce)
            assertTrue(missingNonce.error.message.defaultMessage.contains("nonce", ignoreCase = true))

            val missingKey = command.execute(
                CreateAuthorizationResponseArgs(
                    createResolvedRequest(),
                    listOf(base.copy(holderKeyRef = null)),
                ),
            )
            assertIs<Err<*>>(missingKey)
            assertTrue(missingKey.error.message.defaultMessage.contains("holder key", ignoreCase = true))

            val missingIdentifier = command.execute(
                CreateAuthorizationResponseArgs(
                    createResolvedRequest(),
                    listOf(base.copy(holderJwtVpSigningIdentifier = null)),
                ),
            )
            assertIs<Err<*>>(missingIdentifier)
            assertTrue(missingIdentifier.error.message.defaultMessage.contains("explicit holder signing identifier", ignoreCase = true))
        }

    @Test
    fun `ldp_vc produces one holder bound Data Integrity presentation without assuming DID`() =
        runTest {
            val proofCommand = RecordingAddProofCommand()
            val command = createCommand(addProofServiceCommand = proofCommand)
            val credential =
                buildJsonObject {
                    putJsonArray("@context") { add(JsonPrimitive("https://www.w3.org/ns/credentials/v2")) }
                    putJsonArray("type") { add(JsonPrimitive("VerifiableCredential")) }
                    put("issuer", "https://issuer.example/identifiers/signing")
                    putJsonObject("credentialSubject") { put("id", "urn:employee:123") }
                    putJsonObject("proof") { put("type", "DataIntegrityProof"); put("proofValue", "zissuer-proof") }
                }
            val securedVp =
                buildJsonObject {
                    putJsonArray("@context") { add(JsonPrimitive("https://www.w3.org/ns/credentials/v2")) }
                    putJsonArray("type") { add(JsonPrimitive("VerifiablePresentation")) }
                    put("holder", "https://wallet.example/holders/123")
                    putJsonArray("verifiableCredential") { add(credential) }
                    putJsonObject("proof") {
                        put("type", "DataIntegrityProof")
                        put("cryptosuite", "eddsa-jcs-2022")
                        put("proofPurpose", "authentication")
                        put("verificationMethod", "https://wallet.example/jwks/123#key-1")
                        put("proofValue", "zholder-proof")
                    }
                }
            val selected =
                SelectedCredential(
                    credentialQueryId = "employee",
                    credentialId = "credential-di",
                    presentation = securedVp,
                    credentialFormat = CredentialFormat.LDP_VC,
                    holderKeyRef = "wallet-kms-alias",
                    holderId = "https://wallet.example/holders/123",
                    holderVerificationMethod = "https://wallet.example/jwks/123#key-1",
                    dataIntegrityCryptosuite = "eddsa-jcs-2022",
                    dataIntegrityProofApplied = true,
                )

            val result = command.execute(CreateAuthorizationResponseArgs(createResolvedRequest(), listOf(selected)))

            assertIs<Ok<*>>(result)
            val presentation = assertIs<JsonObject>(result.value.vpToken!!.presentationElements.getValue("employee").single())
            assertEquals("https://www.w3.org/ns/credentials/v2", presentation["@context"]!!.jsonArray.first().jsonPrimitive.content)
            assertEquals("VerifiablePresentation", presentation["type"]!!.jsonArray.first().jsonPrimitive.content)
            assertEquals("https://wallet.example/holders/123", presentation["holder"]!!.jsonPrimitive.content)
            assertEquals(credential, presentation["verifiableCredential"]!!.jsonArray.single())
            assertEquals("authentication", presentation["proof"]!!.jsonObject["proofPurpose"]!!.jsonPrimitive.content)
            assertTrue(proofCommand.inputs.isEmpty(), "generic holder must not sign an already-secured ldp_vc VP")
        }

    @Test
    fun `prepared aggregate VP is emitted once for credentials sharing a DCQL query`() = runTest {
        val command = createCommand()
        val first = SelectedCredential(
            credentialQueryId = "employee",
            credentialId = "credential-a",
            presentation = buildJsonObject { put("credentialSubject", buildJsonObject { put("id", "urn:subject:a") }) },
            credentialFormat = CredentialFormat.LDP_VC,
        )
        val second = first.copy(
            credentialId = "credential-b",
            presentation = buildJsonObject { put("credentialSubject", buildJsonObject { put("id", "urn:subject:b") }) },
        )
        val aggregate = buildJsonObject {
            putJsonArray("@context") { add("https://www.w3.org/ns/credentials/v2") }
            putJsonArray("type") { add("VerifiablePresentation") }
            putJsonArray("verifiableCredential") { add(first.presentation); add(second.presentation) }
            putJsonArray("proof") {
                add(buildJsonObject { put("type", "DataIntegrityProof"); put("verificationMethod", "https://holder.example/key#a") })
                add(buildJsonObject { put("type", "DataIntegrityProof"); put("verificationMethod", "https://holder.example/key#b") })
            }
        }
        val response = command.execute(
            CreateAuthorizationResponseArgs(
                createResolvedRequest(),
                listOf(first, second),
                listOf(PreparedPresentation(aggregate, PresentationFormat.LDP_VP, listOf("employee"), listOf("credential-a", "credential-b"))),
            ),
        )

        assertIs<Ok<*>>(response)
        val presentations = response.value.vpToken!!.presentationElements["employee"]!!
        assertEquals(1, presentations.size)
        assertEquals(2, presentations.single().jsonObject["verifiableCredential"]!!.jsonArray.size)
        assertEquals(2, presentations.single().jsonObject["proof"]!!.jsonArray.size)
    }

    @Test
    fun `separately prepared SD-JWT holder bindings remain separate presentations`() = runTest {
        val command = createCommand()
        val first = SelectedCredential(
            credentialQueryId = "employee",
            credentialId = "sdjwt-first",
            presentation = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJmaXJzdCJ9.signature~kb-first"),
            credentialFormat = CredentialFormat.SD_JWT_VC,
            holderKeyRef = null,
            sdJwtKeyBindingApplied = true,
        )
        val second = first.copy(
            credentialId = "sdjwt-second",
            presentation = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJzZWNvbmQifQ.signature~kb-second"),
        )

        val response = command.execute(CreateAuthorizationResponseArgs(createResolvedRequest(), listOf(first, second)))

        assertIs<Ok<*>>(response)
        val presentations = response.value.vpToken!!.getPresentation("employee")!!
        assertEquals(2, presentations.size)
        assertEquals(first.presentation.jsonPrimitive.content, presentations[0])
        assertEquals(second.presentation.jsonPrimitive.content, presentations[1])
    }

    private fun createCommand(
        jwtService: JwtService = RecordingJwtService(),
        addProofServiceCommand: AddProofServiceCommand = RecordingAddProofCommand(),
    ): CreateAuthorizationResponseCommandImpl {
        val execution = TestExecutionContext.createExecution()
        // Real mdoc-core impls (no fakes). The mso_mdoc DeviceResponse path is exercised end to
        // end in the jvmTest sibling (CreateAuthorizationResponseCommandImplMdocTest) where a real
        // device-key-bound mdoc is issued and signed; these common tests cover the SD-JWT / JWT /
        // pass-through branches and never reach the mdoc service.
        val mdocSignService =
            MdocSignServiceImpl(
                coseCryptoService = CoseCryptoServiceImpl(),
                execution = execution,
                mobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl(),
                sessionTranscriptCborCodec = SessionTranscriptCborCodecImpl(),
            )
        return CreateAuthorizationResponseCommandImpl(
            execution = execution,
            holderJwtVpSigningProvider = JwtServiceHolderJwtVpSigningProvider(jwtService),
            addProofServiceCommand = addProofServiceCommand,
            mdocOid4vpService =
                MdocOid4vpServiceImpl(
                    signService = mdocSignService,
                    logService = execution.log,
                    mobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl(),
                ),
            issuerSignedCborCodec = IssuerSignedCborCodecImpl(),
            deviceResponseCborCodec = DeviceResponseCborCodecImpl(),
            mobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl(),
        )
    }

    private fun vcdm11Credential(subject: String = "https://subject.example"): String =
        compactCredential(
            header = buildJsonObject {
                put("alg", "ES256")
                put("typ", "JWT")
            },
            payload =
                buildJsonObject {
                    put("iss", "https://issuer.example")
                    put("sub", subject)
                    put("nbf", 1)
                    putJsonObject("vc") {
                        putJsonArray("@context") { add("https://www.w3.org/2018/credentials/v1") }
                        putJsonArray("type") { add("VerifiableCredential") }
                        put("issuer", "https://issuer.example")
                        put("issuanceDate", "1970-01-01T00:00:01Z")
                        putJsonObject("credentialSubject") { put("id", subject) }
                    }
                },
        )

    private fun vcdm20Credential(): String =
        compactCredential(
            header = buildJsonObject {
                put("alg", "ES256")
                put("typ", "vc+jwt")
                put("cty", "vc")
            },
            payload =
                buildJsonObject {
                    putJsonArray("@context") { add("https://www.w3.org/ns/credentials/v2") }
                    putJsonArray("type") { add("VerifiableCredential") }
                    put("issuer", "https://issuer.example")
                    put("iss", "https://issuer.example")
                    putJsonObject("credentialSubject") { put("id", "https://subject.example") }
                },
        )

    private fun compactCredential(header: JsonObject, payload: JsonObject): String =
        listOf(
            header.toString().encodeToByteArray().encodeToBase64Url(),
            payload.toString().encodeToByteArray().encodeToBase64Url(),
            "issuer-signature".encodeToByteArray().encodeToBase64Url(),
        ).joinToString(".")

    private fun decodeJwtSubject(compact: String): String {
        val payload = compact.split('.')[1].decodeFromBase64Url().decodeToString()
        return kotlinx.serialization.json.Json.parseToJsonElement(payload).jsonObject["vc"]!!.jsonObject["credentialSubject"]!!.jsonObject["id"]!!.jsonPrimitive.content
    }

}

private class RecordingAddProofCommand : AddProofServiceCommand {
    val inputs = mutableListOf<AddProofInput>()
    override val inputTypeToken: TypeToken<AddProofInput> = typeToken<AddProofInput>()
    override val outputTypeToken: TypeToken<AddProofOutput> = typeToken<AddProofOutput>()
    override val isEnabled: Boolean = true

    override suspend fun execute(args: AddProofInput): IdkResult<AddProofOutput, IdkError> {
        inputs += args
        val options = args.proofs.single()
        val proof =
            buildJsonObject {
                put("type", "DataIntegrityProof")
                put("cryptosuite", options.cryptosuite)
                put("proofPurpose", options.proofPurpose.value)
                put("verificationMethod", options.verificationMethod)
                options.domain?.let { put("domain", it) }
                options.challenge?.let { put("challenge", it) }
                put("proofValue", "zholder-proof")
            }
        return Ok(AddProofOutput(JsonObject(args.unsecuredDocument + ("proof" to proof))))
    }
}

private class RecordingJwtService : JwtService {
    data class Call(
        val payload: JsonObject,
        val protectedHeader: JsonObject,
        val issuerIdentifier: Any?,
        val keyReference: String?,
    )

    val calls = mutableListOf<Call>()

    override val commands: JwtService.Commands
        get() = error("not needed")

    override fun assembleJwsGeneral(prepared: PreparedJwsObject, signatureBytes: ByteArray): JwsJsonGeneral = error("not needed")
    override fun assembleJwsFlattened(prepared: PreparedJwsObject, signatureBytes: ByteArray): JwsJsonFlattened = error("not needed")
    override fun assembleJwsCompact(prepared: PreparedJwsObject, signatureBytes: ByteArray): JwtCompactResult = error("not needed")
    override suspend fun prepareJws(args: CreateJwsJsonArgs): IdkResult<PreparedJwsObject, IdkError> = error("not needed")
    override suspend fun createJwsJsonFlattened(args: CreateJwsJsonArgs): IdkResult<JwsJsonFlattened, IdkError> = error("not needed")
    override suspend fun createJwsJsonGeneral(args: CreateJwsJsonArgs): IdkResult<JwsJsonGeneral, IdkError> = error("not needed")
    override suspend fun verifyJws(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> = error("not needed")

    override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> {
        val payload = args.payload as JsonObject
        val protectedHeader = args.opts.protectedHeader!!
        val header = buildJsonObject {
            put("alg", "ES256")
            protectedHeader.forEach { (key, value) -> put(key, value) }
            when (val identifier = args.issuer?.identifier) {
                is String -> put("kid", identifier)
                is List<*> -> put("x5c", JsonArray(identifier.map { JsonPrimitive(it as String) }))
            }
        }
        calls += Call(payload, header, args.issuer?.identifier, args.issuer?.lookup?.alias)
        val encodedHeader = header.toString().encodeToByteArray().encodeToBase64Url()
        val encodedPayload = payload.toString().encodeToByteArray().encodeToBase64Url()
        val signature = "signature-${calls.size}".encodeToByteArray().encodeToBase64Url()
        return com.sphereon.core.api.Ok(JwtCompactResult("$encodedHeader.$encodedPayload.$signature"))
    }
}
