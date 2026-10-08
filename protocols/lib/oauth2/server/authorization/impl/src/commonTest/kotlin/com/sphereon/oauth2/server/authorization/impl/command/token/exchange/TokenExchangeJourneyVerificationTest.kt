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

package com.sphereon.oauth2.server.authorization.impl.command.token.exchange

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenTypeIdentifier
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthorization
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.TestFixtures
import com.sphereon.oauth2.server.authorization.impl.policy.StandardTokenExchangeProfile
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryClientRegistryImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemorySigningKeyStore
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryTokenStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.RecordingJwtService
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.trust.NoForeignSubjectTokenIssuerTrust
import com.sphereon.oauth2.server.authorization.impl.testutil.fixedSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.command.token.AuthorizeTokenExchangeCommand
import com.sphereon.oauth2.server.authorization.command.token.AnchoredSubjectToken
import com.sphereon.oauth2.server.authorization.command.token.AuthorizeTokenExchangeInput
import com.sphereon.oauth2.server.authorization.command.token.BoundedTokenExchange
import com.sphereon.oauth2.server.authorization.command.token.BuildTokenExchangeActorChainCommand
import com.sphereon.oauth2.server.authorization.command.token.MapTokenExchangeClaimsCommand
import com.sphereon.oauth2.server.authorization.command.token.TokenExchangeActorChainInput
import com.sphereon.oauth2.server.authorization.command.token.TokenExchangeActorExtensions
import com.sphereon.oauth2.server.authorization.command.token.TokenExchangeAuthorization
import com.sphereon.oauth2.server.authorization.command.token.TokenExchangeClaimMapping
import com.sphereon.oauth2.server.authorization.command.token.TokenExchangeProfile
import com.sphereon.oauth2.server.authorization.command.token.TokenIssuerAnchor
import com.sphereon.oauth2.server.authorization.command.token.TrustedIssuerRef
import com.sphereon.oauth2.server.authorization.signing.AsSigningKeyPublicJwkResolver
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import com.sphereon.oauth2.server.authorization.trust.SubjectTokenIssuerTrust
import com.sphereon.core.api.service.StringResult
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand
import com.sphereon.oauth2.common.model.ActorClaim
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.ClientCredentials
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseCommand
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestCommand
import com.sphereon.oauth2.server.authorization.command.TokenRequestData
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthentication
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationCommand
import com.sphereon.oauth2.server.authorization.command.token.HandleTokenRequestArgs
import com.sphereon.oauth2.server.authorization.dpop.DpopNonceManager
import com.sphereon.oauth2.server.authorization.dpop.DpopProofJtiCache
import com.sphereon.oauth2.server.authorization.impl.command.token.CreateAccessTokenCommandImpl
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * RFC 8693 verification, authorization, bounding and actor-chain vectors, executed through the
 * production token-exchange journey. Results are read from the arguments the journey mints with.
 * A recording verifier and explicit issuer anchors expose the keys selected for each token.
 */
class TokenExchangeJourneyVerificationTest {
    private companion object {
        const val TOKEN_ENDPOINT = "https://as.example.test/token"
    }

