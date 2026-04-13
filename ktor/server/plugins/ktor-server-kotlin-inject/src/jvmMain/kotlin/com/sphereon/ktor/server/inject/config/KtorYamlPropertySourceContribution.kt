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

package com.sphereon.ktor.server.inject.config

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.PropertySourceContribution
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.di.Order
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet

/**
 * Auto-registration contribution for Ktor YAML APP-level property source.
 *
 * When the ktor-server-kotlin-inject module is on the classpath, this contribution
 * automatically registers the Ktor YAML property source for APP-level settings
 * via the [PropertySourceContribution] mechanism.
 *
 * The YAML property source reads configuration from application.yml files
 * and is always enabled when on the classpath.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<PropertySourceContribution>())
class KtorYamlAppPropertySourceContribution(
    private val ktorYamlAppPropertySource: KtorYamlAppPropertySource,
    appLogManager: AppLogManager
) : PropertySourceContribution {

    private val log = appLogManager.withTag("KtorYamlAppPropertySource")

    override val configLevel: ConfigLevel = ConfigLevel.APP

    override val providerId: String = PROVIDER_ID

    override fun isEnabled(resolver: PropertyResolver): Boolean = true

    override fun getPropertySource(): PropertySource<*> {
        log.debug("Registering with AppConfigEnvironment")
        return (ktorYamlAppPropertySource as PropertySource<*>).also {
            log.debug("Registration complete")
        }
    }

    override fun getOrder(): Int = Order.HIGH.orderValue

    override val requiresAsyncInit: Boolean = false

    companion object {
        const val PROVIDER_ID = "ktor-yaml"
    }
}
