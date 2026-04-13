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

package com.sphereon.ktor.server.inject

import com.sphereon.di.app.AppComponent
import com.sphereon.ktor.server.inject.resolver.DefaultPrincipalResolver
import com.sphereon.ktor.server.inject.resolver.DefaultTenantResolver
import com.sphereon.ktor.server.inject.resolver.PrincipalResolver
import com.sphereon.ktor.server.inject.resolver.TenantResolver

/**
 * Configuration for the KotlinInject Ktor plugin.
 *
 * **Required:**
 * - [appComponent]: The root kotlin-inject AppComponent instance
 *
 * **Optional:**
 * - [tenantResolver]: Strategy for extracting tenant information from requests (default: header-based)
 * - [principalResolver]: Strategy for extracting principal information from requests (default: header-based)
 * - [tenantHeader]: HTTP header name for tenant ID (default: "X-Tenant-ID")
 * - [principalHeader]: HTTP header name for principal/user ID (default: "X-User-ID")
 *
 * **Example:**
 * ```kotlin
 * install(KotlinInject) {
 *     appComponent = MyAppComponent.create(...)
 *
 *     // Use custom resolvers
 *     tenantResolver = JwtTenantResolver()
 *     principalResolver = JwtPrincipalResolver()
 *
 *     // Or configure default header-based resolvers
 *     tenantHeader = "X-Tenant-ID"
 *     principalHeader = "X-User-ID"
 * }
 * ```
 */
class KotlinInjectConfiguration {

    /**
     * The root kotlin-inject AppComponent instance.
     * This is the entry point to the kotlin-inject dependency injection tree.
     *
     * **Required** - must be set in the configuration.
     */
    var appComponent: AppComponent? = null

    /**
     * HTTP header name for extracting tenant ID.
     * Used by the default tenant resolver.
     * Default: "X-Tenant-ID"
     */
    var tenantHeader: String = "X-Tenant-ID"

    /**
     * HTTP header name for extracting principal/user ID.
     * Used by the default principal resolver.
     * Default: "X-User-ID"
     */
    var principalHeader: String = "X-User-ID"

    /**
     * Custom tenant resolver implementation.
     * If not set, a default header-based resolver will be used.
     */
    private var _tenantResolver: TenantResolver? = null

    /**
     * Custom principal resolver implementation.
     * If not set, a default header-based resolver will be used.
     */
    private var _principalResolver: PrincipalResolver? = null

    /**
     * The tenant resolver to use for extracting tenant information from requests.
     * Defaults to header-based resolver using [tenantHeader].
     */
    var tenantResolver: TenantResolver
        get() = _tenantResolver ?: DefaultTenantResolver(tenantHeader)
        set(value) {
            _tenantResolver = value
        }

    /**
     * The principal resolver to use for extracting principal information from requests.
     * Defaults to header-based resolver using [principalHeader].
     */
    var principalResolver: PrincipalResolver
        get() = _principalResolver ?: DefaultPrincipalResolver(principalHeader)
        set(value) {
            _principalResolver = value
        }
}