    private val issuer = "https://as.example.test"
    private val testForeignIssuer = "https://external-test-issuer.example.test"
    private val ctx = OAuth2ServerTestContext("verify-token-exchange-test", this)
    private val execution = ctx.execution
    private val jwtService: JwtService = (ctx.session.graph as JwtServiceImpl.Graph).jwtService
    private val configProvider =
        TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers = mapOf("default" to OAuth2ServerInstanceConfig(issuer = issuer)),
            ),
        )

    // Test client authorized for TOKEN_EXCHANGE
    private val tokenExchangeClient =
        TestFixtures.confidentialClient.copy(
            clientId = "token-exchange-client",
            grantTypes =
                listOf(
                    GrantType.AUTHORIZATION_CODE,
                    GrantType.TOKEN_EXCHANGE,
                ),
            defaultAccessTokenAudience = "enterprise-platform",
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
        claims: Map<String, Any?>,
        kid: String? = "test-external-key",
        headerJson: String? = null,
        includeDefaultIssuer: Boolean = true,
        includeDefaultExpiration: Boolean = true,
    ): String {
        val header =
            encodeBase64Url(
                headerJson ?: if (kid == null) {
                    """{"alg":"RS256","typ":"JWT"}"""
                } else {
                    """{"alg":"RS256","typ":"JWT","kid":"$kid"}"""
                },
            )
        val payloadJson =
            buildString {
                append("{")
                val effectiveClaims =
                    buildMap {
                        if (includeDefaultIssuer && "iss" !in claims) put("iss", testForeignIssuer)
                        if (includeDefaultExpiration && "exp" !in claims) {
                            put("exp", Clock.System.now().epochSeconds + 300)
                        }
                        putAll(claims)
                    }
                effectiveClaims.entries.forEachIndexed { index, (key, value) ->
                    if (index > 0) append(",")
                    append("\"$key\":")
                    when (value) {
                        null -> append("null")
                        is String -> {
                            append(JsonPrimitive(value))
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
                                appendJsonValue(v)
                            }
                            append("]")
                        }

                        is Map<*, *> -> appendJsonValue(value)

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

    private fun StringBuilder.appendJsonValue(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> append(JsonPrimitive(value))
            is Number, is Boolean -> append(value)
            is Map<*, *> -> {
                append("{")
                value.entries.forEachIndexed { index, (key, nested) ->
                    if (index > 0) append(",")
                    append(JsonPrimitive(key.toString()))
                    append(":")
                    appendJsonValue(nested)
                }
                append("}")
            }
            is List<*> -> {
                append("[")
                value.forEachIndexed { index, nested ->
                    if (index > 0) append(",")
                    appendJsonValue(nested)
                }
                append("]")
            }
            else -> append(JsonPrimitive(value.toString()))
        }
    }

    private fun localSubjectClaims(
        subject: String = "user123",
        clientId: String? = tokenExchangeClient.clientId,
        audience: Any? = "enterprise-platform",
        expiresAt: Long? = Clock.System.now().epochSeconds + 300,
        notBefore: Long? = Clock.System.now().epochSeconds - 1,
        authorizedParty: String? = null,
        includeIssuer: Boolean = true,
        email: String? = "user@example.test",
    ): Map<String, Any> =
        buildMap {
            put("sub", subject)
            clientId?.let { put("client_id", it) }
            if (includeIssuer) put("iss", issuer)
            audience?.let { put("aud", it) }
            expiresAt?.let { put("exp", it) }
            notBefore?.let { put("nbf", it) }
            authorizedParty?.let { put("azp", it) }
            email?.let { put("email", it) }
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

    /** One deployment decision, split by [asProfile] across the three fixed extension positions. */
    private data class PolicyDecision(
        val allowed: Boolean,
        val isDelegation: Boolean,
        val grantedScope: String?,
        val grantedAudience: List<String>,
        val additionalClaims: Map<String, Any> = emptyMap(),
        val denyReason: String? = null,
        val actorClaimExtensions: Map<String, JsonElement> = emptyMap(),
    )

    private fun interface TestPolicy {
        suspend fun evaluate(request: AuthorizeTokenExchangeInput): IdkResult<PolicyDecision, AuthorizationServerError>
    }

    private fun TestPolicy.asProfile(): TokenExchangeProfile {
        val policy = this
        var decision: PolicyDecision? = null
        return object : TokenExchangeProfile {
            override val id: String = "test.tokenexchange.policy"
            override val authorize =
                object : AuthorizeTokenExchangeCommand {
                    override suspend fun execute(args: AuthorizeTokenExchangeInput): IdkResult<TokenExchangeAuthorization, AuthorizationServerError> =
                        policy.evaluate(args).map { result ->
                            decision = result
                            TokenExchangeAuthorization(
                                allowed = result.allowed,
                                isDelegation = result.isDelegation,
                                grantedScope = result.grantedScope,
                                grantedTargets = result.grantedAudience,
                                denyReason = result.denyReason,
                            )
                        }
                }
            override val mapClaims =
                object : MapTokenExchangeClaimsCommand {
                    override suspend fun execute(args: BoundedTokenExchange): IdkResult<TokenExchangeClaimMapping, AuthorizationServerError> =
                        Ok(TokenExchangeClaimMapping(JsonObject(decision?.additionalClaims.orEmpty().mapValues { (_, value) -> toJsonElement(value) })))
                }
            override val buildActorChain =
                object : BuildTokenExchangeActorChainCommand {
                    override suspend fun execute(args: TokenExchangeActorChainInput): IdkResult<TokenExchangeActorExtensions, AuthorizationServerError> =
                        Ok(TokenExchangeActorExtensions(JsonObject(decision?.actorClaimExtensions.orEmpty())))
                }
        }
    }

    private fun toJsonElement(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate { (key, nested) -> key.toString() to toJsonElement(nested) })
            is List<*> -> JsonArray(value.map(::toJsonElement))
            else -> JsonPrimitive(value.toString())
        }

    private fun createCommand(
        clientRegistry: InMemoryClientRegistryImpl,
        policy: TestPolicy? = null,
        jwtService: JwtService = RecordingJwtService(),
        signingKeyStore: SigningKeyStore = InMemorySigningKeyStore(),
        signingKeyPublicJwkResolver: AsSigningKeyPublicJwkResolver? = null,
        serversConfigProvider: TestOAuth2ServersConfigProvider = configProvider,
        subjectTokenIssuerTrust: SubjectTokenIssuerTrust = testForeignIssuerTrust,
    ): JourneyHarness =
        JourneyHarness(
            clientRegistry = clientRegistry,
            profile = policy?.asProfile() ?: StandardTokenExchangeProfile(),
            jwtService = jwtService,
            signingKeyStore = signingKeyStore,
            signingKeyPublicJwkResolver = signingKeyPublicJwkResolver,
            serversConfigProvider = serversConfigProvider,
            subjectTokenIssuerTrust = subjectTokenIssuerTrust,
        )

    /** Raw RFC 8693 request parameters for one exchange. */
    private data class ExchangeArgs(
        val subjectToken: String,
        val subjectTokenType: String,
        val actorToken: String? = null,
        val actorTokenType: String? = null,
        val resources: List<String> = emptyList(),
        val audiences: List<String> = emptyList(),
        val scope: String? = null,
        val requestedTokenType: String? = null,
        val clientId: String,
    )

    /** What the journey minted, plus the request resources it bounded. */
    private data class ExchangeResult(
        val subject: String,
        val clientId: String,
        val scope: String?,
        val audience: List<String>,
        val resource: List<String>,
        val issuedTokenType: String?,
        val isDelegation: Boolean,
        val actorSubject: String?,
        val actorClaim: ActorClaim?,
        val authTime: Long?,
        val acr: String?,
        val amr: List<String>?,
        val additionalClaims: Map<String, Any>,
    )

    /**
     * Runs the production [TokenExchangeJourneyCommandImpl]. Client authentication yields the
     * requested client id without a registration, so the journey reads the registration itself.
     */
    private inner class JourneyHarness(
        private val clientRegistry: ClientRegistry,
        private val profile: TokenExchangeProfile,
        private val jwtService: JwtService,
        private val signingKeyStore: SigningKeyStore,
        private val signingKeyPublicJwkResolver: AsSigningKeyPublicJwkResolver?,
        private val serversConfigProvider: TestOAuth2ServersConfigProvider,
        private val subjectTokenIssuerTrust: SubjectTokenIssuerTrust,
    ) {
        suspend fun execute(args: ExchangeArgs): IdkResult<ExchangeResult, IdkError> {
            val exchange = args
            var minted: CreateAccessTokenArgs? = null
            var issuedTokenType: String? = null
            val journey =
                TokenExchangeJourneyCommandImpl(
                    execution = execution,
                    parseTokenRequestCommand =
                        object : ParseTokenRequestCommand {
                            override val inputTypeToken = typeToken<ParseTokenRequestArgs>()
                            override val outputTypeToken = typeToken<TokenRequestData>()
                            override val isEnabled = true

                            override suspend fun execute(args: ParseTokenRequestArgs): IdkResult<TokenRequestData, IdkError> =
                                Ok(
                                    TokenRequestData(
                                        grantType = GrantType.TOKEN_EXCHANGE,
                                        clientId = exchange.clientId,
                                        clientAuthentication = ClientAuthenticationConfig.Basic(ClientCredentials(exchange.clientId, "test-secret")),
                                        grantParameters =
                                            GrantParameters.TokenExchange(
                                                subjectToken = exchange.subjectToken,
                                                subjectTokenType = exchange.subjectTokenType,
                                                actorToken = exchange.actorToken,
                                                actorTokenType = exchange.actorTokenType,
                                                resources = exchange.resources,
                                                audiences = exchange.audiences,
                                                scope = exchange.scope,
                                                requestedTokenType = exchange.requestedTokenType,
                                            ),
                                        httpUrl = TOKEN_ENDPOINT,
                                    ),
                                )
                        },
                    verifyClientAuthenticationCommand =
                        object : VerifyClientAuthenticationCommand {
                            override val inputTypeToken = typeToken<VerifyClientAuthenticationArgs>()
                            override val outputTypeToken = typeToken<VerifiedClientAuthentication>()
                            override val isEnabled = true

                            override suspend fun execute(args: VerifyClientAuthenticationArgs): IdkResult<VerifiedClientAuthentication, IdkError> =
                                Ok(VerifiedClientAuthentication(clientId = args.clientId, method = ClientAuthenticationMethod.CLIENT_SECRET_BASIC))
                        },
                    serversConfigProvider = serversConfigProvider,
                    verifyDpopProofCommand = lazy<VerifyDpopProofCommand> { error("No DPoP proof is presented in these vectors") },
                    dpopProofJtiCache = lazy<DpopProofJtiCache> { error("No DPoP proof is presented in these vectors") },
                    dpopNonceManager = lazy<DpopNonceManager> { error("No DPoP proof is presented in these vectors") },
                    clientRegistry = clientRegistry,
                    jwtService = jwtService,
                    signingKeyStore = signingKeyStore,
                    subjectTokenIssuerTrust = subjectTokenIssuerTrust,
                    tokenExchangeProfile = profile,
                    createAccessToken =
                        object : CreateAccessTokenCommand {
                            override val inputTypeToken = typeToken<CreateAccessTokenArgs>()
                            override val outputTypeToken = typeToken<StringResult>()
                            override val isEnabled = true

                            override suspend fun execute(args: CreateAccessTokenArgs): IdkResult<StringResult, IdkError> {
                                minted = args
                                return Ok(StringResult(value = "exchanged-access-token"))
                            }
                        },
                    createTokenResponse =
                        object : CreateTokenResponseCommand {
                            override val inputTypeToken = typeToken<CreateTokenResponseArgs>()
                            override val outputTypeToken = typeToken<TokenResponse>()
                            override val isEnabled = true

                            override suspend fun execute(args: CreateTokenResponseArgs): IdkResult<TokenResponse, IdkError> {
                                issuedTokenType = args.issuedTokenType
                                return Ok(TokenResponse(accessToken = args.accessToken, tokenType = args.tokenType, issuedTokenType = args.issuedTokenType))
                            }
                        },
                    signingKeyPublicJwkResolver = signingKeyPublicJwkResolver,
                )
            val result = journey.execute(HandleTokenRequestArgs(requestBody = emptyMap(), requestHeaders = emptyMap(), httpUrl = TOKEN_ENDPOINT))
            if (result.isErr) return Err(result.error).asResult()
            val mint = checkNotNull(minted) { "A successful exchange must mint" }
            val actor = mint.additionalClaims["act"] as? ActorClaim
            return Ok(
                ExchangeResult(
                    subject = mint.subject,
                    clientId = mint.clientId,
                    scope = mint.scope,
                    audience = mint.audience,
                    resource = args.resources,
                    issuedTokenType = issuedTokenType,
                    isDelegation = actor != null,
                    actorSubject = actor?.sub,
                    actorClaim = actor,
                    authTime = mint.authTime,
                    acr = mint.acr,
                    amr = mint.amr,
                    additionalClaims = mint.additionalClaims - "act",
                ),
            ).asResult()
        }
    }

    private val testForeignIssuerTrust =
        object : SubjectTokenIssuerTrust {
            private val issuers =
                setOf(
                    testForeignIssuer,
                    "https://external-idp.example.test",
                    "https://idp.example.com",
                    "https://auth.internal.com",
                )

            override suspend fun resolveTrustedKeys(issuer: String): List<Jwk>? =
                if (issuer in issuers) {
                    listOf(publicJwk("test-external-key"), publicJwk("external-idp-key"))
                } else {
                    null
                }
        }

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
                    jwtService = recordingJwtService,
                    signingKeyStore = store,
                    signingKeyPublicJwkResolver = resolver,
                )

            val result =
                command.execute(
                    ExchangeArgs(
                        subjectToken = createTestJwt(localSubjectClaims(), kid = kid),
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
    fun anchoredForeignIssuerUsesOnlyItsTrustedKeys() =
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
                    jwtService = recordingJwtService,
                    signingKeyStore = store,
                    signingKeyPublicJwkResolver = resolver,
                    subjectTokenIssuerTrust = object : SubjectTokenIssuerTrust {
                        override suspend fun resolveTrustedKeys(issuer: String): List<Jwk>? =
                            if (issuer == "https://external-idp.example.test") {
                                listOf(publicJwk("external-idp-key"))
                            } else {
                                null
                            }
                    },
                )

            val result =
                command.execute(
                    ExchangeArgs(
                        subjectToken =
                            createTestJwt(
                                mapOf(
                                    "sub" to "external-user",
                                    "iss" to "https://external-idp.example.test",
                                    "exp" to Clock.System.now().epochSeconds + 300,
                                ),
                                kid = "external-idp-key",
                            ),
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

            assertTrue(result.isOk, "An anchored foreign issuer may exchange a token signed by its trusted key")
            assertEquals(0, resolverCalls)
            val trustedJwks = assertNotNull(recordingJwtService.verifyArgs.single().trustedJwks)
            val trustedKeys = assertNotNull(trustedJwks["keys"]).jsonArray
            assertEquals(1, trustedKeys.size, "Verification must receive only the issuer's anchored key")
            assertEquals("external-idp-key", trustedKeys.single().jsonObject["kid"]?.jsonPrimitive?.content)
        }

    @Test
    fun foreignIssuerWithoutTrustAnchorIsRejectedEvenWhenSignatureWouldVerify() =
        runTest {
            val recordingJwtService = RecordingJwtService()
            var policyInvoked = false
            val permissivePolicy = object : TestPolicy {
                override suspend fun evaluate(request: AuthorizeTokenExchangeInput): IdkResult<PolicyDecision, AuthorizationServerError> {
                    policyInvoked = true
                    return Ok(
                        PolicyDecision(
                            allowed = true,
                            isDelegation = false,
                            grantedScope = null,
                            grantedAudience = listOf("enterprise-platform"),
                        ),
                    )
                }
            }
            val command =
                createCommand(
                    clientRegistry = setupClientRegistry(tokenExchangeClient),
                    policy = permissivePolicy,
                    jwtService = recordingJwtService,
                    subjectTokenIssuerTrust = object : SubjectTokenIssuerTrust {
                        override suspend fun resolveTrustedKeys(issuer: String): List<Jwk>? = null
                    },
                )
            val token =
                createTestJwt(
                    mapOf("iss" to "https://unanchored.example.test", "sub" to "external-user"),
                    kid = "external-idp-key",
                )

            val result = command.execute(exchangeArgs(token))

            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
            assertTrue(recordingJwtService.verifyArgs.isEmpty(), "An unanchored issuer must fail before signature resolution")
            assertFalse(policyInvoked, "Unanchored issuer must fail before deployment policy")
        }

    @Test
    fun embeddedJwkHeaderIsRejected() =
        runTest {
            val token =
                createTestJwt(
                    emptyMap(),
                    headerJson = """{"alg":"RS256","typ":"JWT","kid":"test-external-key","jwk":{"kty":"EC"}}""",
                )
            val result = createCommand(setupClientRegistry(tokenExchangeClient)).execute(exchangeArgs(token))

            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
        }

    @Test
    fun didKeyKidIsRejected() =
        runTest {
            val token = createTestJwt(emptyMap(), kid = "did:key:z6Mktest")
            val result = createCommand(setupClientRegistry(tokenExchangeClient)).execute(exchangeArgs(token))

            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
        }

    @Test
    fun foreignUnanchoredActorTokenIsRejected() =
        runTest {
            val command =
                createCommand(
                    clientRegistry = setupClientRegistry(tokenExchangeClient),
                    subjectTokenIssuerTrust = object : SubjectTokenIssuerTrust {
                        override suspend fun resolveTrustedKeys(issuer: String): List<Jwk>? =
                            if (issuer == testForeignIssuer) listOf(publicJwk("test-external-key")) else null
                    },
                )
            val args =
                exchangeArgs(createTestJwt(emptyMap())).copy(
                    actorToken = createTestJwt(
                        mapOf("iss" to "https://unanchored-actor.example.test", "sub" to "actor"),
                        kid = "external-idp-key",
                    ),
                    actorTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                )

            val result = command.execute(args)

            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
        }

    @Test
    fun localSubjectWithUnregisteredClientIsRejected() =
        runTest {
            val result = executeLocalSubject(localSubjectClaims(clientId = "unregistered-client"))

            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
            assertTrue(result.error.message.defaultMessage.contains("was not issued to a registered client"))
        }

    @Test
    fun localSubjectWithoutClientIdOrAzpIsRejected() =
        runTest {
            val result = executeLocalSubject(localSubjectClaims(clientId = null))

            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
            assertTrue(result.error.message.defaultMessage.contains("was not issued to a registered client"))
        }

    @Test
    fun localActorWithoutClientIdOrAzpIsRejected() =
        runTest {
            val result =
                executeLocalSubject(
                    localSubjectClaims(),
                    actorTokenClaims = mapOf("sub" to "actor", "iss" to issuer),
                )

            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
            assertTrue(result.error.message.defaultMessage.contains("was not issued to a registered client"))
        }

    @Test
    fun localActorWithAudienceOutsideItsClientRegistrationIsRejected() =
        runTest {
            val result =
                executeLocalSubject(
                    localSubjectClaims(),
                    actorTokenClaims = localSubjectClaims(subject = "actor-user", audience = "unauthorized-resource"),
                )

            assertTrue(result.isErr, "A local actor token issued for another audience must not prove an actor")
            assertEquals("invalid_request", result.error.code)
            assertTrue(result.error.message.defaultMessage.contains("actor token audience is not authorized"))
        }

    @Test
    fun localActorWithoutAudienceIsRejected() =
        runTest {
            val result =
                executeLocalSubject(
                    localSubjectClaims(),
                    actorTokenClaims = localSubjectClaims(subject = "actor-user", audience = null),
                )

            assertTrue(result.isErr, "A local actor token must carry an audience")
            assertTrue(result.error.message.defaultMessage.contains("actor token has no valid aud claim"))
        }

    @Test
    fun localWorkloadActorOfAnotherClientIsRejected() =
        runTest {
            val otherWorkload =
                tokenExchangeClient.copy(
                    clientId = "other-workload",
                    defaultAccessTokenAudience = "enterprise-platform",
                )
            val result =
                executeLocalSubject(
                    localSubjectClaims(),
                    additionalClients = arrayOf(otherWorkload),
                    actorTokenClaims =
                        localSubjectClaims(
                            subject = otherWorkload.clientId,
                            clientId = otherWorkload.clientId,
                            authorizedParty = otherWorkload.clientId,
                            email = null,
                        ),
                )

            assertTrue(result.isErr, "A workload actor token is usable only by the client it was issued to")
            assertTrue(result.error.message.defaultMessage.contains("workload actor token is not bound"))
        }

    @Test
    fun localWorkloadActorOfTheExchangingClientIsAccepted() =
        runTest {
            val result =
                executeLocalSubject(
                    localSubjectClaims(),
                    actorTokenClaims =
                        localSubjectClaims(
                            subject = tokenExchangeClient.clientId,
                            authorizedParty = tokenExchangeClient.clientId,
                            email = null,
                        ),
                )

            assertTrue(result.isOk, "The exchanging client may present its own workload token as actor")
        }

    @Test
    fun localIdTokenUsesRegisteredAzpWhenClientIdIsAbsent() =
        runTest {
            val result =
                executeLocalSubject(
                    localSubjectClaims(clientId = null, authorizedParty = tokenExchangeClient.clientId),
                    subjectTokenType = TokenTypeIdentifier.ID_TOKEN,
                )

            assertTrue(result.isOk, "A local ID token with a registered azp must be accepted")
        }

    @Test
    fun defaultSubjectTokenIssuerTrustDoesNotAnchorForeignIssuers() =
        runTest {
            assertNull(NoForeignSubjectTokenIssuerTrust().resolveTrustedKeys(testForeignIssuer))
        }

    @Test
    fun expiredVerifiedLocalSubjectIsRejected() =
        runTest {
            val result =
                executeLocalSubject(
                    localSubjectClaims(expiresAt = Clock.System.now().epochSeconds - 1),
                )

            assertTrue(result.isErr, "An expired local subject token must fail before policy evaluation")
        }

    @Test
    fun missingExpirationVerifiedLocalSubjectIsRejected() =
        runTest {
            val subjectClient = tokenExchangeClient.copy(clientId = "local-subject-client")
            assertTrue(
                executeLocalSubject(
                    localSubjectClaims(clientId = subjectClient.clientId, expiresAt = null),
                    additionalClients = arrayOf(subjectClient),
                    includeDefaultExpiration = false,
                ).isErr,
                "A verified local subject token must carry exp",
            )
        }

    @Test
    fun futureNotBeforeVerifiedLocalSubjectIsRejected() =
        runTest {
            val result =
                executeLocalSubject(
                    localSubjectClaims(notBefore = Clock.System.now().epochSeconds + 60),
                )

            assertTrue(result.isErr, "A local subject token outside the nbf skew must fail")
        }

    @Test
    fun wrongIssuerVerifiedLocalSubjectIsRejected() =
        runTest {
            val claims = localSubjectClaims().toMutableMap().apply { put("iss", "https://wrong-issuer.example.test") }

            assertTrue(executeLocalSubject(claims).isErr, "A locally signed token cannot claim another issuer")
        }

    @Test
    fun missingIssuerVerifiedLocalSubjectIsRejected() =
        runTest {
            assertTrue(
                executeLocalSubject(
                    localSubjectClaims(includeIssuer = false),
                    includeDefaultIssuer = false,
                ).isErr,
                "A locally signed token must identify this authorization server as issuer",
            )
        }

    @Test
    fun missingAudienceVerifiedLocalSubjectIsRejected() =
        runTest {
            assertTrue(
                executeLocalSubject(localSubjectClaims(audience = null)).isErr,
                "A local subject token must carry a resource audience",
            )
        }

    @Test
    fun unauthorizedAudienceVerifiedLocalSubjectIsRejected() =
        runTest {
            assertTrue(
                executeLocalSubject(localSubjectClaims(audience = "unauthorized-resource")).isErr,
                "The local subject audience must be authorized for its issuing client",
            )
        }

    @Test
    fun workloadSubjectFromDifferentClientIsRejected() =
        runTest {
            val otherWorkload =
                tokenExchangeClient.copy(
                    clientId = "other-workload",
                    defaultAccessTokenAudience = "enterprise-platform",
                )
            val result =
                executeLocalSubject(
                    claims =
                        localSubjectClaims(
                            subject = otherWorkload.clientId,
                            clientId = otherWorkload.clientId,
                            authorizedParty = otherWorkload.clientId,
                            email = null,
                        ),
                    additionalClients = arrayOf(otherWorkload),
                )

            assertTrue(result.isErr, "A workload subject must be bound to the authenticated exchanger")
        }

    @Test
    fun workloadSubjectWithWrongAuthorizedPartyIsRejected() =
        runTest {
            val result =
                executeLocalSubject(
                    localSubjectClaims(
                        subject = tokenExchangeClient.clientId,
                        authorizedParty = "different-client",
                        email = null,
                    ),
                )

            assertTrue(result.isErr, "A workload subject must carry sub == client_id == azp")
        }

    @Test
    fun workloadSubjectBoundToExchangingClientIsAccepted() =
        runTest {
            val result =
                executeLocalSubject(
                    localSubjectClaims(
                        subject = tokenExchangeClient.clientId,
                        authorizedParty = tokenExchangeClient.clientId,
                        email = null,
                    ),
                )

            assertTrue(result.isOk, "A valid self-issued workload token must remain exchangeable")
        }

    @Test
    fun workloadSubjectWithoutAuthorizedPartyIsRejected() =
        runTest {
            val result =
                executeLocalSubject(
                    localSubjectClaims(
                        subject = tokenExchangeClient.clientId,
                        authorizedParty = null,
                        email = null,
                    ),
                )

            assertTrue(result.isErr, "A workload-shaped subject token must carry azp")
        }

    @Test
    fun verifiedLocalHumanSubjectMayBeExchangedByDifferentAuthorizedClient() =
        runTest {
            val loginClient =
                tokenExchangeClient.copy(
                    clientId = "human-login-client",
                    defaultAccessTokenAudience = "enterprise-platform",
                )
            val result =
                executeLocalSubject(
                    claims =
                        localSubjectClaims(
                            subject = "human-user-123",
                            clientId = loginClient.clientId,
                            email = "human@example.test",
                        ),
                    additionalClients = arrayOf(loginClient),
                )

            assertTrue(result.isOk, "Policy-authorized delegation must not bind a human subject to its original client")
            assertEquals("human-user-123", result.value.subject)
            assertEquals(tokenExchangeClient.clientId, result.value.clientId)
        }

    @Test
    fun realHumanAccessTokenMintWithStringAudienceIsAcceptedByDifferentAuthorizedExchanger() =
        runTest {
            val kid = "authorization-code-human-key"
            val loginClient =
                tokenExchangeClient.copy(
                    clientId = "platform-operator-cli",
                    grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
                    defaultAccessTokenAudience = "enterprise-platform",
                )
            val recordingJwtService = RecordingJwtService(mintedKid = kid)
            val mintCommand =
                CreateAccessTokenCommandImpl(
                    execution = execution,
                    jwtService = recordingJwtService,
                    tokenStorage = InMemoryTokenStorageImpl(InMemoryOAuth2BackingStorageImpl()),
                    secureRandom = defaultSecureRandom(),
                    configProvider = configProvider,
                    signingIdentifierResolver =
                        fixedSigningIdentifierResolver(ManagedOptsAlias(identifier = kid)),
                    eventService = null,
                )
            val minted =
                mintCommand.execute(
                    CreateAccessTokenArgs(
                        subject = "platform-admin-user",
                        clientId = loginClient.clientId,
                        scope = "openid",
                        audience = listOf("enterprise-platform"),
                    ),
                )
            assertTrue(minted.isOk, "The authorization-code-shaped human access token must mint")
            val accessToken = minted.value.value
            val mintedClaims = JwsUtils.decodeBase64UrlToJson(accessToken.split('.')[1])
            assertTrue(mintedClaims["aud"] is JsonPrimitive, "A single RFC 9068 audience must be a JSON string")
            assertEquals("enterprise-platform", mintedClaims["aud"]?.jsonPrimitive?.content)

            val store = InMemorySigningKeyStore()
            assertTrue(store.register(signingKey(kid)).isOk)
            val exchangeCommand =
                createCommand(
                    clientRegistry = setupClientRegistry(tokenExchangeClient, loginClient),
                    jwtService = recordingJwtService,
                    signingKeyStore = store,
                    signingKeyPublicJwkResolver = resolverForLocalKeys(),
                )
            val exchanged = exchangeCommand.execute(exchangeArgs(accessToken))

            assertTrue(exchanged.isOk, "A policy-authorized different client must exchange the audience-bound human token")
            assertEquals("platform-admin-user", exchanged.value.subject)
            assertEquals(tokenExchangeClient.clientId, exchanged.value.clientId)
        }

    @Test
    fun localIssuerWithUnknownKidFailsWithoutGenericFallback() =
        runTest {
            val recordingJwtService = RecordingJwtService()
            val command =
                createCommand(
                    clientRegistry = setupClientRegistry(tokenExchangeClient),
                    jwtService = recordingJwtService,
                    signingKeyStore = InMemorySigningKeyStore(),
                    signingKeyPublicJwkResolver = resolverForLocalKeys(),
                )

            val result = command.execute(exchangeArgs(createTestJwt(localSubjectClaims(), kid = "unknown-local-kid")))

            assertTrue(result.isErr, "A local issuer with an unknown kid must fail closed")
            assertEquals("invalid_request", result.error.code)
            assertTrue(recordingJwtService.verifyArgs.isEmpty(), "Unknown local kids must not reach generic JOSE resolution")
        }

    @Test
    fun unresolvableAuthorizationServerIssuerFailsClosed() =
        runTest {
            val kid = "local-kid-without-issuer-policy"
            val store = InMemorySigningKeyStore()
            assertTrue(store.register(signingKey(kid)).isOk)
            val recordingJwtService = RecordingJwtService()
            val command =
                createCommand(
                    clientRegistry = setupClientRegistry(tokenExchangeClient),
                    jwtService = recordingJwtService,
                    signingKeyStore = store,
                    signingKeyPublicJwkResolver = resolverForLocalKeys(),
                    serversConfigProvider = TestOAuth2ServersConfigProvider(OAuth2ServersConfig()),
                )

            val result = command.execute(exchangeArgs(createTestJwt(localSubjectClaims(), kid = kid)))

            assertTrue(result.isErr, "Token exchange must fail closed when the AS issuer cannot be resolved")
            assertTrue(recordingJwtService.verifyArgs.isEmpty(), "Missing issuer policy must not enter generic JOSE resolution")
        }

    @Test
    fun disabledLocalKidFailsWithoutSignatureResolution() =
        runTest {
            val kid = "disabled-local-kid"
            val store = InMemorySigningKeyStore()
            assertTrue(store.register(signingKey(kid, OAuth2SigningKeyState.DISABLED)).isOk)
            var resolverCalls = 0
            val recordingJwtService = RecordingJwtService()
            val command =
                createCommand(
                    clientRegistry = setupClientRegistry(tokenExchangeClient),
                    jwtService = recordingJwtService,
                    signingKeyStore = store,
                    signingKeyPublicJwkResolver =
                        object : AsSigningKeyPublicJwkResolver {
                            override suspend fun resolve(signingKey: OAuth2SigningKey): Jwk? =
                                publicJwk(signingKey.kid).also { resolverCalls++ }
                        },
                )

            val result = command.execute(exchangeArgs(createTestJwt(localSubjectClaims(), kid = kid)))

            assertTrue(result.isErr, "A disabled local signing key must never validate a subject token")
            assertEquals(0, resolverCalls)
            assertTrue(recordingJwtService.verifyArgs.isEmpty())
        }

    private suspend fun executeLocalSubject(
        claims: Map<String, Any>,
        additionalClients: Array<out ClientRegistration> = emptyArray(),
        subjectTokenType: String = TokenTypeIdentifier.ACCESS_TOKEN,
        actorTokenClaims: Map<String, Any>? = null,
        includeDefaultIssuer: Boolean = true,
        includeDefaultExpiration: Boolean = true,
    ): IdkResult<ExchangeResult, IdkError> {
        val kid = "strict-local-subject-key"
        val store = InMemorySigningKeyStore()
        check(store.register(signingKey(kid)).isOk)
        val command =
            createCommand(
                clientRegistry = setupClientRegistry(tokenExchangeClient, *additionalClients),
                jwtService = RecordingJwtService(),
                signingKeyStore = store,
                signingKeyPublicJwkResolver = resolverForLocalKeys(),
            )
        val args =
            exchangeArgs(
                createTestJwt(
                    claims,
                    kid = kid,
                    includeDefaultIssuer = includeDefaultIssuer,
                    includeDefaultExpiration = includeDefaultExpiration,
                ),
            ).copy(subjectTokenType = subjectTokenType)
        return command.execute(
            args.copy(
                actorToken = actorTokenClaims?.let { createTestJwt(it, kid = kid) },
                actorTokenType = actorTokenClaims?.let { TokenTypeIdentifier.ACCESS_TOKEN },
            ),
        )
    }

    private fun resolverForLocalKeys(): AsSigningKeyPublicJwkResolver =
        object : AsSigningKeyPublicJwkResolver {
            override suspend fun resolve(signingKey: OAuth2SigningKey): Jwk = publicJwk(signingKey.kid)
        }

    private fun exchangeArgs(subjectToken: String): ExchangeArgs =
        ExchangeArgs(
            subjectToken = subjectToken,
            subjectTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
            actorToken = null,
            actorTokenType = null,
            resources = emptyList(),
            audiences = emptyList(),
            scope = null,
            requestedTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
            clientId = tokenExchangeClient.clientId,
        )

    private fun signingKey(
        kid: String,
        state: OAuth2SigningKeyState = OAuth2SigningKeyState.ACTIVE,
    ): OAuth2SigningKey {
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
            state = state,
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
                    ExchangeArgs(
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
                    ExchangeArgs(
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
            assertEquals("https://auth.internal.com", grant.actorClaim!!.additionalClaims["iss"]?.jsonPrimitive?.content)
            assertNull(grant.actorClaim!!.additionalClaims["client_id"])
        }

    // --- Validation error tests ---

    @Test
    fun testMissingSubjectTokenReturnsError() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry)

            val result =
                command.execute(
                    ExchangeArgs(
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
                    ExchangeArgs(
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
                    ExchangeArgs(
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
                    ExchangeArgs(
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
                    ExchangeArgs(
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
                    ExchangeArgs(
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
                    ExchangeArgs(
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
                    ExchangeArgs(
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
    fun opaqueRefreshTokenTypeIsRejectedEvenWithPermissivePolicy() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            var policyInvoked = false
            val permissivePolicy = object : TestPolicy {
                override suspend fun evaluate(request: AuthorizeTokenExchangeInput): IdkResult<PolicyDecision, AuthorizationServerError> {
                    policyInvoked = true
                    return Ok(
                        PolicyDecision(
                            allowed = true,
                            isDelegation = false,
                            grantedScope = null,
                            grantedAudience = listOf("enterprise-platform"),
                        ),
                    )
                }
            }
            val command = createCommand(registry, policy = permissivePolicy)

            val result =
                command.execute(
                    ExchangeArgs(
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

            assertTrue(result.isErr, "Opaque refresh tokens are unsupported")
            assertEquals("invalid_request", result.error.code)
            assertFalse(policyInvoked, "Unverified token must not reach deployment policy")

            val actorResult = command.execute(
                exchangeArgs(createTestJwt(mapOf("sub" to "user123"))).copy(
                    actorToken = "opaque-actor-refresh-token",
                    actorTokenType = TokenTypeIdentifier.REFRESH_TOKEN,
                ),
            )
            assertTrue(actorResult.isErr, "Opaque actor refresh tokens are unsupported")
            assertEquals("invalid_request", actorResult.error.code)
            assertFalse(policyInvoked, "Unverified actor must not reach deployment policy")
        }

    // --- Resource and audience pass-through tests ---

    @Test
    fun testResourcesAndAudiencesPassedToResult() =
        runTest {
            val client = tokenExchangeClient.copy(allowedAccessTokenAudiences = setOf("audience1", "https://api.example.com", "https://other.example.com"))
            val registry = setupClientRegistry(client)
            val command = createCommand(registry)

            // Subject token already holds the requested scopes, so they pass through.
            val subjectJwt = createTestJwt(mapOf("sub" to "user123", "scope" to "read write admin"))

            val result =
                command.execute(
                    ExchangeArgs(
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
            assertEquals(listOf("audience1", "https://api.example.com", "https://other.example.com"), grant.audience)
            assertEquals("read write", grant.scope)
        }

    @Test
    fun unauthorizedAudienceRequestReturnsInvalidTarget() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val result = createCommand(registry).execute(
                exchangeArgs(createTestJwt(mapOf("sub" to "user123"))).copy(audiences = listOf("https://unregistered.example.test")),
            )
            assertTrue(result.isErr)
            assertEquals("invalid_target", result.error.code)
        }

    @Test
    fun unauthorizedResourceRequestReturnsInvalidTarget() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val result = createCommand(registry).execute(
                exchangeArgs(createTestJwt(mapOf("sub" to "user123"))).copy(resources = listOf("https://unregistered.example.test")),
            )
            assertTrue(result.isErr)
            assertEquals("invalid_target", result.error.code)
        }

    @Test
    fun resourceOnlyRequestGrantsResourceAsAccessTokenAudience() =
        runTest {
            val resource = "https://resource.example.test"
            val client = tokenExchangeClient.copy(allowedAccessTokenAudiences = setOf(resource))
            val registry = setupClientRegistry(client)
            val result = createCommand(registry).execute(
                exchangeArgs(createTestJwt(mapOf("sub" to "user123"))).copy(resources = listOf(resource)),
            )
            assertTrue(result.isOk)
            assertEquals(listOf(resource), result.value.audience)
            assertEquals(listOf(resource), result.value.resource)
        }

    @Test
    fun unsupportedRequestedTokenTypeIsRejected() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val result = createCommand(registry).execute(
                exchangeArgs(createTestJwt(mapOf("sub" to "user123"))).copy(requestedTokenType = TokenTypeIdentifier.ID_TOKEN),
            )
            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
        }

    @Test
    fun mayActRequiresMatchingIssuerAndSubject() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val subject = createTestJwt(mapOf("sub" to "user123", "may_act" to mapOf("iss" to "https://authorized-actor.example.test", "sub" to "same-actor")))
            val actor = createTestJwt(mapOf("iss" to testForeignIssuer, "sub" to "same-actor"))
            val result = createCommand(registry).execute(
                exchangeArgs(subject).copy(actorToken = actor, actorTokenType = TokenTypeIdentifier.ACCESS_TOKEN),
            )
            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
        }

    @Test
    fun mayActPreservesExistingActorChain() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val existingActor = mapOf("sub" to " prior-actor ", "iss" to " https://prior-issuer.example.test ", "act" to mapOf("sub" to "root-actor "))
            val subject = createTestJwt(
                mapOf(
                    "sub" to "user123",
                    "act" to existingActor,
                    "may_act" to mapOf("iss" to testForeignIssuer, "sub" to "current-actor"),
                ),
            )
            val actor = createTestJwt(mapOf("iss" to testForeignIssuer, "sub" to "current-actor", "client_id" to "current-actor-client", "tenant_id" to "tenant-a"))
            val result = createCommand(registry).execute(
                exchangeArgs(subject).copy(actorToken = actor, actorTokenType = TokenTypeIdentifier.ACCESS_TOKEN),
            )

            assertTrue(result.isOk)
            val current = result.value.actorClaim!!
            assertEquals("current-actor", current.sub)
            assertEquals(" prior-actor ", current.act!!.sub)
            assertEquals(" https://prior-issuer.example.test ", current.act!!.additionalClaims["iss"]!!.let { (it as JsonPrimitive).content })
            assertEquals("root-actor ", current.act!!.act!!.sub)
            assertEquals(testForeignIssuer, current.additionalClaims["iss"]?.let { (it as JsonPrimitive).content })
        }

    @Test
    fun existingActorWithoutActorTokenRemainsDelegation() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val existingActor = mapOf("sub" to "current-actor", "act" to mapOf("sub" to "root-actor"))
            val result = createCommand(registry).execute(
                exchangeArgs(createTestJwt(mapOf("sub" to "user123", "act" to existingActor))),
            )

            assertTrue(result.isOk)
            assertTrue(result.value.isDelegation)
            assertEquals("current-actor", result.value.actorSubject)
            assertEquals("current-actor", result.value.actorClaim?.sub)
            assertEquals("root-actor", result.value.actorClaim?.act?.sub)
        }

    @Test
    fun policyDelegationBuildsVerifiedCurrentActorAndPreservesHistory() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val subject = createTestJwt(
                mapOf(
                    "sub" to "platform-operator",
                    "iss" to testForeignIssuer,
                    "client_id" to "subject-login-client",
                    "act" to mapOf("sub" to "earlier-actor"),
                ),
            )
            val policy = object : TestPolicy {
                override suspend fun evaluate(request: AuthorizeTokenExchangeInput): IdkResult<PolicyDecision, AuthorizationServerError> =
                    Ok(
                        PolicyDecision(
                            allowed = true,
                            isDelegation = true,
                            grantedScope = null,
                            grantedAudience = listOf("enterprise-platform"),
                            actorClaimExtensions = mapOf(
                                "actor_context" to JsonPrimitive("organization-a"),
                                "purpose" to JsonPrimitive("support"),
                                "sub" to JsonPrimitive("untrusted-override"),
                                "iss" to JsonPrimitive("https://untrusted.example.test"),
                                "client_id" to JsonPrimitive("untrusted-client"),
                            ),
                        ),
                    )
            }

            val result = createCommand(registry, policy).execute(exchangeArgs(subject))

            assertTrue(result.isOk)
            val actor = result.value.actorClaim!!
            assertEquals("platform-operator", actor.sub)
            assertEquals(testForeignIssuer, actor.additionalClaims["iss"]?.let { (it as JsonPrimitive).content })
            assertEquals(tokenExchangeClient.clientId, actor.additionalClaims["client_id"]?.let { (it as JsonPrimitive).content })
            assertEquals("organization-a", actor.additionalClaims["actor_context"]?.let { (it as JsonPrimitive).content })
            assertEquals("support", actor.additionalClaims["purpose"]?.let { (it as JsonPrimitive).content })
            assertEquals("earlier-actor", actor.act?.sub)
        }

    @Test
    fun addingActorAtMaximumExistingChainDepthIsRejected() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val maximumDepthChain = (1..15).fold(mapOf<String, Any>("sub" to "root-actor")) { nested, depth ->
                mapOf("sub" to "actor-$depth", "act" to nested)
            }
            val subject = createTestJwt(mapOf("sub" to "user123", "act" to maximumDepthChain))
            val actor = createTestJwt(mapOf("iss" to testForeignIssuer, "sub" to "new-actor"))
            var policyInvoked = false
            val permissivePolicy = object : TestPolicy {
                override suspend fun evaluate(request: AuthorizeTokenExchangeInput): IdkResult<PolicyDecision, AuthorizationServerError> {
                    policyInvoked = true
                    return Ok(
                        PolicyDecision(
                            allowed = true,
                            isDelegation = true,
                            grantedScope = null,
                            grantedAudience = listOf("enterprise-platform"),
                        ),
                    )
                }
            }
            val result = createCommand(registry, permissivePolicy).execute(
                exchangeArgs(subject).copy(actorToken = actor, actorTokenType = TokenTypeIdentifier.ACCESS_TOKEN),
            )

            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
            assertFalse(policyInvoked, "Adding an actor beyond maximum depth must fail before policy")
        }

    @Test
    fun policyCreatedActorAllowsDepthSixteenAndRejectsDepthSeventeen() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val policy = object : TestPolicy {
                override suspend fun evaluate(request: AuthorizeTokenExchangeInput): IdkResult<PolicyDecision, AuthorizationServerError> =
                    Ok(
                        PolicyDecision(
                            allowed = true,
                            isDelegation = true,
                            grantedScope = null,
                            grantedAudience = listOf("enterprise-platform"),
                        ),
                    )
            }

            for ((priorActors, expectedOk) in listOf(15 to true, 16 to false)) {
                var history: Any = mapOf("sub" to "oldest")
                repeat(priorActors - 1) { index -> history = mapOf("sub" to "prior-$index", "act" to history) }
                val subject = createTestJwt(
                    mapOf("iss" to testForeignIssuer, "sub" to "current-subject", "act" to history),
                )
                val result = createCommand(registry, policy).execute(exchangeArgs(subject))

                assertEquals(expectedOk, result.isOk, "priorActors=$priorActors")
                if (expectedOk) {
                    var actor = result.value.actorClaim
                    var depth = 0
                    while (actor != null) {
                        depth++
                        actor = actor.act
                    }
                    assertEquals(16, depth)
                } else {
                    assertEquals("invalid_request", result.error.code)
                }
            }
        }

    @Test
    fun malformedMayActIsRejected() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val subject = createTestJwt(mapOf("sub" to "user123", "may_act" to mapOf("sub" to "actor-without-issuer")))
            val actor = createTestJwt(mapOf("iss" to testForeignIssuer, "sub" to "actor-without-issuer"))
            val result = createCommand(registry).execute(
                exchangeArgs(subject).copy(actorToken = actor, actorTokenType = TokenTypeIdentifier.ACCESS_TOKEN),
            )
            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
        }

    @Test
    fun deploymentPolicyCannotGrantAnUnregisteredAudience() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val permissivePolicy =
                object : TestPolicy {
                    override suspend fun evaluate(request: AuthorizeTokenExchangeInput): IdkResult<PolicyDecision, AuthorizationServerError> =
                        Ok(
                            PolicyDecision(
                                allowed = true,
                                isDelegation = false,
                                grantedScope = null,
                                grantedAudience = listOf("https://unregistered.example.test"),
                            ),
                        )
                }
            val result = createCommand(registry, policy = permissivePolicy).execute(
                exchangeArgs(createTestJwt(mapOf("sub" to "user123"))),
            )
            assertTrue(result.isErr)
            assertEquals("invalid_target", result.error.code)
        }

    @Test
    fun policyRefusalErrorsAreNormalizedAtTheTokenExchangeBoundary() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val refusedErrors = listOf(
                AuthorizationServerError.InvalidGrant(details = "policy refused"),
                AuthorizationServerError.AccessDenied(reason = "policy refused"),
            )
            for (policyError in refusedErrors) {
                val policy = object : TestPolicy {
                    override suspend fun evaluate(request: AuthorizeTokenExchangeInput): IdkResult<PolicyDecision, AuthorizationServerError> = Err(policyError)
                }
                val result = createCommand(registry, policy).execute(exchangeArgs(createTestJwt(mapOf("sub" to "user123"))))
                assertTrue(result.isErr)
                assertEquals("invalid_request", result.error.code)
            }

            val serverPathErrors = listOf(
                AuthorizationServerError.InvalidTarget(reason = "policy target refusal"),
                AuthorizationServerError.ServerError(details = "policy unavailable"),
                AuthorizationServerError.TemporarilyUnavailable(),
                AuthorizationServerError.StorageError(operation = "evaluate", details = "database details must not escape"),
            )
            for (policyError in serverPathErrors) {
                val policy = object : TestPolicy {
                    override suspend fun evaluate(request: AuthorizeTokenExchangeInput): IdkResult<PolicyDecision, AuthorizationServerError> = Err(policyError)
                }
                val result = createCommand(registry, policy).execute(exchangeArgs(createTestJwt(mapOf("sub" to "user123"))))
                assertTrue(result.isErr)
                val expectedErrorCode = if (policyError is AuthorizationServerError.StorageError) "server_error" else policyError.code
                assertEquals(expectedErrorCode, result.error.code)
                if (policyError is AuthorizationServerError.StorageError) {
                    assertFalse(result.error.message.defaultMessage.contains("database details"))
                }
            }

            val deniedPolicy = object : TestPolicy {
                override suspend fun evaluate(request: AuthorizeTokenExchangeInput): IdkResult<PolicyDecision, AuthorizationServerError> =
                    Ok(
                        PolicyDecision(
                            allowed = false,
                            isDelegation = false,
                            grantedScope = null,
                            grantedAudience = emptyList(),
                            denyReason = "deployment policy denied",
                        ),
                    )
            }
            val denied = createCommand(registry, deniedPolicy).execute(exchangeArgs(createTestJwt(mapOf("sub" to "user123"))))
            assertTrue(denied.isErr)
            assertEquals("invalid_request", denied.error.code)
        }

    @Test
    fun policyMustGrantEveryRequestedResourceAndAtLeastOneEffectiveTarget() =
        runTest {
            val resource = "https://resource.example.test"
            val client = tokenExchangeClient.copy(allowedAccessTokenAudiences = setOf("enterprise-platform", resource))
            val registry = setupClientRegistry(client)
            val omittingResourcePolicy = object : TestPolicy {
                override suspend fun evaluate(request: AuthorizeTokenExchangeInput): IdkResult<PolicyDecision, AuthorizationServerError> =
                    Ok(
                        PolicyDecision(
                            allowed = true,
                            isDelegation = false,
                            grantedScope = null,
                            grantedAudience = listOf("enterprise-platform"),
                        ),
                    )
            }
            val request = exchangeArgs(createTestJwt(mapOf("sub" to "user123"))).copy(resources = listOf(resource))
            val omitted = createCommand(registry, omittingResourcePolicy).execute(request)
            assertTrue(omitted.isErr)
            assertEquals("invalid_target", omitted.error.code)

            val emptyGrantPolicy = object : TestPolicy {
                override suspend fun evaluate(request: AuthorizeTokenExchangeInput): IdkResult<PolicyDecision, AuthorizationServerError> =
                    Ok(
                        PolicyDecision(
                            allowed = true,
                            isDelegation = false,
                            grantedScope = null,
                            grantedAudience = emptyList(),
                        ),
                    )
            }
            val empty = createCommand(registry, emptyGrantPolicy).execute(request)
            assertTrue(empty.isErr)
            assertEquals("invalid_target", empty.error.code)

            val noTargetsClient = tokenExchangeClient.copy(defaultAccessTokenAudience = null, allowedAccessTokenAudiences = emptySet())
            val noTargetsRegistry = setupClientRegistry(noTargetsClient)
            val noEffectiveTargets = createCommand(noTargetsRegistry).execute(
                exchangeArgs(createTestJwt(mapOf("sub" to "user123"))),
            )
            assertTrue(noEffectiveTargets.isErr)
            assertEquals("invalid_target", noEffectiveTargets.error.code)
        }

    @Test
    fun standardAuthorizationRejectsRatherThanFiltersUnregisteredTargets() =
        runTest {
            val subject =
                object : AnchoredSubjectToken {
                    override val issuer = TrustedIssuerRef(testForeignIssuer, TokenIssuerAnchor.ISSUER_TRUST)
                    override val subject = "subject"
                    override val tokenType = TokenTypeIdentifier.ACCESS_TOKEN
                    override val claims = buildJsonObject { put("sub", "subject") }
                }
            val result = StandardTokenExchangeProfile().authorize.execute(
                AuthorizeTokenExchangeInput(
                    clientId = tokenExchangeClient.clientId,
                    subject = subject,
                    actor = null,
                    requestedResources = listOf("https://unregistered.example.test"),
                    requestedAudiences = emptyList(),
                    requestedScope = null,
                    registeredTargets = setOf("enterprise-platform"),
                ),
            )
            assertTrue(result.isErr)
            assertEquals("invalid_target", result.error.code)
        }

    @Test
    fun mayActIdentityComparisonDoesNotTrimWhitespace() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val mismatches = listOf(
                mapOf("iss" to testForeignIssuer, "sub" to " same-actor "),
                mapOf("iss" to " $testForeignIssuer ", "sub" to "same-actor"),
            )
            for (actorClaims in mismatches) {
                val subject = createTestJwt(
                    mapOf("sub" to "user123", "may_act" to mapOf("iss" to testForeignIssuer, "sub" to "same-actor")),
                )
                val actor = createTestJwt(actorClaims)
                val result = createCommand(registry).execute(
                    exchangeArgs(subject).copy(actorToken = actor, actorTokenType = TokenTypeIdentifier.ACCESS_TOKEN),
                )
                assertTrue(result.isErr)
                assertEquals("invalid_request", result.error.code)
            }
        }

    @Test
    fun malformedSubjectActorClaimIsRejectedWithoutActorToken() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val deepChain = generateSequence(mapOf<String, Any>("sub" to "deep-actor")) { previous -> mapOf("sub" to "nested", "act" to previous) }
                .take(18)
                .last()
            val malformedClaims = listOf(
                mapOf<String, Any?>("sub" to "user123", "act" to null),
                mapOf("sub" to "user123", "act" to "not-an-object"),
                mapOf("sub" to "user123", "act" to mapOf("iss" to testForeignIssuer)),
                mapOf("sub" to "user123", "act" to deepChain),
            )
            for (claims in malformedClaims) {
                val token = createTestJwt(claims)
                if (claims["act"] == null && "act" in claims) {
                    val decoded = Json.parseToJsonElement(token.split(".")[1].decodeFromBase64Url().decodeToString()).jsonObject
                    assertTrue(decoded["act"] is JsonNull, "act=null vector must encode JSON null")
                }
                val result = createCommand(registry).execute(exchangeArgs(token))
                assertTrue(result.isErr)
                assertEquals("invalid_request", result.error.code)
            }
        }

    @Test
    fun rfc8693ResourceSyntaxAndActorParameterPairAreValidated() =
        runTest {
            val resource = "https://resource.example.test"
            val registry = setupClientRegistry(tokenExchangeClient.copy(allowedAccessTokenAudiences = setOf("enterprise-platform", resource)))
            val subject = createTestJwt(mapOf("sub" to "user123"))
            val invalidArgs = listOf(
                exchangeArgs(subject).copy(resources = listOf("relative/resource")),
                exchangeArgs(subject).copy(resources = listOf("https://resource.example.test/path#fragment")),
                exchangeArgs(subject).copy(actorToken = " ", actorTokenType = TokenTypeIdentifier.ACCESS_TOKEN),
                exchangeArgs(subject).copy(actorTokenType = TokenTypeIdentifier.ACCESS_TOKEN),
                exchangeArgs(subject).copy(audiences = listOf(" enterprise-platform ")),
            )

            for (args in invalidArgs) {
                val result = createCommand(registry).execute(args)
                assertTrue(result.isErr)
                assertEquals(if (args.audiences.isNotEmpty()) "invalid_target" else "invalid_request", result.error.code)
            }

            for (validResource in listOf("urn:example:resource", "https://resource.example.test")) {
                val validRegistry = setupClientRegistry(tokenExchangeClient.copy(allowedAccessTokenAudiences = setOf("enterprise-platform", validResource)))
                val validResult = createCommand(validRegistry).execute(exchangeArgs(subject).copy(resources = listOf(validResource)))
                assertTrue(validResult.isOk, "Valid absolute URI resource should be accepted: $validResource")
            }
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
                    ExchangeArgs(
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
                    ExchangeArgs(
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
                    ExchangeArgs(
                        subjectToken = subjectJwt,
                        subjectTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                        actorToken = null,
                        actorTokenType = null,
                        resources = emptyList(),
                        audiences = listOf("enterprise-platform"),
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
            val command = createCommand(registry, subjectTokenIssuerTrust = testForeignIssuerTrust)

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
                    ExchangeArgs(
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
    fun foreignTokenWithInvalidSignatureIsRejected() =
        runTest {
            val registry = setupClientRegistry(tokenExchangeClient)
            val command = createCommand(registry, jwtService = jwtService)

            val subjectJwt = createTestJwt(mapOf("sub" to "user123"))

            val result =
                command.execute(
                    ExchangeArgs(
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

            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
        }
}
