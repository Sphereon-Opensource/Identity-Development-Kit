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

package com.sphereon.oauth2.server.resource.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.oauth2.server.resource.cache.TokenCache
import com.sphereon.oauth2.server.resource.command.ValidateAccessTokenArgs
import com.sphereon.oauth2.server.resource.command.ValidateAccessTokenCommand
import com.sphereon.oauth2.server.resource.command.VerifyDpopProofArgs
import com.sphereon.oauth2.server.resource.command.VerifyDpopProofCommand
import com.sphereon.oauth2.server.resource.command.VerifyJwtArgs
import com.sphereon.oauth2.server.resource.command.VerifyJwtCommand
import com.sphereon.oauth2.server.resource.error.ResourceServerError
import com.sphereon.oauth2.server.resource.model.AuthenticationScheme
import com.sphereon.oauth2.server.resource.model.ResourceRequest
import com.sphereon.oauth2.server.resource.model.VerifiedResourceRequest
import kotlinx.serialization.json.jsonPrimitive
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import com.sphereon.di.session.SessionScope
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of ValidateAccessTokenCommand
 *
 * Main orchestrator for resource server token validation.
 *
 * **Flow**:
 * 1. Extract and parse Authorization header (Bearer or DPoP)
 * 2. Check token cache for previous validation
 * 3. Validate token (JWT verification)
 * 4. Validate DPoP proof and binding (if applicable)
 * 5. Validate scope and audience claims
 * 6. Cache validated token
 * 7. Return VerifiedResourceRequest
 *
 * **Authentication Schemes**:
 * - Bearer (RFC 6750): `Authorization: Bearer <token>`
 * - DPoP (RFC 9449): `Authorization: DPoP <token>` + `DPoP: <proof>`
 *
 * **Note**: Currently only implements JWT verification. Token introspection
 * is stubbed out pending configuration implementation.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ValidateAccessTokenCommandImpl", exact = true)
