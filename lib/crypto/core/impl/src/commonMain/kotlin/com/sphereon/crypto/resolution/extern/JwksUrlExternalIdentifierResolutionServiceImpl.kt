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
 *
 */

@file:Suppress("TooGenericExceptionCaught")

package com.sphereon.crypto.resolution.extern

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.cache.CacheManager
import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheSerializers
import com.sphereon.core.api.cache.ScopedCache
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Resolves a JWKS from a remote URL (for example an OpenID `jwks_uri`).
 *
 * This service exists to ensure key retrieval is uniform and reusable across the IDK:
 * callers should rely on identifier resolution (crypto core) rather than ad-hoc HTTP fetching.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<JwksUrlExternalIdentifierResolutionService>())
@ContributesIntoSet(SessionScope::class, binding = binding<ExternalIdentifierService>())
class JwksUrlExternalIdentifierResolutionServiceImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
    private val cacheManager: CacheManager? = null,
) : ExternalIdentifierServiceAdapter<ExternalIdentifierResult.JwksUrl>(
        supportedIdentifierMethods = listOf(IdentifierMethodDefaults.JWKS_URL),
        execution = execution,
        commandId = COMMAND_ID,
    ),
    JwksUrlExternalIdentifierResolutionService {
    /**
     * Per-`jwks_uri` JWKS cache via the IDK [CacheManager] abstraction, so a peer whose JWKS
     * endpoint is briefly unavailable (e.g. mid rolling-restart) does not fail validation.
     * Keyed strictly by URL; a `kid` miss re-resolves (rotation); failures and empty results
     * are never cached. Null when no [CacheManager] is bound (optional dependency) — then every
     * call resolves fresh, exactly as before.
     */
    private val jwksCache: ScopedCache<String, JwkSet>? by lazy {
        cacheManager?.let { cm ->
            cm.getCache<String, JwkSet>(CACHE_NAMESPACE)
                ?: cm.createStringCache(
                    CacheRequirements.localOnly(CACHE_NAMESPACE),
                    CacheSerializers.json(JwkSet.serializer()),
                )
        }
    }

    override suspend fun doExecute(
        args: ExternalIdentifierOptsOrResult,
        applyDuring: (ExternalIdentifierOptsOrResult) -> ExternalIdentifierOptsOrResult,
    ): IdkResult<ExternalIdentifierResult.JwksUrl, IdkErrorType> {
        // Note: supports() validation is already performed by parent CommandAdapter.execute()
        val opts = asSupportedOpts(args).value
        val url = opts.identifier.trim()
        if (url.isBlank()) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "JWKS URL is blank").asErrorResult()
        }

        val requestedKid = opts.lookup.kid
        val jwkSet = resolveJwkSetCached(url, requestedKid).getOrElse { return it.asErrorResult() }

        @Suppress("UNCHECKED_CAST")
        val resolvedKeys =
            jwkSet.keys
                .mapNotNull {
                    try {
                        ResolvedKeyInfo.fromKey(it) as ResolvedKeyInfoType<JwkType>
                    } catch (expected: Exception) {
                        log.debug("Skipping unsupported key in JWKS: ${expected.message}")
                        null
                    }
                }.toTypedArray()
        if (resolvedKeys.isEmpty()) {
            return IdkError.NOT_FOUND_ERROR(message = "JWKS from $url contains no keys").asErrorResult()
        }
        val selectedKey: com.sphereon.crypto.core.ResolvedKeyInfoType<JwkType> =
            when {
                !requestedKid.isNullOrBlank() -> {
                    resolvedKeys.firstOrNull { it.kid == requestedKid }
                        ?: return IdkError.NOT_FOUND_ERROR(message = "No key with kid '$requestedKid' found in JWKS from $url").asErrorResult()
                }

                resolvedKeys.isNotEmpty() -> {
                    resolvedKeys.first()
                }

                // We simply take the first key
                else -> {
                    return IdkError
                        .ILLEGAL_ARGUMENT_ERROR(
                            message = "JWKS from $url contains no keys",
                        ).asErrorResult()
                }
            }

        return ExternalIdentifierResult
            .JwksUrl(
                identifierOpts = opts,
                jwks = resolvedKeys,
                keyInfo = selectedKey,
                jwksUrl = url,
                selectedKid = requestedKid,
            ).asOkResult()
    }

    /**
     * Fetch the raw JWKS JSON with a bounded, jittered retry on TRANSIENT failures only:
     * transport/connection errors and HTTP 5xx (peer briefly unavailable / not-yet-ready,
     * e.g. mid rolling-restart). A definite 4xx (and any other non-OK, non-5xx) is a real
     * error and fails fast; a 200 whose body cannot be read is treated as transient. Nothing
     * is cached: a recovered peer is re-resolved on the next call, a 200-with-missing-kid is
     * surfaced (not retried) by the caller, and a stale/forged key is never trusted.
     */
    private suspend fun fetchJwksJsonWithRetry(
        httpClient: HttpClient,
        url: String,
    ): IdkResult<String, IdkErrorType> {
        var lastDetail = "unknown error"
        var lastException: Throwable? = null
        repeat(MAX_FETCH_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                delay(retryBackoffMillis(attempt))
            }
            val response =
                try {
                    httpClient.get(url)
                } catch (transient: Exception) {
                    lastDetail = "transport error: ${transient.message}"
                    lastException = transient
                    log.debug("JWKS fetch attempt ${attempt + 1}/$MAX_FETCH_ATTEMPTS to $url failed (transport): ${transient.message}")
                    return@repeat
                }
            when {
                response.status == HttpStatusCode.OK -> {
                    val body =
                        try {
                            response.body<String>()
                        } catch (bodyError: Exception) {
                            lastDetail = "response read error: ${bodyError.message}"
                            lastException = bodyError
                            log.debug("JWKS fetch attempt ${attempt + 1}/$MAX_FETCH_ATTEMPTS to $url failed reading body: ${bodyError.message}")
                            return@repeat
                        }
                    return body.asOkResult()
                }

                response.status.value in 500..599 -> {
                    lastDetail = "HTTP ${response.status.value}"
                    log.debug("JWKS fetch attempt ${attempt + 1}/$MAX_FETCH_ATTEMPTS to $url failed (HTTP ${response.status.value})")
                }

                else -> {
                    return IdkError
                        .UNKNOWN_ERROR(
                            message = "Failed to fetch JWKS from $url: HTTP ${response.status.value}",
                        ).asErrorResult()
                }
            }
        }
        return IdkError
            .UNKNOWN_ERROR(
                message = "JWKS endpoint $url unavailable after $MAX_FETCH_ATTEMPTS attempts ($lastDetail)",
                exception = lastException,
            ).asErrorResult()
    }

    private fun retryBackoffMillis(attempt: Int): Long = BASE_RETRY_BACKOFF_MILLIS * attempt + Random.nextLong(RETRY_JITTER_MILLIS)

    /**
     * Resolve the JWKS for [url], serving a cached copy when it already contains the requested
     * key. On a cache miss OR a `kid` miss (rotation) the JWKS is fetched fresh (with
     * [fetchJwksJsonWithRetry]) and the cache updated. A fetch/parse failure, or a JWKS with no
     * usable key, is NEVER cached — so a transient peer outage never pins a failure and a
     * recovered peer is re-resolved on the next call.
     */
    private suspend fun resolveJwkSetCached(
        url: String,
        requestedKid: String?,
    ): IdkResult<JwkSet, IdkErrorType> {
        jwksCache?.getApp(url)?.let { cached ->
            if (jwkSetContainsUsableKey(cached, requestedKid)) {
                return cached.asOkResult()
            }
        }

        val httpClient =
            try {
                httpClientFactory.createClient(HttpClientOptions.createDefault())
            } catch (expected: Exception) {
                return IdkError.UNKNOWN_ERROR(message = "Failed to create HTTP client: ${expected.message}", exception = expected).asErrorResult()
            }

        val jwksJson =
            try {
                fetchJwksJsonWithRetry(httpClient, url).getOrElse { return it.asErrorResult() }
            } finally {
                try {
                    httpClient.close()
                } catch (expected: Exception) {
                    log.debug("HTTP client close failed: ${expected.message}")
                }
            }

        val jwkSet =
            try {
                JwkSet.fromJsonString(jwksJson)
            } catch (expected: Exception) {
                return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid JWKS JSON from $url: ${expected.message}", throwable = expected).asErrorResult()
            }

        // Cache only a freshly-fetched JWKS that yields at least one usable key. Never cache a
        // failure or an empty/unusable result — that would reintroduce the durable-failure-cache
        // anti-pattern (a transient outage pinning a 401 until restart).
        if (jwkSetContainsUsableKey(jwkSet, requestedKid = null)) {
            jwksCache?.putApp(url, jwkSet, JWKS_CACHE_TTL)
        }
        return jwkSet.asOkResult()
    }

    /**
     * True when [jwkSet] yields at least one supported key and — when [requestedKid] is given —
     * a key carrying that `kid`. A requested `kid` absent from a cached set means it is stale
     * (rotation) and must be re-resolved, never rejected from the cache.
     */
    private fun jwkSetContainsUsableKey(
        jwkSet: JwkSet,
        requestedKid: String?,
    ): Boolean {
        val resolved =
            jwkSet.keys.mapNotNull {
                runCatching { ResolvedKeyInfo.fromKey(it) }.getOrNull()
            }
        if (resolved.isEmpty()) return false
        return requestedKid.isNullOrBlank() || resolved.any { it.kid == requestedKid }
    }

    override suspend fun supports(args: Any): Boolean {
        val externalArgs = args as? ExternalIdentifierOptsOrResult ?: return false
        val methodSupported =
            externalArgs is ExternalIdentifierJwksUrlOpts ||
                externalArgs.method?.let { isSupportedIdentifierMethod(it) } == true
        return methodSupported && isSupportedIdentifier(externalArgs.identifier)
    }

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean {
        // Do not try to infer the method from the URL shape; selection should be driven by opts.method (JWKS_URL).
        // We only require a valid-looking http(s) URL here.
        return identifier is String &&
            (identifier.startsWith("https://", ignoreCase = true) || identifier.startsWith("http://", ignoreCase = true)) &&
            identifier.contains("://")
    }

    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult.JwksUrl, IdkErrorType> = execute(opts)

    override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierJwksUrlOpts, IdkErrorType> =
        if (isSupportedOpts(opts)) {
            (opts as ExternalIdentifierJwksUrlOpts).asOkResult()
        } else {
            IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR().asErrorResult()
        }

    companion object {
        const val COMMAND_ID = "crypto.resolution.jwksurl"

        /** Namespace for the per-`jwks_uri` JWKS cache. */
        const val CACHE_NAMESPACE = "crypto.resolution.jwks-cache"

        /**
         * Positive TTL for a cached JWKS. Signing keys rotate on human timescales, so a short
         * TTL balances freshness against peer availability; a `kid` miss re-resolves immediately
         * so a rotation still surfaces promptly, and failures are never cached.
         */
        val JWKS_CACHE_TTL: Duration = 10.minutes

        /**
         * Bounded retry for a TRANSIENT JWKS-endpoint condition (transport failure / HTTP
         * 5xx) — e.g. a peer briefly unreachable or not-yet-ready during a rolling restart.
         * A definite 4xx and a 200-with-missing-kid are NOT retried, and failures are never
         * cached, so a real auth problem still fails fast and a recovered peer is picked up
         * on the next request. Worst-case added latency on a fully-down peer is bounded
         * (~3 attempts with sub-second jittered backoff).
         */
        const val MAX_FETCH_ATTEMPTS = 3
        private const val BASE_RETRY_BACKOFF_MILLIS = 200L
        private const val RETRY_JITTER_MILLIS = 150L
    }
}
