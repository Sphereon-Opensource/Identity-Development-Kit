/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.resolver

import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet
import com.sphereon.di.session.SessionScope
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.plugins.timeout
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Trust list resolver that fetches trust lists via HTTP/HTTPS.
 * Uses CacheService instead of ad-hoc Kache caching.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<TrustListResolver>())
class HttpTrustListResolver(
    private val httpClientFactory: HttpClientFactory,
    private val cacheService: CacheService,
    private val execution: SessionExecution
) : TrustListResolver {

    private val httpClient: HttpClient by lazy { httpClientFactory.createClient(HttpClientOptions()) }
    private val logger = execution.log.logManager.withTagAsync("HttpTrustListResolver")

    private val cache by lazy {
        cacheService.getStringCache<ByteArray>(
            com.sphereon.core.api.cache.CacheRequirements(
                namespace = "trust.resolver.http",
                ttlConfig = com.sphereon.core.api.cache.CacheTtlConfig(app = 60.minutes)
            )
        )
    }

    override fun getId(): String = "http"

    override suspend fun resolve(uri: String, options: ResolutionOptions): TrustListData {
        if (!supports(uri)) {
            throw TrustListResolutionException("URI scheme not supported: $uri")
        }

        // Check cache if enabled
        if (options.useCache) {
            val cached = cache.getApp(uri)
            if (cached.isOk) {
                cached.value?.let { data ->
                    logger.debug("Using cached trust list for $uri")
                    return TrustListData(
                        data = data,
                        sourceUri = uri,
                        fromCache = true,
                        retrievedAt = Clock.System.now().toEpochMilliseconds()
                    )
                }
            }
        }

        logger.info("Fetching trust list from $uri")

        try {
            val response = httpClient.get(uri) {
                timeout {
                    requestTimeoutMillis = options.timeoutMs
                }
            }

            if (!response.status.isSuccess()) {
                throw TrustListResolutionException(
                    "Failed to fetch trust list: HTTP ${response.status.value} ${response.status.description}"
                )
            }

            val data = response.bodyAsBytes()
            val contentType = response.headers[HttpHeaders.ContentType]
            val etag = response.headers[HttpHeaders.ETag]

            val trustListData = TrustListData(
                data = data,
                sourceUri = uri,
                contentType = contentType,
                fromCache = false,
                retrievedAt = Clock.System.now().toEpochMilliseconds(),
                cacheToken = etag
            )

            // Update cache
            if (options.useCache) {
                cache.putApp(uri, data)
            }

            logger.info("Successfully fetched trust list from $uri (${data.size} bytes)")
            return trustListData

        } catch (e: TrustListResolutionException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to resolve trust list from $uri", exception = e)
            throw TrustListResolutionException("Failed to resolve trust list from $uri", e)
        }
    }

    override fun supports(uri: String): Boolean {
        return uri.startsWith("http://", ignoreCase = true) ||
                uri.startsWith("https://", ignoreCase = true)
    }
}
