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

// Role-neutral OAuth REST capability. Executable server startup remains in services-oauth2-as-rest.
package com.sphereon.oauth2.server.authorization.impl.http.command.userinfo

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.server.authorization.command.clientauth.ClientCertificateExtractor
import com.sphereon.oauth2.server.authorization.command.userinfo.HandleUserInfoRequestArgs
import com.sphereon.oauth2.server.authorization.command.userinfo.HandleUserInfoRequestCommand
import com.sphereon.oauth2.server.authorization.command.userinfo.UserInfoHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.dpop.DpopNonceManager
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.mapOAuth2ErrorToResponse
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.http.parseFormBody
import com.sphereon.oauth2.server.resource.command.ValidateAccessTokenArgs
import com.sphereon.oauth2.server.resource.command.ValidateAccessTokenCommand
import com.sphereon.oauth2.server.resource.model.ResourceRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/**
 * HTTP shell over [HandleUserInfoRequestCommand] (OIDC Core 1.0 §5.3). Accepts both `GET` and
 * `POST` per the spec via an overridden [supports]. Returns 404 when the `oidc` feature policy
 * is disabled.
 *
 * Access-token validation, including the RFC 8705 §3.2 `cnf.x5t#S256` and RFC 9449 `cnf.jkt`
 * binding checks, scheme/binding mismatch detection, signature/expiry checks, and DPoP proof
 * verification, is delegated to [ValidateAccessTokenCommand] so the binding contract for any
 * resource that consumes a bearer or DPoP-bound access token lives in one place.
 *
 * The HTTP shell still owns:
 *  - Authorization header parsing and scheme detection.
 *  - The RFC 9449 §8 `use_dpop_nonce` retry challenge: rotating the AS nonce, refusing the
 *    Bearer scheme when the AS requires DPoP, and rejecting DPoP proofs whose `nonce` claim
 *    is missing or stale relative to the [DpopNonceManager] window.
 *  - Mapping [com.sphereon.oauth2.server.resource.error.ResourceServerError] codes to the AS's
 *    OAuth2 wire error envelope.
 *  - Adding `DPoP-Nonce` to every DPoP-bearing response.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(UserInfoHttpEndpointCommand.COMMAND_ID)
class UserInfoHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val handleUserInfoRequestCommand: HandleUserInfoRequestCommand,
    private val validateAccessTokenCommand: ValidateAccessTokenCommand,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: OAuth2ServerBaseUrlResolver,
    private val dpopNonceManager: DpopNonceManager,
    private val clientCertificateExtractor: ClientCertificateExtractor,
) : HttpEndpointCommandAdapter(
        id = UserInfoHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = UserInfoHttpEndpointCommand.ENDPOINT,
    ),
    UserInfoHttpEndpointCommand {
    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    override suspend fun supports(args: Any): Boolean =
        if (args is GenericHttpRequest) {
            (args.method.equals("GET", ignoreCase = true) || args.method.equals("POST", ignoreCase = true)) &&
                args.path == endpoint.pathPattern
        } else {
            false
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        if (!configProvider.serverConfig.oidc.isEnabled) {
            return Ok(
                GenericHttpResponse(
                    statusCode = 404,
                    headers = mapOf("Content-Type" to "application/json"),
                    body = json.encodeToString(mapOf("error" to "not_found", "error_description" to "OIDC is not enabled")),
                ),
            )
        }

        val authHeader = request.headers["authorization"] ?: request.headers["Authorization"]
        // RFC 6750 §2.2 — Form-Encoded Body Parameter. POST with
        // Content-Type: application/x-www-form-urlencoded MAY carry the access token in the
        // body as `access_token=<value>` (treated as Bearer). Spec: a single request MUST NOT
        // use more than one method to transmit the token, so reject when both header AND body
        // carry it. Body extraction is best-effort: malformed bodies are reported via the
        // existing missing-token / 401 path so we never accidentally accept a partial parse.
        val bodyAccessToken =
            if (authHeader == null && request.method.equals("POST", ignoreCase = true) && isFormUrlEncoded(request)) {
                parseFormBody(request.body)?.get("access_token")?.firstOrNull()?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        // Header-AND-body would be a per-spec violation. Header-XOR-body is fine.
        if (authHeader != null && request.method.equals("POST", ignoreCase = true) && isFormUrlEncoded(request)) {
            val bodyHasToken = parseFormBody(request.body)?.containsKey("access_token") == true
            if (bodyHasToken) {
                return Ok(
                    oauth2ErrorResponse(
                        400,
                        "invalid_request",
                        "Access token MUST NOT be transmitted via both Authorization header and request body (RFC 6750 §2)",
                        json,
                    ),
                )
            }
        }
        if (authHeader == null && bodyAccessToken == null) {
            return Ok(oauth2ErrorResponse(401, "invalid_token", "Missing access token (Authorization header or POST body)", json))
        }

        val scheme: String
        val accessToken: String
        if (authHeader != null) {
            val parts = authHeader.trim().split(" ", limit = 2)
            if (parts.size != 2) {
                return Ok(oauth2ErrorResponse(401, "invalid_token", "Malformed Authorization header", json))
            }
            scheme = parts[0]
            accessToken = parts[1]
        } else {
            // Body-carried tokens are Bearer per RFC 6750 §2.2 (DPoP requires the proof in a
            // header anyway, so the body channel can't carry a DPoP-bound token meaningfully).
            scheme = "Bearer"
            accessToken = bodyAccessToken!!
        }
        val isDpopScheme = scheme.equals("DPoP", ignoreCase = true)
        val isBearerScheme = scheme.equals("Bearer", ignoreCase = true)
        if (!isDpopScheme && !isBearerScheme) {
            return Ok(oauth2ErrorResponse(401, "invalid_token", "Unsupported Authorization scheme: $scheme", json))
        }

        val nonceRequired = configProvider.serverConfig.dpopNonceRequired
        if (nonceRequired) {
            val challenge = enforceDpopNonceChallenge(request, isDpopScheme)
            if (challenge != null) {
                return Ok(challenge)
            }
        }

        // Build the resource-server view of the request so [ValidateAccessTokenCommand] can run
        // its full RFC 6750 / RFC 9449 / RFC 8705 binding checks. The TLS client cert (when
        // present at the AS edge) is what `cnf.x5t#S256` matches against.
        val clientCertificateDer =
            clientCertificateExtractor
                .extractCertificate(request)
                .getOrElse { error ->
                    return Ok(
                        oauth2ErrorResponse(
                            statusCode = 401,
                            error = "invalid_token",
                            errorDescription = error.message.defaultMessage ?: "Invalid client certificate",
                            jsonFormat = json,
                        ),
                    )
                }
        // When the token rode in the body (RFC 6750 §2.2), synthesize an `Authorization: Bearer
        // <token>` header into the validator's view so the existing scheme/binding machinery
        // doesn't need to know about the body channel.
        val resourceRequestHeaders =
            if (authHeader == null && bodyAccessToken != null) {
                request.headers + ("Authorization" to "Bearer $bodyAccessToken")
            } else {
                request.headers
            }
        // DPoP `htu` (RFC 9449 §4.2) must match the URL the wallet computed from the AS's
        // advertised discovery metadata. Discovery emits `${baseUrl}/userinfo` via the resolver,
        // so reconstruction here uses the same resolver + fixed `/userinfo` suffix — picks up
        // the EDK tenant-public-endpoint overlay's binding-derived base URL automatically.
        val resourceUrl = "${baseUrlResolver.resolveBaseUrl(request, configProvider)}/userinfo"
        val resourceRequest =
            ResourceRequest(
                method = request.method,
                url = resourceUrl,
                headers = resourceRequestHeaders,
                clientCertificateDer = clientCertificateDer,
            )

        val validateResult =
            validateAccessTokenCommand.execute(
                ValidateAccessTokenArgs(request = resourceRequest),
            )
        if (validateResult.isErr) {
            val errorResponse = mapValidateErrorToResponse(validateResult.error)
            return Ok(if (isDpopScheme) errorResponse.withDpopNonce(dpopNonceManager.currentNonce()) else errorResponse)
        }

        val handleResult = handleUserInfoRequestCommand.execute(HandleUserInfoRequestArgs(accessToken = accessToken))
        val baseResponse =
            if (handleResult.isOk) {
                val responseBody = json.encodeToString(handleResult.value)
                GenericHttpResponse(
                    statusCode = 200,
                    headers = mapOf("Content-Type" to "application/json"),
                    body = responseBody,
                )
            } else {
                mapOAuth2ErrorToResponse(handleResult.error, json, execution)
            }
        // RFC 9449 §8: emit the current nonce on every DPoP-bearing response so clients can
        // rotate proactively.
        val finalResponse = if (isDpopScheme) baseResponse.withDpopNonce(dpopNonceManager.currentNonce()) else baseResponse
        return Ok(finalResponse)
    }

    /**
     * RFC 9449 §8: when the AS demands a DPoP nonce on this request, return the
     * `use_dpop_nonce` 401 with a freshly rotated nonce so the client can retry. The Bearer
     * scheme cannot satisfy the challenge: surface the same 401 so the client switches to
     * DPoP. For DPoP requests the AS only needs to peek at the proof's `nonce` claim, which
     * lives in the JWT payload and is base64url-decodable without verifying the signature
     * (the signature check happens in [ValidateAccessTokenCommand]). Returns `null` when the
     * request is allowed to proceed to validation.
     */
    private suspend fun enforceDpopNonceChallenge(
        request: GenericHttpRequest,
        isDpopScheme: Boolean,
    ): GenericHttpResponse? {
        if (!isDpopScheme) {
            val freshNonce = dpopNonceManager.rotate()
            return oauth2ErrorResponse(
                401,
                "use_dpop_nonce",
                "Resource requires DPoP with a server nonce",
                json,
            ).withDpopNonce(freshNonce)
        }
        val dpopProof = request.headers["DPoP"] ?: request.headers["dpop"]
        val proofNonce = dpopProof?.let { extractProofNonce(it) }
        if (proofNonce == null || !dpopNonceManager.isValid(proofNonce)) {
            val freshNonce = dpopNonceManager.rotate()
            return oauth2ErrorResponse(
                401,
                "use_dpop_nonce",
                "DPoP proof must include a valid nonce",
                json,
            ).withDpopNonce(freshNonce)
        }
        return null
    }

    /**
     * Decode the DPoP proof JWT payload and read the `nonce` claim. Best-effort: a malformed
     * proof yields `null` and the validator flow downstream returns the canonical
     * `invalid_dpop_proof` rejection. No signature verification happens here.
     */
    private fun extractProofNonce(dpopProof: String): String? {
        val segments = dpopProof.split(".")
        if (segments.size != 3) {
            return null
        }
        return runCatching {
            JwsUtils
                .decodeBase64UrlToJson(segments[1])["nonce"]
                ?.jsonPrimitive
                ?.content
        }.getOrNull()
    }

    /**
     * Map a [ValidateAccessTokenCommand] failure to the AS's wire-shape OAuth2 error response.
     * RFC 6750 §3 + RFC 9449 §7.1 say the `error` parameter on `WWW-Authenticate` for a
     * resource endpoint is `invalid_token` for bearer-token failures and `invalid_dpop_proof`
     * for DPoP-specific failures.
     */
    private fun mapValidateErrorToResponse(error: IdkError): GenericHttpResponse {
        val description = error.message.defaultMessage ?: error.code ?: "Invalid access token"
        return when (error.code) {
            "invalid_dpop_proof", "dpop_binding_mismatch" -> oauth2ErrorResponse(401, "invalid_dpop_proof", description, json)
            "missing_authorization", "malformed_authorization", "unsupported_scheme" -> oauth2ErrorResponse(401, "invalid_token", description, json)
            "insufficient_scope" -> oauth2ErrorResponse(403, "insufficient_scope", description, json)
            "audience_mismatch" -> oauth2ErrorResponse(401, "invalid_token", description, json)
            else -> oauth2ErrorResponse(401, "invalid_token", description, json)
        }
    }

    private fun GenericHttpResponse.withDpopNonce(nonce: String): GenericHttpResponse = copy(headers = headers + ("DPoP-Nonce" to nonce))

    /**
     * Match `Content-Type: application/x-www-form-urlencoded` (with optional `; charset=...`)
     * case-insensitively. Lookup tries the conventional capitalisation first and falls back
     * to the lowercase variant some HTTP layers emit.
     */
    private fun isFormUrlEncoded(request: GenericHttpRequest): Boolean {
        val contentType = request.headers["Content-Type"] ?: request.headers["content-type"] ?: return false
        return contentType.substringBefore(';').trim().equals("application/x-www-form-urlencoded", ignoreCase = true)
    }
}
