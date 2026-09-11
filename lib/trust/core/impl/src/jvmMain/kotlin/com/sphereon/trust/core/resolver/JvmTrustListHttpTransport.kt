/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.trust.core.resolver

import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.TrustDiagnosticReasonCodes
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import okhttp3.Dns

/**
 * JVM transport that pins the OkHttp DNS result to the addresses approved
 * immediately before the request. OkHttp is not allowed to perform a second,
 * independent DNS lookup for the connection.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<TrustListHttpTransport>(),
    replaces = [FailClosedTrustListHttpTransport::class],
)
internal class JvmTrustListHttpTransport internal constructor(
    private val resolveAddresses: (String) -> List<InetAddress>,
    private val addressPolicy: (List<String>) -> Unit,
    private val dialAddresses: (String, List<InetAddress>) -> List<InetAddress>,
    private val dialAddressPolicy: (List<String>, String) -> Unit,
) : TrustListHttpTransport {
    @Inject
    constructor() : this(
        resolveAddresses = { InetAddress.getAllByName(it).toList() },
        addressPolicy = { addresses -> TrustListAddressPolicy.requireGlobalAddresses(addresses) },
        dialAddresses = { _, approved -> approved },
        dialAddressPolicy = { approved, dial ->
            TrustListAddressPolicy.requireApprovedDialAddress(approved, dial)
        },
    )

    internal constructor(
        resolveAddresses: (String) -> List<InetAddress>,
        addressPolicy: (List<String>) -> Unit,
        dialAddressPolicy: (List<String>, String) -> Unit = { approved, dial ->
            TrustListAddressPolicy.requireApprovedDialAddress(approved, dial)
        },
    ) : this(
        resolveAddresses = resolveAddresses,
        addressPolicy = addressPolicy,
        dialAddresses = { _, approved -> approved },
        dialAddressPolicy = dialAddressPolicy,
    )

    override suspend fun execute(
        uri: String,
        timeoutMs: Long,
    ): TrustListHttpResponse {
        val url =
            try {
                Url(uri).also { require(it.host.isNotBlank()) }
            } catch (expected: Exception) {
                throw TrustListResolutionException(
                    "Trust-list URL is malformed",
                    expected,
                    TrustDiagnosticReasonCodes.TRUST_LIST_URL_MALFORMED,
                )
            }

        val approvedAddresses =
            try {
                resolveAddresses(url.host).also { addressPolicy(it.map(InetAddress::getHostAddress)) }
            } catch (expected: TrustListResolutionException) {
                throw expected
            } catch (expected: UnknownHostException) {
                throw TrustListResolutionException(
                    "Trust-list destination could not be resolved",
                    expected,
                    TrustDiagnosticReasonCodes.TRUST_LIST_ADDRESS_RESOLUTION_FAILED,
                )
            } catch (expected: Exception) {
                throw TrustListResolutionException(
                    "Trust-list destination could not be resolved",
                    expected,
                    TrustDiagnosticReasonCodes.TRUST_LIST_ADDRESS_RESOLUTION_FAILED,
                )
            }

        if (approvedAddresses.isEmpty()) {
            throw TrustListResolutionException(
                "Trust-list destination could not be resolved",
                reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_ADDRESS_RESOLUTION_FAILED,
            )
        }

        val approvedAddressStrings = approvedAddresses.map(InetAddress::getHostAddress)
        val client =
            HttpClient(OkHttp) {
                followRedirects = false
                engine {
                    config {
                        proxy(java.net.Proxy.NO_PROXY)
                        dns(
                            object : Dns {
                                override fun lookup(hostname: String): List<InetAddress> {
                                    if (!hostname.equals(url.host, ignoreCase = true)) {
                                        throw UnknownHostException()
                                    }
                                    val selected = dialAddresses(hostname, approvedAddresses)
                                    if (selected.isEmpty()) throw UnknownHostException()
                                    selected.forEach { dialAddressPolicy(approvedAddressStrings, it.hostAddress) }
                                    return selected
                                }
                            },
                        )
                    }
                }
            }

        try {
            val response =
                client.get(uri) {
                    timeout {
                        requestTimeoutMillis = timeoutMs
                    }
                }
            return response.toTrustListResponse(client)
        } catch (expected: TrustListResolutionException) {
            client.close()
            throw expected
        } catch (expected: Exception) {
            client.close()
            throw expected.findTrustListResolutionException() ?: expected
        }
    }

    private suspend fun HttpResponse.toTrustListResponse(client: HttpClient): TrustListHttpResponse {
        val response = this
        val channel = response.bodyAsChannel()
        val headers =
            response.headers.entries().associate { (name, values) ->
                name to values.joinToString(",")
            }
        return TrustListHttpResponse(
            statusCode = response.status.value,
            statusDescription = response.status.description,
            headers = headers,
            body = KtorTrustListResponseBody(channel),
            expiresAtEpochMillis = parseExpires(response.headers[HttpHeaders.Expires]),
            closeAction = {
                client.close()
            },
        )
    }

    private class KtorTrustListResponseBody(
        private val channel: ByteReadChannel,
    ) : TrustListResponseBody {
        override suspend fun readChunk(): ByteArray? {
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = channel.readAvailable(buffer)
                if (count < 0) return null
                if (count > 0) return buffer.copyOf(count)
            }
        }

        override suspend fun cancel() {
            channel.cancel(null)
        }
    }

    internal companion object {
        fun parseExpires(value: String?): Long? =
            value?.let {
                runCatching {
                    ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
                }.getOrNull()
            }
    }
}

private fun Throwable.findTrustListResolutionException(): TrustListResolutionException? {
    var current: Throwable? = this
    while (current != null) {
        if (current is TrustListResolutionException) return current
        current = current.cause
    }
    return null
}
