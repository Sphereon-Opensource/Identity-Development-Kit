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
import com.sphereon.core.api.conf.PrincipalConfigEnvironment
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Principal-level YAML property source interface.
 *
 * Loads from:
 * - `{configLocation}/tenant/{tenantId}/principal/{principalId}/principal.yml` - principal-specific config
 * - `{configLocation}/tenant/{tenantId}/principal/{principalId}/principal-{profile}.yml` - principal+profile overrides
 *
 * File-based principal config is for dev/staging.
 * Production multi-tenancy uses database or cloud providers.
 */
interface YamlFilePrincipalPropertySource : YamlPropertySource {
    @ContributesTo(UserScope::class)
    interface Graph {
        val yamlFilePrincipalPropertySource: YamlFilePrincipalPropertySource
    }
}

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<YamlFilePrincipalPropertySource>())
class YamlFilePrincipalPropertySourceImpl(
    configEnvironment: PrincipalConfigEnvironment,
    userContextInstance: UserContextInstance,
) : YamlPropertySourceImpl(
        name = NAME,
        sourceLevel = ConfigLevel.PRINCIPAL,
        configLocation = configEnvironment.getConfigLocation(),
        profile = configEnvironment.getActiveProfile(),
        filePrefix = FILE_PREFIX,
        subPath = "$TENANT_DIR/${userContextInstance.context.tenant.tenantId}/$PRINCIPAL_DIR/${userContextInstance.context.principal}",
    ),
    YamlFilePrincipalPropertySource {
    companion object {
        const val NAME = "yaml.principal"
        const val FILE_PREFIX = "principal"
        const val TENANT_DIR = "tenant"
        const val PRINCIPAL_DIR = "principal"
    }
}
