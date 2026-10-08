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

import com.sphereon.core.api.conf.PropertiesFileTenantPropertySource
import com.sphereon.core.api.conf.TenantConfigEnvironment
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Tenant-level property source that loads properties from tenant-specific property files.
 *
 * Loads from:
 * - `{configLocation}/tenant/{tenantId}/tenant.properties` - tenant-specific config
 * - `{configLocation}/tenant/{tenantId}/tenant-{profile}.properties` - tenant+profile overrides
 *
 * Registered explicitly during user-context startup.
 */
@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<PropertiesFileTenantPropertySource>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertiesFileTenantPropertySourceImpl", exact = true)
class PropertiesFileTenantPropertySourceImpl(
    configEnvironment: TenantConfigEnvironment,
    userContextInstance: UserContextInstance,
) : AbstractProtectedPropertiesFilePropertySource(
        name = NAME,
        sourceLevel = com.sphereon.core.api.conf.ConfigLevel.TENANT,
        configLocation = configEnvironment.getConfigLocation(),
        profile = configEnvironment.getActiveProfile(),
        filePrefix = FILE_PREFIX,
        subPath = "$TENANT_DIR/${userContextInstance.context.tenant.tenantId}",
    ),
    PropertiesFileTenantPropertySource {
    companion object {
        const val NAME = "properties-file-tenant"
        const val FILE_PREFIX = "tenant"
        const val TENANT_DIR = "tenant"
    }
}
