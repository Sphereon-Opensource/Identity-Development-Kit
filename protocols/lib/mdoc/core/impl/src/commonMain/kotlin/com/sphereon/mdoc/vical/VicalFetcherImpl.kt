/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.mdoc.vical

import com.sphereon.compression.CompressionAlgorithm
import com.sphereon.compression.decompress
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.cache.CacheTtlConfig
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.ktor.http.client.provider.UrlValidationPolicy
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Tenant-scoped VICAL retrieval with validation-before-cache-use semantics. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<VicalFetcher>())
@ContributesIntoSet(SessionScope::class, binding = binding<VicalFetcher>())
class VicalFetcherImpl(
    private val httpClientFactory: HttpClientFactory,
    private val cacheService: CacheService,
    private val execution: SessionExecution,
    private val validator: VicalValidator,
) : VicalFetcher {
    private val cache by lazy {
        cacheService.getCache(
            CacheRequirements(
                namespace = "mdoc.vical.signed",
                ttlConfig = CacheTtlConfig(tenant = 60.minutes),
            ),
        )
    }

    private val cacheJson = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(
        url: String,
        policy: VicalFetchPolicy,
    ): IdkResult<VicalFetchResult, IdkError> {
        return try {
            val canonicalUrl = validateUrl(url, policy.requireHttps)
            if (policy.useCache) {
                readCached(canonicalUrl, policy)?.let { return it }
            }
            fetchFromNetwork(canonicalUrl, policy)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (expected: Exception) {
            failure("MDOC_VICAL_FETCH_FAILED", "VICAL retrieval failed: ${expected.message}", expected)
        }
    }

    private suspend fun readCached(
        canonicalUrl: String,
        policy: VicalFetchPolicy,
    ): IdkResult<VicalFetchResult, IdkError>? {
        val cached =
            try {
                cache.getTenant(execution.tenantId, canonicalUrl)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: return null
        val signedVical =
            try {
                cacheJson.decodeFromString(CachedVicalEnvelope.serializer(), cached).signedVicalBase64.decodeFromBase64()
            } catch (_: Exception) {
                removeCached(canonicalUrl)
                return null
            }
        if (signedVical.size.toLong() > policy.maxBodyBytes) {
            removeCached(canonicalUrl)
            return null
        }

        val validation = validator.validate(signedVical, policy.validation)
        if (validation.isErr) {
            // A cached artifact is never allowed to mask a refreshable failure.
            removeCached(canonicalUrl)
            return null
        }
        return Ok(
            VicalFetchResult(
                validation = validation.value,
                sourceUrl = canonicalUrl,
                fetchedAtEpochSeconds = cachedFetchedAt(cached),
                fromCache = true,
            ),
        )
    }

    private suspend fun fetchFromNetwork(
        canonicalUrl: String,
        policy: VicalFetchPolicy,
    ): IdkResult<VicalFetchResult, IdkError> {
        val client =
            try {
                httpClientFactory.createClient(
                    HttpClientOptions.createDefault().copy(
                        followRedirects = false,
                        urlValidation = validationPolicy(policy.requireHttps),
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expected: Exception) {
                return failure("MDOC_VICAL_HTTP_CLIENT_FAILED", "VICAL HTTP client creation failed: ${expected.message}", expected)
            }

        return try {
            var currentUrl = canonicalUrl
            var redirects = 0
            var signedVical: ByteArray? = null
            while (signedVical == null) {
                    val response =
                        client.get(currentUrl) {
                            header(HttpHeaders.Accept, "application/cbor, application/cwt")
                            header(HttpHeaders.AcceptEncoding, "gzip")
                        }
                    if (response.status.value in 300..399) {
                        if (redirects >= policy.maxRedirects) {
                            throw IllegalArgumentException("VICAL redirect limit exceeded")
                        }
                        currentUrl = resolveRedirect(currentUrl, response.headers[HttpHeaders.Location], policy.requireHttps)
                        redirects++
                        discard(response)
                        continue
                    }
                    if (!response.status.isSuccess()) {
                        discard(response)
                        throw IllegalArgumentException("VICAL HTTP response was rejected: ${response.status.value}")
                    }
                    val contentType = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()?.lowercase()
                    if (policy.requireApplicationCwt && contentType !in setOf("application/cbor", "application/cwt")) {
                        discard(response)
                        throw IllegalArgumentException("VICAL response must use Content-Type application/cbor or application/cwt")
                    }
                    signedVical = readResponseBody(response, policy.maxBodyBytes)
            }
            val fetchedBytes = signedVical

            val validation = validator.validate(fetchedBytes, policy.validation)
            validation.fold(
                success = { validated ->
                    if (policy.useCache) {
                        writeCached(
                            canonicalUrl,
                            CachedVicalEnvelope(
                                signedVicalBase64 = fetchedBytes.encodeToBase64(),
                                fetchedAtEpochSeconds = Clock.System.now().epochSeconds,
                            ),
                            policy,
                        )
                    }
                    Ok(
                        VicalFetchResult(
                            validation = validated,
                            sourceUrl = canonicalUrl,
                            fetchedAtEpochSeconds = Clock.System.now().epochSeconds,
                            fromCache = false,
                        ),
                    )
                },
                failure = { error -> Err(error) },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (expected: Exception) {
            failure("MDOC_VICAL_FETCH_FAILED", "VICAL retrieval failed: ${expected.message}", expected)
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
                // Best-effort cleanup; the primary retrieval result is authoritative.
            }
        }
    }

    private suspend fun writeCached(
        canonicalUrl: String,
        envelope: CachedVicalEnvelope,
        policy: VicalFetchPolicy,
    ) {
        try {
            cache.putTenant(
                execution.tenantId,
                canonicalUrl,
                cacheJson.encodeToString(CachedVicalEnvelope.serializer(), envelope),
                policy.cacheTtl,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Cache availability must not turn an otherwise validated VICAL into a failure.
        }
    }

    private suspend fun removeCached(canonicalUrl: String) {
        try {
            cache.removeTenant(execution.tenantId, canonicalUrl)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Invalid cache entries are untrusted even if cleanup cannot be persisted.
        }
    }

    private fun cachedFetchedAt(serialized: String): Long =
        try {
            cacheJson.decodeFromString(CachedVicalEnvelope.serializer(), serialized).fetchedAtEpochSeconds
        } catch (_: Exception) {
            0L
        }

    private fun validationPolicy(requireHttps: Boolean): UrlValidationPolicy =
        UrlValidationPolicy.BLOCK_PRIVATE.copy(
            allowedSchemes = if (requireHttps) setOf("https") else setOf("https", "http"),
        )

    private fun validateUrl(value: String, requireHttps: Boolean): String {
        require(value.isNotBlank()) { "VICAL URL must not be blank" }
        require('#' !in value) { "VICAL URL must not contain a fragment" }
        val parsed = Url(value)
        validationPolicy(requireHttps).validate(parsed)
        return canonicalUrl(parsed)
    }

    private fun resolveRedirect(
        currentValue: String,
        location: String?,
        requireHttps: Boolean,
    ): String {
        require(!location.isNullOrBlank()) { "VICAL redirect did not provide a Location header" }
        val current = Url(currentValue)
        val targetValue = location.trim().substringBefore('#')
        require(targetValue.isNotEmpty()) { "VICAL redirect location was empty" }
        val target =
            when {
                targetValue.startsWith("//") -> "${current.protocol.name}:$targetValue"
                Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(targetValue) -> targetValue
                targetValue.startsWith("?") -> "${origin(current)}${current.encodedPath.ifEmpty { "/" }}$targetValue"
                else -> {
                    val queryStart = targetValue.indexOf('?')
                    val pathPart = if (queryStart < 0) targetValue else targetValue.substring(0, queryStart)
                    val queryPart = if (queryStart < 0) "" else targetValue.substring(queryStart)
                    val baseDirectory = current.encodedPath.ifEmpty { "/" }.substringBeforeLast('/', missingDelimiterValue = "") + "/"
                    "${origin(current)}${normalizePath(if (pathPart.startsWith('/')) pathPart else baseDirectory + pathPart)}$queryPart"
                }
            }
        return validateUrl(target, requireHttps)
    }

    private fun origin(url: Url): String {
        val host = if (url.host.contains(':')) "[${url.host}]" else url.host
        val defaultPort = if (url.protocol.name.equals("https", ignoreCase = true)) 443 else 80
        val port = if (url.port == defaultPort) "" else ":${url.port}"
        return "${url.protocol.name}://$host$port"
    }

    private fun canonicalUrl(url: Url): String {
        val scheme = url.protocol.name.lowercase()
        val host = if (url.host.contains(':')) "[${url.host.lowercase()}]" else url.host.lowercase()
        val defaultPort = if (scheme == "https") 443 else 80
        val port = if (url.port == defaultPort) "" else ":${url.port}"
        val path = url.encodedPath.ifEmpty { "/" }
        val query = url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""
        return "$scheme://$host$port$path$query"
    }

    private fun normalizePath(path: String): String {
        val output = ArrayDeque<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (output.isNotEmpty()) output.removeLast()
                else -> output.addLast(segment)
            }
        }
        val normalized = "/" + output.joinToString("/")
        return if (path.endsWith('/') && normalized != "/") "$normalized/" else normalized
    }

    private suspend fun readBounded(response: HttpResponse, maxBodyBytes: Long): ByteArray {
        val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        require(declaredLength == null || declaredLength in 0..maxBodyBytes) {
            "VICAL response body exceeds the configured maximum"
        }
        val channel = response.bodyAsChannel()
        val chunks = mutableListOf<ByteArray>()
        val buffer = ByteArray(minOf(8 * 1024L, maxBodyBytes).toInt())
        var total = 0L
        try {
            while (true) {
                val count = channel.readAvailable(buffer)
                if (count < 0) break
                if (count == 0) continue
                require(total <= maxBodyBytes - count.toLong()) {
                    "VICAL response body exceeds the configured maximum"
                }
                total += count
                chunks += buffer.copyOf(count)
            }
        } catch (cancelled: CancellationException) {
            try {
                channel.cancel(cancelled)
            } catch (_: Exception) {
                // Preserve cancellation even when the transport cleanup fails.
            }
            throw cancelled
        } catch (expected: Exception) {
            try {
                channel.cancel(expected)
            } catch (_: Exception) {
                // Preserve the size/read failure.
            }
            throw expected
        }
        val result = ByteArray(total.toInt())
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(result, destinationOffset = offset)
            offset += chunk.size
        }
        return result
    }

    private suspend fun readResponseBody(response: HttpResponse, maxBodyBytes: Long): ByteArray {
        val encoded = readBounded(response, maxBodyBytes)
        val codings =
            response.headers[HttpHeaders.ContentEncoding]
                ?.split(',')
                ?.map { it.trim().lowercase() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        if (codings.isEmpty() || codings == listOf("identity")) return encoded
        require(codings.size == 1 && codings[0] == "gzip") {
            "VICAL response uses unsupported Content-Encoding"
        }
        // GZIP carries the uncompressed size modulo 2^32 in its trailer. Check
        // that bound before inflating so a small compressed body cannot trigger
        // an oversized allocation in the common decompressor.
        require(encoded.size >= 18) { "VICAL gzip response is too short" }
        val declaredUncompressedSize =
            (encoded[encoded.size - 4].toLong() and 0xFF) or
                ((encoded[encoded.size - 3].toLong() and 0xFF) shl 8) or
                ((encoded[encoded.size - 2].toLong() and 0xFF) shl 16) or
                ((encoded[encoded.size - 1].toLong() and 0xFF) shl 24)
        require(declaredUncompressedSize <= maxBodyBytes) {
            "VICAL decompressed body exceeds the configured maximum"
        }
        val decoded = decompress(encoded, CompressionAlgorithm.GZIP)
        require(decoded.size.toLong() <= maxBodyBytes) {
            "VICAL decompressed body exceeds the configured maximum"
        }
        return decoded
    }

    private suspend fun discard(response: HttpResponse) {
        try {
            response.bodyAsChannel().cancel(null)
        } catch (_: Exception) {
            // The client close in fetchFromNetwork remains the final cleanup boundary.
        }
    }

    private fun <T> failure(
        code: String,
        message: String,
        cause: Exception? = null,
    ): IdkResult<T, IdkError> =
        Err(
            IdkError.fromString(
                code = code,
                message = message,
                exception = cause,
            ),
        )
}

@Serializable
private data class CachedVicalEnvelope(
    val signedVicalBase64: String,
    val fetchedAtEpochSeconds: Long,
)
