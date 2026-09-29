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

package com.sphereon.oauth2.jwt.validation.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.session.CommandLifecycleInterceptorChain
import com.sphereon.core.api.session.EmptyInterceptorChain
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.jose.jws.*
import com.sphereon.crypto.jose.jws.command.*
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwkOpts
import com.sphereon.oauth2.jwt.validation.AsJwtArtifactScope
import com.sphereon.oauth2.jwt.validation.AsIssuerTrustMaterial
import com.sphereon.oauth2.jwt.validation.AccessTokenValidationOptions
import com.sphereon.oauth2.jwt.validation.IdTokenValidationOptions
import com.sphereon.oauth2.jwt.validation.IdpConfig
import com.sphereon.oauth2.jwt.validation.IdpRegistry
import com.sphereon.oauth2.jwt.validation.JwtArtifactContext
import com.sphereon.oauth2.jwt.validation.JwtValidationConfig
import com.sphereon.oauth2.jwt.validation.JwtValidationError
import com.sphereon.oauth2.jwt.validation.JwtValidationErrorType
import com.sphereon.oauth2.jwt.validation.OidcDiscoveryMetadata
import com.sphereon.oauth2.jwt.validation.OidcDiscoveryService
import com.sphereon.oauth2.server.resource.command.VerifyJwtArgs
import com.sphereon.oauth2.server.resource.command.VerifyJwtCommand
import com.sphereon.oauth2.server.resource.command.StandardJwtArtifactContext
import com.sphereon.oauth2.server.resource.impl.command.VerifyJwtCommandImpl
import com.sphereon.oauth2.server.resource.error.ResourceServerError
import com.sphereon.oauth2.server.resource.model.TokenPayload
import com.sphereon.oauth2.common.model.CanonicalAuthorizationServerIssuer
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Instant

/**
 * Unit-test sweep for DefaultJwtValidationService.
 *
 * Style notes (intentional):
 * - Uses the `when (val result) { is Ok -> ...; is Err -> ... }` idiom to match
 *   the sibling DefaultIdpRegistryTest file so the whole test package reads the
 *   same way. The newer `.isOk` / `.isErr` idiom is fine in greenfield code, but
 *   mixing styles mid-package hurts readability more than it helps.
 * - No backticked test names — this module compiles to JS/Native too.
 * - Test tokens are built structurally (header.payload.signature) with a
 *   placeholder signature. Most service dispatch cases use a stub; focused
 *   ID-token and AS-artifact cases run VerifyJwtCommandImpl with a JOSE boundary fixture.
 */
class DefaultJwtValidationServiceTest {
    // ========== Common fixtures ==========

    private val keycloakIdp =
        IdpConfig.keycloak(
            id = "primary-keycloak",
            baseUrl = "https://auth.example.com",
            realm = "master",
            audience = "my-api",
        )

    private val keycloakIssuer = "https://auth.example.com/realms/master"

    // ========== Helpers ==========

    private fun buildJwt(payloadClaims: Map<String, JsonElement>, typ: String? = "at+jwt"): String {
        val header =
            buildJsonObject {
                put("alg", JsonPrimitive("RS256"))
                typ?.let { put("typ", JsonPrimitive(it)) }
            }
        val payload =
            JsonObject(payloadClaims)
        val headerPart = Json.encodeToString(JsonObject.serializer(), header).encodeToByteArray().encodeToBase64Url()
        val payloadPart = Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray().encodeToBase64Url()
        // Most validation tests stub the command; focused command-path tests use a JOSE boundary fixture.
        val signaturePart = "stub-signature".encodeToByteArray().encodeToBase64Url()
        return "$headerPart.$payloadPart.$signaturePart"
    }

    private fun jwtPayload(
        sub: String = "user-1",
        iss: String = keycloakIssuer,
        aud: List<String>? = listOf("my-api"),
        expSeconds: Long = 9_999_999_999L,
        iatSeconds: Long = 1_700_000_000L,
        scope: String? = "read write",
        clientId: String? = "client-1",
        jti: String? = "jti-1",
        additional: Map<String, String> = emptyMap(),
    ): TokenPayload.Jwt =
        TokenPayload.Jwt(
            sub = sub,
            iss = iss,
            aud = aud,
            exp = Instant.fromEpochSeconds(expSeconds),
            iat = Instant.fromEpochSeconds(iatSeconds),
            scope = scope,
            clientId = clientId,
            dpopJkt = null,
            jti = jti,
            additionalClaims = additional.mapValues { JsonPrimitive(it.value) },
        )

    private fun service(
        vararg idps: IdpConfig,
        strict: Boolean = true,
        defaultIdp: IdpConfig? = keycloakIdp,
        stub: StubVerifyJwtCommand,
        discovery: OidcDiscoveryService = StubOidcDiscoveryService.conventionBased(),
    ): DefaultJwtValidationService {
        val tenantIdps =
            idps
                .filter { it.id != defaultIdp?.id }
                .associateBy { "tenant-${it.id}" }
        val config =
            JwtValidationConfig(
                enabled = true,
                defaultIdp = defaultIdp,
                tenantIdps = tenantIdps,
                strictIssuerMatching = strict,
            )
        val registry = DefaultIdpRegistry(config)
        return DefaultJwtValidationService(
            verifyJwtCommand = stub,
            idpRegistry = registry,
            oidcDiscoveryService = discovery,
        )
    }

    @Test
    fun incompleteOrContradictoryPerCallTrustFailsBeforeAnyFallback() =
        runTest {
            val token = buildJwt(mapOf("iss" to JsonPrimitive(keycloakIssuer)))
            val invalidOptions =
                listOf(
                    AccessTokenValidationOptions(trustedIssuer = " ", trustedJwksUri = "https://keys.example/jwks"),
                    AccessTokenValidationOptions(trustedIssuer = keycloakIssuer, trustedJwksUri = " "),
                    AccessTokenValidationOptions(trustedIssuer = keycloakIssuer),
                    AccessTokenValidationOptions(trustedJwksUri = "https://keys.example/jwks"),
                    AccessTokenValidationOptions(
                        idpId = "configured-idp",
                        trustedIssuer = keycloakIssuer,
                        trustedJwksUri = "https://keys.example/jwks",
                    ),
                    AccessTokenValidationOptions(
                        tenantHint = "tenant-1",
                        trustedIssuer = keycloakIssuer,
                        trustedJwksUri = "https://keys.example/jwks",
                    ),
                    AccessTokenValidationOptions(
                        trustedIdentifier = ExternalIdentifierJwkOpts(
                            Jwk.fromJsonObject(
                                Json.parseToJsonElement("""{"kty":"RSA","n":"AQ","e":"Ag","kid":"local"}""").jsonObject,
                            ),
                        ),
                        trustedIssuer = keycloakIssuer,
                        trustedJwksUri = "https://keys.example/jwks",
                    ),
                )

            invalidOptions.forEach { options ->
                val registry = NoLookupIdpRegistry()
                val discovery = StubOidcDiscoveryService.failing(
                    JwtValidationError.discoveryFailed(keycloakIssuer, "must not be called"),
                )
                val verifier = StubVerifyJwtCommand.neverInvoked()
                val svc =
                    DefaultJwtValidationService(
                        verifyJwtCommand = verifier,
                        idpRegistry = registry,
                        oidcDiscoveryService = discovery,
                    )

                val result = svc.validateAccessToken(token, options)

                assertTrue(result.isErr)
                assertEquals(JwtValidationErrorType.IDP_CONFIGURATION_ERROR, result.error.type)
                assertEquals(0, registry.lookups)
                assertEquals(0, discovery.invocationCount)
                assertEquals(0, verifier.invocationCount)
            }
        }

