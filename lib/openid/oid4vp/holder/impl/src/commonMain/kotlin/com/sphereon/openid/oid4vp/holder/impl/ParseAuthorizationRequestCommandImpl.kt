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

package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.api.validation.toIdkResult
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.resolution.extern.ExternalIdentifierDidOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwksUrlOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierX5cOpts
import com.sphereon.crypto.resolution.extern.MultiExternalIdentifierService
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.FetchRequestUriArgs
import com.sphereon.ktor.http.client.FetchRequestUriCommand
import com.sphereon.ktor.http.client.ParseUriQueryCommand
import com.sphereon.ktor.http.client.RequestUriMethod
import com.sphereon.ktor.http.client.getOptional
import com.sphereon.ktor.http.client.getOrDefault
import com.sphereon.ktor.http.client.getRequired
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.client.JarService
import com.sphereon.oauth2.client.command.MergeRequestObjectArgs
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ClientMetadata
import com.sphereon.openid.oid4vp.common.Oid4vpJson
import com.sphereon.openid.oid4vp.holder.ParseAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.holder.ParseAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.holder.ParseAuthorizationRequestCommandService
import com.sphereon.openid.oid4vp.holder.WalletConfig
import com.sphereon.openid.oid4vp.holder.validation.validateOid4vpAuthorizationRequest
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Implementation of ParseAuthorizationRequestCommand for OpenID4VP.
 *
 * Parses OpenID4VP authorization request URIs, handling:
 * - Direct request parameters (inline query string)
 * - Request by reference (request_uri parameter)
 * - Request Object (signed JWT)
 *
 * Reference: OpenID4VP 1.0 Section 5.1 - Authorization Request
 *
 * Supported URI formats:
 * - `openid4vp://?client_id=...&...` (direct inline parameters)
 * - `openid4vp://?client_id=...&request_uri=https://...` (request by reference)
 * - `openid4vp://?client_id=...&request=eyJ...` (request object JWT)
 */
