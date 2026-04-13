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

package com.sphereon.di.session

import com.sphereon.di.context.AnonymousContext
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.SecuredTenantContextDetails
import com.sphereon.di.context.createAnonymousSessionContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import software.amazon.app.platform.scope.coroutine.CoroutineScopeScoped
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionContextIsAnonymousTest {

    @Test
    fun noOpSessionContextIsAnonymous() {
        assertTrue(NoOpSessionContext.isAnonymous())
    }

    @Test
    fun anonymousSessionContextIsAnonymous() {
        val ctx = createAnonymousSessionContext("<anonymous>")
        assertTrue(ctx.isAnonymous())
    }

    @Test
    fun sessionContextWithNonAnonymousSessionIdIsNotAnonymous() {
        val ctx = createAnonymousSessionContext("non-anonymous-session")
        assertFalse(ctx.isAnonymous())
    }

    @Test
    fun sessionContextWithNonAnonymousTenantIsNotAnonymous() {
        val userContext = object : UserContext {
            override val id = "user-1"
            override val tenant = object : TenantContextData {
                override val tenantId = "real-tenant"
            }
            override val principal = "<anonymous>"
            override val secureDetails: SecuredTenantContextDetails? = null
        }
        val ctx = object : SessionContext {
            override val context = userContext
            override val sessionId = "<anonymous>"
        }
        assertFalse(ctx.isAnonymous())
    }

    @Test
    fun sessionContextWithNonAnonymousPrincipalIsNotAnonymous() {
        val userContext = object : UserContext {
            override val id = "user-1"
            override val tenant = object : TenantContextData {
                override val tenantId = "<anonymous>"
            }
            override val principal = "user@example.com"
            override val secureDetails: SecuredTenantContextDetails? = null
        }
        val ctx = object : SessionContext {
            override val context = userContext
            override val sessionId = "<anonymous>"
        }
        assertFalse(ctx.isAnonymous())
    }

    @Test
    fun fullyAnonymousSessionContextIsAnonymous() {
        val userContext = object : UserContext {
            override val id = "anon"
            override val tenant = object : TenantContextData {
                override val tenantId = "<anonymous>"
            }
            override val principal = "<anonymous>"
            override val secureDetails: SecuredTenantContextDetails? = null
        }
        val ctx = object : SessionContext {
            override val context = userContext
            override val sessionId = "<anonymous>"
        }
        assertTrue(ctx.isAnonymous())
    }

    @Test
    fun sessionContextEqualsNoOpIsAnonymous() {
        // NoOpSessionContext is explicitly checked in isAnonymous()
        val ctx: SessionContext = NoOpSessionContext
        assertTrue(ctx.isAnonymous())
    }
}

class SessionContextInterfaceTest {

    @Test
    fun sessionContextExposesSessionId() {
        val ctx = object : SessionContext {
            override val context = AnonymousContext
            override val sessionId = "test-session-123"
        }
        kotlin.test.assertEquals("test-session-123", ctx.sessionId)
    }

    @Test
    fun sessionContextExposesContext() {
        val ctx = object : SessionContext {
            override val context = AnonymousContext
            override val sessionId = "test"
        }
        kotlin.test.assertEquals(AnonymousContext, ctx.context)
    }

    @Test
    fun sessionContextImplementsContextAware() {
        val ctx: SessionContext = createAnonymousSessionContext("test")
        // SessionContext extends ContextAware, so it has .context
        kotlin.test.assertNotNull(ctx.context)
    }
}

class ISessionContextAwareInterfaceTest {

    @Test
    fun sessionContextAwareExposesSessionContext() {
        val sessionCtx = createAnonymousSessionContext("test-session")
        val aware = object : ISessionContextAware {
            override val sessionContext = sessionCtx
        }
        kotlin.test.assertEquals(sessionCtx, aware.sessionContext)
        kotlin.test.assertEquals("test-session", aware.sessionContext.sessionId)
    }
}

// ========== SessionScope Integration Tests ==========

class SessionScopeTest {

    @Test
    fun sessionScopeClassExists() {
        // Verify the SessionScope class exists and can be referenced
        val scopeClass: KClass<SessionScope> = SessionScope::class
        assertNotNull(scopeClass)
    }

