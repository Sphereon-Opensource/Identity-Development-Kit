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

package com.sphereon.oauth2.server.authorization.impl.command.jar

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommand
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.ktor.http.client.FetchRequestUriArgs
import com.sphereon.ktor.http.client.FetchRequestUriCommand
import com.sphereon.ktor.http.client.FetchedRequestUri
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.command.jar.VerifyRequestObjectArgs
import com.sphereon.oauth2.server.authorization.command.jar.VerifyRequestObjectCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.StubClientRegistry
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.model.ClientType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Security regression tests for [VerifyRequestObjectCommandImpl]'s STRICT key-selection
 * precedence on the RFC 9101 JAR (`request` object JWS) verification path:
 *
 *  1. inline `jwks` present -> pinned mode (resolvers disabled; inline always wins);
 *  2. else `jwks_uri` -> identifier-resolution path, but ONLY after two fail-closed guards:
 *       (2a) `jwks_uri` MUST be https (SSRF / scheme-downgrade guard);
 *       (2b) the JAR JWS header MUST carry a `kid` (so the resolver binds to that exact key);
 *  3. else (no jwks and no jwks_uri) -> fail closed with `invalid_request_object`.
 *
 * The command is wired with the REAL [VerifyJwsCommand] resolved from the session graph so the
 * pinned-JWKS verification path and the identifier-resolution system are genuine, not stubbed.
 * JARs are minted with the real [JwtService] (software KMS key) so signatures actually verify.
 *
 * Case E (a jwks_uri-only client whose JWKS is served over HTTP by a Ktor MockEngine) is NOT
 * implemented: this AS test harness ([OAuth2ServerTestContext]) constructs its AppGraph through
 * the Metro graph factory and exposes no hook to substitute the `HttpClientFactory` with a
 * MockEngine, so the JWKS HTTP fetch cannot be cleanly stubbed here. The two guards (2a/2b) plus
 * the pinned-vs-fail-closed precedence (cases A-D below) already prove the security-critical
 * selection logic; the resolver's own HTTP-fetch path is covered by the crypto resolver tests.
 */
class VerifyRequestObjectKeySelectionTest {
    private val ctx = OAuth2ServerTestContext("verify-request-object-key-selection-test", this)
    private val jwtService: JwtService = (ctx.session.graph as JwtServiceImpl.Graph).jwtService
    private val verifyJwsCommand: VerifyJwsCommand = jwtService.commands.verifyJws

    private companion object {
        const val ISSUER = "https://as.example.com"
        const val CLIENT_ID = "jar-test-client"
        const val INVALID_REQUEST_OBJECT_CODE = "invalid_request_object"
    }

    /**
     * Build a [VerifyRequestObjectCommandImpl] wired with the real JWS verifier and a single-client
     * registry. The `request_uri` fetch command is a fail-loud stub: every JAR in these tests is
     * supplied inline via `request`, so the fetch path is never legitimately reached.
     */
    private fun createCommand(client: ClientRegistration): VerifyRequestObjectCommandImpl {
        val serverConfig = OAuth2ServerInstanceConfig(issuer = ISSUER)
        val servers = OAuth2ServersConfig(servers = mapOf("default" to serverConfig), defaultServer = "default")
        return VerifyRequestObjectCommandImpl(
            execution = ctx.execution,
            clientRegistry = StubClientRegistry(mapOf(client.clientId to client)),
            verifyJwsCommand = verifyJwsCommand,
            fetchRequestUriCommand = FailingFetchRequestUriCommand(ctx.execution),
            serversConfigProvider = TestOAuth2ServersConfigProvider(servers),
        )
    }

    private fun client(
        jwks: List<Jwk>? = null,
        jwksUri: String? = null,
    ): ClientRegistration =
        ClientRegistration(
            clientId = CLIENT_ID,
            clientName = "JAR Key Selection Test Client",
            clientType = ClientType.CONFIDENTIAL,
            grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
            tokenEndpointAuthMethod = ClientAuthenticationMethod.PRIVATE_KEY_JWT,
            jwks = jwks,
            jwksUri = jwksUri,
        )

