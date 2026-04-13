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

package com.sphereon.core.api.conf

import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Property source interface for loading app-level properties from files.
 *
 * Loads properties from:
 * - `{configLocation}/application.properties` - base app config
 * - `{configLocation}/application-{profile}.properties` - profile-specific overrides
 *
 * Properties are loaded without prefix - the context (app/tenant/principal) is determined
 * by which file the property is defined in.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertiesFileAppPropertySource", exact = true)
interface PropertiesFileAppPropertySource : PropertySource<MutableMap<String, Any>> {
    @ContributesTo(AppScope::class)
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Component", exact = true)
    interface Component {
        val propertiesFileAppPropertySource: PropertiesFileAppPropertySource
    }
}

/**
 * Property source interface for loading tenant-level properties from files.
 *
 * Loads properties from:
 * - `{configLocation}/tenant/{tenantId}/tenant.properties` - tenant-specific config
 * - `{configLocation}/tenant/{tenantId}/tenant-{profile}.properties` - tenant+profile overrides
 *
 * Properties are loaded without prefix - the context (app/tenant/principal) is determined
 * by which file the property is defined in.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertiesFileTenantPropertySource", exact = true)
interface PropertiesFileTenantPropertySource : PropertySource<MutableMap<String, Any>> {
    @ContributesTo(UserScope::class)
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Component", exact = true)
    interface Component {
        val propertiesFileTenantPropertySource: PropertiesFileTenantPropertySource
    }
}

/**
 * Property source interface for loading principal-level properties from files.
 *
 * Loads properties from:
 * - `{configLocation}/tenant/{tenantId}/principal/{principalId}/principal.properties` - principal-specific config
 * - `{configLocation}/tenant/{tenantId}/principal/{principalId}/principal-{profile}.properties` - principal+profile overrides
 *
 * Properties are loaded without prefix - the context (app/tenant/principal) is determined
 * by which file the property is defined in.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertiesFilePrincipalPropertySource", exact = true)
interface PropertiesFilePrincipalPropertySource : PropertySource<MutableMap<String, Any>> {
    @ContributesTo(UserScope::class)
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Component", exact = true)
    interface Component {
        val propertiesFilePrincipalPropertySource: PropertiesFilePrincipalPropertySource
    }
}
