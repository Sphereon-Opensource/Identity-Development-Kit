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

import com.sphereon.core.api.conf.AppConfigEnvironment
import com.sphereon.core.api.conf.ConfigLevel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * App-level YAML property source interface.
 *
 * Loads from:
 * - `{configLocation}/application.yml` - base app config
 * - `{configLocation}/application-{profile}.yml` - profile-specific overrides
 * - Classpath fallback if no filesystem file is found
 */
interface YamlFileAppPropertySource : YamlPropertySource {
    @ContributesTo(AppScope::class)
    interface Graph {
        val yamlFileAppPropertySource: YamlFileAppPropertySource
    }
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<YamlFileAppPropertySource>())
class YamlFileAppPropertySourceImpl(
    configEnvironment: AppConfigEnvironment,
) : YamlPropertySourceImpl(
        name = NAME,
        sourceLevel = ConfigLevel.APP,
        configLocation = configEnvironment.getConfigLocation(),
        profile = configEnvironment.getActiveProfile(),
        filePrefix = FILE_PREFIX,
    ),
    YamlFileAppPropertySource {
    companion object {
        const val NAME = "yaml.app"
        const val FILE_PREFIX = "application"
    }
}
