/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.testutil

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionInstance

expect fun createTrustTestAppGraph(testInstance: Any): AppGraph

class TrustTestContext(
    sessionId: String,
    testInstance: Any,
) {
    val app: AppGraph = createTrustTestAppGraph(testInstance)
    private val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId, principalType = com.sphereon.di.context.PrincipalType.USER)

    val commandRegistry: SessionScopedCommandRegistry
        get() = (session.graph as SessionScopedCommandRegistry.Graph).sessionScopedCommandRegistry
}
