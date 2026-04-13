/*
 * © 2025 Sphereon International B.V.
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
import com.sphereon.openid.oid4vp.common.dcqlQuery
import com.sphereon.openid.oid4vp.dcql.DcqlClaimQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.impl.testutil.Oid4vpVerifierTestContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for CreateAuthorizationRequestCommandImpl
 */
class CreateAuthorizationRequestCommandImplTest {

    private val testContext = Oid4vpVerifierTestContext("create-auth-req-test", this)
    private val command = createTestCommand()

    @Test
    fun `test create basic authorization request`() = runTest {
        // Given: Basic DCQL query requesting identity credential
        val dcqlQuery = DcqlQuery(
            credentials = listOf(
                DcqlCredentialQuery(
                    id = "identity_credential",
                    format = "dc+sd-jwt",
                    claims = listOf(
                        DcqlClaimQuery(path = listOf("first_name")),
                        DcqlClaimQuery(path = listOf("last_name"))
                    )
                )
            )
        )

        val args = CreateAuthorizationRequestArgs(
            dcqlQuery = dcqlQuery,
            clientId = "https://verifier.example.com",
            responseUri = "https://verifier.example.com/response",
            responseMode = ResponseMode.DIRECT_POST,
            nonce = "nonce12345678",
            state = "state123"
        )

        // When: Creating the authorization request
        val result = command.createAuthorizationRequest(args)

        // Then: Request should be created successfully
        assertIs<Ok<*>>(result)
        val createdRequest = result.value

        assertEquals("https://verifier.example.com", createdRequest.request.clientId)
        assertEquals("vp_token", createdRequest.request.responseType)
        assertEquals("nonce12345678", createdRequest.request.nonce)
        assertEquals("direct_post", createdRequest.request.responseMode)
        assertEquals("state123", createdRequest.request.state)
        assertNotNull(createdRequest.sessionId)
        assertNotNull(createdRequest.request.dcqlQuery)
    }

    @Test
    fun `test create authorization request with client metadata`() = runTest {
        val dcqlQuery = DcqlQuery(
            credentials = listOf(
                DcqlCredentialQuery(
                    id = "mdoc_credential",
                    format = "mso_mdoc"
                )
            )
        )

        val args = CreateAuthorizationRequestArgs(
            dcqlQuery = dcqlQuery,
            clientId = "https://verifier.example.com",
            responseUri = "https://verifier.example.com/response",
            responseMode = ResponseMode.DIRECT_POST,
            nonce = "nonce12345678",
            clientIdScheme = ClientIdScheme.REDIRECT_URI
        )

        val result = command.createAuthorizationRequest(args)

        assertIs<Ok<*>>(result)
        val request = result.value.request
        assertEquals("redirect_uri", request.additionalParameters["client_id_scheme"]?.toString()?.replace("\"", ""))
    }

    @Test
    fun `test validation fails without nonce`() = runTest {
        val dcqlQuery = DcqlQuery(
            credentials = listOf(DcqlCredentialQuery(id = "test"))
        )

        val args = CreateAuthorizationRequestArgs(
            dcqlQuery = dcqlQuery,
            clientId = "https://verifier.example.com",
            responseUri = "https://verifier.example.com/response",
            responseMode = ResponseMode.DIRECT_POST,
            nonce = "" // Empty nonce
        )

        val result = command.createAuthorizationRequest(args)

        assertIs<Err<*>>(result)
        assertTrue(result.error.message.defaultMessage.contains("nonce"))
    }

    @Test
    fun `test validation fails with short nonce`() = runTest {
        val dcqlQuery = DcqlQuery(
            credentials = listOf(DcqlCredentialQuery(id = "test"))
        )

        val args = CreateAuthorizationRequestArgs(
            dcqlQuery = dcqlQuery,
            clientId = "https://verifier.example.com",
            responseUri = "https://verifier.example.com/response",
            responseMode = ResponseMode.DIRECT_POST,
            nonce = "short" // Too short (< 8 chars)
        )

        val result = command.createAuthorizationRequest(args)

        assertIs<Err<*>>(result)
        assertTrue(result.error.message.defaultMessage.contains("8 characters"))
    }

    @Test
    fun `test validation fails without response_uri for direct_post`() = runTest {
        val dcqlQuery = DcqlQuery(
            credentials = listOf(DcqlCredentialQuery(id = "test"))
        )

        val args = CreateAuthorizationRequestArgs(
            dcqlQuery = dcqlQuery,
            clientId = "https://verifier.example.com",
            responseUri = null, // Missing response_uri
            responseMode = ResponseMode.DIRECT_POST,
            nonce = "nonce12345678"
        )

        val result = command.createAuthorizationRequest(args)

        assertIs<Err<*>>(result)
        assertTrue(result.error.message.defaultMessage.contains("response_uri"))
    }

    @Test
    fun `test fragment response mode`() = runTest {
        val dcqlQuery = DcqlQuery(
            credentials = listOf(DcqlCredentialQuery(id = "test"))
        )

        val args = CreateAuthorizationRequestArgs(
            dcqlQuery = dcqlQuery,
            clientId = "https://verifier.example.com",
            redirectUri = "https://verifier.example.com/callback",
            responseMode = ResponseMode.FRAGMENT,
            nonce = "nonce12345678"
        )

        val result = command.createAuthorizationRequest(args)

        assertIs<Ok<*>>(result)
        val request = result.value.request
        assertEquals("fragment", request.responseMode)
    }

    @Test
    fun `test empty DCQL query fails validation`() = runTest {
        val dcqlQuery = DcqlQuery(
            credentials = emptyList(),
            credential_sets = null
        )

        val args = CreateAuthorizationRequestArgs(
            dcqlQuery = dcqlQuery,
            clientId = "https://verifier.example.com",
            responseUri = "https://verifier.example.com/response",
            responseMode = ResponseMode.DIRECT_POST,
            nonce = "nonce12345678"
        )

        val result = command.createAuthorizationRequest(args)

        assertIs<Err<*>>(result)
        assertTrue(result.error.message.defaultMessage.contains("credential"))
    }

    private fun createTestCommand(): CreateAuthorizationRequestCommandImpl {
        return CreateAuthorizationRequestCommandImpl(
            execution = testContext.execution,
            authorizationSessionStore = TestAuthorizationSessionStore()
        )
    }
}
