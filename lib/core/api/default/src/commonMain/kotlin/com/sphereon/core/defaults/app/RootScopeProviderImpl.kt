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

package com.sphereon.core.defaults.app

import com.sphereon.di.app.AppComponent
import com.sphereon.di.app.RootScopeProvider
import software.amazon.app.platform.scope.Scope
import software.amazon.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.amazon.app.platform.scope.di.metro.addMetroDependencyGraph
import software.amazon.app.platform.scope.register

/**
 * Shared class between the platform to manage the root scope. It itself implements the
 * [software.amazon.app.platform.scope.RootScopeProvider] interface.
 */
class DefaultRootScopeProvider : RootScopeProvider {

    private var _rootScope: Scope? = null

    override val rootScope: Scope
        get() = checkNotNull(_rootScope) { "Must call create() first." }

    override fun isDestroyed() = _rootScope == null

    /** Creates the root scope and remembers the instance. */
    override fun create(appComponent: AppComponent) {
        check(_rootScope == null) { "create() should be called only once." }

        _rootScope =
            Scope.buildRootScope {
                addMetroDependencyGraph(appComponent)
                addCoroutineScopeScoped(appComponent.appScopeCoroutineScopeScoped)
            }

        // Register instances after the rootScope has been set to avoid race conditions for Scoped
        // instances that may use the rootScope.
        rootScope.register(appComponent.appScopedInstances)
    }

    /** Destroys the root scope. */
    override fun destroy() {
        rootScope.destroy()
        _rootScope = null
    }

}
