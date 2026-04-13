/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.did.manager.impl

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.RootScopeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

/**
 * Test app graph for DID manager E2E testing.
 *
 * This graph merges all DI contributions from:
 * - lib-did-manager-impl (DidManager, DidCreationDslProcessor)
 * - lib-did-methods-key (KeyDidProvider, KeyDidResolver)
 * - lib-did-methods-jwk (JwkDidProvider, JwkDidResolver)
 * - lib-did-persistence-memory (InMemoryDidRepository)
 * - lib-crypto-kms-provider-software (SoftwareKeyManagerProvider)
 *
 * The DID method providers and resolvers are automatically discovered via multibinding.
 */
@DependencyGraph(AppScope::class)
abstract class DidManagerTestAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): DidManagerTestAppGraph
    }
}

fun createDidManagerTestAppGraph(
    application: Any,
    appId: String = "did-manager-test",
    profile: String = "test",
    version: String = "0.13.0-test",
): DidManagerTestAppGraph {
    val graph =
        createGraphFactory<DidManagerTestAppGraph.Factory>().create(
            application = application,
            appId = appId,
            profile = profile,
            version = version,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
