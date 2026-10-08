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

package com.sphereon.oauth2.server.authorization.provider

import com.sphereon.core.compat.JsExportCompat

/**
 * Platform-free branding snapshot for the AS login / account-action surfaces.
 *
 * Populated by a [LoginPageThemeProvider] implementation (typically the platform
 * `lib-oauth2-server-authorization-theme` adapter that maps ResolvedTheme /
 * ResolvedFeature). Protocols public/impl never import theme-core types.
 */
@JsExportCompat
data class LoginPageBranding(
    val primary: String? = null,
    val surface: String? = null,
    val onSurface: String? = null,
    val logoUrl: String? = null,
    val logoDarkUrl: String? = null,
    val backgroundUrl: String? = null,
    val appName: String? = null,
    /** Complete server-resolved CSS token snapshot; null retains the scalar-color defaults. */
    val cssTokens: Map<String, String>? = null,
    /** Strong token-content identity when supplied by the resolver; otherwise consumers hash tokens. */
    val tokenEtag: String? = null,
    /** Server-resolved scope for rendering and bounded tenant/application CSS caches. */
    val tenantId: String? = null,
    val applicationId: String? = null,
    val faviconUrl: String? = null,
    val tagline: String? = null,
)

/**
 * Resolved theming inputs for one login render. All-null / empty = neutral page.
 */
@JsExportCompat
data class LoginPageThemeSnapshot(
    val lightBranding: LoginPageBranding? = null,
    val darkBranding: LoginPageBranding? = null,
    /** Absolute http(s) asset URIs that may need CSP `img-src` origins. */
    val imageUris: List<String> = emptyList(),
)

/**
 * Optional SPI that supplies tenant branding for AS HTML pages without leaking
 * platform theme types into protocols. Assemblies without a binding render the
 * neutral page.
 */
interface LoginPageThemeProvider {
    suspend fun resolve(
        tenantId: String?,
        asInstanceId: String,
    ): LoginPageThemeSnapshot
}
