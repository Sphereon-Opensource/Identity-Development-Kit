/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.trust.core.resolver

import com.sphereon.trust.core.TrustDiagnosticReasonCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class TrustListHttpTransportTest {
    @Test
    fun streamingLimitCancelsTheBodyBeforeReturningTheStructuredFailure() = runBlocking {
        val body = ChunkedBody(listOf(byteArrayOf(1, 2), byteArrayOf(3, 4)))
        val response = response(body)

        val failure = assertFailsWith<TrustListResolutionException> { body.readAll(3) }

        assertEquals(TrustDiagnosticReasonCodes.TRUST_LIST_BODY_TOO_LARGE, failure.reasonCode)
        assertTrue(body.cancelled)
        response.close()
    }

    @Test
    fun responseCloseRunsTheTransportCloseActionEvenWhenBodyCancellationFails() = runBlocking {
        var transportClosed = false
        val response =
            TrustListHttpResponse(
                statusCode = 200,
                statusDescription = "OK",
                headers = emptyMap(),
                body =
                    object : TrustListResponseBody {
                        override suspend fun readChunk(): ByteArray? = null

                        override suspend fun cancel() {
                            error("body cancellation failed")
                        }
                    },
                closeAction = { transportClosed = true },
            )

        assertFailsWith<IllegalStateException> { response.close() }
        assertTrue(transportClosed)
    }

    private fun response(body: TrustListResponseBody): TrustListHttpResponse =
        TrustListHttpResponse(
            statusCode = 200,
            statusDescription = "OK",
            headers = emptyMap(),
            body = body,
            closeAction = {},
        )

    private class ChunkedBody(
        private val chunks: List<ByteArray>,
    ) : TrustListResponseBody {
        private var index = 0
        var cancelled = false
            private set

        override suspend fun readChunk(): ByteArray? = chunks.getOrNull(index++)

        override suspend fun cancel() {
            cancelled = true
        }
    }
}
