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

package com.sphereon.oauth2.server.resource.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.session.CommandLifecycleInterceptorChain
import com.sphereon.core.api.session.EmptyInterceptorChain
import com.sphereon.crypto.jose.jws.*
import com.sphereon.crypto.jose.jws.command.*
import com.sphereon.core.api.context.IdkScope
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.oauth2.server.resource.command.StandardJwtArtifactContext
import com.sphereon.oauth2.server.resource.command.VerifyJwtArgs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class VerifyJwtAccessTokenTypTest {
    private val command = VerifyJwtCommandImpl(TestSessionExecution, SignatureBoundaryJwtService)

    @Test
    fun accessExecutionAcceptsRfc9068MediaTypes() =
        kotlinx.coroutines.test.runTest {
            listOf("at+jwt", "application/at+jwt").forEach { typ ->
                val result = command.execute(accessArgs(jwt(typ = kotlinx.serialization.json.JsonPrimitive(typ))))
                assertTrue(result.isOk, "expected access-token typ '$typ' to pass the real command path")
            }
        }

    @Test
    fun accessExecutionRejectsAbsentGenericIdAndMalformedTyp() =
        kotlinx.coroutines.test.runTest {
            val invalidTypes: List<JsonElement?> =
                listOf(
                    null,
                    kotlinx.serialization.json.JsonPrimitive("JWT"),
                    kotlinx.serialization.json.JsonPrimitive("id+jwt"),
                    JsonNull,
                    kotlinx.serialization.json.JsonPrimitive(7),
                    buildJsonObject { put("value", "at+jwt") },
                )
            invalidTypes.forEach { typ ->
                val result = command.execute(accessArgs(jwt(typ)))
                assertTrue(result.isErr, "expected malformed or non-access typ '$typ' to fail")
            }
        }

    @Test
    fun standardIdTokenVerificationUsesTypedContextAndKeepsIssuerAudienceChecks() =
        kotlinx.coroutines.test.runTest {
            val idClaims = artifactClaims()
            assertTrue(command.verifyStandardArtifact(accessArgs(jwt(JsonPrimitive("JWT"), idClaims)), StandardJwtArtifactContext.ID_TOKEN).isOk)
            assertTrue(command.verifyStandardArtifact(accessArgs(jwt(null, idClaims)), StandardJwtArtifactContext.ID_TOKEN).isOk,
                "ID_TOKEN may omit typ")

            val jarmClaims = JsonObject(artifactClaims(includeSubject = false).minus("iat") + ("code" to JsonPrimitive("auth-code")))
            assertTrue(command.verifyStandardArtifact(accessArgs(jwt(JsonPrimitive("JWT"), jarmClaims)), StandardJwtArtifactContext.JARM_RESPONSE).isOk,
                "JARM accepts a response field without requiring sub or iat")
            listOf("error", "access_token", "id_token").forEach { responseField ->
                val responseClaims = JsonObject(artifactClaims(includeSubject = false).minus("iat") + (responseField to JsonPrimitive("response-value")))
                assertTrue(command.verifyStandardArtifact(accessArgs(jwt(JsonPrimitive("JWT"), responseClaims)), StandardJwtArtifactContext.JARM_RESPONSE).isOk,
                    "supported JARM response field '$responseField' should be accepted")
            }

            val logoutClaims = JsonObject(
                artifactClaims(includeSubject = false) + mapOf(
                    "sid" to JsonPrimitive("session-1"),
                    "jti" to JsonPrimitive("logout-1"),
                    "events" to buildJsonObject {
                        put("http://schemas.openid.net/event/backchannel-logout", JsonObject(emptyMap()))
                    },
                ),
            )
            assertTrue(command.verifyStandardArtifact(accessArgs(jwt(JsonPrimitive("logout+jwt"), logoutClaims)), StandardJwtArtifactContext.LOGOUT_TOKEN).isOk,
                "sid-only logout token with required event claims is valid")
            assertTrue(command.verifyStandardArtifact(accessArgs(jwt(JsonPrimitive("JWT"), logoutClaims)), StandardJwtArtifactContext.LOGOUT_TOKEN).isOk,
                "legacy generic logout typ remains accepted")
            val invalidLogoutClaims = listOf(
                JsonObject(logoutClaims + ("sid" to JsonPrimitive(" "))),
                JsonObject(logoutClaims + ("sub" to JsonPrimitive(" "))),
                JsonObject(logoutClaims + ("sub" to buildJsonObject { put("id", "user") })),
                JsonObject(logoutClaims - "jti"),
                JsonObject(logoutClaims - "iat"),
                JsonObject(logoutClaims + ("nonce" to JsonPrimitive("forbidden"))),
                JsonObject(logoutClaims + ("events" to buildJsonObject {
                    put("http://schemas.openid.net/event/backchannel-logout", buildJsonObject { put("unexpected", true) })
                })),
            )
            invalidLogoutClaims.forEach { claims ->
                assertTrue(command.verifyStandardArtifact(accessArgs(jwt(JsonPrimitive("JWT"), claims)), StandardJwtArtifactContext.LOGOUT_TOKEN).isErr,
                    "malformed logout claims must fail: $claims")
            }

            val accessToken = jwt(JsonPrimitive("at+jwt"), idClaims)
            StandardJwtArtifactContext.values().forEach { context ->
                assertTrue(command.verifyStandardArtifact(accessArgs(accessToken), context).isErr, "access typ must fail $context")
            }
            assertTrue(command.verifyStandardArtifact(accessArgs(jwt(JsonPrimitive("logout+jwt"), idClaims)), StandardJwtArtifactContext.ID_TOKEN).isErr)
            assertTrue(command.verifyStandardArtifact(accessArgs(jwt(JsonPrimitive("id+jwt"), idClaims)), StandardJwtArtifactContext.LOGOUT_TOKEN).isErr)
            assertTrue(command.verifyStandardArtifact(accessArgs(jwt(JsonPrimitive("oauth-authz-resp+jwt"), idClaims)), StandardJwtArtifactContext.ID_TOKEN).isErr)
            assertTrue(command.verifyStandardArtifact(accessArgs(jwt(JsonPrimitive("logout+jwt"), jarmClaims)), StandardJwtArtifactContext.JARM_RESPONSE).isErr)
            assertTrue(command.verifyStandardArtifact(accessArgs(jwt(JsonPrimitive("id+jwt"), jarmClaims)), StandardJwtArtifactContext.JARM_RESPONSE).isErr)
            assertTrue(command.verifyStandardArtifact(accessArgs(jwt(JsonPrimitive("JWT"), idClaims)), StandardJwtArtifactContext.JARM_RESPONSE).isErr,
                "JARM without response fields must fail")

            val idToken = jwt(JsonPrimitive("JWT"), idClaims)
            val wrongIssuer = command.verifyStandardArtifact(accessArgs(idToken, issuer = "https://untrusted.example"), StandardJwtArtifactContext.ID_TOKEN)
            assertTrue(wrongIssuer.isErr)

            val wrongAudience = command.verifyStandardArtifact(accessArgs(idToken, audience = "other-api"), StandardJwtArtifactContext.ID_TOKEN)
            assertTrue(wrongAudience.isErr)
        }

    @Test
    fun serializedResourceArgsCannotSelectLenientVerification() =
        kotlinx.coroutines.test.runTest {
            val args = accessArgs(jwt(kotlinx.serialization.json.JsonPrimitive("JWT")))
            val fields = Json.encodeToJsonElement(VerifyJwtArgs.serializer(), args).jsonObject.toMutableMap()
            fields["artifactContext"] = JsonPrimitive("ID_TOKEN")
            fields["standardArtifactContext"] = JsonPrimitive("ID_TOKEN")
            fields["requireAccessTokenTyp"] = JsonPrimitive(false)
            val forgedSerialized = Json.encodeToString(JsonObject(fields))
            val strictFailure = runCatching { Json.decodeFromString(VerifyJwtArgs.serializer(), forgedSerialized) }.exceptionOrNull()
            assertTrue(strictFailure is SerializationException, "strict decoding must reject forged verification selectors")
            val tolerantArgs = Json { ignoreUnknownKeys = true }.decodeFromString(VerifyJwtArgs.serializer(), forgedSerialized)
            assertTrue(command.execute(tolerantArgs).isErr, "tolerant decoding must enforce the access-token typ")

            val serialized = Json.encodeToString(VerifyJwtArgs.serializer(), args)
            assertFalse(serialized.contains("artifactContext"))
            assertFalse(serialized.contains("requireAccessTokenTyp"))
            assertTrue(command.execute(args).isErr)
        }

    private fun accessArgs(
        compactJwt: String,
        issuer: String = ISSUER,
        audience: String = AUDIENCE,
    ) = VerifyJwtArgs(jwt = compactJwt, authorizationServer = issuer, expectedAudience = audience, clockSkewSeconds = 0)

    private fun artifactClaims(includeSubject: Boolean = true): JsonObject = buildJsonObject {
        put("iss", ISSUER)
        if (includeSubject) put("sub", "user-1")
        put("aud", AUDIENCE)
        put("exp", Clock.System.now().epochSeconds + 600)
        put("iat", Clock.System.now().epochSeconds)
    }

    private fun jwt(typ: JsonElement?, payload: JsonObject = artifactClaims()): String {
        val header = buildJsonObject {
            put("alg", "RS256")
            if (typ != null) put("typ", typ)
        }
        val headerPart = Json.encodeToString(JsonObject.serializer(), header).encodeToByteArray().encodeToBase64Url()
        val payloadPart = Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray().encodeToBase64Url()
        return "$headerPart.$payloadPart.${"fixture-signature".encodeToByteArray().encodeToBase64Url()}"
    }

    private companion object {
        const val ISSUER = "https://issuer.example"
        const val AUDIENCE = "resource.example"
    }
}

