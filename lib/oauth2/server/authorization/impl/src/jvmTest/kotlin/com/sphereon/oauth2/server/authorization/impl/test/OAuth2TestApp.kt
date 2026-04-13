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

package com.sphereon.oauth2.server.authorization.impl.test

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppComponent
import com.sphereon.di.app.RootScopeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import com.sphereon.di.session.SessionScope
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult

/**
 * Test-specific override: use opaque tokens (no signing key required).
 * Replaces the jvmMain DefaultOAuth2ConfigModule which tries to auto-create a signing key.
 */
@ContributesTo(SessionScope::class, replaces = [com.sphereon.oauth2.server.authorization.impl.config.DefaultOAuth2ConfigModule::class])
interface TestOAuth2ConfigModule {
    @Provides
    @SingleIn(SessionScope::class)
    @Named("oauth2.serverIdentifier")
    fun provideTestServerIdentifier(): ManagedIdentifierOptsOrResult? = null
}

@DependencyGraph(AppScope::class)
abstract class OAuth2TestAppComponent : AbstractAppComponent() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): OAuth2TestAppComponent
    }
}

fun createOAuth2TestAppComponent(
    application: Any = "OAuth2Test",
    appId: String = "com.sphereon.oauth2.test",
    profile: String = "test",
    version: String = "1.0.0",
): OAuth2TestAppComponent {
    val component = createGraphFactory<OAuth2TestAppComponent.Factory>().create(
        application = application,
        appId = appId,
        profile = profile,
        version = version,
        rootScopeProvider = DefaultRootScopeProvider()
    )
    component.initRootScopeProvider()
    return component
}