    @Test
    fun testAsIssuedArtifactFailsClosedWithoutCallerEstablishedTrustMaterial() =
        runTest {
            val issuer = CanonicalAuthorizationServerIssuer.parse("https://as.example.com")
            val trustMaterial =
                AsIssuerTrustMaterial(
                    canonicalIssuer = issuer,
                    artifactScopes = setOf(AsJwtArtifactScope.ACCESS_TOKEN),
                )
            val stub = StubVerifyJwtCommand.neverInvoked()
            val svc = service(stub = stub)

            val result = svc.validateAsIssuedArtifact("not-a-jwt", trustMaterial, JwtArtifactContext.ACCESS_TOKEN)

            assertTrue(result.isErr)
            assertEquals("IDP_CONFIGURATION_ERROR", result.error.type.name)
            assertEquals(0, stub.invocationCount)
        }

    @Test
    fun testAsIssuedArtifactRejectsUnadmittedArtifactContextBeforeVerification() =
        runTest {
            val issuer = CanonicalAuthorizationServerIssuer.parse("https://as.example.com")
            val trustMaterial =
                AsIssuerTrustMaterial(
                    canonicalIssuer = issuer,
                    artifactScopes = setOf(AsJwtArtifactScope.ACCESS_TOKEN),
                )
            val stub = StubVerifyJwtCommand.neverInvoked()
            val svc = service(stub = stub)

            val result = svc.validateAsIssuedArtifact("not-a-jwt", trustMaterial, JwtArtifactContext.ID_TOKEN)

            assertTrue(result.isErr)
            assertEquals("VALIDATION_ERROR", result.error.type.name)
            assertEquals(0, stub.invocationCount)
        }

    @Test
    fun testAsIssuedArtifactUsesExplicitStandardContextForIdJarmAndLogout() =
        runTest {
            val issuer = CanonicalAuthorizationServerIssuer.parse("https://as.example.com")
            val now = kotlin.time.Clock.System.now().epochSeconds
            val contexts =
                mapOf(
                    JwtArtifactContext.ID_TOKEN to AsJwtArtifactScope.ID_TOKEN,
                    JwtArtifactContext.JARM_RESPONSE to AsJwtArtifactScope.JARM_RESPONSE,
                    JwtArtifactContext.LOGOUT_TOKEN to AsJwtArtifactScope.LOGOUT_TOKEN,
                )
            contexts.forEach { (context, scope) ->
                val claims =
                    buildMap {
                        put("iss", JsonPrimitive(issuer.value))
                        put("aud", JsonPrimitive("client"))
                        put("exp", JsonPrimitive(now + 600))
                        if (context == JwtArtifactContext.ID_TOKEN) {
                            put("sub", JsonPrimitive("client"))
                            put("iat", JsonPrimitive(now))
                        }
                        if (context == JwtArtifactContext.JARM_RESPONSE) {
                            put("code", JsonPrimitive("authorization-code"))
                        }
                        if (context == JwtArtifactContext.LOGOUT_TOKEN) {
                            put("jti", JsonPrimitive("logout-1"))
                            put("sid", JsonPrimitive("session-1"))
                            put("iat", JsonPrimitive(now))
                            put(
                                "events",
                                buildJsonObject {
                                    put("http://schemas.openid.net/event/backchannel-logout", JsonObject(emptyMap()))
                                },
                            )
                        }
                    }
                val compactJwt = buildJwt(claims, typ = if (context == JwtArtifactContext.LOGOUT_TOKEN) null else "JWT")
                val joseBoundary = SignatureBoundaryJwtService()
                val verifier = VerifyJwtCommandImpl(TestCommandSessionExecution, joseBoundary)
                val material =
                    AsIssuerTrustMaterial(
                        canonicalIssuer = issuer,
                        artifactScopes = setOf(scope),
                        trustedIdentifier =
                            ExternalIdentifierJwkOpts(
                                Jwk.fromJsonObject(
                                    Json.parseToJsonElement("""{"kty":"RSA","n":"AQ","e":"Ag","kid":"as-key"}""").jsonObject,
                                ),
                            ),
                    )
                val service =
                    DefaultJwtValidationService(
                        verifyJwtCommand = verifier,
                        idpRegistry = NoLookupIdpRegistry(),
                        oidcDiscoveryService = StubOidcDiscoveryService.conventionBased(),
                    )
                val result = service.validateAsIssuedArtifact(compactJwt, material, context)

                assertTrue(result.isOk, "expected $context artifact verification success")
                assertEquals(1, joseBoundary.verificationCount, "$context must run through VerifyJwtCommandImpl")
            }
        }

