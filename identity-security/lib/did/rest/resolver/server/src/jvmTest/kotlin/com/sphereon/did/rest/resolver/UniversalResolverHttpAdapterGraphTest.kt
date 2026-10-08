/*
 * Â© 2026 Sphereon International B.V.
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

package com.sphereon.did.rest.resolver

import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandRegistry
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteMatch
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.session.EmptyInterceptorChain
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class UniversalResolverHttpAdapterGraphTest {
    @Test
    fun concreteAccessorUsesTheCanonicalLazyMapInstance() {
        val adapter = UniversalResolverHttpAdapter(unusedExecution(), unusedRegistry)
        var creations = 0
        val selected = lazy<HttpAdapter> {
            creations++
            adapter
        }
        val unrelated = lazy<HttpAdapter> { error("Unrelated adapter must not materialize") }
        val graph = graph(mapOf(UniversalResolverHttpAdapter.ID to selected, "unrelated.http" to unrelated))
        assertFalse(selected.isInitialized())
        val projected = graph.universalResolverHttpAdapter
        assertSame(selected.value, projected)
        assertSame(projected, graph.universalResolverHttpAdapter)
        assertEquals(1, creations)
        assertFalse(unrelated.isInitialized())
    }

    @Test
    fun missingCanonicalMapEntryFailsClosed() {
        assertFailsWith<IllegalStateException> { graph(emptyMap()).universalResolverHttpAdapter }
    }

    @Test
    fun canonicalEntryWithWrongAdapterTypeFailsClosed() {
        val wrong = object : HttpAdapter {
            override val id = UniversalResolverHttpAdapter.ID
            override fun describe() = UniversalResolverHttpAdapterDescriptorProvider().describe()
            override suspend fun handleResolvedRequest(
                request: GenericHttpRequest,
                route: HttpAdapterRouteMatch,
            ): GenericHttpResponse = error("No endpoint may execute in a graph projection test")
        }
        assertFailsWith<IllegalStateException> {
            graph(mapOf(UniversalResolverHttpAdapter.ID to lazy { wrong })).universalResolverHttpAdapter
        }
    }

    private fun graph(adapters: Map<String, Lazy<HttpAdapter>>) = object : UniversalResolverHttpAdapter.Graph {
        override val universalResolverHttpAdapters = adapters
    }

    // Only constructor collaborators are supplied. Any accidental command/log/config use fails.
    private fun unusedExecution(): SessionExecution =
        Proxy.newProxyInstance(SessionExecution::class.java.classLoader, arrayOf(SessionExecution::class.java)) { _, method, _ ->
            when (method.name) {
                "getLog" -> unusedInterface(SessionLogService::class.java)
                "getConf" -> unusedInterface(ContextConfig::class.java)
                "getInterceptorChain" -> EmptyInterceptorChain
                else -> error("Unexpected execution access: ${method.name}")
            }
        } as SessionExecution

    private fun <T> unusedInterface(type: Class<T>): T =
        type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
                error("Unexpected constructor collaborator access: ${method.name}")
            },
        )

    private val unusedRegistry = object : HttpEndpointCommandRegistry {
        override fun get(handlerCommandId: String): HttpEndpointCommand? = error("No endpoint may resolve in a graph projection test")
        override fun listHandlerCommandIds(): Set<String> = error("No endpoint may resolve in a graph projection test")
    }
}
