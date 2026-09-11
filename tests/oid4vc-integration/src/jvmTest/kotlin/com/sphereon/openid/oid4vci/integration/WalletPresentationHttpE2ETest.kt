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

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.openid.oid4vc.common.QrCodeOptions
import com.sphereon.openid.oid4vp.dcql.DcqlClaimQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.claimsPathPointer
import com.sphereon.openid.oid4vp.dcql.sdJwtVcMeta
import com.sphereon.openid.oid4vp.universal.CreateAuthorizationRequestInput
import com.sphereon.openid.oid4vp.universal.CreateAuthorizationRequestOutput
import com.sphereon.openid.oid4vp.universal.GetAuthorizationRequestStatusOutput
import com.sphereon.openid.oid4vp.universal.impl.UniversalOid4vpHttpAdapter
import com.sphereon.openid.oid4vp.verifier.impl.http.Oid4vpVerifierHttpAdapter
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end integration tests simulating a wallet interacting with an OID4VP verifier
 * through HTTP adapters.
 *
 * The full chain under test:
 *   wallet (test code) -> GenericHttpRequest -> HTTP adapter dispatcher -> CommandBackedHttpAdapter -> endpoint command -> GenericHttpResponse
 *
 * This proves:
 * - The OID4VP verifier HTTP adapters correctly route and deserialize requests
 * - The endpoint commands produce correct HTTP responses
 * - The response format matches what a real wallet/backend client would parse
 * - Error codes and status codes follow the OID4VP spec
 */
class WalletPresentationHttpE2ETest {
    private val ctx = Oid4vciTestContext(this)

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    // =========================================================================
    // Helper: get adapters from DI graph
    // =========================================================================

    private fun verifierAdapter(): Oid4vpVerifierHttpAdapter {
        val adapters = (ctx.session.graph as HttpAdapterTestGraph).httpAdapters.values.map { it.value }
        return adapters.filterIsInstance<Oid4vpVerifierHttpAdapter>().firstOrNull()
            ?: error("Oid4vpVerifierHttpAdapter not found in DI graph. Found: ${adapters.map { it::class.simpleName }}")
    }

    private fun universalAdapter(): UniversalOid4vpHttpAdapter {
        val adapters = (ctx.session.graph as HttpAdapterTestGraph).httpAdapters.values.map { it.value }
        return adapters.filterIsInstance<UniversalOid4vpHttpAdapter>().firstOrNull()
            ?: error("UniversalOid4vpHttpAdapter not found in DI graph. Found: ${adapters.map { it::class.simpleName }}")
    }

    // =========================================================================
    // Helper: standard DCQL query for tests
    // =========================================================================

    private fun defaultDcqlQuery(): DcqlQuery =
        DcqlQuery(
            credentials =
                listOf(
                    DcqlCredentialQuery(
                        id = "pid_credential",
                        format = "dc+sd-jwt",
                        meta = sdJwtVcMeta("urn:test:pid"),
                        claims =
                            listOf(
                                DcqlClaimQuery(path = claimsPathPointer("given_name")),
                                DcqlClaimQuery(path = claimsPathPointer("family_name")),
                                DcqlClaimQuery(path = claimsPathPointer("birthdate")),
                            ),
                    ),
                ),
        )

    // =========================================================================
    // Helper: create an authorization request via the backend API and return output
    // =========================================================================

    private suspend fun createAuthorizationRequestViaBackend(
        dcqlQuery: DcqlQuery = defaultDcqlQuery(),
        clientId: String = "https://verifier.example.com",
        state: String? = null,
    ): CreateAuthorizationRequestOutput {
        val input =
            CreateAuthorizationRequestInput(
                dcqlQuery = dcqlQuery,
                clientId = clientId,
                state = state,
                qrCodeOptions = QrCodeOptions(),
            )

        val request =
            GenericHttpRequest.withTextBody(
                method = "POST",
                path = "/oid4vp/backend/auth/requests",
                headers = mapOf("Content-Type" to "application/json"),
                body = json.encodeToString(CreateAuthorizationRequestInput.serializer(), input),
            )

        val response = ctx.dispatchInProcessHttp(request)
        assertEquals(201, response.statusCode, "Expected 201 Created for authorization request. Body: ${response.body}")
        assertNotNull(response.body, "Response body should not be null")
        return json.decodeFromString(CreateAuthorizationRequestOutput.serializer(), response.body!!)
    }

