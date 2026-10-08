/* Copyright 2026 Sphereon International B.V. */
package com.sphereon.mdoc.testutil

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.di.app.RootScopeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

@DependencyGraph(AppScope::class)
internal abstract class MdocCryptoTestAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): MdocCryptoTestAppGraph
    }
}

internal actual fun createMdocCryptoTestAppGraph(application: Any): AppGraph =
    createGraphFactory<MdocCryptoTestAppGraph.Factory>().create(
        application = application,
        appId = "mdoc-crypto-regression",
        profile = "test",
        version = "1.0.0-test",
        rootScopeProvider = DefaultRootScopeProvider(),
    ).also { it.initRootScopeProvider() }
