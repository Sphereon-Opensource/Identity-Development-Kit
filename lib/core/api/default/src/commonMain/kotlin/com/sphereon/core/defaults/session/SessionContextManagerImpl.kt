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

package com.sphereon.core.defaults.session

import com.sphereon.di.context.AnonymousUserGraphManager
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserContextManager
import com.sphereon.di.context.UserScope
import com.sphereon.di.context.createAnonymousSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionGraph
import com.sphereon.di.session.SessionInstance
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import software.amazon.app.platform.scope.Scope
import software.amazon.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.amazon.app.platform.scope.di.metro.addMetroDependencyGraph
import software.amazon.app.platform.scope.register
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<SessionContextManager>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionContextManagerImpl", exact = true)
class SessionContextManagerImpl(
    private val sessionGraphFactory: SessionGraph.Factory,
    val userContextManager: UserContextManager,
    private val anonymousUserGraphManager: AnonymousUserGraphManager,
) : SynchronizedObject(),
    SessionContextManager {
    // Active instance tracking (atomic reference for thread-safe reads)
    private val activeInstanceRef: AtomicRef<SessionInstance?> = atomic(null)

    // StateFlow that mirrors activeInstanceRef for reactive observation
    private val _activeInstance = MutableStateFlow<SessionInstance?>(null)

    /**
     * Reactive flow of the active session instance.
     * Emits whenever the active session changes.
     */
    override val activeInstance: StateFlow<SessionInstance?> = _activeInstance.asStateFlow()

    // Internal map for management (stores all sessions for this user/tenant context)
    // Using AtomicRef instead of MutableStateFlow for better thread-safety
    private val instances: AtomicRef<Map<String, SessionInstance>> = atomic(emptyMap())

    // Primary access method - always returns instance (anonymous if map empty)
    override fun getActive(): SessionInstance {
        // Fast path: lock-free read
        activeInstanceRef.value?.let { return it }

        // No active session - return the anonymous session (always from anonymous user graph)
        return getOrCreateAnonymousSessionInternal(false)
    }

    override fun hasActive(): Boolean {
        val current = activeInstanceRef.value
        return current != null && current.sessionId != ANONYMOUS_SESSION_ID
    }

    // Instance-based access (primary approach) - with makeActive capability
    // Ensures session uniqueness: the same session ID always returns the same instance within this user/tenant scope
    override fun getById(
        sessionId: String,
        makeActive: Boolean,
    ): SessionInstance? {
        // Read the atomic value once
        val currentInstances = instances.value
        val existingInstance = currentInstances[sessionId]

        if (existingInstance != null && makeActive) {
            setActiveInstance(existingInstance)
        }

        return existingInstance
    }

    override fun hasById(sessionId: String): Boolean = instances.value.containsKey(sessionId)

    // Session switching (updates flows)
    override fun activateById(sessionId: String): Boolean {
        // Read the atomic value once
        val currentInstances = instances.value
        val instance = currentInstances[sessionId]
        return if (instance != null) {
            setActiveInstance(instance)
            true
        } else {
            false
        }
    }

    // Session listing
    override fun listIds(): Set<String> = instances.value.keys

    // Session creation (returns instances)
    override fun createOrGetFromCallbacks(sessionContextProvider: () -> SessionContext): SessionInstance {
        val runtimeSessionContext = sessionContextProvider()
        val sessionId = runtimeSessionContext.sessionId
        return getOrCreateSessionInternal(
            sessionId = sessionId,
            sessionContext = runtimeSessionContext,
            correlationId = runtimeSessionContext.correlationId,
            makeActive = true,
        ).instance
    }

    override fun createOrGetFromId(
        sessionId: String,
        correlationId: String,
        makeActive: Boolean,
    ): SessionInstance {
        requireNotNull(sessionId) { "sessionId must not be null" }
        return getOrCreateSessionInternal(sessionId, null, correlationId, makeActive).instance
    }

    // Session cleanup
    override fun destroyById(sessionId: String) {
        synchronized(this) {
            // Read the atomic value once
            val currentInstances = instances.value
            val instance = currentInstances[sessionId]
            if (instance != null) {
                // Remove from map first
                instances.value = currentInstances - sessionId

                // Clear active if this was the active session
                val currentActive = activeInstanceRef.value
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
            val instancesToDestroy = instances.value.values.toList()

            // Clear all state first
            instances.value = emptyMap()
            setActiveInstance(null)

            // Destroy scopes after state is cleared
            instancesToDestroy.forEach { it.scope.destroy() }
        }
    }

    // Special session types - return instances (convenience methods)
    override fun getOrCreateBackgroundService(makeActive: Boolean): SessionInstance {
        // Background sessions are ALWAYS created in the background service graph's scope
        val backgroundGraph = anonymousUserGraphManager.getBackgroundGraph()
        val backgroundScope = backgroundGraph.instance.scope
        val backgroundSessionManager = backgroundGraph.sessionContextManager

        // First check if it's already created (fast path)
        val existingInstance = backgroundSessionManager.getById(ANONYMOUS_SESSION_ID)
        if (existingInstance != null) {
            // Background service can never be made active
            return existingInstance
        }

        // Only create if we ARE the background session manager (prevent other managers from creating it)
        if (this !== backgroundSessionManager) {
            // We're not the background session manager, delegate to it
            return backgroundSessionManager.getOrCreateBackgroundService(false)
        }

        // We ARE the background session manager, create the session with synchronization
        return synchronized(this) {
            // Double-check after acquiring lock - read atomic value once
            val currentInstances = instances.value
            val existing = currentInstances[ANONYMOUS_SESSION_ID]
            if (existing != null) {
                return existing
            }

            val backgroundCorrelationId = IdentityConstants.ANONYMOUS_ID
            val anonymousSessionContext = createAnonymousSessionContext(ANONYMOUS_SESSION_ID, backgroundCorrelationId)
            val sessionGraph = sessionGraphFactory.createSessionGraph(ANONYMOUS_SESSION_ID, backgroundCorrelationId)

            val scope =
                backgroundScope.buildChild("session:$ANONYMOUS_SESSION_ID") {
                    addMetroDependencyGraph(sessionGraph)
                    addService("sessionContext", anonymousSessionContext)
                    addCoroutineScopeScoped(sessionGraph.sessionScopeCoroutineScopeScoped)
                }

            // Initialize the instance with its graph and scope
            val instance = sessionGraph.instance
            (instance as? SessionInstanceImpl)?.initialize(sessionGraph, scope)

            // Store the instance atomically
            instances.value = currentInstances + (ANONYMOUS_SESSION_ID to instance)

            // Background service can never be made active (no need to set activeInstanceRef)

            // Register instances after the instance is stored
            scope.register(sessionGraph.sessionScopedInstances)

            instance
        }
    }

    override fun getAnonymous(makeActive: Boolean): SessionInstance = getOrCreateAnonymousSessionInternal(makeActive)

    override fun getBackgroundServiceId(): String = ANONYMOUS_SESSION_ID

    // Private helper methods
    private fun getOrCreateAnonymousSessionInternal(makeActive: Boolean): SessionInstance {
        // Anonymous sessions are ALWAYS created in the anonymous user graph's scope
        val anonymousUserGraph = anonymousUserGraphManager.getAnonymousGraph()
        val anonymousScope = anonymousUserGraph.instance.scope
        val anonymousSessionManager = anonymousUserGraph.sessionContextManager

        // First check if it's already created (fast path)
        val existingInstance = anonymousSessionManager.getById(ANONYMOUS_SESSION_ID)
        if (existingInstance != null) {
            if (makeActive && this === anonymousSessionManager) {
                setActiveInstance(existingInstance)
            }
            return existingInstance
        }

        // Only create if we ARE the anonymous session manager (prevent other managers from creating it)
        if (this !== anonymousSessionManager) {
            // We're not the anonymous session manager, delegate to it
            return anonymousSessionManager.getAnonymous(false)
        }

        // We ARE the anonymous session manager, create the session with synchronization
        return synchronized(this) {
            // Double-check after acquiring lock - read atomic value once
            val currentInstances = instances.value
            val existing = currentInstances[ANONYMOUS_SESSION_ID]
            if (existing != null) {
                if (makeActive) {
                    setActiveInstance(existing)
                }
                return existing
            }

            val anonymousCorrelationId = IdentityConstants.ANONYMOUS_ID
            val anonymousSessionContext = createAnonymousSessionContext(ANONYMOUS_SESSION_ID, anonymousCorrelationId)
            val sessionGraph = sessionGraphFactory.createSessionGraph(ANONYMOUS_SESSION_ID, anonymousCorrelationId)

            val scope =
                anonymousScope.buildChild("session:$ANONYMOUS_SESSION_ID") {
                    addMetroDependencyGraph(sessionGraph)
                    addService("sessionContext", anonymousSessionContext)
                    addCoroutineScopeScoped(sessionGraph.sessionScopeCoroutineScopeScoped)
                }

            // Initialize the instance with its graph and scope
            val instance = sessionGraph.instance
            (instance as? SessionInstanceImpl)?.initialize(sessionGraph, scope)

            // Store the instance atomically
            instances.value = currentInstances + (ANONYMOUS_SESSION_ID to instance)

            // Set as active if requested
            if (makeActive) {
                setActiveInstance(instance)
            }

            // Register instances after the instance is stored and active
            scope.register(sessionGraph.sessionScopedInstances)

            instance
        }
    }

    private fun getOrCreateSessionInternal(
        sessionId: String,
        sessionContext: SessionContext?,
        correlationId: String,
        makeActive: Boolean,
    ): SessionGraph {
        // Fast-path: lock-free read if already created
        // Read the atomic value once to avoid multiple atomic reads
        val currentInstances = instances.value
        val existing = currentInstances[sessionId]
        if (existing != null) {
            if (makeActive) {
                setActiveInstance(existing)
            }
            return existing.graph
        }

        // Slow path: need to create, use synchronized block with double-check
        return synchronized(this) {
            // Double-check after acquiring lock - read atomic value once
            val lockedInstances = instances.value
            val lockedExisting = lockedInstances[sessionId]
            if (lockedExisting != null) {
                if (makeActive) {
                    setActiveInstance(lockedExisting)
                }
                return lockedExisting.graph
            }

            // Determine the correct scope for this session
            val contextScope =
                when {
                    isBackgroundContext() -> anonymousUserGraphManager.getBackgroundGraph().instance.scope
                    isAnonymousContext() -> anonymousUserGraphManager.getAnonymousGraph().instance.scope
                    else -> getContextScope()
                }

            // Create the session context
            val actualSessionContext =
                sessionContext ?: if (sessionId == ANONYMOUS_SESSION_ID) {
                    createAnonymousSessionContext(sessionId, correlationId)
                } else {
                    SessionContextImpl(
                        context = userContextManager.getActive().context,
                        sessionId = sessionId,
                        correlationId = correlationId,
                    )
                }

            // Create the session graph and scope
            val sessionGraph = sessionGraphFactory.createSessionGraph(sessionId, actualSessionContext.correlationId)
            val scope =
                contextScope.buildChild("session:$sessionId") {
                    addMetroDependencyGraph(sessionGraph)
                    addService("sessionContext", actualSessionContext)
                    addCoroutineScopeScoped(sessionGraph.sessionScopeCoroutineScopeScoped)
                }

            // Initialize the instance
            val instance = sessionGraph.instance
            (instance as? SessionInstanceImpl)?.initialize(sessionGraph, scope)

            // Store the instance atomically
            instances.value = lockedInstances + (sessionId to instance)

            // Set as active if requested
            if (makeActive) {
                setActiveInstance(instance)
            }

            // Register scoped instances
            scope.register(sessionGraph.sessionScopedInstances)

            sessionGraph
        }
    }

    /**
     * Sets the active instance atomically and updates the flow.
     * This ensures both the atomic reference and the StateFlow are kept in sync.
     */
    private fun setActiveInstance(instance: SessionInstance?) {
        activeInstanceRef.value = instance
        _activeInstance.value = instance
    }

    // Get the context scope from the active user context
    private fun getContextScope(): Scope = userContextManager.getActive().scope

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

    companion object {
        const val ANONYMOUS_SESSION_ID = IdentityConstants.ANONYMOUS_SESSION_ID
    }
}
