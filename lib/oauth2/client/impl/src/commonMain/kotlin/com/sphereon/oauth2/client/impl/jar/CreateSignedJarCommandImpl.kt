/*
 * © 2025 Sphereon International B.V.
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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.Err
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.jose.jws.CreateJwsArgs
import com.sphereon.crypto.jose.jws.CreateJwsOpts
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.di.session.SessionScope
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.oauth2.client.command.CreateSignedJarArgs
import com.sphereon.oauth2.client.command.CreateSignedJarCommand
import com.sphereon.oauth2.common.error.Oauth2Error
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.random.Random

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
    private val jwtService: JwtService
) : TypedServiceCommandAdapter<CreateSignedJarArgs, StringResult>(
    commandId = CreateSignedJarCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<CreateSignedJarArgs>(),
    outputTypeToken = typeToken<StringResult>(),
), CreateSignedJarCommand {

    override val commandId: String get() = CreateSignedJarCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateSignedJarArgs

    override suspend fun doExecute(
        args: CreateSignedJarArgs,
        applyDuring: (CreateSignedJarArgs) -> CreateSignedJarArgs
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
            val payload = buildJsonObject {
                // Standard JWT claims (RFC 9101 Section 3)
                put("iss", args.issuer)
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

            // When the issuer (client_id) is a DID, set the DID verification method
            // as kid in the JAR header per OID4VP 1.0 Section 5.2. The signing key
            // itself is looked up by alias in the KMS; the kid is set at the protocol layer.
            val isDid = args.issuer.startsWith("did:")
            val jwsMode = if (isDid) JwsIdentifierMode.DID else JwsIdentifierMode.AUTO
            val protectedHeader = if (isDid) {
                buildJsonObject { put("kid", "${args.issuer}#0") }
            } else null

            // Create signed JWT using JwtService
            val jwsArgs = CreateJwsArgs(
                issuer = ManagedOptsKeyInfo(identifier = args.signingKey),
                payload = payloadString,
                mode = jwsMode,
                opts = CreateJwsOpts(
                    noIssPayloadUpdate = true, // Don't modify iss - we already set it
                    noIdentifierInHeader = false, // Include kid in header for verification
                    protectedHeader = protectedHeader
                )
            )

            // Sign the JWT
            val jwtResult = jwtService.createJwsCompact(jwsArgs).getOrElse { error ->
                return Err(Oauth2Error.JarCreationFailed(
                    failureMessage = "Failed to sign JAR: ${error.message.defaultMessage}",
                    cause = error.exception
                ))
            }

            return Ok(jwtResult.jwt)

        } catch (e: Exception) {
            return Err(Oauth2Error.JarCreationFailed(
                failureMessage = "JAR creation failed: ${e.message}",
                cause = e
            ))
        }
    }

    /**
     * Generates a unique JWT ID using random bytes
     */
    private fun generateJti(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.encodeToBase64Url()
    }
}
