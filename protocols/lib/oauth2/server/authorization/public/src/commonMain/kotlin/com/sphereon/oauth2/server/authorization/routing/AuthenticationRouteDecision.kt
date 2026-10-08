/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.oauth2.server.authorization.routing

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.Serializable

/**
 * Immutable authentication route bound to one downstream authorization transaction.
 * Upstream options are exact binding identifiers, never provider aliases or row-order guesses.
 */
@Serializable
data class AuthenticationRouteDecision(
    val route: AuthenticationRoute,
    val hostedAuthorizationServerId: String,
    val hostedAuthorizationServerRevision: Long,
    val localLoginAllowed: Boolean,
    val eligibleBindings: List<AuthenticationRouteBinding> = emptyList(),
    val selectedBindingId: String? = null,
) {
    init {
        require(hostedAuthorizationServerId.isNotBlank()) { "Hosted authorization-server id is required" }
        require(hostedAuthorizationServerRevision >= 0) { "Hosted authorization-server revision cannot be negative" }
        require(eligibleBindings.map { it.bindingId }.distinct().size == eligibleBindings.size) {
            "Authentication route contains duplicate federation bindings"
        }
        when (route) {
            AuthenticationRoute.UPSTREAM_REDIRECT -> require(selectedBindingId != null && eligibleBindings.any { it.bindingId == selectedBindingId }) {
                "Upstream redirect requires an exact eligible binding selection"
            }
            AuthenticationRoute.LOCAL_LOGIN -> require(localLoginAllowed && selectedBindingId == null && eligibleBindings.isEmpty()) {
                "Local-only routing cannot carry upstream bindings"
            }
            AuthenticationRoute.CHOOSER -> require(selectedBindingId == null && eligibleBindings.isNotEmpty()) {
                "Chooser routing requires eligible bindings and no implicit selection"
            }
        }
    }
}

@Serializable
enum class AuthenticationRoute { LOCAL_LOGIN, UPSTREAM_REDIRECT, CHOOSER }

@Serializable
data class AuthenticationRouteBinding(
    val bindingId: String,
    val upstreamResourceId: String,
    val displayName: String,
    val upstreamIssuer: String,
    val bindingRevision: Long,
    val upstreamResourceRevision: Long,
    val claimsMapping: Map<String, String>,
) {
    init {
        require(bindingId.isNotBlank()) { "Federation binding id is required" }
        require(upstreamResourceId.isNotBlank()) { "Upstream resource id is required" }
        require(displayName.isNotBlank()) { "Federation binding display name is required" }
        require(upstreamIssuer.isNotBlank()) { "Upstream issuer is required" }
        require(bindingRevision >= 0 && upstreamResourceRevision >= 0) { "Federation route revisions cannot be negative" }
        require(claimsMapping.all { (source, target) -> source.isNotBlank() && target.isNotBlank() }) { "Claim mappings cannot contain blanks" }
        require(claimsMapping.values.toSet().size == claimsMapping.size) {
            "Claim mappings cannot contain duplicate governed targets"
        }
    }
}

data class AuthenticationRouteRequest(
    val downstreamSessionId: String,
    val clientId: String,
    val requestedBindingId: String? = null,
    val requestedAcrValues: List<String> = emptyList(),
    val loginHint: String? = null,
)

/** Product/runtime boundary that makes the route decision from durable resource authority. */
interface AuthenticationRoutePlanner {
    suspend fun decide(request: AuthenticationRouteRequest): IdkResult<AuthenticationRouteDecision, IdkError>

    /** Revalidates all pinned revisions and ownership immediately before an upstream callback is accepted. */
    suspend fun revalidate(decision: AuthenticationRouteDecision, selectedBindingId: String): IdkResult<Unit, IdkError>
}

const val SESSION_KEY_AUTHENTICATION_ROUTE: String = "oauth2.authentication.route"
