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

import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Structural regression guard for the IDK OAuth2 AS HTTP surface.
 *
 * Every contributed [HttpAdapter] (SessionScope) must have a paired
 * [com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider] (AppScope) registered with
 * the same `id`. Without a descriptor the dispatcher catalog never sees the adapter's routes and
 * requests against them return 404 even though the adapter is on the classpath. This bug shipped
 * twice during the OIDF conformance sprint (`OAuth2LoginHttpAdapter`,
 * `OAuth2AttestationHttpAdapter`); the test pins the invariant at build time.
 *
 * The test boots the same Metro graph the harness drives ([OidfOpTestAppGraph]) so any future
 * adapter contributed into the keyed `Map<String, Lazy<HttpAdapter>>`
 * surfaces here regardless of which module owns it.
 */
class OAuth2HttpAdapterParityTest {
    @Test
    fun everyHttpAdapterHasAMatchingDescriptorProvider() {
        val graph = createOidfOpTestAppGraph()
        val sessionGraph =
            graph.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId("http-adapter-parity-test", principalType = com.sphereon.di.context.PrincipalType.USER)
                .graph as HttpAdapterParitySessionGraph
        val adapterIds = sessionGraph.httpAdapters.keys
        val descriptorIds = graph.httpAdapterDescriptorProviders.map { it.id }.toSet()

        assertTrue(adapterIds.isNotEmpty(), "no HttpAdapters contributed to the test graph")
        assertTrue(descriptorIds.isNotEmpty(), "no HttpAdapterDescriptorProviders contributed to the test graph")

        val adaptersWithoutDescriptor = adapterIds - descriptorIds
        val descriptorsWithoutAdapter = descriptorIds - adapterIds
        // Distinct asserts so a parity break shows the offending side directly. Both sides
        // matter: an adapter without a descriptor 404s in the catalog; a descriptor without
        // an adapter advertises a route that no SessionScope handler can fulfil.
        assertEquals(
            emptySet<String>(),
            adaptersWithoutDescriptor,
            "HttpAdapter ids missing a HttpAdapterDescriptorProvider: $adaptersWithoutDescriptor",
        )
        assertEquals(
            emptySet<String>(),
            descriptorsWithoutAdapter,
            "HttpAdapterDescriptorProvider ids missing a contributed HttpAdapter: $descriptorsWithoutAdapter",
        )
        assertEquals(
            adapterIds,
            descriptorIds,
            "HttpAdapter id set must equal HttpAdapterDescriptorProvider id set",
        )
    }
}

/**
 * SessionScope accessor for the keyed, lazy [HttpAdapter] multibinding. Adapters cannot live at
 * AppScope (they read per-tenant config + per-session state), so the parity check pulls them out
 * of a freshly-created session graph and then compares to the AppScope descriptor set.
 */
@ContributesTo(SessionScope::class)
interface HttpAdapterParitySessionGraph {
    val httpAdapters: Map<String, Lazy<HttpAdapter>>
}
