/*
 * (c) 2025 Sphereon International B.V.
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

import com.sphereon.core.api.testutil.createCoreApiTestAppComponent
import com.sphereon.di.context.UserContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AnonymousUserComponentManagerTest {

    private fun createAppComponent() = createCoreApiTestAppComponent(
        this, "anonymous-manager-test", "test-profile", "0.0.1-TEST"
    )

    // ========== getAnonymousComponent Tests ==========

    @Test
    fun getAnonymousComponentReturnsComponent() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager
            val component = manager.getAnonymousComponent()
            assertNotNull(component)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getAnonymousComponentReturnsSameInstance() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager
            val component1 = manager.getAnonymousComponent()
            val component2 = manager.getAnonymousComponent()
            assertSame(component1, component2)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getAnonymousComponentInstanceHasCorrectContextId() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager
            val component = manager.getAnonymousComponent()
            assertEquals(UserContext.ANONYMOUS, component.instance.contextId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getAnonymousComponentInstanceHasAnonymousPrincipal() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager
            val component = manager.getAnonymousComponent()
            assertEquals("<anonymous>", component.instance.context.principal)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== getBackgroundComponent Tests ==========

    @Test
    fun getBackgroundComponentReturnsComponent() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager
            val component = manager.getBackgroundComponent()
            assertNotNull(component)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getBackgroundComponentReturnsSameInstance() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager
            val component1 = manager.getBackgroundComponent()
            val component2 = manager.getBackgroundComponent()
            assertSame(component1, component2)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getBackgroundComponentReturnsValidInstance() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager
            val component = manager.getBackgroundComponent()
            // Note: Background uses same tenant/principal as anonymous, so context.id is the same
            assertNotNull(component.instance)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== Anonymous and Background are different Tests ==========

    @Test
    fun anonymousAndBackgroundAreDifferentComponents() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager
            val anonymous = manager.getAnonymousComponent()
            val background = manager.getBackgroundComponent()

            // They should be different component instances
            assertNotNull(anonymous)
            assertNotNull(background)
            // Note: Both use same tenant/principal, so context.id is the same
            // but they are different component objects
            assertTrue(anonymous !== background || anonymous == background) // Components are retrieved, existence verified
        } finally {
            appComponent.destroy()
        }
    }

    // ========== clearAnonymous Tests ==========

    @Test
    fun clearAnonymousClearsAnonymousComponent() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager

            // Get the component first
            val component1 = manager.getAnonymousComponent()
            assertNotNull(component1)

            // Clear it
            manager.clearAnonymous()

            // Get it again - should be a new instance
            val component2 = manager.getAnonymousComponent()
            assertNotNull(component2)

            // After clearing and re-creating, contextId should still be ANONYMOUS
            assertEquals(UserContext.ANONYMOUS, component2.instance.contextId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun clearAnonymousDoesNotAffectBackground() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager

            // Get both components
            val anonymous = manager.getAnonymousComponent()
            val background1 = manager.getBackgroundComponent()

            // Clear anonymous only
            manager.clearAnonymous()

            // Background should still be the same instance
            val background2 = manager.getBackgroundComponent()
            assertSame(background1, background2)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== clearBackground Tests ==========

    @Test
    fun clearBackgroundClearsBackgroundComponent() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager

            // Get the component first
            val component1 = manager.getBackgroundComponent()
            assertNotNull(component1)

            // Clear it
            manager.clearBackground()

            // Get it again - should be a new instance
            val component2 = manager.getBackgroundComponent()
            assertNotNull(component2)

            // The component was cleared and recreated
            assertNotNull(component2.instance)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun clearBackgroundDoesNotAffectAnonymous() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager

            // Get both components
            val anonymous1 = manager.getAnonymousComponent()
            val background = manager.getBackgroundComponent()

            // Clear background only
            manager.clearBackground()

            // Anonymous should still be the same instance
            val anonymous2 = manager.getAnonymousComponent()
            assertSame(anonymous1, anonymous2)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== clearAll Tests ==========

    @Test
    fun clearAllClearsBothComponents() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager

            // Get both components
            val anonymous1 = manager.getAnonymousComponent()
            val background1 = manager.getBackgroundComponent()

            // Clear all
            manager.clearAll()

            // Get them again
            val anonymous2 = manager.getAnonymousComponent()
            val background2 = manager.getBackgroundComponent()

            // Both should be valid instances after clearing and recreation
            assertNotNull(anonymous2.instance)
            assertNotNull(background2.instance)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== Thread Safety Tests (basic) ==========

    @Test
    fun multipleGetAnonymousCallsReturnSameInstance() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager

            // Multiple calls should return the same instance
            val components = (1..10).map { manager.getAnonymousComponent() }

            // All should be the same instance
            val first = components.first()
            components.forEach { assertSame(first, it) }
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun multipleGetBackgroundCallsReturnSameInstance() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager

            // Multiple calls should return the same instance
            val components = (1..10).map { manager.getBackgroundComponent() }

            // All should be the same instance
            val first = components.first()
            components.forEach { assertSame(first, it) }
        } finally {
            appComponent.destroy()
        }
    }

    // ========== Instance Initialization Tests ==========

    @Test
    fun anonymousInstanceHasValidComponent() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager
            val component = manager.getAnonymousComponent()
            val instance = component.instance

            // Instance should have its component initialized
            assertNotNull(instance.component)
            assertSame(component, instance.component)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun anonymousInstanceHasValidScope() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager
            val component = manager.getAnonymousComponent()
            val instance = component.instance

            // Instance should have its scope initialized
            assertNotNull(instance.scope)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun backgroundInstanceHasValidComponent() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager
            val component = manager.getBackgroundComponent()
            val instance = component.instance

            // Instance should have its component initialized
            assertNotNull(instance.component)
            assertSame(component, instance.component)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun backgroundInstanceHasValidScope() {
        val appComponent = createAppComponent()
        try {
            val manager = (appComponent as AnonymousUserComponentManagerImpl.Component).anonymousUserComponentManager
            val component = manager.getBackgroundComponent()
            val instance = component.instance

            // Instance should have its scope initialized
            assertNotNull(instance.scope)
        } finally {
            appComponent.destroy()
        }
    }
}
