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

package com.sphereon.conf.settings

import com.sphereon.core.api.conf.DefaultInterpolationPolicyProvider
import com.sphereon.core.api.conf.DefaultPropertyInterpolator
import com.sphereon.core.api.conf.InterpolationPolicy
import com.sphereon.core.api.conf.InterpolationPolicyCatalog
import com.sphereon.core.api.conf.InterpolationPolicyProvider
import com.sphereon.core.api.conf.PropertyInterpolator
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.core.defaults.conf.ConfigPipelineModule
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.RootScopeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory

/**
 * Test product catalog. The production settings property sources consume the core configuration
 * graph's policy; they do not create an ambient resolver or a second catalog.
 */
@ContributesTo(AppScope::class, replaces = [ConfigPipelineModule::class])
interface SettingsTestConfigPipelineModule {
    @Provides
    @SingleIn(AppScope::class)
    fun providePropertyInterpolator(): PropertyInterpolator = DefaultPropertyInterpolator()

    @Provides
    fun provideNullablePropertyInterpolator(interpolator: PropertyInterpolator): PropertyInterpolator? = interpolator

    @Provides
    @SingleIn(AppScope::class)
    fun provideInterpolationPolicyCatalog(): InterpolationPolicyCatalog =
        InterpolationPolicyCatalog(
            exactPolicies =
                mapOf(
                    "users.endpoint" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY,
                    "api.endpoint" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY,
                    "env.ref" to InterpolationPolicy.APP_ENVIRONMENT,
                    "env.missing" to InterpolationPolicy.APP_ENVIRONMENT,
                    "complex.value" to InterpolationPolicy.APP_ENVIRONMENT,
                ),
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideInterpolationPolicyProvider(catalog: InterpolationPolicyCatalog): InterpolationPolicyProvider =
        DefaultInterpolationPolicyProvider(catalog)
}

@DependencyGraph(AppScope::class)
abstract class JvmMPSettingsAppGraph : AbstractAppGraph() {
    abstract val interpolationPolicyCatalog: InterpolationPolicyCatalog

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): JvmMPSettingsAppGraph
    }
}

fun createJvmMPSettingsAppGraph(
    application: Any,
    appId: String = application.javaClass.name ?: "<unknown>",
    profile: String = "mp-settings-profile",
    version: String = "version-example",
): JvmMPSettingsAppGraph {
    val graph =
        createGraphFactory<JvmMPSettingsAppGraph.Factory>().create(
            application = application,
            appId = appId,
            profile = profile,
            version = version,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
