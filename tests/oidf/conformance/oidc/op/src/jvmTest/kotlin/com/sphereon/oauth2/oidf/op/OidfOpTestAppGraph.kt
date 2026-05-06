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

import com.sphereon.core.api.codec.StreamingCodec
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.IPropertyValueConversion
import com.sphereon.core.api.http.codec.HttpBodyCodec
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.log.Logger
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.core.events.EventService
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

/**
 * Test-scope app graph that mirrors [OidfOpAppGraph] but is compiled in jvmTest so Metro picks
 * up the test-only [TestClockModule] (which replaces the production [com.sphereon.core.defaults.time.ClockModule]).
 * Tests use this instead of the production graph so they can drive virtual time through
 * [TestClock] and skip `Thread.sleep` for OIDC `max_age` / staleness assertions.
 *
 * Accessor [testClock] returns the AppScope-bound shared instance for the test fixture to expose.
 */
@DependencyGraph(AppScope::class)
abstract class OidfOpTestAppGraph : AbstractAppGraph() {
    abstract val testClock: TestClock
    abstract val httpBodyCodecs: Set<HttpBodyCodec>
    abstract val loggers: Set<Logger>
    abstract val eventServices: Set<EventService>
    abstract val streamingCodecs: Set<StreamingCodec>
    abstract val configServices: Set<ConfigService>
    abstract val propertyValueConversions: Set<IPropertyValueConversion<*>>

    /**
     * AppScope-bound `HttpAdapterDescriptorProvider` multibinding. Adapters live at SessionScope
     * (where their per-request state hangs off the session graph), so to assert parity between
     * adapters and descriptors the harness pulls adapters from `session.graph` and descriptors
     * from this AppScope set. See [com.sphereon.oauth2.oidf.op.OAuth2HttpAdapterParityTest].
     */
    abstract val httpAdapterDescriptorProviders: Set<HttpAdapterDescriptorProvider>

    /**
     * AppScope-bound [DeviceAuthorizationStorage] (the IDK in-memory implementation). Exposed so
     * the device-flow OIDF tests can either inject pending records directly or transition records
     * to APPROVED / DENIED without driving the full HTML approval form, mirroring how DPoP / JARM
     * tests reach into the AppScope KMS for sign-time helpers.
     */
    abstract val deviceAuthorizationStorage: DeviceAuthorizationStorage

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): OidfOpTestAppGraph
    }
}

fun createOidfOpTestAppGraph(
    application: Any = "OidfOpHarnessTest",
    appId: String = "com.sphereon.oauth2.oidf.op",
    profile: String = "oidf-op",
    version: String = "1.0.0",
): OidfOpTestAppGraph {
    val graph =
        createGraphFactory<OidfOpTestAppGraph.Factory>().create(
            application = application,
            appId = appId,
            profile = profile,
            version = version,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
