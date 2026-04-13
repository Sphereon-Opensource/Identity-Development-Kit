package com.sphereon.core.api.di

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppComponent
import com.sphereon.di.app.RootScopeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

@DependencyGraph(AppScope::class)
abstract class CoreApiTestAppComponent : AbstractAppComponent() {

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): CoreApiTestAppComponent
    }

}

private const val APP_ID = "core-api-test"
private const val PROFILE = "test"
private const val VERSION = "0.0.1-TEST"

fun createCoreApiTestAppComponent(
    application: Any,
    appId: String = APP_ID,
    profile: String = PROFILE,
    version: String = VERSION,
): CoreApiTestAppComponent {
    val component = createGraphFactory<CoreApiTestAppComponent.Factory>().create(
        application = application,
        appId = appId,
        profile = profile,
        version = version,
        rootScopeProvider = DefaultRootScopeProvider()
    )
    component.initRootScopeProvider()
    return component
}
