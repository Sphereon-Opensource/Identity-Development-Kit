/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.command.token.exchange

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.error.sourceAs
import com.sphereon.core.api.session.Command
import com.sphereon.core.api.session.CommandErrorMapper
import com.sphereon.core.api.session.CommandId
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.ActorClaim
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.common.model.TokenTypeIdentifier
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseCommand
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthorization
import com.sphereon.oauth2.server.authorization.command.token.AnchoredActorToken
import com.sphereon.oauth2.server.authorization.command.token.AnchoredSubjectToken
import com.sphereon.oauth2.server.authorization.command.token.AuthorizeTokenExchangeCommand
import com.sphereon.oauth2.server.authorization.command.token.AuthorizeTokenExchangeInput
import com.sphereon.oauth2.server.authorization.command.token.BoundedTokenExchange
import com.sphereon.oauth2.server.authorization.command.token.BuildTokenExchangeActorChainCommand
import com.sphereon.oauth2.server.authorization.command.token.MapTokenExchangeClaimsCommand
import com.sphereon.oauth2.server.authorization.command.token.TokenExchangeActorChainInput
import com.sphereon.oauth2.server.authorization.command.token.TokenExchangeClaimMapping
import com.sphereon.oauth2.server.authorization.command.token.TokenExchangeProfile
import com.sphereon.oauth2.server.authorization.command.token.TokenIssuerAnchor
import com.sphereon.oauth2.server.authorization.command.token.TrustedIssuerRef
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.command.clientauth.toVerifiedClientAuthorization
import com.sphereon.oauth2.server.authorization.impl.command.token.grant.tokenTypeFor
import com.sphereon.oauth2.server.authorization.signing.AsSigningKeyPublicJwkResolver
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import com.sphereon.oauth2.server.authorization.trust.SubjectTokenIssuerTrust
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

private const val JWT_PART_COUNT = 3
internal const val MAX_ACTOR_CHAIN_DEPTH = 16
private const val NOT_BEFORE_CLOCK_SKEW_SECONDS = 1L
private const val CLAIM_ISSUER = "iss"
private const val CLAIM_SUBJECT = "sub"
private const val CLAIM_AUDIENCE = "aud"
private const val CLAIM_EXPIRATION = "exp"
private const val CLAIM_NOT_BEFORE = "nbf"
private const val CLAIM_CLIENT_ID = "client_id"
private const val CLAIM_AUTHORIZED_PARTY = "azp"
private const val CLAIM_EMAIL = "email"
private const val CLAIM_ACT = "act"
private const val CLAIM_MAY_ACT = "may_act"
private const val CLAIM_CNF = "cnf"
private const val SUBJECT_ROLE = "subject"
private const val ACTOR_ROLE = "actor"
private const val PLATFORM_TENANT_ID = "platform"

/** Actor-object names owned by the standard actor-chain step. */
internal val RESERVED_ACTOR_CLAIMS: Set<String> = setOf(CLAIM_SUBJECT, CLAIM_ISSUER, CLAIM_CLIENT_ID, CLAIM_ACT)

/**
 * Issued-token names owned by the standard steps: identity, audience/target, sender constraint,
 * lifetime, delegation and authentication context taken from the verified subject.
 */
internal val RESERVED_ISSUED_TOKEN_CLAIMS: Set<String> =
    setOf(
        CLAIM_ISSUER,
        CLAIM_SUBJECT,
        CLAIM_AUDIENCE,
        CLAIM_EXPIRATION,
        CLAIM_NOT_BEFORE,
        "iat",
        "jti",
        CLAIM_CLIENT_ID,
        CLAIM_AUTHORIZED_PARTY,
        "scope",
        CLAIM_CNF,
        CLAIM_ACT,
        CLAIM_MAY_ACT,
        "auth_time",
        "acr",
        "amr",
    )

/** Command-framework failures inside the journey are server errors, never client errors. */
internal object TokenExchangeProtocolErrorMapper : CommandErrorMapper<AuthorizationServerError> {
    override fun unsupportedArg(command: Any, arg: Any): AuthorizationServerError =
        AuthorizationServerError.ServerError(details = "Token exchange step received an unexpected state")

    override fun commandDisabled(commandId: String): AuthorizationServerError =
        AuthorizationServerError.ServerError(details = "Token exchange step '$commandId' is disabled")

    override fun notAuthorized(commandId: CommandId, reason: String): AuthorizationServerError =
        AuthorizationServerError.ServerError(details = "Token exchange step was not authorized to run")

    override fun unknown(message: String, cause: Throwable?): AuthorizationServerError =
        AuthorizationServerError.ServerError(details = message, exception = cause)

    override fun allHandlersFailed(errors: List<AuthorizationServerError>): AuthorizationServerError =
        errors.firstOrNull() ?: AuthorizationServerError.ServerError(details = "Token exchange failed")

    override fun invalidCommandId(commandId: String): AuthorizationServerError =
        AuthorizationServerError.ServerError(details = "Invalid token exchange step id")
}