/** A narrow JOSE boundary fixture: the command itself parses headers/payload and validates claims. */
private object SignatureBoundaryJwtService : JwtService {
    override val commands: JwtService.Commands get() = error("command calls the service verification method directly")

    override suspend fun prepareJws(args: CreateJwsJsonArgs): IdkResult<PreparedJwsObject, IdkError> = unsupported()
    override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> = unsupported()
    override suspend fun createJwsJsonFlattened(args: CreateJwsJsonArgs): IdkResult<JwsJsonFlattened, IdkError> = unsupported()
    override suspend fun createJwsJsonGeneral(args: CreateJwsJsonArgs): IdkResult<JwsJsonGeneral, IdkError> = unsupported()

    override suspend fun verifyJws(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> {
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

private object TestSessionExecution : SessionExecution {
    override val sessionContextManager: SessionContextManager get() = error("unused")
    override val sessionContext: SessionContext = NoOpSessionContext
    override val log: SessionLogService = TestSessionLogService
    override val conf: ContextConfig = TestContextConfig
    override val interceptorChain: CommandLifecycleInterceptorChain = EmptyInterceptorChain
}

private object TestContextConfig : ContextConfig {
    override val app: AppConfigService get() = error("unused")
    override val tenant: TenantConfigService get() = error("unused")
    override val principal: PrincipalConfigService get() = error("unused")
    override fun conf(level: ConfigLevel): ConfigService = error("unused")
}

private object TestSessionLogService : SessionLogService {
    override val sessionContext: SessionContext = NoOpSessionContext
    override val id: String = "verify-jwt-test"
    override val scope: IdkScope = IdkScope.SESSION
    override val isEnabled: Boolean = false
    override val logManager: SessionLogManager get() = error("unused")
    override suspend fun setConfig(config: LoggerConfig): LogService = this
    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)
    override fun toAsync(): AsyncLogService = error("unused")
}
