package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.oauth2.jwt.validation.JwtValidationConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory

@DependencyGraph(AppScope::class)
abstract class Oid4vciTestAppGraph : AbstractAppGraph() {
    @Provides
    @SingleIn(AppScope::class)
    fun provideJwtValidationConfig(): JwtValidationConfig = JwtValidationConfig()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): Oid4vciTestAppGraph
    }
}

fun createOid4vciTestAppGraph(
    application: Any = "Oid4vciIntegrationTest",
    appId: String = "com.sphereon.oid4vci.integration.test",
    profile: String = "test",
    version: String = "test",
): Oid4vciTestAppGraph {
    val graph =
        createGraphFactory<Oid4vciTestAppGraph.Factory>().create(
            application = application,
            appId = appId,
            profile = profile,
            version = version,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
