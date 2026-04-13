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
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwksUrlOpts
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.resource.command.VerifyJwtArgs
import com.sphereon.oauth2.server.resource.command.VerifyJwtCommand
import com.sphereon.oauth2.server.resource.error.ResourceServerError
import com.sphereon.oauth2.server.resource.model.TokenPayload
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Implementation of VerifyJwtCommand
 *
 * Verifies JWT access tokens according to RFC 9068 (JWT Profile for OAuth 2.0 Access Tokens).
 *
 * **Verification flow**:
 * 1. Verify JWT signature using JwtService
 * 2. Parse and validate claims (iss, aud, exp, iat, sub)
 * 3. Validate typ header (should be "at+jwt" per RFC 9068)
 * 4. Extract DPoP binding (cnf.jkt) if present
 * 5. Return TokenPayload.Jwt
 *
 * **Note**: Relies on JwtService for signature verification and uses JwsUtils
 * for proper base64url decoding.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyJwtCommandImpl", exact = true)
class VerifyJwtCommandImpl(
    execution: SessionExecution,
    private val jwtService: JwtService,
) : TypedServiceCommandAdapter<VerifyJwtArgs, TokenPayload.Jwt>(
        commandId = VerifyJwtCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifyJwtArgs>(),
        outputTypeToken = typeToken<TokenPayload.Jwt>(),
    ),
    VerifyJwtCommand {
    override val commandId: String get() = VerifyJwtCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyJwtArgs

    override suspend fun doExecute(
        args: VerifyJwtArgs,
        applyDuring: (VerifyJwtArgs) -> VerifyJwtArgs,
    ): IdkResult<TokenPayload.Jwt, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.jwt, applied.authorizationServer, applied.expectedAudience, applied.jwksUri).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        jwt: String,
        authorizationServer: String,
        expectedAudience: String?,
        jwksUri: String? = null,
    ): IdkResult<TokenPayload.Jwt, ResourceServerError> {
        // 1. Verify JWT signature using JwtService
        val identifier =
            if (jwksUri != null) {
                val parts = jwt.split(".")
                if (parts.size == 3) {
                    val headerJson =
                        try {
                            JwsUtils.decodeBase64UrlToJson(parts[0])
                        } catch (expected: Exception) {
                            execution.log.debug("Failed to decode JWT header for kid extraction: ${expected.message}")
                            null
                        }
                    val kid = headerJson?.get("kid")?.jsonPrimitive?.content
                    ExternalIdentifierJwksUrlOpts(
                        identifier = jwksUri,
                        lookup = AdditionalIdentifierLookup(kid = kid),
                    )
                } else {
                    null
                }
            } else {
                null
            }

        val verifyArgs = VerifyJwsArgs(jws = JwsCompact(jwt), identifier = identifier)
        val verificationResult = jwtService.verifyJws(verifyArgs)

        if (verificationResult.isErr) {
            return Err(
                ResourceServerError.InvalidToken(
                    "JWT verification failed: ${verificationResult.error.message}",
                ),
            )
        }

        val validationResult = verificationResult.value
        if (!validationResult.isValid) {
            return Err(
                ResourceServerError.InvalidToken(
                    "JWT signature invalid: ${validationResult.errorMessages.joinToString(", ")}",
                ),
            )
        }

        // 2. Extract header and payload using JwsUtils
        val jwsGeneral = validationResult.jws
        if (jwsGeneral.signatures.isEmpty()) {
            return Err(ResourceServerError.InvalidToken("JWT has no signatures"))
        }

        // Parse header from first signature (JWT has only one signature)
        val protectedHeader =
            try {
                JwsUtils.decodeBase64UrlToJson(jwsGeneral.signatures[0].protected)
            } catch (expected: Exception) {
                return Err(ResourceServerError.InvalidToken("Failed to parse JWT header: ${expected.message}"))
            }

        // Parse payload
        val payloadJson =
            try {
                JwsUtils.decodeBase64UrlToJson(jwsGeneral.payload)
            } catch (expected: Exception) {
                return Err(ResourceServerError.InvalidToken("Failed to parse JWT payload: ${expected.message}"))
            }

        // 3. Validate typ header (RFC 9068 recommends "at+jwt")
        val typ = protectedHeader["typ"]?.jsonPrimitive?.content
        if (typ != null && typ != "at+jwt" && typ != "JWT") {
            // Be lenient - accept both "at+jwt" and "JWT" or missing typ
            return Err(
                ResourceServerError.InvalidToken(
                    "Invalid JWT type: expected 'at+jwt' or 'JWT', got '$typ'",
                ),
            )
        }

        // 4. Extract and validate required claims
        val iss = payloadJson["iss"]?.jsonPrimitive?.content
        if (iss != authorizationServer) {
            return Err(
                ResourceServerError.InvalidToken(
                    "Issuer mismatch: expected '$authorizationServer', got '$iss'",
                ),
            )
        }

        val sub =
            payloadJson["sub"]?.jsonPrimitive?.content
                ?: return Err(ResourceServerError.InvalidToken("Missing sub claim"))

        val exp =
            payloadJson["exp"]
                ?.jsonPrimitive
                ?.content
                ?.toDoubleOrNull()
                ?.toLong()
                ?: return Err(ResourceServerError.InvalidToken("Missing or invalid exp claim"))

        val iat =
            payloadJson["iat"]
                ?.jsonPrimitive
                ?.content
                ?.toDoubleOrNull()
                ?.toLong()
                ?: return Err(ResourceServerError.InvalidToken("Missing or invalid iat claim"))

        // 5. Validate expiration
        val now = Clock.System.now()
        val expInstant = Instant.fromEpochSeconds(exp)
        if (now >= expInstant) {
            return Err(ResourceServerError.InvalidToken("Token expired at $expInstant"))
        }

        // 6. Validate audience if specified
        val audValue = payloadJson["aud"]
        val audiences =
            when {
                audValue == null -> {
                    null
                }

                audValue is JsonPrimitive -> {
                    listOf(audValue.content)
                }

                else -> {
                    try {
                        audValue.jsonArray.map { it.jsonPrimitive.content }
                    } catch (_: Exception) {
                        return Err(ResourceServerError.InvalidToken("Invalid aud claim format"))
                    }
                }
            }

        if (expectedAudience != null && audiences != null) {
            if (!audiences.contains(expectedAudience)) {
                return Err(
                    ResourceServerError.AudienceMismatch(
                        expected = expectedAudience,
                        actual = audiences,
                    ),
                )
            }
        }

        // 7. Extract optional claims
        val scope = payloadJson["scope"]?.jsonPrimitive?.content
        val clientId = payloadJson["client_id"]?.jsonPrimitive?.content
        val jti = payloadJson["jti"]?.jsonPrimitive?.content

        // 8. Extract DPoP binding (cnf.jkt) if present (RFC 9449)
        val dpopJkt =
            payloadJson["cnf"]
                ?.jsonObject
                ?.get("jkt")
                ?.jsonPrimitive
                ?.content

        // 9. Build and return TokenPayload.Jwt
        return Ok(
            TokenPayload.Jwt(
                sub = sub,
                iss = iss ?: authorizationServer,
                aud = audiences,
                exp = expInstant,
                iat = Instant.fromEpochSeconds(iat),
                scope = scope,
                clientId = clientId,
                dpopJkt = dpopJkt,
                jti = jti,
            ),
        )
    }
}
