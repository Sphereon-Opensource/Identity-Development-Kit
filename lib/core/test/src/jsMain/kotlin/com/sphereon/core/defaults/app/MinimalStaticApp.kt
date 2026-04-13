package com.sphereon.core.defaults.app

import com.sphereon.di.app.AbstractAppComponent
import com.sphereon.di.app.RootScopeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

@DependencyGraph(AppScope::class)
abstract class MinimalStaticJsAppComponent : AbstractAppComponent() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("version") version: String,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): MinimalStaticJsAppComponent
    }
}

fun createMinimalStaticJsAppComponent(
    application: Any, appId: String, profile: String, version: String,
): MinimalStaticJsAppComponent {
    val component = createGraphFactory<MinimalStaticJsAppComponent.Factory>().create(
        application = application, version = version, appId = appId, profile = profile,
        rootScopeProvider = DefaultRootScopeProvider()
    )
    component.initRootScopeProvider()
    return component
}
