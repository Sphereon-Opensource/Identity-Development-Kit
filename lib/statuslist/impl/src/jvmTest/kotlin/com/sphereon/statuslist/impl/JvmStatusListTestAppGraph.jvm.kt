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
 */

package com.sphereon.statuslist.impl

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.did.manager.DidProviderRegistry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

/**
 * Minimal merged app graph for the status-list real-crypto e2e. Metro auto-discovers every
 * `@ContributesBinding` on the test classpath (crypto JwtService + KMS + the status-list bindings),
 * so the e2e can obtain a genuine [com.sphereon.crypto.jose.jws.JwtService] and signing key.
 */
@DependencyGraph(AppScope::class)
abstract class JvmStatusListTestAppGraph : AbstractAppGraph() {
    // The status-list signer can resolve DID `kid`s; these crypto-only e2e tests don't exercise a
    // `did:` signing mode, so a no-op registry satisfies the graph without pulling in DID providers.
    @Provides
    fun provideDidProviderRegistry(): DidProviderRegistry = NoopDidProviderRegistry

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): JvmStatusListTestAppGraph
    }
}

fun createJvmStatusListTestAppGraph(
    application: Any,
    appId: String = application.javaClass.name ?: "<unknown>",
    profile: String = "console-log-profile",
    version: String = "version-example",
): JvmStatusListTestAppGraph {
    val graph =
        createGraphFactory<JvmStatusListTestAppGraph.Factory>().create(
            application = application,
            appId = appId,
            profile = profile,
            version = version,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
