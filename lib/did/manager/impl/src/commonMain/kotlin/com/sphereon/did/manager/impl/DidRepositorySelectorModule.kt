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
 */

package com.sphereon.did.manager.impl

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.did.persistence.DidPersistenceConfig
import com.sphereon.did.persistence.DidRepository
import com.sphereon.did.persistence.DidRepositoryFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * App-scope provider for [DidRepository], driven by `did.persistence.*` configuration.
 *
 * Each persistence dialect contributes a [DidRepositoryFactory] into the multimap (keyed by
 * [DidPersistenceConfig.type]). At graph init this provider:
 *   1. Reads [DidPersistenceConfig] from the [AppConfigService].
 *   2. Looks up the matching factory by `config.type`.
 *   3. Calls `factory.createRepository(config)` and returns the result.
 *
 * Defaults to [DidPersistenceConfig.TYPE_MEMORY] when no `did.persistence.type` property is
 * set, so existing dev/test workflows continue to start without extra configuration. If the
 * configured type has no matching factory on the classpath, fails fast with a clear message
 * listing the contributed dialects.
 */
@ContributesTo(AppScope::class)
interface DidRepositorySelectorModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideDidRepository(
        configService: AppConfigService,
        factories: Map<String, DidRepositoryFactory>,
        logManager: AppLogManager,
    ): DidRepository {
        val config = DidPersistenceConfigBinder.bind(configService)
        logManager.withTag("DidPersistence").info(
            "DID repository: type=${config.type}" +
                (config.connectionUrl?.let { ", connectionUrl=${redactJdbcUrl(it)}" } ?: ""),
        )
        return DidRepositorySelector.select(config, factories)
    }
}

/**
 * Strips userinfo (`user:password@`) and password query parameters from a JDBC URL so the
 * sanitised form can be safely written to logs.
 */
internal fun redactJdbcUrl(url: String): String {
    val withoutUserinfo = Regex("(jdbc:[^:]+://)([^@/]+@)").replace(url, "$1")
    return Regex("([?&](?:password|pwd))=[^&]*", RegexOption.IGNORE_CASE)
        .replace(withoutUserinfo, "$1=***")
}

/**
 * Pure selector logic, extracted for unit-testability without spinning up Metro or a
 * full ConfigService. The `@Provides` glue above wires it into the app graph.
 */
object DidRepositorySelector {
    fun select(
        config: DidPersistenceConfig,
        factories: Map<String, DidRepositoryFactory>,
    ): DidRepository {
        val factory =
            factories[config.type]
                ?: error(
                    "No DidRepositoryFactory contributed for did.persistence.type='${config.type}'. " +
                        "Contributed dialects: ${factories.keys.sorted().joinToString(", ").ifEmpty { "(none)" }}. " +
                        "Add the matching persistence module to the classpath, or set did.persistence.type to a supported dialect.",
                )
        return factory.createRepository(config)
    }
}
