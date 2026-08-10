/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.integration

import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.dcql.DcqlClaimQuery
import com.sphereon.openid.oid4vp.dcql.ClaimsPathPointer
import com.sphereon.openid.oid4vp.dcql.claimsPathPointer
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.sdJwtVcMeta
import com.sphereon.openid.oid4vp.dcql.w3cVcMeta
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.sphereon.openid.oid4vp.holder.Oid4vpHolder
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriArgs
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.Oid4vpVerifierService
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Graph interface to access OID4VP services from the session graph.
 */
@ContributesTo(SessionScope::class)
interface Oid4vpPresentationTestGraph {
    val oid4vpVerifierService: Oid4vpVerifierService
    val oid4vpHolder: Oid4vpHolder
}

/**
 * End-to-end integration tests for the OID4VP presentation flow.
 *
 * These tests exercise the REAL DI-wired services through the full Metro graph:
 * - Verifier creates authorization requests with DCQL queries
 * - Verifier builds authorization request URIs
 * - Holder parses authorization requests
 * - Session management through real stores
 *
 * Note: OID4VP 1.0 Final uses DCQL, NOT Presentation Exchange.
 */
class Oid4vpPresentationE2ETest {
    private val ctx = Oid4vciTestContext(this)

    private val verifierClientId = "https://verifier.example.com"
    private val responseUri = "https://verifier.example.com/oid4vp/response"

    // =========================================================================
    // Test 1: Verifier creates authorization request with DCQL query
    // =========================================================================

