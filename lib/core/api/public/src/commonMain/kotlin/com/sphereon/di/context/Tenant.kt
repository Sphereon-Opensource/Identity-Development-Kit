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

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import com.sphereon.di.Order
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("TenantAware", exact = true)
@JsExportCompat
interface TenantAware {
    val tenant: TenantContextData
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("PrincipalAware", exact = true)
@JsExportCompat
interface PrincipalAware {
    val principal: Any?
}


@OptIn(ExperimentalObjCName::class)
@ObjCName("TenantContextData", exact = true)
@JsExportCompat
interface TenantContextData {
    val tenantId: String
}




@OptIn(ExperimentalObjCName::class)
@ObjCName("TenantResolutionHandler", exact = true)
interface TenantResolutionHandler {
    fun resolveTenant(tenantInput: TenantInput): TenantAware
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("PrincipalResolutionHandler", exact = true)
interface PrincipalResolutionHandler {
    fun resolvePrincipal(principalInput: PrincipalInput, tenantAware: TenantAware): PrincipalAware
}

/**
 * Marker interface for Tenant context input for the resolution process
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TenantInput", exact = true)
interface TenantInput {
    val tenant: Any
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("TenantInputString", exact = true)
interface TenantInputString : TenantInput {
    override val tenant: String
}


/**
 * Marker interface for Tenant context input for the resolution process
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PrincipalInput", exact = true)
interface PrincipalInput {
    val principal: Any
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("PrincipalInputString", exact = true)
interface PrincipalInputString : PrincipalInput {
    override val principal: String
}






@OptIn(ExperimentalObjCName::class)
@ObjCName("TenantResolver", exact = true)
interface TenantResolver : Comparable<TenantResolver> {
    fun resolveTenant(tenantInput: TenantInput): String
    val order: Int get() = Order.MEDIUM.orderValue
    fun supports(tenantInput: TenantInput): Boolean

    override fun compareTo(other: TenantResolver): Int = when {
        this.order != other.order -> this.order compareTo other.order
        else -> 1 // We return 1 as the objects are not equal. Whichever we have seen first takes precedence
    }

    @SingleIn(AppScope::class)
    @ContributesTo(AppScope::class)
    interface Component {
        // TODO: Probably wise to add these to the app component and make them private
        val tenantResolvers: Set<TenantResolver>
    }

}


@OptIn(ExperimentalObjCName::class)
@ObjCName("PrincipalResolver", exact = true)
interface PrincipalResolver : Comparable<PrincipalResolver> {
    fun resolvePrincipal(principalInput: PrincipalInput, tenant: TenantAware): String
    val priority: Int get() = Order.MEDIUM.orderValue
    fun supports(principalInput: PrincipalInput): Boolean

    override fun compareTo(other: PrincipalResolver): Int = when {
        this.priority != other.priority -> this.priority compareTo other.priority
        else -> 1 // We return 1 as the objects are not equal. Whichever we have seen first takes precedence
    }


    @SingleIn(AppScope::class)
    @ContributesTo(AppScope::class)
    interface Component {
        // TODO: Probably wise to add these to the app component and make them private
        val principalResolvers: Set<PrincipalResolver>
    }
}


