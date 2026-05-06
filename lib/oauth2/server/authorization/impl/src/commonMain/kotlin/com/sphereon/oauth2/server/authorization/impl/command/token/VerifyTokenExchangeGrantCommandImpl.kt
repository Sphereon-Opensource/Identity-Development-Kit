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
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.ActorClaim
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenTypeIdentifier
import com.sphereon.oauth2.server.authorization.command.VerifiedTokenExchangeGrant
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.policy.TokenExchangePolicy
import com.sphereon.oauth2.server.authorization.policy.TokenExchangePolicyRequest
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

private const val JWT_PART_COUNT = 3

/**
 * Implementation of VerifyTokenExchangeGrantCommand
 *
 * Verifies token exchange grant requests according to RFC 8693.
 *
 * Verification steps:
 * 1. Validate required fields (subject_token, subject_token_type)
 * 2. Validate actor_token_type required when actor_token present
 * 3. Verify client is authorized for TOKEN_EXCHANGE grant
 * 4. Validate subject and actor tokens by type
 * 5. Invoke TokenExchangePolicy for authorization decision
 * 6. Build VerifiedTokenExchangeGrant (including ActorClaim for delegation)
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyTokenExchangeGrantCommandImpl", exact = true)
class VerifyTokenExchangeGrantCommandImpl(
    execution: SessionExecution,
    private val clientRegistry: ClientRegistry,
    private val tokenExchangePolicy: TokenExchangePolicy,
    private val jwtService: JwtService,
) : TypedServiceCommandAdapter<VerifyTokenExchangeGrantArgs, VerifiedTokenExchangeGrant, IdkError>(
        commandId = VerifyTokenExchangeGrantCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifyTokenExchangeGrantArgs>(),
        outputTypeToken = typeToken<VerifiedTokenExchangeGrant>(),
    ),
    VerifyTokenExchangeGrantCommand {
    override val commandId: String get() = VerifyTokenExchangeGrantCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyTokenExchangeGrantArgs

    override suspend fun doExecute(
        args: VerifyTokenExchangeGrantArgs,
        applyDuring: (VerifyTokenExchangeGrantArgs) -> VerifyTokenExchangeGrantArgs,
    ): IdkResult<VerifiedTokenExchangeGrant, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(args: VerifyTokenExchangeGrantArgs): IdkResult<VerifiedTokenExchangeGrant, AuthorizationServerError> {
        // 1. Validate required fields
        if (args.subjectToken.isBlank()) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Missing required parameter: subject_token",
                ),
            )
        }
        if (args.subjectTokenType.isBlank()) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Missing required parameter: subject_token_type",
                ),
            )
        }

        // 2. Validate actor_token_type required when actor_token present
        if (args.actorToken != null && args.actorTokenType.isNullOrBlank()) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "actor_token_type is required when actor_token is present",
                ),
            )
        }

        // 3. Verify client is authorized for TOKEN_EXCHANGE grant
        val client =
            clientRegistry.getClient(args.clientId).getOrElse { error ->
                return Err(
                    AuthorizationServerError.ServerError(
                        details = "Failed to retrieve client registration: $error",
                    ),
                )
            }

        if (client == null) {
            return Err(
                AuthorizationServerError.InvalidClient(
                    details = "Client not found",
                ),
            )
        }

        if (GrantType.TOKEN_EXCHANGE !in client.grantTypes) {
            return Err(
                AuthorizationServerError.UnauthorizedClient(
                    clientId = args.clientId,
                ),
            )
        }

        // 4. Validate subject token by type
        val subjectResult =
            validateToken(args.subjectToken, args.subjectTokenType, "subject")
                .getOrElse { error -> return Err(error) }

        // 5. Validate actor token (if present)
        val actorResult =
            if (args.actorToken != null && args.actorTokenType != null) {
                validateToken(args.actorToken!!, args.actorTokenType!!, "actor")
                    .getOrElse { error -> return Err(error) }
            } else {
                null
            }

        // 6. Invoke TokenExchangePolicy
        val policyRequest =
            TokenExchangePolicyRequest(
                clientId = args.clientId,
                subjectTokenClaims = subjectResult.claims,
                subjectTokenType = args.subjectTokenType,
                subjectTokenVerified = subjectResult.verified,
                actorTokenClaims = actorResult?.claims,
                actorTokenType = args.actorTokenType,
                actorTokenVerified = actorResult?.verified,
                requestedResources = args.resources,
                requestedAudiences = args.audiences,
                requestedScope = args.scope,
                requestedTokenType = args.requestedTokenType,
            )

        val policyDecision =
            tokenExchangePolicy
                .evaluate(policyRequest)
                .getOrElse { error -> return Err(error) }

        if (!policyDecision.allowed) {
            return Err(
                AuthorizationServerError.AccessDenied(
                    reason = policyDecision.denyReason ?: "Token exchange denied by policy",
                ),
            )
        }

        // 7. Build result
        val subject = subjectResult.claims["sub"] as? String ?: args.clientId
        val actorSubject = actorResult?.claims?.get("sub") as? String

        // Build ActorClaim for delegation
        val actorClaim =
            if (policyDecision.isDelegation && actorSubject != null) {
                ActorClaim(sub = actorSubject)
            } else {
                null
            }

        // RFC 9449 §10.1: surface the subject token's `cnf.jkt` (when bound) so the orchestrator
        // can enforce that the exchanged-token DPoP proof comes from the same key.
        val subjectCnfJkt = extractCnfJkt(subjectResult.claims)

        return Ok(
            VerifiedTokenExchangeGrant(
                subject = subject,
                clientId = args.clientId,
                scope = policyDecision.grantedScope,
                audience = policyDecision.grantedAudience,
                resource = args.resources,
                issuedTokenType = policyDecision.issuedTokenType,
                isDelegation = policyDecision.isDelegation,
                actorSubject = actorSubject,
                actorClaim = actorClaim,
                additionalClaims = policyDecision.additionalClaims,
                subjectCnfJkt = subjectCnfJkt,
            ),
        )
    }

    /**
     * Read `cnf.jkt` from a parsed JWT claim map. The `cnf` claim is decoded by
     * [jsonElementToAny] as `Map<String, Any>`, so the lookup is a nested map read. Returns
     * `null` when the subject token is not DPoP-bound (no `cnf.jkt` present) or when the value
     * is not a string.
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractCnfJkt(claims: Map<String, Any>): String? {
        val cnf = claims["cnf"] as? Map<String, Any> ?: return null
        return cnf["jkt"] as? String
    }

    /**
     * Result of token validation: extracted claims + whether the signature was verified.
     */
    private data class TokenValidationResult(
        val claims: Map<String, Any>,
        val verified: Boolean,
    )

    /**
     * Validate a token based on its declared type.
     *
     * For JWT-based token types, uses JwtService for signature verification
     * and JwsUtils for claims extraction.
     * For SAML types, returns an error (not supported in this phase).
     */
    private suspend fun validateToken(
        token: String,
        tokenType: String,
        tokenRole: String,
    ): IdkResult<TokenValidationResult, AuthorizationServerError> =
        when (tokenType) {
            TokenTypeIdentifier.ACCESS_TOKEN,
            TokenTypeIdentifier.ID_TOKEN,
            TokenTypeIdentifier.JWT,
            -> {
                validateJwtToken(token, tokenRole)
            }

            TokenTypeIdentifier.REFRESH_TOKEN -> {
                // Opaque refresh token — return minimal claims (not signature-verified)
                // In a full implementation, this would look up the token in TokenStorage
                Ok(
                    TokenValidationResult(
                        claims = mapOf("token" to token, "token_type" to tokenType),
                        verified = false,
                    ),
                )
            }

            TokenTypeIdentifier.SAML1,
            TokenTypeIdentifier.SAML2,
            -> {
                Err(
                    AuthorizationServerError.InvalidRequest(
                        details = "SAML token types are not supported for $tokenRole token",
                    ),
                )
            }

            else -> {
                Err(
                    AuthorizationServerError.InvalidRequest(
                        details = "Unsupported $tokenRole token type: $tokenType",
                    ),
                )
            }
        }

    /**
     * Validate a JWT token using JwtService for signature verification
     * and JwsUtils for payload decoding.
     *
     * Returns extracted claims + verification status. Structural JWT issues
     * (wrong format, unparseable payload) are rejected. Signature verification
     * failures are reported to the policy via the [TokenValidationResult.verified] flag.
     */
    private suspend fun validateJwtToken(
        token: String,
        tokenRole: String,
    ): IdkResult<TokenValidationResult, AuthorizationServerError> {
        // Basic structure check (3 dot-separated parts)
        val parts = token.split(".")
        if (parts.size != JWT_PART_COUNT) {
            return Err(
                AuthorizationServerError.InvalidGrant(
                    details = "Invalid JWT format for $tokenRole token",
                ),
            )
        }

        // Attempt JWS signature verification using JwtService (key resolved from JWT header: kid, x5c, jwk).
        // Verification status is passed to the TokenExchangePolicy which decides whether
        // to accept unverified tokens (e.g. external IdP tokens without local signing keys).
        val verifyResult = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(token)))
        val verified = verifyResult.isOk && verifyResult.value.isValid

        // Decode payload claims using JwsUtils (uses com.sphereon.core.api.Encoding internally)
        val claims =
            try {
                val payloadJson = JwsUtils.decodeBase64UrlToJson(parts[1])
                payloadJson.entries.associate { (key, value) ->
                    key to jsonElementToAny(value)
                }
            } catch (_: Exception) {
                return Err(
                    AuthorizationServerError.InvalidGrant(
                        details = "Failed to decode $tokenRole token JWT payload",
                    ),
                )
            }

        return Ok(TokenValidationResult(claims = claims, verified = verified))
    }

    private fun jsonElementToAny(element: JsonElement): Any =
        when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.content == "true" -> true
                    element.content == "false" -> false
                    element.content.contains('.') -> element.content.toDoubleOrNull() ?: element.content
                    else -> element.content.toLongOrNull() ?: element.content
                }
            }

            is JsonArray -> {
                element.map { jsonElementToAny(it) }
            }

            is JsonObject -> {
                element.entries.associate { (k, v) -> k to jsonElementToAny(v) }
            }
        }
}
