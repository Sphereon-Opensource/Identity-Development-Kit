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

package com.sphereon.core.defaults.conf

import com.sphereon.core.api.conf.DefaultMapPropertySourceFactory
import com.sphereon.core.api.conf.DefaultPropertySources
import com.sphereon.core.api.conf.PrincipalConfigEnvironment
import com.sphereon.core.api.conf.PropertyInterpolator
import com.sphereon.core.api.conf.InterpolationPolicyProvider
import com.sphereon.core.api.conf.StaticProtectedEnvPropertySourceObject
import com.sphereon.core.api.conf.SyncConfigSnapshotCache
import com.sphereon.core.api.conf.TenantConfigEnvironment
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<PrincipalConfigEnvironment>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("PrincipalConfigEnvironmentImpl", exact = true)
class PrincipalConfigEnvironmentImpl(
    @Named("appId") appId: String,
    @Named("profile") profile: String,
    userContextInstance: UserContextInstance,
    snapshotCache: SyncConfigSnapshotCache,
    override val parent: TenantConfigEnvironment,
    interpolator: PropertyInterpolator?,
    interpolationPolicyProvider: InterpolationPolicyProvider,
) : AbstractConfigEnvironment(
        profile = profile,
        appId = appId,
        propertySources = DefaultPropertySources(mutableListOf(StaticProtectedEnvPropertySourceObject, DefaultMapPropertySourceFactory.principal())),
        snapshotCache = snapshotCache,
        tenantId = userContextInstance.context.tenant.tenantId,
        principalId = userContextInstance.context.principal?.toString() ?: IdentityConstants.ANONYMOUS_PRINCIPAL_ID,
        interpolator = interpolator,
        interpolationPolicyProvider = interpolationPolicyProvider,
    ),
    PrincipalConfigEnvironment {
    override fun getNamespace(): String = super<AbstractConfigEnvironment>.getNamespace()
}