    @Test
    fun realArtifactVerificationRejectsIssuerAudienceNonceAndContextMismatches() =
        runTest {
            val issuer = CanonicalAuthorizationServerIssuer.parse("https://as.example.com")
            val now = kotlin.time.Clock.System.now().epochSeconds
            val baseClaims = mapOf(
                "iss" to JsonPrimitive(issuer.value),
                "sub" to JsonPrimitive("client"),
                "aud" to JsonPrimitive("client"),
                "exp" to JsonPrimitive(now + 600),
                "iat" to JsonPrimitive(now),
            )
            suspend fun verify(context: JwtArtifactContext, claims: Map<String, JsonElement>, typ: String? = "JWT"): Boolean {
                val verifier = VerifyJwtCommandImpl(TestCommandSessionExecution, SignatureBoundaryJwtService())
                val service = DefaultJwtValidationService(verifier, NoLookupIdpRegistry(), StubOidcDiscoveryService.conventionBased())
                val scope = when (context) {
                    JwtArtifactContext.ID_TOKEN -> AsJwtArtifactScope.ID_TOKEN
                    JwtArtifactContext.JARM_RESPONSE -> AsJwtArtifactScope.JARM_RESPONSE
                    JwtArtifactContext.LOGOUT_TOKEN -> AsJwtArtifactScope.LOGOUT_TOKEN
                    JwtArtifactContext.ACCESS_TOKEN -> AsJwtArtifactScope.ACCESS_TOKEN
                }
                val material = AsIssuerTrustMaterial(
                    canonicalIssuer = issuer,
                    artifactScopes = setOf(scope),
                    trustedIdentifier = ExternalIdentifierJwkOpts(
                        Jwk.fromJsonObject(Json.parseToJsonElement("""{"kty":"RSA","n":"AQ","e":"Ag","kid":"as-key"}""").jsonObject),
                    ),
                )
                return service.validateAsIssuedArtifact(buildJwt(claims, typ), material, context).isOk
            }

            assertTrue(!verify(JwtArtifactContext.ID_TOKEN, baseClaims + ("iss" to JsonPrimitive("https://other-as.example"))))
            assertTrue(!verify(JwtArtifactContext.ID_TOKEN, baseClaims - "aud"), "ID_TOKEN needs aud")
            assertTrue(!verify(JwtArtifactContext.ID_TOKEN, baseClaims, typ = "at+jwt"), "access typ cannot cross into ID_TOKEN")
            assertTrue(!verify(JwtArtifactContext.JARM_RESPONSE, baseClaims), "JARM requires a supported response field")
            assertTrue(!verify(JwtArtifactContext.LOGOUT_TOKEN, baseClaims + mapOf(
                "sid" to JsonPrimitive("session"),
                "jti" to JsonPrimitive("logout-2"),
                "nonce" to JsonPrimitive("forbidden"),
                "events" to buildJsonObject {
                    put("http://schemas.openid.net/event/backchannel-logout", JsonObject(emptyMap()))
                },
            ), typ = null), "logout nonce is forbidden")
        }

