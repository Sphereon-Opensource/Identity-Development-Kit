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

import com.sphereon.core.api.conf.PrincipalConfigEnvironment
import com.sphereon.core.api.conf.PropertiesFilePrincipalPropertySource
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Principal-level property source that loads properties from principal-specific property files.
 *
 * Loads from:
 * - `{configLocation}/tenant/{tenantId}/principal/{principalId}/principal.properties` - principal-specific config
 * - `{configLocation}/tenant/{tenantId}/principal/{principalId}/principal-{profile}.properties` - principal+profile overrides
 *
 * Registered explicitly during user-context startup.
 */
@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<PropertiesFilePrincipalPropertySource>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertiesFilePrincipalPropertySourceImpl", exact = true)
class PropertiesFilePrincipalPropertySourceImpl(
    configEnvironment: PrincipalConfigEnvironment,
    userContextInstance: UserContextInstance
) : PropertiesFilePrincipalPropertySource, AbstractProtectedPropertiesFilePropertySource(
    name = NAME,
    sourceLevel = com.sphereon.core.api.conf.ConfigLevel.PRINCIPAL,
    configLocation = configEnvironment.getConfigLocation(),
    profile = configEnvironment.getActiveProfile(),
    filePrefix = FILE_PREFIX,
    subPath = "$TENANT_DIR/${userContextInstance.context.tenant.tenantId}/$PRINCIPAL_DIR/${userContextInstance.context.principal}"
) {
    companion object {
        const val NAME = "properties-file-principal"
        const val FILE_PREFIX = "principal"
        const val TENANT_DIR = "tenant"
        const val PRINCIPAL_DIR = "principal"
    }
}
