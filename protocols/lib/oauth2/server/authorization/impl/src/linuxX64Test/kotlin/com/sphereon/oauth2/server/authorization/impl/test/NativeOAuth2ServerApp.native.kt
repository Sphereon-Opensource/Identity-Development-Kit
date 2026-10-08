/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.test

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.impl.config.DefaultAsServerSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.signing.AsServerSigningIdentifierResolver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory

/**
 * Provides a null-resolving AS signing identifier for native tests.
 * Replaces [DefaultAsServerSigningIdentifierResolver], which would otherwise auto-seed one.
 */
@ContributesTo(SessionScope::class, replaces = [DefaultAsServerSigningIdentifierResolver::class])
interface NativeTestOAuth2ServerIdentifierModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideSigningIdentifierResolver(): AsServerSigningIdentifierResolver =
        object : AsServerSigningIdentifierResolver {
            override suspend fun resolveSigningIdentifier(): ManagedIdentifierOptsOrResult? = null
        }
}

/**
 * Native test implementation for [AbstractAppGraph].
 */
@DependencyGraph(AppScope::class)
abstract class NativeOAuth2ServerTestAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): NativeOAuth2ServerTestAppGraph
    }
}

fun createNativeOAuth2ServerTestAppGraph(
    application: Any,
    appId: String = "oauth2-server-test",
    profile: String = "console-log-profile",
    version: String = "test-version",
): NativeOAuth2ServerTestAppGraph {
    val graph =
        createGraphFactory<NativeOAuth2ServerTestAppGraph.Factory>().create(
            application = application,
            appId = appId,
            profile = profile,
            version = version,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