    @Test
    fun testOidcIdpDiscoversJwksWhenUriIsAbsent() =
        runTest {
            val issuer = "https://tenant-as.example.com"
            val discoveredJwksUri = "$issuer/keys/jwks.json"
            val idp = IdpConfig.oidc(id = "tenant-as", issuer = issuer)
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(issuer),
                        "sub" to JsonPrimitive("service-client"),
                    ),
                )
            val stub = StubVerifyJwtCommand.returning(token, Ok(jwtPayload(iss = issuer)))
            val discovery = StubOidcDiscoveryService.returning(issuer, discoveredJwksUri)
            val svc = service(defaultIdp = idp, stub = stub, discovery = discovery)

            val result = svc.validateAccessToken(token)

            assertTrue(result.isOk)
            assertEquals(discoveredJwksUri, stub.lastArgs?.jwksUri)
            assertEquals(1, discovery.invocationCount)
        }

    @Test
    fun testOidcDiscoveryFailureDoesNotFallBackToManagedKmsLookup() =
        runTest {
            val issuer = "https://tenant-as.example.com"
            val idp = IdpConfig.oidc(id = "tenant-as", issuer = issuer)
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(issuer),
                        "sub" to JsonPrimitive("service-client"),
                    ),
                )
            val stub = StubVerifyJwtCommand.neverInvoked()
            val discovery =
                StubOidcDiscoveryService.failing(
                    JwtValidationError.discoveryFailed(issuer, "metadata unavailable"),
                )
            val svc = service(defaultIdp = idp, stub = stub, discovery = discovery)

            val result = svc.validateAccessToken(token)

            assertTrue(result.isErr)
            assertEquals(JwtValidationErrorType.DISCOVERY_FAILED, result.error.type)
            assertEquals(0, stub.invocationCount)
        }

    @Test
    fun testCallerEstablishedJwkSkipsOidcDiscovery() =
        runTest {
            val issuer = "https://tenant-as.example.com"
            val idp = IdpConfig.oidc(id = "tenant-as", issuer = issuer)
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(issuer),
                        "sub" to JsonPrimitive("service-client"),
                    ),
                )
            val stub = StubVerifyJwtCommand.returning(token, Ok(jwtPayload(iss = issuer)))
            val discovery = StubOidcDiscoveryService.failing(
                JwtValidationError.discoveryFailed(issuer, "discovery must not be called"),
            )
            val trusted = ExternalIdentifierJwkOpts(
                Jwk.fromJsonObject(
                    Json.parseToJsonElement("""{"kty":"RSA","n":"AQ","e":"Ag","kid":"managed-key"}""").jsonObject,
                ),
            )
            val svc = service(defaultIdp = idp, stub = stub, discovery = discovery)

            val result = svc.validateAccessToken(token, AccessTokenValidationOptions(trustedIdentifier = trusted))

            assertTrue(result.isOk)
            assertEquals(0, discovery.invocationCount)
            assertEquals(null, stub.lastArgs?.jwksUri)
            assertNotNull(stub.lastArgs?.trustedIdentifier)
        }

    @Test
    fun perCallIssuerTrustRejectsIssuerMismatchBeforeSignatureVerification() =
        runTest {
            val trustedIssuer = "https://tenant-one.example.com/as"
            val token = buildJwt(mapOf("iss" to JsonPrimitive("https://tenant-two.example.com/as")))
            val stub = StubVerifyJwtCommand.neverInvoked()
            val registry = NoLookupIdpRegistry()
            val discovery = StubOidcDiscoveryService.failing(
                JwtValidationError.discoveryFailed(trustedIssuer, "must not be called"),
            )
            val svc =
                DefaultJwtValidationService(
                    verifyJwtCommand = stub,
                    idpRegistry = registry,
                    oidcDiscoveryService = discovery,
                )

            val result =
                svc.validateAccessToken(
                    token,
                    AccessTokenValidationOptions(
                        trustedIssuer = trustedIssuer,
                        trustedJwksUri = "$trustedIssuer/.well-known/jwks.json",
                    ),
                )

            assertTrue(result.isErr)
            assertEquals(JwtValidationErrorType.UNTRUSTED_ISSUER, result.error.type)
            assertEquals(0, registry.lookups)
            assertEquals(0, discovery.invocationCount)
            assertEquals(0, stub.invocationCount)
        }

    @Test
    fun perCallIssuerTrustRequiresIssuerAndJwksTogether() =
        runTest {
            val token = buildJwt(mapOf("iss" to JsonPrimitive(keycloakIssuer)))
            val stub = StubVerifyJwtCommand.neverInvoked()
            val svc = service(stub = stub)

            val result =
                svc.validateAccessToken(
                    token,
                    AccessTokenValidationOptions(trustedIssuer = keycloakIssuer),
                )

            assertTrue(result.isErr)
            assertEquals(JwtValidationErrorType.IDP_CONFIGURATION_ERROR, result.error.type)
            assertEquals(0, stub.invocationCount)
        }

    @Test
    fun concurrentPerCallIssuerTrustUsesEachRequestsIssuerAndJwks() =
        runTest {
            val tenantTrust =
                listOf(
                    Triple("https://tenant-one.example.com/as", "https://tenant-one.example.com/keys/jwks.json", "tenant-one-api"),
                    Triple("https://tenant-two.example.com/as", "https://tenant-two.example.com/keys/jwks.json", "tenant-two-api"),
                )
            val requestsAtVerifier = mutableListOf<VerifyJwtArgs>()
            val allRequestsArrived = CompletableDeferred<Unit>()
            val releaseVerification = CompletableDeferred<Unit>()
            val arrivalCount = atomic(0)
            val stub =
                StubVerifyJwtCommand.forPerCallIssuerTrust { args ->
                    requestsAtVerifier += args
                    if (arrivalCount.incrementAndGet() == tenantTrust.size) {
                        allRequestsArrived.complete(Unit)
                    }
                    releaseVerification.await()
                    Ok(jwtPayload(iss = args.authorizationServer))
                }
            val svc = service(stub = stub)
            val validations =
                tenantTrust.mapIndexed { index, (issuer, jwksUri, audience) ->
                    val token = buildJwt(mapOf("iss" to JsonPrimitive(issuer), "sub" to JsonPrimitive("tenant-$index")))
                    async {
                        svc.validateAccessToken(
                            token,
                            AccessTokenValidationOptions(
                                expectedAudience = audience,
                                trustedIssuer = issuer,
                                trustedJwksUri = jwksUri,
                            ),
                        )
                    }
                }
            val wrongIssuerValidation =
                async {
                    svc.validateAccessToken(
                        buildJwt(mapOf("iss" to JsonPrimitive(tenantTrust[1].first))),
                        AccessTokenValidationOptions(
                            expectedAudience = tenantTrust[0].third,
                            trustedIssuer = tenantTrust[0].first,
                            trustedJwksUri = tenantTrust[0].second,
                        ),
                    )
                }

            allRequestsArrived.await()
            assertEquals(
                tenantTrust.map { Triple(it.first, it.second, it.third) }.toSet(),
                requestsAtVerifier.map { Triple(it.authorizationServer, it.jwksUri, it.expectedAudience) }.toSet(),
            )
            assertTrue(wrongIssuerValidation.await().isErr)
            releaseVerification.complete(Unit)
            validations.awaitAll().forEach { result -> assertTrue(result.isOk) }
        }

    // ========== 1. Happy path access token ==========

    @Test
    fun testValidAccessTokenHappyPath() =
        runTest {
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(keycloakIssuer),
                        "sub" to JsonPrimitive("user-42"),
                        "aud" to JsonPrimitive("my-api"),
                        "exp" to JsonPrimitive(9_999_999_999L),
                        "iat" to JsonPrimitive(1_700_000_000L),
                    ),
                )
            val payload =
                jwtPayload(
                    sub = "user-42",
                    iss = keycloakIssuer,
                    aud = listOf("my-api"),
                    scope = "read write admin",
                )
            val stub = StubVerifyJwtCommand.returning(token, Ok(payload))
            val svc = service(stub = stub)

            when (val result = svc.validateAccessToken(token)) {
                is Ok -> {
                    val validated = result.value
                    assertEquals("user-42", validated.subject)
                    assertEquals(keycloakIssuer, validated.issuer)
                    assertEquals(listOf("my-api"), validated.audiences)
                    assertEquals(9_999_999_999L, validated.expiresAt)
                    assertEquals(1_700_000_000L, validated.issuedAt)
                    assertEquals(setOf("read", "write", "admin"), validated.scopes)
                    assertEquals("client-1", validated.clientId)
                    assertEquals("jti-1", validated.jwtId)
                    assertEquals(token, validated.rawToken)
                    assertEquals("primary-keycloak", validated.idpId)
                    // Tenant resolution is pipeline-level; the validated-token type no longer
                    // carries a tenant field (FU-9).
                }

                is Err -> {
                    fail("Expected Ok but got Err: ${result.error}")
                }
            }
        }

    // ========== 2. Happy path id token ==========

    @Test
    fun testValidIdTokenHappyPathWithNonce() =
        runTest {
            val expectedNonce = "nonce-abc"
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(keycloakIssuer),
                        "sub" to JsonPrimitive("user-42"),
                        "aud" to JsonPrimitive("my-api"),
                        "exp" to JsonPrimitive(9_999_999_999L),
                        "iat" to JsonPrimitive(1_700_000_000L),
                        "nonce" to JsonPrimitive(expectedNonce),
                        "name" to JsonPrimitive("Alice Example"),
                        "email" to JsonPrimitive("alice@example.com"),
                        "email_verified" to JsonPrimitive(true),
                    ),
                    typ = "JWT",
                )
            val payload =
                jwtPayload(
                    sub = "user-42",
                    aud = listOf("my-api"),
                    additional =
                        mapOf(
                            "nonce" to expectedNonce,
                            "name" to "Alice Example",
                            "email" to "alice@example.com",
                            "email_verified" to "true",
                            "preferred_username" to "alice",
                        ),
                )
            val stub = StubVerifyJwtCommand.returning(token, Ok(payload))
            val svc = service(stub = stub)

            when (
                val result =
                    svc.validateIdToken(
                        token,
                        IdTokenValidationOptions(expectedNonce = expectedNonce),
                    )
            ) {
                is Ok -> {
                    val id = result.value
                    assertEquals("user-42", id.subject)
                    assertEquals(expectedNonce, id.nonce)
                    assertEquals("Alice Example", id.name)
                    assertEquals("alice@example.com", id.email)
                    assertEquals(true, id.emailVerified)
                    assertEquals("alice", id.preferredUsername)
                    assertEquals("primary-keycloak", id.idpId)
                }

                is Err -> {
                    fail("Expected Ok but got Err: ${result.error}")
                }
            }
            assertEquals(StandardJwtArtifactContext.ID_TOKEN, stub.lastStandardContext)
        }

    @Test
    fun validGenericIdTokenUsesRealTypedVerificationCommand() =
        runTest {
            val now = kotlin.time.Clock.System.now().epochSeconds
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(keycloakIssuer),
                        "sub" to JsonPrimitive("user-42"),
                        "aud" to JsonPrimitive("my-api"),
                        "exp" to JsonPrimitive(now + 600),
                        "iat" to JsonPrimitive(now),
                        "nonce" to JsonPrimitive("nonce-typed"),
                    ),
                    typ = "JWT",
                )
            val joseBoundary = SignatureBoundaryJwtService()
            val verifier = VerifyJwtCommandImpl(TestCommandSessionExecution, joseBoundary)
            val service =
                DefaultJwtValidationService(
                    verifyJwtCommand = verifier,
                    idpRegistry =
                        DefaultIdpRegistry(
                            JwtValidationConfig(enabled = true, defaultIdp = keycloakIdp, strictIssuerMatching = true),
                        ),
                    oidcDiscoveryService = StubOidcDiscoveryService.conventionBased(),
                )

            val result = service.validateIdToken(token, IdTokenValidationOptions(expectedNonce = "nonce-typed"))

            assertTrue(result.isOk, "generic JWT ID token should use typed standard verification")
            assertEquals("user-42", result.value.subject)
            assertEquals("nonce-typed", result.value.nonce)
            assertEquals(1, joseBoundary.verificationCount)
        }

    @Test
    fun realIdTokenVerifierRejectsAudienceAndNonceMismatch() =
        runTest {
            val now = kotlin.time.Clock.System.now().epochSeconds
            suspend fun validate(audience: String, nonce: String): Boolean {
                val token = buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(keycloakIssuer),
                        "sub" to JsonPrimitive("user-42"),
                        "aud" to JsonPrimitive(audience),
                        "exp" to JsonPrimitive(now + 600),
                        "iat" to JsonPrimitive(now),
                        "nonce" to JsonPrimitive(nonce),
                    ),
                    typ = "JWT",
                )
                val verifier = VerifyJwtCommandImpl(TestCommandSessionExecution, SignatureBoundaryJwtService())
                val service = DefaultJwtValidationService(
                    verifier,
                    DefaultIdpRegistry(JwtValidationConfig(enabled = true, defaultIdp = keycloakIdp, strictIssuerMatching = true)),
                    StubOidcDiscoveryService.conventionBased(),
                )
                return service.validateIdToken(token, IdTokenValidationOptions(expectedNonce = "expected-nonce")).isOk
            }
            assertTrue(!validate("wrong-client", "expected-nonce"), "ID token audience mismatch must fail")
            assertTrue(!validate("my-api", "wrong-nonce"), "ID token nonce mismatch must fail")
        }

    @Test
    fun testIdTokenNonceMismatchReturnsValidationError() =
        runTest {
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(keycloakIssuer),
                        "sub" to JsonPrimitive("user-42"),
                    ),
                    typ = "JWT",
                )
            val payload =
                jwtPayload(
                    additional = mapOf("nonce" to "wrong-nonce"),
                )
            val stub = StubVerifyJwtCommand.returning(token, Ok(payload))
            val svc = service(stub = stub)

            when (
                val result =
                    svc.validateIdToken(
                        token,
                        IdTokenValidationOptions(expectedNonce = "expected-nonce"),
                    )
            ) {
                is Ok -> fail("Expected Err but got Ok=${result.value}")
                is Err -> assertEquals(JwtValidationErrorType.VALIDATION_ERROR, result.error.type)
            }
        }

    // ========== 3. Unknown issuer, strict mode ==========

    @Test
    fun testUnknownIssuerInStrictModeReturnsUntrustedError() =
        runTest {
            val unknownIssuer = "https://evil.example.com"
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(unknownIssuer),
                        "sub" to JsonPrimitive("attacker"),
                    ),
                )
            // Stub should NEVER be consulted in strict mode when issuer is unknown.
            val stub = StubVerifyJwtCommand.neverInvoked()
            val svc = service(stub = stub, strict = true)

            when (val result = svc.validateAccessToken(token)) {
                is Ok -> fail("Expected Err(UntrustedIssuer) but got Ok=${result.value}")
                is Err -> assertEquals(JwtValidationErrorType.UNTRUSTED_ISSUER, result.error.type)
            }
            assertEquals(0, stub.invocationCount, "VerifyJwtCommand must not run for untrusted issuer in strict mode")
        }

    // ========== 4. Unknown issuer, lax mode (falls back to default IdP) ==========

    @Test
    fun testUnknownIssuerInLaxModeFallsBackToDefaultIdp() =
        runTest {
            val unknownIssuer = "https://unrecognised.example.com"
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(unknownIssuer),
                        "sub" to JsonPrimitive("user-lax"),
                        "aud" to JsonPrimitive(keycloakIdp.audience!!),
                    ),
                )
            // The stub echoes a payload whose iss/aud match the default IdP, simulating a
            // trusted downstream verify step that re-issued/accepted the token under the
            // default IdP's audience.
            val payload =
                jwtPayload(
                    sub = "user-lax",
                    iss = keycloakIssuer,
                    aud = listOf(keycloakIdp.audience!!),
                )
            val stub = StubVerifyJwtCommand.returning(token, Ok(payload))
            val svc = service(stub = stub, strict = false)

            when (val result = svc.validateAccessToken(token)) {
                is Ok -> {
                    assertEquals("primary-keycloak", result.value.idpId)
                    // Verify the default IdP's audience was passed through to VerifyJwtCommand.
                    val args = stub.lastArgs
                    assertNotNull(args)
                    assertEquals(keycloakIdp.issuer, args.authorizationServer)
                    assertEquals(keycloakIdp.audience, args.expectedAudience)
                }

                is Err -> {
                    fail("Expected Ok (lax fallback) but got Err: ${result.error}")
                }
            }
        }

    // ========== 5. Expired token ==========

    @Test
    fun testExpiredTokenMapsToExpiredError() =
        runTest {
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(keycloakIssuer),
                        "sub" to JsonPrimitive("user-1"),
                    ),
                )
            val expiredError =
                IdkError.fromDTO(
                    ResourceServerError.InvalidToken.Expired(expiresAt = 1_700_000_000L),
                )
            val stub = StubVerifyJwtCommand.returning(token, Err(expiredError))
            val svc = service(stub = stub)

            when (val result = svc.validateAccessToken(token)) {
                is Ok -> fail("Expected Err(TOKEN_EXPIRED) but got Ok=${result.value}")
                is Err -> assertEquals(JwtValidationErrorType.TOKEN_EXPIRED, result.error.type)
            }
        }

    // ========== 6. Wrong audience ==========

    @Test
    fun testWrongAudienceMapsToInvalidAudienceError() =
        runTest {
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(keycloakIssuer),
                        "sub" to JsonPrimitive("user-1"),
                        "aud" to JsonPrimitive("other-api"),
                    ),
                )
            val mismatchError =
                IdkError.fromDTO(
                    ResourceServerError.AudienceMismatch(
                        expected = "my-api",
                        actual = listOf("other-api"),
                    ),
                )
            val stub = StubVerifyJwtCommand.returning(token, Err(mismatchError))
            val svc = service(stub = stub)

            when (val result = svc.validateAccessToken(token)) {
                is Ok -> {
                    fail("Expected Err(INVALID_AUDIENCE) but got Ok=${result.value}")
                }

                is Err -> {
                    assertEquals(JwtValidationErrorType.INVALID_AUDIENCE, result.error.type)
                    assertEquals("my-api", result.error.audience)
                }
            }
        }

    // ========== 7. Malformed JWT (wrong segment count) ==========

    @Test
    fun testMalformedJwtReturnsInvalidFormatWithoutInvokingVerifyCommand() =
        runTest {
            val malformed = "not-a-real-jwt"
            val stub = StubVerifyJwtCommand.neverInvoked()
            val svc = service(stub = stub)

            when (val result = svc.validateAccessToken(malformed)) {
                is Ok -> fail("Expected Err but got Ok=${result.value}")
                is Err -> assertEquals(JwtValidationErrorType.INVALID_TOKEN_FORMAT, result.error.type)
            }
            assertEquals(0, stub.invocationCount, "VerifyJwtCommand must not run for structurally invalid tokens")
        }

    // ========== 8. Malformed base64 payload ==========

    @Test
    fun testMalformedBase64PayloadReturnsInvalidFormatFromExtractClaims() =
        runTest {
            // Three dot-separated parts, but the middle isn't valid base64url-encoded JSON.
            val malformed = "aGVhZGVy.@@@not-base64@@@.c2ln"
            val stub = StubVerifyJwtCommand.neverInvoked()
            val svc = service(stub = stub)

            when (val result = svc.extractClaims(malformed)) {
                is Ok -> fail("Expected Err(INVALID_TOKEN_FORMAT) but got Ok=${result.value}")
                is Err -> assertEquals(JwtValidationErrorType.INVALID_TOKEN_FORMAT, result.error.type)
            }
        }

    @Test
    fun testPayloadNotJsonObjectReturnsInvalidFormat() =
        runTest {
            // Base64url-encoded JSON string literal ("not-an-object"), not a JSON object.
            val headerPart =
                "{\"alg\":\"RS256\"}".encodeToByteArray().encodeToBase64Url()
            val payloadPart =
                "\"not-an-object\"".encodeToByteArray().encodeToBase64Url()
            val sigPart = "sig".encodeToByteArray().encodeToBase64Url()
            val token = "$headerPart.$payloadPart.$sigPart"
            val stub = StubVerifyJwtCommand.neverInvoked()
            val svc = service(stub = stub)

            when (val result = svc.extractClaims(token)) {
                is Ok -> fail("Expected Err(INVALID_TOKEN_FORMAT) but got Ok=${result.value}")
                is Err -> assertEquals(JwtValidationErrorType.INVALID_TOKEN_FORMAT, result.error.type)
            }
        }

    // ========== 9. Nested claims access via extractClaims ==========

    @Test
    fun testExtractClaimsPreservesNestedJsonObjects() =
        runTest {
            val realmAccess =
                buildJsonObject {
                    put("roles", Json.parseToJsonElement("[\"admin\",\"developer\"]"))
                }
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(keycloakIssuer),
                        "sub" to JsonPrimitive("user-1"),
                        "aud" to JsonPrimitive("my-api"),
                        "exp" to JsonPrimitive(9_999_999_999L),
                        "iat" to JsonPrimitive(1_700_000_000L),
                        "realm_access" to realmAccess,
                    ),
                )
            val stub = StubVerifyJwtCommand.neverInvoked()
            val svc = service(stub = stub)

            when (val result = svc.extractClaims(token)) {
                is Ok -> {
                    val claims = result.value
                    assertEquals(keycloakIssuer, claims.issuer)
                    assertEquals("user-1", claims.subject)
                    assertEquals(listOf("my-api"), claims.audiences)
                    assertEquals(9_999_999_999L, claims.expiresAt)
                    val nested = claims.payload["realm_access"]
                    assertNotNull(nested, "realm_access claim should survive extraction")
                    // Structure must survive as JsonObject with nested array.
                    val nestedObj = nested.jsonObject
                    val roles = nestedObj["roles"]
                    assertNotNull(roles, "realm_access.roles should be present")
                    val rolesArray = roles as kotlinx.serialization.json.JsonArray
                    assertEquals(2, rolesArray.size)
                    assertEquals("admin", rolesArray[0].jsonPrimitive.content)
                    assertEquals("developer", rolesArray[1].jsonPrimitive.content)
                }

                is Err -> {
                    fail("Expected Ok but got Err: ${result.error}")
                }
            }
        }

    // ========== 10. Required claim missing ==========

    @Test
    fun testRequiredClaimMissingReturnsMissingClaimError() =
        runTest {
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(keycloakIssuer),
                        "sub" to JsonPrimitive("user-1"),
                    ),
                )
            val payload =
                jwtPayload(
                    sub = "user-1",
                    additional = mapOf("other_claim" to "other-value"),
                )
            val stub = StubVerifyJwtCommand.returning(token, Ok(payload))
            val svc = service(stub = stub)

            when (
                val result =
                    svc.validateAccessToken(
                        token,
                        AccessTokenValidationOptions(requiredClaims = listOf("tenant_id")),
                    )
            ) {
                is Ok -> {
                    fail("Expected Err(MISSING_REQUIRED_CLAIM) but got Ok=${result.value}")
                }

                is Err -> {
                    assertEquals(JwtValidationErrorType.MISSING_REQUIRED_CLAIM, result.error.type)
                    assertEquals("tenant_id", result.error.claim)
                }
            }
        }

    @Test
    fun testRequiredClaimPresentIsOk() =
        runTest {
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(keycloakIssuer),
                        "sub" to JsonPrimitive("user-1"),
                    ),
                )
            val payload =
                jwtPayload(
                    sub = "user-1",
                    additional = mapOf("tenant_id" to "acme"),
                )
            val stub = StubVerifyJwtCommand.returning(token, Ok(payload))
            val svc = service(stub = stub)

            when (
                val result =
                    svc.validateAccessToken(
                        token,
                        AccessTokenValidationOptions(requiredClaims = listOf("tenant_id")),
                    )
            ) {
                is Ok -> assertEquals("user-1", result.value.subject)
                is Err -> fail("Expected Ok but got Err: ${result.error}")
            }
        }

    @Test
    fun testMissingRequiredScopeReturnsMissingClaimError() =
        runTest {
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(keycloakIssuer),
                        "sub" to JsonPrimitive("user-1"),
                    ),
                )
            val payload = jwtPayload(scope = "read")
            val stub = StubVerifyJwtCommand.returning(token, Ok(payload))
            val svc = service(stub = stub)

            when (
                val result =
                    svc.validateAccessToken(
                        token,
                        AccessTokenValidationOptions(requiredScopes = setOf("read", "admin")),
                    )
            ) {
                is Ok -> fail("Expected Err for missing scope but got Ok=${result.value}")
                is Err -> assertEquals(JwtValidationErrorType.MISSING_REQUIRED_CLAIM, result.error.type)
            }
        }

    // ========== 11. Concurrent validation stress (minimal) ==========

    @Test
    fun testConcurrentValidationProducesConsistentOkResults() =
        runTest {
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(keycloakIssuer),
                        "sub" to JsonPrimitive("user-concurrent"),
                        "aud" to JsonPrimitive("my-api"),
                    ),
                )
            val payload = jwtPayload(sub = "user-concurrent")
            val stub = StubVerifyJwtCommand.returning(token, Ok(payload))
            val svc = service(stub = stub)

            val concurrentCount = 20
            val deferred =
                (1..concurrentCount).map {
                    async { svc.validateAccessToken(token) }
                }
            val results = deferred.awaitAll()

            assertEquals(concurrentCount, results.size)
            results.forEach { r ->
                when (r) {
                    is Ok -> assertEquals("user-concurrent", r.value.subject)
                    is Err -> fail("Concurrent validation produced Err: ${r.error}")
                }
            }
            // All coroutines exercised the stub.
            assertEquals(concurrentCount, stub.invocationCount)
        }

    @Test
    fun testConcurrentValidationWithIdpRegistryRegistrationDoesNotCrash() =
        runTest {
            // AppScope IdpRegistry is mutable (registerIdp). Hammer it while validation
            // runs to catch ConcurrentModification-style crashes from the IDK code path.
            val token =
                buildJwt(
                    mapOf(
                        "iss" to JsonPrimitive(keycloakIssuer),
                        "sub" to JsonPrimitive("user-concurrent"),
                    ),
                )
            val payload = jwtPayload(sub = "user-concurrent")
            val stub = StubVerifyJwtCommand.returning(token, Ok(payload))
            val config =
                JwtValidationConfig(
                    enabled = true,
                    defaultIdp = keycloakIdp,
                )
            val registry = DefaultIdpRegistry(config)
            val svc =
                DefaultJwtValidationService(
                    verifyJwtCommand = stub,
                    idpRegistry = registry,
                    oidcDiscoveryService = StubOidcDiscoveryService.conventionBased(),
                )

            val validations =
                (1..10).map {
                    async { svc.validateAccessToken(token) }
                }
            val registrations =
                (1..10).map { i ->
                    async {
                        registry.registerIdp(
                            IdpConfig.oidc(
                                id = "dynamic-$i",
                                issuer = "https://dynamic-$i.example.com",
                                audience = "api-$i",
                            ),
                        )
                    }
                }
            (validations + registrations).awaitAll()

            validations.awaitAll().forEach { r ->
                when (r) {
                    is Ok -> assertEquals("user-concurrent", r.value.subject)
                    is Err -> fail("Concurrent validation produced Err: ${r.error}")
                }
            }
        }
}

