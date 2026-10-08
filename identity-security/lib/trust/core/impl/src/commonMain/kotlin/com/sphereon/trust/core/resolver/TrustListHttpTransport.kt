/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.trust.core.resolver

import com.sphereon.trust.core.TrustDiagnosticReasonCodes

/**
 * The resolver-owned HTTP boundary. Implementations must make the address
 * approval used by validation the same address set used by the dialer.
 */
internal interface TrustListHttpTransport {
    suspend fun execute(
        uri: String,
        timeoutMs: Long,
    ): TrustListHttpResponse
}

internal class TrustListHttpResponse(
    val statusCode: Int,
    val statusDescription: String?,
    val headers: Map<String, String>,
    body: TrustListResponseBody,
    val expiresAtEpochMillis: Long? = null,
    private val closeAction: () -> Unit,
) {
    val body: TrustListResponseBody = CancelOnceTrustListResponseBody(body)
    private var closed = false

    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    suspend fun close() {
        if (closed) return
        closed = true
        var cleanupFailure: Throwable? = null
        try {
            body.cancel()
        } catch (expected: Throwable) {
            cleanupFailure = expected
        }
        try {
            closeAction()
        } catch (expected: Throwable) {
            if (cleanupFailure == null) cleanupFailure = expected
        }
        cleanupFailure?.let { throw it }
    }
}

private class CancelOnceTrustListResponseBody(
    private val delegate: TrustListResponseBody,
) : TrustListResponseBody {
    private var cancelled = false

    override suspend fun readChunk(): ByteArray? = delegate.readChunk()

    override suspend fun cancel() {
        if (cancelled) return
        cancelled = true
        delegate.cancel()
    }
}

internal interface TrustListResponseBody {
    suspend fun readChunk(): ByteArray?

    suspend fun cancel()

    /**
     * Reads in bounded chunks and only materializes the complete value after
     * the bound has been checked. Crossing the bound cancels the source before
     * the exception is returned.
     */
    suspend fun readAll(maxBodyBytes: Long): ByteArray {
        if (maxBodyBytes <= 0L) {
            runCatching { cancel() }
            throw TrustListResolutionException(
                "Trust-list response body limit is invalid",
                reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_BODY_TOO_LARGE,
            )
        }

        val chunks = mutableListOf<ByteArray>()
        var total = 0L
        try {
            while (true) {
                val chunk = readChunk() ?: break
                if (chunk.isEmpty()) continue
                val chunkSize = chunk.size.toLong()
                if (total > maxBodyBytes - chunkSize || total > Int.MAX_VALUE.toLong() - chunkSize) {
                    throw TrustListResolutionException(
                        "Trust-list response body limit exceeded",
                        reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_BODY_TOO_LARGE,
                    )
                }
                total += chunkSize
                chunks += chunk
            }
        } catch (expected: TrustListResolutionException) {
            runCatching { cancel() }
            throw expected
        } catch (expected: Exception) {
            runCatching { cancel() }
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
}
