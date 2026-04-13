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

package com.sphereon.core.defaults.context

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.PropertiesFilePrincipalPropertySource
import com.sphereon.core.api.conf.PropertiesFileTenantPropertySource
import com.sphereon.core.api.conf.PropertySourceBootstrap
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.di.app.App
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.di.context.AnonymousContext
import com.sphereon.di.context.AnonymousUserComponentManager
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.PrincipalAware
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserContextComponent
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import dev.zacsweers.metro.Inject
import software.amazon.app.platform.scope.Scope
import software.amazon.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.amazon.app.platform.scope.di.metro.addMetroDependencyGraph
import software.amazon.app.platform.scope.register
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

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
@ContributesBinding(AppScope::class, binding = binding<AnonymousUserComponentManager>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("AnonymousUserComponentManagerImpl", exact = true)
class AnonymousUserComponentManagerImpl(
    private val rootScopeProvider: RootScopeProvider,
    private val contextComponentFactory: UserContextComponent.Factory,
    private val propertySourceBootstrap: PropertySourceBootstrap,
    private val app: App
) : AnonymousUserComponentManager, SynchronizedObject() {

    /**
     * Holds both component and scope together to ensure atomicity
     */
    private data class ContextHolder(
        val component: UserContextComponent,
        val scope: Scope
    )

    // Atomic references for singleton contexts
    private val anonymousHolder: AtomicRef<ContextHolder?> = atomic(null)
    private val backgroundHolder: AtomicRef<ContextHolder?> = atomic(null)

    override fun getAnonymousComponent(): UserContextComponent {
        // Fast path: read without lock
        anonymousHolder.value?.let { return it.component }

        // Slow path: need to create, use synchronized block
        return synchronized(this) {
            // Double-check: another thread might have initialized while we waited
            anonymousHolder.value?.let { return it.component }

            val anonymousPrincipalAware = object : PrincipalAware {
                override val principal = IdentityConstants.ANONYMOUS_PRINCIPAL_ID
            }

            val holder = createContextInternal(
                contextId = UserContext.ANONYMOUS,
                tenantContextData = AnonymousContext.tenant,
                principal = anonymousPrincipalAware
            ).let { ContextHolder(it.first, it.second) }

            anonymousHolder.value = holder
            holder.component
        }
    }

    override fun getBackgroundComponent(): UserContextComponent {
        // Fast path: read without lock
        backgroundHolder.value?.let { return it.component }

        // Slow path: need to create, use synchronized block
        return synchronized(this) {
            // Double-check: another thread might have initialized while we waited
            backgroundHolder.value?.let { return it.component }

            val anonymousPrincipalAware = object : PrincipalAware {
                override val principal = IdentityConstants.ANONYMOUS_PRINCIPAL_ID
            }

            val holder = createContextInternal(
                contextId = UserContext.BACKGROUND_SERVICE,
                tenantContextData = AnonymousContext.tenant,
                principal = anonymousPrincipalAware
            ).let { ContextHolder(it.first, it.second) }

            backgroundHolder.value = holder
            holder.component
        }
    }

    override fun clearAnonymous(): Unit {
        synchronized(this) {
            // Atomically get and clear the holder
            val holder = anonymousHolder.getAndSet(null)

            // Destroy scope outside of atomic operation
            holder?.scope?.destroy()
        }
    }

    override fun clearBackground(): Unit {
        synchronized(this) {
            // Atomically get and clear the holder
            val holder = backgroundHolder.getAndSet(null)

            // Destroy scope outside of atomic operation
            holder?.scope?.destroy()
        }
    }

    override fun clearAll(): Unit {
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
        principal: PrincipalAware
    ): Pair<UserContextComponent, Scope> {
        // Create new context
        val context = UserContextImpl(tenant = tenantContextData, principal = principal.principal)
        val contextComponent = contextComponentFactory.createUserContext(context)

        val scope = rootScopeProvider.rootScope.buildChild("${app.appId}:${app.profile}:$contextId") {
            addMetroDependencyGraph(contextComponent)
            addService("context", context)
            addCoroutineScopeScoped(contextComponent.contextScopeCoroutineScopeScoped)
        }

        // Initialize the instance with its component and scope
        val instance = contextComponent.instance
        (instance as? UserContextInstanceImpl)?.initialize(contextComponent, scope)

        registerContextConfigSources(
            contextComponent = contextComponent,
            tenantId = tenantContextData.tenantId,
            principalId = principal.principal?.toString() ?: IdentityConstants.ANONYMOUS_PRINCIPAL_ID
        )

        // Register instances after the instance is initialized
        scope.register(contextComponent.contextScopedInstances)

        return contextComponent to scope
    }

    private fun registerContextConfigSources(contextComponent: UserContextComponent, tenantId: String, principalId: String) {
        val tenantConfigService = (contextComponent as? TenantConfigService.Component)?.tenantConfigService
        val tenantPropertySource = (contextComponent as? PropertiesFileTenantPropertySource.Component)?.propertiesFileTenantPropertySource
        if (tenantConfigService != null && tenantPropertySource != null) {
            val localSources = tenantConfigService.getPropertySources(includeParents = false)
            if (!localSources.contains(tenantPropertySource.getName())) {
                tenantConfigService.addPropertySource(tenantPropertySource)
            }
        }

        val principalConfigService = (contextComponent as? PrincipalConfigService.Component)?.principalConfigService
        val principalPropertySource = (contextComponent as? PropertiesFilePrincipalPropertySource.Component)?.propertiesFilePrincipalPropertySource
        if (principalConfigService != null && principalPropertySource != null) {
            val localSources = principalConfigService.getPropertySources(includeParents = false)
            if (!localSources.contains(principalPropertySource.getName())) {
                principalConfigService.addPropertySource(principalPropertySource)
            }
        }

        tenantConfigService?.let { propertySourceBootstrap.registerTenantSources(it, tenantId) }
        principalConfigService?.let { propertySourceBootstrap.registerPrincipalSources(it, tenantId, principalId) }
    }

    /**
     * Component interface to expose AnonymousUserComponentManager publicly from the AppComponent.
     */
    @ContributesTo(AppScope::class)
    interface Component {
        val anonymousUserComponentManager: AnonymousUserComponentManager
    }
}
