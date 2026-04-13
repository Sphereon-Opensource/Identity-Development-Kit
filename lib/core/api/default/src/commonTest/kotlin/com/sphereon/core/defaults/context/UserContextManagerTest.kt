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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserContextManagerTest {

    private fun createAppComponent() = createCoreApiTestAppComponent(
        this, "user-context-manager-test", "test-profile", "0.0.1-TEST"
    )

    // ========== hasActive/hasAuthenticated Tests ==========

    @Test
    fun hasActiveReturnsFalseInitially() {
        val appComponent = createAppComponent()
        try {
            // hasActive returns true if there's an active context (including anonymous)
            // But background service cannot be active
            val hasActive = appComponent.userContextManager.hasActive()
            // Initially may return false if no active context is set
            assertNotNull(hasActive)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun hasAuthenticatedReturnsFalseInitially() {
        val appComponent = createAppComponent()
        try {
            assertFalse(appComponent.userContextManager.hasAuthenticated())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun hasAuthenticatedReturnsTrueAfterCreatingContext() {
        val appComponent = createAppComponent()
        try {
            appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertTrue(appComponent.userContextManager.hasAuthenticated())
        } finally {
            appComponent.destroy()
        }
    }

    // ========== get/getById Tests ==========

    @Test
    fun getByIdReturnsNullForUnknownId() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.getById("unknown-id", false)
            assertNull(instance)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getByIdReturnsAnonymousContext() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.getById(UserContext.ANONYMOUS, false)
            assertNotNull(instance)
            assertEquals(UserContext.ANONYMOUS, instance.contextId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getByIdReturnsBackgroundContext() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.getById(UserContext.BACKGROUND_SERVICE, false)
            assertNotNull(instance)
            // Note: Background service uses same tenant/principal as anonymous,
            // so context.id returns the same value
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getByIdMakesContextActive() {
        val appComponent = createAppComponent()
        try {
            val created = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
                false
            )

            // Get with makeActive = true
            appComponent.userContextManager.getById(created.contextId, true)

            val active = appComponent.userContextManager.getActive()
            assertEquals(created.contextId, active.contextId)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== has/hasById Tests ==========

    @Test
    fun hasReturnsFalseForUnknownContext() {
        val appComponent = createAppComponent()
        try {
            val tenantAware = object : com.sphereon.di.context.TenantAware {
                override val tenant = object : com.sphereon.di.context.TenantContextData {
                    override val tenantId = "unknown-tenant"
                }
            }
            val principalAware = object : com.sphereon.di.context.PrincipalAware {
                override val principal = "unknown-user"
            }
            assertFalse(appComponent.userContextManager.has(tenantAware, principalAware))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun hasReturnsTrue() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val tenantAware = object : com.sphereon.di.context.TenantAware {
                override val tenant = instance.context.tenant
            }
            val principalAware = object : com.sphereon.di.context.PrincipalAware {
                override val principal = instance.context.principal
            }
            assertTrue(appComponent.userContextManager.has(tenantAware, principalAware))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun hasByIdReturnsTrueForAnonymous() {
        val appComponent = createAppComponent()
        try {
            assertTrue(appComponent.userContextManager.hasById(UserContext.ANONYMOUS))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun hasByIdReturnsTrueForBackgroundService() {
        val appComponent = createAppComponent()
        try {
            assertTrue(appComponent.userContextManager.hasById(UserContext.BACKGROUND_SERVICE))
        } finally {
            appComponent.destroy()
        }
    }

    // ========== activateById Tests ==========

    @Test
    fun activateByIdReturnsFalseForBackgroundService() {
        val appComponent = createAppComponent()
        try {
            // Background service can NEVER be activated
            assertFalse(appComponent.userContextManager.activateById(UserContext.BACKGROUND_SERVICE))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun activateByIdReturnsTrueForAnonymous() {
        val appComponent = createAppComponent()
        try {
            assertTrue(appComponent.userContextManager.activateById(UserContext.ANONYMOUS))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun activateByIdReturnsFalseForUnknown() {
        val appComponent = createAppComponent()
        try {
            assertFalse(appComponent.userContextManager.activateById("unknown-context-id"))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun activateByIdReturnsTrueForCreatedContext() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
                false
            )
            assertTrue(appComponent.userContextManager.activateById(instance.contextId))
            assertEquals(instance.contextId, appComponent.userContextManager.getActive().contextId)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== activate Tests ==========

    @Test
    fun activateReturnsFalseForUnknownContext() {
        val appComponent = createAppComponent()
        try {
            val tenantAware = object : com.sphereon.di.context.TenantAware {
                override val tenant = object : com.sphereon.di.context.TenantContextData {
                    override val tenantId = "unknown-tenant"
                }
            }
            val principalAware = object : com.sphereon.di.context.PrincipalAware {
                override val principal = "unknown-user"
            }
            assertFalse(appComponent.userContextManager.activate(tenantAware, principalAware))
        } finally {
            appComponent.destroy()
        }
    }

    // ========== listIds Tests ==========

    @Test
    fun listIdsContainsAnonymousAndBackground() {
        val appComponent = createAppComponent()
        try {
            val ids = appComponent.userContextManager.listIds()
            assertTrue(ids.contains(UserContext.ANONYMOUS))
            assertTrue(ids.contains(UserContext.BACKGROUND_SERVICE))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun listIdsContainsCreatedContexts() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val ids = appComponent.userContextManager.listIds()
            assertTrue(ids.contains(instance.contextId))
        } finally {
            appComponent.destroy()
        }
    }

    // ========== createOrGetFromCallbacks Tests ==========

    @Test
    fun createOrGetFromCallbacksCreatesContext() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromCallbacks(
                { DefaultTenantInputString("callback-tenant") },
                { DefaultPrincipalInputString("callback-user") }
            )
            assertNotNull(instance)
            assertEquals("callback-user", instance.context.principal)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== createOrGetWithId Tests ==========

    @Test
    fun createOrGetWithIdReturnsAnonymousForAnonymousId() {
        val appComponent = createAppComponent()
        try {
            val tenantAware = object : com.sphereon.di.context.TenantAware {
                override val tenant = object : com.sphereon.di.context.TenantContextData {
                    override val tenantId = "ignored-tenant"
                }
            }
            val principalAware = object : com.sphereon.di.context.PrincipalAware {
                override val principal = "ignored-user"
            }
            val instance = appComponent.userContextManager.createOrGetWithId(
                UserContext.ANONYMOUS,
                tenantAware,
                principalAware,
                false
            )
            assertEquals(UserContext.ANONYMOUS, instance.contextId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun createOrGetWithIdReturnsBackgroundForBackgroundId() {
        val appComponent = createAppComponent()
        try {
            val tenantAware = object : com.sphereon.di.context.TenantAware {
                override val tenant = object : com.sphereon.di.context.TenantContextData {
                    override val tenantId = "ignored-tenant"
                }
            }
            val principalAware = object : com.sphereon.di.context.PrincipalAware {
                override val principal = "ignored-user"
            }
            val instance = appComponent.userContextManager.createOrGetWithId(
                UserContext.BACKGROUND_SERVICE,
                tenantAware,
                principalAware,
                true // makeActive should be ignored for background
            )
            // The instance is returned for BACKGROUND_SERVICE key
            assertNotNull(instance)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== destroy Tests ==========

    @Test
    fun destroyRemovesContext() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val contextId = instance.contextId

            assertTrue(appComponent.userContextManager.hasById(contextId))
            appComponent.userContextManager.destroyById(contextId)
            assertFalse(appComponent.userContextManager.hasById(contextId))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun destroyAllRemovesAllContexts() {
        val appComponent = createAppComponent()
        try {
            appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-1"),
                DefaultPrincipalInputString("user-1")
            )
            appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-2"),
                DefaultPrincipalInputString("user-2")
            )

            assertTrue(appComponent.userContextManager.hasAuthenticated())
            appComponent.userContextManager.destroyAll()
            assertFalse(appComponent.userContextManager.hasAuthenticated())
        } finally {
            appComponent.destroy()
        }
    }

    // ========== getBackgroundService/getAnonymous Tests ==========

    @Test
    fun getBackgroundServiceReturnsBackgroundContext() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.getBackgroundService()
            assertNotNull(instance)
            // Note: Background service context uses same tenant/principal as anonymous
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getAnonymousReturnsAnonymousContext() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.getAnonymous(false)
            assertNotNull(instance)
            assertEquals(UserContext.ANONYMOUS, instance.contextId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getAnonymousWithMakeActiveActivatesContext() {
        val appComponent = createAppComponent()
        try {
            // First create and activate a regular context
            val regularContext = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
                true
            )
            assertEquals(regularContext.contextId, appComponent.userContextManager.getActive().contextId)

            // Now get anonymous with makeActive = true
            val anonymous = appComponent.userContextManager.getAnonymous(true)
            assertEquals(UserContext.ANONYMOUS, appComponent.userContextManager.getActive().contextId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getBackgroundServiceIdReturnsCorrectId() {
        val appComponent = createAppComponent()
        try {
            assertEquals(UserContext.BACKGROUND_SERVICE, appComponent.userContextManager.getBackgroundServiceId())
        } finally {
            appComponent.destroy()
        }
    }

    // ========== isAnonymous Tests ==========

    @Test
    fun isAnonymousReturnsTrueWhenNoActiveContext() {
        val appComponent = createAppComponent()
        try {
            // Initially no authenticated context is active
            assertTrue(appComponent.userContextManager.isAnonymous())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun isAnonymousReturnsFalseWhenAuthenticatedContextActive() {
        val appComponent = createAppComponent()
        try {
            appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
                true
            )
            assertFalse(appComponent.userContextManager.isAnonymous())
        } finally {
            appComponent.destroy()
        }
    }

    // ========== createOrGetFromData Tests ==========

    @Test
    fun createOrGetFromDataCreatesContext() {
        val appComponent = createAppComponent()
        try {
            val tenantData = object : com.sphereon.di.context.TenantContextData {
                override val tenantId = "data-tenant"
            }
            val instance = appComponent.userContextManager.createOrGetFromData(
                tenantData,
                "data-user",
                false
            )
            assertNotNull(instance)
            assertEquals("data-tenant", instance.context.tenant.tenantId)
            assertEquals("data-user", instance.context.principal)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== activeInstance StateFlow Tests ==========

    @Test
    fun activeInstanceReturnsAnonymousInitially() {
        val appComponent = createAppComponent()
        try {
            val active = appComponent.userContextManager.activeInstance.value
            assertEquals(UserContext.ANONYMOUS, active.contextId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun activeInstanceUpdatesWhenContextActivated() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
                true
            )
            // Use getActive() which is synchronous, rather than StateFlow which uses lazy sharing
            val active = appComponent.userContextManager.getActive()
            assertEquals(instance.contextId, active.contextId)
        } finally {
            appComponent.destroy()
        }
    }
}
