package com.sphereon.oauth2.server.authorization.impl.test

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppComponent
import com.sphereon.di.app.RootScopeProvider

/**
 * wasmJs test implementation for [AbstractAppComponent].
 */
@DependencyGraph(AppScope::class)
abstract class WasmJsOAuth2ServerTestAppComponent : AbstractAppComponent() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): WasmJsOAuth2ServerTestAppComponent
    }
}

fun createWasmJsOAuth2ServerTestAppComponent(
    application: Any,
    appId: String = "oauth2-server-test",
    profile: String = "console-log-profile",
    version: String = "test-version",
): WasmJsOAuth2ServerTestAppComponent {
    val component = createGraphFactory<WasmJsOAuth2ServerTestAppComponent.Factory>().create(
        application = application,
        appId = appId,
        profile = profile,
        version = version,
        rootScopeProvider = DefaultRootScopeProvider()
    )
    component.initRootScopeProvider()
    return component
}
