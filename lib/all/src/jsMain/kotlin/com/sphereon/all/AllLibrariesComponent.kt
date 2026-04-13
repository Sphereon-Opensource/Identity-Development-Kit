/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.libraries.all

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppComponent
import com.sphereon.di.app.RootScopeProvider

/**
 * Pre-generated AllLibraries AppComponent that merges all library modules.
 *
 * This component is intended for external developers who do not want to use KSP
 * to build their own components. It provides a ready-to-use dependency injection
 * setup that includes all library modules in the project.
 *
 * This class is a singleton and provides all bindings from all libraries
 * through the [MergeComponent] annotation.
 */
@DependencyGraph(AppScope::class)
abstract class AllLibrariesAppComponent : AbstractAppComponent() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): AllLibrariesAppComponent
    }
}

fun createAllLibrariesAppComponent(
    application: Any,
    appId: String = "all-libraries-app",
    profile: String = "profile",
    version: String = "version-example",
): AllLibrariesAppComponent {
    val component = createGraphFactory<AllLibrariesAppComponent.Factory>().create(
        application = application,
        appId = appId,
        profile = profile,
        version = version,
        rootScopeProvider = DefaultRootScopeProvider()
    )
    component.initRootScopeProvider()
    return component
}