/** The fixed RFC 8693 steps, composed by the token-exchange journey. One instance per request scope. */
internal class TokenExchangeStepCommands(
    private val tenantId: String,
    private val clientRegistry: ClientRegistry,
    private val jwtService: JwtService,
    private val signingKeyStore: SigningKeyStore,
    private val serversConfigProvider: OAuth2ServersConfigProvider,
    private val signingKeyPublicJwkResolver: AsSigningKeyPublicJwkResolver?,
    private val subjectTokenIssuerTrust: SubjectTokenIssuerTrust,
    private val profile: TokenExchangeProfile,
    private val createAccessToken: CreateAccessTokenCommand,
    private val createTokenResponse: CreateTokenResponseCommand,
) {
    val parse: Command<ClientAuthenticatedExchange, ParsedExchange, AuthorizationServerError> =
        step("oauth2.tokenexchange.parse") { parseExchange(it) }

    val resolveIssuerTrust: Command<ParsedExchange, IssuerTrustResolvedExchange, AuthorizationServerError> =
        step("oauth2.tokenexchange.resolve-issuer-trust") { resolveTrust(it) }

    val verifySubject: Command<IssuerTrustResolvedExchange, SubjectVerifiedExchange, AuthorizationServerError> =
        step("oauth2.tokenexchange.verify-subject") { verifySubjectToken(it) }

    val verifyActor: Command<SubjectVerifiedExchange, PrincipalsVerifiedExchange, AuthorizationServerError> =
        step("oauth2.tokenexchange.verify-actor") { verifyActorToken(it) }

    val authorize: Command<PrincipalsVerifiedExchange, AuthorizedExchange, AuthorizationServerError> =
        step(AuthorizeTokenExchangeCommand.COMMAND_ID) { authorizeExchange(it) }

    val boundTargets: Command<AuthorizedExchange, TargetBoundedExchange, AuthorizationServerError> =
        step("oauth2.tokenexchange.bound-targets") { bound(it) }

    val mapClaims: Command<TargetBoundedExchange, ClaimsMappedExchange, AuthorizationServerError> =
        step(MapTokenExchangeClaimsCommand.COMMAND_ID) { mapIssuedClaims(it) }

    val buildActorChain: Command<ClaimsMappedExchange, ExchangeGrant, AuthorizationServerError> =
        step(BuildTokenExchangeActorChainCommand.COMMAND_ID) { buildActorChainFor(it) }

    val mint: Command<ExchangeGrant, MintedExchange, AuthorizationServerError> =
        step("oauth2.tokenexchange.mint") { mintAccessToken(it) }

    val respond: Command<MintedExchange, TokenResponse, AuthorizationServerError> =
        step("oauth2.tokenexchange.respond") { respondWith(it) }

    // ---------------------------------------------------------------- parse-exchange

    private suspend fun parseExchange(request: ClientAuthenticatedExchange): IdkResult<ParsedExchange, AuthorizationServerError> {
        val parameters = request.parameters
        if (parameters.subjectToken.isBlank()) {
            return invalidRequest("Missing required parameter: subject_token")
        }
        if (parameters.subjectTokenType.isBlank()) {
            return invalidRequest("Missing required parameter: subject_token_type")
        }
        if (parameters.actorToken != null && parameters.actorToken.isBlank()) {
            return invalidRequest("actor_token must not be blank")
        }
        if (parameters.actorToken != null && parameters.actorTokenType.isNullOrBlank()) {
            return invalidRequest("actor_token_type is required when actor_token is present")
        }
        if (parameters.actorToken == null && parameters.actorTokenType != null) {
            return invalidRequest("actor_token_type requires actor_token")
        }
        if (parameters.requestedTokenType != null && parameters.requestedTokenType != TokenTypeIdentifier.ACCESS_TOKEN) {
            return invalidRequest("Requested token type is not supported")
        }

        val authenticated = request.clientAuthorization
        if (authenticated != null && authenticated.clientId != request.requestedClientId) {
            return Err(AuthorizationServerError.InvalidClient(details = "Authenticated client does not match requested client"))
        }
        val client =
            authenticated
                ?: clientRegistry
                    .getClient(request.requestedClientId)
                    .getOrElse { error ->
                        return Err(AuthorizationServerError.ServerError(details = "Failed to retrieve client registration: $error"))
                    }?.toVerifiedClientAuthorization()
                ?: return Err(AuthorizationServerError.InvalidClient(details = "Client not found"))

        if (GrantType.TOKEN_EXCHANGE !in client.grantTypes) {
            return Err(AuthorizationServerError.UnauthorizedClient(clientId = request.requestedClientId))
        }

        val defaultAudience = client.defaultAccessTokenAudience?.trim()?.takeIf(String::isNotEmpty)
        val registeredTargets = registeredTargetsOf(client)
        if (parameters.resources.any { !it.isAbsoluteUriWithoutFragment() }) {
            return invalidRequest("resource must be an absolute URI without a fragment")
        }
        val requestedTargets = (parameters.audiences + parameters.resources).distinct()
        // Audiences must be registered up front. A resource may instead be resolved by the
        // profile to registered audiences; the bound step enforces that resolution.
        val unregistered = parameters.audiences.filter { it.isEmpty() || it !in registeredTargets }.distinct()
        if (unregistered.isNotEmpty()) {
            return Err(
                AuthorizationServerError.InvalidTarget(
                    audience = unregistered.joinToString(" "),
                    reason = "Requested audience is not registered for this client",
                ),
            )
        }
        val effectiveRequestedTargets =
            requestedTargets.ifEmpty {
                listOf(
                    defaultAudience
                        ?: return Err(
                            AuthorizationServerError.InvalidTarget(
                                audience = "",
                                reason = "No target was requested and this client has no default access-token audience",
                            ),
                        ),
                )
            }
        return Ok(
            ParsedExchange(
                clientId = request.requestedClientId,
                client = client,
                sender = request.sender,
                parameters = parameters,
                registeredTargets = registeredTargets,
                effectiveRequestedTargets = effectiveRequestedTargets,
                requestedAudiencesForAuthorization =
                    parameters.audiences.ifEmpty {
                        if (parameters.resources.isEmpty()) effectiveRequestedTargets else emptyList()
                    },
            ),
        )
    }

    // ---------------------------------------------------------------- resolve-issuer-trust

    private suspend fun resolveTrust(parsed: ParsedExchange): IdkResult<IssuerTrustResolvedExchange, AuthorizationServerError> {
        val parameters = parsed.parameters
        val expectedIssuer = configuredIssuer().getOrElse { return Err(it) }
        val subject =
            resolveTokenIssuer(parameters.subjectToken, parameters.subjectTokenType, "subject", expectedIssuer)
                .getOrElse { return Err(it) }
        val actorToken = parameters.actorToken
        val actorTokenType = parameters.actorTokenType
        val actor =
            if (actorToken != null && actorTokenType != null) {
                resolveTokenIssuer(actorToken, actorTokenType, "actor", expectedIssuer).getOrElse { return Err(it) }
            } else {
                null
            }
        return Ok(IssuerTrustResolvedExchange(parsed, subject, actor))
    }

    /**
     * Decodes the token structure and resolves verification keys for the issuer it names.
     * A token naming this AS resolves only to this AS's registered, enabled keys; any other
     * issuer resolves only through [SubjectTokenIssuerTrust]. Nothing in the token can select
     * its own key.
     */
    private suspend fun resolveTokenIssuer(
        token: String,
        tokenType: String,
        role: String,
        expectedIssuer: String,
    ): IdkResult<IssuerResolvedToken, AuthorizationServerError> {
        when (tokenType) {
            TokenTypeIdentifier.ACCESS_TOKEN,
            TokenTypeIdentifier.ID_TOKEN,
            TokenTypeIdentifier.JWT,
            -> Unit
            TokenTypeIdentifier.REFRESH_TOKEN -> return invalidRequest("Opaque refresh tokens are not supported for token exchange")
            TokenTypeIdentifier.SAML1,
            TokenTypeIdentifier.SAML2,
            -> return invalidRequest("SAML token types are not supported for $role token")
            else -> return invalidRequest("Unsupported $role token type: $tokenType")
        }
        val parts = token.split(".")
        if (parts.size != JWT_PART_COUNT) {
            return invalidRequest("Invalid JWT format for $role token")
        }
        val claims =
            runCatching { JwsUtils.decodeBase64UrlToJson(parts[1]) }.getOrNull()
                ?: return invalidRequest("Failed to decode $role token JWT payload")
        val tokenIssuer = claims.strictString(CLAIM_ISSUER)
            ?: return invalidRequest("$role token is missing its issuer")
        val header =
            runCatching { JwsUtils.decodeBase64UrlToJson(parts[0]) }.getOrNull()
                ?: return invalidRequest("$role token has an invalid protected header")
        if ("jwk" in header) {
            return invalidRequest("$role token cannot select a key from its protected header")
        }
        val kid = (header["kid"] as? JsonPrimitive)?.content?.takeIf(String::isNotBlank)
        if (kid?.startsWith("did:", ignoreCase = true) == true) {
            return invalidRequest("$role token cannot use a DID kid as a trust root")
        }

        if (tokenIssuer == expectedIssuer) {
            // A token that claims this AS never escapes to the issuer trust. Otherwise an
            // unknown kid could turn a local issuer claim into a self-selected trust root.
            val localSigningKey = kid?.let { signingKeyForIssuer(it, expectedIssuer) }
                ?: return invalidRequest("$role token uses an unknown local signing key")
            if (localSigningKey.state == OAuth2SigningKeyState.DISABLED) {
                return invalidRequest("$role token uses a disabled local signing key")
            }
            val resolver = signingKeyPublicJwkResolver
                ?: return invalidRequest("$role token local signing key resolver is unavailable")
            val publicJwk = resolver.resolve(localSigningKey)
                ?: return invalidRequest("$role token local signing key could not be resolved")
            return Ok(
                IssuerResolvedToken(
                    role = role,
                    token = token,
                    tokenType = tokenType,
                    issuer = tokenIssuer,
                    trustedIssuer = TrustedIssuerRef(tokenIssuer, TokenIssuerAnchor.THIS_AUTHORIZATION_SERVER),
                    untrustedClaims = claims,
                    trustedJwks = jwksOf(listOf(publicJwk)),
                ),
            )
        }

        if (kid == null) {
            return invalidRequest("$role token is missing a key identifier")
        }
        val trustedKeys = subjectTokenIssuerTrust.resolveTrustedKeys(tokenIssuer)
            ?: return invalidRequest("$role token issuer is not trusted")
        if (trustedKeys.none { it.kid == kid }) {
            return invalidRequest("$role token key is not trusted for its issuer")
        }
        return Ok(
            IssuerResolvedToken(
                role = role,
                token = token,
                tokenType = tokenType,
                issuer = tokenIssuer,
                trustedIssuer = TrustedIssuerRef(tokenIssuer, TokenIssuerAnchor.ISSUER_TRUST),
                untrustedClaims = claims,
                trustedJwks = jwksOf(trustedKeys),
            ),
        )
    }

    /**
     * The monolith hosts the platform authorization server and tenant workloads in one process.
     * A sibling-audience exchange can therefore execute from a tenant session while its subject
     * token is still issued by the shared platform authority. Resolve that subject key from the
     * platform tenant only when the configured issuer is demonstrably the platform issuer; a
     * distinct tenant issuer remains strictly tenant-local.
     */
    private suspend fun signingKeyForIssuer(
        kid: String,
        expectedIssuer: String,
    ) = signingKeyStore.findByKid(tenantId, kid).getOrNull()
        ?: runCatching {
            val config = serversConfigProvider.getConfig()
            val platformIssuer = serversConfigProvider
                .resolveIssuer(config.defaultServer, PLATFORM_TENANT_ID)
                .trim()
            if (expectedIssuer == platformIssuer && tenantId != PLATFORM_TENANT_ID) {
                signingKeyStore.findByKid(PLATFORM_TENANT_ID, kid).getOrNull()
            } else {
                null
            }
        }.getOrNull()

    private fun configuredIssuer(): IdkResult<String, AuthorizationServerError> {
        val issuer =
            runCatching {
                val config = serversConfigProvider.getConfig()
                serversConfigProvider
                    .resolveIssuer(config.defaultServer, tenantId)
                    .trim()
                    .takeIf(String::isNotEmpty)
            }.getOrNull()
        return if (issuer != null) {
            Ok(issuer)
        } else {
            Err(AuthorizationServerError.ServerError(details = "Authorization server issuer policy is unavailable"))
        }
    }

    // ---------------------------------------------------------------- verify-subject

    private suspend fun verifySubjectToken(trust: IssuerTrustResolvedExchange): IdkResult<SubjectVerifiedExchange, AuthorizationServerError> {
        val resolved = trust.subject
        verifySignatureAndTime(resolved)?.let { return Err(it) }
        val claims = resolved.untrustedClaims
        if (resolved.trustedIssuer.anchor == TokenIssuerAnchor.THIS_AUTHORIZATION_SERVER) {
            val issuingClient = registeredLocalTokenClient(claims, resolved.role).getOrElse { return Err(it) }
            validateLocalTokenClaims(claims, SUBJECT_ROLE, trust.parsed.client, issuingClient)?.let { return Err(it) }
        }
        val subject = claims.strictString(CLAIM_SUBJECT) ?: return invalidRequest("Subject token is missing sub")
        val priorActor =
            if (CLAIM_ACT in claims) {
                actorClaimFrom(claims[CLAIM_ACT], 0)
                    ?: return invalidRequest("Subject token act claim is malformed")
            } else {
                null
            }
        return Ok(
            SubjectVerifiedExchange(
                trust = trust,
                subject = anchoredSubject(resolved, subject),
                priorActor = priorActor,
                subjectCnfJkt = ((claims[CLAIM_CNF] as? JsonObject)?.get("jkt") as? JsonPrimitive)?.takeIf { it.isString }?.content,
            ),
        )
    }

    // ---------------------------------------------------------------- verify-actor

    private suspend fun verifyActorToken(verified: SubjectVerifiedExchange): IdkResult<PrincipalsVerifiedExchange, AuthorizationServerError> {
        val resolved = verified.trust.actor
        val actor =
            if (resolved != null) {
                verifySignatureAndTime(resolved)?.let { return Err(it) }
                if (resolved.trustedIssuer.anchor == TokenIssuerAnchor.THIS_AUTHORIZATION_SERVER) {
                    val issuingClient = registeredLocalTokenClient(resolved.untrustedClaims, resolved.role).getOrElse { return Err(it) }
                    validateLocalTokenClaims(resolved.untrustedClaims, ACTOR_ROLE, verified.trust.parsed.client, issuingClient)?.let { return Err(it) }
                }
                val actorSubject = resolved.untrustedClaims.strictString(CLAIM_SUBJECT)
                    ?: return invalidRequest("Actor token is missing sub")
                anchoredActor(resolved, actorSubject)
            } else {
                null
            }

        val mayAct = verified.subject.claims[CLAIM_MAY_ACT]
        if (mayAct != null) {
            val mayActClaims = mayAct as? JsonObject ?: return invalidRequest("Subject token may_act claim is malformed")
            val mayActIssuer = mayActClaims.strictString(CLAIM_ISSUER) ?: return invalidRequest("Subject token may_act claim is missing iss")
            val mayActSubject = mayActClaims.strictString(CLAIM_SUBJECT) ?: return invalidRequest("Subject token may_act claim is missing sub")
            val actorIssuer = actor?.issuer?.issuer ?: return invalidRequest("may_act requires an actor token with iss")
            if (mayActIssuer != actorIssuer || mayActSubject != actor.subject) {
                return invalidRequest("Actor is not authorized by subject token may_act claim")
            }
        }
        val priorActor = verified.priorActor
        if (actor != null && priorActor != null && actorChainDepth(priorActor) >= MAX_ACTOR_CHAIN_DEPTH) {
            return invalidRequest("Actor token would exceed the maximum actor chain depth")
        }
        return Ok(
            PrincipalsVerifiedExchange(
                parsed = verified.trust.parsed,
                subject = verified.subject,
                actor = actor,
                priorActor = priorActor,
                subjectCnfJkt = verified.subjectCnfJkt,
            ),
        )
    }

    // ---------------------------------------------------------------- authorize-exchange

    private suspend fun authorizeExchange(principals: PrincipalsVerifiedExchange): IdkResult<AuthorizedExchange, AuthorizationServerError> {
        val parsed = principals.parsed
        val input =
            AuthorizeTokenExchangeInput(
                clientId = parsed.clientId,
                subject = principals.subject,
                actor = principals.actor,
                requestedResources = parsed.parameters.resources.toList(),
                requestedAudiences = parsed.requestedAudiencesForAuthorization.toList(),
                requestedScope = parsed.parameters.scope,
                registeredTargets = parsed.registeredTargets.toSet(),
                clientExchangeAuthority = parsed.client.tokenExchangeAuthority.toMap(),
            )
        val authorization =
            profile.authorize.execute(input).getOrElse { error ->
                return Err(
                    when (error) {
                        is AuthorizationServerError.InvalidTarget,
                        is AuthorizationServerError.ServerError,
                        is AuthorizationServerError.TemporarilyUnavailable,
                        -> error
                        is AuthorizationServerError.StorageError -> AuthorizationServerError.ServerError(details = "Token exchange policy is unavailable")
                        else -> AuthorizationServerError.InvalidRequest(details = "Token exchange denied by policy")
                    },
                )
            }
        if (!authorization.allowed) {
            return invalidRequest(authorization.denyReason ?: "Token exchange denied by policy")
        }
        return Ok(AuthorizedExchange(principals, input, authorization.copy(grantedTargets = authorization.grantedTargets.toList())))
    }

    // ---------------------------------------------------------------- bound-targets

    private fun bound(authorized: AuthorizedExchange): IdkResult<TargetBoundedExchange, AuthorizationServerError> {
        val parsed = authorized.principals.parsed
        val grantedTargets = authorized.authorization.grantedTargets.distinct()
        val unregistered = grantedTargets.filter { it !in parsed.registeredTargets }
        if (unregistered.isNotEmpty()) {
            return Err(
                AuthorizationServerError.InvalidTarget(
                    audience = unregistered.joinToString(" "),
                    reason = "Token exchange policy selected an audience not registered for this client",
                ),
            )
        }
        val mappings = authorized.authorization.resourceMappings.associate { it.resource to it.audiences.distinct() }
        val unmappedOrUngranted =
            mappings.filter { (resource, audiences) ->
                resource !in parsed.parameters.resources || audiences.isEmpty() || audiences.any { it !in grantedTargets }
            }.keys
        if (unmappedOrUngranted.isNotEmpty()) {
            return Err(
                AuthorizationServerError.InvalidTarget(
                    resource = unmappedOrUngranted.joinToString(" "),
                    reason = "Token exchange policy mapped a resource that was not requested or not granted",
                ),
            )
        }
        val missing =
            parsed.effectiveRequestedTargets.filter { target ->
                target !in grantedTargets && !(target in parsed.parameters.resources && target in mappings)
            }
        if (missing.isNotEmpty() || grantedTargets.isEmpty()) {
            return Err(
                AuthorizationServerError.InvalidTarget(
                    audience = missing.ifEmpty { parsed.effectiveRequestedTargets }.joinToString(" "),
                    resource = parsed.parameters.resources.joinToString(" ").takeIf(String::isNotEmpty),
                    reason = "Token exchange policy did not authorize every requested target",
                ),
            )
        }
        return Ok(
            TargetBoundedExchange(
                authorized = authorized,
                grantedTargets = grantedTargets,
                bounded =
                    BoundedTokenExchange(
                        input = authorized.input,
                        authorization = authorized.authorization,
                        audiences = grantedTargets.toList(),
                        resources = parsed.parameters.resources.toList(),
                    ),
            ),
        )
    }

    // ---------------------------------------------------------------- map-claims

    private suspend fun mapIssuedClaims(bounded: TargetBoundedExchange): IdkResult<ClaimsMappedExchange, AuthorizationServerError> {
        val mapping = profile.mapClaims.execute(bounded.bounded).getOrElse { return Err(it) }
        return Ok(
            ClaimsMappedExchange(
                bounded = bounded,
                additionalClaims = JsonObject(mapping.claims.filterKeys { it !in RESERVED_ISSUED_TOKEN_CLAIMS }.mapValues { (_, value) -> deepCopy(value) }),
            ),
        )
    }

    // ---------------------------------------------------------------- build-actor-chain

    private suspend fun buildActorChainFor(mapped: ClaimsMappedExchange): IdkResult<ExchangeGrant, AuthorizationServerError> {
        val bounded = mapped.bounded
        val authorized = bounded.authorized
        val principals = authorized.principals
        val parsed = principals.parsed
        val subject = principals.subject
        val actor = principals.actor
        val priorActor = principals.priorActor
        val policyDelegation = authorized.authorization.isDelegation

        val insertsCurrentActor = actor != null || policyDelegation
        if (insertsCurrentActor && priorActor != null && actorChainDepth(priorActor) >= MAX_ACTOR_CHAIN_DEPTH) {
            return invalidRequest("Actor token would exceed the maximum actor chain depth")
        }
        val actorSubject = actor?.subject ?: if (policyDelegation) subject.subject else priorActor?.sub

        val actorClaim =
            if (insertsCurrentActor && actorSubject != null) {
                val currentActor = actor ?: subject
                val currentActorClientId =
                    if (actor == null) {
                        parsed.clientId
                    } else if (CLAIM_CLIENT_ID in actor.claims) {
                        actor.claims.strictString(CLAIM_CLIENT_ID) ?: return invalidRequest("Actor token has an invalid client_id")
                    } else {
                        null
                    }
                val extensions =
                    profile.buildActorChain
                        .execute(TokenExchangeActorChainInput(bounded.bounded, TokenExchangeClaimMapping(mapped.additionalClaims)))
                        .getOrElse { return Err(it) }
                ActorClaim(
                    sub = actorSubject,
                    act = priorActor,
                    additionalClaims =
                        buildMap {
                            extensions.claims
                                .filterKeys { it !in RESERVED_ACTOR_CLAIMS }
                                .forEach { (key, value) -> put(key, deepCopy(value)) }
                            put(CLAIM_ISSUER, JsonPrimitive(currentActor.issuer.issuer))
                            currentActorClientId?.let { put(CLAIM_CLIENT_ID, JsonPrimitive(it)) }
                        },
                )
            } else {
                priorActor
            }

        return Ok(
            ExchangeGrant(
                mapped = mapped,
                subject = subject.subject,
                clientId = parsed.clientId,
                scope = authorized.authorization.grantedScope,
                audience = bounded.grantedTargets,
                resources = parsed.parameters.resources,
                isDelegation = policyDelegation || actor != null || priorActor != null,
                actorSubject = actorSubject,
                actorClaim = actorClaim,
                authTime = subject.claims.numericDate("auth_time"),
                acr = subject.claims.strictString("acr"),
                amr = subject.claims.stringValuesLenient("amr"),
                subjectCnfJkt = principals.subjectCnfJkt,
                sender = parsed.sender,
            ),
        )
    }

    // ---------------------------------------------------------------- mint

    private suspend fun mintAccessToken(grant: ExchangeGrant): IdkResult<MintedExchange, AuthorizationServerError> {
        val sender = grant.sender

        // RFC 9449 §10.1: when the subject token carries `cnf.jkt`, the DPoP proof on this
        // exchange MUST be from the same key. A bound subject token with no proof, or a proof
        // from a different key, fails as `invalid_dpop_proof`.
        val subjectJkt = grant.subjectCnfJkt
        val proofJkt = sender.proofJkt
        if (subjectJkt != null && proofJkt == null) {
            return Err(AuthorizationServerError.InvalidDpopProof(details = "Subject token is DPoP-bound but request did not present a DPoP proof"))
        }
        if (subjectJkt != null && proofJkt != null && subjectJkt != proofJkt) {
            return Err(AuthorizationServerError.InvalidDpopProof(details = "DPoP proof thumbprint does not match subject token cnf.jkt"))
        }
        // The exchanged token is bound to the proof's thumbprint. When the subject token was
        // bound, the check above makes these equal.
        val boundJkt = subjectJkt ?: proofJkt

        val accessToken =
            createAccessToken
                .execute(
                    CreateAccessTokenArgs(
                        subject = grant.subject,
                        clientId = grant.clientId,
                        scope = grant.scope,
                        audience = grant.audience,
                        dpopJkt = boundJkt,
                        certificateThumbprintS256 = sender.certThumbprintS256,
                        authTime = grant.authTime,
                        acr = grant.acr,
                        amr = grant.amr,
                        additionalClaims =
                            buildMap {
                                putAll(grant.mapped.additionalClaims)
                                grant.actorClaim?.let { put(CLAIM_ACT, it) }
                            },
                        baseUrlOverride = sender.baseUrlOverride,
                    ),
                ).getOrElse { return Err(it.asProtocolError()) }
        return Ok(MintedExchange(grant, accessToken.value, boundJkt))
    }

    // ---------------------------------------------------------------- respond

    private suspend fun respondWith(minted: MintedExchange): IdkResult<TokenResponse, AuthorizationServerError> {
        // No refresh token for token exchange (RFC 8693 §2.1).
        return createTokenResponse
            .execute(
                CreateTokenResponseArgs(
                    accessToken = minted.accessToken,
                    tokenType = tokenTypeFor(minted.boundJkt),
                    scope = minted.grant.scope,
                    issuedTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
                ),
            ).mapError { it.asProtocolError() }
    }

    // ---------------------------------------------------------------- verification helpers

    private suspend fun verifySignatureAndTime(resolved: IssuerResolvedToken): AuthorizationServerError? {
        val result = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(resolved.token), trustedJwks = resolved.trustedJwks))
        if (result.isErr || !result.value.isValid) {
            val local = resolved.trustedIssuer.anchor == TokenIssuerAnchor.THIS_AUTHORIZATION_SERVER
            return AuthorizationServerError.InvalidRequest(
                details = if (local) "${resolved.role} token has an invalid local signature" else "${resolved.role} token has an invalid signature",
            )
        }
        val claims = resolved.untrustedClaims
        val now = Clock.System.now().epochSeconds
        val expiresAt = claims.numericDate(CLAIM_EXPIRATION)
            ?: return AuthorizationServerError.InvalidRequest(details = "${resolved.role} token has no valid exp claim")
        if (expiresAt <= now) {
            return AuthorizationServerError.InvalidRequest(details = "${resolved.role} token is expired")
        }
        if (CLAIM_NOT_BEFORE in claims) {
            val notBefore = claims.numericDate(CLAIM_NOT_BEFORE)
                ?: return AuthorizationServerError.InvalidRequest(details = "${resolved.role} token has an invalid nbf claim")
            if (notBefore > now + NOT_BEFORE_CLOCK_SKEW_SECONDS) {
                return AuthorizationServerError.InvalidRequest(details = "${resolved.role} token is not yet valid")
            }
        }
        return null
    }

    private suspend fun registeredLocalTokenClient(
        claims: JsonObject,
        role: String,
    ): IdkResult<VerifiedClientAuthorization, AuthorizationServerError> {
        val issuingClientId =
            (if (CLAIM_CLIENT_ID in claims) claims.strictString(CLAIM_CLIENT_ID) else claims.strictString(CLAIM_AUTHORIZED_PARTY))
                ?: return invalidRequest("$role token was not issued to a registered client")
        val issuingClient =
            clientRegistry
                .getClient(issuingClientId)
                .getOrElse { return invalidRequest("$role token was not issued to a registered client") }
                ?: return invalidRequest("$role token was not issued to a registered client")
        return Ok(issuingClient.toVerifiedClientAuthorization())
    }

    /**
     * A token this AS issued is usable in an exchange only within the audiences its client is
     * registered for, and a workload token only by the client it was issued to. Subject and actor
     * tokens are held to the same rule.
     */
    private fun validateLocalTokenClaims(
        claims: JsonObject,
        role: String,
        exchangingClient: VerifiedClientAuthorization,
        issuingClient: VerifiedClientAuthorization,
    ): AuthorizationServerError? {
        val audiences = claims.stringValues(CLAIM_AUDIENCE)?.takeIf { it.isNotEmpty() }
            ?: return AuthorizationServerError.InvalidRequest(details = "$role token has no valid aud claim")
        val allowedAudiences = registeredTargetsOf(issuingClient)
        if (allowedAudiences.isEmpty() || audiences.any { it !in allowedAudiences }) {
            return AuthorizationServerError.InvalidRequest(details = "$role token audience is not authorized for its client")
        }
        val subject = claims.strictString(CLAIM_SUBJECT)
        val isWorkload = subject != null && subject == issuingClient.clientId && claims[CLAIM_EMAIL] == null
        if (isWorkload) {
            val authorizedParty = claims.strictString(CLAIM_AUTHORIZED_PARTY)
            if (authorizedParty == null || authorizedParty != issuingClient.clientId || exchangingClient.clientId != issuingClient.clientId) {
                return AuthorizationServerError.InvalidRequest(details = "workload $role token is not bound to the exchanging client")
            }
        }
        return null
    }
}

