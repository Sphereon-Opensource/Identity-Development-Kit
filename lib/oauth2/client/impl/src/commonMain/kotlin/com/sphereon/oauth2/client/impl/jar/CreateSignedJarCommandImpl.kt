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

package com.sphereon.oauth2.client.impl.jar

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.CreateSignedJarArgs
import com.sphereon.oauth2.client.command.CreateSignedJarCommand
import com.sphereon.oauth2.common.error.Oauth2Error
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * Implementation of CreateSignedJarCommand
 *
 * Creates signed JAR (JWT-secured Authorization Request) as defined in RFC 9101.
 *
 * This command:
 * 1. Converts authorization request parameters to JWT claims
 * 2. Adds standard JWT claims (iss, aud, exp, iat, jti)
 * 3. Signs the JWT using the client's private key
 * 4. Returns the compact JWS serialization
 */
@Inject
@SingleIn(SessionScope::class)
class CreateSignedJarCommandImpl(
    execution: SessionExecution,
    private val jwtService: JwtService,
    private val secureRandom: SecureRandom,
) : TypedServiceCommandAdapter<CreateSignedJarArgs, StringResult, IdkError>(
        commandId = CreateSignedJarCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateSignedJarArgs>(),
        outputTypeToken = typeToken<StringResult>(),
    ),
    CreateSignedJarCommand {
    override val commandId: String get() = CreateSignedJarCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateSignedJarArgs

    override suspend fun doExecute(
        args: CreateSignedJarArgs,
        applyDuring: (CreateSignedJarArgs) -> CreateSignedJarArgs,
    ): IdkResult<StringResult, IdkError> {
        val applied = applyDuring(args)
        return createSignedJarInternal(applied).map { StringResult(it) }.mapError { IdkError.fromDTO(it) }
    }

    private suspend fun createSignedJarInternal(args: CreateSignedJarArgs): IdkResult<String, Oauth2Error> {
        try {
            // Get current timestamp
            val iat = Clock.System.now().epochSeconds
            val exp = iat + args.expirationSeconds

            // Generate unique JTI
            val jti = generateJti()

            // Convert authorization request to JSON
            val authRequestJson = Json.encodeToJsonElement(args.authorizationRequest).jsonObject

            // Build JAR payload with JWT claims + authorization request parameters
            val payload =
                buildJsonObject {
                    // Standard JWT claims (RFC 9101 Section 3). `iss` is SHOULD not MUST
                    // and is caller-controlled (see CreateSignedJarArgs.includeIss).
                    if (args.includeIss) {
                        put("iss", args.issuer)
                    }
                    put("aud", args.audience)
                    put("iat", iat)
                    put("exp", exp)
                    put("jti", jti)

                    // Add all authorization request parameters as claims
                    authRequestJson.forEach { (key, value) ->
                        // Skip null values
                        if (!value.toString().equals("null", ignoreCase = true)) {
                            put(key, value)
                        }
                    }
                }

            // Serialize payload to JSON string
            val payloadString = Json.encodeToString(JsonObject.serializer(), payload)

            // Resolve the JOSE header identifier. Exactly one of kid or x5c is used:
            //   x5c present         → emit x5c chain (RFC 7515 §4.1.6).
            //   kid or DID issuer   → emit kid (RFC 7515 §4.1.4). If issuer is a DID URL
            //                         or carries a `<prefix>:did:...` form, treat as DID.
            //   neither             → auto-detect via the JWS pipeline.
            val explicitKid = args.kid
            val x5cChain = args.x5c
            val isDid =
                explicitKid?.startsWith("did:") == true ||
                    args.issuer.startsWith("did:") ||
                    args.issuer.substringAfter(':', "").startsWith("did:")
            val jwsMode =
                when {
                    x5cChain != null -> JwsIdentifierMode.X5C
                    isDid -> JwsIdentifierMode.DID
                    else -> JwsIdentifierMode.AUTO
                }
            // `typ: oauth-authz-req+jwt` is REQUIRED for every JAR per RFC 9101 §10.8
            // and reiterated by OID4VP 1.0 §5.10.3. Wallets (including credo-ts
            // `oid4vc-ts/packages/openid4vp/src/jar/.../verify-jar-request.ts`) reject
            // the JAR when this header is missing or has any other value.
            // Only the caller knows the correct verification-method URL for a DID —
            // it is NEVER "<did>#0" unless the caller explicitly says so. If the
            // issuer is a DID but no kid was supplied, we leave the header identifier
            // to the JWS pipeline (which resolves it from the resolved KMS key's
            // stored kid). Callers that need a specific DID VM URL MUST pass it via
            // [args.kid].
            val protectedHeader =
                when {
                    x5cChain != null -> {
                        buildJsonObject {
                            put("typ", JsonPrimitive(JAR_JWT_TYP))
                            put("x5c", JsonArray(x5cChain.map { JsonPrimitive(it) }))
                        }
                    }

                    explicitKid != null -> {
                        buildJsonObject {
                            put("typ", JsonPrimitive(JAR_JWT_TYP))
                            put("kid", explicitKid)
                        }
                    }

                    else -> {
                        buildJsonObject {
                            put("typ", JsonPrimitive(JAR_JWT_TYP))
                        }
                    }
                }

            // Create signed JWT using JwtService
            val jwsArgs =
                CreateJwsArgs(
                    issuer = ManagedOptsKeyInfo(identifier = args.signingKey),
                    payload = payloadString,
                    mode = jwsMode,
                    opts =
                        CreateJwsOpts(
                            noIssPayloadUpdate = true, // Don't modify iss - we already set it
                            // Keep noIdentifierInHeader=false so PrepareJwsCommandImpl
                            // adds the `alg` claim based on the signing key. When we
                            // supply our own [protectedHeader] (for OID4VP §5.9.3 DID
                            // or x509 bindings) the pipeline starts from our map, adds
                            // alg, and only overwrites the identifier when it can derive
                            // a stronger one from the resolved key (DID-prefixed kid,
                            // or x509 chain). Setting this to true would skip the entire
                            // identifier-population function including alg → JWS becomes
                            // invalid (wallets reject "expected string received undefined
                            // at 'alg'").
                            noIdentifierInHeader = false,
                            protectedHeader = protectedHeader,
                        ),
                )

            // Sign the JWT
            val jwtResult =
                jwtService.createJwsCompact(jwsArgs).getOrElse { error ->
                    return Err(
                        Oauth2Error.JarCreationFailed(
                            failureMessage = "Failed to sign JAR: ${error.message.defaultMessage}",
                            cause = error.exception,
                        ),
                    )
                }

            return Ok(jwtResult.jwt)
        } catch (expected: Exception) {
            return Err(
                Oauth2Error.JarCreationFailed(
                    failureMessage = "JAR creation failed: ${expected.message}",
                    cause = expected,
                ),
            )
        }
    }

    /**
     * Generates a unique JWT ID (16 random bytes, base64url-encoded).
     */
    private suspend fun generateJti(): String = secureRandom.newToken(lengthBytes = JTI_RANDOM_BYTES)

    companion object {
        private const val JTI_RANDOM_BYTES = 16

        /** RFC 9101 §10.8 / OID4VP §5.10.3 — JAR JWT `typ` header. */
        private const val JAR_JWT_TYP = "oauth-authz-req+jwt"
    }
}
