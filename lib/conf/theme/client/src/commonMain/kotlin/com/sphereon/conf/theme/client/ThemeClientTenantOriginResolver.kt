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

package com.sphereon.conf.theme.client

import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Supplies the public origin (`https://host[:port]`) a tenant's public routes are served on.
 *
 * The theme service resolves the tenant of its public endpoints from the request host, never from
 * a credential: those endpoints brand pages before any sign-in. A caller that reaches the theme
 * service over an internal address therefore presents a host the service cannot map to a tenant.
 * When an implementation knows the tenant's public origin, the remote theme client addresses the
 * theme service through that origin instead of the configured base URL, and the request carries a
 * host the theme service resolves. Returning null keeps the configured base URL.
 */
interface ThemeClientTenantOriginResolver {
    suspend fun publicOrigin(tenantId: String): String?
}

/** Default: no tenant origin is known, so the configured base URL is used for every tenant. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ThemeClientTenantOriginResolver>())
class ConfiguredBaseUrlThemeClientTenantOriginResolver : ThemeClientTenantOriginResolver {
    override suspend fun publicOrigin(tenantId: String): String? = null
}