/** Access-token targets authorized by a client's registration. */
internal fun registeredTargetsOf(client: VerifiedClientAuthorization): Set<String> =
    buildSet {
        client.defaultAccessTokenAudience?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
        client.allowedAccessTokenAudiences.map(String::trim).filter(String::isNotEmpty).forEach(::add)
    }

internal fun actorChainDepth(actor: ActorClaim): Int = 1 + (actor.act?.let(::actorChainDepth) ?: 0)

private fun actorClaimFrom(
    value: Any?,
    depth: Int,
): ActorClaim? {
    if (depth >= MAX_ACTOR_CHAIN_DEPTH) return null
    val claims = value as? JsonObject ?: return null
    val subject = claims.strictString(CLAIM_SUBJECT) ?: return null
    val nested = claims[CLAIM_ACT]?.let { actorClaimFrom(it, depth + 1) }
    if (CLAIM_ACT in claims && nested == null) return null
    return ActorClaim(
        sub = subject,
        act = nested,
        additionalClaims = claims.filterKeys { it != CLAIM_SUBJECT && it != CLAIM_ACT }.mapValues { (_, claim) -> deepCopy(claim) },
    )
}

/** Authentication-method references; non-string entries are ignored as they were before. */
private fun JsonObject.stringValuesLenient(name: String): List<String>? =
    (this[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content }

private fun jwksOf(keys: List<Jwk>): JsonObject =
    buildJsonObject {
        put("keys", JsonArray(keys.map { Json.encodeToJsonElement(Jwk.serializer(), it) }))
    }

private fun invalidRequest(details: String): IdkResult<Nothing, AuthorizationServerError> =
    Err(AuthorizationServerError.InvalidRequest(details = details))

/**
 * Failures of IdkError-based commands keep their protocol meaning inside the journey: a typed
 * authorization-server error passes through, and a plain error carrying an RFC 6749 or RFC 9449
 * token-endpoint code keeps that code, message and metadata. Anything else is a server error.
 */
internal fun IdkErrorType.asProtocolError(): AuthorizationServerError {
    (this as? AuthorizationServerError)?.let { return it }
    (this as? IdkError)?.sourceAs<AuthorizationServerError>()?.let { return it }
    val details = message.defaultMessage
    return when (code) {
        "invalid_request" -> AuthorizationServerError.InvalidRequest(details = details, message = message, exception = exception, causes = causes, meta = meta)
        "invalid_client" -> AuthorizationServerError.InvalidClient(details = details, message = message, exception = exception, causes = causes, meta = meta)
        "invalid_grant" -> AuthorizationServerError.InvalidGrant(details = details, message = message, exception = exception, causes = causes, meta = meta)
        "invalid_dpop_proof" -> AuthorizationServerError.InvalidDpopProof(details = details, message = message, exception = exception, causes = causes, meta = meta)
        "access_denied" -> AuthorizationServerError.AccessDenied(reason = details, message = message, exception = exception, causes = causes, meta = meta)
        else -> AuthorizationServerError.ServerError(details = details)
    }
}

private fun <I : Any, O : Any> step(
    commandId: String,
    body: suspend (I) -> IdkResult<O, AuthorizationServerError>,
): Command<I, O, AuthorizationServerError> =
    object : Command<I, O, AuthorizationServerError> {
        override val id: String = commandId
        override val isEnabled: Boolean = true

        override suspend fun execute(args: I): IdkResult<O, AuthorizationServerError> = body(args)
    }

/**
 * Anchored evidence implementations. They are private to this file; the verify steps create them
 * only after anchored signature, time and registered-client checks succeed.
 */
private class VerifiedSubjectToken(
    override val issuer: TrustedIssuerRef,
    override val subject: String,
    override val tokenType: String,
    override val claims: JsonObject,
) : AnchoredSubjectToken

private class VerifiedActorToken(
    override val issuer: TrustedIssuerRef,
    override val subject: String,
    override val tokenType: String,
    override val claims: JsonObject,
) : AnchoredActorToken

private fun anchoredSubject(
    resolved: IssuerResolvedToken,
    subject: String,
): AnchoredSubjectToken = VerifiedSubjectToken(resolved.trustedIssuer, subject, resolved.tokenType, deepCopy(resolved.untrustedClaims))

private fun anchoredActor(
    resolved: IssuerResolvedToken,
    subject: String,
): AnchoredActorToken = VerifiedActorToken(resolved.trustedIssuer, subject, resolved.tokenType, deepCopy(resolved.untrustedClaims))

/** RFC 8693 resource values are absolute RFC 3986 URIs and cannot contain fragments. */
private fun String.isAbsoluteUriWithoutFragment(): Boolean {
    if (isEmpty() || any { it <= ' ' || it >= '\u007f' } || '#' in this) return false
    val colon = indexOf(':')
    if (colon <= 0 || !this[0].isAsciiLetter()) return false
    if (take(colon).drop(1).any { !it.isAsciiLetterOrDigit() && it !in "+-." }) return false
    var index = colon + 1
    while (index < length) {
        val character = this[index]
        if (character == '%') {
            if (index + 2 >= length || !this[index + 1].isHexDigit() || !this[index + 2].isHexDigit()) return false
            index += 3
            continue
        }
        if (!character.isAsciiLetterOrDigit() && character !in "-._~:/?@!$&'()*+,;=[]") return false
        index++
    }
    return true
}

private fun Char.isAsciiLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

private fun Char.isAsciiLetterOrDigit(): Boolean = isAsciiLetter() || this in '0'..'9'

private fun Char.isHexDigit(): Boolean = isAsciiLetter() && lowercaseChar() in 'a'..'f' || this in '0'..'9'
