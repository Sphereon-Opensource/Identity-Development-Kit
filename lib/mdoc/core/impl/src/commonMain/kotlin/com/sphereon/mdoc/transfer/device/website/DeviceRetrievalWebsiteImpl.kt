/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

package com.sphereon.mdoc.transfer.device.website

import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.toException
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.ktor.http.client.provider.UrlValidationPolicy
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.SessionData
import com.sphereon.mdoc.SessionDataCborCodec
import com.sphereon.mdoc.SessionEstablishment
import com.sphereon.mdoc.SessionEstablishmentCborCodec
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.engagement.DeviceEngagementCborCodec
import com.sphereon.mdoc.engagement.MdocEngagementEvent
import com.sphereon.mdoc.transfer.MdocRetrievalEvent
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.Uuid

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRetrievalWebsiteImpl", exact = true)
internal class DeviceRetrievalWebsiteImpl(
    private val log: LogService,
    private val httpClientFactory: HttpClientFactory,
    options: HttpClientOptions?,
    private val deviceEngagementCborCodec: DeviceEngagementCborCodec,
    private val sessionEstablishmentCborCodec: SessionEstablishmentCborCodec,
    private val sessionDataCborCodec: SessionDataCborCodec,
    override val readerEngagement: ReaderEngagement? = null,
) : DeviceRetrievalWebsite,
    MdocEngagementEvent.Dispatcher {
    val engagementId = Uuid.Companion.random()

    private val _engagementEvents: MutableStateFlow<MdocEngagementEvent> = MutableStateFlow(MdocEngagementEvent.Initializing(role = MdocRole.MDOC, engagementId = engagementId))
    val engagementEvents: StateFlow<MdocEngagementEvent> = _engagementEvents.asStateFlow()

    private val _retrievalEvents: MutableStateFlow<MdocRetrievalEvent> = MutableStateFlow(MdocRetrievalEvent.Initializing(engagementId))
    val retrievalEvents: StateFlow<MdocRetrievalEvent> = _retrievalEvents.asStateFlow()
    private lateinit var restApiOptions: RestApiOptions
    lateinit var client: HttpClient

    override var options: HttpClientOptions

    init {
        this.options = options ?: defaultDeviceRetrievalWebsiteHttpClientOptions()
    }

    // override
    suspend fun sendRaw(bytes: ByteArray): ByteArray {
        val client = getClient(options)
        val response =
            client.post(getRestApiUrl()) {
                contentType(ContentType.Application.Cbor)
                header(HttpHeaders.Accept, ContentType.Application.Cbor.toString())
                setBody(bytes)
            }
        if (response.status != HttpStatusCode.Companion.OK) {
            _engagementEvents.emit(
                MdocEngagementEvent.Error(
                    role = MdocRole.MDOC,
                    engagementId = engagementId,
                    reason = "Device engagement message failed to send to remote REST API reader at: ${this.restApiOptions.uri}, status: ${response.status}",
                ),
            )
            error("Device engagement message failed to send to remote REST API reader at: ${this.restApiOptions.uri}, status: ${response.status}")
        }
        requireCborResponse(response, "SessionEstablishment")
        log.debug("Received response from reader REST API: ${response.status}, will decode session establishment message")
        return readBoundedResponseBody(response)
    }

    // override
    suspend fun receiveRaw(bytes: ByteArray): ByteArray {
        val client = getClient(options)
        val response =
            client.post(getRestApiUrl()) {
                contentType(ContentType.Application.Cbor)
                header(HttpHeaders.Accept, ContentType.Application.Cbor.toString())
                setBody(bytes)
            }
        if (response.status != HttpStatusCode.Companion.OK) {
            _retrievalEvents.emit(
                MdocRetrievalEvent.Error(
                    engagementId,
                    data = byteArrayOf(),
                    error = IllegalStateException("Session data failed to send to remote REST API reader at: ${this.restApiOptions.uri}, status: ${response.status}"),
                ),
            )
            error("Session data failed to send to remote REST API reader at: ${this.restApiOptions.uri}, status: ${response.status}")
        }
        requireCborResponse(response, "SessionData")
        log.debug("Received response from reader REST API: ${response.status}, will decode session data message")
        return readBoundedResponseBody(response)
    }

    override suspend fun sendDeviceEngagement(
        deviceEngagement: DeviceEngagement,
        options: HttpClientOptions?,
        restApiOptions: RestApiOptions?,
    ): SessionEstablishment {
        _engagementEvents.emit(MdocEngagementEvent.Start(role = MdocRole.MDOC, engagementId = engagementId))
        options?.let { this.options = it }
        initRestApiOptions(restApiOptions)
        log.info("Sending device engagement message with version ${deviceEngagement.version} to remote REST API reader at: ${this.restApiOptions.uri}")

        _engagementEvents.emit(
            MdocEngagementEvent.RestApiEngagement(
                role = MdocRole.MDOC,
                readerEngagement = this.readerEngagement,
                engagementId = engagementId,
                uri = getRestApiUrl(),
            ),
        )

        val deviceRetrievalWebsite =
            deviceEngagement.deviceRetrievalMethods?.first { method -> method.type == DeviceRetrievalMethodType.WEBSITE }?.copy(retrievalOptions = this.restApiOptions)
                ?: error("Device engagement does not contain a website retrieval method")
        _engagementEvents.emit(
            MdocEngagementEvent.Connecting(
                role = MdocRole.MDOC,
                engagementId = engagementId,
                deviceRetrievalMethods = arrayOf(deviceRetrievalWebsite),
            ),
        )
        val deviceEngagementBytes =
            deviceEngagementCborCodec
                .encode(deviceEngagement)
                .getOrElse { throw it.toException() }
        val bytes = sendRaw(deviceEngagementBytes)
        val sessionEstablishment =
            sessionEstablishmentCborCodec
                .decode(bytes)
                .getOrElse { throw it.toException() }
                .value
        log.info("Device engagement message sent successfully, received Session Establishment from remote REST API reader at: ${this.restApiOptions.uri}")
        // TODO probably move to raw send and inspect current state first, so connection manager can also properly use it
        _engagementEvents.emit(MdocEngagementEvent.Connected(role = MdocRole.MDOC, engagementId = engagementId, deviceRetrievalMethod = deviceRetrievalWebsite))
        _retrievalEvents.emit(MdocRetrievalEvent.SessionEstablishmentReceived(engagementId, data = bytes))

        return sessionEstablishment
    }

    override suspend fun sendSessionData(sessionData: SessionData): SessionData {
        log.info("Sending session data message with status ${sessionData.status} to remote REST API reader at: ${getRestApiUrl()}")

        if (_engagementEvents.value !is MdocEngagementEvent.Connected) {
            _engagementEvents.emit(MdocEngagementEvent.Error(role = MdocRole.MDOC, engagementId = engagementId, reason = "Cannot send session data without a connected engagement"))
            error("Cannot send session data without a connected engagement")
        } else if (_retrievalEvents.value !is MdocRetrievalEvent.SessionEstablishmentReceived) {
            _retrievalEvents.emit(
                MdocRetrievalEvent.Error(
                    engagementId,
                    data = byteArrayOf(),
                    error = IllegalStateException("Cannot send session data without a session establishment"),
                ),
            )
            error("Cannot send session data without a session establishment")
        }

        val sessionDataBytes =
            sessionDataCborCodec
                .encode(sessionData)
                .getOrElse { throw it.toException() }
        _retrievalEvents.emit(MdocRetrievalEvent.DeviceRequestReady(engagementId, data = sessionDataBytes))

        val bytes = receiveRaw(sessionDataBytes)
        return sessionDataCborCodec
            .decode(bytes)
            .getOrElse { throw it.toException() }
            .value
            .also {
                client.close()
                log.info("Session data message sent successfully, received Session Data from remote REST API reader at: ${getRestApiUrl()}")
            }
    }

    private fun initRestApiOptions(restApiOptions: RestApiOptions? = null) {
        if (restApiOptions != null) {
            validateRestApiUri(restApiOptions.uri)
            if (readerEngagement != null) {
                if (!readerEngagement.hasWebsiteRetrievalMethod) {
                    error("Reader engagement was supplied, but it does not have a website retrieval method. Cannot combine non website retrieval method with supplied reader engagement")
                } else {
                    require(readerEngagement.getWebsiteRetrievalOptions()?.uri == restApiOptions.uri) {
                        "Reader engagement website retrieval method URI ${readerEngagement.getWebsiteRetrievalOptions()?.uri} does not match supplied restApiOptions URI ${restApiOptions.uri}"
                    }
                }
            }
            this.restApiOptions = RestApiOptions(uri = restApiOptions.uri)
            return
        } else if (readerEngagement != null) {
            if (!readerEngagement.hasWebsiteRetrievalMethod) {
                error("Reader engagement was supplied, but it does not have a website retrieval method. Cannot retrieve device engagement without a reader engagement")
            } else {
                val uri = readerEngagement.getWebsiteRetrievalOptions()?.uri ?: error("Reader engagement website retrieval method URI is null")
                validateRestApiUri(uri)
                this.restApiOptions = RestApiOptions(uri = uri)
                return
            }
        }

        error("No reader engagement or restApiOptions supplied. Cannot retrieve device engagement")
    }

    private fun validateRestApiUri(uri: String) {
        require(uri.startsWith("https://", ignoreCase = true)) {
            "REST API URI must use HTTPS, got: $uri"
        }
        UrlValidationPolicy.BLOCK_PRIVATE.validate(Url(uri))
    }

    private fun getRestApiUrl(): String {
        check(::restApiOptions.isInitialized) { "restApiOptions has not been initialized. Cannot retrieve device engagement" }
        return restApiOptions.uri
    }

    /**
     * Annex A exchanges CBOR data items over the website endpoint. Do not pass a successful text or
     * JSON response to a CBOR decoder: accepting it makes a misrouted endpoint indistinguishable
     * from a protocol response and can also consume an attacker-controlled response body first.
     */
    private fun requireCborResponse(response: HttpResponse, messageType: String) {
        val responseType = response.contentType()?.withoutParameters()
        require(responseType == ContentType.Application.Cbor) {
            "REST API $messageType response must use Content-Type application/cbor, got ${response.contentType()}"
        }
    }

    private fun getClient(options: HttpClientOptions? = null): HttpClient {
        // Reader-engagement URLs are untrusted input. Do not let a caller-provided
        // HttpClientOptions re-enable redirects or disable the SSRF policy for this
        // retrieval mechanism.
        val effectiveOptions =
            (options ?: this.options).copy(
                followRedirects = false,
                urlValidation = UrlValidationPolicy.BLOCK_PRIVATE,
            )
        if (!::client.isInitialized) {
            this.options = effectiveOptions
            this.client = httpClientFactory.createClient(effectiveOptions)
        } else if (effectiveOptions != this.options) {
            this.options = effectiveOptions
            client.close()
            this.client = httpClientFactory.createClient(effectiveOptions)
        }
        return client
    }

    override fun dispatch(event: MdocEngagementEvent) {
        _engagementEvents.value = event
    }

    override fun close() {
        if (::client.isInitialized) {
            client.close()
        }
    }

    /**
     * Bound remote session responses before they reach the CBOR/session
     * decryptors. The endpoint comes from reader engagement input and must not
     * be able to turn an HTTP response into an unbounded allocation.
     */
    private suspend fun readBoundedResponseBody(response: HttpResponse): ByteArray {
        val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        require(declaredLength == null || declaredLength in 0..MAX_RESPONSE_BODY_BYTES) {
            "REST API response body exceeds the configured maximum"
        }

        val channel = response.bodyAsChannel()
        val chunks = mutableListOf<ByteArray>()
        val buffer = ByteArray(minOf(8 * 1024L, MAX_RESPONSE_BODY_BYTES).toInt())
        var total = 0L
        try {
            while (true) {
                val count = channel.readAvailable(buffer)
                if (count < 0) break
                if (count == 0) continue
                require(total <= MAX_RESPONSE_BODY_BYTES - count.toLong()) {
                    "REST API response body exceeds the configured maximum"
                }
                total += count
                chunks += buffer.copyOf(count)
            }
        } catch (expected: CancellationException) {
            try {
                channel.cancel(expected)
            } catch (_: Exception) {
                // Preserve cancellation.
            }
            throw expected
        } catch (expected: Exception) {
            try {
                channel.cancel(expected)
            } catch (_: Exception) {
                // Preserve the primary read/size failure.
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

    private companion object {
        private const val MAX_RESPONSE_BODY_BYTES = 10L * 1024L * 1024L
    }
}