private class NoLookupIdpRegistry : IdpRegistry {
    var lookups = 0
        private set

    private fun unexpectedLookup(): IdkResult<IdpConfig, JwtValidationError> {
        lookups++
        return Err(JwtValidationError.idpConfigurationError("registry lookup was not expected"))
    }

    override fun getDefaultIdp() = unexpectedLookup()

    override fun getIdpForTenant(tenantId: String) = unexpectedLookup()

    override fun getIdpByIssuer(issuer: String) = unexpectedLookup()

    override fun getIdpById(idpId: String) = unexpectedLookup()

    override fun getAllIdps(): List<IdpConfig> = emptyList()

    override fun isTrustedIssuer(issuer: String): Boolean = false

    override fun registerIdp(config: IdpConfig) = Unit

    override fun registerTenantIdp(
        tenantId: String,
        config: IdpConfig,
    ) = Unit

    override fun removeIdp(idpId: String): Boolean = false
}

private class StubOidcDiscoveryService(
    private val response: (String) -> IdkResult<OidcDiscoveryMetadata, JwtValidationError>,
) : OidcDiscoveryService {
    private val invocations = atomic(0)

    val invocationCount: Int get() = invocations.value

    override suspend fun discover(issuer: String): IdkResult<OidcDiscoveryMetadata, JwtValidationError> {
        invocations.incrementAndGet()
        return response(issuer)
    }

    override suspend fun getMetadata(issuer: String): IdkResult<OidcDiscoveryMetadata, JwtValidationError> = discover(issuer)

    override suspend fun invalidateCache(issuer: String) = Unit

    companion object {
        fun conventionBased(): StubOidcDiscoveryService =
            StubOidcDiscoveryService { issuer -> metadata(issuer, "${issuer.trimEnd('/')}/.well-known/jwks.json") }

        fun returning(issuer: String, jwksUri: String): StubOidcDiscoveryService =
            StubOidcDiscoveryService { requestedIssuer ->
                if (requestedIssuer == issuer) {
                    metadata(issuer, jwksUri)
                } else {
                    Err(JwtValidationError.discoveryFailed(requestedIssuer, "unexpected issuer"))
                }
            }

        fun failing(error: JwtValidationError): StubOidcDiscoveryService =
            StubOidcDiscoveryService { Err(error) }

        private fun metadata(issuer: String, jwksUri: String): IdkResult<OidcDiscoveryMetadata, JwtValidationError> =
            Ok(
                OidcDiscoveryMetadata(
                    issuer = issuer,
                    jwksUri = jwksUri,
                    authorizationEndpoint = null,
                    tokenEndpoint = null,
                    userinfoEndpoint = null,
                    responseTypesSupported = null,
                    subjectTypesSupported = null,
                    idTokenSigningAlgValuesSupported = null,
                    scopesSupported = null,
                ),
            )
    }
}

