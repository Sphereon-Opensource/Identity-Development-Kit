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

import com.sphereon.conf.theme.core.model.ProductType
import com.sphereon.conf.theme.core.model.ResolvedFeature
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.resolve.FeatureResolver
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLPathPart

/**
 * [FeatureResolver] that fetches the public feature resolution endpoints of a remote theme
 * service: `/{tenant}/applications/{applicationId}/features/{featureId}/resolved` when an
 * application is supplied, `/{tenant}/products/{productType}/features/{featureId}/resolved`
 * otherwise. Responses are cached with ETag revalidation through the shared app-scoped
 * [ThemeClientCache].
 *
 * A 404 returns null per the [FeatureResolver] contract (unknown feature). An unconfigured
 * `theme.client.base-url` or any other non-success response throws [ThemeClientException].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FeatureResolver>())
class RemoteFeatureResolver(
    private val execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
    private val configProvider: ThemeClientConfigProvider,
    private val cache: ThemeClientCache,
) : FeatureResolver {
    private val http: HttpClient by lazy { httpClientFactory.createClient(HttpClientOptions()) }

    override suspend fun resolve(
        tenant: String,
        productType: ProductType,
        featureId: String,
        applicationId: String?,
        variant: ThemeVariant?,
    ): ResolvedFeature? {
        val baseUrl = requireThemeClientBaseUrl(configProvider, execution, TAG)
        val encodedTenant = tenant.encodeURLPathPart()
        val encodedFeatureId = featureId.encodeURLPathPart()
        val url =
            if (applicationId != null) {
                "$baseUrl$THEME_API_BASE_PATH/$encodedTenant/applications/" +
                    "${applicationId.encodeURLPathPart()}/features/$encodedFeatureId/resolved"
            } else {
                "$baseUrl$THEME_API_BASE_PATH/$encodedTenant/products/" +
                    "${productType.name}/features/$encodedFeatureId/resolved"
            }
        val query =
            buildMap {
                variant?.let { put("variant", it.name) }
            }
        val cacheKey =
            "feature|$tenant|${productType.name}|$featureId|${applicationId.orEmpty()}|${variant?.name.orEmpty()}"
        val body = fetchThemeResource(http, cache, cacheKey, url, query) ?: return null
        return themeClientJson.decodeFromString(ResolvedFeature.serializer(), body)
    }

    companion object {
        private const val TAG: String = "RemoteFeatureResolver"
    }
}
