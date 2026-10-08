/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.trust.core.resolver

import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.cache.HttpCacheExpiryPolicy
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.encodeToBase64
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.TrustDiagnosticReasonCodes
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Trust-list resolver with bounded, resolver-owned HTTP transport. */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<TrustListResolver>())
internal class HttpTrustListResolver(
    private val transport: TrustListHttpTransport,
    cacheService: CacheService,
) : TrustListResolver {
    private val cache by lazy {
        cacheService.getCache(
            CacheRequirements(
                namespace = "trust.resolver.http",
                ttlConfig = com.sphereon.core.api.cache.CacheTtlConfig(app = 60.minutes),
            ),
        )
    }

    private val cacheJson = Json { ignoreUnknownKeys = true }

    override fun getId(): String = "http"

    override suspend fun resolve(
        uri: String,
        options: ResolutionOptions,
    ): TrustListData {
        if (!supports(uri)) {
            throw TrustListResolutionException(
                "Trust-list URL scheme is not supported",
                reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_URL_REJECTED,
            )
        }
        if (options.timeoutMs <= 0L) {
            throw TrustListResolutionException(
                "Trust-list request timeout is invalid",
                reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_RESOLUTION_TIMEOUT,
            )
        }
        if (options.maxBodyBytes <= 0L) {
            throw TrustListResolutionException(
                "Trust-list response body limit is invalid",
                reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_BODY_TOO_LARGE,
            )
        }
        if (options.maxRedirects < 0) {
            throw TrustListResolutionException(
                "Trust-list redirect limit is invalid",
                reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_REDIRECT_LIMIT,
            )
        }

        val canonicalUri = TrustListUrlPolicy.validate(uri, options.requireHttps)
        var cacheDiagnosticReasonCode: String? = null
        if (options.useCache) {
            val cacheRead = readCached(canonicalUri, options)
            cacheRead.data?.let { return it }
            cacheDiagnosticReasonCode = cacheRead.diagnosticReasonCode
        }

        var currentUri = canonicalUri
        var redirects = 0
        while (true) {
            val response =
                try {
                    transport.execute(currentUri, options.timeoutMs)
                } catch (expected: TrustListResolutionException) {
                    throw expected
                } catch (expected: Exception) {
                    throw transportFailure(expected)
                }

            var primaryFailure: Throwable? = null
            try {
                if (response.statusCode in 300..399) {
                    if (redirects >= options.maxRedirects) {
                        throw TrustListResolutionException(
                            "Trust-list redirect limit exceeded",
                            reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_REDIRECT_LIMIT,
                        )
                    }
                    currentUri =
                        TrustListUrlPolicy.resolveRedirect(
                            currentUri = currentUri,
                            location = response.header("Location"),
                            requireHttps = options.requireHttps,
                        )
                    redirects++
                    continue
                }

                if (response.statusCode !in 200..299) {
                    throw TrustListResolutionException(
                        "Trust-list HTTP response was rejected",
                        reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_HTTP_STATUS,
                    )
                }

                response.header("Content-Length")?.let { rawLength ->
                    val declaredLength = rawLength.toLongOrNull()
                    if (declaredLength == null || declaredLength < 0L) {
                        throw TrustListResolutionException(
                            "Trust-list HTTP response length was malformed",
                            reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_HTTP_STATUS,
                        )
                    }
                    if (declaredLength > options.maxBodyBytes) {
                        throw TrustListResolutionException(
                            "Trust-list response body limit exceeded",
                            reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_BODY_TOO_LARGE,
                        )
                    }
                }

                val data =
                    try {
                        response.body.readAll(options.maxBodyBytes)
                    } catch (expected: TrustListResolutionException) {
                        throw expected
                    } catch (expected: Exception) {
                        throw TrustListResolutionException(
                            "Trust-list response body could not be read",
                            expected,
                            TrustDiagnosticReasonCodes.TRUST_LIST_RESOLUTION_FAILED,
                        )
                    }
                val retrievedAt = Clock.System.now().toEpochMilliseconds()
                val cacheControl = response.header("Cache-Control")
                val expiresAt = response.expiresAtEpochMillis
                val effectiveTtl =
                    HttpCacheExpiryPolicy.ttlMillis(
                        cacheControl = cacheControl,
                        localTtlMillis = options.maxCacheAgeMs,
                        expiresAt = expiresAt?.let(Instant::fromEpochMilliseconds),
                        signedNextUpdate = options.signedNextUpdateEpochMillis?.let(Instant::fromEpochMilliseconds),
                        now = Instant.fromEpochMilliseconds(retrievedAt),
                    )
                val cacheMetadata =
                    TrustListCacheMetadata(
                        cacheControl = cacheControl,
                        expiresAtEpochMillis = expiresAt,
                        signedNextUpdateEpochMillis = options.signedNextUpdateEpochMillis,
                        effectiveTtlMs = effectiveTtl,
                        noStore = HttpCacheExpiryPolicy.isNoStore(cacheControl),
                        revalidationRequired = HttpCacheExpiryPolicy.requiresRevalidation(cacheControl),
                        diagnosticReasonCode =
                            cacheDiagnosticReasonCode
                                ?: if (HttpCacheExpiryPolicy.requiresRevalidation(cacheControl)) {
                                    TrustDiagnosticReasonCodes.TRUST_LIST_CACHE_REVALIDATION_REQUIRED
                                } else {
                                    null
                                },
                    )
                val result =
                    TrustListData(
                        data = data,
                        sourceUri = currentUri,
                        contentType = response.header("Content-Type"),
                        retrievedAt = retrievedAt,
                        cacheToken = response.header("ETag"),
                        cacheMetadata = cacheMetadata,
                    )

                if (options.useCache &&
                    effectiveTtl > 0L &&
                    !cacheMetadata.noStore &&
                    !cacheMetadata.revalidationRequired
                ) {
                    cache.putApp(
                        canonicalUri,
                        cacheJson.encodeToString(CachedTrustListEnvelope.serializer(), result.toCacheEnvelope()),
                        // Keep the cache entry available long enough for the
                        // envelope's authoritative HTTP/signed expiry check
                        // to report CACHE_EXPIRED instead of losing that
                        // distinction inside the backend TTL layer.
                        null,
                    )
                }
                return result
            } catch (expected: Throwable) {
                primaryFailure = expected
                throw expected
            } finally {
                try {
                    response.close()
                } catch (cleanupFailure: Throwable) {
                    if (primaryFailure == null) {
                        throw TrustListResolutionException(
                            "Trust-list response cleanup failed",
                            reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_RESOLUTION_FAILED,
                        )
                    }
                }
            }
        }
    }

    override fun supports(uri: String): Boolean =
        uri.startsWith("http://", ignoreCase = true) ||
            uri.startsWith("https://", ignoreCase = true)

    private suspend fun readCached(
        uri: String,
        options: ResolutionOptions,
    ): CacheReadResult {
        val cached = cache.getApp(uri) ?: return CacheReadResult()
        val envelope =
            runCatching {
                cacheJson.decodeFromString(CachedTrustListEnvelope.serializer(), cached)
            }.getOrNull()
            ?: run {
                cache.removeApp(uri)
                return CacheReadResult()
            }
        val metadata =
            envelope.cacheMetadata
                ?: run {
                    cache.removeApp(uri)
                    return CacheReadResult()
                }
        val data =
            runCatching { envelope.dataBase64.decodeFromBase64() }.getOrNull()
                ?: run {
                    cache.removeApp(uri)
                    return CacheReadResult()
                }
        if (data.size.toLong() > options.maxBodyBytes) {
            cache.removeApp(uri)
            return CacheReadResult()
        }

        when (cacheFreshness(envelope.retrievedAt, metadata, options)) {
            CacheFreshness.REUSABLE ->
                return CacheReadResult(data = envelope.toTrustListData(data))
            CacheFreshness.EXPIRED -> {
                cache.removeApp(uri)
                return CacheReadResult(
                    diagnosticReasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_CACHE_EXPIRED,
                )
            }
            CacheFreshness.UNTRUSTED -> {
                cache.removeApp(uri)
                return CacheReadResult()
            }
        }
    }

    private fun cacheFreshness(
        retrievedAt: Long,
        metadata: TrustListCacheMetadata,
        options: ResolutionOptions,
    ): CacheFreshness {
        val effectiveTtlMs = metadata.effectiveTtlMs ?: return CacheFreshness.UNTRUSTED
        if (effectiveTtlMs <= 0L || metadata.noStore || metadata.revalidationRequired) {
            return CacheFreshness.UNTRUSTED
        }

        val now = Clock.System.now().toEpochMilliseconds()
        if (retrievedAt < 0L || retrievedAt > now) return CacheFreshness.UNTRUSTED
        val age = now - retrievedAt
        if (age >= effectiveTtlMs || options.maxCacheAgeMs <= 0L || age >= options.maxCacheAgeMs) {
            return CacheFreshness.EXPIRED
        }

        metadata.expiresAtEpochMillis?.let { expiresAt ->
            if (expiresAt < retrievedAt) return CacheFreshness.UNTRUSTED
            if (now >= expiresAt) return CacheFreshness.EXPIRED
        }
        metadata.signedNextUpdateEpochMillis?.let { signedNextUpdate ->
            if (signedNextUpdate < retrievedAt) return CacheFreshness.UNTRUSTED
            if (now >= signedNextUpdate) return CacheFreshness.EXPIRED
        }
        options.signedNextUpdateEpochMillis?.let { signedNextUpdate ->
            if (signedNextUpdate < retrievedAt) return CacheFreshness.UNTRUSTED
            if (now >= signedNextUpdate) return CacheFreshness.EXPIRED
        }

        return CacheFreshness.REUSABLE
    }

    private fun transportFailure(expected: Exception): TrustListResolutionException {
        val timeout = expected.causesContain { it::class.simpleName?.contains("Timeout", ignoreCase = true) == true }
        return TrustListResolutionException(
            if (timeout) "Trust-list request timed out" else "Trust-list transport failed",
            expected,
            if (timeout) {
                TrustDiagnosticReasonCodes.TRUST_LIST_RESOLUTION_TIMEOUT
            } else {
                TrustDiagnosticReasonCodes.TRUST_LIST_RESOLUTION_FAILED
            },
        )
    }
}

