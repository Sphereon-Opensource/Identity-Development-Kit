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
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.ActorClaim
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenTypeIdentifier
import com.sphereon.oauth2.server.authorization.command.VerifiedTokenExchangeGrant
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthorization
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.command.clientauth.toVerifiedClientAuthorization
import com.sphereon.oauth2.server.authorization.policy.TokenExchangePolicy
import com.sphereon.oauth2.server.authorization.policy.TokenExchangePolicyRequest
import com.sphereon.oauth2.server.authorization.signing.AsSigningKeyPublicJwkResolver
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

private const val JWT_PART_COUNT = 3
private const val NOT_BEFORE_CLOCK_SKEW_SECONDS = 1L
private const val CLAIM_ISSUER = "iss"
private const val CLAIM_SUBJECT = "sub"
private const val CLAIM_AUDIENCE = "aud"
private const val CLAIM_EXPIRATION = "exp"
private const val CLAIM_NOT_BEFORE = "nbf"
private const val CLAIM_CLIENT_ID = "client_id"
private const val CLAIM_AUTHORIZED_PARTY = "azp"
private const val CLAIM_EMAIL = "email"

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
    private val signingKeyStore: SigningKeyStore,
    private val serversConfigProvider: OAuth2ServersConfigProvider,
    private val signingKeyPublicJwkResolver: AsSigningKeyPublicJwkResolver? = null,
) : TypedServiceCommandAdapter<VerifyTokenExchangeGrantArgs, VerifiedTokenExchangeGrant, IdkError>(
        commandId = VerifyTokenExchangeGrantCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifyTokenExchangeGrantArgs>(),
        outputTypeToken = typeToken<VerifiedTokenExchangeGrant>(),
    ),
    VerifyTokenExchangeGrantCommand {
    private val sessionExecution = execution

    override val commandId: String get() = VerifyTokenExchangeGrantCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyTokenExchangeGrantArgs

    override suspend fun doExecute(
        args: VerifyTokenExchangeGrantArgs,
        applyDuring: (VerifyTokenExchangeGrantArgs) -> VerifyTokenExchangeGrantArgs,
    ): IdkResult<VerifiedTokenExchangeGrant, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied, null).mapError { IdkError.fromDTO(it) }
    }

    internal suspend fun verifyWithTrustedClientAuthorization(
        args: VerifyTokenExchangeGrantArgs,
        clientAuthorization: VerifiedClientAuthorization,
    ): IdkResult<VerifiedTokenExchangeGrant, IdkError> =
        executeInternal(args, clientAuthorization).mapError { IdkError.fromDTO(it) }

    private suspend fun executeInternal(
        args: VerifyTokenExchangeGrantArgs,
        clientAuthorization: VerifiedClientAuthorization?,
    ): IdkResult<VerifiedTokenExchangeGrant, AuthorizationServerError> {
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
        if (clientAuthorization != null && clientAuthorization.clientId != args.clientId) {
            return Err(AuthorizationServerError.InvalidClient(details = "Authenticated client does not match requested client"))
        }
        val client =
            clientAuthorization
                ?: clientRegistry
                    .getClient(args.clientId)
                    .getOrElse { error ->
                        return Err(
                            AuthorizationServerError.ServerError(
                                details = "Failed to retrieve client registration: $error",
                            ),
                        )
                    }?.toVerifiedClientAuthorization()

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
            validateToken(
                token = args.subjectToken,
                tokenType = args.subjectTokenType,
                tokenRole = "subject",
                exchangingClient = client,
            )
                .getOrElse { error -> return Err(error) }

        // 5. Validate actor token (if present)
        val actorResult =
            if (args.actorToken != null && args.actorTokenType != null) {
                validateToken(
                    token = args.actorToken!!,
                    tokenType = args.actorTokenType!!,
                    tokenRole = "actor",
                    exchangingClient = null,
                )
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
                authTime = (subjectResult.claims["auth_time"] as? Number)?.toLong(),
                acr = subjectResult.claims["acr"] as? String,
                amr = (subjectResult.claims["amr"] as? List<*>)?.filterIsInstance<String>(),
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
        exchangingClient: VerifiedClientAuthorization?,
    ): IdkResult<TokenValidationResult, AuthorizationServerError> =
        when (tokenType) {
            TokenTypeIdentifier.ACCESS_TOKEN,
            TokenTypeIdentifier.ID_TOKEN,
            TokenTypeIdentifier.JWT,
            -> {
                validateJwtToken(token, tokenRole, exchangingClient)
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
     * (wrong format, unparseable payload) are rejected. Locally-issued JWTs are pinned to this
     * AS's issuer and signing-key registry and fail closed here; genuinely external signature
     * failures are reported to the policy via the [TokenValidationResult.verified] flag.
     */
    private suspend fun validateJwtToken(
        token: String,
        tokenRole: String,
        exchangingClient: VerifiedClientAuthorization?,
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

        val expectedIssuer = configuredIssuer().getOrElse { return Err(it) }
        val tokenIssuer = claims.strictStringClaim(CLAIM_ISSUER)
        val kid = jwtHeaderKid(token)
        val localSigningKey =
            kid?.let { signingKeyStore.findByKid(sessionExecution.tenantId, it).getOrNull() }

        // A token that claims this AS must never escape to the generic JOSE resolver. Otherwise
        // an attacker could present an embedded key under an unknown kid and turn a local issuer
        // claim into a self-selected trust root.
        if (tokenIssuer == expectedIssuer && localSigningKey == null) {
            return invalidGrant("$tokenRole token uses an unknown local signing key")
        }

        val verified =
            if (localSigningKey != null) {
                if (tokenIssuer != expectedIssuer) {
                    return invalidGrant("$tokenRole token issuer does not match this authorization server")
                }
                if (localSigningKey.state == OAuth2SigningKeyState.DISABLED) {
                    return invalidGrant("$tokenRole token uses a disabled local signing key")
                }
                val resolver = signingKeyPublicJwkResolver
                    ?: return invalidGrant("$tokenRole token local signing key resolver is unavailable")
                val publicJwk = resolver.resolve(localSigningKey)
                    ?: return invalidGrant("$tokenRole token local signing key could not be resolved")
                val trustedJwks =
                    buildJsonObject {
                        put(
                            "keys",
                            JsonArray(listOf(Json.encodeToJsonElement(Jwk.serializer(), publicJwk))),
                        )
                    }
                val verifyResult =
                    jwtService.verifyJws(
                        VerifyJwsArgs(
                            jws = JwsCompact(token),
                            trustedJwks = trustedJwks,
                        ),
                    )
                if (verifyResult.isErr || !verifyResult.value.isValid) {
                    return invalidGrant("$tokenRole token has an invalid local signature")
                }
                validateVerifiedJwtTimeClaims(claims, tokenRole)
                    ?.let { return Err(it) }
                if (exchangingClient != null) {
                    validateLocalSubjectClaims(claims, exchangingClient)
                        .getOrElse { return Err(it) }
                }
                true
            } else {
                // Genuinely external issuers retain the generic verification and policy path.
                // Once the signature verifies, JWT time claims are still enforced here rather
                // than trusting every TokenExchangePolicy implementation to repeat them.
                val verifyResult = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(token)))
                val isVerified = verifyResult.isOk && verifyResult.value.isValid
                if (isVerified) {
                    validateVerifiedJwtTimeClaims(claims, tokenRole)
                        ?.let { return Err(it) }
                }
                isVerified
            }

        return Ok(TokenValidationResult(claims = claims, verified = verified))
    }

    private fun configuredIssuer(): IdkResult<String, AuthorizationServerError> =
        runCatching {
            val config = serversConfigProvider.getConfig()
            serversConfigProvider
                .resolveIssuer(config.defaultServer, sessionExecution.tenantId)
                .trim()
                .takeIf(String::isNotEmpty)
        }.fold(
            onSuccess = { issuer ->
                if (issuer != null) {
                    Ok(issuer)
                } else {
                    Err(
                        AuthorizationServerError.ServerError(
                            details = "Authorization server issuer policy is unavailable",
                        ),
                    )
                }
            },
            onFailure = {
                Err(
                    AuthorizationServerError.ServerError(
                        details = "Authorization server issuer policy is unavailable",
                    ),
                )
            },
        )

    private fun validateVerifiedJwtTimeClaims(
        claims: Map<String, Any>,
        tokenRole: String,
    ): AuthorizationServerError.InvalidGrant? {
        val now = Clock.System.now().epochSeconds
        val expiresAt = claims.numericDateClaim(CLAIM_EXPIRATION)
            ?: return AuthorizationServerError.InvalidGrant(
                details = "$tokenRole token has no valid exp claim",
            )
        if (expiresAt <= now) {
            return AuthorizationServerError.InvalidGrant(details = "$tokenRole token is expired")
        }
        if (CLAIM_NOT_BEFORE in claims) {
            val notBefore = claims.numericDateClaim(CLAIM_NOT_BEFORE)
                ?: return AuthorizationServerError.InvalidGrant(
                    details = "$tokenRole token has an invalid nbf claim",
                )
            if (notBefore > now + NOT_BEFORE_CLOCK_SKEW_SECONDS) {
                return AuthorizationServerError.InvalidGrant(details = "$tokenRole token is not yet valid")
            }
        }
        return null
    }

    private suspend fun validateLocalSubjectClaims(
        claims: Map<String, Any>,
        exchangingClient: VerifiedClientAuthorization,
    ): IdkResult<Unit, AuthorizationServerError> {
        val issuingClientId = claims.strictStringClaim(CLAIM_CLIENT_ID)
            ?: return invalidGrant("subject token has no valid client_id claim")
        val issuingClient =
            if (issuingClientId == exchangingClient.clientId) {
                exchangingClient
            } else {
                clientRegistry
                    .getClient(issuingClientId)
                    .getOrElse {
                        return Err(
                            AuthorizationServerError.ServerError(
                                details = "Failed to retrieve the subject token client registration",
                            ),
                        )
                    }?.toVerifiedClientAuthorization()
                    ?: return invalidGrant("subject token client is not registered")
            }

        val audiences = claims.stringClaimValues(CLAIM_AUDIENCE)
            ?.takeIf { it.isNotEmpty() }
            ?: return invalidGrant("subject token has no valid aud claim")
        val allowedAudiences =
            buildSet {
                issuingClient.defaultAccessTokenAudience
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let(::add)
                issuingClient.allowedAccessTokenAudiences
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .forEach(::add)
            }
        if (allowedAudiences.isEmpty() || audiences.any { it !in allowedAudiences }) {
            return invalidGrant("subject token audience is not authorized for its client")
        }

        val subject = claims.strictStringClaim(CLAIM_SUBJECT)
        val emailPresent = claims[CLAIM_EMAIL] != null
        val isWorkload = subject != null && subject == issuingClientId && !emailPresent
        if (isWorkload) {
            val authorizedParty = claims.strictStringClaim(CLAIM_AUTHORIZED_PARTY)
            if (
                authorizedParty == null ||
                authorizedParty != issuingClientId ||
                exchangingClient.clientId != issuingClientId
            ) {
                return invalidGrant("workload subject token is not bound to the exchanging client")
            }
        }

        return Ok(Unit)
    }

    private fun invalidGrant(details: String): IdkResult<Nothing, AuthorizationServerError> =
        Err(AuthorizationServerError.InvalidGrant(details = details))

    private fun Map<String, Any>.strictStringClaim(name: String): String? =
        (this[name] as? String)?.trim()?.takeIf(String::isNotEmpty)

    private fun Map<String, Any>.numericDateClaim(name: String): Long? =
        (this[name] as? Number)?.toLong()

    private fun Map<String, Any>.stringClaimValues(name: String): List<String>? =
        when (val value = this[name]) {
            is String -> listOf(value.trim()).filter(String::isNotEmpty)
            is List<*> ->
                value
                    .mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) }
                    .takeIf { it.size == value.size }
            else -> null
        }

    private fun jwtHeaderKid(token: String): String? =
        runCatching {
            val compactHeader = token.substringBefore('.', missingDelimiterValue = "")
            JwsUtils.decodeBase64UrlToJson(compactHeader)["kid"]
                ?.jsonPrimitive
                ?.content
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

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
