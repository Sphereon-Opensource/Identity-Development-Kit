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

package com.sphereon.core.api.context

import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import com.sphereon.core.api.log.UserContextLogManager
import com.sphereon.core.api.session.ISessionCommandExecutor
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserContextComponent
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionInstance
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

fun UserContextInstance.asCoreApiContextComponent(): CoreApiContextExtensionComponent = this.component as CoreApiContextExtensionComponent
fun UserContextComponent.asCoreApiContextComponent(): CoreApiContextExtensionComponent = this as CoreApiContextExtensionComponent


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
@ObjCName("CoreApiContextExtensionComponent", exact = true)
@SingleIn(UserScope::class)
@ContributesTo(UserScope::class)
interface CoreApiContextExtensionComponent {


    fun asSureComponent(): UserContextComponent = this as UserContextComponent

    val _sessionContextManager: SessionContextManager
    val logManager: UserContextLogManager
    val conf: ContextConfig
    val serviceExecutor: ISessionCommandExecutor
    fun loggerWithTag(tag: String = "sphereon") = logManager.withTagAsync(tag)


    fun createExecutionContextComponent(context: UserContext, sessionId: String): SessionInstance {
        return _sessionContextManager.createOrGetFromId(sessionId = sessionId)
    }

}
