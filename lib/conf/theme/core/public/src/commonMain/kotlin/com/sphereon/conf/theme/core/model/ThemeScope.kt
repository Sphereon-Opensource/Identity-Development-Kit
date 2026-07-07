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

package com.sphereon.conf.theme.core.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * The hierarchical scope at which a theme definition applies.
 * Later scopes override earlier ones during resolution:
 * SYSTEM < PRODUCT < TENANT < APPLICATION < PRINCIPAL
 *
 * PRODUCT definitions require a [ThemeDefinition.productType]; APPLICATION definitions
 * require a [ThemeDefinition.applicationId]; the other scopes carry neither.
 */
@JsExportCompat
@Serializable
enum class ThemeScope {
    /** Built-in system defaults (lowest priority) */
    SYSTEM,

    /** Defaults shipped with a product (authorization server, web wallet, portal, ...), keyed by product type, tenant agnostic */
    PRODUCT,

    /** The tenant default, applying to every application the tenant runs */
    TENANT,

    /** The tenant's override for one deployed application instance, keyed by application id */
    APPLICATION,

    /** Per-principal (user) overrides (highest priority) */
    PRINCIPAL,
}
