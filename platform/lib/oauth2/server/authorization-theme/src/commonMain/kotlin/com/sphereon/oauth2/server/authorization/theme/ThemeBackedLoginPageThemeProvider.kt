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

package com.sphereon.oauth2.server.authorization.theme

import com.sphereon.conf.theme.core.model.AssetElementValue
import com.sphereon.conf.theme.core.model.ProductType
import com.sphereon.conf.theme.core.model.ResolvedFeature
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.TextElementValue
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.resolve.FeatureResolver
import com.sphereon.conf.theme.core.resolve.ThemeResolver
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.provider.LoginPageBranding
import com.sphereon.oauth2.server.authorization.provider.LoginPageThemeProvider
import com.sphereon.oauth2.server.authorization.provider.LoginPageThemeSnapshot
import com.sphereon.software.registry.SoftwareInstanceRegistry
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException

/**
 * Platform adapter that maps theme-core [ThemeResolver] / [FeatureResolver] output onto the
 * protocols-public [LoginPageThemeProvider] SPI. Keeps ResolvedTheme / ResolvedFeature out of
 * the protocols pack compile classpath.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class ThemeBackedLoginPageThemeProvider(
    private val themeResolver: Provider<ThemeResolver>? = null,
    private val featureResolver: Provider<FeatureResolver>? = null,
    private val softwareInstanceRegistry: Provider<SoftwareInstanceRegistry>? = null,
) : LoginPageThemeProvider {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun resolve(
        tenantId: String?,
        asInstanceId: String,
    ): LoginPageThemeSnapshot {
        if (themeResolver == null && featureResolver == null) return LoginPageThemeSnapshot()
        if (tenantId.isNullOrBlank()) return LoginPageThemeSnapshot()
        return try {
            val applicationId = softwareInstanceRegistry?.invoke()?.get(tenantId, asInstanceId)?.partyId
            val resolver = themeResolver?.invoke()
            val light = resolver?.resolve(tenant = tenantId, variant = ThemeVariant.LIGHT, applicationId = applicationId)
            val dark = resolver?.resolve(tenant = tenantId, variant = ThemeVariant.DARK, applicationId = applicationId)
            val loginFeature =
                featureResolver?.invoke()?.resolve(
                    tenant = tenantId,
                    productType = ProductType.AUTHORIZATION_SERVER,
                    featureId = LOGIN_FEATURE_ID,
                    applicationId = applicationId,
                    variant = null,
                )
            val loginFeatureDark =
                featureResolver?.invoke()?.resolve(
                    tenant = tenantId,
                    productType = ProductType.AUTHORIZATION_SERVER,
                    featureId = LOGIN_FEATURE_ID,
                    applicationId = applicationId,
                    variant = ThemeVariant.DARK,
                )
            if (light == null && dark == null && loginFeature == null && loginFeatureDark == null) {
                return LoginPageThemeSnapshot()
            }
            LoginPageThemeSnapshot(
                lightBranding = branding(light, loginFeature, dark = false),
                darkBranding = branding(dark, loginFeatureDark, dark = true, baseTheme = light, baseFeature = loginFeature),
                imageUris = imageUris(light, dark, loginFeature, loginFeatureDark),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            LoginPageThemeSnapshot()
        }
    }

    companion object {
        private const val LOGIN_FEATURE_ID: String = "login"
        private val THEMED_IMAGE_ELEMENT_IDS: List<String> = listOf("logo", "logoDark", "background", "favicon")

        internal fun branding(
            theme: ResolvedTheme?,
            feature: ResolvedFeature?,
            dark: Boolean,
            baseTheme: ResolvedTheme? = null,
            baseFeature: ResolvedFeature? = null,
        ): LoginPageBranding {
            val lightFeature = if (dark) baseFeature else feature
            val darkLogo =
                assetUri(feature, "logo")?.takeIf { it != assetUri(lightFeature, "logo") }
            return LoginPageBranding(
                primary = safeCssColor(theme?.tokens?.get("color.primary") ?: theme?.branding?.primaryColor),
                surface = safeCssColor(theme?.tokens?.get("color.surface")),
                onSurface = safeCssColor(theme?.tokens?.get("color.onSurface")),
                logoUrl =
                    if (dark) {
                        darkLogo
                            ?: assetUri(lightFeature, "logoDark")
                            ?: theme?.branding?.logoDarkUrl
                            ?: baseTheme?.branding?.logoDarkUrl
                    } else {
                        assetUri(lightFeature, "logo") ?: theme?.branding?.logoUrl
                    },
                logoDarkUrl =
                    assetUri(lightFeature, "logoDark")
                        ?: theme?.branding?.logoDarkUrl
                        ?: baseTheme?.branding?.logoDarkUrl,
                backgroundUrl = assetUri(feature, "background") ?: assetUri(lightFeature, "background"),
                appName = theme?.branding?.appName,
                cssTokens = theme?.tokens,
                tokenEtag = theme?.etag,
                tenantId = theme?.tenantId ?: lightFeature?.tenantId ?: feature?.tenantId ?: baseTheme?.tenantId,
                applicationId = theme?.applicationId ?: lightFeature?.applicationId ?: feature?.applicationId ?: baseTheme?.applicationId,
                faviconUrl =
                    assetUri(lightFeature, "favicon")
                        ?: (if (dark) baseTheme?.branding?.faviconUrl else null)
                        ?: theme?.branding?.faviconUrl,
                tagline = textValue(lightFeature, "tagline"),
            )
        }

        private fun imageUris(
            light: ResolvedTheme?,
            dark: ResolvedTheme?,
            loginFeature: ResolvedFeature?,
            loginFeatureDark: ResolvedFeature?,
        ): List<String> =
            buildList {
                for (feature in listOfNotNull(loginFeature, loginFeatureDark)) {
                    for (elementId in THEMED_IMAGE_ELEMENT_IDS) {
                        assetUri(feature, elementId)?.let(::add)
                    }
                }
                for (theme in listOfNotNull(light, dark)) {
                    theme.branding?.let { branding ->
                        branding.logoUrl?.let(::add)
                        branding.logoDarkUrl?.let(::add)
                        branding.faviconUrl?.let(::add)
                    }
                }
            }

        private fun assetUri(feature: ResolvedFeature?, elementId: String): String? =
            (feature?.elements?.get(elementId)?.value as? AssetElementValue)?.asset?.uri?.trim()?.takeIf { it.isNotEmpty() }

        private fun textValue(feature: ResolvedFeature?, elementId: String): String? =
            (feature?.elements?.get(elementId)?.value as? TextElementValue)?.text?.trim()?.takeIf { it.isNotEmpty() }

        private fun safeCssColor(value: String?): String? =
            value?.trim()?.takeIf { CSS_COLOR.matches(it) }

        private val CSS_COLOR = Regex("""#[0-9A-Fa-f]{3,8}""")
    }
}
