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
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.sourceAs
import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.crypto.jose.jws.JwsJsonFlattened
import com.sphereon.crypto.jose.jws.JwsJsonGeneral
import com.sphereon.crypto.jose.jws.JwsValidationResult
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.PreparedJwsObject
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.config.OAuth2SigningKeyUnavailableException
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryTokenStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.testutil.fixedSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.signing.AsServerSigningIdentifierResolver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins the RFC 9068 `azp` stamping in [CreateAccessTokenCommandImpl] that the EDK enterprise
 * STS (service-to-service) east-west path depends on.
 *
 * Self-issued SERVICE tokens minted via the client_credentials grant have `sub == client_id`.
 * For exactly those tokens the command stamps `azp = client_id`, so the binary/gRPC transport's
 * `AuthContextExtractor.isWorkloadToken()` (which keys on `sub == client_id == azp` with no
 * `email`) recognises the bearer as a workload identity and applies the internal
 * service-forwarding trust policy (target tenant carried in `X-Tenant-Id` atop the validated
 * service token). Human (authorization_code) tokens, whose subject is the end user and differs
 * from the client_id, must NOT carry `azp`, otherwise a user token could be mistaken for a
 * workload token and gain the service-forwarding trust.
 *
 * The command builds the JWT payload BEFORE handing it to the signer, so a [RecordingJwtService]
 * that base64url-encodes the payload it receives into the JWT's middle segment lets the test
 * decode the real minted payload and assert on the `azp` claim without coupling to crypto. The
 * `azp` decision lives entirely in the payload construction, so this faithfully exercises it.
 */
class CreateAccessTokenAzpClaimTest {
    private val ctx = OAuth2ServerTestContext("create-access-token-azp-test", this)

