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

package com.sphereon.di.context

import kotlinx.coroutines.flow.StateFlow
import software.amazon.app.platform.scope.Scope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("UserContextManager", exact = true)
interface UserContextManager {

    // Primary access method - always returns instance (anonymous if map empty)
    fun getActive(): UserContextInstance

    /**
     * Returns true if there is an active context (anonymous or authenticated, but not background service).
     */
    fun hasActive(): Boolean

    /**
     * Returns true if there is an authenticated user context (not anonymous and not background service).
     */
    fun hasAuthenticated(): Boolean

    // Instance-based access (primary approach)
    fun get(tenantAware: TenantAware, principalAware: PrincipalAware, makeActive: Boolean = false): UserContextInstance?
    fun getById(contextId: String, makeActive: Boolean = false): UserContextInstance?
    fun has(tenantAware: TenantAware, principalAware: PrincipalAware): Boolean
    fun hasById(contextId: String): Boolean

    // Context switching (updates flows)
    fun activateById(contextId: String): Boolean
    fun activate(tenantAware: TenantAware, principalAware: PrincipalAware): Boolean

    // Context listing
    fun listIds(): Set<String>

    // Context creation (returns instances)
    fun createOrGetFromCallbacks(
        tenantInput: () -> TenantInput,
        principalInput: () -> PrincipalInput
    ): UserContextInstance

    fun createOrGetFromInputs(
        tenantInput: TenantInput,
        principalInput: PrincipalInput,
        makeActive: Boolean = true
    ): UserContextInstance

    fun createOrGet(
        tenantAware: TenantAware,
        principalAware: PrincipalAware,
        makeActive: Boolean = true
    ): UserContextInstance

    fun createOrGetFromData(
        tenantData: TenantContextData,
        principalValue: Any?,
        makeActive: Boolean = true
    ): UserContextInstance

    fun createOrGetWithId(
        contextId: String,
        tenantAware: TenantAware,
        principalAware: PrincipalAware,
        makeActive: Boolean = true
    ): UserContextInstance

    // Context cleanup
    fun destroyById(contextId: String)
    fun destroy(tenantAware: TenantAware, principalAware: PrincipalAware)
    fun destroyAll()

    // Singleton context instances - always available
    fun getBackgroundService(): UserContextInstance
    fun getAnonymous(makeActive: Boolean = false): UserContextInstance
    fun getBackgroundServiceId(): String

    // Utility methods (delegate to active context instance)
    fun isAnonymous(): Boolean


    val activeInstance: StateFlow<UserContextInstance>

    @ContributesTo(AppScope::class)
    interface Component {
        val userContextManager: UserContextManager
    }
}
