/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.oauth2.server.authorization.impl.testutil

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteDecision
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRoutePlanner
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteRequest

internal class StubAuthenticationRoutePlanner(
    private val decision: (AuthenticationRouteRequest) -> AuthenticationRouteDecision,
) : AuthenticationRoutePlanner {
    override suspend fun decide(request: AuthenticationRouteRequest): IdkResult<AuthenticationRouteDecision, IdkError> =
        Ok(decision(request))

    override suspend fun revalidate(
        decision: AuthenticationRouteDecision,
        selectedBindingId: String,
    ): IdkResult<Unit, IdkError> = Ok(Unit)
}