    /**
     * Mint a freshly-generated software KMS key, sign a valid JAR with it, and return the compact
     * JWS together with the public JWK (kid-stamped to match the JWS header). [includeKid] controls
     * whether the JWS protected header carries a `kid` (off => the kid-guard should fire on the
     * jwks_uri path).
     */
    private suspend fun mintJar(
        includeKid: Boolean = true,
        nowSeconds: Long = Clock.System.now().epochSeconds,
    ): SignedJar {
        val managedKeyPair = ctx.keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer =
            ManagedOptsKeyInfo(
                identifier = keyInfo,
                context = IdentifierContext(clientId = CLIENT_ID, issuer = CLIENT_ID),
            )

        // typ MUST be the JAR media type (RFC 9101 §10.8); iss/client_id are filled by the
        // issuer context, aud/exp/iat are valid for the AS issuer.
        val payload =
            buildJsonObject {
                put("aud", JsonPrimitive(ISSUER))
                put("exp", JsonPrimitive(nowSeconds + 600))
                put("iat", JsonPrimitive(nowSeconds))
                put("response_type", JsonPrimitive("code"))
                put("scope", JsonPrimitive("openid"))
            }

        val opts =
            CreateJwsOpts(
                protectedHeader = buildJsonObject { put("typ", JsonPrimitive(VerifyRequestObjectCommand.JAR_JWT_TYP)) },
                // includeKid=false: suppress all identifier hints in the header (alg still set),
                // so the produced JWS carries no `kid`.
                noIdentifierInHeader = !includeKid,
            )

        val createResult =
            jwtService.createJwsCompact(
                CreateJwsArgs(issuer = issuer, payload = payload, mode = JwsIdentifierMode.AUTO, opts = opts),
            )
        check(createResult.isOk) { "Failed to mint JAR: ${createResult.error}" }
        val jwt = createResult.value.jwt

        val headerKid = readHeaderKid(jwt)
        val publicJwk = stampKid(managedKeyPair.jose.publicJwk, headerKid)
        return SignedJar(jwt = jwt, publicJwk = publicJwk, headerKid = headerKid)
    }

    private fun readHeaderKid(jwt: String): String? {
        val headerJson =
            Json.parseToJsonElement(jwt.substringBefore('.').decodeFromBase64Url().decodeToString()) as? JsonObject
                ?: return null
        return (headerJson["kid"] as? JsonPrimitive)?.content
    }

    /** Round-trip the JWK through JSON to stamp the header `kid` (so pinned-mode selection matches). */
    private fun stampKid(
        jwk: Jwk,
        kid: String?,
    ): Jwk {
        if (kid == null) return jwk
        val asJson = Json.encodeToJsonElement(Jwk.serializer(), jwk).jsonObject
        val withKid = JsonObject(asJson + ("kid" to JsonPrimitive(kid)))
        return Json.decodeFromJsonElement(Jwk.serializer(), withKid)
    }

    private fun argsFor(jwt: String): VerifyRequestObjectArgs =
        VerifyRequestObjectArgs(
            requestJwt = jwt,
            clientIdHint = CLIENT_ID,
            issuer = ISSUER,
            queryParameters = mapOf("client_id" to CLIENT_ID),
        )

    private fun errorCode(result: IdkResult<*, *>): String? = if (result.isErr) (result.error as? IdkError)?.code else null

    // ─── Case A: pinned (inline jwks) mode unchanged ──────────────────────────────────────────

    @Test
    fun pinnedMode_jarSignedByPinnedKey_verifies() =
        runTest {
            val jar = mintJar()
            val cmd = createCommand(client(jwks = listOf(jar.publicJwk)))

            val result = cmd.execute(argsFor(jar.jwt))

            assertTrue(
                result.isOk,
                "A JAR signed by the client's inline jwks key must verify (pinned mode). " +
                    "error=${errorCode(result)}",
            )
            assertEquals(CLIENT_ID, result.value.clientId)
        }

