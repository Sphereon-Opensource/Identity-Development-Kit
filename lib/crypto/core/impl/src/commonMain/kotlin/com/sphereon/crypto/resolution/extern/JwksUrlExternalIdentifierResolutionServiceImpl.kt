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
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Resolves a JWKS from a remote URL (for example an OpenID `jwks_uri`).
 *
 * This service exists to ensure key retrieval is uniform and reusable across the IDK:
 * callers should rely on identifier resolution (crypto core) rather than ad-hoc HTTP fetching.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<JwksUrlExternalIdentifierResolutionService>())
@ContributesIntoSet(SessionScope::class, binding = binding<ExternalIdentifierService>())
class JwksUrlExternalIdentifierResolutionServiceImpl private constructor(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
    private val cacheManager: CacheManager?,
    @Suppress("UNUSED_PARAMETER")
    cacheManagerBinding: Unit,
) : ExternalIdentifierServiceAdapter<ExternalIdentifierResult.JwksUrl>(
        supportedIdentifierMethods = listOf(IdentifierMethodDefaults.JWKS_URL),
        execution = execution,
        commandId = COMMAND_ID,
    ),
    JwksUrlExternalIdentifierResolutionService {
    @Inject
    constructor(
        execution: SessionExecution,
        httpClientFactory: HttpClientFactory,
        cacheManager: CacheManager,
    ) : this(execution, httpClientFactory, cacheManager, Unit)

    constructor(
        execution: SessionExecution,
        httpClientFactory: HttpClientFactory,
    ) : this(execution, httpClientFactory, null, Unit)

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
     * Fetch the raw JWKS JSON. Bounded retry for transport errors and HTTP 5xx is configured
     * on the Ktor client via [jwksHttpClientOptions], so this function only maps the final
     * response into domain errors. A definite 4xx fails fast; a missing `kid` is surfaced by
     * the caller and never retried here.
     */
    private suspend fun fetchJwksJson(
        httpClient: HttpClient,
        url: String,
    ): IdkResult<String, IdkErrorType> {
        val response =
            try {
                httpClient.get(url)
            } catch (expected: Exception) {
                return IdkError
                    .UNKNOWN_ERROR(
                        message = "JWKS endpoint $url unavailable after ${MAX_FETCH_RETRIES + 1} attempts (transport error: ${expected.message})",
                        exception = expected,
                    ).asErrorResult()
            }

        if (response.status != HttpStatusCode.OK) {
            val prefix =
                if (response.status.value in 500..599) {
                    "JWKS endpoint $url unavailable after ${MAX_FETCH_RETRIES + 1} attempts"
                } else {
                    "Failed to fetch JWKS from $url"
                }
            return IdkError.UNKNOWN_ERROR(message = "$prefix: HTTP ${response.status.value}").asErrorResult()
        }

        return try {
            response.body<String>().asOkResult()
        } catch (expected: Exception) {
            IdkError
                .UNKNOWN_ERROR(
                    message = "JWKS endpoint $url returned an unreadable response body: ${expected.message}",
                    exception = expected,
                ).asErrorResult()
        }
    }

    /**
     * Resolve the JWKS for [url], serving a cached copy when it already contains the requested
     * key. On a cache miss OR a `kid` miss (rotation) the JWKS is fetched fresh (with
     * [fetchJwksJson]) and the cache updated. A fetch/parse failure, or a JWKS with no
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
                httpClientFactory.createClient(jwksHttpClientOptions())
            } catch (expected: Exception) {
                return IdkError.UNKNOWN_ERROR(message = "Failed to create HTTP client: ${expected.message}", exception = expected).asErrorResult()
            }

        val jwksJson =
            try {
                fetchJwksJson(httpClient, url).getOrElse { return it.asErrorResult() }
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

    private fun jwksHttpClientOptions(): HttpClientOptions =
        HttpClientOptions.createDefault().copy(
            additionalConfig = {
                followRedirects = false
                install(HttpRequestRetry) {
                    retryOnException(maxRetries = MAX_FETCH_RETRIES, retryOnTimeout = true)
                    retryOnServerErrors(maxRetries = MAX_FETCH_RETRIES)
                    delayMillis(respectRetryAfterHeader = true) { retry -> BASE_RETRY_BACKOFF_MILLIS * retry }
                }
            },
        )

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
         * Bounded Ktor retry for a TRANSIENT JWKS-endpoint condition (transport failure / HTTP
         * 5xx) — e.g. a peer briefly unreachable or not-yet-ready during a rolling restart.
         * A definite 4xx and a 200-with-missing-kid are NOT retried, and failures are never
         * cached, so a real auth problem still fails fast and a recovered peer is picked up
         * on the next request.
         */
        const val MAX_FETCH_RETRIES = 2
        private const val BASE_RETRY_BACKOFF_MILLIS = 200L
    }
}
