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
import com.sphereon.core.api.conf.ConfigBootstrapGuard
import com.sphereon.core.api.conf.PropertySourceBootstrap
import com.sphereon.core.api.conf.SecretProviderBootstrap
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.di.app.App
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.di.context.AnonymousContext
import com.sphereon.di.context.AnonymousUserGraphManager
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.PrincipalAware
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserContextGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import software.amazon.app.platform.scope.Scope
import software.amazon.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.amazon.app.platform.scope.di.metro.addMetroDependencyGraph
import software.amazon.app.platform.scope.register
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Manages singleton anonymous and background user contexts.
 *
 * This class ensures that:
 * - Anonymous user/tenant is always a singleton (cannot have more than one instance)
 * - Background user/tenant is always a singleton (cannot have more than one instance)
 * - Anonymous contexts are kept separate from regular user contexts
 * - Background contexts are kept separate from anonymous and regular user contexts
 * - Internal state is not exposed to the outside world
 * - Thread-safe access to singleton instances using atomic operations
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<AnonymousUserGraphManager>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("AnonymousUserGraphManagerImpl", exact = true)
class AnonymousUserGraphManagerImpl(
    private val rootScopeProvider: RootScopeProvider,
    private val contextGraphFactory: UserContextGraph.Factory,
    private val propertySourceBootstrap: PropertySourceBootstrap,
    private val app: App,
) : SynchronizedObject(),
    AnonymousUserGraphManager {
    /**
     * Holds both graph and scope together to ensure atomicity
     */
    private data class ContextHolder(
        val graph: UserContextGraph,
        val scope: Scope,
        val tenantId: String,
        val principalId: String,
    )

    // Atomic references for singleton contexts
    private val anonymousHolder: AtomicRef<ContextHolder?> = atomic(null)
    private val backgroundHolder: AtomicRef<ContextHolder?> = atomic(null)

    override fun getAnonymousGraph(): UserContextGraph {
        // Fast path: read without lock
        anonymousHolder.value?.let { return it.graph }

        // Slow path: need to create, use synchronized block
        return synchronized(this) {
            // Double-check: another thread might have initialized while we waited
            anonymousHolder.value?.let { return it.graph }

            val anonymousPrincipalAware =
                object : PrincipalAware {
                    override val principal = IdentityConstants.ANONYMOUS_PRINCIPAL_ID
                }

            val holder =
                createContextInternal(
                    contextId = UserContext.ANONYMOUS,
                    tenantContextData = AnonymousContext.tenant,
                    principal = anonymousPrincipalAware,
                )
            try {
                completeContextRegistration(holder)
                anonymousHolder.value = holder
            } catch (expected: Throwable) {
                holder.scope.destroy()
                throw expected
            }
            holder.graph
        }
    }

    override fun getBackgroundGraph(): UserContextGraph {
        // Fast path: read without lock
        backgroundHolder.value?.let { return it.graph }

        // Slow path: need to create, use synchronized block
        return synchronized(this) {
            // Double-check: another thread might have initialized while we waited
            backgroundHolder.value?.let { return it.graph }

            val anonymousPrincipalAware =
                object : PrincipalAware {
                    override val principal = IdentityConstants.ANONYMOUS_PRINCIPAL_ID
                }

            val holder =
                createContextInternal(
                    contextId = UserContext.BACKGROUND_SERVICE,
                    tenantContextData = AnonymousContext.tenant,
                    principal = anonymousPrincipalAware,
                )
            try {
                completeContextRegistration(holder)
                backgroundHolder.value = holder
            } catch (expected: Throwable) {
                holder.scope.destroy()
                throw expected
            }
            holder.graph
        }
    }

    override fun clearAnonymous() {
        synchronized(this) {
            // Atomically get and clear the holder
            val holder = anonymousHolder.getAndSet(null)

            // Destroy scope outside of atomic operation
            holder?.scope?.destroy()
        }
    }

    override fun clearBackground() {
        synchronized(this) {
            // Atomically get and clear the holder
            val holder = backgroundHolder.getAndSet(null)

            // Destroy scope outside of atomic operation
            holder?.scope?.destroy()
        }
    }

    override fun clearAll() {
        synchronized(this) {
            // Atomically get and clear both holders
            val anonymous = anonymousHolder.getAndSet(null)
            val background = backgroundHolder.getAndSet(null)

            // Destroy scopes outside of atomic operations
            anonymous?.scope?.destroy()
            background?.scope?.destroy()
        }
    }

    private fun createContextInternal(
        contextId: String,
        tenantContextData: TenantContextData,
        principal: PrincipalAware,
    ): ContextHolder {
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

        return ContextHolder(
            graph = contextGraph,
            scope = scope,
            tenantId = tenantContextData.tenantId,
            principalId = principal.principal?.toString() ?: IdentityConstants.ANONYMOUS_PRINCIPAL_ID,
        )
    }

    private fun completeContextRegistration(holder: ContextHolder) {
        ConfigBootstrapGuard.withContextRegistration {
            registerContextConfigSources(
                contextGraph = holder.graph,
                tenantId = holder.tenantId,
                principalId = holder.principalId,
            )

            // Register instances after the instance is initialized
            holder.scope.register(holder.graph.contextScopedInstances)
        }
    }

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
        (contextGraph as? SecretProviderBootstrap.UserGraph)?.userSecretProviderBootstrap?.registerSecretProviders()
    }
}
