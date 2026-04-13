/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.testutil

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppComponent
import com.sphereon.di.app.AppComponent
import com.sphereon.di.context.UserScope
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
abstract class NativeTrustTestAppComponent(
    application: Any,
    appId: String = "test",
    profile: String = "console-log-profile",
    version: String = "version-example",
) : NativeTrustTestAppComponentMerged,
    AbstractAppComponent(application, appId = appId, profile = profile, version = version, rootScopeProvider = DefaultRootScopeProvider())

@SingleIn(UserScope::class)
@DependencyGraph(UserScope::class)
abstract class NativeTrustTestContextComponent(
    @DependencyGraph val appComponent: NativeTrustTestAppComponent
) : NativeTrustTestContextComponentMerged

@DependencyGraph(SessionScope::class)
@SingleIn(SessionScope::class)
@DependencyGraph
abstract class NativeTrustTestSessionComponent(
    @DependencyGraph val appComponent: NativeTrustTestAppComponent,
    @DependencyGraph val contextComponent: NativeTrustTestContextComponent
) : NativeTrustTestSessionComponentMerged

actual fun createTrustTestAppComponent(testInstance: Any): AppComponent {
    return createNativeTrustTestAppComponent("test")
}
