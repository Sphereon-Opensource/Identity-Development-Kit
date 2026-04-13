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

package com.sphereon.di.app

import kotlinx.coroutines.test.runTest
import com.sphereon.core.api.testutil.app
import com.sphereon.core.api.testutil.createCoreApiTestAppComponent
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.di.app.AbstractAppComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppTest {


    @Test
    fun shouldCreateATestAppWithDefaultAppIdAndProfile() {
        val appComponent = createCoreApiTestAppComponent(this, "default", "default", version = "0.1.1-TEST")
        assertNotNull(appComponent)
        assertNotNull(appComponent.app)
        assertEquals("default", appComponent.appId)
        assertEquals("default", appComponent.profile)
        assertEquals("0.1.1-TEST", appComponent.version)
        appComponent.destroy()
    }


    @Test
    fun shouldCreateATestAppInitializeItDestroyItAndReinitializeIt() {
        val appComponent = createCoreApiTestAppComponent(this, "appId", "profile", "0.0.1-TEST")
        assertNotNull(appComponent)
        assertNotNull(appComponent.app)
        assertEquals("appId", appComponent.appId)
        assertEquals("profile", appComponent.profile)
        assertEquals("0.0.1-TEST", appComponent.version)

        val rootScope = appComponent.rootScopeProvider.rootScope
        assertNotNull(rootScope)

        assertEquals(0, rootScope.children().size)

        // We do this to test the session / scope registration (and cleanup)
        val contextInstance = appComponent.userContextManager.createOrGetFromInputs(DefaultTenantInputString("test@principal.com"), DefaultPrincipalInputString("test@principal.com"))
        assertTrue { appComponent.userContextManager.hasActive() }
        val context = appComponent.userContextManager.getActive()
        assertNotNull(context)

        assertEquals(1, rootScope.children().size)
        appComponent.destroy()
        val appComponent2 = (appComponent as AbstractAppComponent).initRootScopeProvider()
        assertEquals(appComponent, appComponent2)
        assertEquals(0, appComponent2.rootScopeProvider.rootScope.children().size)

    }


    @Test
    fun consoleLogPlugin() = runTest {
        val appComponent = createCoreApiTestAppComponent(this, "test-app", "test-profile", "0.0.1-TEST")
        assertNotNull(appComponent)
        assertNotNull(appComponent.app)
        assertEquals("test-app", appComponent.appId)
        assertEquals("test-profile", appComponent.profile)
        assertEquals("0.0.1-TEST", appComponent.version)


        val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
            DefaultTenantInputString("test@principal.com"),
            DefaultPrincipalInputString("test@principal.com")
        )

        val context = appComponent.userContextManager.getActive()
        println(context)

        println(appComponent.userContextManager.hasActive())

        println(context.scope.name)

        appComponent.destroy()
        val appComponent2 = (appComponent as AbstractAppComponent).initRootScopeProvider()
        assertEquals(appComponent, appComponent2)
    }

}
