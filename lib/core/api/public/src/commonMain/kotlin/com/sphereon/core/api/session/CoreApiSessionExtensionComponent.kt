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

package com.sphereon.core.api.session

import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionComponent
import com.sphereon.di.session.SessionInstance
import com.sphereon.di.session.SessionScope
import kotlin.experimental.ExperimentalObjCName
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.native.ObjCName

fun SessionInstance.asCoreApiServiceComponent(): CoreApiSessionExtensionComponent = this.component as CoreApiSessionExtensionComponent

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
fun SessionComponent.asCoreApiServiceComponent(): CoreApiSessionExtensionComponent = this as CoreApiSessionExtensionComponent

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
@ObjCName("CoreApiSessionExtensionComponent", exact = true)
@SingleIn(SessionScope::class)
@ContributesTo(SessionScope::class)
interface CoreApiSessionExtensionComponent {

    fun asSureComponent(): SessionComponent  = this as SessionComponent

    val serviceExecution: SessionExecution
    val sessionContext: SessionContext

    val logManager: SessionLogManager
    fun logger() = loggerWithTag()
    fun loggerWithTag(tag: String = sessionContext.sessionId) = logManager.withTagAsync(tag)
}
