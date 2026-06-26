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

package com.sphereon.oauth2.oidf.op

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

/**
 * App graph for the OIDF conformance OP harness. Metro merges every `@ContributesBinding` on the
 * classpath into one graph, so this single app carries the full OAuth2 AS surface area:
 * `lib-oauth2-server-authorization-impl` for the protocol logic, `services-oauth2-as-rest` for
 * the HTTP adapters, the software KMS provider for in-process signing keys, and the
 * config-backed [com.sphereon.oauth2.server.authorization.impl.provider.ConfigBackedUserAuthenticationProvider]
 * keyed `local-config` for seeded users.
 *
 * The harness is IDK-only: no EDK / VDX modules are on the classpath, and the build's
 * `checkIdkPurity` task enforces that.
 */
@DependencyGraph(AppScope::class)
abstract class OidfOpAppGraph : AbstractAppGraph() {
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
        ): OidfOpAppGraph
    }
}

fun createOidfOpAppGraph(
    application: Any = "OidfOpHarness",
    appId: String = "com.sphereon.oauth2.oidf.op",
    profile: String = "oidf-op",
    version: String = "1.0.0",
): OidfOpAppGraph {
    val graph =
        createGraphFactory<OidfOpAppGraph.Factory>().create(
            application = application,
            appId = appId,
            profile = profile,
            version = version,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
