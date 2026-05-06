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

import com.sphereon.core.api.codec.StreamingCodec
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.IPropertyValueConversion
import com.sphereon.core.api.http.codec.HttpBodyCodec
import com.sphereon.core.api.log.Logger
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.core.events.EventService
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.RootScopeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

/**
 * JVM-only AppScope graph used by tests that need a full IDK runtime composition (file system,
 * KMS, etc.). Mirrors [OAuth2TestAppGraph] but stays platform-specific because some bindings only
 * resolve on JVM. Accessors below pin the mass-contributed app-scope sets so Metro doesn't flag
 * them as unused.
 */
@DependencyGraph(AppScope::class)
abstract class JvmOAuth2ServerTestAppGraph : AbstractAppGraph() {
    abstract val httpBodyCodecs: Set<HttpBodyCodec>
    abstract val loggers: Set<Logger>
    abstract val eventServices: Set<EventService>
    abstract val streamingCodecs: Set<StreamingCodec>
    abstract val configServices: Set<ConfigService>
    abstract val propertyValueConversions: Set<IPropertyValueConversion<*>>

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): JvmOAuth2ServerTestAppGraph
    }
}

fun createJvmOAuth2ServerTestAppGraph(
    application: Any,
    appId: String = "oauth2-server-test",
    profile: String = "test",
    version: String = "test-version",
): JvmOAuth2ServerTestAppGraph {
    val graph =
        createGraphFactory<JvmOAuth2ServerTestAppGraph.Factory>().create(
            application = application,
            appId = appId,
            profile = profile,
            version = version,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