private fun Throwable.causesContain(predicate: (Throwable) -> Boolean): Boolean {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        if (predicate(current)) return true
        current = current.cause
    }
    return false
}

private data class CacheReadResult(
    val data: TrustListData? = null,
    val diagnosticReasonCode: String? = null,
)

private enum class CacheFreshness {
    REUSABLE,
    EXPIRED,
    UNTRUSTED,
}

@Serializable
private data class CachedTrustListEnvelope(
    val dataBase64: String,
    val sourceUri: String,
    val contentType: String?,
    val fromCache: Boolean,
    val retrievedAt: Long,
    val cacheToken: String?,
    val cacheMetadata: TrustListCacheMetadata?,
) {
    fun toTrustListData(data: ByteArray): TrustListData =
        TrustListData(
            data = data,
            sourceUri = sourceUri,
            contentType = contentType,
            fromCache = true,
            retrievedAt = retrievedAt,
            cacheToken = cacheToken,
            cacheMetadata = cacheMetadata,
        )
}

private fun TrustListData.toCacheEnvelope(): CachedTrustListEnvelope =
    CachedTrustListEnvelope(
        dataBase64 = data.encodeToBase64(),
        sourceUri = sourceUri,
        contentType = contentType,
        fromCache = fromCache,
        retrievedAt = retrievedAt,
        cacheToken = cacheToken,
        cacheMetadata = cacheMetadata,
    )
