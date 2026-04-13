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
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import com.sphereon.core.api.conf.AppConfigEnvironment
import com.sphereon.core.api.conf.ConfigEnvironment
import com.sphereon.core.api.conf.DefaultMapPropertySourceFactory
import com.sphereon.core.api.conf.DefaultPropertySources
import com.sphereon.core.api.conf.PropertyInterpolator
import com.sphereon.core.api.conf.SecretResolver
import com.sphereon.core.api.conf.StaticProtectedEnvPropertySourceObject
import com.sphereon.core.api.conf.SyncConfigSnapshotCache
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.binding


@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<AppConfigEnvironment>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("AppConfigEnvironmentImpl", exact = true)
class AppConfigEnvironmentImpl(
    @Named("appId") appId: String,
    @Named("profile") profile: String,
    snapshotCache: SyncConfigSnapshotCache,
    interpolator: PropertyInterpolator?,
    secretResolver: SecretResolver?
) : AbstractConfigEnvironment(
    profile = profile,
    appId = appId,
    propertySources = DefaultPropertySources(mutableListOf(StaticProtectedEnvPropertySourceObject, DefaultMapPropertySourceFactory.app())),
    snapshotCache = snapshotCache,
    tenantId = null,      // APP scope: no tenant partitioning
    principalId = null,
    interpolator = interpolator,
    secretResolver = secretResolver
), AppConfigEnvironment {
    override val parent: ConfigEnvironment? = null
}
