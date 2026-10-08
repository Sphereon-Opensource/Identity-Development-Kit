/*
 * (c) 2026 Sphereon International B.V.
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
 */

package com.sphereon.conf.yaml

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.TenantConfigEnvironment
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Tenant-level YAML property source interface.
 *
 * Loads from:
 * - `{configLocation}/tenant/{tenantId}/tenant.yml` - tenant-specific config
 * - `{configLocation}/tenant/{tenantId}/tenant-{profile}.yml` - tenant+profile overrides
 *
 * File-based tenant config is for dev/staging with known tenants.
 * Production multi-tenancy uses database or cloud providers.
 */
interface YamlFileTenantPropertySource : YamlPropertySource {
    @ContributesTo(UserScope::class)
    interface Graph {
        val yamlFileTenantPropertySource: YamlFileTenantPropertySource
    }
}

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<YamlFileTenantPropertySource>())
class YamlFileTenantPropertySourceImpl(
    configEnvironment: TenantConfigEnvironment,
    userContextInstance: UserContextInstance,
) : YamlPropertySourceImpl(
        name = NAME,
        sourceLevel = ConfigLevel.TENANT,
        configLocation = configEnvironment.getConfigLocation(),
        profile = configEnvironment.getActiveProfile(),
        filePrefix = FILE_PREFIX,
        subPath = "$TENANT_DIR/${userContextInstance.context.tenant.tenantId}",
    ),
    YamlFileTenantPropertySource {
    companion object {
        const val NAME = "yaml.tenant"
        const val FILE_PREFIX = "tenant"
        const val TENANT_DIR = "tenant"
    }
}
