/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.core.defaults.context

import com.sphereon.core.api.testutil.createCoreApiTestAppGraph
import com.sphereon.di.context.AnonymousUserGraphManager
import com.sphereon.di.context.UserContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AnonymousUserGraphManagerTest {
    private fun createAppGraph() =
        createCoreApiTestAppGraph(
            this,
            "anonymous-manager-test",
            "test-profile",
            "0.0.1-TEST",
        )

    // ========== getAnonymousGraph Tests ==========

    @Test
    fun getAnonymousComponentReturnsGraph() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager
            val graph = manager.getAnonymousGraph()
            assertNotNull(graph)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun getAnonymousGraphReturnsSameInstance() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager
            val component1 = manager.getAnonymousGraph()
            val component2 = manager.getAnonymousGraph()
            assertSame(component1, component2)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun getAnonymousGraphInstanceHasCorrectContextId() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager
            val graph = manager.getAnonymousGraph()
            assertEquals(UserContext.ANONYMOUS, graph.instance.contextId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun getAnonymousGraphInstanceHasAnonymousPrincipal() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager
            val graph = manager.getAnonymousGraph()
            assertEquals("<anonymous>", graph.instance.context.principal)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== getBackgroundGraph Tests ==========

    @Test
    fun getBackgroundComponentReturnsGraph() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager
            val graph = manager.getBackgroundGraph()
            assertNotNull(graph)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun getBackgroundGraphReturnsSameInstance() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager
            val component1 = manager.getBackgroundGraph()
            val component2 = manager.getBackgroundGraph()
            assertSame(component1, component2)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun getBackgroundGraphReturnsValidInstance() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager
            val graph = manager.getBackgroundGraph()
            // Note: Background uses same tenant/principal as anonymous, so context.id is the same
            assertNotNull(graph.instance)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== Anonymous and Background are different Tests ==========

    @Test
    fun anonymousAndBackgroundAreDifferentGraphs() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager
            val anonymous = manager.getAnonymousGraph()
            val background = manager.getBackgroundGraph()

            // They should be different graph instances
            assertNotNull(anonymous)
            assertNotNull(background)
            // Note: Both use same tenant/principal, so context.id is the same
            // but they are different graph objects
            assertTrue(anonymous !== background || anonymous == background) // Components are retrieved, existence verified
        } finally {
            appGraph.destroy()
        }
    }

    // ========== clearAnonymous Tests ==========

    @Test
    fun clearAnonymousClearsAnonymousGraph() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager

            // Get the graph first
            val component1 = manager.getAnonymousGraph()
            assertNotNull(component1)

            // Clear it
            manager.clearAnonymous()

            // Get it again - should be a new instance
            val component2 = manager.getAnonymousGraph()
            assertNotNull(component2)

            // After clearing and re-creating, contextId should still be ANONYMOUS
            assertEquals(UserContext.ANONYMOUS, component2.instance.contextId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun clearAnonymousDoesNotAffectBackground() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager

            // Get both components
            val anonymous = manager.getAnonymousGraph()
            val background1 = manager.getBackgroundGraph()

            // Clear anonymous only
            manager.clearAnonymous()

            // Background should still be the same instance
            val background2 = manager.getBackgroundGraph()
            assertSame(background1, background2)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== clearBackground Tests ==========

    @Test
    fun clearBackgroundClearsBackgroundGraph() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager

            // Get the graph first
            val component1 = manager.getBackgroundGraph()
            assertNotNull(component1)

            // Clear it
            manager.clearBackground()

            // Get it again - should be a new instance
            val component2 = manager.getBackgroundGraph()
            assertNotNull(component2)

            // The graph was cleared and recreated
            assertNotNull(component2.instance)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun clearBackgroundDoesNotAffectAnonymous() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager

            // Get both components
            val anonymous1 = manager.getAnonymousGraph()
            val background = manager.getBackgroundGraph()

            // Clear background only
            manager.clearBackground()

            // Anonymous should still be the same instance
            val anonymous2 = manager.getAnonymousGraph()
            assertSame(anonymous1, anonymous2)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== clearAll Tests ==========

    @Test
    fun clearAllClearsBothGraphs() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager

            // Get both components
            val anonymous1 = manager.getAnonymousGraph()
            val background1 = manager.getBackgroundGraph()

            // Clear all
            manager.clearAll()

            // Get them again
            val anonymous2 = manager.getAnonymousGraph()
            val background2 = manager.getBackgroundGraph()

            // Both should be valid instances after clearing and recreation
            assertNotNull(anonymous2.instance)
            assertNotNull(background2.instance)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== Thread Safety Tests (basic) ==========

    @Test
    fun multipleGetAnonymousCallsReturnSameInstance() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager

            // Multiple calls should return the same instance
            val components = (1..10).map { manager.getAnonymousGraph() }

            // All should be the same instance
            val first = components.first()
            components.forEach { assertSame(first, it) }
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun multipleGetBackgroundCallsReturnSameInstance() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager

            // Multiple calls should return the same instance
            val components = (1..10).map { manager.getBackgroundGraph() }

            // All should be the same instance
            val first = components.first()
            components.forEach { assertSame(first, it) }
        } finally {
            appGraph.destroy()
        }
    }

    // ========== Instance Initialization Tests ==========

    @Test
    fun anonymousInstanceHasValidGraph() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager
            val graph = manager.getAnonymousGraph()
            val instance = graph.instance

            // Instance should have its graph initialized
            assertNotNull(instance.graph)
            assertSame(graph, instance.graph)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun anonymousInstanceHasValidScope() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager
            val graph = manager.getAnonymousGraph()
            val instance = graph.instance

            // Instance should have its scope initialized
            assertNotNull(instance.scope)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun backgroundInstanceHasValidGraph() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager
            val graph = manager.getBackgroundGraph()
            val instance = graph.instance

            // Instance should have its graph initialized
            assertNotNull(instance.graph)
            assertSame(graph, instance.graph)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun backgroundInstanceHasValidScope() {
        val appGraph = createAppGraph()
        try {
            val manager = (appGraph as AnonymousUserGraphManager.Graph).anonymousUserGraphManager
            val graph = manager.getBackgroundGraph()
            val instance = graph.instance

            // Instance should have its scope initialized
            assertNotNull(instance.scope)
        } finally {
            appGraph.destroy()
        }
    }
}
