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

package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Resolves the externally visible base URL the authorization server should advertise.
 *
 * The default IDK implementation preserves config-driven behavior (prefers
 * `OAuth2ServerInstanceConfig.issuer`, falls back to effective scheme + host from the request,
 * appending an optional tenant path slug). EDK runtimes can bind a tenant-aware implementation
 * that resolves the active tenant's public endpoint binding from `tenant_public_endpoint`.
 */
interface OAuth2ServerBaseUrlResolver {
    suspend fun resolveBaseUrl(
        request: GenericHttpRequest,
        configProvider: OAuth2ServersConfigProvider,
        tenantPath: String? = null,
    ): String
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultOAuth2ServerBaseUrlResolver : OAuth2ServerBaseUrlResolver {
    override suspend fun resolveBaseUrl(
        request: GenericHttpRequest,
        configProvider: OAuth2ServersConfigProvider,
        tenantPath: String?,
    ): String = request.resolveBaseUrl(configProvider, tenantPath)
}
