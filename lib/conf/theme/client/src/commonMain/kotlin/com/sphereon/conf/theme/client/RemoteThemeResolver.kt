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

import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.resolve.ThemeResolver
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
 * [ThemeResolver] that fetches `GET {base}/api/theme/v1/{tenant}/resolved` from a remote theme
 * service (the endpoint is public by design so pre-auth surfaces can brand themselves).
 * Responses are cached per (tenant, variant, applicationId) with ETag revalidation through the
 * shared app-scoped [ThemeClientCache].
 *
 * Failure semantics: an unconfigured `theme.client.base-url`, an unknown tenant (404), or any
 * non-success response throws [ThemeClientException]. No fallback theme is ever substituted;
 * callers that treat theming as optional guard the call and render their neutral defaults.
 *
 * The [principalId] argument is not sent: the public resolution endpoint carries no principal
 * parameter, so principal overlays only apply where the theme backend runs in-process.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ThemeResolver>())
class RemoteThemeResolver(
    private val execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
    private val configProvider: ThemeClientConfigProvider,
    private val cache: ThemeClientCache,
) : ThemeResolver {
    private val http: HttpClient by lazy { httpClientFactory.createClient(HttpClientOptions()) }

    override suspend fun resolve(
        tenant: String,
        variant: ThemeVariant?,
        applicationId: String?,
        principalId: String?,
    ): ResolvedTheme {
        val baseUrl = requireThemeClientBaseUrl(configProvider, execution, TAG)
        val url = "$baseUrl$THEME_API_BASE_PATH/${tenant.encodeURLPathPart()}/resolved"
        val query =
            buildMap {
                variant?.let { put("variant", it.name) }
                applicationId?.let { put("applicationId", it) }
            }
        val cacheKey = "theme|$tenant|${variant?.name.orEmpty()}|${applicationId.orEmpty()}"
        val body =
            fetchThemeResource(http, cache, cacheKey, url, query)
                ?: throw ThemeClientException(
                    "Theme resolution returned 404 for tenant '$tenant': unknown tenant",
                    statusCode = 404,
                )
        return themeClientJson.decodeFromString(ResolvedTheme.serializer(), body)
    }

    companion object {
        private const val TAG: String = "RemoteThemeResolver"
    }
}
