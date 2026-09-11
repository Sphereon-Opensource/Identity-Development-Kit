/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.trust.core.resolver

import com.sphereon.core.api.cache.DefaultCacheManager
import com.sphereon.core.api.cache.DefaultCacheService
import com.sphereon.core.api.cache.MapCacheBackend
import com.sphereon.trust.core.TrustDiagnosticReasonCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

class TrustListResponseClosureMatrixTest {
    @Test
    fun resolverClosesEveryResponseExactlyOnceAcrossTheRequiredOutcomeMatrix() = runBlocking {
        runCase(
            name = "success",
            expectedReasonCode = null,
            plans = listOf(plan(statusCode = 200, body = "success")),
        )
        runCase(
            name = "redirect hop",
            expectedReasonCode = null,
            options = options(maxRedirects = 1),
            plans =
                listOf(
                    plan(statusCode = 302, headers = mapOf("Location" to "/final"), body = "redirect"),
                    plan(statusCode = 200, body = "final"),
                ),
        )
        runCase(
            name = "http error",
            expectedReasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_HTTP_STATUS,
            plans = listOf(plan(statusCode = 503, body = "error")),
        )
        runCase(
            name = "missing redirect",
            expectedReasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_REDIRECT_MISSING,
            options = options(maxRedirects = 1),
            plans = listOf(plan(statusCode = 302, body = "redirect")),
        )
        runCase(
            name = "malformed redirect",
            expectedReasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_REDIRECT_MALFORMED,
            options = options(maxRedirects = 1),
            plans = listOf(plan(statusCode = 302, headers = mapOf("Location" to "https://[malformed"))),
        )
        runCase(
            name = "redirect limit",
            expectedReasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_REDIRECT_LIMIT,
            plans = listOf(plan(statusCode = 302, headers = mapOf("Location" to "/next"))),
        )
        runCase(
            name = "malformed content length",
            expectedReasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_HTTP_STATUS,
            plans = listOf(plan(statusCode = 200, headers = mapOf("Content-Length" to "invalid"))),
        )
        runCase(
            name = "negative content length",
            expectedReasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_HTTP_STATUS,
            plans = listOf(plan(statusCode = 200, headers = mapOf("Content-Length" to "-1"))),
        )
        runCase(
            name = "oversized content length",
            expectedReasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_BODY_TOO_LARGE,
            options = options(maxBodyBytes = 3),
            plans = listOf(plan(statusCode = 200, headers = mapOf("Content-Length" to "4"), body = "1234")),
        )
        runCase(
            name = "streaming oversize",
            expectedReasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_BODY_TOO_LARGE,
            options = options(maxBodyBytes = 3),
            plans = listOf(plan(statusCode = 200, chunks = listOf("12", "34"))),
        )
        runCase(
            name = "transport read exception",
            expectedReasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_RESOLUTION_FAILED,
            plans =
                listOf(
                    plan(
                        statusCode = 200,
                        readFailure = IllegalStateException("socket read detail must not escape"),
                    ),
                ),
        )
    }

    @Test
    fun transportExceptionHasAStableNonSensitiveReason() = runBlocking {
        val resolver = resolverFor(ThrowingTransport())
        val failure =
            try {
                resolver.resolve("https://example.com/transport", options())
                error("expected transport failure")
            } catch (expected: TrustListResolutionException) {
                expected
            }

        assertEquals(TrustDiagnosticReasonCodes.TRUST_LIST_RESOLUTION_FAILED, failure.reasonCode)
        assertNotNull(failure.message)
        assertFalse(failure.message!!.contains("secret socket detail"))
    }

    @Test
    fun bodyCancellationFailureCannotReplacePrimaryHttpFailure() = runBlocking {
        val tracked = TrackedResponse(plan(statusCode = 503, cancelFailure = IllegalStateException("secret cancel detail")))
        val resolver = resolverFor(QueueTransport(listOf(tracked.response)))
        val failure =
            try {
                resolver.resolve("https://example.com/cleanup-http.xml", options())
                error("expected HTTP failure")
            } catch (expected: Throwable) {
                expected
            }

        val resolutionFailure = failure as? TrustListResolutionException
        assertNotNull(resolutionFailure)
        assertEquals(TrustDiagnosticReasonCodes.TRUST_LIST_HTTP_STATUS, resolutionFailure.reasonCode)
        assertEquals(1, tracked.body.cancelCount)
        assertEquals(1, tracked.closeCount)
        assertFalse(resolutionFailure.message!!.contains("secret cancel detail"))
    }

    @Test
    fun transportCloseFailureCannotReplacePrimaryBodyLimitFailure() = runBlocking {
        val tracked =
            TrackedResponse(
                plan(
                    statusCode = 200,
                    chunks = listOf("1234"),
                    closeFailure = IllegalStateException("secret close detail"),
                ),
            )
        val resolver = resolverFor(QueueTransport(listOf(tracked.response)))
        val failure =
            try {
                resolver.resolve("https://example.com/cleanup-body.xml", options(maxBodyBytes = 3))
                error("expected body limit failure")
            } catch (expected: Throwable) {
                expected
            }

        val resolutionFailure = failure as? TrustListResolutionException
        assertNotNull(resolutionFailure)
        assertEquals(TrustDiagnosticReasonCodes.TRUST_LIST_BODY_TOO_LARGE, resolutionFailure.reasonCode)
        assertEquals(1, tracked.body.cancelCount)
        assertEquals(1, tracked.closeCount)
        assertFalse(resolutionFailure.message!!.contains("secret close detail"))
    }

