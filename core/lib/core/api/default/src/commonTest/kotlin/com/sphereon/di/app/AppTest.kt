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

package com.sphereon.di.app

import com.sphereon.core.api.testutil.app
import com.sphereon.core.api.testutil.createCoreApiTestAppGraph
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.di.app.AbstractAppGraph
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppTest {
    @Test
    fun shouldCreateATestAppWithDefaultAppIdAndProfile() {
        val appGraph = createCoreApiTestAppGraph(this, "default", "default", version = "0.1.1-TEST")
        assertNotNull(appGraph)
        assertNotNull(appGraph.app)
        assertEquals("default", appGraph.appId)
        assertEquals("default", appGraph.profile)
        assertEquals("0.1.1-TEST", appGraph.version)
        appGraph.destroy()
    }

    @Test
    fun shouldCreateATestAppInitializeItDestroyItAndReinitializeIt() {
        val appGraph = createCoreApiTestAppGraph(this, "appId", "profile", "0.0.1-TEST")
        assertNotNull(appGraph)
        assertNotNull(appGraph.app)
        assertEquals("appId", appGraph.appId)
        assertEquals("profile", appGraph.profile)
        assertEquals("0.0.1-TEST", appGraph.version)

        val rootScope = appGraph.rootScopeProvider.rootScope
        assertNotNull(rootScope)

        assertEquals(0, rootScope.children().size)

        // We do this to test the session / scope registration (and cleanup)
        val contextInstance = appGraph.userContextManager.createOrGetFromInputs(DefaultTenantInputString("test@principal.com"), DefaultPrincipalInputString("test@principal.com"))
        assertTrue { appGraph.userContextManager.hasActive() }
        val context = appGraph.userContextManager.getActive()
        assertNotNull(context)

        assertEquals(1, rootScope.children().size)
        appGraph.destroy()
        val appGraph2 = (appGraph as AbstractAppGraph).initRootScopeProvider()
        assertEquals(appGraph, appGraph2)
        assertEquals(
            0,
            appGraph2.rootScopeProvider.rootScope
                .children()
                .size,
        )
    }

    @Test
    fun consoleLogPlugin() =
        runTest {
            val appGraph = createCoreApiTestAppGraph(this, "test-app", "test-profile", "0.0.1-TEST")
            assertNotNull(appGraph)
            assertNotNull(appGraph.app)
            assertEquals("test-app", appGraph.appId)
            assertEquals("test-profile", appGraph.profile)
            assertEquals("0.0.1-TEST", appGraph.version)

            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test@principal.com"),
                    DefaultPrincipalInputString("test@principal.com"),
                )

            val context = appGraph.userContextManager.getActive()
            println(context)

            println(appGraph.userContextManager.hasActive())

            println(context.scope.name)

            appGraph.destroy()
            val appGraph2 = (appGraph as AbstractAppGraph).initRootScopeProvider()
            assertEquals(appGraph, appGraph2)
        }
}
