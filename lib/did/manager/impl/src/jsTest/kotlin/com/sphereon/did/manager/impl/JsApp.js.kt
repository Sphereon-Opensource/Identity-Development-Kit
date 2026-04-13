package com.sphereon.did.manager.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppComponent
import com.sphereon.di.app.RootScopeProvider

/**
 * JS test implementation for [AbstractAppComponent].
 */
@DependencyGraph(AppScope::class)
abstract class JsDidManagerTestAppComponent : AbstractAppComponent() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): JsDidManagerTestAppComponent
    }
}

fun createJsDidManagerTestAppComponent(
    application: Any,
    appId: String = "did-manager-test",
    profile: String = "test",
    version: String = "0.13.0-test",
): JsDidManagerTestAppComponent {
    val component = createGraphFactory<JsDidManagerTestAppComponent.Factory>().create(
        application = application,
        appId = appId,
        profile = profile,
        version = version,
        rootScopeProvider = DefaultRootScopeProvider()
    )
    component.initRootScopeProvider()
    return component
}
