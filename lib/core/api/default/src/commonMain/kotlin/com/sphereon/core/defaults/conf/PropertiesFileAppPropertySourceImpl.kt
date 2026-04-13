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

import com.sphereon.core.api.conf.AppConfigEnvironment
import com.sphereon.core.api.conf.PropertiesFileAppPropertySource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * App-level property source that loads properties from application property files.
 *
 * Loads from:
 * - `{configLocation}/application.properties` - base app config
 * - `{configLocation}/application-{profile}.properties` - profile-specific overrides
 *
 * Registered explicitly during app startup.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PropertiesFileAppPropertySource>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertiesFileAppPropertySourceImpl", exact = true)
class PropertiesFileAppPropertySourceImpl(
    configEnvironment: AppConfigEnvironment,
) : AbstractProtectedPropertiesFilePropertySource(
        name = NAME,
        sourceLevel = com.sphereon.core.api.conf.ConfigLevel.APP,
        configLocation = configEnvironment.getConfigLocation(),
        profile = configEnvironment.getActiveProfile(),
        filePrefix = FILE_PREFIX,
    ),
    PropertiesFileAppPropertySource {
    companion object {
        const val NAME = "properties-file-app"
        const val FILE_PREFIX = "application"
    }
}
