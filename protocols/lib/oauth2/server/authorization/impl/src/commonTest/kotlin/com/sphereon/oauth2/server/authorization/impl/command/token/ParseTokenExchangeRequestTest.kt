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

package com.sphereon.oauth2.server.authorization.impl.command.token

import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestArgs
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParseTokenExchangeRequestTest {
    private val ctx = OAuth2ServerTestContext("parse-token-exchange-test", this)
    private val command = ParseTokenRequestCommandImpl(ctx.execution)

    private val tokenExchangeGrantType = "urn:ietf:params:oauth:grant-type:token-exchange"
    private val accessTokenType = "urn:ietf:params:oauth:token-type:access_token"
    private val jwtTokenType = "urn:ietf:params:oauth:token-type:jwt"

    @Test
    fun parseValidTokenExchangeImpersonationRequest() =
        runTest {
            // Given - impersonation: subject_token only, no actor
            val requestBody =
                mapOf(
                    "grant_type" to listOf(tokenExchangeGrantType),
                    "subject_token" to listOf("eyJhbGciOiJSUzI1NiJ9.subject-token-content"),
                    "subject_token_type" to listOf(accessTokenType),
                    "client_id" to listOf("client123"),
                )

            // When
            val result = command.execute(ParseTokenRequestArgs(requestBody, emptyMap()))

            // Then
            assertTrue(result.isOk)
            val request = result.value
            assertEquals(GrantType.TOKEN_EXCHANGE, request.grantType)
            assertEquals("client123", request.clientId)

            assertTrue(request.grantParameters is GrantParameters.TokenExchange)
            val grantParams = request.grantParameters as GrantParameters.TokenExchange
            assertEquals("eyJhbGciOiJSUzI1NiJ9.subject-token-content", grantParams.subjectToken)
            assertEquals(accessTokenType, grantParams.subjectTokenType)
            assertNull(grantParams.actorToken)
            assertNull(grantParams.actorTokenType)
            assertTrue(grantParams.resources.isEmpty())
            assertTrue(grantParams.audiences.isEmpty())
            assertNull(grantParams.scope)
            assertNull(grantParams.requestedTokenType)
        }

    @Test
    fun parseValidTokenExchangeDelegationRequest() =
        runTest {
            // Given - delegation: subject_token + actor_token
            val requestBody =
                mapOf(
                    "grant_type" to listOf(tokenExchangeGrantType),
                    "subject_token" to listOf("eyJhbGciOiJSUzI1NiJ9.subject-token"),
                    "subject_token_type" to listOf(accessTokenType),
                    "actor_token" to listOf("eyJhbGciOiJSUzI1NiJ9.actor-token"),
                    "actor_token_type" to listOf(accessTokenType),
                    "client_id" to listOf("client123"),
                )

            // When
            val result = command.execute(ParseTokenRequestArgs(requestBody, emptyMap()))

            // Then
            assertTrue(result.isOk)
            val request = result.value
            assertEquals(GrantType.TOKEN_EXCHANGE, request.grantType)

            assertTrue(request.grantParameters is GrantParameters.TokenExchange)
            val grantParams = request.grantParameters as GrantParameters.TokenExchange
            assertEquals("eyJhbGciOiJSUzI1NiJ9.subject-token", grantParams.subjectToken)
            assertEquals(accessTokenType, grantParams.subjectTokenType)
            assertEquals("eyJhbGciOiJSUzI1NiJ9.actor-token", grantParams.actorToken)
            assertEquals(accessTokenType, grantParams.actorTokenType)
        }

    @Test
    fun parseTokenExchangeWithMultiValueResourceParameters() =
        runTest {
            // Given - resource=A&resource=B produces listOf("A", "B")
            val requestBody =
                mapOf(
                    "grant_type" to listOf(tokenExchangeGrantType),
                    "subject_token" to listOf("eyJhbGciOiJSUzI1NiJ9.subject-token"),
                    "subject_token_type" to listOf(accessTokenType),
                    "resource" to listOf("https://api.example.com/v1", "https://api.example.com/v2"),
                    "client_id" to listOf("client123"),
                )

            // When
            val result = command.execute(ParseTokenRequestArgs(requestBody, emptyMap()))

            // Then
            assertTrue(result.isOk)
            val request = result.value

            assertTrue(request.grantParameters is GrantParameters.TokenExchange)
            val grantParams = request.grantParameters as GrantParameters.TokenExchange
            assertEquals(2, grantParams.resources.size)
            assertEquals("https://api.example.com/v1", grantParams.resources[0])
            assertEquals("https://api.example.com/v2", grantParams.resources[1])
        }

    @Test
    fun parseTokenExchangeWithMultiValueAudienceParameters() =
        runTest {
            // Given - audience=A&audience=B produces listOf("A", "B")
            val requestBody =
                mapOf(
                    "grant_type" to listOf(tokenExchangeGrantType),
                    "subject_token" to listOf("eyJhbGciOiJSUzI1NiJ9.subject-token"),
                    "subject_token_type" to listOf(accessTokenType),
                    "audience" to listOf("aud-service-a", "aud-service-b"),
                    "client_id" to listOf("client123"),
                )

            // When
            val result = command.execute(ParseTokenRequestArgs(requestBody, emptyMap()))

            // Then
            assertTrue(result.isOk)
            val request = result.value

            assertTrue(request.grantParameters is GrantParameters.TokenExchange)
            val grantParams = request.grantParameters as GrantParameters.TokenExchange
            assertEquals(2, grantParams.audiences.size)
            assertEquals("aud-service-a", grantParams.audiences[0])
            assertEquals("aud-service-b", grantParams.audiences[1])
        }

    @Test
    fun parseTokenExchangeWithRequestedTokenType() =
        runTest {
            // Given
            val requestBody =
                mapOf(
                    "grant_type" to listOf(tokenExchangeGrantType),
                    "subject_token" to listOf("eyJhbGciOiJSUzI1NiJ9.subject-token"),
                    "subject_token_type" to listOf(accessTokenType),
                    "requested_token_type" to listOf(jwtTokenType),
                    "scope" to listOf("openid profile"),
                    "client_id" to listOf("client123"),
                )

            // When
            val result = command.execute(ParseTokenRequestArgs(requestBody, emptyMap()))

            // Then
            assertTrue(result.isOk)
            val request = result.value

            assertTrue(request.grantParameters is GrantParameters.TokenExchange)
            val grantParams = request.grantParameters as GrantParameters.TokenExchange
            assertEquals(jwtTokenType, grantParams.requestedTokenType)
            assertEquals("openid profile", grantParams.scope)
        }

    @Test
    fun parseMissingSubjectTokenStillParses() =
        runTest {
            // Given - subject_token is missing; validation is done later, not at parse time
            val requestBody =
                mapOf(
                    "grant_type" to listOf(tokenExchangeGrantType),
                    "subject_token_type" to listOf(accessTokenType),
                    "client_id" to listOf("client123"),
                )

            // When
            val result = command.execute(ParseTokenRequestArgs(requestBody, emptyMap()))

            // Then
            assertTrue(result.isOk)
            val request = result.value
            assertEquals(GrantType.TOKEN_EXCHANGE, request.grantType)

            assertTrue(request.grantParameters is GrantParameters.TokenExchange)
            val grantParams = request.grantParameters as GrantParameters.TokenExchange
            assertEquals("", grantParams.subjectToken)
        }

    @Test
    fun parseMissingSubjectTokenTypeStillParses() =
        runTest {
            // Given - subject_token_type is missing; validation is done later, not at parse time
            val requestBody =
                mapOf(
                    "grant_type" to listOf(tokenExchangeGrantType),
                    "subject_token" to listOf("eyJhbGciOiJSUzI1NiJ9.subject-token"),
                    "client_id" to listOf("client123"),
                )

            // When
            val result = command.execute(ParseTokenRequestArgs(requestBody, emptyMap()))

            // Then
            assertTrue(result.isOk)
            val request = result.value
            assertEquals(GrantType.TOKEN_EXCHANGE, request.grantType)

            assertTrue(request.grantParameters is GrantParameters.TokenExchange)
            val grantParams = request.grantParameters as GrantParameters.TokenExchange
            assertEquals("", grantParams.subjectTokenType)
        }
}
