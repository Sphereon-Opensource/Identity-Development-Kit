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

@file:Suppress("UNCHECKED_CAST")

package com.sphereon.core.defaults.context

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.Order
import com.sphereon.di.context.PrincipalAware
import com.sphereon.di.context.PrincipalInput
import com.sphereon.di.context.PrincipalInputString
import com.sphereon.di.context.PrincipalResolutionHandler
import com.sphereon.di.context.PrincipalResolver
import com.sphereon.di.context.TenantAware
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.TenantInput
import com.sphereon.di.context.TenantInputString
import com.sphereon.di.context.TenantResolutionHandler
import com.sphereon.di.context.TenantResolver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("TenantContextDataImpl", exact = true)
class TenantContextDataImpl(
    override val tenantId: String,
) : TenantContextData {
    override fun toString() = tenantId

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as TenantContextDataImpl

        return tenantId == other.tenantId
    }

    override fun hashCode(): Int = tenantId.hashCode()
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("TenantResolutionHandlerImpl", exact = true)
class TenantResolutionHandlerImpl(
    tenantResolvers: Set<TenantResolver>,
) : TenantResolutionHandler {
    val tenantResolvers = tenantResolvers.sorted()

    override suspend fun resolveTenant(tenantInput: TenantInput): TenantAware {
        val tenantId =
            tenantResolvers.find { it.supports(tenantInput) }?.resolveTenant(tenantInput) ?: throw IllegalArgumentException("TenantResolver not found for input: $tenantInput")
        return object : TenantAware {
            override val tenant = TenantContextDataImpl(tenantId)
        }
    }

    /**
     * Graph interface to expose TenantResolutionHandler publicly from the AppGraph.
     */
    @ContributesTo(AppScope::class)
    interface Graph {
        val tenantResolutionHandler: TenantResolutionHandler
    }
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("PrincipalResolutionHandlerImpl", exact = true)
class PrincipalResolutionHandlerImpl(
    principalResolvers: Set<PrincipalResolver>,
) : PrincipalResolutionHandler {
    val principalResolvers = principalResolvers.sorted()

    override fun resolvePrincipal(
        principalInput: PrincipalInput,
        tenantAware: TenantAware,
    ): PrincipalAware {
        val principal =
            principalResolvers.find { it.supports(principalInput) }?.resolvePrincipal(principalInput, tenantAware)
                ?: throw IllegalArgumentException("PrincipalResolver not found for input: $principalInput")
        return object : PrincipalAware {
            override val principal = principal

            override fun toString(): String = principal.toString()
        }
    }

    /**
     * Graph interface to expose PrincipalResolutionHandler publicly from the AppGraph.
     */
    @ContributesTo(AppScope::class)
    interface Graph {
        val principalResolutionHandler: PrincipalResolutionHandler
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultTenantInputString", exact = true)
data class DefaultTenantInputString(
    override val tenant: String,
) : TenantInputString {
    override fun toString(): String = "TenantInput(tenant='$tenant')"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as DefaultTenantInputString

        return tenant == other.tenant
    }

    override fun hashCode(): Int = tenant.hashCode()
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultPrincipalInputString", exact = true)
data class DefaultPrincipalInputString(
    override val principal: String,
) : PrincipalInputString {
    override fun toString(): String = "PrincipalInput(principal='$principal')"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as DefaultPrincipalInputString

        return principal == other.principal
    }

    override fun hashCode(): Int = principal.hashCode()
}

/**
 * Input type for host-based tenant resolution (subdomain-based tenancy).
 *
 * @property host The Host header value (e.g., "acme.example.com")
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("HostTenantInput", exact = true)
data class HostTenantInput(
    val host: String,
) : TenantInput {
    override val tenant: Any get() = host
}

/**
 * Input type for path-based tenant resolution.
 *
 * @property path The URL path prefix (e.g., "/tenants/acme/keys")
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("PathTenantInput", exact = true)
data class PathTenantInput(
    val path: String,
) : TenantInput {
    override val tenant: Any get() = path
}

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<TenantResolver>())
class StaticTenantResolver : TenantResolver {
    override val order: Int = Order.LOWEST.orderValue

    override fun supports(tenantInput: TenantInput) = tenantInput.tenant is String && tenantInput.asString().isNotBlank()

    override suspend fun resolveTenant(tenantInput: TenantInput) = tenantInput.asString().trim().lowercase()
}

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<TenantResolver>())
class EmailDomainTenantResolver : TenantResolver {
    override val order: Int = Order.MEDIUM.orderValue

    override fun supports(tenantInput: TenantInput) = tenantInput.tenant is String && tenantInput.asString().contains("@")

    override suspend fun resolveTenant(tenantInput: TenantInput): String =
        tenantInput
            .asString()
            .substringAfter("@")
            .trim()
            .lowercase()
}

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<PrincipalResolver>())
class StaticPrincipalResolver : PrincipalResolver {
    override val priority: Int = Order.LOWEST.orderValue

    override fun supports(principalInput: PrincipalInput) = principalInput.principal is String && principalInput.asString().isNotBlank()

    override fun resolvePrincipal(
        principalInput: PrincipalInput,
        tenant: TenantAware,
    ): String = principalInput.asString().trim()
}

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<PrincipalResolver>())
class EmailPrincipalResolver : PrincipalResolver {
    override val priority: Int = Order.MEDIUM.orderValue

    override fun supports(principalInput: PrincipalInput) = principalInput.principal is String && principalInput.asString().contains("@")

    override fun resolvePrincipal(
        principalInput: PrincipalInput,
        tenant: TenantAware,
    ): String = principalInput.asString().trim().lowercase()
}

private fun Any.asString(): String =
    when (this) {
        is TenantInputString -> this.tenant
        is PrincipalInputString -> this.principal
        is TenantInput -> this.tenant.toString()
        is PrincipalInput -> this.principal.toString()
        else -> this.toString()
    }