/**
 * Local test stub for VerifyJwtCommand.
 *
 * Avoids the full ExecutionScopedCommandAdapter/SessionExecution wiring because
 * DefaultJwtValidationService calls `execute` for access tokens and
 * `verifyStandardArtifact` for explicit non-access contexts. The ServiceCommand interface
 * requires a few members (inputTypeToken, outputTypeToken, isEnabled, commandId)
 * that are trivial to provide; the rest inherit defaults from Command/BaseCommand.
 */
private class StubVerifyJwtCommand(
    private val responses: Map<String, IdkResult<TokenPayload.Jwt, IdkError>>,
    private val defaultResponse: IdkResult<TokenPayload.Jwt, IdkError>?,
    private val failOnInvocation: Boolean,
    private val perCallIssuerTrustResponse: (suspend (VerifyJwtArgs) -> IdkResult<TokenPayload.Jwt, IdkError>)? = null,
) : VerifyJwtCommand {
    private val invocations = atomic(0)
    private val lastArgsRef = atomic<VerifyJwtArgs?>(null)
    private val lastStandardContextRef = atomic<StandardJwtArtifactContext?>(null)

    val invocationCount: Int get() = invocations.value
    val lastArgs: VerifyJwtArgs? get() = lastArgsRef.value
    val lastStandardContext: StandardJwtArtifactContext? get() = lastStandardContextRef.value

    override val isEnabled: Boolean = true
    override val inputTypeToken: TypeToken<VerifyJwtArgs> = typeToken<VerifyJwtArgs>()
    override val outputTypeToken: TypeToken<TokenPayload.Jwt> = typeToken<TokenPayload.Jwt>()

    override suspend fun execute(args: VerifyJwtArgs): IdkResult<TokenPayload.Jwt, IdkError> {
        if (failOnInvocation) {
            error("VerifyJwtCommand invoked unexpectedly with args=$args")
        }
        invocations.incrementAndGet()
        lastArgsRef.value = args
        return perCallIssuerTrustResponse?.invoke(args) ?: responses[args.jwt]
            ?: defaultResponse
            ?: error("No stub response for jwt='${args.jwt}'")
    }

    override suspend fun verifyStandardArtifact(
        args: VerifyJwtArgs,
        context: StandardJwtArtifactContext,
    ): IdkResult<JsonObject, IdkError> {
        if (failOnInvocation) error("VerifyJwtCommand artifact verification invoked unexpectedly with args=$args, context=$context")
        invocations.incrementAndGet()
        lastArgsRef.value = args
        lastStandardContextRef.value = context
        val payload = perCallIssuerTrustResponse?.invoke(args) ?: responses[args.jwt]
            ?: defaultResponse
            ?: error("No stub response for artifact jwt='${args.jwt}'")
        return payload.map { token ->
            buildJsonObject {
                put("sub", token.sub)
                put("iss", token.iss)
                token.aud?.let { audiences -> put("aud", JsonArray(audiences.map { JsonPrimitive(it) })) }
                put("exp", token.exp.epochSeconds)
                put("iat", token.iat.epochSeconds)
                token.scope?.let { put("scope", it) }
                token.clientId?.let { put("client_id", it) }
                token.jti?.let { put("jti", it) }
                token.additionalClaims.forEach { (name, value) -> put(name, value) }
            }
        }
    }

    companion object {
        fun returning(
            jwt: String,
            response: IdkResult<TokenPayload.Jwt, IdkError>,
        ): StubVerifyJwtCommand =
            StubVerifyJwtCommand(
                responses = mapOf(jwt to response),
                defaultResponse = null,
                failOnInvocation = false,
            )

        fun neverInvoked(): StubVerifyJwtCommand =
            StubVerifyJwtCommand(
                responses = emptyMap(),
                defaultResponse = null,
                failOnInvocation = true,
            )

        fun forPerCallIssuerTrust(
            response: suspend (VerifyJwtArgs) -> IdkResult<TokenPayload.Jwt, IdkError>,
        ): StubVerifyJwtCommand =
            StubVerifyJwtCommand(
                responses = emptyMap(),
                defaultResponse = null,
                failOnInvocation = false,
                perCallIssuerTrustResponse = response,
            )
    }
}