class ValidateAccessTokenCommandImpl(
    execution: SessionExecution,
    private val verifyJwtCommand: VerifyJwtCommand,
    private val verifyDpopProofCommand: VerifyDpopProofCommand,
    private val tokenCache: TokenCache
) : TypedServiceCommandAdapter<ValidateAccessTokenArgs, VerifiedResourceRequest>(
    commandId = ValidateAccessTokenCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ValidateAccessTokenArgs>(),
    outputTypeToken = typeToken<VerifiedResourceRequest>(),
), ValidateAccessTokenCommand {

    companion object {
        private const val AUTHORIZATION_HEADER = "authorization"
        private const val DPOP_HEADER = "dpop"
        private const val BEARER_PREFIX = "bearer "
        private const val DPOP_PREFIX = "dpop "
    }

    override val commandId: String get() = ValidateAccessTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ValidateAccessTokenArgs

    override suspend fun doExecute(
        args: ValidateAccessTokenArgs,
        applyDuring: (ValidateAccessTokenArgs) -> ValidateAccessTokenArgs
    ): IdkResult<VerifiedResourceRequest, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.request, applied.requiredScope, applied.requiredAudience).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        request: ResourceRequest,
        requiredScope: String?,
        requiredAudience: String?
    ): IdkResult<VerifiedResourceRequest, ResourceServerError> {
        // 1. Extract Authorization header (case-insensitive)
        val authHeader = request.headers.entries
            .firstOrNull { it.key.lowercase() == AUTHORIZATION_HEADER }
            ?.value
            ?: return Err(ResourceServerError.MissingAuthorizationHeader(
                allowedSchemes = listOf(AuthenticationScheme.BEARER, AuthenticationScheme.DPOP)
            ))

        // 2. Determine authentication scheme and extract token
        val (scheme, token) = when {
            authHeader.lowercase().startsWith(BEARER_PREFIX) -> {
                AuthenticationScheme.BEARER to authHeader.substring(BEARER_PREFIX.length).trim()
            }
            authHeader.lowercase().startsWith(DPOP_PREFIX) -> {
                AuthenticationScheme.DPOP to authHeader.substring(DPOP_PREFIX.length).trim()
            }
            else -> {
                return Err(ResourceServerError.MalformedAuthorizationHeader(
                    header = authHeader
                ))
            }
        }

        // 3. Check token cache
        val cachedPayload = tokenCache.get(token)
        if (cachedPayload != null) {
            // DPoP proof is verified in validateAndBuildResult() on every request,
            // even when the token itself is cached (DPoP is bound to HTTP method + URL)
            return validateAndBuildResult(
                tokenPayload = cachedPayload,
                scheme = scheme,
                token = token,
                request = request,
                requiredScope = requiredScope,
                requiredAudience = requiredAudience
            )
        }

        // 4. Extract authorization server from token (for JWT, we'll parse it to get iss)
        // For now, we need the authorization server to be configured or extracted from metadata
        // This is a limitation of the current implementation - needs configuration
        val authorizationServer = extractAuthorizationServerFromToken(token)
            ?: return Err(ResourceServerError.InvalidToken(
                "Could not determine authorization server from token - configuration needed"
            ))

        // 5. Verify token (try JWT first)
        val tokenPayload = verifyJwtCommand.execute(
            VerifyJwtArgs(
                jwt = token,
                authorizationServer = authorizationServer,
                expectedAudience = requiredAudience
            )
        )

        if (tokenPayload.isErr) {
            // JWT verification failed - could try introspection fallback here
            return Err(ResourceServerError.InvalidToken(
                reason = tokenPayload.error.message.defaultMessage ?: tokenPayload.error.code ?: "JWT verification failed"
            ))
        }

        val payload = tokenPayload.value

        // 6. Cache the validated token
        tokenCache.put(token, payload, payload.exp)

        // 7. Validate and build result
        return validateAndBuildResult(
            tokenPayload = payload,
            scheme = scheme,
            token = token,
            request = request,
            requiredScope = requiredScope,
            requiredAudience = requiredAudience
        )
    }

    /**
     * Validates scope/audience and builds VerifiedResourceRequest
     */
    private suspend fun validateAndBuildResult(
        tokenPayload: com.sphereon.oauth2.server.resource.model.TokenPayload,
        scheme: AuthenticationScheme,
        token: String,
        request: ResourceRequest,
        requiredScope: String?,
        requiredAudience: String?
    ): IdkResult<VerifiedResourceRequest, ResourceServerError> {
        // Validate required scope if specified
        if (requiredScope != null) {
            val tokenScopes = tokenPayload.scope?.split(" ", ",") ?: emptyList()
            val requiredScopes = requiredScope.split(" ", ",")

            val hasAllScopes = requiredScopes.all { required ->
                tokenScopes.any { it.trim() == required.trim() }
            }

            if (!hasAllScopes) {
                return Err(ResourceServerError.InsufficientScope(
                    required = requiredScope,
                    actual = tokenPayload.scope
                ))
            }
        }

        // Validate DPoP if scheme is DPoP
        val dpopVerification = if (scheme == AuthenticationScheme.DPOP) {
            // Extract DPoP header
            val dpopProof = request.headers.entries
                .firstOrNull { it.key.lowercase() == DPOP_HEADER }
                ?.value
                ?: return Err(ResourceServerError.InvalidDpopProof("Missing DPoP header for DPoP-authenticated request"))

            // Verify DPoP proof
            val dpopResult = verifyDpopProofCommand.execute(
                VerifyDpopProofArgs(
                    dpopProof = dpopProof,
                    httpMethod = request.method,
                    httpUrl = normalizeUrl(request.url),
                    expectedJkt = tokenPayload.dpopJkt
                )
            )

            if (dpopResult.isErr) {
                return Err(ResourceServerError.InvalidDpopProof(
                    reason = dpopResult.error.message.defaultMessage ?: dpopResult.error.code ?: "DPoP proof verification failed"
                ))
            }

            dpopResult.value
        } else {
            // Bearer token - check that it's not DPoP-bound
            if (tokenPayload.dpopJkt != null) {
                return Err(ResourceServerError.InvalidToken(
                    "Token is DPoP-bound but presented with Bearer scheme"
                ))
            }
            null
        }

        // Build and return VerifiedResourceRequest
        return Ok(VerifiedResourceRequest(
            tokenPayload = tokenPayload,
            scheme = scheme,
            accessToken = token,
            authorizationServer = tokenPayload.iss,
            dpop = dpopVerification
        ))
    }

    /**
     * Extracts authorization server from token
     * For JWT tokens, we can parse the iss claim without verification
     */
    private fun extractAuthorizationServerFromToken(token: String): String? {
        return try {
            // Simple JWT parsing to extract iss claim
            val parts = token.split(".")
            if (parts.size != 3) return null

            // Decode payload using JwsUtils
            val payloadJson = JwsUtils.decodeBase64UrlToJson(parts[1])
            payloadJson["iss"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Normalizes URL by removing query parameters and fragment
     */
    private fun normalizeUrl(url: String): String {
        val queryStart = url.indexOf('?')
        val fragmentStart = url.indexOf('#')

        val cutPosition = when {
            queryStart != -1 && fragmentStart != -1 -> minOf(queryStart, fragmentStart)
            queryStart != -1 -> queryStart
            fragmentStart != -1 -> fragmentStart
            else -> url.length
        }

        return url.substring(0, cutPosition)
    }
}
