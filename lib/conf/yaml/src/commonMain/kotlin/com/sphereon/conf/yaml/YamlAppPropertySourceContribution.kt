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
import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.PropertySourceContribution
import com.sphereon.di.Order
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Bridges [YamlFileAppPropertySource] into the [PropertySourceContribution] set
 * so that the bootstrap registers the YAML config at startup.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<PropertySourceContribution>())
class YamlAppPropertySourceContribution(
    private val yamlSource: YamlFileAppPropertySource,
) : PropertySourceContribution {
    override val configLevel: ConfigLevel = ConfigLevel.APP
    override val providerId: String = "yaml.app"

    override fun isEnabled(resolver: PropertyResolver): Boolean = true

    override fun getPropertySource(): PropertySource<*> = yamlSource

    // File-backed bootstrap configuration must be registered before providers whose own
    // construction depends on it (for example the PostgreSQL settings property source).
    // The source itself still keeps YamlPropertySourceImpl's LOW precedence, so this changes
    // bootstrap sequencing only; environment and higher-precedence sources continue to win.
    override fun getOrder(): Int = Order.HIGH.orderValue
}