    private val configProvider =
        TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers = mapOf("default" to OAuth2ServerInstanceConfig(issuer = ISSUER)),
            ),
        )

    private fun newCommand(
        jwtService: RecordingJwtService,
        signingIdentifierResolver: AsServerSigningIdentifierResolver =
            fixedSigningIdentifierResolver(ManagedOptsAlias(identifier = "as-signing-key")),
    ): CreateAccessTokenCommandImpl =
        CreateAccessTokenCommandImpl(
            execution = ctx.execution,
            jwtService = jwtService,
            tokenStorage = InMemoryTokenStorageImpl(InMemoryOAuth2BackingStorageImpl()),
            secureRandom = defaultSecureRandom(),
            configProvider = configProvider,
            // Non-null signing identifier forces the JWT (not opaque) path so a payload is built.
            signingIdentifierResolver = signingIdentifierResolver,
            eventService = null,
        )

    @Test
    fun unavailableSigningKeyReturnsTemporarilyUnavailableInsteadOfThrowing() =
        runTest {
            val resolver =
                object : AsServerSigningIdentifierResolver {
                    override suspend fun resolveSigningIdentifier() =
                        throw OAuth2SigningKeyUnavailableException(
                            tenantId = "platform",
                            message = "platform bootstrap has not provisioned its signing key",
                        )
                }

            val result =
                newCommand(RecordingJwtService(), resolver).execute(
                    CreateAccessTokenArgs(
                        subject = SERVICE_CLIENT_ID,
                        clientId = SERVICE_CLIENT_ID,
                        scope = "service",
                    ),
                )

            assertTrue(result.isErr)
            assertIs<AuthorizationServerError.TemporarilyUnavailable>(result.error.sourceAs())
        }

    /**
     * Decode the middle (payload) segment of the compact JWT the command produced and parse it
     * as a JSON object. The [RecordingJwtService] places the exact payload the command built into
     * this segment, base64url-encoded, so this is the minted RFC 9068 token body.
     */
    private fun decodePayload(compactJwt: String): JsonObject {
        val segments = compactJwt.split(".")
        assertEquals(3, segments.size, "compact JWT must have 3 dot-separated segments, got: $compactJwt")
        val json = segments[1].decodeFromBase64Url().decodeToString()
        return Json.parseToJsonElement(json) as JsonObject
    }

    @Test
    fun selfIssuedServiceTokenStampsAzpEqualToClientId() =
        runTest {
            // client_credentials-style mint: the subject IS the client (sub == client_id).
            val jwtService = RecordingJwtService()
            val command = newCommand(jwtService)

            val result =
                command.execute(
                    CreateAccessTokenArgs(
                        subject = SERVICE_CLIENT_ID,
                        clientId = SERVICE_CLIENT_ID,
                        scope = "service",
                    ),
                )

            assertTrue(result.isOk, "service token mint must succeed: ${if (result.isErr) result.error else ""}")
            val payload = decodePayload(result.value.value)

            assertEquals(SERVICE_CLIENT_ID, payload["sub"]?.jsonPrimitive?.contentOrNull, "sub must be the client id")
            assertEquals(SERVICE_CLIENT_ID, payload["client_id"]?.jsonPrimitive?.contentOrNull, "client_id must be present")
            assertEquals(
                SERVICE_CLIENT_ID,
                payload["azp"]?.jsonPrimitive?.contentOrNull,
                "self-issued service token (sub == client_id) MUST stamp azp == client_id for workload-token recognition",
            )
        }

    @Test
    fun humanUserTokenDoesNotStampAzp() =
        runTest {
            // authorization_code-style mint: the subject is the end user, distinct from client_id.
            val jwtService = RecordingJwtService()
            val command = newCommand(jwtService)

            val result =
                command.execute(
                    CreateAccessTokenArgs(
                        subject = "user-123",
                        clientId = "web-app-client",
                        scope = "openid profile",
                    ),
                )

            assertTrue(result.isOk, "user token mint must succeed: ${if (result.isErr) result.error else ""}")
            val payload = decodePayload(result.value.value)

            assertEquals("user-123", payload["sub"]?.jsonPrimitive?.contentOrNull, "sub must be the user")
            assertEquals("web-app-client", payload["client_id"]?.jsonPrimitive?.contentOrNull, "client_id must be present")
            assertFalse(
                payload.containsKey("azp"),
                "human user token (sub != client_id) MUST NOT carry azp, or it would be mistaken for a workload token",
            )
        }

    @Test
    fun humanUserTokenStampsTypedAuthenticationContextButRejectsReservedClaimOverrides() =
        runTest {
            val jwtService = RecordingJwtService()
            val command = newCommand(jwtService)

            val result =
                command.execute(
                    CreateAccessTokenArgs(
                        subject = "user-123",
                        clientId = "web-app-client",
                        authTime = 1_784_485_200L,
                        acr = "urn:nist:sp:800-63:aal1",
                        amr = listOf("pwd"),
                        additionalClaims =
                            mapOf(
                                "auth_time" to 1L,
                                "acr" to "attacker-override",
                                "amr" to listOf("attacker"),
                            ),
                    ),
                )

            assertTrue(result.isOk, "user token mint must succeed: ${if (result.isErr) result.error else ""}")
            val payload = decodePayload(result.value.value)
            assertEquals(1_784_485_200L, payload["auth_time"]?.jsonPrimitive?.content?.toLong())
            assertEquals("urn:nist:sp:800-63:aal1", payload["acr"]?.jsonPrimitive?.contentOrNull)
            assertEquals(listOf("pwd"), payload["amr"]?.jsonArray?.map { it.jsonPrimitive.content })
        }

    @Test
    fun accessTokenSigningUsesKidHeaderMode() =
        runTest {
            val jwtService = RecordingJwtService()
            val command = newCommand(jwtService)

            val result =
                command.execute(
                    CreateAccessTokenArgs(
                        subject = SERVICE_CLIENT_ID,
                        clientId = SERVICE_CLIENT_ID,
                        scope = "service",
                    ),
                )

            assertTrue(result.isOk, "access token mint must succeed: ${if (result.isErr) result.error else ""}")
            assertEquals(
                JwsIdentifierMode.KID,
                jwtService.lastArgs?.mode,
                "OAuth2 access tokens must carry a kid header so resource servers can select the AS JWKS key",
            )
        }

    companion object {
        private const val ISSUER = "https://as.example.com"
        private const val SERVICE_CLIENT_ID = "service-tenant-as"
    }

    /**
     * A [JwtService] fake whose only real behaviour is in [createJwsCompact]: it records the args
     * and emits a compact JWT of the form `<header>.<base64url(payload)>.<sig>`. The payload
     * segment is the exact JSON the command under test handed in, so the test can decode the
     * minted RFC 9068 body and assert on its claims. Every other member is unused by
     * [CreateAccessTokenCommandImpl] and throws / returns Err to fail loudly if reached.
     *
     * Modelled on the `StubJwtService` in `VerifyClientAuthenticationCommandImplTest`.
     */
    private class RecordingJwtService : JwtService {
        private val notImpl = IdkError(code = "not_implemented", message = IdkError.Message(i18nKey = "", defaultMessage = "Not implemented"))

        var lastPayload: String? = null
            private set
        var lastArgs: CreateJwsArgs? = null
            private set

        override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> {
            lastArgs = args
            val payloadJson =
                args.payload as? String
                    ?: return Err(IdkError(code = "bad_payload", message = IdkError.Message(i18nKey = "", defaultMessage = "expected String payload")))
            lastPayload = payloadJson
            val header = "eyJ0eXAiOiJhdCtqd3QifQ" // {"typ":"at+jwt"} base64url, value irrelevant to the test
            val payloadSegment = payloadJson.encodeToByteArray().encodeToBase64Url()
            return Ok(JwtCompactResult(jwt = "$header.$payloadSegment.sig"))
        }

        override suspend fun prepareJws(args: CreateJwsJsonArgs): IdkResult<PreparedJwsObject, IdkError> = Err(notImpl)

        override suspend fun createJwsJsonFlattened(args: CreateJwsJsonArgs): IdkResult<JwsJsonFlattened, IdkError> = Err(notImpl)

        override suspend fun createJwsJsonGeneral(args: CreateJwsJsonArgs): IdkResult<JwsJsonGeneral, IdkError> = Err(notImpl)

        override suspend fun verifyJws(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> = Err(notImpl)

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
