/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.policy

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.token.AuthorizeTokenExchangeCommand
import com.sphereon.oauth2.server.authorization.command.token.AuthorizeTokenExchangeInput
import com.sphereon.oauth2.server.authorization.command.token.BoundedTokenExchange
import com.sphereon.oauth2.server.authorization.command.token.BuildTokenExchangeActorChainCommand
import com.sphereon.oauth2.server.authorization.command.token.MapTokenExchangeClaimsCommand
import com.sphereon.oauth2.server.authorization.command.token.TokenExchangeActorChainInput
import com.sphereon.oauth2.server.authorization.command.token.TokenExchangeActorExtensions
import com.sphereon.oauth2.server.authorization.command.token.TokenExchangeAuthorization
import com.sphereon.oauth2.server.authorization.command.token.TokenExchangeClaimMapping
import com.sphereon.oauth2.server.authorization.command.token.TokenExchangeProfile
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Standard RFC 8693 authorization.
 *
 * - Delegation when an actor token is present, impersonation otherwise.
 * - Grants only requested targets that the authenticated client's registration authorizes.
 * - Downscopes `scope` to what the subject token already carries (RFC 8693 §2.1): the issued
 *   token is never broader than the subject's authorization.
 *
 * Subject and actor are anchored, verified tokens. `may_act`, target bounds, reserved claims
 * and actor history are enforced by the fixed journey steps regardless of this result.
 */
class StandardAuthorizeTokenExchangeCommand : AuthorizeTokenExchangeCommand {
    override suspend fun execute(args: AuthorizeTokenExchangeInput): IdkResult<TokenExchangeAuthorization, AuthorizationServerError> {
        val requestedTargets = (args.requestedAudiences + args.requestedResources).distinct()
        val unregistered = requestedTargets.filter { it !in args.registeredTargets }
        if (requestedTargets.isEmpty() || unregistered.isNotEmpty()) {
            return Err(
                AuthorizationServerError.InvalidTarget(
                    audience = unregistered.joinToString(" "),
                    resource = args.requestedResources.filter { it !in args.registeredTargets }.joinToString(" ").takeIf(String::isNotEmpty),
                    reason = "Requested target is not authorized for this client",
                ),
            )
        }
        return Ok(
            TokenExchangeAuthorization(
                allowed = true,
                isDelegation = args.actor != null,
                grantedScope = downscope(args),
                grantedTargets = requestedTargets,
            ),
        )
    }

    /**
     * When no scope is requested the subject's scope is inherited verbatim; when a scope is
     * requested only the subset the subject already holds is granted, in requested order. A
     * requested scope outside the subject's grant is dropped rather than minted.
     */
    private fun downscope(args: AuthorizeTokenExchangeInput): String? {
        val subjectScope = (args.subject.claims["scope"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val subjectScopes = subjectScope?.split(" ")?.filter(String::isNotBlank)?.toSet().orEmpty()
        val requestedScopes =
            args.requestedScope?.split(" ")?.filter(String::isNotBlank)
                ?: return subjectScope?.takeIf(String::isNotBlank)
        return requestedScopes.filter { it in subjectScopes }.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }
}

/** Standard profiles add no claims to the exchanged token. */
class StandardMapTokenExchangeClaimsCommand : MapTokenExchangeClaimsCommand {
    override suspend fun execute(args: BoundedTokenExchange): IdkResult<TokenExchangeClaimMapping, AuthorizationServerError> =
        Ok(TokenExchangeClaimMapping(JsonObject(emptyMap())))
}

/** Standard profiles add nothing to the current actor beyond the fixed `sub`, `iss` and `client_id`. */
class StandardBuildTokenExchangeActorChainCommand : BuildTokenExchangeActorChainCommand {
    override suspend fun execute(args: TokenExchangeActorChainInput): IdkResult<TokenExchangeActorExtensions, AuthorizationServerError> =
        Ok(TokenExchangeActorExtensions(JsonObject(emptyMap())))
}

/** The IDK token-exchange profile. Enterprise and platform graphs replace it. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<TokenExchangeProfile>())
class StandardTokenExchangeProfile : TokenExchangeProfile {
    override val id: String = PROFILE_ID
    override val authorize: AuthorizeTokenExchangeCommand = StandardAuthorizeTokenExchangeCommand()
    override val mapClaims: MapTokenExchangeClaimsCommand = StandardMapTokenExchangeClaimsCommand()
    override val buildActorChain: BuildTokenExchangeActorChainCommand = StandardBuildTokenExchangeActorChainCommand()

    companion object {
        const val PROFILE_ID = "oauth2.tokenexchange.standard"
    }
}