/** The verifier still parses claims and enforces issuer/audience/time; this fixture isolates JOSE crypto. */
private class SignatureBoundaryJwtService : JwtService {
    var verificationCount: Int = 0
        private set

    override val commands: JwtService.Commands get() = error("the verifier uses the service method")

    override suspend fun prepareJws(args: CreateJwsJsonArgs): IdkResult<PreparedJwsObject, IdkError> = unsupported()
    override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> = unsupported()
    override suspend fun createJwsJsonFlattened(args: CreateJwsJsonArgs): IdkResult<JwsJsonFlattened, IdkError> = unsupported()
    override suspend fun createJwsJsonGeneral(args: CreateJwsJsonArgs): IdkResult<JwsJsonGeneral, IdkError> = unsupported()

    override suspend fun verifyJws(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> {
        verificationCount++
        val parts = args.jws.value.split('.')
        val protected = JwsUtils.decodeBase64UrlToJson(parts[0])
        return Ok(
            JwsValidationResult(
                jws =
                    JwsJsonGeneralWithIdentifiers(
                        payload = parts[1],
                        signatures =
                            listOf(
                                JwsJsonSignatureWithIdentifier(
                                    protected = parts[0],
                                    parsedProtectedHeader = protected,
                                    signature = parts[2],
                                ),
                            ),
                    ),
                isValid = true,
                parsedPayload = JsonObject(emptyMap()),
            ),
        )
    }

    override fun assembleJwsGeneral(prepared: PreparedJwsObject, signatureBytes: ByteArray): JwsJsonGeneral = error("unused")
    override fun assembleJwsFlattened(prepared: PreparedJwsObject, signatureBytes: ByteArray): JwsJsonFlattened = error("unused")
    override fun assembleJwsCompact(prepared: PreparedJwsObject, signatureBytes: ByteArray): JwtCompactResult = error("unused")

    private fun <T : Any> unsupported(): IdkResult<T, IdkError> =
        Err(IdkError.fromString(code = "UNSUPPORTED_TEST_OPERATION", message = "unused JOSE operation"))
}

private object TestCommandSessionExecution : SessionExecution {
    override val sessionContextManager: SessionContextManager get() = error("unused")
    override val sessionContext: SessionContext = com.sphereon.di.context.NoOpSessionContext
    override val log: SessionLogService = TestCommandSessionLogService
    override val conf: ContextConfig = TestCommandContextConfig
    override val interceptorChain: CommandLifecycleInterceptorChain = EmptyInterceptorChain
}

private object TestCommandContextConfig : ContextConfig {
    override val app: AppConfigService get() = error("unused")
    override val tenant: TenantConfigService get() = error("unused")
    override val principal: PrincipalConfigService get() = error("unused")
    override fun conf(level: ConfigLevel): ConfigService = error("unused")
}

private object TestCommandSessionLogService : SessionLogService {
    override val sessionContext: SessionContext = com.sphereon.di.context.NoOpSessionContext
    override val id: String = "jwt-validation-command-test"
    override val scope: IdkScope = IdkScope.SESSION
    override val isEnabled: Boolean = false
    override val logManager: SessionLogManager get() = error("unused")
    override suspend fun setConfig(config: LoggerConfig): LogService = this
    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)
    override fun toAsync(): AsyncLogService = error("unused")
}
