/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.iso18013

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.interaction.WalletInteractionClient
import com.sphereon.wallet.interaction.impl.DefaultWalletInteractionEngine
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.createGraphFactory

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletInteractionClient>())
class Iso18013MdocIntegrationWalletInteractionClient : WalletInteractionClient by DefaultWalletInteractionEngine()

@DependencyGraph(AppScope::class)
abstract class Iso18013MdocIntegrationTestAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): Iso18013MdocIntegrationTestAppGraph
    }
}

fun createIso18013MdocIntegrationTestAppGraph(
    application: Any,
    appId: String = "com.sphereon.wallet.interaction.iso18013-test",
    profile: String = "test",
    version: String = "0.1.0-test",
): Iso18013MdocIntegrationTestAppGraph {
    val graph =
        createGraphFactory<Iso18013MdocIntegrationTestAppGraph.Factory>().create(
            application = application,
            appId = appId,
            profile = profile,
            version = version,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