    @Test
    fun verifierCreatesAuthorizationRequest() =
        runTest {
            val graph = ctx.session.graph as Oid4vpPresentationTestGraph
            val verifier = graph.oid4vpVerifierService

            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(
                                id = "identity_credential",
                                format = "dc+sd-jwt",
                                meta = sdJwtVcMeta("urn:test:identity"),
                                claims =
                                    listOf(
                                        DcqlClaimQuery(path = claimsPathPointer("last_name")),
                                        DcqlClaimQuery(path = claimsPathPointer("first_name")),
                                        DcqlClaimQuery(path = claimsPathPointer("birth_date")),
                                    ),
                            ),
                        ),
                )

            val result =
                verifier.createAuthorizationRequest(
                    CreateAuthorizationRequestArgs(
                        instanceId = "oid4vc-integration-verifier",
                        dcqlQuery = dcqlQuery,
                        clientId = verifierClientId,
                        responseUri = responseUri,
                        responseMode = ResponseMode.DIRECT_POST,
                        nonce = "test-nonce-12345",
                        state = "test-state-abcde",
                    ),
                )

            assertTrue(
                result.isOk,
                "Authorization request creation should succeed: ${if (result.isErr) {
                    result.error.message.defaultMessage
                } else {
                    ""
                }}"
            )
            val created = result.value
            assertNotNull(created.request, "Authorization request should be present")
            assertNotNull(created.sessionId, "Session ID should be generated")

            // Verify the request contains expected fields
            val request = created.request
            assertEquals(verifierClientId, request.clientId)
            assertEquals("test-nonce-12345", request.nonce)
            assertEquals("test-state-abcde", request.state)
        }

    // =========================================================================
    // Test 2: Verifier creates request and builds URI
    // =========================================================================

    @Test
    fun verifierCreatesRequestAndBuildsUri() =
        runTest {
            val graph = ctx.session.graph as Oid4vpPresentationTestGraph
            val verifier = graph.oid4vpVerifierService

            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(
                                id = "university_degree",
                                format = "jwt_vc_json",
                                meta = w3cVcMeta(listOf("VerifiableCredential", "UniversityDegreeCredential")),
                                claims =
                                    listOf(
                                        DcqlClaimQuery(path = claimsPathPointer("degree", "type")),
                                        DcqlClaimQuery(path = claimsPathPointer("degree", "name")),
                                    ),
                            ),
                        ),
                )

            // Step 1: Create authorization request
            val createResult =
                verifier.createAuthorizationRequest(
                    CreateAuthorizationRequestArgs(
                        instanceId = "oid4vc-integration-verifier",
                        dcqlQuery = dcqlQuery,
                        clientId = verifierClientId,
                        responseUri = responseUri,
                        responseMode = ResponseMode.DIRECT_POST,
                        nonce = "uri-test-nonce",
                    ),
                )
            assertTrue(createResult.isOk, "Request creation should succeed")
            val created = createResult.value

            // Step 2: Build URI from the request
            val uriResult =
                verifier.buildAuthorizationRequestUri(
                    BuildAuthorizationRequestUriArgs(
                        request = created.request,
                    ),
                )
            assertTrue(uriResult.isOk, "URI building should succeed: ${if (uriResult.isErr) uriResult.error.message.defaultMessage else ""}")
            val uri = uriResult.value.value
            assertNotNull(uri, "URI should not be null")
            assertTrue(uri.startsWith("openid4vp://"), "URI should use openid4vp scheme, got: $uri")
            assertTrue(uri.contains("client_id="), "URI should contain client_id parameter")
        }

    // =========================================================================
    // Test 3: Verifier creates request with multiple credentials
    // =========================================================================

    @Test
    fun verifierCreatesRequestWithMultipleCredentials() =
        runTest {
            val graph = ctx.session.graph as Oid4vpPresentationTestGraph
            val verifier = graph.oid4vpVerifierService

            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(
                                id = "pid_credential",
                                format = "dc+sd-jwt",
                                meta = sdJwtVcMeta("urn:test:pid"),
                                claims =
                                    listOf(
                                        DcqlClaimQuery(path = claimsPathPointer("family_name")),
                                        DcqlClaimQuery(path = claimsPathPointer("given_name")),
                                    ),
                            ),
                            DcqlCredentialQuery(
                                id = "mdl_credential",
                                format = "mso_mdoc",
                                meta = JsonObject(mapOf("doctype_value" to JsonPrimitive("org.iso.18013.5.1.mDL"))),
                                claims =
                                    listOf(
                                        DcqlClaimQuery(path = claimsPathPointer("org.iso.18013.5.1", "document_number")),
                                        DcqlClaimQuery(path = claimsPathPointer("org.iso.18013.5.1", "driving_privileges")),
                                    ),
                            ),
                        ),
                )

            val result =
                verifier.createAuthorizationRequest(
                    CreateAuthorizationRequestArgs(
                        instanceId = "oid4vc-integration-verifier",
                        dcqlQuery = dcqlQuery,
                        clientId = verifierClientId,
                        responseUri = responseUri,
                        responseMode = ResponseMode.DIRECT_POST,
                        nonce = "multi-cred-nonce",
                    ),
                )

            assertTrue(result.isOk, "Multi-credential request should succeed")
            val created = result.value
            assertNotNull(created.request)
            assertNotNull(created.sessionId)
        }

    // =========================================================================
    // Test 4: Verifier and holder commands are fully wired
    // =========================================================================

    @Test
    fun verifierAndHolderCommandsResolveFromDi() =
        runTest {
            val graph = ctx.session.graph as Oid4vpPresentationTestGraph
            val verifier = graph.oid4vpVerifierService
            val holder = graph.oid4vpHolder

            // Verifier commands
            assertNotNull(verifier.commands, "Verifier commands should be available")
            assertNotNull(verifier.commands.createAuthorizationRequest, "createAuthorizationRequest command should be wired")
            assertNotNull(verifier.commands.parseAuthorizationResponse, "parseAuthorizationResponse command should be wired")
            assertNotNull(verifier.commands.validateAuthorizationResponse, "validateAuthorizationResponse command should be wired")
            assertNotNull(verifier.commands.verifyHolderBinding, "verifyHolderBinding command should be wired")
            assertNotNull(verifier.commands.buildAuthorizationRequestUri, "buildAuthorizationRequestUri command should be wired")
            assertNotNull(verifier.commands.createSignedAuthorizationRequest, "createSignedAuthorizationRequest command should be wired")
            assertNotNull(verifier.commands.handleDirectPostResponse, "handleDirectPostResponse command should be wired")
            assertNotNull(verifier.commands.retrieveAuthorizationResponse, "retrieveAuthorizationResponse command should be wired")

            // Holder commands
            assertNotNull(holder.commands, "Holder commands should be available")
            assertNotNull(holder.commands.parseAuthorizationRequest, "parseAuthorizationRequest command should be wired")
            assertNotNull(holder.commands.resolveAuthorizationRequest, "resolveAuthorizationRequest command should be wired")
            assertNotNull(holder.commands.createAuthorizationResponse, "createAuthorizationResponse command should be wired")
            assertNotNull(holder.commands.submitAuthorizationResponse, "submitAuthorizationResponse command should be wired")
        }

    // =========================================================================
    // Test 5: Verifier stores and services resolve from DI
    // =========================================================================

    @Test
    fun verifierStoresResolveFromDi() =
        runTest {
            val graph = ctx.session.graph as Oid4vpPresentationTestGraph
            val verifier = graph.oid4vpVerifierService

            assertNotNull(verifier.responseCodeStore, "Response code store should be available")
            assertNotNull(verifier.authorizationSessionStore, "Authorization session store should be available")
            assertNotNull(verifier.dcqlQueryConfigurationStore, "DCQL query configuration store should be available")
            assertNotNull(verifier.clientMetadataConfigurationStore, "Client metadata configuration store should be available")
            assertNotNull(verifier.requestUriHandler, "Request URI handler should be available")
        }

    // =========================================================================
    // Test 6: Authorization request with DCQL claim constraints
    // =========================================================================

    @Test
    fun verifierCreatesRequestWithClaimValueConstraints() =
        runTest {
            val graph = ctx.session.graph as Oid4vpPresentationTestGraph
            val verifier = graph.oid4vpVerifierService

            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(
                                id = "age_verification",
                                format = "dc+sd-jwt",
                                meta = sdJwtVcMeta("urn:test:age-verification"),
                                claims =
                                    listOf(
                                        DcqlClaimQuery(
                                            path = ClaimsPathPointer(listOf(JsonPrimitive("age_over_18"))),
                                            values = listOf(JsonPrimitive(true)),
                                        ),
                                    ),
                            ),
                        ),
                )

            val result =
                verifier.createAuthorizationRequest(
                    CreateAuthorizationRequestArgs(
                        instanceId = "oid4vc-integration-verifier",
                        dcqlQuery = dcqlQuery,
                        clientId = verifierClientId,
                        responseUri = responseUri,
                        responseMode = ResponseMode.DIRECT_POST,
                        nonce = "age-verify-nonce",
                    ),
                )

            assertTrue(result.isOk, "Request with value constraints should succeed")
            val created = result.value
            assertNotNull(created.request)
        }

    // =========================================================================
    // Test 7: Session created during request is retrievable
    // =========================================================================

    @Test
    fun authorizationSessionIsStoredAndRetrievable() =
        runTest {
            val graph = ctx.session.graph as Oid4vpPresentationTestGraph
            val verifier = graph.oid4vpVerifierService

            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(
                                id = "test_credential",
                                format = "dc+sd-jwt",
                                meta = sdJwtVcMeta("urn:test:credential"),
                            ),
                        ),
                )

            val result =
                verifier.createAuthorizationRequest(
                    CreateAuthorizationRequestArgs(
                        instanceId = "oid4vc-integration-verifier",
                        dcqlQuery = dcqlQuery,
                        clientId = verifierClientId,
                        responseUri = responseUri,
                        responseMode = ResponseMode.DIRECT_POST,
                        nonce = "session-test-nonce",
                        state = "session-test-state",
                    ),
                )

            assertTrue(result.isOk, "Request creation should succeed")
            val created = result.value
            val sessionId = created.sessionId
            assertNotNull(sessionId, "Session ID should be present")

            // Verify the session can be retrieved from the store via correlation ID
            val sessionResult = verifier.authorizationSessionStore.getByCorrelationId(sessionId)
            assertTrue(sessionResult.isOk, "Session retrieval should succeed")
            val session = sessionResult.value
            assertNotNull(session, "Session should be retrievable from the store")
            assertEquals(sessionId, session.correlationId)
            assertEquals("session-test-nonce", session.authorizationRequest.nonce)
            assertEquals("session-test-state", session.authorizationRequest.state)
        }
}
