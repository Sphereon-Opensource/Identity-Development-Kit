/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.conf.theme.core.resolve

import com.sphereon.conf.theme.core.model.Application
import com.sphereon.conf.theme.core.model.ProductType
import com.sphereon.conf.theme.core.model.ThemeDefinition
import com.sphereon.conf.theme.core.model.ThemeScope
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.store.InMemoryThemeStore
import com.sphereon.conf.theme.core.token.buildTokens
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultThemeResolverLayeringTest {
    private val tenant = "acme"
    private val applicationId = "app-1"

    private suspend fun storeWithApplication(): InMemoryThemeStore {
        val store = InMemoryThemeStore()
        store.saveApplication(
            tenant,
            Application(
                applicationId = applicationId,
                tenantId = tenant,
                productType = ProductType.WEB_WALLET,
                name = "Customer Wallet",
            ),
        )
        return store
    }

    private fun definition(
        id: String,
        scope: ThemeScope,
        primary: String,
        variant: ThemeVariant? = null,
        productType: ProductType? = null,
        applicationId: String? = null,
    ): ThemeDefinition =
        ThemeDefinition(
            id = id,
            name = id,
            scope = scope,
            variant = variant,
            productType = productType,
            applicationId = applicationId,
            tokens = buildTokens { color("color.primary", primary) },
        )

    @Test
    fun productScopeBeatsSystemBaseline() =
        runTest {
            val store = storeWithApplication()
            store.saveDefinition(tenant, definition("product", ThemeScope.PRODUCT, "#101010", productType = ProductType.WEB_WALLET))

            val resolved = DefaultThemeResolver(store).resolve(tenant, applicationId = applicationId)

            assertEquals("#101010", resolved.tokens["color.primary"])
        }

    @Test
    fun tenantScopeBeatsProductScope() =
        runTest {
            val store = storeWithApplication()
            store.saveDefinition(tenant, definition("product", ThemeScope.PRODUCT, "#101010", productType = ProductType.WEB_WALLET))
            store.saveDefinition(tenant, definition("tenant", ThemeScope.TENANT, "#202020"))

            val resolved = DefaultThemeResolver(store).resolve(tenant, applicationId = applicationId)

            assertEquals("#202020", resolved.tokens["color.primary"])
        }

    @Test
    fun applicationScopeBeatsTenantScope() =
        runTest {
            val store = storeWithApplication()
            store.saveDefinition(tenant, definition("tenant", ThemeScope.TENANT, "#202020"))
            store.saveDefinition(tenant, definition("application", ThemeScope.APPLICATION, "#303030", applicationId = applicationId))

            val resolved = DefaultThemeResolver(store).resolve(tenant, applicationId = applicationId)

            assertEquals("#303030", resolved.tokens["color.primary"])
        }

    @Test
    fun variantSpecificBeatsVariantNullWithinLayer() =
        runTest {
            val store = storeWithApplication()
            store.saveDefinition(tenant, definition("tenant", ThemeScope.TENANT, "#202020"))
            store.saveDefinition(tenant, definition("tenant-dark", ThemeScope.TENANT, "#202021", variant = ThemeVariant.DARK))

            val resolved = DefaultThemeResolver(store).resolve(tenant, variant = ThemeVariant.DARK)

            assertEquals("#202021", resolved.tokens["color.primary"])
        }

    @Test
    fun variantSpecificLayerDoesNotApplyToOtherVariants() =
        runTest {
            val store = storeWithApplication()
            store.saveDefinition(tenant, definition("tenant", ThemeScope.TENANT, "#202020"))
            store.saveDefinition(tenant, definition("tenant-dark", ThemeScope.TENANT, "#202021", variant = ThemeVariant.DARK))

            val resolvedLight = DefaultThemeResolver(store).resolve(tenant, variant = ThemeVariant.LIGHT)
            val resolvedCommon = DefaultThemeResolver(store).resolve(tenant)

            assertEquals("#202020", resolvedLight.tokens["color.primary"])
            assertEquals("#202020", resolvedCommon.tokens["color.primary"])
        }

    @Test
    fun applicationVariantSpecificBeatsEveryLowerLayer() =
        runTest {
            val store = storeWithApplication()
            store.saveDefinition(tenant, definition("product", ThemeScope.PRODUCT, "#101010", productType = ProductType.WEB_WALLET))
            store.saveDefinition(
                tenant,
                definition("product-dark", ThemeScope.PRODUCT, "#101011", variant = ThemeVariant.DARK, productType = ProductType.WEB_WALLET),
            )
            store.saveDefinition(tenant, definition("tenant", ThemeScope.TENANT, "#202020"))
            store.saveDefinition(tenant, definition("tenant-dark", ThemeScope.TENANT, "#202021", variant = ThemeVariant.DARK))
            store.saveDefinition(tenant, definition("application", ThemeScope.APPLICATION, "#303030", applicationId = applicationId))
            store.saveDefinition(
                tenant,
                definition("application-dark", ThemeScope.APPLICATION, "#303031", variant = ThemeVariant.DARK, applicationId = applicationId),
            )

            val resolved = DefaultThemeResolver(store).resolve(tenant, variant = ThemeVariant.DARK, applicationId = applicationId)

            assertEquals("#303031", resolved.tokens["color.primary"])
        }

    @Test
    fun productAndApplicationLayersAreSkippedWithoutApplicationId() =
        runTest {
            val store = storeWithApplication()
            store.saveDefinition(tenant, definition("product", ThemeScope.PRODUCT, "#101010", productType = ProductType.WEB_WALLET))
            store.saveDefinition(tenant, definition("application", ThemeScope.APPLICATION, "#303030", applicationId = applicationId))

            val resolved = DefaultThemeResolver(store).resolve(tenant)
            val baselineOnly = DefaultThemeResolver(InMemoryThemeStore()).resolve(tenant)

            assertEquals(baselineOnly.tokens["color.primary"], resolved.tokens["color.primary"])
        }

    @Test
    fun productLayerUsesTheApplicationsProductType() =
        runTest {
            val store = storeWithApplication()
            store.saveDefinition(tenant, definition("product-wallet", ThemeScope.PRODUCT, "#101010", productType = ProductType.WEB_WALLET))
            store.saveDefinition(tenant, definition("product-portal", ThemeScope.PRODUCT, "#404040", productType = ProductType.PORTAL))

            val resolved = DefaultThemeResolver(store).resolve(tenant, applicationId = applicationId)

            assertEquals("#101010", resolved.tokens["color.primary"])
        }

    @Test
    fun resolvedThemeCarriesTenantAndApplicationIds() =
        runTest {
            val store = storeWithApplication()

            val withApplication = DefaultThemeResolver(store).resolve(tenant, applicationId = applicationId)
            val withoutApplication = DefaultThemeResolver(store).resolve(tenant)

            assertEquals(tenant, withApplication.tenantId)
            assertEquals(applicationId, withApplication.applicationId)
            assertEquals(tenant, withoutApplication.tenantId)
            assertNull(withoutApplication.applicationId)
        }
}
