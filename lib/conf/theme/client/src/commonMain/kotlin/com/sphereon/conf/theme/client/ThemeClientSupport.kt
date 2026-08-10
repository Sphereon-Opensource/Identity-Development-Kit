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

import com.sphereon.core.api.context.SessionExecution
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Failure of a remote theme resolution: unconfigured base URL, unknown tenant (404 on the
 * resolved endpoint), or any non-success response. Callers that guard theming (such as the
 * login page renderer) catch this and fall back to their neutral defaults; a substitute theme
 * is never fabricated.
 */
class ThemeClientException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal const val THEME_API_BASE_PATH: String = "/api/theme/v1"

/**
 * How long a cached representation is served without contacting the theme service. After this
 * window the next resolution revalidates with `If-None-Match`; a 304 refreshes the window
 * without re-transferring the body.
 */
internal val THEME_CLIENT_REVALIDATE_AFTER: Duration = 30.seconds

/**
 * Decoder for theme service representations. `classDiscriminator` mirrors the theme API
 * contract, which pins `kind` as the discriminator of the sealed `ElementValue` and
 * `DesignElement` hierarchies carried inside a `ResolvedFeature`.
 */
internal val themeClientJson: Json =
    Json {
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }

/**
 * Process-wide guard so the missing base URL is logged once at warn instead of on every
 * request. A benign race can log twice; that is acceptable.
 */
internal object ThemeClientBaseUrlWarning {
    @Volatile
    var logged: Boolean = false
}

/**
 * Returns the configured base URL or fails fast with a [ThemeClientException] when the
 * `theme.client.base-url` property is absent, logging the misconfiguration once at warn.
 */
internal fun requireThemeClientBaseUrl(
    configProvider: ThemeClientConfigProvider,
    execution: SessionExecution,
    tag: String,
): String {
    val baseUrl = configProvider.getConfig().baseUrl
    if (baseUrl == null) {
        if (!ThemeClientBaseUrlWarning.logged) {
            ThemeClientBaseUrlWarning.logged = true
            execution.log.logManager
                .withTag(tag)
                .warn(
                    "'${ThemeClientConfigBinder.BASE_URL_KEY}' is not configured; " +
                        "remote theme resolution is disabled and callers fall back to their neutral defaults",
                )
        }
        throw ThemeClientException(
            "'${ThemeClientConfigBinder.BASE_URL_KEY}' is not configured; remote theme resolution is unavailable",
        )
    }
    return baseUrl
}

/**
 * Fetches one theme service resource with ETag revalidation through [cache].
 *
 * - A cached entry younger than [THEME_CLIENT_REVALIDATE_AFTER] is served without any HTTP call.
 * - A stale entry with an ETag revalidates via `If-None-Match`; 304 serves the cached body and
 *   refreshes its freshness window.
 * - 200 stores body plus ETag and returns the body.
 * - 404 returns null (the caller decides whether that means unknown tenant or unknown feature).
 * - Any other status throws [ThemeClientException]; transport failures propagate as thrown.
 */
internal suspend fun fetchThemeResource(
    http: HttpClient,
    cache: ThemeClientCache,
    cacheKey: String,
    url: String,
    query: Map<String, String>,
): String? {
    val now = Clock.System.now().toEpochMilliseconds()
    val cached = cache.get(cacheKey)
    if (cached != null && now - cached.fetchedAtMs < THEME_CLIENT_REVALIDATE_AFTER.inWholeMilliseconds) {
        return cached.body
    }
    val response =
        http.get(url) {
            query.forEach { (name, value) -> parameter(name, value) }
            cached?.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
        }
    return when {
        response.status == HttpStatusCode.NotModified -> {
            val entry =
                cached ?: throw ThemeClientException(
                    "Theme service answered 304 for $url without a cached representation to serve",
                    statusCode = HttpStatusCode.NotModified.value,
                )
            cache.put(cacheKey, entry.copy(fetchedAtMs = now))
            entry.body
        }

        response.status == HttpStatusCode.NotFound -> null

        response.status.isSuccess() -> {
            val body = response.bodyAsText()
            cache.put(
                cacheKey,
                ThemeClientCacheEntry(
                    body = body,
                    etag = response.headers[HttpHeaders.ETag],
                    fetchedAtMs = now,
                ),
            )
            body
        }

        else ->
            throw ThemeClientException(
                "Theme resolution request failed with HTTP ${response.status.value} for $url",
                statusCode = response.status.value,
            )
    }
}
