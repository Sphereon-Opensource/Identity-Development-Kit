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
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.CoseCryptoServiceImpl
import com.sphereon.mdoc.MdocSignServiceImpl
import com.sphereon.mdoc.SessionTranscriptCborCodecImpl
import com.sphereon.mdoc.data.device.DeviceResponseCborCodecImpl
import com.sphereon.mdoc.data.device.IssuerSignedCborCodecImpl
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodecImpl
import com.sphereon.mdoc.oid4vp.MdocOid4vpServiceImpl
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.VpToken
import com.sphereon.openid.oid4vp.common.vpToken
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.openid.oid4vp.holder.VerifierInfo
import com.sphereon.sdjwt.PresentSdJwtArgs
import com.sphereon.sdjwt.PresentSdJwtResult
import com.sphereon.sdjwt.command.PresentSdJwtCommand
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
                additionalParameters =
                    mapOf(
                        "nonce" to JsonPrimitive("test-nonce"),
                        "response_mode" to JsonPrimitive("direct_post"),
                    ),
            )

        return ResolvedOid4vpRequest(
            request = authRequest,
            dcqlQuery = null,
            clientMetadata = null,
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
                        presentation = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIxMjM0In0.signature",
                        format = "dc+sd-jwt",
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
                        presentation = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIxMjM0In0.sig1",
                        format = "dc+sd-jwt",
                        disclosedClaims = mapOf("given_name" to "John"),
                    ),
                    SelectedCredential(
                        credentialQueryId = "employment_query",
                        credentialId = "cred-2",
                        presentation = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiI1Njc4In0.sig2",
                        format = "dc+sd-jwt",
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
                        presentation = "eyJhbGciOiJFUzI1NiJ9.employer1.sig1",
                        format = "dc+sd-jwt",
                    ),
                    SelectedCredential(
                        credentialQueryId = "employment_query",
                        credentialId = "cred-2",
                        presentation = "eyJhbGciOiJFUzI1NiJ9.employer2.sig2",
                        format = "dc+sd-jwt",
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
                        presentation = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIxMjM0In0.signature",
                        format = "dc+sd-jwt",
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
                        presentation = sdJwtPresentation,
                        format = "dc+sd-jwt",
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
    fun `test create response with mixed non-mdoc credential formats`() =
        runTest {
            // SD-JWT (no holder key -> pass-through) + JWT VP pass-through. The mso_mdoc format is
            // exercised separately in the jvmTest sibling because it now produces a real
            // DeviceResponse (it can no longer be represented by an opaque stub string here).
            val command = createCommand()

            val resolvedRequest = createResolvedRequest()
            val selectedCredentials =
                listOf(
                    SelectedCredential(
                        credentialQueryId = "sdjwt_query",
                        credentialId = "sdjwt-1",
                        presentation = "eyJ...~WyJz...~eyJ...kb",
                        format = "dc+sd-jwt",
                        disclosedClaims = mapOf("name" to "John"),
                    ),
                    SelectedCredential(
                        credentialQueryId = "jwt_query",
                        credentialId = "jwt-1",
                        presentation = "eyJhbG...signature",
                        format = "jwt_vp_json",
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

    private fun createCommand(): CreateAuthorizationResponseCommandImpl {
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
            presentSdJwtCommand = FakePresentSdJwtCommand,
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

    /**
     * Fake SD-JWT present command. These tests never set [SelectedCredential.holderKeyAlias], so
     * the holder skips KB-JWT production and this fake is never invoked. It fails loudly if a test
     * ever does request a Key Binding JWT, so the no-op path stays honest.
     */
    private object FakePresentSdJwtCommand : PresentSdJwtCommand {
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<PresentSdJwtArgs> = typeToken<PresentSdJwtArgs>()
        override val outputTypeToken: TypeToken<PresentSdJwtResult> = typeToken<PresentSdJwtResult>()

        override suspend fun execute(args: PresentSdJwtArgs): IdkResult<PresentSdJwtResult, IdkError> = error("FakePresentSdJwtCommand should not be called: no test sets holderKeyAlias")
    }
}
