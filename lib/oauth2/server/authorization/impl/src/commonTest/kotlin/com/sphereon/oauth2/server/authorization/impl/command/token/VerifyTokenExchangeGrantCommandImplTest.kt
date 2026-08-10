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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.jose.jws.JwsJsonFlattened
import com.sphereon.crypto.jose.jws.JwsJsonGeneral
import com.sphereon.crypto.jose.jws.JwsJsonGeneralWithIdentifiers
import com.sphereon.crypto.jose.jws.JwsValidationResult
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.jose.jws.PreparedJwsObject
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenTypeIdentifier
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthorization
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantArgs
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.TestFixtures
import com.sphereon.oauth2.server.authorization.impl.policy.DefaultTokenExchangePolicy
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryClientRegistryImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemorySigningKeyStore
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.policy.TokenExchangePolicy
import com.sphereon.oauth2.server.authorization.policy.TokenExchangePolicyDecision
import com.sphereon.oauth2.server.authorization.policy.TokenExchangePolicyRequest
import com.sphereon.oauth2.server.authorization.signing.AsSigningKeyPublicJwkResolver
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

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
    private fun createTestJwt(
        claims: Map<String, Any>,
        kid: String? = null,
    ): String {
        val header =
            encodeBase64Url(
                if (kid == null) {
                    """{"alg":"RS256","typ":"JWT"}"""
                } else {
                    """{"alg":"RS256","typ":"JWT","kid":"$kid"}"""
                },
            )
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
        jwtService: JwtService = this.jwtService,
        signingKeyStore: SigningKeyStore = InMemorySigningKeyStore(),
        signingKeyPublicJwkResolver: AsSigningKeyPublicJwkResolver? = null,
    ): VerifyTokenExchangeGrantCommandImpl =
        VerifyTokenExchangeGrantCommandImpl(
            execution = execution,
            clientRegistry = clientRegistry,
            tokenExchangePolicy = policy,
            jwtService = jwtService,
            signingKeyStore = signingKeyStore,
            signingKeyPublicJwkResolver = signingKeyPublicJwkResolver,
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

    @Test
    fun locallyRegisteredKidUsesTypedResolverAndPinnedJwks() =
        runTest {
            val kid = "oauth2-as-token-exchange-test"
            val store = InMemorySigningKeyStore()
            assertTrue(store.register(signingKey(kid)).isOk)
            var resolverCalls = 0
            val resolver =
                object : AsSigningKeyPublicJwkResolver {
                    override suspend fun resolve(signingKey: OAuth2SigningKey): Jwk? =
                        publicJwk(signingKey.kid).also { resolverCalls++ }
                }
            val recordingJwtService = RecordingJwtService()
            val command =
                createCommand(
                    clientRegistry = setupClientRegistry(tokenExchangeClient),
                    policy = DefaultTokenExchangePolicy(),
                    jwtService = recordingJwtService,
                    signingKeyStore = store,
                    signingKeyPublicJwkResolver = resolver,
                )

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = createTestJwt(mapOf("sub" to "user123"), kid = kid),
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

            assertTrue(result.isOk, "A locally-issued token must verify through its typed public resolver")
            assertEquals(1, resolverCalls)
            val trustedJwks = assertNotNull(recordingJwtService.verifyArgs.single().trustedJwks)
            val trustedKeys = assertNotNull(trustedJwks["keys"]).jsonArray
            assertEquals(1, trustedKeys.size)
            assertEquals(kid, trustedKeys.single().jsonObject["kid"]?.jsonPrimitive?.content)
        }

    @Test
    fun unknownKidRetainsGenericJwtVerificationFallback() =
        runTest {
            val store = InMemorySigningKeyStore()
            var resolverCalls = 0
            val resolver =
                object : AsSigningKeyPublicJwkResolver {
                    override suspend fun resolve(signingKey: OAuth2SigningKey): Jwk? =
                        publicJwk(signingKey.kid).also { resolverCalls++ }
                }
            val recordingJwtService = RecordingJwtService()
            val command =
                createCommand(
                    clientRegistry = setupClientRegistry(tokenExchangeClient),
                    policy = DefaultTokenExchangePolicy(),
                    jwtService = recordingJwtService,
                    signingKeyStore = store,
                    signingKeyPublicJwkResolver = resolver,
                )

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = createTestJwt(mapOf("sub" to "external-user"), kid = "external-idp-key"),
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

            assertTrue(result.isOk, "Unknown external kids keep the existing JWT verification path")
            assertEquals(0, resolverCalls)
            assertNull(recordingJwtService.verifyArgs.single().trustedJwks, "External tokens must not be pinned to tenant AS keys")
        }

    private fun signingKey(kid: String): OAuth2SigningKey {
        val now = Clock.System.now()
        return OAuth2SigningKey(
            tenantId = execution.tenantId,
            keyInfo =
                KeyInfo<KeyType>(
                    kid = kid,
                    alias = kid,
                    providerId = "tenant-kms",
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                ),
            state = OAuth2SigningKeyState.ACTIVE,
            priority = 1,
            createdAt = now,
            notBefore = now,
        )
    }

    private fun publicJwk(kid: String): Jwk =
        Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "x-coordinate",
            y = "y-coordinate",
            kid = kid,
        )

    // --- Impersonation flow tests ---

    @Test
    fun testImpersonationFlowWithJwtSubjectToken() =
        runTest {
            val registry = setupClientRegistry()
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
                command.verifyWithTrustedClientAuthorization(
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
                    VerifiedClientAuthorization(
                        clientId = tokenExchangeClient.clientId,
                        grantTypes = tokenExchangeClient.grantTypes,
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

            // Subject token already holds the requested scopes, so they pass through.
            val subjectJwt = createTestJwt(mapOf("sub" to "user123", "scope" to "read write admin"))

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

    @Test
    fun testScopeIsDownscopedToSubjectToken() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            // Subject only holds "read"; requesting "read write admin" must not
            // mint scopes the subject never had (RFC 8693 downscope-only).
            val subjectJwt = createTestJwt(mapOf("sub" to "user123", "scope" to "read"))

            val result =
                command.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = subjectJwt,
                        subjectTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                        actorToken = null,
                        actorTokenType = null,
                        resources = emptyList(),
                        audiences = emptyList(),
                        scope = "read write admin",
                        requestedTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                        clientId = tokenExchangeClient.clientId,
                    ),
                )

            assertTrue(result.isOk, "Should succeed")
            assertEquals("read", result.value.scope, "Only the subject-held scope may be granted")
        }

    @Test
    fun testScopeInheritedFromSubjectWhenNoneRequested() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            val subjectJwt = createTestJwt(mapOf("sub" to "user123", "scope" to "read write"))

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
                        requestedTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                        clientId = tokenExchangeClient.clientId,
                    ),
                )

            assertTrue(result.isOk, "Should succeed")
            assertEquals("read write", result.value.scope, "Subject scope is inherited when none is requested")
        }

    @Test
    fun testAuthenticationContextClaimsPropagateToExchangedToken() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            val subjectJwt =
                createTestJwt(
                    mapOf(
                        "sub" to "user123",
                        "scope" to "openid profile",
                        "acr" to "urn:nist:sp:800-63:aal2",
                        "amr" to listOf("pwd", "mfa"),
                        "auth_time" to 1782930000L,
                        "email" to "user@example.com",
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
                        audiences = listOf("enterprise-wallet-unit"),
                        scope = null,
                        requestedTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                        clientId = tokenExchangeClient.clientId,
                    ),
                )

            assertTrue(result.isOk, "Should succeed")
            assertEquals("urn:nist:sp:800-63:aal2", result.value.acr)
            assertEquals(listOf("pwd", "mfa"), result.value.amr)
            assertEquals(1782930000L, result.value.authTime)
            val additionalClaims = result.value.additionalClaims
            assertFalse("acr" in additionalClaims, "Reserved acr must not be carried as an additional claim")
            assertFalse("amr" in additionalClaims, "Reserved amr must not be carried as an additional claim")
            assertFalse("auth_time" in additionalClaims, "Reserved auth_time must not be carried as an additional claim")
            assertTrue("email" !in additionalClaims, "Token exchange must not broad-copy identity claims")
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

    /** Captures whether token exchange selected the pinned-JWKS or generic JwtService branch. */
    private class RecordingJwtService : JwtService {
        val verifyArgs = mutableListOf<VerifyJwsArgs>()

        private val notImplemented =
            IdkError(
                code = "not_implemented",
                message = IdkError.Message(i18nKey = "", defaultMessage = "Not implemented"),
            )

        override suspend fun prepareJws(args: CreateJwsJsonArgs): IdkResult<PreparedJwsObject, IdkError> = Err(notImplemented)

        override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> = Err(notImplemented)

        override suspend fun createJwsJsonFlattened(args: CreateJwsJsonArgs): IdkResult<JwsJsonFlattened, IdkError> = Err(notImplemented)

        override suspend fun createJwsJsonGeneral(args: CreateJwsJsonArgs): IdkResult<JwsJsonGeneral, IdkError> = Err(notImplemented)

        override suspend fun verifyJws(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> {
            verifyArgs += args
            return Ok(
                JwsValidationResult(
                    jws = JwsJsonGeneralWithIdentifiers(payload = "", signatures = emptyList()),
                    isValid = true,
                    parsedPayload = JsonObject(emptyMap()),
                ),
            )
        }

        override fun assembleJwsGeneral(
            prepared: PreparedJwsObject,
            signatureBytes: ByteArray,
        ): JwsJsonGeneral = throw NotImplementedError()

        override fun assembleJwsFlattened(
            prepared: PreparedJwsObject,
            signatureBytes: ByteArray,
        ): JwsJsonFlattened = throw NotImplementedError()

        override fun assembleJwsCompact(
            prepared: PreparedJwsObject,
            signatureBytes: ByteArray,
        ): JwtCompactResult = throw NotImplementedError()

        override val commands: JwtService.Commands
            get() = throw NotImplementedError()
    }
}
