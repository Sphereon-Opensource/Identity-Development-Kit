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

package com.sphereon.core.defaults.context

import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.PropertiesFilePrincipalPropertySource
import com.sphereon.core.api.conf.PropertiesFileTenantPropertySource
import com.sphereon.core.api.conf.PropertySourceBootstrap
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.di.app.App
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.di.context.AnonymousUserGraphManager
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.PrincipalAware
import com.sphereon.di.context.PrincipalInput
import com.sphereon.di.context.PrincipalResolutionHandler
import com.sphereon.di.context.TenantAware
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.TenantInput
import com.sphereon.di.context.TenantResolutionHandler
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserContextGraph
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserContextManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import software.amazon.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.amazon.app.platform.scope.coroutine.coroutineScope
import software.amazon.app.platform.scope.di.metro.addMetroDependencyGraph
import software.amazon.app.platform.scope.register
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<UserContextManager>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("UserContextManagerImpl", exact = true)
class UserContextManagerImpl(
    private val rootScopeProvider: RootScopeProvider,
    private val contextGraphFactory: UserContextGraph.Factory,
    private val principalResolutionHandler: PrincipalResolutionHandler,
    private val tenantResolutionHandler: TenantResolutionHandler,
    private val anonymousUserGraphManager: AnonymousUserGraphManager,
    private val propertySourceBootstrap: PropertySourceBootstrap,
    appLogManager: AppLogManager,
    val app: App,
) : SynchronizedObject(),
    UserContextManager {
    private val log = appLogManager.withTag("UserContextManager")

    init {
        check(!rootScopeProvider.isDestroyed()) {
            "Root scope has not been initialized yet. Call init() on the App Graph first"
        }
    }

    // Internal map for management (only for regular user contexts, not anonymous or background)
    // Each UserContextInstance already contains graph, scope, and context
    // Using AtomicRef for thread-safe access
    private val instances: AtomicRef<Map<String, UserContextInstance>> = atomic(emptyMap())

    // Active instance tracking (atomic reference for thread-safe reads)
    private val activeAuthenticatedInstance: AtomicRef<UserContextInstance?> = atomic(null)

    // Hot flow that emits the active instance
    // Note: This flow is backed by AtomicRef but exposed as StateFlow for API compatibility
    private val _activeInstance = MutableStateFlow<UserContextInstance?>(null)

    override val activeInstance: StateFlow<UserContextInstance> by lazy {
        _activeInstance
            .map {
                it ?: anonymousUserGraphManager.getAnonymousGraph().instance
            }.stateIn(
                rootScopeProvider.rootScope.coroutineScope(),
                SharingStarted.Lazily,
                anonymousUserGraphManager.getAnonymousGraph().instance,
            )
    }

    // Primary access method - always returns instance (anonymous if map empty)
    override fun getActive(): UserContextInstance {
        // Fast path: lock-free read
        return activeAuthenticatedInstance.value ?: anonymousUserGraphManager.getAnonymousGraph().instance
    }

    override fun hasActive(): Boolean {
        // Returns true if there's an active context (anonymous or authenticated, but NOT background service)
        val current = activeAuthenticatedInstance.value
        return current != null && current.contextId != UserContext.BACKGROUND_SERVICE
    }

    override fun hasAuthenticated(): Boolean {
        // Check if there's at least one regular user context (excluding anonymous and background)
        return instances.value.isNotEmpty()
    }

    // Instance-based access (primary approach) - with makeActive capability
    override fun get(
        tenantAware: TenantAware,
        principalAware: PrincipalAware,
        makeActive: Boolean,
    ): UserContextInstance? {
        val contextId = generateContextId(tenantAware.tenant, principalAware.principal)
        return getById(contextId, makeActive)
    }

    override fun getById(
        contextId: String,
        makeActive: Boolean,
    ): UserContextInstance? {
        // Check if it's a special context managed by AnonymousUserGraphManager
        val existingInstance =
            when (contextId) {
                UserContext.ANONYMOUS -> anonymousUserGraphManager.getAnonymousGraph().instance
                UserContext.BACKGROUND_SERVICE -> anonymousUserGraphManager.getBackgroundGraph().instance
                else -> instances.value[contextId]
            }

        // Background service can NEVER be made active
        if (existingInstance != null && makeActive && contextId != UserContext.BACKGROUND_SERVICE) {
            setActiveInstance(existingInstance)
        }

        return existingInstance
    }

    override fun has(
        tenantAware: TenantAware,
        principalAware: PrincipalAware,
    ): Boolean {
        val contextId = generateContextId(tenantAware.tenant, principalAware.principal)
        return hasById(contextId)
    }

    override fun hasById(contextId: String): Boolean =
        when (contextId) {
            UserContext.ANONYMOUS -> true

            // Anonymous context always exists
            UserContext.BACKGROUND_SERVICE -> true

            // Background context always exists
            else -> instances.value.containsKey(contextId)
        }

    // Context switching (updates flows)
    override fun activateById(contextId: String): Boolean {
        // Background service can NEVER be activated
        if (contextId == UserContext.BACKGROUND_SERVICE) {
            return false
        }

        val instance =
            when (contextId) {
                UserContext.ANONYMOUS -> anonymousUserGraphManager.getAnonymousGraph().instance
                else -> instances.value[contextId]
            }
        return if (instance != null) {
            setActiveInstance(instance)
            true
        } else {
            false
        }
    }

    override fun activate(
        tenantAware: TenantAware,
        principalAware: PrincipalAware,
    ): Boolean {
        val contextId = generateContextId(tenantAware.tenant, principalAware.principal)
        return activateById(contextId)
    }

    // Context listing
    override fun listIds(): Set<String> {
        val regularIds = instances.value.keys
        // Anonymous and background contexts always exist
        return regularIds + setOf(UserContext.ANONYMOUS, UserContext.BACKGROUND_SERVICE)
    }

    // Context creation (returns instances)
    override fun createOrGetFromCallbacks(
        tenantInput: () -> TenantInput,
        principalInput: () -> PrincipalInput,
    ): UserContextInstance = createOrGetFromInputs(tenantInput(), principalInput())

    override fun createOrGetFromInputs(
        tenantInput: TenantInput,
        principalInput: PrincipalInput,
        makeActive: Boolean,
    ): UserContextInstance {
        val tenantContext = tenantResolutionHandler.resolveTenant(tenantInput)
        val principal = principalResolutionHandler.resolvePrincipal(principalInput, tenantContext)
        return createOrGet(tenantContext, principal, makeActive)
    }

    override fun createOrGet(
        tenantAware: TenantAware,
        principalAware: PrincipalAware,
        makeActive: Boolean,
    ): UserContextInstance = createOrGetContextInternal(tenantAware.tenant, principalAware, makeActive).instance

    override fun createOrGetFromData(
        tenantData: TenantContextData,
        principalValue: Any?,
        makeActive: Boolean,
    ): UserContextInstance {
        val principalAware =
            object : PrincipalAware {
                override val principal = principalValue
            }
        return createOrGetContextInternal(tenantData, principalAware, makeActive).instance
    }

    override fun createOrGetWithId(
        contextId: String,
        tenantAware: TenantAware,
        principalAware: PrincipalAware,
        makeActive: Boolean,
    ): UserContextInstance {
        // If requesting a special context ID, delegate to the appropriate method
        return when (contextId) {
            UserContext.ANONYMOUS -> {
                val graph = anonymousUserGraphManager.getAnonymousGraph()
                if (makeActive) {
                    setActiveInstance(graph.instance)
                }
                graph.instance
            }

            UserContext.BACKGROUND_SERVICE -> {
                // Background service can NEVER be made active
                val graph = anonymousUserGraphManager.getBackgroundGraph()
                graph.instance
            }

            else -> {
                createOrGetContextInternal(tenantAware.tenant, principalAware, makeActive, contextId).instance
            }
        }
    }

    // Context cleanup
    override fun destroyById(contextId: String) {
        when (contextId) {
            UserContext.ANONYMOUS -> {
                anonymousUserGraphManager.clearAnonymous()
            }

            UserContext.BACKGROUND_SERVICE -> {
                anonymousUserGraphManager.clearBackground()
            }

            else -> {
                synchronized(this) {
                    val instance = instances.value[contextId]
                    if (instance != null) {
                        // Remove from map first
                        instances.value = instances.value - contextId

                        // Clear active if this was the active context
                        if (activeAuthenticatedInstance.value?.contextId == contextId) {
                            setActiveInstance(null)
                        }

                        // Destroy scope last
                        instance.scope.destroy()
                    }
                }
            }
        }
    }

    override fun destroy(
        tenantAware: TenantAware,
        principalAware: PrincipalAware,
    ) {
        val contextId = generateContextId(tenantAware.tenant, principalAware.principal)
        destroyById(contextId)
    }

    override fun destroyAll() {
        synchronized(this) {
            // Capture all instances to destroy
            val instancesToDestroy = instances.value.values.toList()

            // Clear all state first
            instances.value = emptyMap()
            setActiveInstance(null)

            // Destroy regular contexts after clearing state
            instancesToDestroy.forEach { it.scope.destroy() }

            // Destroy special contexts managed by AnonymousUserManager
            anonymousUserGraphManager.clearAll()
        }
    }

    // Singleton context instances - always available
    override fun getBackgroundService(): UserContextInstance {
        // Background service can NEVER be made active
        val graph = anonymousUserGraphManager.getBackgroundGraph()
        return graph.instance
    }

    override fun getAnonymous(makeActive: Boolean): UserContextInstance {
        val graph = anonymousUserGraphManager.getAnonymousGraph()
        if (makeActive) {
            setActiveInstance(graph.instance)
        }
        return graph.instance
    }

    override fun getBackgroundServiceId(): String = UserContext.BACKGROUND_SERVICE

    // Utility methods (delegate to active context instance)
    override fun isAnonymous(): Boolean {
        val activeInstance = activeAuthenticatedInstance.value ?: return true
        return activeInstance.context.principal == IdentityConstants.ANONYMOUS_PRINCIPAL_ID
    }

    // Private helper methods

    private fun createOrGetContextInternal(
        tenantContextData: TenantContextData,
        principal: PrincipalAware,
        makeActive: Boolean,
        contextId: String = generateContextId(tenantContextData, principal.principal),
    ): UserContextGraph {
        // Fast path: lock-free read if already exists
        instances.value[contextId]?.let { instance ->
            if (makeActive) {
                setActiveInstance(instance)
            }
            return instance.graph
        }

        // Slow path: need to create, use synchronized block with double-check
        return synchronized(this) {
            // Double-check after acquiring lock
            instances.value[contextId]?.let { instance ->
                if (makeActive) {
                    setActiveInstance(instance)
                }
                return instance.graph
            }

            // Create new context
            val context = UserContextImpl(tenant = tenantContextData, principal = principal.principal)
            val contextGraph = contextGraphFactory.createUserContext(context)

            val scope =
                rootScopeProvider.rootScope.buildChild("${app.appId}:${app.profile}:$contextId") {
                    addMetroDependencyGraph(contextGraph)
                    addService("context", context)
                    addCoroutineScopeScoped(contextGraph.contextScopeCoroutineScopeScoped)
                }

            // Initialize the instance with its graph and scope
            val instance = contextGraph.instance

            (instance as? UserContextInstanceImpl)?.initialize(contextGraph, scope)

            registerContextConfigSources(
                contextGraph = contextGraph,
                tenantId = tenantContextData.tenantId,
                principalId = principal.principal?.toString() ?: IdentityConstants.ANONYMOUS_PRINCIPAL_ID,
            )

            log.debug("##################################")
            log.debug("##################################")
            log.debug("##################################")
            log.debug("instance context: ${instance.context}")
            log.debug("##################################")
            log.debug("##################################")

            // Store the instance atomically
            instances.value = instances.value + (contextId to instance)

            // Set as active if requested (before registration to ensure getActive() returns correct instance)
            if (makeActive) {
                setActiveInstance(instance)
            }

            // Register instances after the instance is stored and active
            scope.register(contextGraph.contextScopedInstances)

            contextGraph
        }
    }

    /**
     * Sets the active instance atomically and updates the flow.
     * This ensures both the atomic reference and the StateFlow are kept in sync.
     */
    private fun setActiveInstance(instance: UserContextInstance?) {
        activeAuthenticatedInstance.value = instance
        _activeInstance.value = instance
    }

    private fun generateContextId(
        tenant: TenantContextData,
        principalValue: Any?,
        suffix: String = "default",
    ): String = "${tenant.tenantId}:$principalValue:$suffix"

    private fun registerContextConfigSources(
        contextGraph: UserContextGraph,
        tenantId: String,
        principalId: String,
    ) {
        val tenantConfigService = (contextGraph as? TenantConfigService.Graph)?.tenantConfigService
        val tenantPropertySource = (contextGraph as? PropertiesFileTenantPropertySource.Graph)?.propertiesFileTenantPropertySource
        if (tenantConfigService != null && tenantPropertySource != null) {
            val localSources = tenantConfigService.getPropertySources(includeParents = false)
            if (!localSources.contains(tenantPropertySource.getName())) {
                tenantConfigService.addPropertySource(tenantPropertySource)
            }
        }

        val principalConfigService = (contextGraph as? PrincipalConfigService.Graph)?.principalConfigService
        val principalPropertySource = (contextGraph as? PropertiesFilePrincipalPropertySource.Graph)?.propertiesFilePrincipalPropertySource
        if (principalConfigService != null && principalPropertySource != null) {
            val localSources = principalConfigService.getPropertySources(includeParents = false)
            if (!localSources.contains(principalPropertySource.getName())) {
                principalConfigService.addPropertySource(principalPropertySource)
            }
        }

        tenantConfigService?.let { propertySourceBootstrap.registerTenantSources(it, tenantId) }
        principalConfigService?.let { propertySourceBootstrap.registerPrincipalSources(it, tenantId, principalId) }
    }
}
