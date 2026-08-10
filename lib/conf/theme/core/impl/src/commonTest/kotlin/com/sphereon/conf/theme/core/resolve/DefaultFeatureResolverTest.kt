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

import com.sphereon.conf.theme.core.feature.DefaultFeatureRegistry
import com.sphereon.conf.theme.core.feature.FeatureDescriptorProvider
import com.sphereon.conf.theme.core.model.AssetDesignElement
import com.sphereon.conf.theme.core.model.AssetElementValue
import com.sphereon.conf.theme.core.model.ChoiceDesignElement
import com.sphereon.conf.theme.core.model.ChoiceElementValue
import com.sphereon.conf.theme.core.model.ElementBinding
import com.sphereon.conf.theme.core.model.ElementOrigin
import com.sphereon.conf.theme.core.model.FeatureDefinition
import com.sphereon.conf.theme.core.model.ProductType
import com.sphereon.conf.theme.core.model.TextDesignElement
import com.sphereon.conf.theme.core.model.TextElementValue
import com.sphereon.conf.theme.core.model.ThemeDefinition
import com.sphereon.conf.theme.core.model.ThemeScope
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.store.InMemoryThemeStore
import com.sphereon.conf.theme.core.token.TokenKeyConstants
import com.sphereon.conf.theme.core.token.buildTokens
import com.sphereon.conf.theme.core.model.ThemeAssetReference
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultFeatureResolverTest {
    private val tenant = "acme"
    private val applicationId = "as-1"
    private val productType = ProductType.AUTHORIZATION_SERVER
    private val featureId = "login"

    private val loginFeature =
        FeatureDefinition(
            featureId = featureId,
            productType = productType,
            name = "Sign-in screen",
            builtIn = true,
            elements =
                listOf(
                    AssetDesignElement(
                        elementId = "logo",
                        fallbackTokenKey = TokenKeyConstants.BRANDING_LOGO_URL,
                    ),
                    AssetDesignElement(
                        elementId = "background",
                        default = ThemeAssetReference(uri = "/defaults/login-background.png"),
                    ),
                    TextDesignElement(
                        elementId = "tagline",
                        fallbackTokenKey = TokenKeyConstants.BRANDING_TAGLINE,
                    ),
                    TextDesignElement(
                        elementId = "legalNotice",
                        required = true,
                    ),
                ),
        )

    private class LoginFeatureProvider(
        override val productType: ProductType,
        private val provided: List<FeatureDefinition>,
    ) : FeatureDescriptorProvider {
        override fun features(): List<FeatureDefinition> = provided
    }

    private fun resolver(store: InMemoryThemeStore): DefaultFeatureResolver {
        val registry = DefaultFeatureRegistry(setOf(LoginFeatureProvider(productType, listOf(loginFeature))), store)
        val themeResolver = DefaultThemeResolver(store)
        return DefaultFeatureResolver(registry, store, Provider { themeResolver })
    }

    private fun logoBinding(
        uri: String,
        applicationId: String? = null,
        variant: ThemeVariant? = null,
    ): ElementBinding =
        ElementBinding(
            productType = productType,
            featureId = featureId,
            elementId = "logo",
            variant = variant,
            applicationId = applicationId,
            value = AssetElementValue(ThemeAssetReference(uri = uri)),
        )

    @Test
    fun applicationBindingWithMatchingVariantWins() =
        runTest {
            val store = InMemoryThemeStore()
            store.setElementBinding(tenant, logoBinding("https://cdn.example.com/app-dark.svg", applicationId, ThemeVariant.DARK))
            store.setElementBinding(tenant, logoBinding("https://cdn.example.com/app.svg", applicationId))
            store.setElementBinding(tenant, logoBinding("https://cdn.example.com/tenant-dark.svg", variant = ThemeVariant.DARK))
            store.setElementBinding(tenant, logoBinding("https://cdn.example.com/tenant.svg"))

            val resolved = resolver(store).resolve(tenant, productType, featureId, applicationId, ThemeVariant.DARK)

            val logo = assertNotNull(resolved).elements["logo"]
            assertNotNull(logo)
            assertEquals(AssetElementValue(ThemeAssetReference(uri = "https://cdn.example.com/app-dark.svg")), logo.value)
            assertEquals(ElementOrigin.APPLICATION, logo.origin)
        }

    @Test
    fun applicationBindingWithVariantNullWinsWhenNoVariantMatch() =
        runTest {
            val store = InMemoryThemeStore()
            store.setElementBinding(tenant, logoBinding("https://cdn.example.com/app.svg", applicationId))
            store.setElementBinding(tenant, logoBinding("https://cdn.example.com/tenant-dark.svg", variant = ThemeVariant.DARK))
            store.setElementBinding(tenant, logoBinding("https://cdn.example.com/tenant.svg"))

            val resolved = resolver(store).resolve(tenant, productType, featureId, applicationId, ThemeVariant.DARK)

            val logo = assertNotNull(resolved).elements["logo"]
            assertNotNull(logo)
            assertEquals(AssetElementValue(ThemeAssetReference(uri = "https://cdn.example.com/app.svg")), logo.value)
            assertEquals(ElementOrigin.APPLICATION, logo.origin)
        }

    @Test
    fun tenantBindingWithMatchingVariantWinsWhenNoApplicationBinding() =
        runTest {
            val store = InMemoryThemeStore()
            store.setElementBinding(tenant, logoBinding("https://cdn.example.com/tenant-dark.svg", variant = ThemeVariant.DARK))
            store.setElementBinding(tenant, logoBinding("https://cdn.example.com/tenant.svg"))

            val resolved = resolver(store).resolve(tenant, productType, featureId, applicationId, ThemeVariant.DARK)

            val logo = assertNotNull(resolved).elements["logo"]
            assertNotNull(logo)
            assertEquals(AssetElementValue(ThemeAssetReference(uri = "https://cdn.example.com/tenant-dark.svg")), logo.value)
            assertEquals(ElementOrigin.TENANT, logo.origin)
        }

    @Test
    fun tenantBindingWithVariantNullIsTheLastBindingFallback() =
        runTest {
            val store = InMemoryThemeStore()
            store.setElementBinding(tenant, logoBinding("https://cdn.example.com/tenant.svg"))

            val resolved = resolver(store).resolve(tenant, productType, featureId, applicationId, ThemeVariant.DARK)

            val logo = assertNotNull(resolved).elements["logo"]
            assertNotNull(logo)
            assertEquals(AssetElementValue(ThemeAssetReference(uri = "https://cdn.example.com/tenant.svg")), logo.value)
            assertEquals(ElementOrigin.TENANT, logo.origin)
        }

    @Test
    fun builtInFeatureElementDefaultResolvesAsProductDefault() =
        runTest {
            val store = InMemoryThemeStore()

            val resolved = resolver(store).resolve(tenant, productType, featureId, applicationId)

            val background = assertNotNull(resolved).elements["background"]
            assertNotNull(background)
            assertEquals(AssetElementValue(ThemeAssetReference(uri = "/defaults/login-background.png")), background.value)
            assertEquals(ElementOrigin.PRODUCT_DEFAULT, background.origin)
        }

    @Test
    fun customFeatureElementDefaultResolvesAsElementDefault() =
        runTest {
            val store = InMemoryThemeStore()
            store.saveFeature(
                tenant,
                FeatureDefinition(
                    featureId = "consent",
                    productType = productType,
                    name = "Consent screen",
                    elements =
                        listOf(
                            TextDesignElement(
                                elementId = "consentCopy",
                                default = "Please review the requested access.",
                            ),
                        ),
                ),
            )

            val resolved = resolver(store).resolve(tenant, productType, "consent", applicationId)

            val consentCopy = assertNotNull(resolved).elements["consentCopy"]
            assertNotNull(consentCopy)
            assertEquals(TextElementValue("Please review the requested access."), consentCopy.value)
            assertEquals(ElementOrigin.ELEMENT_DEFAULT, consentCopy.origin)
        }

    @Test
    fun assetElementWrapsFallbackTokenValueAsThemeAssetReference() =
        runTest {
            val store = InMemoryThemeStore()
            store.saveDefinition(
                tenant,
                ThemeDefinition(
                    id = "tenant-brand",
                    name = "Tenant brand",
                    scope = ThemeScope.TENANT,
                    tokens = buildTokens { string(TokenKeyConstants.BRANDING_LOGO_URL, "https://cdn.example.com/acme.svg") },
                ),
            )

            val resolved = resolver(store).resolve(tenant, productType, featureId, applicationId)

            val logo = assertNotNull(resolved).elements["logo"]
            assertNotNull(logo)
            assertEquals(AssetElementValue(ThemeAssetReference(uri = "https://cdn.example.com/acme.svg")), logo.value)
            assertEquals(ElementOrigin.TOKEN_FALLBACK, logo.origin)
        }

    @Test
    fun textElementFallsBackToTokenValue() =
        runTest {
            val store = InMemoryThemeStore()
            store.saveDefinition(
                tenant,
                ThemeDefinition(
                    id = "tenant-brand",
                    name = "Tenant brand",
                    scope = ThemeScope.TENANT,
                    tokens = buildTokens { string(TokenKeyConstants.BRANDING_TAGLINE, "Credentials for everyone") },
                ),
            )

            val resolved = resolver(store).resolve(tenant, productType, featureId, applicationId)

            val tagline = assertNotNull(resolved).elements["tagline"]
            assertNotNull(tagline)
            assertEquals(TextElementValue("Credentials for everyone"), tagline.value)
            assertEquals(ElementOrigin.TOKEN_FALLBACK, tagline.origin)
        }

    @Test
    fun requiredElementWithoutAnyValueLandsInMissingRequired() =
        runTest {
            val store = InMemoryThemeStore()

            val resolved = resolver(store).resolve(tenant, productType, featureId, applicationId)

            val feature = assertNotNull(resolved)
            assertFalse(feature.elements.containsKey("legalNotice"))
            assertTrue(assertNotNull(feature.missingRequired).contains("legalNotice"))
        }

    @Test
    fun unknownFeatureResolvesToNull() =
        runTest {
            val store = InMemoryThemeStore()

            val resolved = resolver(store).resolve(tenant, productType, "unknown", applicationId)

            assertNull(resolved)
        }

    @Test
    fun resolvedFeatureCarriesResolutionContext() =
        runTest {
            val store = InMemoryThemeStore()

            val resolved = assertNotNull(resolver(store).resolve(tenant, productType, featureId, applicationId, ThemeVariant.DARK))

            assertEquals(productType, resolved.productType)
            assertEquals(featureId, resolved.featureId)
            assertEquals(tenant, resolved.tenantId)
            assertEquals(applicationId, resolved.applicationId)
            assertEquals(ThemeVariant.DARK, resolved.variant)
        }

    @Test
    fun choiceElementFallsBackFromBindingToDefault() =
        runTest {
            val store = InMemoryThemeStore()
            store.saveFeature(
                tenant,
                FeatureDefinition(
                    featureId = "checkout",
                    productType = productType,
                    name = "Checkout",
                    elements =
                        listOf(
                            ChoiceDesignElement(
                                elementId = "headerStyle",
                                allowedValues = listOf("panel", "hairline", "logoOnly"),
                                default = "panel",
                            ),
                        ),
                ),
            )

            val resolved = resolver(store).resolve(tenant, productType, "checkout", applicationId)

            val headerStyle = assertNotNull(resolved).elements["headerStyle"]
            assertNotNull(headerStyle)
            assertEquals(ChoiceElementValue("panel"), headerStyle.value)
            assertEquals(ElementOrigin.ELEMENT_DEFAULT, headerStyle.origin)
        }

    @Test
    fun choiceBindingWinsOverTheElementDefault() =
        runTest {
            val store = InMemoryThemeStore()
            store.saveFeature(
                tenant,
                FeatureDefinition(
                    featureId = "checkout",
                    productType = productType,
                    name = "Checkout",
                    elements =
                        listOf(
                            ChoiceDesignElement(
                                elementId = "headerStyle",
                                allowedValues = listOf("panel", "hairline", "logoOnly"),
                                default = "panel",
                            ),
                        ),
                ),
            )
            store.setElementBinding(
                tenant,
                ElementBinding(
                    productType = productType,
                    featureId = "checkout",
                    elementId = "headerStyle",
                    value = ChoiceElementValue("hairline"),
                ),
            )

            val resolved = resolver(store).resolve(tenant, productType, "checkout", applicationId)

            val headerStyle = assertNotNull(resolved).elements["headerStyle"]
            assertNotNull(headerStyle)
            assertEquals(ChoiceElementValue("hairline"), headerStyle.value)
            assertEquals(ElementOrigin.TENANT, headerStyle.origin)
        }
}
