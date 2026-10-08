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

package com.sphereon

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.di.app.RootScopeProvider

/**
 * Android implementation for [AbstractAppGraph] and provides the package name as application
 * ID as well as the application and its context.
 *
 * This class is a singleton and automatically provided in the dependency graph whenever you
 * inject [AbstractAppGraph] through the [ContributesBinding] annotation.
 */

@DependencyGraph(AppScope::class)
abstract class AndroidUnitTestAppGraph : AbstractAppGraph(), AndroidUnitAppGraph {
    init {
        require(application is Context) { "The application provided must be of type ${Context::class.java.simpleName}" }

        // NOTE: KMS provider configuration should be done BEFORE creating the graph
        // See ReaderHolderIntegrationTest for an example of configuring properties in companion object
    }
    override fun getContext(): Context = application as Context

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): AndroidUnitTestAppGraph
    }
}


interface AndroidUnitAppGraph: AppGraph {
    fun getContext(): Context
}

fun createAndroidUnitTestAppGraph(
    application: Any,
    appId: String = application.javaClass.name ?: "<unknown>",
    profile: String = "console-log-profile",
    version: String = "version-example",
): AndroidUnitTestAppGraph {
    val graph = createGraphFactory<AndroidUnitTestAppGraph.Factory>().create(
        application = application,
        appId = appId,
        profile = profile,
        version = version,
        rootScopeProvider = DefaultRootScopeProvider()
    )
    graph.initRootScopeProvider()
    return graph
}
