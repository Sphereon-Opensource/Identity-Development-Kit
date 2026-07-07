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

import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.core.compat.JsExportCompat

/**
 * Resolves a fully flattened theme by merging definition layers
 * in precedence order: SYSTEM < PRODUCT < TENANT < APPLICATION < PRINCIPAL.
 */
@JsExportCompat
interface ThemeResolver {
    /**
     * Resolve the effective theme for a tenant.
     *
     * The PRODUCT and APPLICATION layers apply only when [applicationId] is supplied:
     * the application's product type selects the PRODUCT layer and the application id
     * selects the APPLICATION layer.
     *
     * @param tenant The tenant identifier
     * @param variant Optional variant to resolve (LIGHT, DARK, HIGH_CONTRAST)
     * @param applicationId Optional registered application to resolve for
     * @param principalId Optional principal for per-user overrides
     * @return The resolved theme
     */
    suspend fun resolve(
        tenant: String,
        variant: ThemeVariant? = null,
        applicationId: String? = null,
        principalId: String? = null,
    ): ResolvedTheme
}
