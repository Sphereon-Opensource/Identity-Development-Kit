package com.sphereon.identity.reconciliation.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppComponent
import com.sphereon.di.app.RootScopeProvider

/**
 * Native test implementation for [AbstractAppComponent].
 */
@DependencyGraph(AppScope::class)
abstract class NativeReconciliationTestAppComponent : AbstractAppComponent() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): NativeReconciliationTestAppComponent
    }
}

fun createNativeReconciliationTestAppComponent(
    application: Any,
    appId: String = "reconciliation-test",
    profile: String = "console-log-profile",
    version: String = "test-version",
): NativeReconciliationTestAppComponent {
    val component = createGraphFactory<NativeReconciliationTestAppComponent.Factory>().create(
        application = application,
        appId = appId,
        profile = profile,
        version = version,
        rootScopeProvider = DefaultRootScopeProvider()
    )
    component.initRootScopeProvider()
    return component
}
