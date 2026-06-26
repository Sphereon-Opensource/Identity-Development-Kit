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
import com.sphereon.oauth2.jwt.validation.AccessTokenValidationOptions
import com.sphereon.oauth2.jwt.validation.IdTokenValidationOptions
import com.sphereon.oauth2.jwt.validation.IdpConfig
import com.sphereon.oauth2.jwt.validation.JwtValidationConfig
import com.sphereon.oauth2.jwt.validation.JwtValidationErrorType
import com.sphereon.oauth2.server.resource.command.VerifyJwtArgs
import com.sphereon.oauth2.server.resource.command.VerifyJwtCommand
import com.sphereon.oauth2.server.resource.error.ResourceServerError
import com.sphereon.oauth2.server.resource.model.TokenPayload
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
 *   placeholder signature; real signature verification is performed by
 *   VerifyJwtCommand, which is stubbed here.
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

    private fun buildJwt(payloadClaims: Map<String, JsonElement>): String {
        val header =
            buildJsonObject {
                put("alg", JsonPrimitive("RS256"))
                put("typ", JsonPrimitive("at+jwt"))
            }
        val payload =
            JsonObject(payloadClaims)
        val headerPart = Json.encodeToString(JsonObject.serializer(), header).encodeToByteArray().encodeToBase64Url()
        val payloadPart = Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray().encodeToBase64Url()
        // Signature is never checked in these tests; VerifyJwtCommand is stubbed.
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
        return DefaultJwtValidationService(verifyJwtCommand = stub, idpRegistry = registry)
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
                DefaultJwtValidationService(verifyJwtCommand = stub, idpRegistry = registry)

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

/**
 * Local test stub for VerifyJwtCommand.
 *
 * Avoids the full ExecutionScopedCommandAdapter/SessionExecution wiring because
 * DefaultJwtValidationService only calls `execute`. The ServiceCommand interface
 * requires a few members (inputTypeToken, outputTypeToken, isEnabled, commandId)
 * that are trivial to provide; the rest inherit defaults from Command/BaseCommand.
 */
private class StubVerifyJwtCommand(
    private val responses: Map<String, IdkResult<TokenPayload.Jwt, IdkError>>,
    private val defaultResponse: IdkResult<TokenPayload.Jwt, IdkError>?,
    private val failOnInvocation: Boolean,
) : VerifyJwtCommand {
    private val invocations = atomic(0)
    private val lastArgsRef = atomic<VerifyJwtArgs?>(null)

    val invocationCount: Int get() = invocations.value
    val lastArgs: VerifyJwtArgs? get() = lastArgsRef.value

    override val isEnabled: Boolean = true
    override val inputTypeToken: TypeToken<VerifyJwtArgs> = typeToken<VerifyJwtArgs>()
    override val outputTypeToken: TypeToken<TokenPayload.Jwt> = typeToken<TokenPayload.Jwt>()

    override suspend fun execute(args: VerifyJwtArgs): IdkResult<TokenPayload.Jwt, IdkError> {
        if (failOnInvocation) {
            error("VerifyJwtCommand invoked unexpectedly with args=$args")
        }
        invocations.incrementAndGet()
        lastArgsRef.value = args
        return responses[args.jwt]
            ?: defaultResponse
            ?: error("No stub response for jwt='${args.jwt}'")
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
    }
}
