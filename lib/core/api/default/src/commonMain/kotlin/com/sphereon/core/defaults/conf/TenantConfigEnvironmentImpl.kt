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

package com.sphereon.core.defaults.conf

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.api.conf.AppConfigEnvironment
import com.sphereon.core.api.conf.DefaultMapPropertySourceFactory
import com.sphereon.core.api.conf.DefaultPropertySources
import com.sphereon.core.api.conf.PropertyInterpolator
import com.sphereon.core.api.conf.SecretResolver
import com.sphereon.core.api.conf.StaticProtectedEnvPropertySourceObject
import com.sphereon.core.api.conf.SyncConfigSnapshotCache
import com.sphereon.core.api.conf.TenantConfigEnvironment
import dev.zacsweers.metro.Named
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<TenantConfigEnvironment>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("TenantConfigEnvironmentImpl", exact = true)
class TenantConfigEnvironmentImpl(
    @Named("appId") appId: String,
    @Named("profile") profile: String,
    userContextInstance: UserContextInstance,
    snapshotCache: SyncConfigSnapshotCache,
    override val parent: AppConfigEnvironment,
    interpolator: PropertyInterpolator?,
    secretResolver: SecretResolver?
) : AbstractConfigEnvironment(
    profile = profile,
    appId = appId,
    propertySources = DefaultPropertySources(mutableListOf(StaticProtectedEnvPropertySourceObject, DefaultMapPropertySourceFactory.tenant())),
    snapshotCache = snapshotCache,
    tenantId = userContextInstance.context.tenant.tenantId,
    principalId = null,
    interpolator = interpolator,
    secretResolver = secretResolver
), TenantConfigEnvironment