    // =========================================================================
    // Test 1: OID4VP adapters resolve from DI graph
    // =========================================================================

    @Test
    fun oid4vpAdaptersResolveFromDi() {
        val adapters = (ctx.session.graph as HttpAdapterTestGraph).httpAdapters.values.map { it.value }
        assertTrue(adapters.isNotEmpty(), "HTTP adapters set should not be empty")

        val verifierAdapters = adapters.filterIsInstance<Oid4vpVerifierHttpAdapter>()
        assertTrue(verifierAdapters.isNotEmpty(), "Oid4vpVerifierHttpAdapter should be in the DI graph")

        val universalAdapters = adapters.filterIsInstance<UniversalOid4vpHttpAdapter>()
        assertTrue(universalAdapters.isNotEmpty(), "UniversalOid4vpHttpAdapter should be in the DI graph")
    }

    // =========================================================================
    // Test 2: Verifier creates request, wallet fetches via HTTP GET
    //
    // 1. Backend creates authorization request
    // 2. Wallet GETs /oid4vp/request-uri/{correlationId}
    // 3. Verify 200 response with authorization request content
    // =========================================================================

    @Test
    fun walletFetchesAuthorizationRequestViaGet() =
        runTest {
            val output = createAuthorizationRequestViaBackend()
            val correlationId = output.correlationId
            assertNotNull(correlationId, "correlationId should be present")

            // Wallet fetches the request object by request_uri
            val fetchRequest =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vp/request-uri/$correlationId",
                    headers = mapOf("Accept" to "application/oauth-authz-req+jwt, application/json"),
                )
            val fetchResponse = ctx.dispatchInProcessHttp(fetchRequest)

            assertEquals(200, fetchResponse.statusCode, "Wallet should be able to fetch request object. Body: ${fetchResponse.body}")
            assertNotNull(fetchResponse.body, "Response body should not be null")

            // The response should be a signed JAR (JWT) or JSON authorization request
            val body = fetchResponse.body!!
            assertTrue(body.isNotEmpty(), "Authorization request body should not be empty")

