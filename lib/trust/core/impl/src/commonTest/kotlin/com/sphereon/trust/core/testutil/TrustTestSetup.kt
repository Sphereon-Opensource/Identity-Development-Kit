/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.testutil

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.core.defaults.service.SimpleSessionScopedCommandRegistryComponent
import com.sphereon.di.app.AppComponent
import com.sphereon.di.session.SessionInstance

expect fun createTrustTestAppComponent(testInstance: Any): AppComponent

class TrustTestContext(sessionId: String, testInstance: Any) {
    val app: AppComponent = createTrustTestAppComponent(testInstance)
    private val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId)

    val commandRegistry: SessionScopedCommandRegistry
        get() = (session.component as SimpleSessionScopedCommandRegistryComponent).sessionScopedCommandRegistry
}
