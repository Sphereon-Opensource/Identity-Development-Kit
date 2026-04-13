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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenTypeIdentifier
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantArgs
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.TestFixtures
import com.sphereon.oauth2.server.authorization.impl.policy.DefaultTokenExchangePolicy
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryClientRegistryImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.policy.TokenExchangePolicy
import com.sphereon.oauth2.server.authorization.policy.TokenExchangePolicyDecision
import com.sphereon.oauth2.server.authorization.policy.TokenExchangePolicyRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for VerifyTokenExchangeGrantCommandImpl (RFC 8693)
 *
 * Uses DI-injected JwtService from the test context for JWT verification.
 * Test JWTs are structurally valid but unsigned — the command handles
 * verification failures gracefully (external tokens without signing keys).
 */
class VerifyTokenExchangeGrantCommandImplTest {
    private val ctx = OAuth2ServerTestContext("verify-token-exchange-test", this)
    private val execution = ctx.execution
    private val jwtService: JwtService = (ctx.session.graph as JwtServiceImpl.Graph).jwtService

    // Test client authorized for TOKEN_EXCHANGE
    private val tokenExchangeClient =
        TestFixtures.confidentialClient.copy(
            clientId = "token-exchange-client",
            grantTypes =
                listOf(
                    GrantType.AUTHORIZATION_CODE,
                    GrantType.TOKEN_EXCHANGE,
                ),
        )

    // Test client NOT authorized for TOKEN_EXCHANGE
    private val unauthorizedClient =
        TestFixtures.confidentialClient.copy(
            clientId = "no-exchange-client",
            grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
        )

    /**
     * Create a test JWT with the given claims payload.
     * The header and signature are minimal but structurally valid (3 dot-separated base64url parts).
     */
    private fun createTestJwt(claims: Map<String, Any>): String {
        val header = encodeBase64Url("""{"alg":"RS256","typ":"JWT"}""")
        val payloadJson =
            buildString {
                append("{")
                claims.entries.forEachIndexed { index, (key, value) ->
                    if (index > 0) append(",")
                    append("\"$key\":")
                    when (value) {
                        is String -> {
                            append("\"$value\"")
                        }

                        is Number -> {
                            append(value)
                        }

                        is Boolean -> {
                            append(value)
                        }

                        is List<*> -> {
                            append("[")
                            value.forEachIndexed { i, v ->
                                if (i > 0) append(",")
                                append("\"$v\"")
                            }
                            append("]")
                        }

                        else -> {
                            append("\"$value\"")
                        }
                    }
                }
                append("}")
            }
        val payload = encodeBase64Url(payloadJson)
        val signature = encodeBase64Url("test-signature")
        return "$header.$payload.$signature"
    }

