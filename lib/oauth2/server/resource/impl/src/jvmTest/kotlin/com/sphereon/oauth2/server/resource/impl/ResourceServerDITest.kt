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
 */

package com.sphereon.oauth2.server.resource.impl

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppComponent
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.oauth2.server.resource.impl.cache.InMemoryDpopNonceCacheImpl
import com.sphereon.oauth2.server.resource.impl.cache.InMemoryTokenCacheImpl
import com.sphereon.oauth2.server.resource.impl.service.ResourceServerServiceImpl
import com.sphereon.oauth2.server.resource.service.ResourceServerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Full E2E integration tests for OAuth2 Resource Server using proper DI.
 *
 * These tests use the kotlin-inject DI container to wire up the complete
 * resource server with real implementations, proving that:
 * 1. The DI setup works correctly with @DependencyGraph
 * 2. All commands are properly contributed via @ContributesBinding
 * 3. The service implementation is correctly bound
 * 4. Cache implementations are registered and accessible
 *
 */
class ResourceServerDITest {

    private lateinit var appComponent: ResourceServerTestAppComponent
    private lateinit var sessionInstance: com.sphereon.di.session.SessionInstance

    @BeforeTest
    fun setup() {
        runBlocking {
            // Create DI component hierarchy through proper context managers
            appComponent = createResourceServerTestAppComponent()

            // Create anonymous user context and session (like authorization server tests)
            val userInstance = appComponent.userContextManager.getAnonymous()
            sessionInstance = userInstance.getOrCreateAnonymousSession()
        }
    }

    @Test
    fun `DI - ResourceServerService is available and properly wired`() = runTest {
        // Get ResourceServerService from session component via Component interface
        val serviceComponent = sessionInstance.component as ResourceServerServiceImpl.Component
        val service = serviceComponent.resourceServerService

        assertNotNull(service, "ResourceServerService should be injected via DI")
        assertTrue(
            service is ResourceServerServiceImpl,
            "ResourceServerService should be ResourceServerServiceImpl"
        )
    }

    @Test
    fun `DI - All commands are available and properly injected`() = runTest {
        val serviceComponent = sessionInstance.component as ResourceServerServiceImpl.Component
        val service = serviceComponent.resourceServerService

        // Verify all commands are injected
        assertNotNull(service.validateAccessTokenCommand, "ValidateAccessTokenCommand should be injected")
        assertNotNull(service.verifyJwtCommand, "VerifyJwtCommand should be injected")
        assertNotNull(service.introspectTokenCommand, "IntrospectTokenCommand should be injected")
        assertNotNull(service.verifyDpopProofCommand, "VerifyDpopProofCommand should be injected")

        // Verify correct implementations are contributed via @ContributesBinding
        assertTrue(
            service.validateAccessTokenCommand is com.sphereon.oauth2.server.resource.impl.command.ValidateAccessTokenCommandImpl,
            "ValidateAccessTokenCommand should be ValidateAccessTokenCommandImpl via @ContributesBinding"
        )
        assertTrue(
            service.verifyJwtCommand is com.sphereon.oauth2.server.resource.impl.command.VerifyJwtCommandImpl,
            "VerifyJwtCommand should be VerifyJwtCommandImpl via @ContributesBinding"
        )
        assertTrue(
            service.verifyDpopProofCommand is com.sphereon.oauth2.server.resource.impl.command.ResourceServerVerifyDpopProofCommandImpl,
            "VerifyDpopProofCommand should be ResourceServerVerifyDpopProofCommandImpl via @ContributesBinding"
        )
    }

    @Test
    fun `DI - Cache implementations are available via Component interfaces`() = runTest {
        // Get caches from app component via Component interfaces (same pattern as authorization server)
        val tokenCacheComponent = appComponent as InMemoryTokenCacheImpl.Component
        val dpopCacheComponent = appComponent as InMemoryDpopNonceCacheImpl.Component

        val tokenCache = tokenCacheComponent.tokenCache
        val dpopCache = dpopCacheComponent.dpopNonceCache

        assertNotNull(tokenCache, "TokenCache should be injected")
        assertNotNull(dpopCache, "DpopNonceCache should be injected")

        assertTrue(
            tokenCache is InMemoryTokenCacheImpl,
            "TokenCache should be InMemoryTokenCacheImpl via @ContributesBinding"
        )
        assertTrue(
            dpopCache is InMemoryDpopNonceCacheImpl,
            "DpopNonceCache should be InMemoryDpopNonceCacheImpl via @ContributesBinding"
        )
    }
}

/**
 * Test DI component hierarchy for resource server tests
 * Uses @DependencyGraph to merge all contributed bindings from the resource server impl module
 */
@DependencyGraph(AppScope::class)
abstract class ResourceServerTestAppComponent : AbstractAppComponent() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): ResourceServerTestAppComponent
    }
}

fun createResourceServerTestAppComponent(
    application: Any = "ResourceServerTest",
    appId: String = "com.sphereon.oauth2.rs.test",
    profile: String = "test",
    version: String = "1.0.0",
): ResourceServerTestAppComponent {
    val component = createGraphFactory<ResourceServerTestAppComponent.Factory>().create(
        application = application,
        appId = appId,
        profile = profile,
        version = version,
        rootScopeProvider = DefaultRootScopeProvider()
    )
    component.initRootScopeProvider()
    return component
}