    @Test
    fun successfulResolutionConvertsCleanupFailureToStableFailure() = runBlocking {
        val tracked =
            TrackedResponse(
                plan(
                    statusCode = 200,
                    body = "success",
                    closeFailure = IllegalStateException("secret close detail"),
                ),
            )
        val resolver = resolverFor(QueueTransport(listOf(tracked.response)))
        val failure =
            try {
                resolver.resolve("https://example.com/cleanup-success.xml", options())
                error("expected cleanup failure")
            } catch (expected: Throwable) {
                expected
            }

        val resolutionFailure = failure as? TrustListResolutionException
        assertNotNull(resolutionFailure)
        assertEquals(TrustDiagnosticReasonCodes.TRUST_LIST_RESOLUTION_FAILED, resolutionFailure.reasonCode)
        assertEquals(1, tracked.body.cancelCount)
        assertEquals(1, tracked.closeCount)
        assertFalse(resolutionFailure.message!!.contains("secret close detail"))
    }

    private suspend fun runCase(
        name: String,
        expectedReasonCode: String?,
        plans: List<ResponsePlan>,
        options: ResolutionOptions = options(),
    ) {
        val tracked = plans.map { TrackedResponse(it) }
        val resolver = resolverFor(QueueTransport(tracked.map { it.response }))
        var failure: Throwable? = null

        try {
            resolver.resolve("https://example.com/current.xml", options)
        } catch (expected: Throwable) {
            failure = expected
        }

        if (expectedReasonCode == null) {
            assertEquals(null, failure, name)
        } else {
            val resolutionFailure = failure as? TrustListResolutionException
            assertNotNull(resolutionFailure, name)
            assertEquals(expectedReasonCode, resolutionFailure.reasonCode, name)
        }

        tracked.forEach { response ->
            assertEquals(1, response.body.cancelCount, "$name body cancellation")
            assertEquals(1, response.closeCount, "$name transport close")
        }
    }

    private fun options(
        maxBodyBytes: Long = 128,
        maxRedirects: Int = 0,
    ) =
        ResolutionOptions(
            useCache = false,
            maxBodyBytes = maxBodyBytes,
            maxRedirects = maxRedirects,
        )

    private fun plan(
        statusCode: Int,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        chunks: List<String>? = null,
        readFailure: Exception? = null,
        cancelFailure: Exception? = null,
        closeFailure: Exception? = null,
    ) =
        ResponsePlan(
            statusCode = statusCode,
            headers = headers,
            chunks = chunks?.map(String::encodeToByteArray) ?: listOf(body.encodeToByteArray()),
            readFailure = readFailure,
            cancelFailure = cancelFailure,
            closeFailure = closeFailure,
        )

    private data class ResponsePlan(
        val statusCode: Int,
        val headers: Map<String, String>,
        val chunks: List<ByteArray>,
        val readFailure: Exception?,
        val cancelFailure: Exception?,
        val closeFailure: Exception?,
    )

    private class TrackedResponse(plan: ResponsePlan) {
        val body = CountingBody(plan.chunks, plan.readFailure, plan.cancelFailure)
        var closeCount = 0

        val response =
            TrustListHttpResponse(
                statusCode = plan.statusCode,
                statusDescription = "test",
                headers = plan.headers,
                body = body,
                closeAction = {
                    closeCount++
                    plan.closeFailure?.let { throw it }
                },
            )
    }

    private class CountingBody(
        private val chunks: List<ByteArray>,
        private val readFailure: Exception?,
        private val cancelFailure: Exception?,
    ) : TrustListResponseBody {
        private var index = 0
        var cancelCount = 0
            private set

        override suspend fun readChunk(): ByteArray? {
            readFailure?.let { throw it }
            return chunks.getOrNull(index++)
        }

        override suspend fun cancel() {
            cancelCount++
            cancelFailure?.let { throw it }
        }
    }

    private class QueueTransport(
        responses: List<TrustListHttpResponse>,
    ) : TrustListHttpTransport {
        private val responses = ArrayDeque(responses)

        override suspend fun execute(uri: String, timeoutMs: Long): TrustListHttpResponse = responses.removeFirst()
    }

    private class ThrowingTransport : TrustListHttpTransport {
        override suspend fun execute(uri: String, timeoutMs: Long): TrustListHttpResponse =
            throw IllegalStateException("secret socket detail")
    }

    private fun resolverFor(transport: TrustListHttpTransport): HttpTrustListResolver {
        val cacheManager = DefaultCacheManager()
        cacheManager.registerBackend(MapCacheBackend())
        return HttpTrustListResolver(transport, DefaultCacheService(cacheManager))
    }
}
