/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.command.token

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.session.Command
import com.sphereon.core.api.session.JourneyProfile
import com.sphereon.core.api.session.JourneyServiceCommand
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import kotlinx.serialization.json.JsonObject

/** How the keys that verified a token were anchored. */
enum class TokenIssuerAnchor {
    /** A registered, enabled signing key of this authorization server. */
    THIS_AUTHORIZATION_SERVER,

    /** Keys returned for the issuer by the configured subject-token issuer trust. */
    ISSUER_TRUST,
}

/** The issuer of a token whose signature verified against keys anchored as [anchor]. */
data class TrustedIssuerRef(
    val issuer: String,
    val anchor: TokenIssuerAnchor,
)

/**
 * Evidence that passed anchored signature, issuer, time and registered-client checks.
 *
 * Instances are created only by the verification steps of the token-exchange journey, after
 * verification succeeds. They exist in process and have no codec, so they cannot arrive over a
 * transport as trusted input. [claims] is an immutable copy of the verified payload.
 */
interface AnchoredToken {
    val issuer: TrustedIssuerRef
    val subject: String
    val tokenType: String
    val claims: JsonObject
}

/** Verified RFC 8693 subject token. */
interface AnchoredSubjectToken : AnchoredToken

/** Verified RFC 8693 actor token. */
interface AnchoredActorToken : AnchoredToken

/** Input of the fixed authorize-exchange position. It carries only anchored principals. */
data class AuthorizeTokenExchangeInput(
    val clientId: String,
    val subject: AnchoredSubjectToken,
    val actor: AnchoredActorToken?,
    val requestedResources: List<String>,
    /** Requested audiences, or the client's default audience when no target was requested. */
    val requestedAudiences: List<String>,
    val requestedScope: String?,
    /** Access-token targets authorized by the authenticated client's registration. */
    val registeredTargets: Set<String>,
    /** The authenticated client's registration-controlled exchange authority, uninterpreted. */
    val clientExchangeAuthority: Map<String, String> = emptyMap(),
)

/** Result of the authorize-exchange extension. Target and history bounds are applied afterwards. */
data class TokenExchangeAuthorization(
    val allowed: Boolean,
    val isDelegation: Boolean,
    val grantedScope: String?,
    val grantedTargets: List<String>,
    val denyReason: String? = null,
    /**
     * Requested resources the profile resolved to service audiences (RFC 8693 permits a resource
     * indicator that differs from the audience it maps to). Each mapped audience must also be in
     * [grantedTargets]; a mapped resource is never added to the issued audience.
     */
    val resourceMappings: List<TokenExchangeResourceMapping> = emptyList(),
)

/** One requested resource and the granted audiences it resolves to. */
data class TokenExchangeResourceMapping(
    val resource: String,
    val audiences: List<String>,
)

/** Authorized exchange after the standard registration and request target bounds. */
data class BoundedTokenExchange(
    val input: AuthorizeTokenExchangeInput,
    val authorization: TokenExchangeAuthorization,
    val audiences: List<String>,
    val resources: List<String>,
)

/**
 * Claims the profile adds to the issued token. Identity, target, sender-constraint, time and
 * actor claims are reserved; the standard step drops them before minting.
 */
data class TokenExchangeClaimMapping(
    val claims: JsonObject,
)

/** Input of the actor-chain extension. */
data class TokenExchangeActorChainInput(
    val bounded: BoundedTokenExchange,
    val mappedClaims: TokenExchangeClaimMapping,
)

/**
 * Profile additions to the current actor object. The standard step owns `sub`, `iss`,
 * `client_id` and the nested `act` history and ignores those names here.
 */
data class TokenExchangeActorExtensions(
    val claims: JsonObject,
)

/**
 * Named extension at the fixed authorize-exchange position. It is an in-process command: the
 * journey never registers it for remote routing.
 */
interface AuthorizeTokenExchangeCommand : Command<AuthorizeTokenExchangeInput, TokenExchangeAuthorization, AuthorizationServerError> {
    override val id: String get() = COMMAND_ID
    override val isEnabled: Boolean get() = true

    companion object {
        const val COMMAND_ID = "oauth2.tokenexchange.authorize"
    }
}

/** Named extension at the fixed map-claims position. */
interface MapTokenExchangeClaimsCommand : Command<BoundedTokenExchange, TokenExchangeClaimMapping, AuthorizationServerError> {
    override val id: String get() = COMMAND_ID
    override val isEnabled: Boolean get() = true

    companion object {
        const val COMMAND_ID = "oauth2.tokenexchange.map-claims"
    }
}

/** Named extension at the fixed build-actor-chain position. */
interface BuildTokenExchangeActorChainCommand : Command<TokenExchangeActorChainInput, TokenExchangeActorExtensions, AuthorizationServerError> {
    override val id: String get() = COMMAND_ID
    override val isEnabled: Boolean get() = true

    companion object {
        const val COMMAND_ID = "oauth2.tokenexchange.build-actor-chain"
    }
}

/**
 * Compile-time token-exchange profile selected by the service graph. It supplies the three
 * extensions; it cannot add, remove or reorder journey steps.
 */
interface TokenExchangeProfile : JourneyProfile {
    val authorize: AuthorizeTokenExchangeCommand
    val mapClaims: MapTokenExchangeClaimsCommand
    val buildActorChain: BuildTokenExchangeActorChainCommand
}

/** Step identifiers of the RFC 8693 token-exchange journey, in execution order. */
object TokenExchangeJourneySteps {
    const val AUTHENTICATE_CLIENT = "authenticate-client"
    const val PARSE_EXCHANGE = "parse-exchange"
    const val RESOLVE_ISSUER_TRUST = "resolve-issuer-trust"
    const val VERIFY_SUBJECT = "verify-subject"
    const val VERIFY_ACTOR = "verify-actor"
    const val AUTHORIZE_EXCHANGE = "authorize-exchange"
    const val BOUND_TARGETS = "bound-targets"
    const val MAP_CLAIMS = "map-claims"
    const val BUILD_ACTOR_CHAIN = "build-actor-chain"
    const val MINT = "mint"
    const val RESPOND = "respond"

    val ORDER: List<String> =
        listOf(
            AUTHENTICATE_CLIENT,
            PARSE_EXCHANGE,
            RESOLVE_ISSUER_TRUST,
            VERIFY_SUBJECT,
            VERIFY_ACTOR,
            AUTHORIZE_EXCHANGE,
            BOUND_TARGETS,
            MAP_CLAIMS,
            BUILD_ACTOR_CHAIN,
            MINT,
            RESPOND,
        )
}

/**
 * Outer routable ServiceCommand for an RFC 8693 token request. Its input is the raw token
 * endpoint request; client authentication, DPoP proof handling and every later check run inside
 * the journey exactly once.
 */
interface TokenExchangeJourneyCommand : JourneyServiceCommand<HandleTokenRequestArgs, TokenResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.journey.token-exchange"
    }
}
