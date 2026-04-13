package com.sphereon.mdoc.transfer.device.website

import com.sphereon.core.api.log.LogService
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.SessionData
import com.sphereon.mdoc.SessionEstablishment
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.engagement.MdocEngagementEvent
import com.sphereon.mdoc.transfer.MdocRetrievalEvent
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transfer.device.website.DeviceRetrievalWebsiteFactory.Defaults.defaultHttpClientOptions
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.Uuid

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRetrievalWebsiteImpl", exact = true)
internal class DeviceRetrievalWebsiteImpl(
    override val log: LogService,
    private val httpClientFactory: HttpClientFactory,
    options: HttpClientOptions,
    override val readerEngagement: ReaderEngagement? = null
) :
    DeviceRetrievalWebsite,
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
        this.options = options
    }

    /*override*/ suspend fun sendRaw(bytes: ByteArray): ByteArray {
        val client = getClient(options)
        val response = client.post(getRestApiUrl()) {
            setBody(bytes)
        }
        if (response.status != HttpStatusCode.Companion.OK) {
            _engagementEvents.emit(
                MdocEngagementEvent.Error(
                    role = MdocRole.MDOC,
                    engagementId = engagementId,
                    reason = "Device engagement message failed to send to remote REST API reader at: ${this.restApiOptions.uri}, status: ${response.status}"
                )
            )
            throw IllegalStateException("Device engagement message failed to send to remote REST API reader at: ${this.restApiOptions.uri}, status: ${response.status}")
        }
        log.debug("Received response from reader REST API: ${response.status}, will decode session establishment message")
        return response.bodyAsBytes()
    }

    /*override*/ suspend fun receiveRaw(bytes: ByteArray): ByteArray {
        val client = getClient(options)
        val response = client.post(getRestApiUrl()) {
            setBody(bytes)
        }
        if (response.status != HttpStatusCode.Companion.OK) {
            _retrievalEvents.emit(
                MdocRetrievalEvent.Error(
                    engagementId,
                    data = byteArrayOf(),
                    error = IllegalStateException("Session data failed to send to remote REST API reader at: ${this.restApiOptions.uri}, status: ${response.status}")
                )
            )
            throw IllegalStateException("Session data failed to send to remote REST API reader at: ${this.restApiOptions.uri}, status: ${response.status}")
        }
        log.debug("Received response from reader REST API: ${response.status}, will decode session data message")
        return response.bodyAsBytes()
    }


    override suspend fun sendDeviceEngagement(
        deviceEngagement: DeviceEngagement,
        options: HttpClientOptions,
        restApiOptions: RestApiOptions?
    ): SessionEstablishment {
        _engagementEvents.emit(MdocEngagementEvent.Start(role = MdocRole.MDOC, engagementId = engagementId))
        this.options = options
        initRestApiOptions(restApiOptions)
        log.info("Sending device engagement message with version ${deviceEngagement.version} to remote REST API reader at: ${this.restApiOptions.uri}")

        _engagementEvents.emit(
            MdocEngagementEvent.RestApiEngagement(
                role = MdocRole.MDOC,
                readerEngagement = this.readerEngagement,
                engagementId = engagementId,
                uri = getRestApiUrl()
            )
        )

        val deviceRetrievalWebsite =
            deviceEngagement.deviceRetrievalMethods?.first { method -> method.type == DeviceRetrievalMethodType.WEBSITE }?.copy(retrievalOptions = this.restApiOptions)
                ?: throw IllegalStateException("Device engagement does not contain a website retrieval method")
        _engagementEvents.emit(
            MdocEngagementEvent.Connecting(
                role = MdocRole.MDOC,
                engagementId = engagementId,
                deviceRetrievalMethods = arrayOf(deviceRetrievalWebsite)
            )
        )
        val bytes = sendRaw(deviceEngagement.encodeCbor())
        val sessionEstablishment = SessionEstablishment.Decoder.decodeCbor(bytes)
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
            throw IllegalStateException("Cannot send session data without a connected engagement")
        } else if (_retrievalEvents.value !is MdocRetrievalEvent.SessionEstablishmentReceived) {
            _retrievalEvents.emit(
                MdocRetrievalEvent.Error(
                    engagementId,
                    data = byteArrayOf(),
                    error = IllegalStateException("Cannot send session data without a session establishment")
                )
            )
            throw IllegalStateException("Cannot send session data without a session establishment")
        }

        _retrievalEvents.emit(MdocRetrievalEvent.DeviceRequestReady(engagementId, data = sessionData.encodeCbor()))

        val bytes = receiveRaw(sessionData.encodeCbor())
        return SessionData.Decoder.decodeCbor(bytes)
            .also { client.close(); log.info("Session data message sent successfully, received Session Data from remote REST API reader at: ${getRestApiUrl()}") }
    }

    private fun initRestApiOptions(restApiOptions: RestApiOptions? = null) {
        if (restApiOptions != null) {
            if (readerEngagement != null) {
                if (!readerEngagement.hasWebsiteRetrievalMethod) {
                    throw IllegalStateException("Reader engagement was supplied, but it does not have a website retrieval method. Cannot combine non website retrieval method with supplied reader engagement")
                } else if (readerEngagement.getWebsiteRetrievalOptions()?.uri != restApiOptions.uri) {
                    throw IllegalArgumentException("Reader engagement website retrieval method URI ${readerEngagement.getWebsiteRetrievalOptions()?.uri} does not match supplied restApiOptions URI ${restApiOptions.uri}")
                }
            }
            this.restApiOptions = RestApiOptions(uri = restApiOptions.uri)
            return
        } else if (readerEngagement != null) {
            if (!readerEngagement.hasWebsiteRetrievalMethod) {
                throw IllegalStateException("Reader engagement was supplied, but it does not have a website retrieval method. Cannot retrieve device engagement without a reader engagement")
            } else {
                val uri = readerEngagement.getWebsiteRetrievalOptions()?.uri ?: throw IllegalStateException("Reader engagement website retrieval method URI is null")
                this.restApiOptions = RestApiOptions(uri = uri)
                return
            }
        }

        throw IllegalStateException("No reader engagement or restApiOptions supplied. Cannot retrieve device engagement")
    }

    private fun getRestApiUrl(): String {
        if (!::restApiOptions.isInitialized) {
            throw IllegalStateException("restApiOptions has not been initialized. Cannot retrieve device engagement")
        }
        return restApiOptions.uri
    }

    private fun getClient(options: HttpClientOptions = defaultHttpClientOptions(log)): HttpClient {
        if (!::client.isInitialized) {
            this.options = options
            this.client = httpClientFactory.createClient(options)
        } else if (options != this.options) {
            this.options = options
            client.close()
            this.client = httpClientFactory.createClient(options)

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


}
