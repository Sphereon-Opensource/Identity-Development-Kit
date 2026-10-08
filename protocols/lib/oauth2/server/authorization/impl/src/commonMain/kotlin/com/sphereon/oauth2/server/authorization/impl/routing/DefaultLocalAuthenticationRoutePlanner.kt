/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.oauth2.server.authorization.impl.routing

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRoute
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteDecision
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRoutePlanner
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ExposeImplBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** Pure-IDK policy: first-party login only. Product assemblies replace this with durable routing. */
@Inject
@SingleIn(SessionScope::class)
@ExposeImplBinding
@ContributesBinding(SessionScope::class, binding = binding<AuthenticationRoutePlanner>())
class DefaultLocalAuthenticationRoutePlanner(
    private val asInstanceIdProvider: OAuth2ServerInstanceIdProvider,
) : AuthenticationRoutePlanner {
    override suspend fun decide(request: AuthenticationRouteRequest): IdkResult<AuthenticationRouteDecision, com.sphereon.core.api.error.IdkError> =
        Ok(
            AuthenticationRouteDecision(
                route = AuthenticationRoute.LOCAL_LOGIN,
                hostedAuthorizationServerId = requireNotNull(asInstanceIdProvider.currentAsInstanceId()) {
                    "Authorization-server identity is required for authentication routing"
                },
                hostedAuthorizationServerRevision = 0,
                localLoginAllowed = true,
            ),
        )

    override suspend fun revalidate(
        decision: AuthenticationRouteDecision,
        selectedBindingId: String,
    ): IdkResult<Unit, com.sphereon.core.api.error.IdkError> = Ok(Unit)
}