@Inject
@SingleIn(SessionScope::class)
class ParseAuthorizationRequestCommandImpl(
    execution: SessionExecution,
    private val parseUriQueryCommand: ParseUriQueryCommand,
    private val fetchRequestUriCommand: FetchRequestUriCommand,
    private val jarService: JarService,
    private val httpClientFactory: HttpClientFactory,
    private val externalIdentifierService: MultiExternalIdentifierService,
) : TypedServiceCommandAdapter<ParseAuthorizationRequestArgs, AuthorizationRequest, IdkError>(
        commandId = ParseAuthorizationRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ParseAuthorizationRequestArgs>(),
        outputTypeToken = typeToken<AuthorizationRequest>(),
    ),
    ParseAuthorizationRequestCommand,
    ParseAuthorizationRequestCommandService {
    override val commandId: String get() = ParseAuthorizationRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ParseAuthorizationRequestArgs

    override suspend fun parseAuthorizationRequest(
        requestUri: String,
        walletConfig: WalletConfig?,
    ): IdkResult<AuthorizationRequest, IdkError> = execute(ParseAuthorizationRequestArgs(requestUri, walletConfig))

    override suspend fun doExecute(
        args: ParseAuthorizationRequestArgs,
        applyDuring: (ParseAuthorizationRequestArgs) -> ParseAuthorizationRequestArgs,
    ): IdkResult<AuthorizationRequest, IdkError> {
        val processedArgs = applyDuring(args)
        val requestUri = processedArgs.requestUri
        val walletConfig = processedArgs.walletConfig

        // Parse URI using dedicated command
        val parsedUri =
            parseUriQueryCommand
                .execute(requestUri)
                .getOrElse { return Err(it) }

        // Validate that query parameters are present
        if (parsedUri.parameters.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid authorization request URI: missing query parameters"))
        }

        // Handle Request Objects (JWT) - RFC 9101 (JAR) and OpenID4VP
        // Two methods per RFC 9101:
        // 1. request_uri: JWT fetched from remote URL
        // 2. request: JWT passed inline in query parameter
        val requestUriParam = parsedUri.getFirst("request_uri")
        val requestParam = parsedUri.getFirst("request")

        // OpenID4VP 1.0: request_uri_method specifies HTTP method for fetching request_uri
        // Defaults to "get" if not specified
        val requestUriMethod = parsedUri.getFirst("request_uri_method")?.lowercase() ?: "get"

        // RFC 9101 Section 3.2: The client SHALL NOT use both the request and request_uri parameters
        // in the same authorization request.
        if (requestUriParam != null && requestParam != null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "The 'request' and 'request_uri' parameters SHALL NOT be used in the same authorization request"))
        }

        // Resolve the request object JWT (either by fetching from request_uri or using request parameter)
        val requestObjectJwt =
            when {
                // Handle request_uri (fetch remote request object)
                requestUriParam != null -> {
                    // Fetch the request object from the remote URI
                    // Per RFC 9101 Section 3.2: content type SHOULD be application/oauth-authz-req+jwt
                    // OpenID4VP inherits this requirement, though some implementations may vary

                    // Parse HTTP method from request_uri_method parameter (OpenID4VP 1.0)
                    val httpMethod =
                        when (requestUriMethod) {
                            "post" -> {
                                RequestUriMethod.POST
                            }

                            "get" -> {
                                RequestUriMethod.GET
                            }

                            else -> {
                                return Err(
                                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                                        message = "Invalid request_uri_method: $requestUriMethod. Must be 'get' or 'post'",
                                    ),
                                )
                            }
                        }

                    fetchRequestUriCommand
                        .execute(
                            FetchRequestUriArgs(
                                requestUri = requestUriParam,
                                httpMethod = httpMethod,
                                httpClientOptions = HttpClientOptions.createDefault(),
                                expectedContentType = null, // Don't enforce - verify JWT format after fetch
                            ),
                        ).getOrElse { return Err(it) }
                        .content
                }

                // Handle request (inline JWT request object)
                requestParam != null -> {
                    // Per RFC 9101 Section 3.1: The request parameter is a JWT that contains
                    // the authorization request parameters. It can be signed, encrypted, or both.
                    requestParam
                }

                // No request object - use query parameters directly
                else -> {
                    null
                }
            }

        // Merge Request Object parameters with query parameters if present
        val mergedParams =
            if (requestObjectJwt != null) {
                mergeRequestObject(requestObjectJwt, parsedUri.parameters, walletConfig)
                    .getOrElse { return Err(it) }
            } else {
                parsedUri.parameters
            }

        // Note: dcql_query is extracted into additionalParameters below (non-standard params)
        // Actual DCQL parsing and validation happens in ResolveAuthorizationRequestCommand

        // Extract required parameters using extension functions
        val clientId =
            mergedParams
                .getRequired("client_id")
                .getOrElse { return Err(it) }

        // Per OID4VP 1.0: redirect_uri is OPTIONAL when response_mode is direct_post
        // or direct_post.jwt, because response_uri is used instead.
        val responseMode = mergedParams.getOptional("response_mode")
        val isDirectPost = responseMode == "direct_post" || responseMode == "direct_post.jwt"
        val redirectUri =
            if (isDirectPost) {
                mergedParams.getOptional("redirect_uri")
            } else {
                mergedParams
                    .getRequired("redirect_uri")
                    .getOrElse { return Err(it) }
            }

        // Build additional parameters as JsonElements (first value only for each key)
        val standardParams =
            setOf(
                "client_id",
                "response_type",
                "redirect_uri",
                "scope",
                "state",
                "nonce",
                "response_mode",
                "request_uri",
                "request",
                "resource",
                "issuer_state",
                "dpop_jkt",
            )
        val additionalParamsFromRequest =
            mergedParams
                .names()
                .filter { key -> key !in standardParams }
                .associate { key -> key to JsonPrimitive(mergedParams[key] ?: "") }

        val jarParams = requestObjectJwt?.let { extractJarContextParams(it) } ?: emptyMap()

        // Create AuthorizationRequest from merged parameters
        val authRequest =
            AuthorizationRequest(
                clientId = clientId,
                redirectUri = redirectUri,
                responseType = mergedParams.getOrDefault("response_type", "vp_token"),
                scope = mergedParams.getOptional("scope"),
                state = mergedParams.getOptional("state"),
                nonce = mergedParams.getOptional("nonce"),
                responseMode = mergedParams.getOptional("response_mode"),
                requestUri = mergedParams.getOptional("request_uri"),
                request = mergedParams.getOptional("request"),
                resource = mergedParams.getOptional("resource"),
                issuerState = mergedParams.getOptional("issuer_state"),
                dpopJkt = mergedParams.getOptional("dpop_jkt"),
                additionalParameters = additionalParamsFromRequest + jarParams,
            )

        // Validate the authorization request using Konform
        return validateOid4vpAuthorizationRequest(authRequest).toIdkResult { errors ->
            IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Invalid OpenID4VP authorization request: ${errors.joinToString(", ") { "${it.path}: ${it.message}" }}",
            )
        }
    }

    /**
     * Merges JWT request object parameters with query parameters.
     *
     * Per RFC 9101 Section 3.2: Request Object params take precedence, with specific override rules.
     *
     * JWT validation parameters:
     * - audience: From walletConfig (the expected Request Object JWT `aud` claim)
     * - decryptionKey: From walletConfig (for decrypting JWE request objects)
     * - verificationKey: Should be resolved from client metadata (jwks/jwks_uri)
     *   TODO: Implement client metadata resolution to fetch verificationKey
     *         Requires ResolveAuthorizationRequestCommand with client metadata fetching
     *
     * @param requestObjectJwt The JWT string (signed or encrypted)
     * @param queryParameters The query parameters from the URI
     * @param walletConfig Optional wallet configuration
     * @return Merged parameters or error
     */
    private suspend fun mergeRequestObject(
        requestObjectJwt: String,
        queryParameters: io.ktor.http.Parameters,
        walletConfig: WalletConfig?,
    ): IdkResult<io.ktor.http.Parameters, IdkError> {
        val header = extractJwtHeader(requestObjectJwt)
        val payloadHint = extractJwtPayload(requestObjectJwt)
        val kid = header["kid"]?.jsonPrimitive?.contentOrNull
        val x5cHeader =
            (header["x5c"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val clientId = queryParameters["client_id"]
        val signedClientId = payloadHint["client_id"]?.jsonPrimitive?.contentOrNull
        if (!clientId.isNullOrBlank() && !signedClientId.isNullOrBlank() && clientId != signedClientId) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Request object client_id mismatch: outer='$clientId', signed='$signedClientId'",
                ),
            )
        }
        val outerClientIdScheme = queryParameters["client_id_scheme"]?.takeIf { it.isNotBlank() }
        val signedClientIdScheme = payloadHint["client_id_scheme"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        if (outerClientIdScheme != null && signedClientIdScheme != null && outerClientIdScheme != signedClientIdScheme) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message =
                        "Request object client_id_scheme mismatch: " +
                            "outer='$outerClientIdScheme', signed='$signedClientIdScheme'",
                ),
            )
        }
        val clientMetadata = resolveClientMetadataFromQuery(queryParameters).getOrElse { return Err(it) }
        val verificationKey =
            resolveJarVerificationKey(
                clientId = clientId,
                clientIdSchemeHint = outerClientIdScheme ?: signedClientIdScheme,
                clientMetadata = clientMetadata,
                requestedKid = kid,
                x5cHeader = x5cHeader,
            ).getOrElse { return Err(it) }

        // RFC 9101 binds `iss` to the complete OAuth client identifier. OID4VP 1.0 also
        // requires outer and signed client_id values to match, including the identifier prefix.
        val jarIssuer = oid4vpJarIssuer(clientId)
        val mergeArgs =
            MergeRequestObjectArgs(
                requestObjectJwt = requestObjectJwt,
                queryParameters = queryParameters,
                issuer = jarIssuer,
                audience = walletConfig?.audience,
                verificationKey = verificationKey,
                decryptionKey = walletConfig?.decryptionKey, // Wallet decryption key from config
            )

        return jarService
            .mergeRequestObject(mergeArgs)
            .mapError { error ->
                // Map OAuth2Error to IdkError
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Failed to merge request object: ${error.message}",
                    throwable = error.exception,
                )
            }.map { it.mergedParameters }
    }

    private fun extractJarContextParams(jwt: String): Map<String, JsonElement> {
        val header = extractJwtHeader(jwt)

        val kid = header["kid"]?.jsonPrimitive?.contentOrNull
        val typ = header["typ"]?.jsonPrimitive?.contentOrNull
        val x5c =
            header["x5c"]?.let { el ->
                when (el) {
                    is JsonArray -> el
                    else -> null
                }
            }

        return buildMap {
            put("__idk_jar_used", JsonPrimitive(true))
            if (!kid.isNullOrBlank()) {
                put("__idk_jar_kid", JsonPrimitive(kid))
            }
            if (!typ.isNullOrBlank()) {
                put("__idk_jar_typ", JsonPrimitive(typ))
            }
            if (x5c != null) {
                put("__idk_jar_x5c", x5c)
            }
        }
    }

    private fun extractJwtHeader(jwt: String): JsonObject {
        val parts = jwt.split(".")
        if (parts.size < 2) {
            return JsonObject(emptyMap())
        }
        return try {
            val headerJson = parts[0].decodeFromBase64Url().decodeToString()
            Json.parseToJsonElement(headerJson).jsonObject
        } catch (expected: Exception) {
            log.debug("Failed to decode JWT header: ${expected.message}")
            JsonObject(emptyMap())
        }
    }

    /**
     * Decode an unverified Request Object payload solely for verification-key selection hints.
     * No authorization parameter returned by this helper is trusted: [JarService] verifies the
     * JWS before its payload is merged into the request.
     */
    private fun extractJwtPayload(jwt: String): JsonObject {
        val parts = jwt.split(".")
        if (parts.size != 3) {
            return JsonObject(emptyMap())
        }
        return try {
            val payloadJson = parts[1].decodeFromBase64Url().decodeToString()
            Json.parseToJsonElement(payloadJson).jsonObject
        } catch (expected: Exception) {
            log.debug("Failed to decode JWT payload hint: ${expected.message}")
            JsonObject(emptyMap())
        }
    }

    private suspend fun resolveClientMetadataFromQuery(queryParameters: io.ktor.http.Parameters): IdkResult<ClientMetadata?, IdkError> {
        val embedded = queryParameters["client_metadata"]?.trim()
        if (!embedded.isNullOrBlank()) {
            return try {
                Ok(Oid4vpJson.wire.decodeFromString(ClientMetadata.serializer(), embedded))
            } catch (expected: Exception) {
                Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Failed to parse client_metadata JSON: ${expected.message}",
                    ),
                )
            }
        }

        val uri = queryParameters["client_metadata_uri"]?.trim()
        if (uri.isNullOrBlank()) {
            return Ok(null)
        }
        if (!uri.startsWith("https://", ignoreCase = true)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "client_metadata_uri must be HTTPS: $uri"))
        }

        return try {
            val httpClient =
                httpClientFactory.createClient(
                    HttpClientOptions(
                        enableContentNegotiation = true,
                    ),
                )
            val response = httpClient.get(uri)
            if (response.status != HttpStatusCode.OK) {
                return Err(IdkError.UNKNOWN_ERROR(message = "Failed to fetch client_metadata from $uri: HTTP ${response.status.value}"))
            }
            val responseBody = response.body<String>()
            Ok(Oid4vpJson.wire.decodeFromString(ClientMetadata.serializer(), responseBody))
        } catch (expected: Exception) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to fetch client_metadata from $uri: ${expected.message}"))
        }
    }

    private suspend fun resolveJarVerificationKey(
        clientId: String?,
        clientIdSchemeHint: String?,
        clientMetadata: ClientMetadata?,
        requestedKid: String?,
        x5cHeader: List<String>?,
    ): IdkResult<KeyInfoType<*>, IdkError> {
        // Per OID4VP 1.0 §5.9.3/§5.10: for prefixed client_ids, the JAR verification key is
        // derived from the client identifier scheme and JOSE header (not client_metadata).
        val effectiveScheme = jarVerificationScheme(clientId, clientIdSchemeHint)
        when (effectiveScheme) {
            ClientIdScheme.DECENTRALIZED_IDENTIFIER -> {
                val vmId =
                    requestedKid?.takeIf { it.isNotBlank() } ?: return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(message = "DID-bound JAR missing kid header"),
                    )
                // OID4VP §5.10.3: kid is a DID URL (verification method id). Strip the
                // fragment and resolve the DID document; selection by vmId is handled by lookup.
                val didOnly = vmId.substringBefore('#')
                val resolved =
                    externalIdentifierService
                        .resolve(
                            ExternalIdentifierDidOpts(identifier = didOnly)
                                .let { it }, // keep call site readable
                        ).getOrElse { err ->
                            return Err(IdkError.fromString(message = err.message.defaultMessage, code = "DID_KID_RESOLUTION_FAILED"))
                        }
                return Ok(resolved.keyInfo)
            }

            ClientIdScheme.X509_SAN_DNS,
            ClientIdScheme.X509_SAN_URI,
            ClientIdScheme.X509_HASH,
            -> {
                val chain =
                    x5cHeader?.takeIf { it.isNotEmpty() } ?: return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(message = "X.509-bound JAR missing x5c header"),
                    )
                val resolved =
                    externalIdentifierService
                        .resolve(ExternalIdentifierX5cOpts(identifier = chain))
                        .getOrElse { err ->
                            return Err(IdkError.fromString(message = err.message.defaultMessage, code = "X5C_RESOLUTION_FAILED"))
                        }
                return Ok(resolved.keyInfo)
            }

            else -> Unit
        }

        if (clientMetadata == null) {
            return Err(
                IdkError.NOT_FOUND_ERROR(
                    resource = "client_metadata",
                    message = "Cannot verify request object (JAR): missing client_metadata/client_metadata_uri",
                ),
            )
        }

        // Embedded JWKS takes precedence over jwks_uri (per general OIDC conventions).
        val jwks = clientMetadata.jwks
        if (jwks != null) {
            val keys = jwks.keys
            val selected =
                when {
                    !requestedKid.isNullOrBlank() -> keys.firstOrNull { it.kid == requestedKid }
                    else -> keys.firstOrNull()
                } ?: return Err(IdkError.NOT_FOUND_ERROR(message = "No suitable JWK found in client_metadata JWKS (kid='$requestedKid')"))

            return Ok(ResolvedKeyInfo.fromKey(selected))
        }

        val jwksUri =
            clientMetadata.jwksUri
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Cannot verify request object (JAR): client_metadata contains neither jwks nor jwks_uri"))

        val resolved =
            externalIdentifierService
                .resolve(
                    ExternalIdentifierJwksUrlOpts(
                        identifier = jwksUri,
                        lookup = AdditionalIdentifierLookup(kid = requestedKid),
                    ),
                ).getOrElse { err ->
                    return Err(IdkError.fromString(message = err.message.defaultMessage, code = "JWKS_URI_RESOLUTION_FAILED"))
                }

        return Ok(resolved.keyInfo)
    }
}

/** RFC 9101 issuer binding uses the complete OAuth client identifier, prefix included. */
internal fun oid4vpJarIssuer(clientId: String?): String? = clientId

/**
 * Resolve only the identity method needed to verify a JAR. A signed payload declaration can
 * select the verification method for ISO 18013-7 requests with a bare client_id, but becomes
 * trusted request data only after the JAR signature has been verified.
 */
internal fun jarVerificationScheme(
    clientId: String?,
    clientIdSchemeHint: String?,
): ClientIdScheme? {
    val prefixedScheme =
        clientId
            ?.let(ClientIdScheme::fromClientId)
            ?.takeUnless { it == ClientIdScheme.PRE_REGISTERED }
    return prefixedScheme ?: ClientIdScheme.fromPrefix(clientIdSchemeHint)
}
