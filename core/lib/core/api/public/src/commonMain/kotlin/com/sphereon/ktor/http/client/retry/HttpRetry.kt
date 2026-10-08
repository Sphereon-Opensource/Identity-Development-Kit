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

package com.sphereon.ktor.http.client.retry

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlin.jvm.JvmStatic
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Retry policy for HTTP requests with exponential backoff, `Retry-After` header
 * support, adaptive rate limiting, and configurable retry predicates for both
 * HTTP status codes and network-level exceptions.
 *
 * **Idempotency note:** This policy has no awareness of HTTP method semantics.
 * Callers should only enable retry for idempotent requests (GET, PUT, DELETE)
 * or for non-idempotent requests (POST, PATCH) only when the server is known
 * to handle duplicates safely (e.g. via idempotency keys).
 *
 * Usage:
 * ```kotlin
 * val policy = HttpRetry()
 *     .withMaxRetries(3)
 *     .withBackoff(initialMs = 500, maxMs = 10_000, multiplier = 2.0)
 *     .withRetryOnRateLimit()
 *     .withRetryOnServerError()
 *     .withRetryOnNetworkError()
 *     .withRateLimitMs(0) // adaptive: learns from X-RateLimit-* server headers
 *     .execute { httpClient.request { ... } }
 * ```
 *
 * Or with the pre-configured default:
 * ```kotlin
 * val response = HttpRetry.defaultPolicy().execute { httpClient.request { ... } }
 * ```
 */
