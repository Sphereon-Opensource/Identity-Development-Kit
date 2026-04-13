package com.sphereon.trust.core.testutil

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppComponent
import com.sphereon.di.app.AppComponent
import com.sphereon.di.app.RootScopeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

@DependencyGraph(AppScope::class)
abstract class WasmJsTrustTestAppComponent : AbstractAppComponent() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): WasmJsTrustTestAppComponent
    }
}

fun createWasmJsTrustTestAppComponent(
    application: Any,
    appId: String = "trust-test",
    profile: String = "console-log-profile",
    version: String = "version-example",
): WasmJsTrustTestAppComponent {
    val component = createGraphFactory<WasmJsTrustTestAppComponent.Factory>().create(
        application = application,
        appId = appId,
        profile = profile,
        version = version,
        rootScopeProvider = DefaultRootScopeProvider()
    )
    component.initRootScopeProvider()
    return component
}

actual fun createTrustTestAppComponent(testInstance: Any): AppComponent {
    return createWasmJsTrustTestAppComponent(testInstance)
}