    private fun encodeBase64Url(input: String): String {
        val bytes = input.encodeToByteArray()
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val result = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            result.append(table[b0 shr 2])
            if (i + 1 < bytes.size) {
                val b1 = bytes[i + 1].toInt() and 0xFF
                result.append(table[((b0 and 0x03) shl 4) or (b1 shr 4)])
                if (i + 2 < bytes.size) {
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    result.append(table[((b1 and 0x0F) shl 2) or (b2 shr 6)])
                    result.append(table[b2 and 0x3F])
                } else {
                    result.append(table[(b1 and 0x0F) shl 2])
                }
            } else {
                result.append(table[(b0 and 0x03) shl 4])
            }
            i += 3
        }
        return result.toString()
    }

    /**
     * Test policy that accepts unverified tokens.
     * Used because test JWTs have no real signing key, so JwtService can't verify them.
     * The DefaultTokenExchangePolicy would reject these — this policy skips that check
     * while preserving all other policy logic (may_act, delegation detection, etc.).
     */
    private val testPolicy =
        object : TokenExchangePolicy {
            private val delegate = DefaultTokenExchangePolicy()

            override suspend fun evaluate(request: TokenExchangePolicyRequest): IdkResult<TokenExchangePolicyDecision, AuthorizationServerError> {
                // Re-evaluate with verified=true so DefaultTokenExchangePolicy doesn't reject
                val adjusted =
                    request.copy(
                        subjectTokenVerified = true,
                        actorTokenVerified = if (request.actorTokenClaims != null) true else null,
                    )
                return delegate.evaluate(adjusted)
            }
        }

    private fun createCommand(
        clientRegistry: InMemoryClientRegistryImpl,
        policy: TokenExchangePolicy = testPolicy,
    ): VerifyTokenExchangeGrantCommandImpl =
        VerifyTokenExchangeGrantCommandImpl(
            execution = execution,
            clientRegistry = clientRegistry,
            tokenExchangePolicy = policy,
            jwtService = jwtService,
        )

    private suspend fun setupClientRegistry(vararg clients: ClientRegistration): InMemoryClientRegistryImpl {
        val storage = InMemoryOAuth2BackingStorageImpl()
        val registry = InMemoryClientRegistryImpl(storage)
        for (client in clients) {
            val result = registry.registerClient(client)
            assertTrue(result.isOk, "Failed to register client: ${client.clientId}")
        }
        return registry
    }

    // --- Impersonation flow tests ---

    @Test
    fun testImpersonationFlowWithJwtSubjectToken() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            val subjectJwt =
                createTestJwt(
                    mapOf(
                        "sub" to "user123",
                        "iss" to "https://idp.example.com",
                        "aud" to "https://api.example.com",
                    ),
                )

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = subjectJwt,
                        subjectTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                        actorToken = null,
                        actorTokenType = null,
                        resources = emptyList(),
                        audiences = emptyList(),
                        scope = "read",
                        requestedTokenType = null,
                        clientId = tokenExchangeClient.clientId,
                    ),
                )

            assertTrue(result.isOk, "Impersonation flow should succeed")
            val grant = result.value
            assertEquals("user123", grant.subject)
            assertEquals(tokenExchangeClient.clientId, grant.clientId)
            assertEquals(TokenTypeIdentifier.ACCESS_TOKEN, grant.issuedTokenType)
            assertNull(grant.actorClaim, "Impersonation should not have actor claim")
        }

    // --- Delegation flow tests ---

    @Test
    fun testDelegationFlowWithActorToken() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            val subjectJwt =
                createTestJwt(
                    mapOf(
                        "sub" to "user123",
                        "iss" to "https://idp.example.com",
                    ),
                )
            val actorJwt =
                createTestJwt(
                    mapOf(
                        "sub" to "service-a",
                        "iss" to "https://auth.internal.com",
                    ),
                )

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = subjectJwt,
                        subjectTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                        actorToken = actorJwt,
                        actorTokenType = TokenTypeIdentifier.JWT,
                        resources = emptyList(),
                        audiences = emptyList(),
                        scope = null,
                        requestedTokenType = null,
                        clientId = tokenExchangeClient.clientId,
                    ),
                )

            assertTrue(result.isOk, "Delegation flow should succeed")
            val grant = result.value
            assertEquals("user123", grant.subject)
            assertTrue(grant.isDelegation)
            assertEquals("service-a", grant.actorSubject)
            assertNotNull(grant.actorClaim, "Delegation should have actor claim")
            assertEquals("service-a", grant.actorClaim!!.sub)
        }

    // --- Validation error tests ---

    @Test
    fun testMissingSubjectTokenReturnsError() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = "",
                        subjectTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                        actorToken = null,
                        actorTokenType = null,
                        resources = emptyList(),
                        audiences = emptyList(),
                        scope = null,
                        requestedTokenType = null,
                        clientId = tokenExchangeClient.clientId,
                    ),
                )

            assertTrue(result.isErr, "Missing subject_token should fail")
        }

    @Test
    fun testMissingSubjectTokenTypeReturnsError() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            val subjectJwt = createTestJwt(mapOf("sub" to "user123"))

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = subjectJwt,
                        subjectTokenType = "",
                        actorToken = null,
                        actorTokenType = null,
                        resources = emptyList(),
                        audiences = emptyList(),
                        scope = null,
                        requestedTokenType = null,
                        clientId = tokenExchangeClient.clientId,
                    ),
                )

            assertTrue(result.isErr, "Missing subject_token_type should fail")
        }

    @Test
    fun testActorTokenWithoutActorTokenTypeReturnsError() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            val subjectJwt = createTestJwt(mapOf("sub" to "user123"))
            val actorJwt = createTestJwt(mapOf("sub" to "service-a"))

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = subjectJwt,
                        subjectTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                        actorToken = actorJwt,
                        actorTokenType = null,
                        resources = emptyList(),
                        audiences = emptyList(),
                        scope = null,
                        requestedTokenType = null,
                        clientId = tokenExchangeClient.clientId,
                    ),
                )

            assertTrue(result.isErr, "actor_token without actor_token_type should fail")
        }

    // --- Client authorization tests ---

    @Test
    fun testClientNotAuthorizedForTokenExchangeReturnsError() =
        runTest {
            val registry = setupClientRegistry(unauthorizedClient)
            val command = createCommand(registry)

            val subjectJwt = createTestJwt(mapOf("sub" to "user123"))

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = subjectJwt,
                        subjectTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                        actorToken = null,
                        actorTokenType = null,
                        resources = emptyList(),
                        audiences = emptyList(),
                        scope = null,
                        requestedTokenType = null,
                        clientId = unauthorizedClient.clientId,
                    ),
                )

            assertTrue(result.isErr, "Client without TOKEN_EXCHANGE grant should fail")
        }

    @Test
    fun testUnknownClientReturnsError() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            val subjectJwt = createTestJwt(mapOf("sub" to "user123"))

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = subjectJwt,
                        subjectTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                        actorToken = null,
                        actorTokenType = null,
                        resources = emptyList(),
                        audiences = emptyList(),
                        scope = null,
                        requestedTokenType = null,
                        clientId = "non-existent-client",
                    ),
                )

            assertTrue(result.isErr, "Unknown client should fail")
        }

    // --- Token type validation tests ---

    @Test
    fun testInvalidJwtFormatReturnsError() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = "not-a-jwt",
                        subjectTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                        actorToken = null,
                        actorTokenType = null,
                        resources = emptyList(),
                        audiences = emptyList(),
                        scope = null,
                        requestedTokenType = null,
                        clientId = tokenExchangeClient.clientId,
                    ),
                )

            assertTrue(result.isErr, "Invalid JWT format should fail")
        }

    @Test
    fun testSamlTokenTypeReturnsError() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = "some-saml-assertion",
                        subjectTokenType = TokenTypeIdentifier.SAML2,
                        actorToken = null,
                        actorTokenType = null,
                        resources = emptyList(),
                        audiences = emptyList(),
                        scope = null,
                        requestedTokenType = null,
                        clientId = tokenExchangeClient.clientId,
                    ),
                )

            assertTrue(result.isErr, "SAML token type should not be supported")
        }

    @Test
    fun testUnsupportedTokenTypeReturnsError() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = "some-token",
                        subjectTokenType = "urn:custom:token-type",
                        actorToken = null,
                        actorTokenType = null,
                        resources = emptyList(),
                        audiences = emptyList(),
                        scope = null,
                        requestedTokenType = null,
                        clientId = tokenExchangeClient.clientId,
                    ),
                )

            assertTrue(result.isErr, "Unsupported token type should fail")
        }

    @Test
    fun testRefreshTokenTypeReturnsMinimalClaims() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = "opaque-refresh-token-xyz",
                        subjectTokenType = TokenTypeIdentifier.REFRESH_TOKEN,
                        actorToken = null,
                        actorTokenType = null,
                        resources = emptyList(),
                        audiences = emptyList(),
                        scope = null,
                        requestedTokenType = null,
                        clientId = tokenExchangeClient.clientId,
                    ),
                )

            // Opaque refresh token has no "sub" claim, so subject falls back to clientId
            assertTrue(result.isOk, "Refresh token type should succeed")
            assertEquals(tokenExchangeClient.clientId, result.value.subject)
        }

    // --- Resource and audience pass-through tests ---

    @Test
    fun testResourcesAndAudiencesPassedToResult() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            val subjectJwt = createTestJwt(mapOf("sub" to "user123"))

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = subjectJwt,
                        subjectTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                        actorToken = null,
                        actorTokenType = null,
                        resources = listOf("https://api.example.com", "https://other.example.com"),
                        audiences = listOf("audience1"),
                        scope = "read write",
                        requestedTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                        clientId = tokenExchangeClient.clientId,
                    ),
                )

            assertTrue(result.isOk, "Should succeed")
            val grant = result.value
            assertEquals(listOf("https://api.example.com", "https://other.example.com"), grant.resource)
            assertEquals("read write", grant.scope)
        }

    // --- ID token type tests ---

    @Test
    fun testIdTokenTypeAccepted() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            val idToken =
                createTestJwt(
                    mapOf(
                        "sub" to "user456",
                        "iss" to "https://idp.example.com",
                        "aud" to "client-id",
                        "nonce" to "test-nonce",
                    ),
                )

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = idToken,
                        subjectTokenType = TokenTypeIdentifier.ID_TOKEN,
                        actorToken = null,
                        actorTokenType = null,
                        resources = emptyList(),
                        audiences = emptyList(),
                        scope = null,
                        requestedTokenType = null,
                        clientId = tokenExchangeClient.clientId,
                    ),
                )

            assertTrue(result.isOk, "ID token type should succeed")
            assertEquals("user456", result.value.subject)
        }

    // --- Default policy verification enforcement tests ---

    @Test
    fun testDefaultPolicyRejectsUnverifiedSubjectToken() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            // Use the real DefaultTokenExchangePolicy (not the test-permissive one)
            val command = createCommand(registry, policy = DefaultTokenExchangePolicy())

            val subjectJwt = createTestJwt(mapOf("sub" to "user123"))

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = subjectJwt,
                        subjectTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                        actorToken = null,
                        actorTokenType = null,
                        resources = emptyList(),
                        audiences = emptyList(),
                        scope = null,
                        requestedTokenType = null,
                        clientId = tokenExchangeClient.clientId,
                    ),
                )

            assertTrue(result.isErr, "Default policy should reject unverified subject tokens")
        }
}