class HttpRetry private constructor(
    private val maxRetries: Int,
    private val initialBackoffMs: Long,
    private val maxBackoffMs: Long,
    private val backoffMultiplier: Double,
    private val retryableStatusCodes: Set<Int>,
    private val retryableExceptionPredicate: ((Throwable) -> Boolean)?,
    private val onRetry: (suspend (attempt: Int, cause: RetryTrigger, delayMs: Long) -> Unit)?,
    private val rateLimiter: RateLimiter?,
) {
    constructor() : this(
        maxRetries = DEFAULT_MAX_RETRIES,
        initialBackoffMs = DEFAULT_INITIAL_BACKOFF_MS,
        maxBackoffMs = DEFAULT_MAX_BACKOFF_MS,
        backoffMultiplier = DEFAULT_BACKOFF_MULTIPLIER,
        retryableStatusCodes = emptySet(),
        retryableExceptionPredicate = null,
        onRetry = null,
        rateLimiter = null,
    )

    fun withMaxRetries(maxRetries: Int): HttpRetry {
        require(maxRetries >= 0) { "maxRetries must be >= 0, got $maxRetries" }
        return copy(maxRetries = maxRetries)
    }

    fun withBackoff(
        initialMs: Long = initialBackoffMs,
        maxMs: Long = maxBackoffMs,
        multiplier: Double = backoffMultiplier,
    ): HttpRetry = copy(initialBackoffMs = initialMs, maxBackoffMs = maxMs, backoffMultiplier = multiplier)

    fun withRetryOn(vararg statusCodes: Int): HttpRetry = copy(retryableStatusCodes = retryableStatusCodes + statusCodes.toSet())

    fun withRetryOn(vararg statusCodes: HttpStatusCode): HttpRetry = copy(retryableStatusCodes = retryableStatusCodes + statusCodes.map { it.value }.toSet())

    fun withRetryOnRateLimit(): HttpRetry = withRetryOn(429)

    fun withRetryOnServerError(): HttpRetry = withRetryOn(502, 503, 504)

    /**
     * Retries on network-level exceptions: connection refused, reset, timeout, DNS failure, etc.
     * Matches [IOException] and common Ktor/network exception messages.
     */
    fun withRetryOnNetworkError(): HttpRetry =
        withRetryOnException { e ->
            e is IOException || e.isNetworkException()
        }

    /**
     * Registers a custom predicate to determine whether an exception is retryable.
     * Multiple predicates are combined with OR logic.
     *
     * The predicate must NOT match [CancellationException] — cancellation is
     * always propagated regardless of predicates.
     */
    fun withRetryOnException(predicate: (Throwable) -> Boolean): HttpRetry {
        val existing = retryableExceptionPredicate
        val combined =
            if (existing != null) {
                { e: Throwable -> existing(e) || predicate(e) }
            } else {
                predicate
            }
        return copy(retryableExceptionPredicate = combined)
    }

    /**
     * Proactively spaces requests at least [minIntervalMs] apart to avoid
     * hitting rate limits in the first place. When server-reported rate limit
     * headers are present (`X-RateLimit-Remaining`, `X-RateLimit-Reset`, or
     * the IETF `RateLimit-*` equivalents), the interval adapts dynamically:
     * the remaining request budget is spread evenly over the remaining window
     * time, with a 10% safety margin.
     *
     * If the server reports the budget is exhausted (`remaining <= 0`), the
     * rate limiter waits until the window resets before allowing the next
     * request.
     *
     * The rate limiter state is shared across all builder copies originating
     * from the same [withRateLimitMs] call, so chaining additional `.withXxx()`
     * calls after this one preserves the same throttle.
     */
    fun withRateLimitMs(minIntervalMs: Long): HttpRetry = copy(rateLimiter = RateLimiter(minIntervalMs))

    /**
     * Registers a callback that is invoked before each retry delay.
     * Useful for logging or metrics.
     */
    fun withOnRetry(callback: suspend (attempt: Int, cause: RetryTrigger, delayMs: Long) -> Unit): HttpRetry = copy(onRetry = callback)

    /**
     * Executes the HTTP [request] block with the configured retry policy.
     *
     * If a rate limit interval is configured, the call will suspend until the
     * minimum interval since the last request has elapsed before firing.
     *
     * `Retry-After` headers from the server are respected without capping,
     * so the caller should use coroutine timeouts if an upper bound on total
     * execution time is needed.
     *
     * Returns the [HttpResponse] on success or after all retries are exhausted.
     * The caller is responsible for checking the response status.
     *
     * @throws CancellationException always propagated (never retried)
     * @throws Exception if all retries are exhausted on network errors,
     *   the last exception is rethrown
     */
    suspend fun execute(request: suspend () -> HttpResponse): HttpResponse {
        var lastResponse: HttpResponse? = null
        var lastException: Throwable? = null

        for (attempt in 0..maxRetries) {
            rateLimiter?.awaitSlot()

            val response =
                try {
                    request()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Throwable) {
                    if (retryableExceptionPredicate?.invoke(exception) == true && attempt < maxRetries) {
                        lastException = exception
                        val delayMs = computeBackoff(attempt)
                        onRetry?.invoke(attempt + 1, RetryTrigger.Exception(exception), delayMs)
                        delay(delayMs)
                        continue
                    }
                    throw exception
                }

            rateLimiter?.updateFromResponse(response)

            if (!isRetryableStatus(response)) {
                return response
            }
            lastResponse = response
            lastException = null
            if (attempt < maxRetries) {
                val delayMs = resolveDelay(response, attempt)
                onRetry?.invoke(attempt + 1, RetryTrigger.Status(response), delayMs)
                delay(delayMs)
            }
        }

        if (lastException != null) throw lastException
        return lastResponse ?: error("HttpRetry: no response after ${maxRetries + 1} attempts")
    }

    private fun isRetryableStatus(response: HttpResponse): Boolean = response.status.value in retryableStatusCodes

    /**
     * Computes the delay before retrying. `Retry-After` from the server is
     * respected without capping — the server knows best when the client should
     * retry. Only the computed exponential backoff is capped by [maxBackoffMs].
     */
    private fun resolveDelay(
        response: HttpResponse,
        attempt: Int
    ): Long {
        val retryAfterMs =
            response.headers[HttpHeaders.RetryAfter]
                ?.toLongOrNull()
                ?.times(1000)
        // Retry-After is NOT capped: the server mandate takes priority
        return retryAfterMs ?: computeBackoff(attempt)
    }

    private fun computeBackoff(attempt: Int): Long = (initialBackoffMs * pow(backoffMultiplier, attempt)).toLong().coerceAtMost(maxBackoffMs)

    private fun copy(
        maxRetries: Int = this.maxRetries,
        initialBackoffMs: Long = this.initialBackoffMs,
        maxBackoffMs: Long = this.maxBackoffMs,
        backoffMultiplier: Double = this.backoffMultiplier,
        retryableStatusCodes: Set<Int> = this.retryableStatusCodes,
        retryableExceptionPredicate: ((Throwable) -> Boolean)? = this.retryableExceptionPredicate,
        onRetry: (suspend (Int, RetryTrigger, Long) -> Unit)? = this.onRetry,
        rateLimiter: RateLimiter? = this.rateLimiter,
    ) = HttpRetry(maxRetries, initialBackoffMs, maxBackoffMs, backoffMultiplier, retryableStatusCodes, retryableExceptionPredicate, onRetry, rateLimiter)

    /**
     * Describes what triggered a retry: either an HTTP status or a network exception.
     */
    sealed class RetryTrigger {
        data class Status(
            val response: HttpResponse
        ) : RetryTrigger() {
            override fun toString(): String = "HTTP ${response.status.value}"
        }

        data class Exception(
            val throwable: Throwable
        ) : RetryTrigger() {
            override fun toString(): String = "${throwable::class.simpleName}: ${throwable.message}"
        }
    }

    /**
     * Coroutine-safe adaptive rate limiter that ensures a minimum interval
     * between requests and dynamically adjusts based on server-reported
     * rate limit headers.
     *
     * When `X-RateLimit-Remaining` / `X-RateLimit-Reset` headers (or the
     * IETF `RateLimit-*` equivalents) are present in responses, the limiter
     * spreads the remaining request budget evenly over the remaining window
     * time. If the budget is exhausted it waits until the window resets.
     *
     * All mutable state is guarded by [mutex] for coroutine safety.
     *
     * The mutable state is shared by reference across [HttpRetry] builder copies.
     */
    internal class RateLimiter(
        private val minIntervalMs: Long
    ) {
        private val mutex = Mutex()
        private var lastRequestMark: TimeSource.Monotonic.ValueTimeMark? = null

        // Server-reported rate limit state, updated after each response
        private var windowLimit: Int? = null
        private var windowRemaining: Int? = null
        private var windowEndMark: TimeSource.Monotonic.ValueTimeMark? = null

        /**
         * Updates rate limit state from HTTP response headers.
         * Supports both `X-RateLimit-*` and IETF draft `RateLimit-*` conventions.
         *
         * `X-RateLimit-Reset` is interpreted as **unix epoch seconds** (common
         * convention for `X-` prefixed headers, used by GitHub, Pronto, etc.).
         * IETF `RateLimit-Reset` is interpreted as **delta-seconds** from now
         * (per the IETF rate limit header draft).
         *
         * All writes are mutex-guarded for coroutine safety.
         */
        suspend fun updateFromResponse(response: HttpResponse) =
            mutex.withLock {
                val limit =
                    response.headerIntOrNull("X-RateLimit-Limit")
                        ?: response.headerIntOrNull("RateLimit-Limit")
                val remaining =
                    response.headerIntOrNull("X-RateLimit-Remaining")
                        ?: response.headerIntOrNull("RateLimit-Remaining")

                if (limit != null) windowLimit = limit
                if (remaining != null) windowRemaining = remaining

                // X-RateLimit-Reset: unix epoch seconds (common convention)
                val xResetEpoch = response.headerLongOrNull("X-RateLimit-Reset")
                if (xResetEpoch != null) {
                    val responseEpochMs = response.responseTime.timestamp
                    val deltaMs = xResetEpoch * 1000 - responseEpochMs
                    if (deltaMs > 0) {
                        windowEndMark = TimeSource.Monotonic.markNow() + deltaMs.milliseconds
                    }
                    return@withLock
                }

                // IETF RateLimit-Reset: delta-seconds from now
                val ietfResetDelta = response.headerLongOrNull("RateLimit-Reset")
                if (ietfResetDelta != null && ietfResetDelta > 0) {
                    windowEndMark = TimeSource.Monotonic.markNow() + ietfResetDelta.seconds
                }
            }

        /**
         * Suspends until the next request slot is available based on the
         * configured minimum interval and server-reported rate limit state.
         */
        suspend fun awaitSlot() {
            if (minIntervalMs <= 0 && windowRemaining == null) return
            mutex.withLock {
                val remaining = windowRemaining
                val endMark = windowEndMark

                // Budget exhausted: wait until window resets
                if (remaining != null && remaining <= 0 && endMark != null) {
                    val waitMs = -endMark.elapsedNow().inWholeMilliseconds
                    if (waitMs > 0) {
                        delay(waitMs + 1000) // +1s safety margin past the reset
                    }
                    // Clear stale state; next response will repopulate
                    windowRemaining = null
                    windowEndMark = null
                    lastRequestMark = TimeSource.Monotonic.markNow()
                    return@withLock
                }

                // Adaptive: spread remaining budget evenly over remaining window time
                val adaptiveMs =
                    if (remaining != null && remaining > 0 && endMark != null) {
                        val windowRemainingMs = -endMark.elapsedNow().inWholeMilliseconds
                        if (windowRemainingMs > 0) {
                            // Reserve 10% of budget as safety margin
                            val safeRemaining = (remaining * 0.9).toInt().coerceAtLeast(1)
                            windowRemainingMs / safeRemaining
                        } else {
                            null
                        }
                    } else {
                        null
                    }

                val effectiveInterval = maxOf(minIntervalMs, adaptiveMs ?: 0L)
                if (effectiveInterval <= 0) {
                    lastRequestMark = TimeSource.Monotonic.markNow()
                    return@withLock
                }

                val mark = lastRequestMark
                if (mark != null) {
                    val elapsedMs = mark.elapsedNow().inWholeMilliseconds
                    val waitMs = effectiveInterval - elapsedMs
                    if (waitMs > 0) {
                        delay(waitMs)
                    }
                }
                lastRequestMark = TimeSource.Monotonic.markNow()
            }
        }

        /** Current remaining budget as reported by the server, or null if unknown. */
        internal val currentRemaining: Int? get() = windowRemaining

        /** Current window limit as reported by the server, or null if unknown. */
        internal val currentLimit: Int? get() = windowLimit

        private fun HttpResponse.headerIntOrNull(name: String): Int? = headers[name]?.toIntOrNull()

        private fun HttpResponse.headerLongOrNull(name: String): Long? = headers[name]?.toLongOrNull()
    }

    companion object {
        private const val DEFAULT_MAX_RETRIES = 3
        private const val DEFAULT_RATE_LIMIT_MS = 100L
        private const val DEFAULT_SAFETY_FACTOR = 0.9
        private const val DEFAULT_INITIAL_BACKOFF_MS = 500L
        private const val DEFAULT_MAX_BACKOFF_MS = 10_000L
        private const val DEFAULT_BACKOFF_MULTIPLIER = 2.0
        private const val MS_PER_SECOND = 1000L
        private const val MAX_BACKOFF_MS = 30_000L

        @JvmStatic
        fun defaultPolicy(): HttpRetry =
            HttpRetry()
                .withRetryOnRateLimit()
                .withRetryOnServerError()
                .withRetryOnNetworkError()

        private fun pow(
            base: Double,
            exponent: Int
        ): Double {
            var result = 1.0
            repeat(exponent) { result *= base }
            return result
        }

        /**
         * Checks common network exception patterns across platforms.
         * Ktor wraps platform-specific exceptions, so we also check messages.
         */
        private fun Throwable.isNetworkException(): Boolean {
            val msg = message?.lowercase() ?: return false
            return NETWORK_ERROR_PATTERNS.any { msg.contains(it) }
        }
    }
}

private val NETWORK_ERROR_PATTERNS =
    setOf(
        "connection reset",
        "connection refused",
        "connect timed out",
        "read timed out",
        "socket closed",
        "broken pipe",
        "no route to host",
        "network is unreachable",
        "unexpected end of stream",
        "failed to connect",
    )
