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

package com.sphereon.core.defaults.session

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.di.context.AnonymousUserComponentManager
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserContextManager
import com.sphereon.di.context.UserScope
import com.sphereon.di.context.createAnonymousSessionContext
import com.sphereon.di.session.SessionComponent
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionInstance
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dev.zacsweers.metro.Inject
import software.amazon.app.platform.scope.Scope
import software.amazon.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.amazon.app.platform.scope.di.metro.addMetroDependencyGraph
import software.amazon.app.platform.scope.register
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<SessionContextManager>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionContextManagerImpl", exact = true)
class SessionContextManagerImpl(
    private val sessionComponentFactory: SessionComponent.Factory,
    val userContextManager: UserContextManager,
    private val anonymousUserComponentManager: AnonymousUserComponentManager
) : SessionContextManager, SynchronizedObject() {

    // Active instance tracking (atomic reference for thread-safe reads)
    private val _activeInstance: AtomicRef<SessionInstance?> = atomic(null)

    // StateFlow that mirrors _activeInstance for reactive observation
    private val _activeInstanceFlow = MutableStateFlow<SessionInstance?>(null)

    /**
     * Reactive flow of the active session instance.
     * Emits whenever the active session changes.
     */
    override val activeInstance: StateFlow<SessionInstance?> = _activeInstanceFlow.asStateFlow()

    // Internal map for management (stores all sessions for this user/tenant context)
    // Using AtomicRef instead of MutableStateFlow for better thread-safety
    private val _instances: AtomicRef<Map<String, SessionInstance>> = atomic(emptyMap())

    // Constants for special session types
    companion object {
        const val ANONYMOUS_SESSION_ID = IdentityConstants.ANONYMOUS_SESSION_ID
    }

    // Primary access method - always returns instance (anonymous if map empty)
    override fun getActive(): SessionInstance {
        // Fast path: lock-free read
        _activeInstance.value?.let { return it }

        // No active session - return the anonymous session (always from anonymous user component)
        return getOrCreateAnonymousSessionInternal(false)
    }

    override fun hasActive(): Boolean {
        val current = _activeInstance.value
        return current != null && current.sessionId != ANONYMOUS_SESSION_ID
    }

    // Instance-based access (primary approach) - with makeActive capability
    // Ensures session uniqueness: the same session ID always returns the same instance within this user/tenant scope
    override fun getById(sessionId: String, makeActive: Boolean): SessionInstance? {
        // Read the atomic value once
        val currentInstances = _instances.value
        val existingInstance = currentInstances[sessionId]

        if (existingInstance != null && makeActive) {
            setActiveInstance(existingInstance)
        }

        return existingInstance
    }

    override fun hasById(sessionId: String): Boolean {
        return _instances.value.containsKey(sessionId)
    }

    // Session switching (updates flows)
    override fun activateById(sessionId: String): Boolean {
        // Read the atomic value once
        val currentInstances = _instances.value
        val instance = currentInstances[sessionId]
        return if (instance != null) {
            setActiveInstance(instance)
            true
        } else {
            false
        }
    }

    // Session listing
    override fun listIds(): Set<String> = _instances.value.keys

    // Session creation (returns instances)
    override fun createOrGetFromCallbacks(
        sessionContextProvider: () -> SessionContext,
    ): SessionInstance {
        val runtimeSessionContext = sessionContextProvider()
        val sessionId = runtimeSessionContext.sessionId
        return getOrCreateSessionInternal(sessionId, runtimeSessionContext, makeActive = true).instance
    }

    override fun createOrGetFromId(
        sessionId: String,
        makeActive: Boolean
    ): SessionInstance {
        requireNotNull(sessionId) { "sessionId must not be null" }
        return getOrCreateSessionInternal(sessionId, null, makeActive).instance
    }

    // Session cleanup
    override fun destroyById(sessionId: String) {
        synchronized(this) {
            // Read the atomic value once
            val currentInstances = _instances.value
            val instance = currentInstances[sessionId]
            if (instance != null) {
                // Remove from map first
                _instances.value = currentInstances - sessionId

                // Clear active if this was the active session
                val currentActive = _activeInstance.value
                if (currentActive?.sessionId == sessionId) {
                    setActiveInstance(null)
                }

                // Destroy scope last
                instance.scope.destroy()
            }
        }
    }

    override fun destroyAll() {
        synchronized(this) {
            val instancesToDestroy = _instances.value.values.toList()

            // Clear all state first
            _instances.value = emptyMap()
            setActiveInstance(null)

            // Destroy scopes after state is cleared
            instancesToDestroy.forEach { it.scope.destroy() }
        }
    }

    // Special session types - return instances (convenience methods)
    override fun getOrCreateBackgroundService(makeActive: Boolean): SessionInstance {
        // Background sessions are ALWAYS created in the background service component's scope
        val backgroundComponent = anonymousUserComponentManager.getBackgroundComponent()
        val backgroundScope = backgroundComponent.instance.scope

        // Check if the background session already exists
        val backgroundSessionManager = backgroundComponent.sessionContextManager as? SessionContextManagerImpl

        // First check if it's already created (fast path)
        // Read the atomic value once to avoid multiple atomic reads
        val existingInstance = if (backgroundSessionManager != null) {
            val existingInstances = backgroundSessionManager._instances.value
            existingInstances[ANONYMOUS_SESSION_ID]
        } else {
            null
        }
        if (existingInstance != null) {
            // Background service can never be made active
            return existingInstance
        }

        // Only create if we ARE the background session manager (prevent other managers from creating it)
        if (this != backgroundSessionManager) {
            // We're not the background session manager, recursively call it
            return backgroundSessionManager?.getOrCreateBackgroundService(false)
                ?: error("Background session manager not found")
        }

        // We ARE the background session manager, create the session with synchronization
        return synchronized(this) {
            // Double-check after acquiring lock - read atomic value once
            val currentInstances = _instances.value
            val existing = currentInstances[ANONYMOUS_SESSION_ID]
            if (existing != null) {
                return existing
            }

            val anonymousSessionContext = createAnonymousSessionContext(ANONYMOUS_SESSION_ID)
            val sessionComponent = sessionComponentFactory.createSessionComponent(ANONYMOUS_SESSION_ID)

            val scope = backgroundScope.buildChild("session:$ANONYMOUS_SESSION_ID") {
                addMetroDependencyGraph(sessionComponent)
                addService("sessionContext", anonymousSessionContext)
                addCoroutineScopeScoped(sessionComponent.sessionScopeCoroutineScopeScoped)
            }

            // Initialize the instance with its component and scope
            val instance = sessionComponent.instance
            (instance as? SessionInstanceImpl)?.initialize(sessionComponent, scope)

            // Store the instance atomically
            _instances.value = currentInstances + (ANONYMOUS_SESSION_ID to instance)

            // Background service can never be made active (no need to set _activeInstance)

            // Register instances after the instance is stored
            scope.register(sessionComponent.sessionScopedInstances)

            instance
        }
    }

    override fun getAnonymous(makeActive: Boolean): SessionInstance {
        return getOrCreateAnonymousSessionInternal(makeActive)
    }

    override fun getBackgroundServiceId(): String = ANONYMOUS_SESSION_ID

    // Private helper methods
    private fun getOrCreateAnonymousSessionInternal(makeActive: Boolean): SessionInstance {
        // Anonymous sessions are ALWAYS created in the anonymous user component's scope
        // Get the anonymous user component
        val anonymousUserComponent = anonymousUserComponentManager.getAnonymousComponent()
        val anonymousScope = anonymousUserComponent.instance.scope

        // Check if the anonymous session already exists in the anonymous component
        val anonymousSessionManager = anonymousUserComponent.sessionContextManager as? SessionContextManagerImpl

        // First check if it's already created (fast path)
        // Read the atomic value once to avoid multiple atomic reads
        val existingInstance = if (anonymousSessionManager != null) {
            val existingInstances = anonymousSessionManager._instances.value
            existingInstances[ANONYMOUS_SESSION_ID]
        } else {
            null
        }
        if (existingInstance != null) {
            if (makeActive && this == anonymousSessionManager) {
                setActiveInstance(existingInstance)
            }
            return existingInstance
        }

        // Only create if we ARE the anonymous session manager (prevent other managers from creating it)
        if (this != anonymousSessionManager) {
            // We're not the anonymous session manager, delegate to it via the public interface
            return anonymousUserComponent.sessionContextManager.getAnonymous(false)
        }

        // We ARE the anonymous session manager, create the session with synchronization
        return synchronized(this) {
            // Double-check after acquiring lock - read atomic value once
            val currentInstances = _instances.value
            val existing = currentInstances[ANONYMOUS_SESSION_ID]
            if (existing != null) {
                if (makeActive) {
                    setActiveInstance(existing)
                }
                return existing
            }

            val anonymousSessionContext = createAnonymousSessionContext(ANONYMOUS_SESSION_ID)
            val sessionComponent = sessionComponentFactory.createSessionComponent(ANONYMOUS_SESSION_ID)

            val scope = anonymousScope.buildChild("session:$ANONYMOUS_SESSION_ID") {
                addMetroDependencyGraph(sessionComponent)
                addService("sessionContext", anonymousSessionContext)
                addCoroutineScopeScoped(sessionComponent.sessionScopeCoroutineScopeScoped)
            }

            // Initialize the instance with its component and scope
            val instance = sessionComponent.instance
            (instance as? SessionInstanceImpl)?.initialize(sessionComponent, scope)

            // Store the instance atomically
            _instances.value = currentInstances + (ANONYMOUS_SESSION_ID to instance)

            // Set as active if requested
            if (makeActive) {
                setActiveInstance(instance)
            }

            // Register instances after the instance is stored and active
            scope.register(sessionComponent.sessionScopedInstances)

            instance
        }
    }

    private fun getOrCreateSessionInternal(
        sessionId: String,
        sessionContext: SessionContext?,
        makeActive: Boolean
    ): SessionComponent {
        // Fast-path: lock-free read if already created
        // Read the atomic value once to avoid multiple atomic reads
        val currentInstances = _instances.value
        val existing = currentInstances[sessionId]
        if (existing != null) {
            if (makeActive) {
                setActiveInstance(existing)
            }
            return existing.component
        }

        // Slow path: need to create, use synchronized block with double-check
        return synchronized(this) {
            // Double-check after acquiring lock - read atomic value once
            val lockedInstances = _instances.value
            val lockedExisting = lockedInstances[sessionId]
            if (lockedExisting != null) {
                if (makeActive) {
                    setActiveInstance(lockedExisting)
                }
                return lockedExisting.component
            }

            // Determine the correct scope for this session
            val contextScope = when {
                isBackgroundContext() -> anonymousUserComponentManager.getBackgroundComponent().instance.scope
                isAnonymousContext() -> anonymousUserComponentManager.getAnonymousComponent().instance.scope
                else -> getContextScope()
            }

            // Create the session context
            val actualSessionContext = sessionContext ?: if (sessionId == ANONYMOUS_SESSION_ID) {
                createAnonymousSessionContext(sessionId)
            } else {
                SessionContextImpl(userContextManager = userContextManager, sessionId = sessionId)
            }

            // Create the session component and scope
            val sessionComponent = sessionComponentFactory.createSessionComponent(sessionId)
            val scope = contextScope.buildChild("session:$sessionId") {
                addMetroDependencyGraph(sessionComponent)
                addService("sessionContext", actualSessionContext)
                addCoroutineScopeScoped(sessionComponent.sessionScopeCoroutineScopeScoped)
            }

            // Initialize the instance
            val instance = sessionComponent.instance
            (instance as? SessionInstanceImpl)?.initialize(sessionComponent, scope)

            // Store the instance atomically
            _instances.value = lockedInstances + (sessionId to instance)

            // Set as active if requested
            if (makeActive) {
                setActiveInstance(instance)
            }

            // Register scoped instances
            scope.register(sessionComponent.sessionScopedInstances)

            sessionComponent
        }
    }

    /**
     * Sets the active instance atomically and updates the flow.
     * This ensures both the atomic reference and the StateFlow are kept in sync.
     */
    private fun setActiveInstance(instance: SessionInstance?) {
        _activeInstance.value = instance
        _activeInstanceFlow.value = instance
    }

    // Get the context scope from the active user context
    private fun getContextScope(): Scope {
        return userContextManager.getActive().scope
    }

    // Check if we're in a background context
    private fun isBackgroundContext(): Boolean {
        val activeContext = userContextManager.getActive()
        return activeContext.contextId == UserContext.BACKGROUND_SERVICE
    }

    // Check if we're in an anonymous context
    private fun isAnonymousContext(): Boolean {
        val activeContext = userContextManager.getActive()
        return activeContext.contextId == UserContext.ANONYMOUS
    }
}