    @Test
    fun sessionScopeSimpleNameIsCorrect() {
        kotlin.test.assertEquals("SessionScope", SessionScope::class.simpleName)
    }

    @Test
    fun sessionScopeClassIsNotNull() {
        // qualifiedName is not available on JS/wasmJs, so just verify the class exists
        assertNotNull(SessionScope::class.simpleName)
    }
}

// ========== SessionComponent Provider Integration Tests ==========

class SessionComponentProviderTest {

    /**
     * Minimal stub implementation of SessionComponent for testing the interface's default methods.
     */
    private class StubSessionComponent : SessionComponent {
        override val sessionId: String = "stub-session"
        override val sessionContext: SessionContext = createAnonymousSessionContext(sessionId)
        override val sessionExecution: com.sphereon.core.api.context.SessionExecution
            get() = throw NotImplementedError("Not needed for this test")
        override val logManager: com.sphereon.core.api.log.SessionLogManager
            get() = throw NotImplementedError("Not needed for this test")
        override val instance: SessionInstance
            get() = throw NotImplementedError("Not needed for this test")
        override val sessionScopedInstances: Set<software.amazon.app.platform.scope.Scoped> = emptySet()

        // Use a proper CoroutineScopeScoped for testing
        private val _sessionScopeCoroutineScopeScoped = CoroutineScopeScoped(
            Dispatchers.Default + SupervisorJob() + CoroutineName("StubSessionScope")
        )
        override val sessionScopeCoroutineScopeScoped: CoroutineScopeScoped
            get() = _sessionScopeCoroutineScopeScoped

        fun cleanup() {
            _sessionScopeCoroutineScopeScoped.cancel()
        }
    }

    @Test
    fun provideSessionCoroutineScopeUsesInterfaceDefault() {
        // Test that the SessionScopedProviders interface's default method works
        val component = StubSessionComponent()
        val providers = object : SessionScopedProviders {}

        val childScope = providers.provideSessionCoroutineScope(component.sessionScopeCoroutineScopeScoped)

        assertNotNull(childScope)
        assertTrue(childScope.isActive, "Child scope should be active")

        // Cleanup
        childScope.cancel()
        component.cleanup()
    }

    @Test
    fun provideEmptyScopedUsesInterfaceDefault() {
        // Test that the SessionScopedProviders interface's default method works
        val providers = object : SessionScopedProviders {}

        val scoped = providers.provideSessionScopeEmptyScoped()

        kotlin.test.assertEquals(software.amazon.app.platform.scope.Scoped.NO_OP, scoped)
    }

    @Test
    fun coroutineScopeScopedCreateChildReturnsActiveScope() {
        // Test CoroutineScopeScoped.createChild() behavior directly
        val parentContext = Dispatchers.Default + SupervisorJob() + CoroutineName("TestScope")
        val scopeScoped = CoroutineScopeScoped(parentContext)

        val childScope = scopeScoped.createChild()

        assertNotNull(childScope)
        assertTrue(childScope.isActive, "Child scope should be active")

        // Cleanup
        childScope.cancel()
        scopeScoped.cancel()
    }

    @Test
    fun coroutineScopeScopedCreatesIndependentChildren() {
        val parentContext = Dispatchers.Default + SupervisorJob() + CoroutineName("TestScope2")
        val scopeScoped = CoroutineScopeScoped(parentContext)

        // Create two child scopes
        val child1 = scopeScoped.createChild()
        val child2 = scopeScoped.createChild()

        // Both should be active
        assertTrue(child1.isActive)
        assertTrue(child2.isActive)

        // Cancelling one should not affect the other
        child1.cancel()
        assertFalse(child1.isActive)
        assertTrue(child2.isActive, "Child2 should still be active after child1 cancellation")

        // Cleanup
        child2.cancel()
        scopeScoped.cancel()
    }

    @Test
    fun scopedNoOpExists() {
        // Test that Scoped.NO_OP is accessible
        val noOp = software.amazon.app.platform.scope.Scoped.NO_OP
        assertNotNull(noOp)
    }
}
