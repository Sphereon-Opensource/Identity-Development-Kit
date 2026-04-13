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


package com.sphereon.core.api.app

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.core.api.log.UserContextLogManager
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.context.CoreApiContextExtensionComponent
import com.sphereon.core.api.context.asCoreApiContextComponent
import com.sphereon.core.api.session.AppCommandExecutor
import com.sphereon.di.app.AppComponent
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

//fun AppComponent.asCoreApAppComponent(): ICoreApiAppExtensionComponent = this as ICoreApiAppExtensionComponent

/**
 * Represents a subcomponent within the `TenantScope` lifecycle, responsible for managing tenant-specific dependencies.
 * This component is designed to provide scoped resources and functionality for a particular tenant context.
 *
 * An instance of this component can be created using the associated `Factory` by providing a valid `TenantContext`.
 * Normally this should be handled by the `TenantManager` in the application scope.
 * The main injections are the `TenantContext`, containing Tenant information and a coroutine scope that is bound by the tenant context
 *
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CoreApiAppExtensionComponent", exact = true)
@SingleIn(AppScope::class)
@ContributesTo(AppScope::class)
interface CoreApiAppExtensionComponent {

    /**
     * Used to provide an anonymous context scope. Mainly useful for libraries and global logging. Always use context and/or session scopes instead of this component when available
     */
    val anonymousContextComponent: Lazy<CoreApiContextExtensionComponent>
        get() = lazy { asAppComponent().userContextManager.getAnonymous().asCoreApiContextComponent() }


    fun asAppComponent(): AppComponent = this as AppComponent

    // Since all our loggers require the context scope, we use the above anonymous scope and fetch the context logger from there
    val appContextLogManager: UserContextLogManager
        get() = anonymousContextComponent.value.logManager

    val appLogManager: AppLogManager
    val appConfig: AppConfigService
    fun appLoggerWithTag(tag: String = "sphereon") = appContextLogManager.withTagAsync(tag)
    fun appLogger() = appLoggerWithTag()

    val serviceExecutor: AppCommandExecutor

}
