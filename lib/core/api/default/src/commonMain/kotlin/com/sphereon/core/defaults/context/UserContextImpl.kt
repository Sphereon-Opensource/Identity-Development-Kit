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
import com.sphereon.di.context.SecuredTenantContextDetails
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.UserContext

/**
 * WARNING: Please note. Although this class is provided as an argument in the UserComponent Factory when creating UserContextComponents, it is not injectable in the UserScope!
 * User UserContextInstance for injections, which does have a property that contains the user context, or directly get the user context from the UserContextComponent.
 *
 * This is likely a bug in kotlin-inject-anvil, as Factory arguments when creating a component should be injectable in the scope of that component
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("UserContextImpl", exact = true)
data class UserContextImpl(
    override val tenant: TenantContextData,
    override val principal: Any?,
    override val secureDetails: SecuredTenantContextDetails? = null,
    override val id: String = "${tenant.tenantId}:${principal}:default"
) :
    UserContext {


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as UserContextImpl

        if (tenant != other.tenant) return false
        if (principal != other.principal) return false
        if (secureDetails != other.secureDetails) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tenant.hashCode()
        result = 31 * result + principal.hashCode()
        result = 31 * result + (secureDetails?.hashCode() ?: 0)
        return result
    }


    override fun toString(): String {
        return "Context(tenant=$tenant, principal=$principal, secureDetails=$secureDetails)"
    }
}