    @Test
    fun pinnedMode_jarSignedByDifferentKey_isRejected_resolversNeverConsulted() =
        runTest {
            // Pin one key, sign with a completely different freshly-generated key. Pinned mode
            // disables resolvers, so there is no fallback that could rescue the wrong signature.
            val pinned = mintJar()
            val attacker = mintJar()
            val cmd = createCommand(client(jwks = listOf(pinned.publicJwk)))

            val result = cmd.execute(argsFor(attacker.jwt))

            assertTrue(result.isErr, "A JAR signed by a key NOT in the client's inline jwks must be rejected")
            assertEquals(INVALID_REQUEST_OBJECT_CODE, errorCode(result))
        }

    // ─── Case B: https guard on jwks_uri ──────────────────────────────────────────────────────

    @Test
    fun jwksUriMode_insecureHttpUri_isRejectedBeforeResolution() =
        runTest {
            val jar = mintJar(includeKid = true)
            // No inline jwks; jwks_uri is http:// (not https) -> the SSRF/scheme guard must fire
            // BEFORE any resolution is attempted.
            val cmd = createCommand(client(jwksUri = "http://insecure.example.com/jwks"))

            val result = cmd.execute(argsFor(jar.jwt))

            assertTrue(result.isErr, "An http:// jwks_uri must be rejected for JAR verification")
            assertEquals(INVALID_REQUEST_OBJECT_CODE, errorCode(result))
            val message = (result.error as? IdkError)?.message?.defaultMessage ?: ""
            assertTrue(
                message.contains("https", ignoreCase = true),
                "Rejection message must indicate the https requirement, got: $message",
            )
        }

    // ─── Case C: kid guard on jwks_uri ────────────────────────────────────────────────────────

    @Test
    fun jwksUriMode_jarWithoutKid_isRejectedBeforeResolution() =
        runTest {
            // Valid https jwks_uri, but the JAR header carries NO kid -> the kid guard must fire
            // so the resolver cannot fall back to a kid-absent first()-pick.
            val jar = mintJar(includeKid = false)
            assertNotNull(jar) // sanity
            check(jar.headerKid == null) { "Test setup error: expected a JAR without a kid header" }
            val cmd = createCommand(client(jwksUri = "https://secure.example.com/jwks"))

            val result = cmd.execute(argsFor(jar.jwt))

            assertTrue(result.isErr, "A JAR without a kid must be rejected on the jwks_uri path")
            assertEquals(INVALID_REQUEST_OBJECT_CODE, errorCode(result))
            val message = (result.error as? IdkError)?.message?.defaultMessage ?: ""
            assertTrue(
                message.contains("kid", ignoreCase = true),
                "Rejection message must indicate the kid requirement, got: $message",
            )
        }

    // ─── Case D: fail closed when neither jwks nor jwks_uri is configured ─────────────────────

    @Test
    fun noKeySource_failsClosed() =
        runTest {
            val jar = mintJar()
            val cmd = createCommand(client(jwks = null, jwksUri = null))

            val result = cmd.execute(argsFor(jar.jwt))

            assertTrue(result.isErr, "A client with neither jwks nor jwks_uri must fail closed")
            assertEquals(INVALID_REQUEST_OBJECT_CODE, errorCode(result))
        }

    private data class SignedJar(
        val jwt: String,
        val publicJwk: Jwk,
        val headerKid: String?,
    )

    /**
     * Fail-loud [FetchRequestUriCommand] stub. Every test supplies the JAR inline via `request`,
     * so a fetch attempt signals a wiring/logic error rather than a legitimate `request_uri` flow.
     */
    private class FailingFetchRequestUriCommand(
        execution: SessionExecution,
    ) : TypedServiceCommandAdapter<FetchRequestUriArgs, FetchedRequestUri, IdkError>(
            commandId = FetchRequestUriCommand.COMMAND_ID,
            execution = execution,
            inputTypeToken = typeToken<FetchRequestUriArgs>(),
            outputTypeToken = typeToken<FetchedRequestUri>(),
        ),
        FetchRequestUriCommand {
        override suspend fun supports(args: Any): Boolean = args is FetchRequestUriArgs

        override suspend fun doExecute(
            args: FetchRequestUriArgs,
            applyDuring: (FetchRequestUriArgs) -> FetchRequestUriArgs,
        ): IdkResult<FetchedRequestUri, IdkError> = Err(IdkError.fromString(code = "stub", message = "FailingFetchRequestUriCommand must never be called in these tests"))
    }
}