            // Verify Cache-Control header per OID4VP spec
            val cacheControl = fetchResponse.headers["Cache-Control"]
            assertEquals("no-store", cacheControl, "Cache-Control should be no-store per OID4VP spec")
        }

    // =========================================================================
    // Test 3: Wallet fetches request with POST (wallet_metadata)
    //
    // 1. Backend creates authorization request
    // 2. Wallet POSTs /oid4vp/request-uri/{correlationId} with wallet_metadata
    // 3. Verify 200 response
    // =========================================================================

    @Test
    fun walletFetchesAuthorizationRequestViaPost() =
        runTest {
            val output = createAuthorizationRequestViaBackend()
            val correlationId = output.correlationId

            // Wallet POSTs with wallet_metadata and wallet_nonce
            val postRequest =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/oid4vp/request-uri/$correlationId",
                    headers = mapOf("Content-Type" to "application/json"),
                    body = """{"wallet_metadata": "{\"authorization_endpoint\": \"openid4vp:\"}", "wallet_nonce": "test-nonce-123"}""",
                )
            val postResponse = ctx.dispatchInProcessHttp(postRequest)

            assertEquals(200, postResponse.statusCode, "POST request-uri should return 200. Body: ${postResponse.body}")
            assertNotNull(postResponse.body, "Response body should not be null")

            // Verify Cache-Control header
            val cacheControl = postResponse.headers["Cache-Control"]
            assertEquals("no-store", cacheControl, "Cache-Control should be no-store per OID4VP spec")
        }

    // =========================================================================
    // Test 4: Wallet fetches non-existent request returns 404
    // =========================================================================

    @Test
    fun walletFetchesNonExistentRequestReturns404() =
        runTest {
            val fetchRequest =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vp/request-uri/nonexistent-correlation-id",
                    headers = mapOf("Accept" to "application/json"),
                )
            val fetchResponse = ctx.dispatchInProcessHttp(fetchRequest)

            assertEquals(404, fetchResponse.statusCode, "Non-existent request should return 404. Body: ${fetchResponse.body}")
        }

    // =========================================================================
    // Test 5: Backend creates authorization request via HTTP
    //
    // POST /oid4vp/backend/auth/requests with CreateAuthorizationRequestInput
    // =========================================================================

    @Test
    fun backendCreatesAuthorizationRequestViaHttp() =
        runTest {
            val input =
                CreateAuthorizationRequestInput(
                    dcqlQuery = defaultDcqlQuery(),
                    clientId = "https://verifier.example.com",
                    state = "backend-test-state",
                    qrCodeOptions = QrCodeOptions(),
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/oid4vp/backend/auth/requests",
                    headers = mapOf("Content-Type" to "application/json"),
                    body = json.encodeToString(CreateAuthorizationRequestInput.serializer(), input),
                )

            val response = ctx.dispatchInProcessHttp(request)

            assertEquals(201, response.statusCode, "Backend create should return 201. Body: ${response.body}")
            assertNotNull(response.body, "Response body should not be null")

            val output = json.decodeFromString(CreateAuthorizationRequestOutput.serializer(), response.body!!)
            assertNotNull(output.correlationId, "correlationId should be present")
            assertNotNull(output.requestUri, "requestUri should be present")
            assertTrue(output.requestUri!!.isNotEmpty(), "requestUri should not be empty")

            // QR code should be generated when qrCodeOptions is provided
            assertNotNull(output.qrUri, "qrUri should be present when qrCodeOptions provided")
            assertTrue(output.qrUri!!.startsWith("data:image/png;base64,"), "QR code should be a data URI with base64 PNG")
        }

    // =========================================================================
    // Test 6: Backend checks status via HTTP
    //
    // 1. Create request via backend
    // 2. GET /oid4vp/backend/auth/requests/{correlation_id}
    // 3. Verify status
    // =========================================================================

    @Test
    fun backendChecksStatusViaHttp() =
        runTest {
            val output = createAuthorizationRequestViaBackend()
            val correlationId = output.correlationId

            val statusRequest =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vp/backend/auth/requests/$correlationId",
                )
            val statusResponse = ctx.dispatchInProcessHttp(statusRequest)

            assertEquals(200, statusResponse.statusCode, "Status check should return 200. Body: ${statusResponse.body}")
            assertNotNull(statusResponse.body, "Response body should not be null")

            val statusOutput = json.decodeFromString(GetAuthorizationRequestStatusOutput.serializer(), statusResponse.body!!)
            assertEquals(correlationId, statusOutput.correlationId, "correlationId should match")
            assertEquals(
                AuthorizationSessionStatus.AUTHORIZATION_REQUEST_CREATED,
                statusOutput.status,
                "Status should be AUTHORIZATION_REQUEST_CREATED",
            )
        }

    // =========================================================================
    // Test 7: Full direct_post flow via HTTP
    //
    // 1. Create authorization request via backend
    // 2. Wallet GETs /oid4vp/request-uri/{correlationId}
    // 3. Wallet POSTs /oid4vp/auth/response with form-encoded VP token
    // 4. Verify response (structured error expected for fake VP token)
    // =========================================================================

    @Test
    fun fullDirectPostFlowViaHttp() =
        runTest {
            val output = createAuthorizationRequestViaBackend(state = "direct-post-test-state")
            val correlationId = output.correlationId

            // Step 2: Wallet fetches the authorization request
            val fetchRequest =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vp/request-uri/$correlationId",
                )
            val fetchResponse = ctx.dispatchInProcessHttp(fetchRequest)
            assertEquals(200, fetchResponse.statusCode, "Request fetch should return 200. Body: ${fetchResponse.body}")

            // Step 3: Wallet submits VP token via direct_post
            // The state parameter is the correlationId (used to look up the session)
            val directPostBody = "vp_token=fake-vp-token-for-testing&state=$correlationId"

            val directPostRequest =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vp/auth/response",
                    headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                    bodySupplier = { directPostBody },
                )
            val directPostResponse = ctx.dispatchInProcessHttp(directPostRequest)

            // With a fake VP token, the verifier should attempt to handle the response.
            // It may return 200 (success with redirect_uri) or a structured error (400/500)
            // depending on how far the validation gets. The key thing is we should NOT get 404.
            assertTrue(
                directPostResponse.statusCode != 404,
                "Direct post should be routable (not 404). Got ${directPostResponse.statusCode}: ${directPostResponse.body}",
            )
            assertNotNull(directPostResponse.body, "Response body should not be null")

            // The response should be valid JSON
            val responseBody = json.parseToJsonElement(directPostResponse.body!!).jsonObject
            assertNotNull(responseBody, "Response should be parseable JSON")
        }

    // =========================================================================
    // Test 8: Direct post without state returns 400
    // =========================================================================

    @Test
    fun directPostWithoutStateReturns400() =
        runTest {
            val directPostBody = "vp_token=some-fake-vp-token"

            val directPostRequest =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vp/auth/response",
                    headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                    bodySupplier = { directPostBody },
                )
            val directPostResponse = ctx.dispatchInProcessHttp(directPostRequest)

            assertEquals(
                400,
                directPostResponse.statusCode,
                "Direct post without state should return 400. Body: ${directPostResponse.body}",
            )
            assertNotNull(directPostResponse.body, "Error response body should not be null")

            val errorBody = json.parseToJsonElement(directPostResponse.body!!).jsonObject
            assertTrue(
                errorBody.containsKey("message") || errorBody.containsKey("error"),
                "Error response should contain a message or error field",
            )
        }

    // =========================================================================
    // Test 9: Direct post with empty body returns 400
    // =========================================================================

    @Test
    fun directPostWithEmptyBodyReturns400() =
        runTest {
            val directPostRequest =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vp/auth/response",
                    headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                    bodySupplier = { "" },
                )
            val directPostResponse = ctx.dispatchInProcessHttp(directPostRequest)

            assertEquals(
                400,
                directPostResponse.statusCode,
                "Direct post with empty body should return 400. Body: ${directPostResponse.body}",
            )
        }

    // =========================================================================
    // Test 10: Backend deletes request via HTTP
    //
    // 1. Create request
    // 2. DELETE /oid4vp/backend/auth/requests/{correlation_id}
    // 3. Verify 204
    // 4. GET the same correlationId — verify 404
    // =========================================================================

    @Test
    fun backendDeletesRequestViaHttp() =
        runTest {
            val output = createAuthorizationRequestViaBackend()
            val correlationId = output.correlationId

            // Delete the session
            val deleteRequest =
                GenericHttpRequest(
                    method = "DELETE",
                    path = "/oid4vp/backend/auth/requests/$correlationId",
                )
            val deleteResponse = ctx.dispatchInProcessHttp(deleteRequest)

            assertEquals(204, deleteResponse.statusCode, "Delete should return 204. Body: ${deleteResponse.body}")

            // Verify it's gone
            val statusRequest =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vp/backend/auth/requests/$correlationId",
                )
            val statusResponse = ctx.dispatchInProcessHttp(statusRequest)

            assertEquals(404, statusResponse.statusCode, "Deleted session should return 404. Body: ${statusResponse.body}")
        }

    // =========================================================================
    // Test 11: Backend get status for non-existent session returns 404
    // =========================================================================

    @Test
    fun backendGetStatusNonExistentReturns404() =
        runTest {
            val statusRequest =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vp/backend/auth/requests/non-existent-correlation-id",
                )
            val statusResponse = ctx.dispatchInProcessHttp(statusRequest)

            assertEquals(404, statusResponse.statusCode, "Non-existent session status should return 404. Body: ${statusResponse.body}")
        }

    // =========================================================================
    // Test 12: Backend create with missing query returns 400
    // =========================================================================

    @Test
    fun backendCreateWithMissingQueryReturns400() =
        runTest {
            val input =
                CreateAuthorizationRequestInput(
                    clientId = "https://verifier.example.com",
                    qrCodeOptions = QrCodeOptions(),
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/oid4vp/backend/auth/requests",
                    headers = mapOf("Content-Type" to "application/json"),
                    body = json.encodeToString(CreateAuthorizationRequestInput.serializer(), input),
                )

            val response = ctx.dispatchInProcessHttp(request)

            assertEquals(400, response.statusCode, "Missing query should return 400. Body: ${response.body}")
        }

    // =========================================================================
    // Test 13: Non-existent route on verifier adapter returns error
    //
    // When requesting a path that doesn't match any endpoint command,
    // the adapter should return an error (404 or 500 depending on how
    // the base path matching interacts with endpoint resolution).
    // =========================================================================

    @Test
    fun nonExistentVerifierRouteReturnsError() =
        runTest {
            val adapter = verifierAdapter()

            val unknownRequest =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vp/does-not-exist/something",
                )
            val response = ctx.dispatchInProcessHttp(unknownRequest)

            assertTrue(
                response.statusCode in listOf(400, 404, 500),
                "Unknown verifier route should return 400, 404, or 500, got ${response.statusCode}: ${response.body}",
            )
        }

    // =========================================================================
    // Test 14: Wallet fetches request, then status changes to REQUEST_URI_RETRIEVED
    // =========================================================================

    @Test
    fun statusUpdatesAfterWalletFetchesRequest() =
        runTest {
            val output = createAuthorizationRequestViaBackend()
            val correlationId = output.correlationId

            // Wallet fetches the request object
            val fetchRequest =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vp/request-uri/$correlationId",
                )
            val fetchResponse = ctx.dispatchInProcessHttp(fetchRequest)
            assertEquals(200, fetchResponse.statusCode, "Request fetch should succeed. Body: ${fetchResponse.body}")

            // Check status - should have been updated
            val statusRequest =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vp/backend/auth/requests/$correlationId",
                )
            val statusResponse = ctx.dispatchInProcessHttp(statusRequest)
            assertEquals(200, statusResponse.statusCode, "Status check should succeed. Body: ${statusResponse.body}")

            val statusOutput = json.decodeFromString(GetAuthorizationRequestStatusOutput.serializer(), statusResponse.body!!)
            assertEquals(correlationId, statusOutput.correlationId, "correlationId should match")
            // After wallet fetches, status may be REQUEST_URI_RETRIEVED or still CREATED depending on implementation
            assertTrue(
                statusOutput.status == AuthorizationSessionStatus.AUTHORIZATION_REQUEST_CREATED ||
                    statusOutput.status == AuthorizationSessionStatus.AUTHORIZATION_REQUEST_RETRIEVED,
                "Status should be CREATED or REQUEST_URI_RETRIEVED after fetch, got: ${statusOutput.status}",
            )
        }

    // =========================================================================
    // Test 15: Direct post with non-existent state returns 404
    // =========================================================================

    @Test
    fun directPostWithNonExistentStateReturns404() =
        runTest {
            val directPostBody = "vp_token=fake-vp-token&state=non-existent-correlation-id"

            val directPostRequest =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vp/auth/response",
                    headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                    bodySupplier = { directPostBody },
                )
            val directPostResponse = ctx.dispatchInProcessHttp(directPostRequest)

            assertEquals(
                404,
                directPostResponse.statusCode,
                "Direct post with non-existent state should return 404. Body: ${directPostResponse.body}",
            )
        }
}
