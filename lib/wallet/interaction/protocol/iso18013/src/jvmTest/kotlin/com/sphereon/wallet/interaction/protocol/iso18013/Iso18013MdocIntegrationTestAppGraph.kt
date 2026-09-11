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
import com.sphereon.wallet.interaction.WalletCounterpartyEncounterRegistry
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputAuthority
import com.sphereon.wallet.interaction.impl.DefaultWalletInteractionEngine
import com.sphereon.wallet.interaction.impl.InMemoryWalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.impl.InMemoryWalletInteractionSessionStore
import com.sphereon.wallet.interaction.impl.LocalWalletInteractionClient
import com.sphereon.wallet.interaction.impl.WalletInteractionSessionStore
import com.sphereon.wallet.wscd.SoftwareWscdKeyStoreConfiguration
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory

@ContributesTo(SessionScope::class)
interface Iso18013MdocIntegrationWalletInteractionBindings {
    @Provides
    @SingleIn(SessionScope::class)
    fun counterpartyEncounterRegistry(): WalletCounterpartyEncounterRegistry =
        WalletCounterpartyEncounterRegistry.none

    @Provides
    @SingleIn(SessionScope::class)
    fun privateSessionStore(): WalletInteractionPrivateSessionStore = InMemoryWalletInteractionPrivateSessionStore()

    @Provides
    @SingleIn(SessionScope::class)
    fun sessionStore(): WalletInteractionSessionStore = InMemoryWalletInteractionSessionStore()

    @Provides
    @SingleIn(SessionScope::class)
    fun sensitiveInputAuthority(): WalletInteractionSensitiveInputAuthority = Iso18013TestSensitiveInputAuthority

    @Provides
    @SingleIn(SessionScope::class)
    fun engine(
        privateSessionStore: WalletInteractionPrivateSessionStore,
        sessionStore: WalletInteractionSessionStore,
        sensitiveInputAuthority: WalletInteractionSensitiveInputAuthority,
    ): DefaultWalletInteractionEngine =
        DefaultWalletInteractionEngine(
            sensitiveInputAuthority = sensitiveInputAuthority,
            privateSessionStore = privateSessionStore,
            sessionStore = sessionStore,
        )

    @Provides
    @SingleIn(SessionScope::class)
    fun client(engine: DefaultWalletInteractionEngine): WalletInteractionClient = LocalWalletInteractionClient(engine)
}

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
            @Provides softwareWscdKeyStoreConfiguration: SoftwareWscdKeyStoreConfiguration,
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
            softwareWscdKeyStoreConfiguration = SoftwareWscdKeyStoreConfiguration.InMemoryForTestingOnly,
        )
    graph.initRootScopeProvider()
    return graph
}
