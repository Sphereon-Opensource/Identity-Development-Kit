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
 */

package com.sphereon.core.api.http.dispatch

import com.sphereon.core.api.http.config.UniversalHttpConfig
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.di.HasOrder
import com.sphereon.di.Order
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default OSS implementation of [HttpAdapterCatalog].
 *
 * Applies [UniversalHttpConfig] overrides to adapter descriptions:
 * - Per-adapter mount overrides (serverPrefix, adapterBasePath, tenant settings)
 * - Adapter enablement (disabled adapters are excluded from the catalog)
 *
 * EDK can replace this by providing an implementation with lower [getOrder] value
 * (e.g., [Order.HIGH] vs this class's [Order.MEDIUM]).
 *
 * **Replacement pattern:**
 * ```kotlin
 * @Inject
 * @SingleIn(AppScope::class)
 * @ContributesBinding(AppScope::class, binding = binding<HttpAdapterCatalog>())
 * class EdkHttpAdapterCatalog(...) : HttpAdapterCatalog, HasOrder {
 *     override fun getOrder(): Int = Order.HIGH.orderValue // Wins over OSS default
 *     // ...
 * }
 * ```
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<HttpAdapterCatalog>())
class DefaultHttpAdapterCatalog(
    descriptorProviders: Set<HttpAdapterDescriptorProvider>,
    private val httpConfig: UniversalHttpConfig
) : HttpAdapterCatalog, HasOrder {

    override fun getOrder(): Int = Order.MEDIUM.orderValue

    override val descriptions: List<HttpAdapterDescription> = descriptorProviders
        .filter { httpConfig.isAdapterEnabled(it.id) }
        .map { provider ->
            val desc = provider.describe()
            desc.copy(mount = httpConfig.resolveMount(desc.id, desc.mount))
        }
        .sortedBy { it.id }

    override val diagnostics: HttpAdapterCatalogDiagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)

    override fun describeAll(): List<HttpAdapterDescription> = descriptions

    override fun descriptionById(id: String): HttpAdapterDescription? = descriptions.firstOrNull { it.id == id }

    override fun requireNoCollisions() {
        val collisions = diagnostics.collisions
        require(collisions.isEmpty()) {
            "HttpAdapterCatalog has collisions:\n" + collisions.joinToString("\n") { it.message }
        }
    }

    /**
     * Default binding for [UniversalHttpConfig].
     *
     * Provides [UniversalHttpConfig.DEFAULT] (no overrides) when no explicit
     * config is provided. VDX or EDK can replace this by contributing their
     * own `@Provides` with a config loaded from ConfigService.
     */
    @ContributesTo(AppScope::class)
    interface DefaultConfigComponent {
        @Provides
        fun provideUniversalHttpConfig(): UniversalHttpConfig = UniversalHttpConfig.DEFAULT
    }
}
