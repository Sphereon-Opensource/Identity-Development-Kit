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

@file:Suppress("ReturnCount", "TooGenericExceptionCaught", "LongMethod", "CyclomaticComplexMethod")

package com.sphereon.oauth2.server.authorization.impl.command.jar

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.FetchRequestUriArgs
import com.sphereon.ktor.http.client.FetchRequestUriCommand
import com.sphereon.ktor.http.client.RequestUriMethod
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.server.authorization.command.jar.VerifiedRequestObject
import com.sphereon.oauth2.server.authorization.command.jar.VerifyRequestObjectArgs
import com.sphereon.oauth2.server.authorization.command.jar.VerifyRequestObjectCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

/**
 * Server-side implementation of RFC 9101 (JAR) acceptance.
 *
 * The flow:
 *   1. Both `request` and `request_uri` present, fail with `invalid_request` per RFC 9101 §5.
 *   2. For `request_uri`, fetch the body via [FetchRequestUriCommand]; for `request`, treat the
 *      input as the JWT directly.
 *   3. Decode the JWS header without trusting it. Refuse `alg=none` (RFC 9101 §6 / RFC 8725 §2.1).
 *   4. Resolve the client by the JWT's `iss` / `client_id` claim (cross-checking against the
 *      front-channel `client_id` hint when the caller supplied one, OIDC Core §6.1).
 *   5. Build the trusted JWKS from the client's inline `jwks` (the JWKS-URI fetch path is a
 *      flagged follow-up at this layer) and call [VerifyJwsCommand] in pinned-JWKS mode.
 *   6. Validate `typ`, `aud`, `iss`, `exp`, `iat` per RFC 9101 §10.
 *   7. Strip JWT-only claims and merge the JAR claims onto the front-channel parameters per
 *      RFC 9101 §6.1 (signed claims win).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<VerifyRequestObjectCommand>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyRequestObjectCommandImpl", exact = true)
class VerifyRequestObjectCommandImpl(
    execution: SessionExecution,
    private val clientRegistry: ClientRegistry,
    private val verifyJwsCommand: VerifyJwsCommand,
    private val fetchRequestUriCommand: FetchRequestUriCommand,
    private val serversConfigProvider: OAuth2ServersConfigProvider,
) : TypedServiceCommandAdapter<VerifyRequestObjectArgs, VerifiedRequestObject, IdkError>(
        commandId = VerifyRequestObjectCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifyRequestObjectArgs>(),
        outputTypeToken = typeToken<VerifiedRequestObject>(),
    ),
    VerifyRequestObjectCommand {
    override val commandId: String get() = VerifyRequestObjectCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyRequestObjectArgs

    override suspend fun doExecute(
        args: VerifyRequestObjectArgs,
        applyDuring: (VerifyRequestObjectArgs) -> VerifyRequestObjectArgs,
    ): IdkResult<VerifiedRequestObject, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(args: VerifyRequestObjectArgs): IdkResult<VerifiedRequestObject, AuthorizationServerError> {
        val serverConfig = serversConfigProvider.serverConfig
        if (!serverConfig.jar.isEnabled) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "JAR (RFC 9101) is disabled on this authorization server",
                ),
            )
        }

        if (args.requestJwt != null && args.requestUri != null) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "request and request_uri MUST NOT both be present (RFC 9101 §5)",
                ),
            )
        }

        val requestJwt = args.requestJwt
        val requestUri = args.requestUri
        val signedJwt: String =
            when {
                requestJwt != null -> {
                    requestJwt
                }

                requestUri != null -> {
                    val fetched = fetchAndExtractJwt(requestUri)
                    if (fetched.isErr) return Err(fetched.error)
                    fetched.value
                }

                else -> {
                    return Err(
                        AuthorizationServerError.InvalidRequest(
                            details = "Either request or request_uri must be supplied",
                        ),
                    )
                }
            }

        val parts = signedJwt.split(".")
        if (parts.size != JWS_PART_COUNT) {
            return Err(
                AuthorizationServerError.InvalidRequestObject(
                    details = "JAR is not a compact JWS (expected 3 parts, got ${parts.size})",
                ),
            )
        }

        val header =
            decodeBase64UrlJson(parts[0])
                ?: return Err(AuthorizationServerError.InvalidRequestObject(details = "Malformed JAR protected header"))
        val payload =
            decodeBase64UrlJson(parts[1])
                ?: return Err(AuthorizationServerError.InvalidRequestObject(details = "Malformed JAR payload"))

        val alg = header["alg"]?.jsonPrimitive?.content
        if (alg.isNullOrBlank()) {
            return Err(AuthorizationServerError.InvalidRequestObject(details = "JAR header missing 'alg'"))
        }
        if (alg.equals("none", ignoreCase = true)) {
            return Err(
                AuthorizationServerError.InvalidRequestObject(
                    details = "JAR with alg='none' is forbidden (RFC 9101 §6 / RFC 8725 §2.1)",
                ),
            )
        }

        val typ = header["typ"]?.jsonPrimitive?.content
        if (typ != VerifyRequestObjectCommand.JAR_JWT_TYP) {
            return Err(
                AuthorizationServerError.InvalidRequestObject(
                    details = "JAR header 'typ' must be '${VerifyRequestObjectCommand.JAR_JWT_TYP}', got '$typ' (RFC 9101 §10.8)",
                ),
            )
        }

        // RFC 9101 §6.1 + OIDC Core §6.1: the JAR's `iss` (and inner `client_id`) MUST match the
        // front-channel `client_id` hint. The verifier resolves the client by the JWT-asserted
        // identity to fetch its keys, then enforces the cross-check.
        val jarIss = payload["iss"]?.jsonPrimitive?.content
        val jarClientId = payload["client_id"]?.jsonPrimitive?.content
        val resolvedClientId =
            jarIss ?: jarClientId
                ?: return Err(
                    AuthorizationServerError.InvalidRequestObject(
                        details = "JAR is missing both 'iss' and 'client_id' claims",
                    ),
                )

        val hint = args.clientIdHint
        if (hint != null && hint != resolvedClientId) {
            return Err(
                AuthorizationServerError.InvalidRequestObject(
                    details = "JAR 'iss' / 'client_id' '$resolvedClientId' does not match front-channel client_id '$hint'",
                ),
            )
        }

        val clientResult = clientRegistry.getClient(resolvedClientId)
        if (clientResult.isErr) {
            return Err(
                AuthorizationServerError.InvalidRequestObject(
                    details = "Failed to load client '$resolvedClientId': ${clientResult.error.message.defaultMessage}",
                ),
            )
        }
        val client =
            clientResult.value
                ?: return Err(
                    AuthorizationServerError.InvalidRequestObject(
                        details = "Unknown client '$resolvedClientId'",
                    ),
                )

        val pinnedAlg = client.requestObjectSigningAlg
        if (pinnedAlg != null && !pinnedAlg.equals(alg, ignoreCase = false)) {
            return Err(
                AuthorizationServerError.InvalidRequestObject(
                    details = "JAR alg '$alg' does not match client's registered request_object_signing_alg '$pinnedAlg'",
                ),
            )
        }

        val advertisedAlgs =
            serverConfig.requestObjectSigningAlgValuesSupported
                ?: serverConfig.idTokenSigningAlgValuesSupported
        if (advertisedAlgs != null && advertisedAlgs.isNotEmpty() && alg !in advertisedAlgs) {
            return Err(
                AuthorizationServerError.InvalidRequestObject(
                    details =
                        "JAR alg '$alg' is not in the AS's request_object_signing_alg_values_supported " +
                            "list (${advertisedAlgs.joinToString(", ")})",
                ),
            )
        }

        val trustedJwks =
            buildTrustedJwks(client)
                ?: return Err(
                    AuthorizationServerError.InvalidRequestObject(
                        details =
                            "Client '$resolvedClientId' has no inline 'jwks' configured for JAR verification " +
                                "(jwks_uri fetch is not yet implemented at this layer)",
                    ),
                )

        val verify =
            verifyJwsCommand.execute(
                VerifyJwsArgs(
                    jws = JwsCompact(signedJwt),
                    trustedJwks = trustedJwks,
                ),
            )
        if (verify.isErr) {
            return Err(
                AuthorizationServerError.InvalidRequestObject(
                    details = "JAR signature verification failed: ${verify.error.message.defaultMessage}",
                ),
            )
        }
        if (!verify.value.isValid) {
            return Err(
                AuthorizationServerError.InvalidRequestObject(
                    details = "JAR signature is invalid: ${verify.value.errorMessages.joinToString("; ")}",
                ),
            )
        }

        // RFC 9101 §10.2 — `aud` MUST be the AS issuer.
        val aud = payload["aud"]
        val audMatches =
            when {
                aud is JsonPrimitive -> aud.content == args.issuer
                aud is kotlinx.serialization.json.JsonArray -> aud.any { it.jsonPrimitive.content == args.issuer }
                else -> false
            }
        if (!audMatches) {
            return Err(
                AuthorizationServerError.InvalidRequestObject(
                    details = "JAR 'aud' does not match the AS issuer '${args.issuer}'",
                ),
            )
        }

        val nowSeconds = Clock.System.now().epochSeconds
        val exp =
            payload["exp"]?.jsonPrimitive?.long
                ?: return Err(AuthorizationServerError.InvalidRequestObject(details = "JAR is missing 'exp' claim"))
        if (nowSeconds > exp + VerifyRequestObjectCommand.CLOCK_SKEW_SECONDS) {
            return Err(AuthorizationServerError.InvalidRequestObject(details = "JAR has expired"))
        }
        val iat = payload["iat"]?.jsonPrimitive?.long
        if (iat != null && iat - VerifyRequestObjectCommand.CLOCK_SKEW_SECONDS > nowSeconds) {
            return Err(AuthorizationServerError.InvalidRequestObject(details = "JAR 'iat' is in the future"))
        }

        // RFC 9101 §6.1: signed claims override the front-channel parameters.
        val merged = mergeParameters(args.queryParameters, payload)
        return Ok(
            VerifiedRequestObject(
                mergedParameters = merged,
                clientId = resolvedClientId,
                signingAlg = alg,
            ),
        )
    }

    private suspend fun fetchAndExtractJwt(uri: String): IdkResult<String, AuthorizationServerError> {
        val fetched = fetchRequestUriCommand.execute(FetchRequestUriArgs(requestUri = uri, httpMethod = RequestUriMethod.GET))
        if (fetched.isErr) {
            return Err(
                AuthorizationServerError.InvalidRequestUri(
                    details = "Failed to fetch request_uri '$uri': ${fetched.error.message.defaultMessage}",
                ),
            )
        }
        val body = fetched.value.content.trim()
        if (body.isEmpty()) {
            return Err(
                AuthorizationServerError.InvalidRequestUri(
                    details = "request_uri '$uri' returned an empty body",
                ),
            )
        }
        return Ok(body)
    }

    private fun buildTrustedJwks(client: ClientRegistration): JsonObject? {
        val keys = client.jwks ?: return null
        if (keys.isEmpty()) return null
        val set = JwkSet(keys = keys.toTypedArray())
        return Json.encodeToJsonElement(JwkSet.serializer(), set).jsonObject
    }

    private fun decodeBase64UrlJson(encoded: String): JsonObject? =
        try {
            val bytes = encoded.decodeFrom(Encoding.BASE64URL)
            Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
        } catch (expected: Exception) {
            null
        }

    private fun mergeParameters(
        front: Map<String, String>,
        payload: JsonObject,
    ): Map<String, String> {
        val merged = front.toMutableMap()
        payload.forEach { (key, value) ->
            if (key in JWT_ENVELOPE_CLAIMS) return@forEach
            merged[key] = jsonElementToParameterValue(value)
        }
        return merged
    }

    private fun jsonElementToParameterValue(element: JsonElement): String =
        when (element) {
            is JsonPrimitive -> if (element.isString) element.content else element.toString()
            else -> element.toString()
        }

    companion object {
        private const val JWS_PART_COUNT = 3

        /** RFC 9101 §6.1: JWT-envelope claims to strip before merging. */
        private val JWT_ENVELOPE_CLAIMS: Set<String> = setOf("iss", "aud", "exp", "iat", "jti", "nbf", "typ", "alg")
    }
}
