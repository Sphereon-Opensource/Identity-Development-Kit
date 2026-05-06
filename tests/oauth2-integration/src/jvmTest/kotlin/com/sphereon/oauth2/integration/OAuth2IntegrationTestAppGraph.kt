/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.integration

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.RootScopeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

/**
 * App graph for the OP+RS+RP integration tests. Metro merges every `@ContributesBinding` on the
 * classpath into a single graph — so one app carries the OP (lib-oauth2-server-authorization-impl
 * + services-oauth2-as-rest), the RS (lib-oauth2-server-resource-impl), and the RP
 * (lib-oauth2-client-impl), plus the software KMS provider. That's the shape of a realistic
 * "all-in-one" dev deployment and lets a test drive the full protocol loop in-memory.
 */
@DependencyGraph(AppScope::class)
abstract class OAuth2IntegrationTestAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): OAuth2IntegrationTestAppGraph
    }
}

fun createOAuth2IntegrationTestAppGraph(
    application: Any = "OAuth2IntegrationTest",
    appId: String = "com.sphereon.oauth2.integration.test",
    profile: String = "test",
    version: String = "test",
): OAuth2IntegrationTestAppGraph {
    val graph =
        createGraphFactory<OAuth2IntegrationTestAppGraph.Factory>().create(
            application = application,
            appId = appId,
            profile = profile,
            version = version,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
